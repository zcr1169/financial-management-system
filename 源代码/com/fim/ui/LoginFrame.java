package com.fim.ui;

import com.fim.dao.ExcelDAO;
import com.fim.model.User;
import com.fim.service.AuthService;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LoginFrame extends JFrame {
    private final String dataFilePath;
    private JTextField accountField;
    private JPasswordField passwordField;
    private JLabel statusLabel;
    private Image backgroundImage;

    public LoginFrame(String dataFilePath) {
        this.dataFilePath = dataFilePath;
        loadBackgroundImage();
        initUI();
    }

    private void loadBackgroundImage() {
        File dir = new File(dataFilePath).getParentFile();
        File imgFile = new File(dir, "f1e41e9bc2cfd1d0fe9adc9cebd8e241.jpg");
        if (imgFile.exists()) {
            backgroundImage = new ImageIcon(imgFile.getAbsolutePath()).getImage();
        }
    }

    private void initUI() {
        setTitle("241001209周宸冉 - 登录");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // 带背景图的主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                    // 蒙一层半透明白色让字看得清
                    g.setColor(new Color(255, 255, 255, 210));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };
        mainPanel.setBorder(BorderFactory.createEmptyBorder(25, 50, 25, 50));

        // 标题区域
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        JLabel titleLabel = new JLabel("241001209周宸冉", JLabel.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 28));
        titleLabel.setForeground(new Color(44, 62, 80));
        JLabel subTitle = new JLabel("Small Enterprise Financial Management", JLabel.CENTER);
        subTitle.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        subTitle.setForeground(new Color(127, 140, 141));
        titlePanel.add(titleLabel, BorderLayout.CENTER);
        titlePanel.add(subTitle, BorderLayout.SOUTH);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        // 登录表单
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 8, 10, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 账号行
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel accLabel = new JLabel("账  号：");
        accLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        formPanel.add(accLabel, gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        accountField = new JTextField(15);
        formPanel.add(accountField, gbc);

        // 密码行
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel pwdLabel = new JLabel("口  令：");
        pwdLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        formPanel.add(pwdLabel, gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        passwordField = new JPasswordField(15);
        formPanel.add(passwordField, gbc);

        // 按钮行
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.insets = new Insets(18, 8, 5, 8);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnPanel.setOpaque(false);

        JButton loginBtn = new JButton("登  录");
        loginBtn.setFont(new Font("微软雅黑", Font.BOLD, 15));
        loginBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton cancelBtn = new JButton("取  消");
        cancelBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        cancelBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnPanel.add(loginBtn);
        btnPanel.add(cancelBtn);
        formPanel.add(btnPanel, gbc);

        // 状态提示
        gbc.gridy = 3;
        gbc.insets = new Insets(10, 8, 5, 8);
        statusLabel = new JLabel(" ", JLabel.CENTER);
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        formPanel.add(statusLabel, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // 底部信息栏
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        JLabel dateInfo = new JLabel("  " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")) + "  ");
        dateInfo.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        dateInfo.setForeground(new Color(149, 165, 166));
        bottomPanel.add(dateInfo, BorderLayout.WEST);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // 绑定事件
        loginBtn.addActionListener(e -> doLogin());
        cancelBtn.addActionListener(e -> System.exit(0));
        passwordField.addActionListener(e -> doLogin());
        accountField.addActionListener(e -> passwordField.requestFocus());

        pack();
        setLocationRelativeTo(null);
    }

    private void doLogin() {
        String account = accountField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (account.isEmpty()) {
            statusLabel.setText("请输入账号！");
            return;
        }

        statusLabel.setText("正在验证...");
        statusLabel.setForeground(new Color(41, 128, 185));

        new Thread(() -> {
            ExcelDAO dao = new ExcelDAO(dataFilePath);
            boolean loginSuccess = false;
            try {
                dao.open();

                if (!dao.validateSheets()) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            "账套文件损坏：缺少必需子表（用户表/会计科目表/期初余额表/凭证表）！",
                            "错误", JOptionPane.ERROR_MESSAGE);
                        statusLabel.setText("账套文件不完整");
                        statusLabel.setForeground(Color.RED);
                    });
                    return;
                }

                AuthService auth = new AuthService(dao);
                User user = auth.login(account, password);

                loginSuccess = true;
                final ExcelDAO finalDao = dao;
                final AuthService finalAuth = auth;
                final User finalUser = user;
                SwingUtilities.invokeLater(() -> {
                    setVisible(false);
                    dispose();
                    showWelcomeToast(finalDao, finalAuth, finalUser);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(ex.getMessage());
                    statusLabel.setForeground(Color.RED);
                });
            } finally {
                if (!loginSuccess) {
                    try { dao.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void showWelcomeToast(ExcelDAO dao, AuthService auth, User user) {
        JDialog toast = new JDialog();
        toast.setUndecorated(true);
        toast.setSize(320, 90);
        toast.setLocationRelativeTo(null);
        toast.setShape(new RoundRectangle2D.Double(0, 0, 320, 90, 16, 16));
        toast.setAlwaysOnTop(true);

        JPanel toastPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(44, 62, 80));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2d.dispose();
            }
        };
        toastPanel.setOpaque(false);
        toastPanel.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));

        JLabel iconLabel = new JLabel("✔", JLabel.CENTER);
        iconLabel.setFont(new Font("微软雅黑", Font.BOLD, 22));
        iconLabel.setForeground(new Color(39, 174, 96));

        JLabel msgLabel = new JLabel("登录成功，欢迎您！" + user.getName(), JLabel.CENTER);
        msgLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        msgLabel.setForeground(Color.WHITE);

        JLabel roleLabel = new JLabel("当前角色: " + user.getRole(), JLabel.CENTER);
        roleLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        roleLabel.setForeground(new Color(189, 195, 199));

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        textPanel.setOpaque(false);
        textPanel.add(msgLabel);
        textPanel.add(roleLabel);

        toastPanel.add(iconLabel, BorderLayout.WEST);
        toastPanel.add(textPanel, BorderLayout.CENTER);

        toast.add(toastPanel);
        toast.setVisible(true);

        Timer timer = new Timer(1800, e -> {
            toast.dispose();
            new MainFrame(dataFilePath, dao, auth, user).setVisible(true);
        });
        timer.setRepeats(false);
        timer.start();
    }
}
