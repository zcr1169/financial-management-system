package com.fim.ui;

import com.fim.dao.ExcelDAO;
import com.fim.model.AccountSubject;
import com.fim.model.User;
import com.fim.service.AccountService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;

public class AccountManagePanel extends JPanel {
    private final ExcelDAO dao;
    private final AccountService accountService;
    private final User currentUser;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JLabel statusLabel;

    public AccountManagePanel(ExcelDAO dao, AccountService accountService, User currentUser) {
        this.dao = dao;
        this.accountService = accountService;
        this.currentUser = currentUser;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // 上面工具栏
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        if (currentUser.canManageAccounts()) {
            JButton addBtn = new JButton("新增科目");
            JButton editBtn = new JButton("修改科目");
            JButton delBtn = new JButton("删除科目");
            btnPanel.add(addBtn);
            btnPanel.add(editBtn);
            btnPanel.add(delBtn);

            addBtn.addActionListener(e -> showAddDialog());
            editBtn.addActionListener(e -> showEditDialog());
            delBtn.addActionListener(e -> deleteSubject());
        }

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        searchField = new JTextField(15);
        JButton searchBtn = new JButton("搜索");
        searchPanel.add(new JLabel("科目编码/名称:"));
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        searchBtn.addActionListener(e -> searchSubject());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { searchSubject(); }
            @Override public void removeUpdate(DocumentEvent e) { searchSubject(); }
            @Override public void changedUpdate(DocumentEvent e) { searchSubject(); }
        });

        topPanel.add(btnPanel, BorderLayout.WEST);
        topPanel.add(searchPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // 表格
        String[] cols = {"科目编码", "科目名称", "辅助账类型", "余额方向", "银行账", "日记账"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(30);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        table.getColumnModel().getColumn(4).setPreferredWidth(60);
        table.getColumnModel().getColumn(5).setPreferredWidth(60);

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel("共 0 个科目");
        add(statusLabel, BorderLayout.SOUTH);
    }

    public void refreshData() {
        try {
            List<AccountSubject> list = accountService.getAllSubjects();
            tableModel.setRowCount(0);
            for (AccountSubject a : list) {
                tableModel.addRow(new Object[]{
                    a.getCode(), a.getName(),
                    a.getAuxType() != null ? a.getAuxType() : "",
                    a.getDirection() != null ? a.getDirection() : "",
                    a.isBankAccount() ? "Y" : "",
                    a.isJournal() ? "Y" : ""
                });
            }
            statusLabel.setText("共 " + list.size() + " 个科目");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载数据失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void searchSubject() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            refreshData();
            return;
        }
        try {
            List<AccountSubject> list = accountService.searchByName(keyword);
            // 也按编码搜一下
            AccountSubject byCode = accountService.findByCode(keyword);
            if (byCode != null && !list.contains(byCode)) {
                list.add(0, byCode);
            }
            tableModel.setRowCount(0);
            for (AccountSubject a : list) {
                tableModel.addRow(new Object[]{
                    a.getCode(), a.getName(),
                    a.getAuxType() != null ? a.getAuxType() : "",
                    a.getDirection() != null ? a.getDirection() : "",
                    a.isBankAccount() ? "Y" : "",
                    a.isJournal() ? "Y" : ""
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "搜索失败: " + ex.getMessage());
        }
    }

    private void showAddDialog() {
        JDialog dialog = createSubjectDialog("新增会计科目", null);
        dialog.setVisible(true);
        if (dialog.getName() != null && dialog.getName().equals("saved")) {
            refreshData();
        }
    }

    private void showEditDialog() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要修改的科目！");
            return;
        }
        String code = (String) tableModel.getValueAt(row, 0);
        try {
            AccountSubject subject = accountService.findByCode(code);
            if (subject == null) {
                JOptionPane.showMessageDialog(this, "科目不存在！");
                return;
            }
            JDialog dialog = createSubjectDialog("修改会计科目", subject);
            dialog.setVisible(true);
            if (dialog.getName() != null && dialog.getName().equals("saved")) {
                refreshData();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "编辑失败: " + ex.getMessage());
        }
    }

    private void deleteSubject() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的科目！");
            return;
        }
        String code = (String) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);
        int confirm = JOptionPane.showConfirmDialog(this,
            "确定要删除科目 " + code + " " + name + " 吗？",
            "确认删除", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            accountService.deleteSubject(code);
            refreshData();
            JOptionPane.showMessageDialog(this, "删除成功！");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "删除失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JDialog createSubjectDialog(String title, AccountSubject existing) {
        JDialog dialog = new JDialog((JFrame)SwingUtilities.getWindowAncestor(this), title, true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField codeField = new JTextField(15);
        JTextField nameField = new JTextField(15);
        JComboBox<String> auxBox = new JComboBox<>(new String[]{"", "客户往来", "供应商往来", "个人往来", "部门核算", "项目核算"});
        auxBox.setEditable(true);
        JComboBox<String> dirBox = new JComboBox<>(new String[]{"借", "贷"});
        JCheckBox bankBox = new JCheckBox();
        JCheckBox journalBox = new JCheckBox();

        if (existing != null) {
            codeField.setText(existing.getCode());
            codeField.setEditable(false);
            nameField.setText(existing.getName());
            String auxType = existing.getAuxType() != null ? existing.getAuxType() : "";
            auxBox.setSelectedItem(auxType);
            dirBox.setSelectedItem(existing.getDirection());
            bankBox.setSelected(existing.isBankAccount());
            journalBox.setSelected(existing.isJournal());
        } else {
            codeField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    autoDetectDirection(codeField.getText().trim(), dirBox);
                }
            });
        }

        addField(dialog, gbc, "科目编码:", codeField, 0);
        addField(dialog, gbc, "科目名称:", nameField, 1);
        addField(dialog, gbc, "辅助账类型:", auxBox, 2);
        addField(dialog, gbc, "余额方向:", dirBox, 3);
        addField(dialog, gbc, "银行账:", bankBox, 4);
        addField(dialog, gbc, "日记账:", journalBox, 5);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton saveBtn = new JButton("保存");
        JButton cancelBtn = new JButton("取消");
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        dialog.add(btnPanel, gbc);

        saveBtn.addActionListener(e -> {
            try {
                String code = codeField.getText().trim();
                String name = nameField.getText().trim();
                if (code.isEmpty() || name.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "科目编码和名称不能为空！");
                    return;
                }
                AccountSubject s = new AccountSubject();
                s.setCode(code);
                s.setName(name);
                s.setAuxType((String) auxBox.getSelectedItem());
                s.setDirection((String) dirBox.getSelectedItem());
                s.setBankAccount(bankBox.isSelected());
                s.setJournal(journalBox.isSelected());

                if (existing != null) {
                    accountService.updateSubject(existing.getCode(), s);
                } else {
                    accountService.addSubject(s);
                }
                dialog.setName("saved");
                dialog.setVisible(false);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancelBtn.addActionListener(e -> dialog.setVisible(false));

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        return dialog;
    }

    private void autoDetectDirection(String code, JComboBox<String> dirBox) {
        if (code.isEmpty()) return;
        char first = code.charAt(0);
        switch (first) {
            case '1': case '5': dirBox.setSelectedItem("借"); break;
            case '2': case '3': case '4': dirBox.setSelectedItem("贷"); break;
            case '6':
                // 60xx-63xx = income (贷), 64xx-69xx = expense (借)
                if (code.length() >= 2 && code.charAt(1) >= '0' && code.charAt(1) <= '3') {
                    dirBox.setSelectedItem("贷");
                } else {
                    dirBox.setSelectedItem("借");
                }
                break;
        }
    }

    private void addField(JDialog dialog, GridBagConstraints gbc, String label, JComponent comp, int row) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        dialog.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        dialog.add(comp, gbc);
    }
}
