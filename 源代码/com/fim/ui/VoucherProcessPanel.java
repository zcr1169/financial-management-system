package com.fim.ui;

import com.fim.dao.ExcelDAO;
import com.fim.model.*;
import com.fim.service.VoucherService;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

public class VoucherProcessPanel extends JPanel {
    private final ExcelDAO dao;
    private final VoucherService voucherService;
    private final User currentUser;

    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel infoLabel;
    private int currentKeyIndex = 0;
    private List<Voucher> allVouchers;
    private List<String> voucherKeys = new java.util.ArrayList<>();

    public VoucherProcessPanel(ExcelDAO dao, VoucherService voucherService, User currentUser) {
        this.dao = dao;
        this.voucherService = voucherService;
        this.currentUser = currentUser;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // 上面导航条
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        JButton prevBtn = new JButton("上一张");
        JButton nextBtn = new JButton("下一张");
        infoLabel = new JLabel(" ");
        navPanel.add(prevBtn);
        navPanel.add(nextBtn);
        navPanel.add(infoLabel);
        add(navPanel, BorderLayout.NORTH);

        // 凭证分录表格
        String[] cols = {"凭证类别", "编号", "日期", "摘要", "科目编码", "科目名称", "借方金额", "贷方金额", "制单", "审核", "出纳", "记账"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(30);
        for (int i = 0; i < cols.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(
                i == 3 ? 150 : i == 4 || i == 5 ? 100 : 65);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // 下面操作按钮
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

        if (currentUser.canReview()) {
            JButton reviewBtn = new JButton("审核");
            JButton unreviewBtn = new JButton("取消审核");
            btnPanel.add(reviewBtn);
            btnPanel.add(unreviewBtn);
            reviewBtn.addActionListener(e -> reviewCurrent());
            unreviewBtn.addActionListener(e -> unreviewCurrent());
        }

        if (currentUser.canSign()) {
            JButton signBtn = new JButton("出纳签字");
            JButton unsignBtn = new JButton("取消签字");
            btnPanel.add(signBtn);
            btnPanel.add(unsignBtn);
            signBtn.addActionListener(e -> signCurrent());
            unsignBtn.addActionListener(e -> unsignCurrent());
        }

        if (currentUser.canCreateVoucher()) {
            JButton editBtn = new JButton("修改");
            JButton delBtn = new JButton("删除");
            btnPanel.add(editBtn);
            btnPanel.add(delBtn);
            editBtn.addActionListener(e -> editCurrent());
            delBtn.addActionListener(e -> deleteCurrent());
        }

        if (currentUser.canPost()) {
            JButton postBtn = new JButton("批量记账");
            btnPanel.add(postBtn);
            postBtn.addActionListener(e -> postVouchers());
        }

        add(btnPanel, BorderLayout.SOUTH);

        prevBtn.addActionListener(e -> navigate(-1));
        nextBtn.addActionListener(e -> navigate(1));
    }

    public void refreshData() {
        try {
            allVouchers = voucherService.getAllVouchers();
            // 构建唯一的凭证键值，保持顺序
            voucherKeys.clear();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Voucher v : allVouchers) {
                String key = v.getType() + "|" + v.getNumber();
                if (seen.add(key)) {
                    voucherKeys.add(key);
                }
            }
            if (allVouchers.isEmpty()) {
                tableModel.setRowCount(0);
                infoLabel.setText("暂无凭证");
                return;
            }
            currentKeyIndex = 0;
            showCurrentVoucher();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载数据失败: " + ex.getMessage());
        }
    }

    private void navigate(int direction) {
        if (voucherKeys.isEmpty()) return;
        currentKeyIndex += direction;
        if (currentKeyIndex < 0) currentKeyIndex = 0;
        if (currentKeyIndex >= voucherKeys.size()) currentKeyIndex = voucherKeys.size() - 1;
        showCurrentVoucher();
    }

    private void showCurrentVoucher() {
        if (voucherKeys.isEmpty() || currentKeyIndex >= voucherKeys.size()) return;

        String key = voucherKeys.get(currentKeyIndex);
        String[] parts = key.split("\\|");
        String type = parts[0];
        int num = Integer.parseInt(parts[1]);

        tableModel.setRowCount(0);
        for (Voucher v : allVouchers) {
            if (v.getType().equals(type) && v.getNumber() == num) {
                tableModel.addRow(new Object[]{
                    v.getType(), v.getNumber(),
                    v.getDate() != null ? new SimpleDateFormat("yyyy年MM月dd日").format(v.getDate()) : "",
                    v.getDescription(), v.getAccountCode(), v.getAccountName(),
                    v.getDebitAmount() > 0 ? String.format("%.2f", v.getDebitAmount()) : "",
                    v.getCreditAmount() > 0 ? String.format("%.2f", v.getCreditAmount()) : "",
                    v.getMaker(), v.getReviewer(), v.getCashier(), v.getPoster()
                });
            }
        }
        infoLabel.setText("凭证 " + type + " " + num + "  [" + (currentKeyIndex + 1) + "/" + voucherKeys.size() + "]");
    }

    private String getCurrentType() {
        if (tableModel.getRowCount() == 0) return "";
        return (String) tableModel.getValueAt(0, 0);
    }

    private int getCurrentNumber() {
        if (tableModel.getRowCount() == 0) return 0;
        return (int) tableModel.getValueAt(0, 1);
    }

    private void reviewCurrent() {
        try {
            String type = getCurrentType();
            int num = getCurrentNumber();
            if (type.isEmpty()) { JOptionPane.showMessageDialog(this, "无凭证！"); return; }
            voucherService.reviewVoucher(type, num, currentUser);
            JOptionPane.showMessageDialog(this, "审核成功！");
            refreshData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void unreviewCurrent() {
        try {
            String type = getCurrentType();
            int num = getCurrentNumber();
            if (type.isEmpty()) { JOptionPane.showMessageDialog(this, "无凭证！"); return; }
            voucherService.unreviewVoucher(type, num);
            JOptionPane.showMessageDialog(this, "取消审核成功！");
            refreshData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void signCurrent() {
        try {
            String type = getCurrentType();
            int num = getCurrentNumber();
            if (type.isEmpty()) { JOptionPane.showMessageDialog(this, "无凭证！"); return; }
            voucherService.signVoucher(type, num, currentUser);
            JOptionPane.showMessageDialog(this, "签字成功！");
            refreshData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void unsignCurrent() {
        try {
            String type = getCurrentType();
            int num = getCurrentNumber();
            if (type.isEmpty()) { JOptionPane.showMessageDialog(this, "无凭证！"); return; }
            voucherService.unsignVoucher(type, num);
            JOptionPane.showMessageDialog(this, "取消签字成功！");
            refreshData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editCurrent() {
        try {
            String type = getCurrentType();
            int num = getCurrentNumber();
            if (type.isEmpty()) { JOptionPane.showMessageDialog(this, "无凭证！"); return; }

            // 检查是不是制单人
            boolean canEdit = false;
            for (Voucher v : allVouchers) {
                if (v.getType().equals(type) && v.getNumber() == num) {
                    if (v.isLocked()) {
                        JOptionPane.showMessageDialog(this, "凭证已审核/签字/记账，禁止修改！");
                        return;
                    }
                    if (v.getMaker().equals(currentUser.getName())) canEdit = true;
                }
            }
            if (!canEdit) {
                JOptionPane.showMessageDialog(this, "仅制单人可修改凭证！");
                return;
            }

            // 修改功能：提示用户删了重填
            JOptionPane.showMessageDialog(this, "修改功能：请删除后重新填制。\n（或切换到填制凭证界面）");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        }
    }

    private void deleteCurrent() {
        try {
            String type = getCurrentType();
            int num = getCurrentNumber();
            if (type.isEmpty()) { JOptionPane.showMessageDialog(this, "无凭证！"); return; }
            voucherService.deleteVoucher(type, num, currentUser);
            JOptionPane.showMessageDialog(this, "删除成功！");
            refreshData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void postVouchers() {
        try {
            int confirm = JOptionPane.showConfirmDialog(this,
                "确定要批量记账所有已审核凭证吗？", "确认记账", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            int count = voucherService.postVouchers(currentUser);
            JOptionPane.showMessageDialog(this, "记账完成！共记账 " + count + " 张凭证。");
            refreshData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}
