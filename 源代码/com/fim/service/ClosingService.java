package com.fim.service;

import com.fim.dao.ExcelDAO;
import com.fim.model.*;

import java.util.*;
import java.util.Date;

public class ClosingService {
    private final ExcelDAO dao;

    public ClosingService(ExcelDAO dao) {
        this.dao = dao;
    }

    /**
     * 试算平衡检查，不平不能结账
     */
    public boolean checkTrialBalance() throws Exception {
        List<AccountSubject> subjects = dao.readAccountSubjects();
        List<OpeningBalance> balances = dao.readOpeningBalances();
        Set<String> leafCodes = new HashSet<>();
        for (AccountSubject s : subjects) {
            String code = s.getCode();
            if (code == null) continue;
            boolean hasChild = false;
            for (AccountSubject other : subjects) {
                if (other.getCode() != null && other.getCode().startsWith(code) && other.getCode().length() > code.length()) {
                    hasChild = true;
                    break;
                }
            }
            if (!hasChild) leafCodes.add(code);
        }

        double totalDebit = 0, totalCredit = 0;
        for (OpeningBalance ob : balances) {
            if (ob.getAccountCode() == null || !leafCodes.contains(ob.getAccountCode())) continue;
            if ("借".equals(ob.getDirection())) totalDebit += ob.getAmount();
            else if ("贷".equals(ob.getDirection())) totalCredit += ob.getAmount();
        }

        // 加上凭证的影响
        List<Voucher> vouchers = dao.readVouchers();
        for (Voucher v : vouchers) {
            totalDebit += v.getDebitAmount();
            totalCredit += v.getCreditAmount();
        }

        return Math.abs(totalDebit - totalCredit) < 0.01;
    }

    /**
     * 月末损益结转，把收入和费用科目的余额转到本年利润(4103)
     */
    public List<Voucher> carryForwardProfitLoss(int month, String maker) throws Exception {
        if (!checkTrialBalance()) throw new Exception("账面试算不平衡，无法进行损益结转！");

        List<AccountSubject> subjects = dao.readAccountSubjects();
        List<OpeningBalance> balances = dao.readOpeningBalances();
        List<Voucher> vouchers = dao.readVouchers();
        int nextNum = vouchers.stream().filter(v -> "转".equals(v.getType())).mapToInt(Voucher::getNumber).max().orElse(0) + 1;

        // 算收入总额（60xx-63xx，贷方余额）
        double incomeTotal = 0;
        for (AccountSubject s : subjects) {
            String code = s.getCode();
            if (isIncomeAccount(code) && s.getLevel() == 1) {
                incomeTotal += computeNetBalance(code, subjects, balances, vouchers);
            }
        }

        // 算费用总额（5xxx + 64xx-69xx）
        double expenseTotal = 0;
        for (AccountSubject s : subjects) {
            String code = s.getCode();
            if (isExpenseAccount(code) && s.getLevel() == 1) {
                expenseTotal += computeNetBalance(code, subjects, balances, vouchers);
            }
        }

        List<Voucher> carryForwardEntries = new ArrayList<>();

        // 把收入科目结平
        if (Math.abs(incomeTotal) > 0.01) {
            for (AccountSubject s : subjects) {
                String code = s.getCode();
                if (isIncomeAccount(code) && s.getLevel() == 1) {
                    double bal = computeNetBalance(code, subjects, balances, vouchers);
                    if (Math.abs(bal) > 0.01) {
                        Voucher v = new Voucher();
                        v.setType("转");
                        v.setNumber(nextNum);
                        v.setDate(new Date());
                        v.setDescription("月末损益结转");
                        v.setAccountCode(code);
                        v.setAccountName(s.getName());
                        if (bal > 0) {
                            v.setDebitAmount(Math.abs(bal));
                            v.setCreditAmount(0);
                        } else {
                            v.setDebitAmount(0);
                            v.setCreditAmount(Math.abs(bal));
                        }
                        v.setMaker(maker);
                        carryForwardEntries.add(v);
                    }
                }
            }
            // 贷：本年利润
            Voucher profitV = new Voucher();
            profitV.setType("转");
            profitV.setNumber(nextNum);
            profitV.setDate(new Date());
            profitV.setDescription("月末损益结转-收入转入");
            profitV.setAccountCode("4103");
            profitV.setAccountName("本年利润");
            profitV.setCreditAmount(Math.abs(incomeTotal));
            profitV.setMaker(maker);
            carryForwardEntries.add(profitV);

            nextNum++;
        }

        // 把费用科目结平
        if (Math.abs(expenseTotal) > 0.01) {
            for (AccountSubject s : subjects) {
                String code = s.getCode();
                if (isExpenseAccount(code) && s.getLevel() == 1) {
                    double bal = computeNetBalance(code, subjects, balances, vouchers);
                    if (Math.abs(bal) > 0.01) {
                        Voucher v = new Voucher();
                        v.setType("转");
                        v.setNumber(nextNum);
                        v.setDate(new Date());
                        v.setDescription("月末损益结转");
                        v.setAccountCode(code);
                        v.setAccountName(s.getName());
                        v.setCreditAmount(Math.abs(bal));
                        v.setMaker(maker);
                        carryForwardEntries.add(v);
                    }
                }
            }
            // 借：本年利润
            Voucher profitV = new Voucher();
            profitV.setType("转");
            profitV.setNumber(nextNum);
            profitV.setDate(new Date());
            profitV.setDescription("月末损益结转-费用转入");
            profitV.setAccountCode("4103");
            profitV.setAccountName("本年利润");
            profitV.setDebitAmount(Math.abs(expenseTotal));
            profitV.setMaker(maker);
            carryForwardEntries.add(profitV);
        }

        return carryForwardEntries;
    }

    /**
     * 月结：把这个月的凭证全标成已结账
     */
    public void closeMonth(int month) throws Exception {
        List<Voucher> all = dao.readVouchers();
        boolean found = false;
        for (Voucher v : all) {
            if (v.getMonth() == month) {
                if (!v.isPosted()) throw new Exception("存在未记账凭证，禁止结账！");
                if (v.isClosed()) throw new Exception(month + "月份已结账，禁止重复结账！");
                found = true;
            }
        }
        if (!found) throw new Exception(month + "月份无凭证可结账！");

        // 再查一次试算平衡
        if (!checkTrialBalance()) throw new Exception("账面试算不平衡，禁止结账！");

        for (Voucher v : all) {
            if (v.getMonth() == month) {
                v.setClosed(true);
                v.setCloseMonth(month);
            }
        }
        dao.writeVouchers(all);
    }

    /**
     * 拿到已结账的月份列表
     */
    public Set<Integer> getClosedMonths() throws Exception {
        Set<Integer> closed = new HashSet<>();
        for (Voucher v : dao.readVouchers()) {
            if (v.isClosed()) closed.add(v.getCloseMonth());
        }
        return closed;
    }

    private double computeNetBalance(String code, List<AccountSubject> subjects,
                                      List<OpeningBalance> balances, List<Voucher> vouchers) {
        // 只算子科目的期初余额（跳过父科目，防止重复）
        double openAmt = 0;
        for (OpeningBalance ob : balances) {
            String obCode = ob.getAccountCode();
            if (obCode == null || !obCode.startsWith(code)) continue;
            if (obCode.equals(code)) continue; // 不要父科目，直接用子科目的
            openAmt += "借".equals(ob.getDirection()) ? ob.getAmount() : -ob.getAmount();
        }
        // 没有子科目的话就用自己
        if (openAmt == 0) {
            for (OpeningBalance ob : balances) {
                if (code.equals(ob.getAccountCode())) {
                    openAmt = "借".equals(ob.getDirection()) ? ob.getAmount() : -ob.getAmount();
                    break;
                }
            }
        }

        String dir = "借";
        for (AccountSubject s : subjects) {
            if (s.getCode().equals(code)) {
                if (s.getDirection() != null) dir = s.getDirection();
                break;
            }
        }

        double debitSum = vouchers.stream()
            .filter(v -> v.getAccountCode() != null && v.getAccountCode().startsWith(code))
            .mapToDouble(Voucher::getDebitAmount).sum();
        double creditSum = vouchers.stream()
            .filter(v -> v.getAccountCode() != null && v.getAccountCode().startsWith(code))
            .mapToDouble(Voucher::getCreditAmount).sum();

        if ("借".equals(dir)) return openAmt + debitSum - creditSum;
        else return openAmt + creditSum - debitSum;
    }

    private static boolean isIncomeAccount(String code) {
        if (code == null || code.length() < 2) return false;
        char c1 = code.charAt(0);
        char c2 = code.charAt(1);
        return c1 == '6' && c2 >= '0' && c2 <= '3'; // 60xx到63xx是收入
    }

    private static boolean isExpenseAccount(String code) {
        if (code == null || code.length() < 2) return false;
        char c1 = code.charAt(0);
        if (c1 == '5') return true; // 5开头的都是费用
        if (c1 == '6') {
            char c2 = code.charAt(1);
            return c2 >= '4' && c2 <= '9'; // 64xx到69xx也是费用
        }
        return false;
    }
}
