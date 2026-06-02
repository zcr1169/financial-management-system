package com.fim.ui;

import com.fim.dao.ExcelDAO;
import com.fim.model.User;
import com.fim.service.AuthService;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

public class UserManagePanel extends JPanel {
    private final ExcelDAO dao;
    private final AuthService authService;
    private final User currentUser;
    private JTable table;
    private DefaultTableModel tableModel;
    private List<User> userList;

    public UserManagePanel(ExcelDAO dao, AuthService authService, User currentUser) {
        this.dao = dao;
        this.authService = authService;
        this.currentUser = currentUser;
        initUI();
        refreshData();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("新增用户");
        JButton editBtn = new JButton("修改用户");
        JButton delBtn = new JButton("删除用户");
        JButton unlockBtn = new JButton("解锁账号");
        JButton refreshBtn = new JButton("刷新");
        btnPanel.add(addBtn);
        btnPanel.add(editBtn);
        btnPanel.add(delBtn);
        btnPanel.add(unlockBtn);
        btnPanel.add(refreshBtn);
        add(btnPanel, BorderLayout.NORTH);

        String[] cols = {"账号", "密码", "角色", "姓名", "失败次数", "状态"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(30);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(60);
        table.getColumnModel().getColumn(5).setPreferredWidth(60);

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        addBtn.addActionListener(e -> showUserDialog("新增用户", null));
        editBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(this, "请选择用户！"); return; }
            String account = (String) tableModel.getValueAt(row, 0);
            User u = userList.stream().filter(usr -> usr.getAccount().equals(account)).findFirst().orElse(null);
            if (u == null) return;
            showUserDialog("修改用户", u);
        });
        delBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(this, "请选择用户！"); return; }
            String account = (String) tableModel.getValueAt(row, 0);
            try {
                authService.deleteUser(account);
                refreshData();
                JOptionPane.showMessageDialog(this, "删除成功！");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        unlockBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(this, "请选择要解锁的账号！"); return; }
            String account = (String) tableModel.getValueAt(row, 0);
            String status = (String) tableModel.getValueAt(row, 5);
            if (!"锁定".equals(status)) {
                JOptionPane.showMessageDialog(this, "该账号未被锁定，无需解锁！");
                return;
            }
            try {
                authService.unlockUser(account);
                refreshData();
                JOptionPane.showMessageDialog(this, "账号 " + account + " 已解锁！");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        refreshBtn.addActionListener(e -> refreshData());
    }

    public void refreshData() {
        try {
            userList = authService.getAllUsers();
            tableModel.setRowCount(0);
            for (User u : userList) {
                tableModel.addRow(new Object[]{
                    u.getAccount(), u.getPassword(), u.getRole(), u.getName(),
                    u.getFailedAttempts(),
                    u.isLocked() ? "锁定" : "正常"
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载失败: " + ex.getMessage());
        }
    }

    private void showUserDialog(String title, User existing) {
        JDialog dialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), title, true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField accountField = new JTextField(15);
        JPasswordField pwdField = new JPasswordField(15);
        JComboBox<String> roleBox = new JComboBox<>(new String[]{
            User.ROLE_ADMIN, User.ROLE_MANAGER, User.ROLE_ACCOUNTANT, User.ROLE_CASHIER, User.ROLE_NORMAL
        });
        JTextField nameField = new JTextField(15);

        if (existing != null) {
            accountField.setText(existing.getAccount());
            accountField.setEditable(false);
            pwdField.setText(existing.getPassword());
            roleBox.setSelectedItem(existing.getRole());
            nameField.setText(existing.getName());
        }

        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("账号:"), gbc);
        gbc.gridx = 1; dialog.add(accountField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("密码:"), gbc);
        gbc.gridx = 1; dialog.add(pwdField, gbc);
        gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("角色:"), gbc);
        gbc.gridx = 1; dialog.add(roleBox, gbc);
        gbc.gridx = 0; gbc.gridy = 3; dialog.add(new JLabel("姓名:"), gbc);
        gbc.gridx = 1; dialog.add(nameField, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton saveBtn = new JButton("保存");
        JButton cancelBtn = new JButton("取消");
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        dialog.add(btnPanel, gbc);

        saveBtn.addActionListener(e -> {
            try {
                String account = accountField.getText().trim();
                String pwd = new String(pwdField.getPassword());
                String role = (String) roleBox.getSelectedItem();
                String name = nameField.getText().trim();
                if (account.isEmpty()) { JOptionPane.showMessageDialog(dialog, "账号不能为空！"); return; }

                User u = new User(account, pwd, role, name);
                if (existing != null) {
                    authService.updateUser(u);
                } else {
                    authService.addUser(u);
                }
                dialog.setVisible(false);
                refreshData();
                JOptionPane.showMessageDialog(this, "保存成功！");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancelBtn.addActionListener(e -> dialog.setVisible(false));

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
}
