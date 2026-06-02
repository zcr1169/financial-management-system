package com.fim.ui;

import com.fim.dao.ExcelDAO;
import com.fim.model.*;
import com.fim.service.AccountService;
import com.fim.service.ExportService;
import com.fim.service.ReportService;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ReportPanel extends JPanel {
    private final ExcelDAO dao;
    private final ReportService reportService;
    private final AccountService accountService;
    private final ExportService exportService;
    private final User currentUser;

    private JTable reportTable;
    private DefaultTableModel reportModel;
    private JComboBox<String> reportTypeBox;
    private JComboBox<String> accountCodeCombo;
    private JLabel titleLabel;
    private List<String[]> lastReportData;

    public ReportPanel(ExcelDAO dao, ReportService reportService, AccountService accountService, User currentUser) {
        this.dao = dao;
        this.reportService = reportService;
        this.accountService = accountService;
        this.exportService = new ExportService();
        this.currentUser = currentUser;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // 上面：报表选择
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topPanel.add(new JLabel("报表类型:"));
        reportTypeBox = new JComboBox<>(new String[]{
            "发生额及余额表", "科目总账", "科目明细账", "现金日记账", "银行日记账", "资产负债表", "利润表"
        });
        topPanel.add(reportTypeBox);

        topPanel.add(new JLabel("科目:"));
        accountCodeCombo = new JComboBox<>();
        accountCodeCombo.setEditable(true);
        accountCodeCombo.setPreferredSize(new Dimension(120, 25));
        topPanel.add(accountCodeCombo);

        JButton queryBtn = new JButton("查询");
        topPanel.add(queryBtn);

        JButton exportExcelBtn = new JButton("导出Excel");
        topPanel.add(exportExcelBtn);

        JButton exportPdfBtn = new JButton("导出PDF");
        topPanel.add(exportPdfBtn);
        add(topPanel, BorderLayout.NORTH);

        // 标题
        titleLabel = new JLabel(" ", JLabel.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));

        // 报表表格
        reportModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        reportTable = new JTable(reportModel);
        reportTable.setRowHeight(30);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(titleLabel, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(reportTable), BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // 绑定事件
        queryBtn.addActionListener(e -> executeQuery());
        reportTypeBox.addActionListener(e -> {
            String type = (String) reportTypeBox.getSelectedItem();
            accountCodeCombo.setEnabled(!type.equals("现金日记账") && !type.equals("银行日记账")
                && !type.equals("发生额及余额表") && !type.equals("资产负债表") && !type.equals("利润表"));
        });
        exportExcelBtn.addActionListener(e -> exportToExcel());
        exportPdfBtn.addActionListener(e -> exportToPDF());
    }

    public void refreshData() {
        try {
            accountCodeCombo.removeAllItems();
            List<AccountSubject> subjects = accountService.getAllSubjects();
            for (AccountSubject s : subjects) {
                accountCodeCombo.addItem(s.getCode() + " " + s.getName());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载数据失败: " + ex.getMessage());
        }
    }

    private void executeQuery() {
        String type = (String) reportTypeBox.getSelectedItem();
        try {
            List<String[]> data = null;
            switch (type) {
                case "发生额及余额表":
                    data = reportService.getTrialBalance();
                    break;
                case "科目总账":
                    data = reportService.getGeneralLedger(getSelectedAccountCode());
                    break;
                case "科目明细账":
                    data = reportService.getSubLedger(getSelectedAccountCode());
                    break;
                case "现金日记账":
                    data = reportService.getCashJournal();
                    break;
                case "银行日记账":
                    data = reportService.getBankJournal();
                    break;
                case "资产负债表":
                    data = reportService.getBalanceSheet();
                    break;
                case "利润表":
                    data = reportService.getProfitStatement();
                    break;
            }

            if (data != null && !data.isEmpty()) {
                lastReportData = data;
                String[] headers = data.get(0);
                reportModel.setColumnIdentifiers(headers);
                reportModel.setRowCount(0);
                for (int i = 1; i < data.size(); i++) {
                    reportModel.addRow(data.get(i));
                }
                titleLabel.setText(type);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "查询失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportToExcel() {
        if (lastReportData == null || lastReportData.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先查询报表！");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("报表导出.xlsx"));
        chooser.setDialogTitle("导出Excel");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String path = chooser.getSelectedFile().getAbsolutePath();
                if (!path.toLowerCase().endsWith(".xlsx")) path += ".xlsx";
                exportService.exportToExcel(lastReportData, titleLabel.getText(), new File(path));
                JOptionPane.showMessageDialog(this, "导出成功！\n" + path);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "导出失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportToPDF() {
        if (lastReportData == null || lastReportData.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先查询报表！");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("报表导出.pdf"));
        chooser.setDialogTitle("导出PDF");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String path = chooser.getSelectedFile().getAbsolutePath();
                if (!path.toLowerCase().endsWith(".pdf")) path += ".pdf";
                exportService.exportToPDF(lastReportData, titleLabel.getText(), new File(path));
                JOptionPane.showMessageDialog(this, "导出成功！\n" + path);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "导出失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private String getSelectedAccountCode() {
        String sel = (String) accountCodeCombo.getSelectedItem();
        if (sel == null || sel.isEmpty()) return "";
        int spaceIdx = sel.indexOf(' ');
        return spaceIdx > 0 ? sel.substring(0, spaceIdx) : sel;
    }
}
