package com.fim;

import com.fim.ui.LoginFrame;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

public class FinApp {
    public static final String APP_NAME = "241001209周宸冉";
    public static final String DATA_FILE = "DATA.xlsx";

    public static void main(String[] args) {
        // 设个好看的界面外观
        FlatLightLaf.setup();

        // 调一下默认字体和表格样式
        UIManager.put("defaultFont", new Font("微软雅黑", Font.PLAIN, 13));
        UIManager.put("Table.rowHeight", 32);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", true);

        SwingUtilities.invokeLater(() -> {
            String dataPath = findDataFile();
            if (dataPath == null) {
                JOptionPane.showMessageDialog(null,
                    "未找到 DATA.xlsx 文件！\n请确保程序运行目录下存在该文件。",
                    "错误", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            new LoginFrame(dataPath).setVisible(true);
        });
    }

    private static String findDataFile() {
        java.io.File f = new java.io.File(DATA_FILE);
        if (f.exists()) return f.getAbsolutePath();

        f = new java.io.File(System.getProperty("user.dir"), DATA_FILE);
        if (f.exists()) return f.getAbsolutePath();

        String jarPath = FinApp.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        f = new java.io.File(new java.io.File(jarPath).getParent(), DATA_FILE);
        if (f.exists()) return f.getAbsolutePath();

        return null;
    }
}
