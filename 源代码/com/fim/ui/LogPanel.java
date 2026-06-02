package com.fim.ui;

import com.fim.model.OperationLog;
import com.fim.model.User;
import com.fim.service.LogService;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public class LogPanel extends JPanel {
    private final LogService logService;
    private final User currentUser;
    private JTable table;
    private DefaultTableModel tableModel;

    public LogPanel(LogService logService, User currentUser) {
        this.logService = logService;
        this.currentUser = currentUser;
        initUI();
        refreshData();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("刷新");
        JButton exportBtn = new JButton("导出TXT");
        JButton clearBtn = new JButton("清空日志");

        btnPanel.add(refreshBtn);
        btnPanel.add(exportBtn);
        btnPanel.add(clearBtn);
        add(btnPanel, BorderLayout.NORTH);

        String[] cols = {"时间", "操作用户", "操作类型", "操作详情"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(30);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(400);

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        refreshBtn.addActionListener(e -> refreshData());
        exportBtn.addActionListener(e -> exportTxt());
        clearBtn.addActionListener(e -> clearLogs());
    }

    public void refreshData() {
        try {
            // 先把缓存里的日志刷到Excel
            logService.flush();
            List<OperationLog> logs = logService.getAllLogs();
            tableModel.setRowCount(0);
            for (OperationLog log : logs) {
                tableModel.addRow(new Object[]{
                    log.getTimeStr(), log.getUser(), log.getType(), log.getDetail()
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加载日志失败: " + ex.getMessage());
        }
    }

    private void exportTxt() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("操作日志.txt"));
        chooser.setDialogTitle("导出操作日志");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String path = chooser.getSelectedFile().getAbsolutePath();
                if (!path.toLowerCase().endsWith(".txt")) path += ".txt";
                logService.flush();
                logService.exportToTxt(new File(path));
                JOptionPane.showMessageDialog(this, "导出成功！\n" + path);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "导出失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void clearLogs() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "确定要清空所有操作日志吗？此操作不可恢复！",
            "确认清空", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            logService.clearLogs();
            refreshData();
            JOptionPane.showMessageDialog(this, "日志已清空！");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "清空失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}
