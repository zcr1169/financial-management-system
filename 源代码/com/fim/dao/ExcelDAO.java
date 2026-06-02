package com.fim.dao;

import com.fim.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ExcelDAO {
    private final String filePath;
    private Workbook workbook;

    // 工作表的名字
    private static final String SHEET_USERS = "用户表";
    private static final String SHEET_ACCOUNTS = "会计科目";
    private static final String SHEET_BALANCES = "期初余额";
    private static final String SHEET_VOUCHERS = "凭证";
    private static final String SHEET_LOGS = "操作日志";

    public ExcelDAO(String filePath) {
        this.filePath = filePath;
    }

    // --- 文件操作 ---

    public void open() throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            workbook = new XSSFWorkbook(fis);
        }
    }

    public synchronized void close() {
        if (workbook != null) {
            try { save(); } catch (Exception ignored) {}
            try { workbook.close(); } catch (Exception ignored) {}
            workbook = null;
        }
    }

    public synchronized void save() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
            fos.flush();
        }
    }

    public synchronized void backupTo(String backupPath) throws IOException {
        Files.copy(Paths.get(filePath), Paths.get(backupPath), StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean validateSheets() {
        if (workbook == null) return false;
        return workbook.getSheet(SHEET_USERS) != null
            && workbook.getSheet(SHEET_ACCOUNTS) != null
            && workbook.getSheet(SHEET_BALANCES) != null
            && workbook.getSheet(SHEET_VOUCHERS) != null;
    }

    // --- 读取数据 ---

    public List<User> readUsers() {
        List<User> users = new ArrayList<>();
        Sheet sheet = workbook.getSheet(SHEET_USERS);
        if (sheet == null) return users;

        Map<String, Integer> colMap = readHeader(sheet, 0);
        if (colMap.isEmpty()) return users;

        int accountCol = colMap.getOrDefault("账号", -1);
        int pwdCol = colMap.getOrDefault("密码", -1);
        int roleCol = colMap.getOrDefault("角色", -1);
        int nameCol = colMap.getOrDefault("姓名", -1);
        int failCol = colMap.getOrDefault("登录失败次数", -1);
        int lockCol = colMap.getOrDefault("锁定状态", -1);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            User u = new User();
            u.setAccount(getCellString(row, accountCol));
            u.setPassword(getCellString(row, pwdCol));
            u.setRole(getCellString(row, roleCol));
            u.setName(getCellString(row, nameCol));
            if (failCol >= 0) u.setFailedAttempts(getCellInt(row, failCol));
            if (lockCol >= 0) u.setLocked("Y".equals(getCellString(row, lockCol)));
            if (!u.getAccount().isEmpty()) users.add(u);
        }
        return users;
    }

    public List<AccountSubject> readAccountSubjects() {
        List<AccountSubject> list = new ArrayList<>();
        Sheet sheet = workbook.getSheet(SHEET_ACCOUNTS);
        if (sheet == null) return list;

        Map<String, Integer> colMap = readHeader(sheet, 0);
        int codeCol = colMap.getOrDefault("科目编码", -1);
        int nameCol = colMap.getOrDefault("科目名称", -1);
        int auxCol = colMap.getOrDefault("辅助账类型", -1);
        int dirCol = colMap.getOrDefault("余额方向", -1);
        int bankCol = colMap.getOrDefault("银行账", -1);
        int journalCol = colMap.getOrDefault("日记账", -1);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            AccountSubject a = new AccountSubject();
            a.setCode(getCellString(row, codeCol));
            a.setName(getCellString(row, nameCol));
            if (auxCol >= 0) a.setAuxType(getCellString(row, auxCol));
            if (dirCol >= 0) a.setDirection(getCellString(row, dirCol));
            if (bankCol >= 0) a.setBankAccount("Y".equals(getCellString(row, bankCol)));
            if (journalCol >= 0) a.setJournal("Y".equals(getCellString(row, journalCol)));
            if (!a.getCode().isEmpty()) list.add(a);
        }
        return list;
    }

    public List<OpeningBalance> readOpeningBalances() {
        List<OpeningBalance> list = new ArrayList<>();
        Sheet sheet = workbook.getSheet(SHEET_BALANCES);
        if (sheet == null) return list;

        Map<String, Integer> colMap = readHeader(sheet, 0);
        int codeCol = colMap.getOrDefault("科目编码", -1);
        int nameCol = colMap.getOrDefault("科目名称", -1);
        int dirCol = colMap.getOrDefault("方向", -1);
        int amtCol = colMap.getOrDefault("期初余额", -1);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            OpeningBalance ob = new OpeningBalance();
            ob.setAccountCode(getCellString(row, codeCol));
            ob.setAccountName(getCellString(row, nameCol));
            if (dirCol >= 0) ob.setDirection(getCellString(row, dirCol));
            if (amtCol >= 0) ob.setAmount(getCellDouble(row, amtCol));
            if (!ob.getAccountCode().isEmpty()) list.add(ob);
        }
        return list;
    }

    public List<Voucher> readVouchers() {
        List<Voucher> list = new ArrayList<>();
        Sheet sheet = workbook.getSheet(SHEET_VOUCHERS);
        if (sheet == null) return list;

        Map<String, Integer> colMap = readHeader(sheet, 0);
        int typeCol = colMap.getOrDefault("凭证类别", -1);
        int numCol = colMap.getOrDefault("编号", -1);
        int dateCol = colMap.getOrDefault("日期", -1);
        int descCol = colMap.getOrDefault("摘要", -1);
        int codeCol = colMap.getOrDefault("科目", -1);
        if (codeCol < 0) codeCol = colMap.getOrDefault("科目编码", -1);
        int nameCol = colMap.getOrDefault("科目名称", -1);
        int debitCol = colMap.getOrDefault("借方金额", -1);
        int creditCol = colMap.getOrDefault("贷方金额", -1);
        int makerCol = colMap.getOrDefault("制单", -1);
        int reviewerCol = colMap.getOrDefault("审核", -1);
        int signCol = colMap.getOrDefault("出纳", -1);
        int posterCol = colMap.getOrDefault("记账", -1);
        int closedCol = colMap.getOrDefault("结账", -1);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Voucher v = new Voucher();
            v.setType(getCellString(row, typeCol));
            v.setNumber(getCellInt(row, numCol));
            v.setDate(getCellDate(row, dateCol));
            v.setDescription(getCellString(row, descCol));
            v.setAccountCode(getCellString(row, codeCol));
            if (nameCol >= 0) v.setAccountName(getCellString(row, nameCol));
            v.setDebitAmount(getCellDouble(row, debitCol));
            v.setCreditAmount(getCellDouble(row, creditCol));
            if (makerCol >= 0) v.setMaker(getCellString(row, makerCol));
            if (reviewerCol >= 0) {
                String rv = getCellString(row, reviewerCol);
                v.setReviewer(rv);
                v.setReviewed(!rv.isEmpty());
            }
            if (signCol >= 0) {
                String sn = getCellString(row, signCol);
                v.setCashier(sn);
                v.setSigned(!sn.isEmpty());
            }
            if (posterCol >= 0) {
                String pt = getCellString(row, posterCol);
                v.setPoster(pt);
                v.setPosted(!pt.isEmpty());
            }
            if (closedCol >= 0) {
                String cl = getCellString(row, closedCol);
                v.setClosed(!cl.isEmpty());
            }
            if (v.getType() != null && !v.getType().isEmpty()) list.add(v);
        }
        return list;
    }

    // --- 写入数据 ---

    public void writeUsers(List<User> users) throws IOException {
        Sheet sheet = workbook.getSheet(SHEET_USERS);
        for (int i = sheet.getLastRowNum(); i >= 1; i--) {
            Row row = sheet.getRow(i);
            if (row != null) sheet.removeRow(row);
        }
        // 确保表头有新加的两列
        Row header = sheet.getRow(0);
        if (header != null) {
            ensureHeaderCell(header, 4, "登录失败次数");
            ensureHeaderCell(header, 5, "锁定状态");
        }
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(u.getAccount());
            row.createCell(1).setCellValue(u.getPassword());
            row.createCell(2).setCellValue(u.getRole());
            row.createCell(3).setCellValue(u.getName());
            row.createCell(4).setCellValue(u.getFailedAttempts());
            row.createCell(5).setCellValue(u.isLocked() ? "Y" : "");
        }
        save();
    }

    public void writeAccountSubjects(List<AccountSubject> subjects) throws IOException {
        Sheet sheet = workbook.getSheet(SHEET_ACCOUNTS);
        // 删掉旧行不然越堆越多
        for (int i = sheet.getLastRowNum(); i >= 1; i--) {
            Row row = sheet.getRow(i);
            if (row != null) sheet.removeRow(row);
        }
        int colCount = sheet.getRow(0) == null ? 6 : sheet.getRow(0).getLastCellNum();
        for (int i = 0; i < subjects.size(); i++) {
            AccountSubject a = subjects.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(a.getCode());
            row.createCell(1).setCellValue(a.getName());
            row.createCell(2).setCellValue(a.getAuxType() != null ? a.getAuxType() : "");
            row.createCell(3).setCellValue(a.getDirection() != null ? a.getDirection() : "");
            row.createCell(4).setCellValue(a.isBankAccount() ? "Y" : "");
            if (colCount > 5) row.createCell(5).setCellValue(a.isJournal() ? "Y" : "");
        }
        save();
    }

    public void writeOpeningBalances(List<OpeningBalance> balances) throws IOException {
        Sheet sheet = workbook.getSheet(SHEET_BALANCES);
        for (int i = sheet.getLastRowNum(); i >= 1; i--) {
            Row row = sheet.getRow(i);
            if (row != null) sheet.removeRow(row);
        }
        for (int i = 0; i < balances.size(); i++) {
            OpeningBalance ob = balances.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(ob.getAccountCode());
            row.createCell(1).setCellValue(ob.getAccountName());
            row.createCell(2).setCellValue(ob.getDirection() != null ? ob.getDirection() : "");
            row.createCell(3).setCellValue(ob.getAmount());
        }
        save();
    }

    public void writeVouchers(List<Voucher> vouchers) throws IOException {
        Sheet sheet = workbook.getSheet(SHEET_VOUCHERS);
        for (int i = sheet.getLastRowNum(); i >= 1; i--) {
            Row row = sheet.getRow(i);
            if (row != null) sheet.removeRow(row);
        }
        CreationHelper helper = workbook.getCreationHelper();
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

        for (int i = 0; i < vouchers.size(); i++) {
            Voucher v = vouchers.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(v.getType());
            row.createCell(1).setCellValue(v.getNumber());
            Cell dateCell = row.createCell(2);
            if (v.getDate() != null) {
                dateCell.setCellValue(v.getDate());
                dateCell.setCellStyle(dateStyle);
            }
            row.createCell(3).setCellValue(v.getDescription());
            row.createCell(4).setCellValue(v.getAccountName() != null ? v.getAccountName() : "");
            row.createCell(5).setCellValue(v.getAccountCode());
            if (v.getDebitAmount() > 0) row.createCell(6).setCellValue(v.getDebitAmount());
            if (v.getCreditAmount() > 0) row.createCell(7).setCellValue(v.getCreditAmount());
            row.createCell(8).setCellValue(v.getMaker() != null ? v.getMaker() : "");
            row.createCell(9).setCellValue(v.getReviewer() != null ? v.getReviewer() : "");
            row.createCell(10).setCellValue(v.getCashier() != null ? v.getCashier() : "");
            row.createCell(11).setCellValue(v.getPoster() != null ? v.getPoster() : "");
            row.createCell(12).setCellValue(v.isClosed() ? "Y" : "");
        }
        save();
    }

    // --- 日志操作 ---

    public List<OperationLog> readLogs() {
        List<OperationLog> list = new ArrayList<>();
        Sheet sheet = workbook.getSheet(SHEET_LOGS);
        if (sheet == null) return list;

        Map<String, Integer> colMap = readHeader(sheet, 0);
        if (colMap.isEmpty()) return list;

        int timeCol = colMap.getOrDefault("时间", -1);
        int userCol = colMap.getOrDefault("操作用户", -1);
        int typeCol = colMap.getOrDefault("操作类型", -1);
        int detailCol = colMap.getOrDefault("操作详情", -1);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            OperationLog log = new OperationLog();
            log.setTime(getCellDate(row, timeCol));
            log.setUser(getCellString(row, userCol));
            log.setType(getCellString(row, typeCol));
            log.setDetail(getCellString(row, detailCol));
            if (log.getUser() != null && !log.getUser().isEmpty()) list.add(log);
        }
        list.sort((a, b) -> {
            if (a.getTime() == null && b.getTime() == null) return 0;
            if (a.getTime() == null) return 1;
            if (b.getTime() == null) return -1;
            return b.getTime().compareTo(a.getTime());
        });
        return list;
    }

    public void writeLogs(List<OperationLog> logs) throws IOException {
        // 要是没有这个表就建一个
        Sheet sheet = workbook.getSheet(SHEET_LOGS);
        if (sheet == null) {
            sheet = workbook.createSheet(SHEET_LOGS);
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("时间");
            header.createCell(1).setCellValue("操作用户");
            header.createCell(2).setCellValue("操作类型");
            header.createCell(3).setCellValue("操作详情");
        }
        // 删掉旧行不然越堆越多
        for (int i = sheet.getLastRowNum(); i >= 1; i--) {
            Row row = sheet.getRow(i);
            if (row != null) sheet.removeRow(row);
        }
        for (int i = 0; i < logs.size(); i++) {
            OperationLog log = logs.get(i);
            Row row = sheet.createRow(i + 1);
            Cell timeCell = row.createCell(0);
            if (log.getTime() != null) {
                timeCell.setCellValue(log.getTime());
                CreationHelper helper = workbook.getCreationHelper();
                CellStyle style = workbook.createCellStyle();
                style.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
                timeCell.setCellStyle(style);
            }
            row.createCell(1).setCellValue(log.getUser());
            row.createCell(2).setCellValue(log.getType());
            row.createCell(3).setCellValue(log.getDetail());
        }
        save();
    }

    // --- 工具方法 ---

    private Map<String, Integer> readHeader(Sheet sheet, int headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        Row row = sheet.getRow(headerRow);
        if (row == null) return map;
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                String val = cell.getStringCellValue().trim();
                if (!val.isEmpty()) map.put(val, i);
            }
        }
        return map;
    }

    private String getCellString(Row row, int col) {
        if (col < 0 || row == null) return "";
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC:
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }

    private int getCellInt(Row row, int col) {
        if (col < 0 || row == null) return 0;
        Cell cell = row.getCell(col);
        if (cell == null) return 0;
        if (cell.getCellType() == CellType.NUMERIC) return (int) cell.getNumericCellValue();
        try { return Integer.parseInt(cell.getStringCellValue().trim()); } catch (Exception e) { return 0; }
    }

    private double getCellDouble(Row row, int col) {
        if (col < 0 || row == null) return 0;
        Cell cell = row.getCell(col);
        if (cell == null) return 0;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        try { return Double.parseDouble(cell.getStringCellValue().trim()); } catch (Exception e) { return 0; }
    }

    private Date getCellDate(Row row, int col) {
        if (col < 0 || row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue();
        }
        try {
            String s = cell.getStringCellValue().trim();
            return new SimpleDateFormat("yyyy-MM-dd").parse(s);
        } catch (Exception e) { return null; }
    }

    private void ensureHeaderCell(Row header, int col, String value) {
        Cell cell = header.getCell(col);
        if (cell == null) {
            header.createCell(col).setCellValue(value);
        }
    }
}
