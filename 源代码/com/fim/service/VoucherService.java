package com.fim.service;

import com.fim.dao.ExcelDAO;
import com.fim.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class VoucherService {
    private final ExcelDAO dao;
    private LogService logService;
    private String currentUserName;

    public VoucherService(ExcelDAO dao) {
        this.dao = dao;
    }

    public void setLogService(LogService logService) {
        this.logService = logService;
    }

    private void log(String type, String detail) {
        if (logService != null) {
            logService.addLog(currentUserName != null ? currentUserName : "系统", type, detail);
        }
    }

    public List<Voucher> getAllVouchers() throws Exception {
        return dao.readVouchers();
    }

    public List<Voucher> getVouchersByMonth(int month) throws Exception {
        return dao.readVouchers().stream()
                .filter(v -> v.getMonth() == month)
                .collect(Collectors.toList());
    }

    public List<Voucher> getUnreviewedVouchers() throws Exception {
        return dao.readVouchers().stream()
                .filter(v -> !v.isReviewed())
                .collect(Collectors.toList());
    }

    public List<Voucher> getReviewedUnpostedVouchers() throws Exception {
        return dao.readVouchers().stream()
                .filter(v -> v.isReviewed() && !v.isPosted())
                .collect(Collectors.toList());
    }

    public List<String> getHistoryDescriptions() throws Exception {
        return dao.readVouchers().stream()
                .map(Voucher::getDescription)
                .filter(d -> d != null && !d.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    public int getNextNumber(String type) throws Exception {
        return dao.readVouchers().stream()
                .filter(v -> type.equals(v.getType()))
                .mapToInt(Voucher::getNumber)
                .max()
                .orElse(0) + 1;
    }

    /**
     * 保存凭证，一张凭证可能有多行分录
     */
    public void saveVoucher(List<Voucher> entries, String maker, User currentUser) throws Exception {
        currentUserName = currentUser.getName();
        validateVoucher(entries, currentUser);
        List<Voucher> all = dao.readVouchers();

        if (!entries.isEmpty()) {
            int month = entries.get(0).getMonth();
            if (isMonthClosed(month, all)) {
                throw new Exception(month + "月份已结账，禁止填制凭证！");
            }
        }

        for (Voucher v : entries) {
            v.setMaker(maker);
            all.add(v);
        }
        dao.writeVouchers(all);
        if (!entries.isEmpty()) {
            log("填制凭证", "填制凭证 " + entries.get(0).getType() + entries.get(0).getNumber());
        }
    }

    /**
     * 修改已有凭证
     */
    public void updateVoucher(int oldNumber, String oldType, List<Voucher> newEntries, User currentUser) throws Exception {
        List<Voucher> all = dao.readVouchers();
        // 找到原来的凭证
        List<Voucher> oldEntries = all.stream()
                .filter(v -> v.getType().equals(oldType) && v.getNumber() == oldNumber)
                .collect(Collectors.toList());
        if (oldEntries.isEmpty()) throw new Exception("凭证不存在！");

        // 检查还能不能改
        for (Voucher v : oldEntries) {
            if (v.isLocked()) throw new Exception("凭证" + v.getType() + v.getNumber() + "已审核/签字/记账/结账，禁止修改！");
            if (!v.getMaker().equals(currentUser.getName())) throw new Exception("仅制单人可修改凭证！");
        }

        // 删旧的加新的
        all.removeAll(oldEntries);
        validateVoucher(newEntries, currentUser);
        for (Voucher v : newEntries) {
            v.setMaker(currentUser.getName());
            all.add(v);
        }
        dao.writeVouchers(all);
    }

    /**
     * 删凭证
     */
    public void deleteVoucher(String type, int number, User currentUser) throws Exception {
        currentUserName = currentUser.getName();
        List<Voucher> all = dao.readVouchers();
        List<Voucher> entries = all.stream()
                .filter(v -> v.getType().equals(type) && v.getNumber() == number)
                .collect(Collectors.toList());
        if (entries.isEmpty()) throw new Exception("凭证不存在！");

        for (Voucher v : entries) {
            if (v.isLocked()) throw new Exception("凭证已审核/签字/记账/结账，禁止删除！");
            if (!v.getMaker().equals(currentUser.getName())) throw new Exception("仅制单人可删除凭证！");
        }
        all.removeAll(entries);
        dao.writeVouchers(all);
        log("删除凭证", "删除凭证 " + type + number);
    }

    /**
     * 审核凭证
     */
    public void reviewVoucher(String type, int number, User reviewer) throws Exception {
        currentUserName = reviewer.getName();
        List<Voucher> all = dao.readVouchers();
        List<Voucher> entries = all.stream()
                .filter(v -> v.getType().equals(type) && v.getNumber() == number)
                .collect(Collectors.toList());
        if (entries.isEmpty()) throw new Exception("凭证不存在！");

        for (Voucher v : entries) {
            if (v.isReviewed()) throw new Exception("凭证已审核，禁止重复审核！");
            if (v.getMaker().equals(reviewer.getName())) throw new Exception("制单人与审核人不能为同一人！");
            v.setReviewer(reviewer.getName());
            v.setReviewed(true);
        }
        dao.writeVouchers(all);
        log("审核凭证", "审核凭证 " + type + number);
    }

    /**
     * 取消审核
     */
    public void unreviewVoucher(String type, int number) throws Exception {
        List<Voucher> all = dao.readVouchers();
        List<Voucher> entries = all.stream()
                .filter(v -> v.getType().equals(type) && v.getNumber() == number)
                .collect(Collectors.toList());
        if (entries.isEmpty()) throw new Exception("凭证不存在！");

        for (Voucher v : entries) {
            if (v.isPosted()) throw new Exception("凭证已记账，禁止取消审核！");
            if (v.isSigned()) throw new Exception("凭证已签字，请先取消签字！");
            v.setReviewer("");
            v.setReviewed(false);
        }
        dao.writeVouchers(all);
    }

    /**
     * 出纳对收付款凭证签字
     */
    public void signVoucher(String type, int number, User cashier) throws Exception {
        currentUserName = cashier.getName();
        if ("转".equals(type)) throw new Exception("转账凭证禁止出纳签字！");

        List<Voucher> all = dao.readVouchers();
        List<Voucher> entries = all.stream()
                .filter(v -> v.getType().equals(type) && v.getNumber() == number)
                .collect(Collectors.toList());
        if (entries.isEmpty()) throw new Exception("凭证不存在！");

        for (Voucher v : entries) {
            if (!v.isReviewed()) throw new Exception("未审核凭证禁止签字！");
            if (v.isSigned()) throw new Exception("凭证已签字！");
            if (v.getReviewer().equals(cashier.getName())) throw new Exception("审核人与出纳签字人不能为同一人！");
            v.setCashier(cashier.getName());
            v.setSigned(true);
        }
        dao.writeVouchers(all);
        log("出纳签字", "出纳签字凭证 " + type + number);
    }

    public void unsignVoucher(String type, int number) throws Exception {
        List<Voucher> all = dao.readVouchers();
        List<Voucher> entries = all.stream()
                .filter(v -> v.getType().equals(type) && v.getNumber() == number)
                .collect(Collectors.toList());
        if (entries.isEmpty()) throw new Exception("凭证不存在！");
        for (Voucher v : entries) {
            if (v.isPosted()) throw new Exception("凭证已记账，禁止取消签字！");
            v.setCashier("");
            v.setSigned(false);
        }
        dao.writeVouchers(all);
    }

    /**
     * 批量记账：所有已审核的凭证一次性记账
     */
    public int postVouchers(User poster) throws Exception {
        currentUserName = poster.getName();
        List<Voucher> all = dao.readVouchers();
        java.util.Set<String> postedKeys = new java.util.HashSet<>();
        java.util.Set<String> checkedKeys = new java.util.HashSet<>();
        for (Voucher v : all) {
            if (!v.isPosted() && v.isReviewed()) {
                if (!"转".equals(v.getType()) && !v.isSigned()) continue;
                String key = v.getType() + "|" + v.getNumber();
                if (!checkedKeys.contains(key)) {
                    checkedKeys.add(key);
                    if (v.getMaker() != null && v.getMaker().equals(poster.getName())) {
                        throw new Exception("凭证 " + key + " 的制单人与记账人不能为同一人！");
                    }
                    if (v.getReviewer() != null && v.getReviewer().equals(poster.getName())) {
                        throw new Exception("凭证 " + key + " 的审核人与记账人不能为同一人！");
                    }
                }
                v.setPoster(poster.getName());
                v.setPosted(true);
                postedKeys.add(key);
            }
        }
        if (postedKeys.isEmpty()) throw new Exception("没有可记账的凭证（需要已审核且收付款凭证需出纳签字）！");
        dao.writeVouchers(all);
        log("批量记账", "批量记账 " + postedKeys.size() + " 张凭证");
        return postedKeys.size();
    }

    /**
     * 看看还有没有没记账的凭证
     */
    public boolean hasUnpostedVouchers() throws Exception {
        return dao.readVouchers().stream().anyMatch(v -> v.isReviewed() && !v.isPosted());
    }

    /**
     * 查某个月是不是全记完了
     */
    public boolean isMonthFullyPosted(int month) throws Exception {
        return dao.readVouchers().stream()
                .filter(v -> v.getMonth() == month)
                .noneMatch(v -> !v.isPosted());
    }

    private boolean isMonthClosed(int month, List<Voucher> vouchers) {
        return vouchers.stream().anyMatch(v -> v.isClosed() && v.getCloseMonth() == month);
    }

    // ==================== Validation ====================

    private void validateVoucher(List<Voucher> entries, User currentUser) throws Exception {
        if (entries.isEmpty()) throw new Exception("凭证不能为空！");

        // 看借贷平不平
        double totalDebit = entries.stream().mapToDouble(Voucher::getDebitAmount).sum();
        double totalCredit = entries.stream().mapToDouble(Voucher::getCreditAmount).sum();
        if (Math.abs(totalDebit - totalCredit) > 0.005) {
            throw new Exception("借贷金额不平衡！借方合计: " + totalDebit + ", 贷方合计: " + totalCredit);
        }

        String type = entries.get(0).getType();
        // 检查凭证类型的规则
        for (Voucher v : entries) {
            if (!v.getType().equals(type)) throw new Exception("同一凭证编号下类别必须一致！");
        }

        if ("收".equals(type)) {
            // 收款凭证：借方必须要有现金或银行科目
            boolean hasCashBank = entries.stream()
                    .filter(v -> v.getDebitAmount() > 0)
                    .anyMatch(v -> isCashOrBank(v.getAccountCode()));
            if (!hasCashBank) throw new Exception("收款凭证借方必有现金/银行存款科目！");
        } else if ("付".equals(type)) {
            // 付款凭证：贷方必须要有现金或银行科目
            boolean hasCashBank = entries.stream()
                    .filter(v -> v.getCreditAmount() > 0)
                    .anyMatch(v -> isCashOrBank(v.getAccountCode()));
            if (!hasCashBank) throw new Exception("付款凭证贷方必有现金/银行存款科目！");
        } else if ("转".equals(type)) {
            // 转账凭证：不能有现金和银行科目
            boolean hasCashBank = entries.stream()
                    .anyMatch(v -> isCashOrBank(v.getAccountCode()));
            if (hasCashBank) throw new Exception("转账凭证不得包含现金/银行存款科目！");
        }
    }

    private boolean isCashOrBank(String code) {
        return "1001".equals(code) || (code != null && code.startsWith("1002"));
    }
}
