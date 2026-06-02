package com.fim.service;

import com.fim.dao.ExcelDAO;
import com.fim.model.OperationLog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LogService {
    private final ExcelDAO dao;
    private final List<OperationLog> buffer = new ArrayList<>();

    public LogService(ExcelDAO dao) {
        this.dao = dao;
    }

    public void addLog(String user, String type, String detail) {
        OperationLog log = new OperationLog(user, type, detail);
        buffer.add(log);
    }

    public void flush() throws Exception {
        if (buffer.isEmpty()) return;
        List<OperationLog> existing = dao.readLogs();
        existing.addAll(buffer);
        dao.writeLogs(existing);
        buffer.clear();
    }

    public List<OperationLog> getAllLogs() throws Exception {
        List<OperationLog> all = dao.readLogs();
        all.addAll(buffer);
        return all;
    }

    public void exportToTxt(File dest) throws Exception {
        List<OperationLog> all = getAllLogs();
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(dest), StandardCharsets.UTF_8))) {
            writer.write("========== 财务管理系统 操作日志 ==========");
            writer.newLine();
            writer.write("导出时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.newLine();
            writer.write("==============================================");
            writer.newLine();
            writer.newLine();
            writer.write(String.format("%-22s %-12s %-12s %s", "时间", "操作用户", "操作类型", "操作详情"));
            writer.newLine();
            writer.write("---------------------------------------------------------------");
            writer.newLine();
            for (OperationLog log : all) {
                writer.write(String.format("%-22s %-12s %-12s %s",
                    log.getTimeStr(),
                    log.getUser(),
                    log.getType(),
                    log.getDetail()));
                writer.newLine();
            }
        }
    }

    public void clearLogs() throws Exception {
        dao.writeLogs(new ArrayList<>());
        buffer.clear();
    }
}
