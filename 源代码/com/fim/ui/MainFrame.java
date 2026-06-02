package com.fim.ui;

import com.fim.dao.ExcelDAO;
import com.fim.model.*;
import com.fim.service.*;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class MainFrame extends JFrame {
    private final String dataFilePath;
    private final ExcelDAO dao;
    private final AuthService authService;
    private final AccountService accountService;
    private final VoucherService voucherService;
    private final ReportService reportService;
    private final ClosingService closingService;
    private final LogService logService;
    private final User currentUser;

    private JPanel contentPanel;
    private CardLayout cardLayout;
    private JLabel userInfoLabel;
    private JLabel dateLabel;
    private JLabel pathLabel;
    private Image backgroundImage;

    private AccountManagePanel accountPanel;
    private BalanceManagePanel balancePanel;
    private VoucherInputPanel voucherInputPanel;
    private VoucherProcessPanel voucherProcessPanel;
    private PeriodEndPanel periodEndPanel;
    private ReportPanel reportPanel;
    private UserManagePanel userManagePanel;
    private LogPanel logPanel;

    // 看板标签
    private JLabel cashBalanceLabel;
    private JLabel bankBalanceLabel;

    // 自动备份
    private ScheduledExecutorService backupScheduler;

    public MainFrame(String dataFilePath, ExcelDAO dao, AuthService authService, User user) {
        this.dataFilePath = dataFilePath;
        this.dao = dao;
        this.authService = authService;
        this.currentUser = user;

        this.logService = new LogService(dao);
        this.authService.setLogService(logService);
        this.accountService = new AccountService(dao);
        this.accountService.setLogService(logService);
        this.voucherService = new VoucherService(dao);
        this.voucherService.setLogService(logService);
        this.reportService = new ReportService(dao);
        this.closingService = new ClosingService(dao);

        loadBackgroundImage();
        initUI();
        updateStatusBar();
        startAutoBackup();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!balancePanel.trySaveOnExit()) return;
                doShutdown();
                dispose();
                System.exit(0);
            }
        });
    }

    private void loadBackgroundImage() {
        File dir = new File(dataFilePath).getParentFile();
        File imgFile = new File(dir, "f1e41e9bc2cfd1d0fe9adc9cebd8e241.jpg");
        if (imgFile.exists()) {
            backgroundImage = new ImageIcon(imgFile.getAbsolutePath()).getImage();
        }
    }

    private void initUI() {
        setTitle("241001209周宸冉 - " + currentUser.getRole() + ": " + currentUser.getName());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1100, 760);
        setMinimumSize(new Dimension(900, 650));

        setJMenuBar(createMenuBar());

        setLayout(new BorderLayout());
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);

        accountPanel = new AccountManagePanel(dao, accountService, currentUser);
        balancePanel = new BalanceManagePanel(dao, accountService, currentUser);
        voucherInputPanel = new VoucherInputPanel(dao, voucherService, accountService, currentUser);
        voucherProcessPanel = new VoucherProcessPanel(dao, voucherService, currentUser);
        periodEndPanel = new PeriodEndPanel(dao, voucherService, closingService, currentUser);
        reportPanel = new ReportPanel(dao, reportService, accountService, currentUser);

        contentPanel.add(createHomePanel(), "home");
        contentPanel.add(accountPanel, "accounts");
        contentPanel.add(balancePanel, "balances");
        contentPanel.add(voucherInputPanel, "voucherInput");
        contentPanel.add(voucherProcessPanel, "voucherProcess");
        contentPanel.add(periodEndPanel, "periodEnd");
        contentPanel.add(reportPanel, "reports");

        add(contentPanel, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        cardLayout.show(contentPanel, "home");
    }

    // ==================== Home Panel with Dashboard ====================

    private JPanel createHomePanel() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                    g.setColor(new Color(255, 255, 255, 185));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 0);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 0;

        // 看板那一行
        JPanel dashRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 0));
        dashRow.setOpaque(false);
        dashRow.add(createDashboardCard("库存现金余额", cashBalanceLabel = new JLabel("加载中...", JLabel.CENTER),
            new Color(41, 128, 185)));
        dashRow.add(createDashboardCard("银行存款余额", bankBalanceLabel = new JLabel("加载中...", JLabel.CENTER),
            new Color(39, 174, 96)));
        gbc.gridy = 0;
        panel.add(dashRow, gbc);

        // 间距
        gbc.gridy = 1;
        panel.add(Box.createVerticalStrut(10), gbc);

        // 导航卡片行
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
        row1.setOpaque(false);
        row1.add(createNavCard("会计科目管理", "管理会计科目编码、名称\n及辅助账类型配置", new Color(41, 128, 185), "accounts"));
        row1.add(createNavCard("期初余额管理", "录入及维护各科目\n期初余额数据", new Color(39, 174, 96), "balances"));
        if (currentUser.canCreateVoucher()) {
            row1.add(createNavCard("填制凭证", "录入收/付/转三类\n会计记账凭证", new Color(211, 84, 0), "voucherInput"));
        }
        gbc.gridy = 2;
        panel.add(row1, gbc);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
        row2.setOpaque(false);
        if (currentUser.canAccessVoucherProcess()) {
            row2.add(createNavCard("凭证处理", "审核、签字、记账\n等凭证流程处理", new Color(142, 68, 173), "voucherProcess"));
        }
        if (currentUser.canClosePeriod()) {
            row2.add(createNavCard("期末处理", "月末损益结转\n及期末结账操作", new Color(192, 57, 43), "periodEnd"));
        }
        row2.add(createNavCard("账表查询", "查询总账、明细账\n日记账及资产负债表", new Color(22, 160, 133), "reports"));
        gbc.gridy = 3;
        panel.add(row2, gbc);

        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
        row3.setOpaque(false);
        if (currentUser.canManageUsers()) {
            row3.add(createNavCard("用户管理", "管理系统用户账号\n及角色权限分配", new Color(44, 62, 80), "userManage"));
        }
        if (currentUser.canViewLogs()) {
            row3.add(createNavCard("日志管理", "查看系统操作日志\n支持导出TXT文件", new Color(230, 126, 34), "logManage"));
        }
        gbc.gridy = 4;
        panel.add(row3, gbc);

        // 刷新看板数据
        refreshDashboard();
        // 每30秒刷新一次
        new javax.swing.Timer(30000, e -> refreshDashboard()).start();

        return panel;
    }

    private JPanel createDashboardCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setPreferredSize(new Dimension(280, 90));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 215, 220), 1),
            BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        JPanel accentBar = new JPanel();
        accentBar.setBackground(accentColor);
        accentBar.setPreferredSize(new Dimension(280, 4));

        JLabel titleLabel = new JLabel(title, JLabel.LEFT);
        titleLabel.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        titleLabel.setForeground(new Color(127, 140, 141));

        valueLabel.setFont(new Font("微软雅黑", Font.BOLD, 26));
        valueLabel.setForeground(accentColor);

        JPanel innerPanel = new JPanel(new BorderLayout(0, 5));
        innerPanel.setOpaque(false);
        innerPanel.add(titleLabel, BorderLayout.NORTH);
        innerPanel.add(valueLabel, BorderLayout.CENTER);

        card.add(accentBar, BorderLayout.NORTH);
        card.add(innerPanel, BorderLayout.CENTER);
        return card;
    }

    private void refreshDashboard() {
        try {
            // 算现金余额（科目1001，包含所有子科目）
            double cashBalance = computeAccountBalance("1001");
            cashBalanceLabel.setText(String.format("￥ %.2f", cashBalance));

            // 算银行存款余额（科目1002，包含子科目）
            double bankBalance = computeAccountBalance("1002");
            bankBalanceLabel.setText(String.format("￥ %.2f", bankBalance));
        } catch (Exception e) {
            cashBalanceLabel.setText("--");
            bankBalanceLabel.setText("--");
        }
    }

    private double computeAccountBalance(String code) throws Exception {
        List<OpeningBalance> balances = dao.readOpeningBalances();
        List<Voucher> vouchers = dao.readVouchers();
        List<AccountSubject> subjects = accountService.getAllSubjects();

        // 把子科目的期初余额全加起来（如果父科目有子女就跳过父科目自己，防止重复算）
        double openAmt = 0;
        for (OpeningBalance ob : balances) {
            String obCode = ob.getAccountCode();
            if (obCode == null || !obCode.startsWith(code)) continue;
            // 这个科目有子女的话跳过自己（它的余额是子女汇总来的）
            if (obCode.equals(code) && hasChildren(code, subjects)) continue;
            openAmt += "借".equals(ob.getDirection()) ? ob.getAmount() : -ob.getAmount();
        }

        double debitSum = vouchers.stream()
            .filter(v -> v.getAccountCode() != null && v.getAccountCode().startsWith(code))
            .mapToDouble(Voucher::getDebitAmount).sum();
        double creditSum = vouchers.stream()
            .filter(v -> v.getAccountCode() != null && v.getAccountCode().startsWith(code))
            .mapToDouble(Voucher::getCreditAmount).sum();

        // 从科目本身拿借贷方向
        String dir = "借";
        for (AccountSubject s : subjects) {
            if (s.getCode().equals(code)) {
                if (s.getDirection() != null) dir = s.getDirection();
                break;
            }
        }

        if ("借".equals(dir)) return openAmt + debitSum - creditSum;
        else return openAmt + creditSum - debitSum;
    }

    private boolean hasChildren(String code, List<AccountSubject> subjects) {
        for (AccountSubject s : subjects) {
            if (s.getCode() != null && s.getCode().startsWith(code) && !s.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

    private JPanel createNavCard(String title, String desc, Color accentColor, String panelName) {
        JPanel card = new JPanel(new BorderLayout());
        card.setPreferredSize(new Dimension(210, 135));
        card.setBackground(Color.WHITE);
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        Border baseBorder = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 215, 220), 1),
            BorderFactory.createEmptyBorder(10, 16, 10, 16));
        Border hoverBorder = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accentColor, 1),
            BorderFactory.createEmptyBorder(10, 16, 10, 16));
        card.setBorder(baseBorder);

        JPanel accentBar = new JPanel();
        accentBar.setBackground(accentColor);
        accentBar.setPreferredSize(new Dimension(210, 5));

        JPanel innerPanel = new JPanel(new BorderLayout(0, 8));
        innerPanel.setOpaque(false);
        innerPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 5, 0));

        JLabel titleLabel = new JLabel(title, JLabel.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 17));
        titleLabel.setForeground(new Color(44, 62, 80));
        innerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel descLabel = new JLabel("<html><center>" + desc.replace("\n", "<br>") + "</center></html>", JLabel.CENTER);
        descLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        descLabel.setForeground(new Color(149, 165, 166));
        innerPanel.add(descLabel, BorderLayout.CENTER);

        card.add(accentBar, BorderLayout.NORTH);
        card.add(innerPanel, BorderLayout.CENTER);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (panelName.equals("userManage") && userManagePanel == null) {
                    userManagePanel = new UserManagePanel(dao, authService, currentUser);
                    contentPanel.add(userManagePanel, "userManage");
                }
                if (panelName.equals("logManage") && logPanel == null) {
                    logPanel = new LogPanel(logService, currentUser);
                    contentPanel.add(logPanel, "logManage");
                }
                showPanel(panelName);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBorder(hoverBorder);
                card.setBackground(new Color(248, 249, 252));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBorder(baseBorder);
                card.setBackground(Color.WHITE);
            }
        });

        return card;
    }

    // ==================== Menu Bar ====================

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenuItem homeItem = new JMenuItem("首页");
        homeItem.addActionListener(e -> { refreshDashboard(); showPanel("home"); });
        menuBar.add(homeItem);

        JMenu sysMenu = new JMenu("系统");
        JMenuItem reloginItem = new JMenuItem("重新登录");
        reloginItem.addActionListener(e -> relogin());
        sysMenu.add(reloginItem);

        if (currentUser.canBackup()) {
            JMenuItem backupItem = new JMenuItem("账套备份");
            backupItem.addActionListener(e -> doBackup());
            sysMenu.add(backupItem);
        }
        sysMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> { doShutdown(); System.exit(0); });
        sysMenu.add(exitItem);
        menuBar.add(sysMenu);

        if (currentUser.canManageAccounts()) {
            JMenu setupMenu = new JMenu("基础设置");
            JMenuItem accItem = new JMenuItem("会计科目管理");
            accItem.addActionListener(e -> showPanel("accounts"));
            setupMenu.add(accItem);
            JMenuItem balItem = new JMenuItem("期初余额管理");
            balItem.addActionListener(e -> showPanel("balances"));
            setupMenu.add(balItem);
            menuBar.add(setupMenu);
        }

        if (currentUser.canAccessVoucherProcess()) {
            JMenu bizMenu = new JMenu("业务处理");
            if (currentUser.canCreateVoucher()) {
                JMenuItem inputItem = new JMenuItem("填制凭证");
                inputItem.addActionListener(e -> showPanel("voucherInput"));
                bizMenu.add(inputItem);
                bizMenu.addSeparator();
            }
            JMenuItem processItem = new JMenuItem("凭证处理");
            processItem.addActionListener(e -> showPanel("voucherProcess"));
            bizMenu.add(processItem);
            if (currentUser.canClosePeriod()) {
                bizMenu.addSeparator();
                JMenuItem closeItem = new JMenuItem("期末处理");
                closeItem.addActionListener(e -> showPanel("periodEnd"));
                bizMenu.add(closeItem);
            }
            menuBar.add(bizMenu);
        }

        if (currentUser.canViewReports()) {
            JMenu reportMenu = new JMenu("账表查询");
            JMenuItem rptItem = new JMenuItem("查询报表");
            rptItem.addActionListener(e -> showPanel("reports"));
            reportMenu.add(rptItem);
            menuBar.add(reportMenu);
        }

        if (currentUser.canManageUsers()) {
            JMenu adminMenu = new JMenu("用户管理");
            JMenuItem userItem = new JMenuItem("管理用户");
            userItem.addActionListener(e -> {
                if (userManagePanel == null) {
                    userManagePanel = new UserManagePanel(dao, authService, currentUser);
                    contentPanel.add(userManagePanel, "userManage");
                }
                showPanel("userManage");
            });
            adminMenu.add(userItem);
            menuBar.add(adminMenu);
        }

        if (currentUser.canViewLogs()) {
            JMenu logMenu = new JMenu("日志");
            JMenuItem logItem = new JMenuItem("操作日志");
            logItem.addActionListener(e -> {
                if (logPanel == null) {
                    logPanel = new LogPanel(logService, currentUser);
                    contentPanel.add(logPanel, "logManage");
                }
                showPanel("logManage");
            });
            logMenu.add(logItem);
            menuBar.add(logMenu);
        }

        return menuBar;
    }

    // ==================== Status Bar ====================

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        dateLabel = new JLabel("  " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")) + "  ");
        dateLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        pathLabel = new JLabel("账套: " + dataFilePath + "  ");
        pathLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));

        userInfoLabel = new JLabel(currentUser.getName() + " (" + currentUser.getRole() + ")  ");
        userInfoLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));

        bar.add(dateLabel, BorderLayout.WEST);
        bar.add(pathLabel, BorderLayout.CENTER);
        bar.add(userInfoLabel, BorderLayout.EAST);
        return bar;
    }

    private void updateStatusBar() {
        dateLabel.setText("  " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")) + "  ");
        userInfoLabel.setText(currentUser.getName() + " (" + currentUser.getRole() + ")  ");
        pathLabel.setText("账套: " + dataFilePath + "  ");
    }

    // ==================== Navigation ====================

    private String currentPanelName = "home";

    public void showPanel(String name) {
        // 如果余额改了没保存，离开的时候弹提示
        if ("balances".equals(currentPanelName) && !"balances".equals(name)) {
            if (!balancePanel.trySaveOnExit()) return;
        }
        currentPanelName = name;

        switch (name) {
            case "accounts": accountPanel.refreshData(); break;
            case "balances": balancePanel.refreshData(); break;
            case "voucherInput": voucherInputPanel.refreshData(); break;
            case "voucherProcess": voucherProcessPanel.refreshData(); break;
            case "periodEnd": periodEndPanel.refreshData(); break;
            case "reports": reportPanel.refreshData(); break;
            case "userManage":
                if (userManagePanel != null) userManagePanel.refreshData();
                break;
            case "logManage":
                if (logPanel != null) logPanel.refreshData();
                break;
            case "home": refreshDashboard(); break;
        }
        cardLayout.show(contentPanel, name);
    }

    private void relogin() {
        doShutdown();
        setVisible(false);
        dispose();
        new LoginFrame(dataFilePath).setVisible(true);
    }

    private void doBackup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("D:\\ztbf.xlsm"));
        chooser.setDialogTitle("选择备份路径");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String path = chooser.getSelectedFile().getAbsolutePath();
                // 备份前先把日志刷到文件里
                logService.flush();
                dao.backupTo(path);
                JOptionPane.showMessageDialog(this, "账套备份成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "备份失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void doShutdown() {
        try {
            logService.addLog(currentUser.getName(), "退出系统", "用户退出系统");
            logService.flush();
        } catch (Exception ignored) {}
        if (backupScheduler != null) backupScheduler.shutdownNow();
        dao.close();
    }

    // ==================== Auto Backup ====================

    private void startAutoBackup() {
        backupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auto-backup");
            t.setDaemon(true);
            return t;
        });

        File dataDir = new File(dataFilePath).getParentFile();
        File backupDir = new File(dataDir, "backup");
        if (!backupDir.exists()) backupDir.mkdirs();

        backupScheduler.scheduleAtFixedRate(() -> {
            try {
                logService.flush();
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String backupName = "DATA_" + timestamp + ".xlsx";
                File backupFile = new File(backupDir, backupName);
                dao.backupTo(backupFile.getAbsolutePath());

                // 删掉7天前的旧备份
                long cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
                File[] files = backupDir.listFiles((d, name) -> name.startsWith("DATA_") && name.endsWith(".xlsx"));
                if (files != null) {
                    for (File f : files) {
                        if (f.lastModified() < cutoff) f.delete();
                    }
                }
            } catch (Exception ignored) {}
        }, 15, 15, TimeUnit.MINUTES);
    }
}
