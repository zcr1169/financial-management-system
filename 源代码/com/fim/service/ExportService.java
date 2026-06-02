package com.fim.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ExportService {

    public void exportToExcel(List<String[]> data, String title, File dest) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(title);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);

            // 标题行
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(title);
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);

            int startRow = data.isEmpty() ? 2 : 2;
            if (!data.isEmpty()) {
                // 表头行
                Row headerRow = sheet.createRow(startRow);
                String[] headers = data.get(0);
                for (int j = 0; j < headers.length; j++) {
                    Cell cell = headerRow.createCell(j);
                    cell.setCellValue(headers[j]);
                    cell.setCellStyle(headerStyle);
                }
                // 数据行
                for (int i = 1; i < data.size(); i++) {
                    Row row = sheet.createRow(startRow + i);
                    String[] values = data.get(i);
                    for (int j = 0; j < values.length; j++) {
                        Cell cell = row.createCell(j);
                        cell.setCellValue(values[j] != null ? values[j] : "");
                        cell.setCellStyle(dataStyle);
                    }
                }
            }

            // 列宽自动调整
            for (int j = 0; j < (data.isEmpty() ? 5 : data.get(0).length); j++) {
                sheet.autoSizeColumn(j);
            }

            try (FileOutputStream fos = new FileOutputStream(dest)) {
                workbook.write(fos);
            }
        }
    }

    public void exportToPDF(List<String[]> data, String title, File dest) throws Exception {
        // 生成一个合法的PDF文件（不用第三方库，自己拼）
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // 先构建PDF的内容
        ByteArrayOutputStream contentStream = new ByteArrayOutputStream();
        contentStream.write("BT\n".getBytes(StandardCharsets.UTF_8));
        contentStream.write("/F1 9 Tf\n".getBytes(StandardCharsets.UTF_8));

        int y = 750;
        // 标题
        contentStream.write(("1 0 0 1 50 " + y + " Tm\n").getBytes(StandardCharsets.UTF_8));
        contentStream.write(escapePDF(title).getBytes(StandardCharsets.UTF_8));
        contentStream.write(" Tj\n".getBytes(StandardCharsets.UTF_8));
        y -= 25;

        if (!data.isEmpty()) {
            String[] headers = data.get(0);
            contentStream.write(("1 0 0 1 50 " + y + " Tm\n").getBytes(StandardCharsets.UTF_8));
            contentStream.write(escapePDF(String.join("    ", headers)).getBytes(StandardCharsets.UTF_8));
            contentStream.write(" Tj\n".getBytes(StandardCharsets.UTF_8));
            y -= 18;

            for (int i = 1; i < data.size() && y > 50; i++) {
                String[] row = data.get(i);
                StringBuilder line = new StringBuilder();
                for (int j = 0; j < row.length; j++) {
                    if (j > 0) line.append("    ");
                    line.append(row[j] != null ? row[j] : "");
                }
                contentStream.write(("1 0 0 1 50 " + y + " Tm\n").getBytes(StandardCharsets.UTF_8));
                contentStream.write(escapePDF(line.toString()).getBytes(StandardCharsets.UTF_8));
                contentStream.write(" Tj\n".getBytes(StandardCharsets.UTF_8));
                y -= 16;
            }
        }
        contentStream.write("ET\n".getBytes(StandardCharsets.UTF_8));
        byte[] contentBytes = contentStream.toByteArray();

        // 写PDF的文件结构
        dos.writeBytes("%PDF-1.4\n");

        // 对象1：目录
        long obj1Offset = baos.size();
        dos.writeBytes("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        // 对象2：页面集合
        long obj2Offset = baos.size();
        dos.writeBytes("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        // 对象3：单页
        long obj3Offset = baos.size();
        dos.writeBytes("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
            + "/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n");

        // 对象4：内容流
        long obj4Offset = baos.size();
        dos.writeBytes("4 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
        dos.write(contentBytes);
        dos.writeBytes("\nendstream\nendobj\n");

        // 对象5：字体（Helvetica，中文可能显示不好）
        long obj5Offset = baos.size();
        dos.writeBytes("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

        // 交叉引用表
        long xrefOffset = baos.size();
        dos.writeBytes("xref\n0 6\n0000000000 65535 f \n");
        dos.writeBytes(String.format("%010d 00000 n \n", obj1Offset));
        dos.writeBytes(String.format("%010d 00000 n \n", obj2Offset));
        dos.writeBytes(String.format("%010d 00000 n \n", obj3Offset));
        dos.writeBytes(String.format("%010d 00000 n \n", obj4Offset));
        dos.writeBytes(String.format("%010d 00000 n \n", obj5Offset));

        // Trailer
        dos.writeBytes("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n");

        dos.flush();
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            baos.writeTo(fos);
        }
    }

    private String escapePDF(String text) {
        // PDF字符串里需要转义的字符
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (char c : text.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c < 32 || c > 126) {
                // 非 ASCII 字符用八进制转义（PDF 原生支持）
                sb.append(String.format("\\%03o", (int) c));
            } else {
                sb.append(c);
            }
        }
        sb.append(')');
        return sb.toString();
    }
}
