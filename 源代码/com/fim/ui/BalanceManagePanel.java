package com.fim.ui;

import com.fim.dao.ExcelDAO;
import com.fim.model.AccountSubject;
import com.fim.model.OpeningBalance;
import com.fim.model.User;
import com.fim.service.AccountService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public class BalanceManagePanel extends JPanel {
    private final ExcelDAO dao;
    private final AccountService accountService;
    private final User currentUser;
    private JTable table;
    private DefaultTableModel tableModel;
    private boolean dirty = false;
    private boolean autoCalcLock = false;

    public BalanceManagePanel(ExcelDAO dao, AccountService accountService, User currentUser) {
        this.dao = dao;
        this.accountService = accountService;
        this.currentUser = currentUser;
        initUI();
    }

    public boolean isDirty() { return dirty; }

    public boolean trySaveOnExit() {
        if (!dirty) return true;
        int choice = JOptionPane.showConfirmDialog(this,
            "期初余额已修改，是否保存？", "提示", JOptionPane.YES_NO_CANCEL_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            saveBalances();
            return true;
        }
        return choice == JOptionPane.NO_OPTION;
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        if (currentUser.canManageAccounts()) {
            JButton saveBtn = new JButton("保存余额");
            topPanel.add(saveBtn);
            saveBtn.addActionListener(e -> saveBalances());
        }
        JButton trialBtn = new JButton("试算平衡检查");
        topPanel.add(trialBtn);
        add(topPanel, BorderLayout.NORTH);

        String[] cols = {"科目编码", "科目名称", "方向", "期初余额"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return (col == 2 || col == 3) && currentUser.canManageAccounts();
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(30);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);

        // 方向下拉框
        JComboBox<String> dirCombo = new JComboBox<>(new String[]{"借", "贷"});
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(dirCombo));

        // 每次编辑单元格都自动汇总到上级科目
        tableModel.addTableModelListener(e -> {
            if (!autoCalcLock) {
                dirty = true;
                autoCalcLock = true;
                autoCalcParentBalances();
                autoCalcLock = false;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new GridLayout(1, 3));
        bottomPanel.add(new JLabel(" "));
        bottomPanel.add(new JLabel(" "));
        bottomPanel.add(new JLabel(" "));
        add(bottomPanel, BorderLayout.SOUTH);

        trialBtn.addActionListener(e -> checkTrialBalance());
    }

    public void refreshData() {
        try {
            autoCalcLock = true;
            dirty = false;
            List<AccountSubject> subjects = accountService.getAllSubjects();
            List<OpeningBalance> balances = dao.readOpeningBalances();

            // 建一个编码到余额的映射
            Map<String, OpeningBalance> balMap = new LinkedHashMap<>();
            for (OpeningBalance ob : balances) {
                balMap.put(ob.getAccountCode(), ob);
            }

            tableModel.setRowCount(0);
            for (AccountSubject s : subjects) {
                OpeningBalance ob = balMap.get(s.getCode());
                String dir = s.getDirection() != null ? s.getDirection() : "借";
                double amt = 0;
                if (ob != null) {
                    if (ob.getDirection() != null && !ob.getDirection().isEmpty()) dir = ob.getDirection();
                    amt = ob.getAmount();
                }
                tableModel.addRow(new Object[]{s.getCode(), s.getName(), dir, amt});
            }
            autoCalcLock = false;
        } catch (Exception ex) {
            autoCalcLock = false;
            JOptionPane.showMessageDialog(this, "加载数据失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveBalances() {
        try {
            // 保存前最后一次汇总，确保父科目值是最新的
            autoCalcParentBalances();
            String trialError = validateTrialBalance();
            if (trialError != null) {
                JOptionPane.showMessageDialog(this, "试算不平衡，无法保存！\n\n" + trialError, "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            List<OpeningBalance> balances = new ArrayList<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                OpeningBalance ob = new OpeningBalance();
                ob.setAccountCode((String) tableModel.getValueAt(i, 0));
                ob.setAccountName((String) tableModel.getValueAt(i, 1));
                ob.setDirection((String) tableModel.getValueAt(i, 2));
                Object amt = tableModel.getValueAt(i, 3);
                ob.setAmount(amt == null ? 0 : Double.parseDouble(amt.toString()));
                balances.add(ob);
            }
            dao.writeOpeningBalances(balances);
            dirty = false;
            JOptionPane.showMessageDialog(this, "保存成功！");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void autoCalcParentBalances() {
        try {
            // 重置所有有子科目的父科目（不限层级）
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String code = (String) tableModel.getValueAt(i, 0);
                if (hasChildren(code)) {
                    tableModel.setValueAt("0.00", i, 3);
                }
            }
            // 按科目编码长度降序排列行索引：先处理最长编码（最深子科目），再处理短编码（父科目）
            // 保证孙→子→父的自底向上汇总
            List<Integer> sortedRows = new ArrayList<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                sortedRows.add(i);
            }
            sortedRows.sort((a, b) -> {
                String ca = (String) tableModel.getValueAt(a, 0);
                String cb = (String) tableModel.getValueAt(b, 0);
                return Integer.compare(cb.length(), ca.length()); // 降序：长的在前
            });

            for (int rowIdx : sortedRows) {
                String code = (String) tableModel.getValueAt(rowIdx, 0);
                String parentCode = getParentCode(code);
                if (!parentCode.isEmpty()) {
                    double childAmt = parseAmt(tableModel.getValueAt(rowIdx, 3));
                    String childDir = (String) tableModel.getValueAt(rowIdx, 2);

                    for (int j = 0; j < tableModel.getRowCount(); j++) {
                        if (parentCode.equals(tableModel.getValueAt(j, 0))) {
                            double parentAmt = parseAmt(tableModel.getValueAt(j, 3));
                            String parentDir = (String) tableModel.getValueAt(j, 2);
                            if (childDir != null && childDir.equals(parentDir)) {
                                parentAmt += childAmt;
                            } else {
                                parentAmt -= childAmt;
                            }
                            tableModel.setValueAt(String.format("%.2f", parentAmt), j, 3);
                            break;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // silent — auto-calc failures shouldn't interrupt the user
        }
    }

    private String validateTrialBalance() {
        double debitTotal = 0, creditTotal = 0;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String code = (String) tableModel.getValueAt(i, 0);
            if (!hasChildren(code)) {
                double amt = parseAmt(tableModel.getValueAt(i, 3));
                String dir = (String) tableModel.getValueAt(i, 2);
                if ("借".equals(dir)) debitTotal += amt;
                else creditTotal += amt;
            }
        }
        double diff = Math.abs(debitTotal - creditTotal);
        if (diff < 0.01) return null;
        return String.format("借方合计: %.2f\n贷方合计: %.2f\n差额: %.2f", debitTotal, creditTotal, diff);
    }

    private void checkTrialBalance() {
        String error = validateTrialBalance();
        if (error == null) {
            JOptionPane.showMessageDialog(this, "试算平衡！", "试算平衡", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, error + "\n\n试算不平衡！", "试算平衡", JOptionPane.WARNING_MESSAGE);
        }
    }

    private double parseAmt(Object val) {
        if (val == null) return 0;
        try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private String getParentCode(String code) {
        if (code.length() <= 4) return "";
        return code.substring(0, code.length() - 2);
    }

    private boolean hasChildren(String code) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String child = (String) tableModel.getValueAt(i, 0);
            if (child.startsWith(code) && child.length() > code.length()) return true;
        }
        return false;
    }
}
