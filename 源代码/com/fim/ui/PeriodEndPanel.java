package com.fim.ui;

import com.fim.dao.ExcelDAO;
import com.fim.model.*;
import com.fim.service.ClosingService;
import com.fim.service.VoucherService;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public class PeriodEndPanel extends JPanel {
    private final ExcelDAO dao;
    private final VoucherService voucherService;
    private final ClosingService closingService;
    private final User currentUser;

    private JTable pnlTable;
    private DefaultTableModel pnlModel;
    private JLabel statusLabel;

    public PeriodEndPanel(ExcelDAO dao, VoucherService voucherService, ClosingService closingService, User currentUser) {
        this.dao = dao;
        this.voucherService = voucherService;
        this.closingService = closingService;
        this.currentUser = currentUser;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        if (currentUser.canClosePeriod()) {
            JButton carryForwardBtn = new JButton("月末损益结转");
            JButton closeMonthBtn = new JButton("月末结账");
            topPanel.add(carryForwardBtn);
            topPanel.add(closeMonthBtn);

            carryForwardBtn.addActionListener(e -> {
                try {
                    int currentMonth = LocalDate.now().getMonthValue();
                    List<Voucher> entries = closingService.carryForwardProfitLoss(currentMonth, currentUser.getName());
                    if (entries.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "损益类科目余额为0，无需结转。");
                    } else {
                        StringBuilder sb = new StringBuilder("生成的损益结转凭证:\n");
                        java.util.Set<String> numSet = new java.util.HashSet<>();
                        for (Voucher v : entries) {
                            String key = v.getType() + v.getNumber();
                            if (numSet.add(key)) {
                                sb.append(v.getType()).append(v.getNumber()).append(" ").append(v.getDescription()).append("\n");
                            }
                        }
                        int confirm = JOptionPane.showConfirmDialog(this,
                            sb.toString() + "\n是否将结转凭证写入凭证表？",
                            "损益结转", JOptionPane.YES_NO_OPTION);
                        if (confirm == JOptionPane.YES_OPTION) {
                            List<Voucher> all = dao.readVouchers();
                            all.addAll(entries);
                            dao.writeVouchers(all);
                            JOptionPane.showMessageDialog(this, "损益结转凭证已生成并保存！");
                            refreshData();
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "结转失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            });

            closeMonthBtn.addActionListener(e -> {
                String monthStr = JOptionPane.showInputDialog(this, "请输入要结账的月份（1-12）：", "选择月份");
                if (monthStr == null) return;
                try {
                    int month = Integer.parseInt(monthStr);
                    int confirm = JOptionPane.showConfirmDialog(this,
                        "确定要对 " + month + " 月份进行结账吗？\n结账后该月份将禁止填制新凭证！",
                        "确认结账", JOptionPane.YES_NO_OPTION);
                    if (confirm != JOptionPane.YES_OPTION) return;
                    closingService.closeMonth(month);
                    JOptionPane.showMessageDialog(this, month + "月份结账完成！");
                    refreshData();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "结账失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            });
        }
        JButton checkBalanceBtn = new JButton("试算平衡检查");
        topPanel.add(checkBalanceBtn);
        add(topPanel, BorderLayout.NORTH);

        // 损益汇总表
        String[] cols = {"科目编码", "科目名称", "本期借方发生额", "本期贷方发生额", "余额", "方向"};
        pnlModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        pnlTable = new JTable(pnlModel);
        pnlTable.setRowHeight(30);
        pnlTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        pnlTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        pnlTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        pnlTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        pnlTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        pnlTable.getColumnModel().getColumn(5).setPreferredWidth(60);

        JScrollPane scrollPane = new JScrollPane(pnlTable);
        add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        add(statusLabel, BorderLayout.SOUTH);

        // 绑定事件
        checkBalanceBtn.addActionListener(e -> {
            try {
                boolean balanced = closingService.checkTrialBalance();
                if (balanced) {
                    JOptionPane.showMessageDialog(this, "试算平衡！可以进行结账。", "检查结果", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "试算不平衡！请检查账目。", "检查结果", JOptionPane.WARNING_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "检查失败: " + ex.getMessage());
            }
        });
    }

    public void refreshData() {
        try {
            List<Voucher> vouchers = dao.readVouchers();
            List<AccountSubject> subjects = dao.readAccountSubjects();
            List<OpeningBalance> balances = dao.readOpeningBalances();

            // 显示所有科目和当前余额
            pnlModel.setRowCount(0);
            for (AccountSubject s : subjects) {
                String code = s.getCode();
                double openAmt = 0;
                String dir = s.getDirection() != null ? s.getDirection() : "借";
                for (OpeningBalance ob : balances) {
                    if (ob.getAccountCode().equals(code)) {
                        openAmt = ob.getAmount();
                        if (ob.getDirection() != null) dir = ob.getDirection();
                        break;
                    }
                }
                double debitSum = vouchers.stream().filter(v -> code.equals(v.getAccountCode())).mapToDouble(Voucher::getDebitAmount).sum();
                double creditSum = vouchers.stream().filter(v -> code.equals(v.getAccountCode())).mapToDouble(Voucher::getCreditAmount).sum();

                double balance;
                if ("借".equals(dir)) balance = openAmt + debitSum - creditSum;
                else balance = openAmt + creditSum - debitSum;

                if (Math.abs(debitSum) > 0.001 || Math.abs(creditSum) > 0.001 || Math.abs(openAmt) > 0.001) {
                    pnlModel.addRow(new Object[]{
                        code, s.getName(),
                        String.format("%.2f", debitSum),
                        String.format("%.2f", creditSum),
                        String.format("%.2f", Math.abs(balance)),
                        balance >= 0 ? "借" : "贷"
                    });
                }
            }

            // 显示已结账的月份
            Set<Integer> closed = closingService.getClosedMonths();
            if (!closed.isEmpty()) {
                statusLabel.setText("已结账月份: " + closed.toString());
            } else {
                statusLabel.setText(" ");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载数据失败: " + ex.getMessage());
        }
    }
}
