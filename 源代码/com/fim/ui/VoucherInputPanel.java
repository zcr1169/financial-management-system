package com.fim.ui;

import com.fim.dao.ExcelDAO;
import com.fim.model.*;
import com.fim.service.AccountService;
import com.fim.service.VoucherService;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.Date;
import java.util.List;

public class VoucherInputPanel extends JPanel {
    private final ExcelDAO dao;
    private final VoucherService voucherService;
    private final AccountService accountService;
    private final User currentUser;

    private JComboBox<String> typeBox;
    private JTextField numberField, dateField;
    private JComboBox<String> descCombo;
    private JTable entryTable;
    private DefaultTableModel entryModel;
    private JLabel debitTotalLabel, creditTotalLabel, balanceLabel;

    private List<AccountSubject> allSubjects;
    private boolean tableLock;

    public VoucherInputPanel(ExcelDAO dao, VoucherService voucherService, AccountService accountService, User currentUser) {
        this.dao = dao;
        this.voucherService = voucherService;
        this.accountService = accountService;
        this.currentUser = currentUser;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // 上面：凭证头信息
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        headerPanel.add(new JLabel("凭证类别:"));
        typeBox = new JComboBox<>(new String[]{"收", "付", "转"});
        typeBox.addActionListener(e -> generateNumber());
        headerPanel.add(typeBox);

        headerPanel.add(new JLabel("编号:"));
        numberField = new JTextField(5);
        numberField.setEditable(false);
        headerPanel.add(numberField);

        headerPanel.add(new JLabel("日期:"));
        dateField = new JTextField(10);
        dateField.setText(new java.text.SimpleDateFormat("yyyy年MM月dd日").format(new Date()));
        headerPanel.add(dateField);

        headerPanel.add(new JLabel("摘要:"));
        descCombo = new JComboBox<>();
        descCombo.setEditable(true);
        descCombo.setPreferredSize(new Dimension(200, 25));
        headerPanel.add(descCombo);

        add(headerPanel, BorderLayout.NORTH);

        // 中间：分录表格
        String[] cols = {"科目编码", "科目名称", "借方金额", "贷方金额"};
        entryModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 0 || col == 1 || col == 2 || col == 3;
            }
        };
        entryTable = new JTable(entryModel);
        entryTable.setRowHeight(30);
        entryTable.putClientProperty("terminateEditOnFocusLost", true);
        entryTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        entryTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        entryTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        entryTable.getColumnModel().getColumn(3).setPreferredWidth(120);

        // 编码列：可以输编码、输名称、或者下拉选
        JComboBox<String> codeCombo = new JComboBox<>();
        codeCombo.setEditable(true);
        entryTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(codeCombo) {
            @Override
            public Object getCellEditorValue() {
                JTextField field = (JTextField) codeCombo.getEditor().getEditorComponent();
                String text = field.getText().trim();
                if (text.isEmpty()) return "";
                int sp = text.indexOf(' ');
                String code = sp > 0 ? text.substring(0, sp) : text;
                AccountSubject s = findSubjectByCode(code);
                return s != null ? s.getCode() : (findSubjectByName(text) != null ? findSubjectByName(text).getCode() : text);
            }
        });

        // 名称列：可以输名字或者下拉选
        JComboBox<String> nameCombo = new JComboBox<>();
        nameCombo.setEditable(true);
        entryTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(nameCombo) {
            @Override
            public Object getCellEditorValue() {
                JTextField field = (JTextField) nameCombo.getEditor().getEditorComponent();
                return field.getText().trim();
            }
        });

        // 表格监听器：编码和名称互相自动填充，用锁防止死循环
        entryModel.addTableModelListener(e -> {
            if (tableLock) return;
            int row = e.getFirstRow();
            int col = e.getColumn();
            if (row < 0 || row >= entryModel.getRowCount()) return;
            if (col != 0 && col != 1) {
                updateTotals();
                return;
            }

            tableLock = true;
            try {
                if (col == 0) {
                    String code = (String) entryModel.getValueAt(row, 0);
                    AccountSubject s = (code != null && !code.isEmpty()) ? findSubjectByCode(code) : null;
                    entryModel.setValueAt(s != null ? s.getName() : "", row, 1);
                } else {
                    String name = (String) entryModel.getValueAt(row, 1);
                    AccountSubject s = (name != null && !name.isEmpty()) ? findSubjectByName(name) : null;
                    entryModel.setValueAt(s != null ? s.getCode() : "", row, 0);
                }
            } finally {
                tableLock = false;
            }
            updateTotals();
        });

        JScrollPane scrollPane = new JScrollPane(entryTable);
        scrollPane.setPreferredSize(new Dimension(700, 250));
        add(scrollPane, BorderLayout.CENTER);

        // 下面：合计和按钮
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        JPanel totalsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        debitTotalLabel = new JLabel("借方合计: 0.00");
        creditTotalLabel = new JLabel("贷方合计: 0.00");
        balanceLabel = new JLabel("差额: 0.00");
        totalsPanel.add(debitTotalLabel);
        totalsPanel.add(creditTotalLabel);
        totalsPanel.add(balanceLabel);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton addRowBtn = new JButton("添加行");
        JButton delRowBtn = new JButton("删除行");
        JButton saveBtn = new JButton("保存凭证");
        JButton clearBtn = new JButton("清空");
        btnPanel.add(addRowBtn);
        btnPanel.add(delRowBtn);
        btnPanel.add(saveBtn);
        btnPanel.add(clearBtn);

        bottomPanel.add(totalsPanel, BorderLayout.WEST);
        bottomPanel.add(btnPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        addRowBtn.addActionListener(e -> entryModel.addRow(new Object[]{"", "", 0.0, 0.0}));
        delRowBtn.addActionListener(e -> {
            int sel = entryTable.getSelectedRow();
            if (sel >= 0 && entryModel.getRowCount() > 1) entryModel.removeRow(sel);
            updateTotals();
        });
        saveBtn.addActionListener(e -> saveVoucher());
        clearBtn.addActionListener(e -> clearForm());
        clearForm();
    }

    public void refreshData() {
        try {
            allSubjects = accountService.getAllSubjects();
            List<String> descs = voucherService.getHistoryDescriptions();
            descCombo.removeAllItems();
            for (String d : descs) descCombo.addItem(d);

            List<String> codeItems = new ArrayList<>();
            List<String> nameItems = new ArrayList<>();
            for (AccountSubject s : allSubjects) {
                codeItems.add(s.getCode() + " " + s.getName());
                nameItems.add(s.getName());
            }

            // 更新编码下拉框
            JComboBox<String> codeCb = getEditorCombo(0);
            if (codeCb != null) {
                codeCb.removeAllItems();
                for (String item : codeItems) codeCb.addItem(item);
            }
            // 更新名称下拉框
            JComboBox<String> nameCb = getEditorCombo(1);
            if (nameCb != null) {
                nameCb.removeAllItems();
                for (String item : nameItems) nameCb.addItem(item);
            }

            generateNumber();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载数据失败: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private JComboBox<String> getEditorCombo(int col) {
        TableCellEditor editor = entryTable.getColumnModel().getColumn(col).getCellEditor();
        if (editor instanceof DefaultCellEditor) {
            return (JComboBox<String>) ((DefaultCellEditor) editor).getComponent();
        }
        return null;
    }

    private void generateNumber() {
        try {
            String type = (String) typeBox.getSelectedItem();
            numberField.setText(String.valueOf(voucherService.getNextNumber(type)));
        } catch (Exception ex) {
            numberField.setText("1");
        }
    }

    private void updateTotals() {
        double debTotal = 0, credTotal = 0;
        for (int i = 0; i < entryModel.getRowCount(); i++) {
            debTotal += parseAmt(entryModel.getValueAt(i, 2));
            credTotal += parseAmt(entryModel.getValueAt(i, 3));
        }
        debitTotalLabel.setText(String.format("借方合计: %.2f", debTotal));
        creditTotalLabel.setText(String.format("贷方合计: %.2f", credTotal));
        double diff = debTotal - credTotal;
        balanceLabel.setText(String.format("差额: %.2f", diff));
        balanceLabel.setForeground(Math.abs(diff) < 0.01 ? new Color(0, 128, 0) : Color.RED);
    }

    private void saveVoucher() {
        try {
            String type = (String) typeBox.getSelectedItem();
            int number = Integer.parseInt(numberField.getText());
            Date date = new java.text.SimpleDateFormat("yyyy年MM月dd日").parse(dateField.getText());
            String desc = descCombo.getEditor().getItem().toString();
            if (desc.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请输入摘要！");
                return;
            }
            List<Voucher> entries = new ArrayList<>();
            for (int i = 0; i < entryModel.getRowCount(); i++) {
                String code = (String) entryModel.getValueAt(i, 0);
                if (code == null || code.isEmpty()) continue;
                Voucher v = new Voucher();
                v.setType(type); v.setNumber(number); v.setDate(date); v.setDescription(desc);
                v.setAccountCode(code);
                v.setAccountName(findSubjectName(code));
                v.setDebitAmount(parseAmt(entryModel.getValueAt(i, 2)));
                v.setCreditAmount(parseAmt(entryModel.getValueAt(i, 3)));
                entries.add(v);
            }
            voucherService.saveVoucher(entries, currentUser.getName(), currentUser);
            JOptionPane.showMessageDialog(this, "凭证保存成功！");
            clearForm();
            generateNumber();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearForm() {
        tableLock = true;
        entryModel.setRowCount(0);
        entryModel.addRow(new Object[]{"", "", 0.0, 0.0});
        entryModel.addRow(new Object[]{"", "", 0.0, 0.0});
        tableLock = false;
        updateTotals();
    }

    private double parseAmt(Object val) {
        if (val == null) return 0;
        try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private AccountSubject findSubjectByCode(String code) {
        if (allSubjects == null) return null;
        for (AccountSubject s : allSubjects) {
            if (s.getCode().equals(code)) return s;
        }
        return null;
    }

    private AccountSubject findSubjectByName(String name) {
        if (allSubjects == null) return null;
        for (AccountSubject s : allSubjects) {
            if (s.getName().equals(name)) return s;
        }
        return null;
    }

    private String findSubjectName(String code) {
        AccountSubject s = findSubjectByCode(code);
        return s != null ? s.getName() : code;
    }
}
