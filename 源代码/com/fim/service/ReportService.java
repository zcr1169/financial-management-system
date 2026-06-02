package com.fim.service;

import com.fim.dao.ExcelDAO;
import com.fim.model.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class ReportService {
    private final ExcelDAO dao;

    public ReportService(ExcelDAO dao) {
        this.dao = dao;
    }

    /**
     * 科目总账：按月份统计借贷发生额
     */
    public List<String[]> getGeneralLedger(String accountCode) throws Exception {
        Map<Integer, Double[]> monthSummary = new LinkedHashMap<>();
        List<Voucher> vouchers = dao.readVouchers().stream()
                .filter(v -> v.getAccountCode() != null && v.getAccountCode().startsWith(accountCode))
                .collect(Collectors.toList());

        for (Voucher v : vouchers) {
            int month = v.getMonth();
            monthSummary.putIfAbsent(month, new Double[]{0.0, 0.0});
            Double[] sums = monthSummary.get(month);
            sums[0] += v.getDebitAmount();
            sums[1] += v.getCreditAmount();
        }

        OpeningBalance ob = findBalance(accountCode);
        double openingAmt = ob != null ? ob.getAmount() : 0;
        String direction = ob != null ? ob.getDirection() : "借";

        List<String[]> result = new ArrayList<>();
        result.add(new String[]{"月份", "期初余额", "借方发生额", "贷方发生额", "期末余额", "方向"});

        double runningBalance = openingAmt;
        for (Map.Entry<Integer, Double[]> entry : monthSummary.entrySet()) {
            int month = entry.getKey();
            double debit = entry.getValue()[0];
            double credit = entry.getValue()[1];
            if ("借".equals(direction)) {
                runningBalance = openingAmt + debit - credit;
            } else {
                runningBalance = openingAmt - debit + credit;
            }
            result.add(new String[]{
                month + "月",
                String.format("%.2f", openingAmt),
                String.format("%.2f", debit),
                String.format("%.2f", credit),
                String.format("%.2f", Math.abs(runningBalance)),
                runningBalance >= 0 ? "借" : "贷"
            });
            openingAmt = runningBalance;
        }
        return result;
    }

    /**
     * 科目明细账：某科目的所有凭证记录
     */
    public List<String[]> getSubLedger(String accountCode) throws Exception {
        List<Voucher> vouchers = dao.readVouchers().stream()
                .filter(v -> v.getAccountCode() != null && v.getAccountCode().startsWith(accountCode))
                .collect(Collectors.toList());

        List<String[]> result = new ArrayList<>();
        result.add(new String[]{"日期", "凭证类别", "编号", "摘要", "借方金额", "贷方金额", "方向", "余额"});

        OpeningBalance ob = findBalance(accountCode);
        double runningBalance = ob != null ? ob.getAmount() : 0;
        String direction = ob != null ? ob.getDirection() : "借";

        for (Voucher v : vouchers) {
            if ("借".equals(direction)) {
                runningBalance += v.getDebitAmount() - v.getCreditAmount();
            } else {
                runningBalance += v.getCreditAmount() - v.getDebitAmount();
            }
            result.add(new String[]{
                v.getDate() != null ? new SimpleDateFormat("yyyy年MM月dd日").format(v.getDate()) : "",
                v.getType(),
                String.valueOf(v.getNumber()),
                v.getDescription(),
                v.getDebitAmount() > 0 ? String.format("%.2f", v.getDebitAmount()) : "",
                v.getCreditAmount() > 0 ? String.format("%.2f", v.getCreditAmount()) : "",
                runningBalance >= 0 ? "借" : "贷",
                String.format("%.2f", Math.abs(runningBalance))
            });
        }
        return result;
    }

    /**
     * 现金日记账
     */
    public List<String[]> getCashJournal() throws Exception {
        return getDailyJournal("1001");
    }

    /**
     * 银行日记账
     */
    public List<String[]> getBankJournal() throws Exception {
        List<String[]> result = new ArrayList<>();
        result.add(new String[]{"日期", "凭证类别", "编号", "摘要", "对方科目", "借方金额", "贷方金额", "余额"});

        List<Voucher> vouchers = dao.readVouchers().stream()
                .filter(v -> v.getAccountCode() != null && v.getAccountCode().startsWith("1002"))
                .collect(Collectors.toList());

        double runningBalance = 0;
        for (OpeningBalance ob : dao.readOpeningBalances()) {
            if ("1002".equals(ob.getAccountCode())) {
                runningBalance += ob.getAmount();
                break;
            }
        }

        for (Voucher v : vouchers) {
            runningBalance += v.getDebitAmount() - v.getCreditAmount();
            // Find opposite entry in the same voucher
            String counterCode = findCounterAccount(v);
            result.add(new String[]{
                v.getDate() != null ? new SimpleDateFormat("yyyy年MM月dd日").format(v.getDate()) : "",
                v.getType(),
                String.valueOf(v.getNumber()),
                v.getDescription(),
                counterCode,
                v.getDebitAmount() > 0 ? String.format("%.2f", v.getDebitAmount()) : "",
                v.getCreditAmount() > 0 ? String.format("%.2f", v.getCreditAmount()) : "",
                String.format("%.2f", Math.abs(runningBalance))
            });
        }
        return result;
    }

    private List<String[]> getDailyJournal(String accountCode) throws Exception {
        List<String[]> result = new ArrayList<>();
        result.add(new String[]{"日期", "凭证类别", "编号", "摘要", "对方科目", "借方金额", "贷方金额", "余额"});

        List<Voucher> vouchers = dao.readVouchers().stream()
                .filter(v -> accountCode.equals(v.getAccountCode()))
                .collect(Collectors.toList());

        OpeningBalance ob = findBalance(accountCode);
        double runningBalance = ob != null ? ob.getAmount() : 0;

        for (Voucher v : vouchers) {
            runningBalance += v.getDebitAmount() - v.getCreditAmount();
            String counterCode = findCounterAccount(v);
            result.add(new String[]{
                v.getDate() != null ? new SimpleDateFormat("yyyy年MM月dd日").format(v.getDate()) : "",
                v.getType(),
                String.valueOf(v.getNumber()),
                v.getDescription(),
                counterCode,
                v.getDebitAmount() > 0 ? String.format("%.2f", v.getDebitAmount()) : "",
                v.getCreditAmount() > 0 ? String.format("%.2f", v.getCreditAmount()) : "",
                String.format("%.2f", Math.abs(runningBalance))
            });
        }
        return result;
    }

    /**
     * Trial balance / 发生额及余额表
     */
    public List<String[]> getTrialBalance() throws Exception {
        List<AccountSubject> subjects = dao.readAccountSubjects();
        List<OpeningBalance> balances = dao.readOpeningBalances();
        List<Voucher> vouchers = dao.readVouchers();

        List<String[]> result = new ArrayList<>();
        result.add(new String[]{"科目编码", "科目名称", "期初借方", "期初贷方", "本期借方", "本期贷方", "期末借方", "期末贷方"});

        double sumOpenDebit = 0, sumOpenCredit = 0;
        double sumCurrDebit = 0, sumCurrCredit = 0;
        double sumEndDebit = 0, sumEndCredit = 0;

        for (AccountSubject subj : subjects) {
            String code = subj.getCode();
            double openAmt = 0;
            String openDir = subj.getDirection();
            for (OpeningBalance ob : balances) {
                if (ob.getAccountCode().equals(code)) {
                    openAmt = ob.getAmount();
                    if (ob.getDirection() != null) openDir = ob.getDirection();
                    break;
                }
            }
            double debitSum = vouchers.stream().filter(v -> v.getAccountCode() != null && v.getAccountCode().startsWith(code)).mapToDouble(Voucher::getDebitAmount).sum();
            double creditSum = vouchers.stream().filter(v -> v.getAccountCode() != null && v.getAccountCode().startsWith(code)).mapToDouble(Voucher::getCreditAmount).sum();

            double openDebit = "借".equals(openDir) ? openAmt : 0;
            double openCredit = "贷".equals(openDir) ? openAmt : 0;
            double endDebit = openDebit + debitSum - creditSum;
            double endCredit = openCredit + creditSum - debitSum;

            if (endDebit < 0) { endCredit = -endDebit; endDebit = 0; }
            if (endCredit < 0) { endDebit = -endCredit; endCredit = 0; }

            if (subj.getLevel() == 1) {
                sumOpenDebit += openDebit;
                sumOpenCredit += openCredit;
                sumCurrDebit += debitSum;
                sumCurrCredit += creditSum;
                sumEndDebit += endDebit;
                sumEndCredit += endCredit;
            }

            // Only show leaf accounts or those with non-zero amounts
            if (debitSum > 0 || creditSum > 0 || openAmt > 0) {
                result.add(new String[]{
                    code, subj.getName(),
                    openDebit > 0 ? String.format("%.2f", openDebit) : "",
                    openCredit > 0 ? String.format("%.2f", openCredit) : "",
                    debitSum > 0 ? String.format("%.2f", debitSum) : "",
                    creditSum > 0 ? String.format("%.2f", creditSum) : "",
                    endDebit > 0 ? String.format("%.2f", endDebit) : "",
                    endCredit > 0 ? String.format("%.2f", endCredit) : ""
                });
            }
        }
        result.add(new String[]{"合计", "", String.format("%.2f", sumOpenDebit), String.format("%.2f", sumOpenCredit),
                String.format("%.2f", sumCurrDebit), String.format("%.2f", sumCurrCredit),
                String.format("%.2f", sumEndDebit), String.format("%.2f", sumEndCredit)});
        return result;
    }

    /**
     * 资产负债表（左边资产，右边负债+权益）
     */
    public List<String[]> getBalanceSheet() throws Exception {
        List<AccountSubject> subjects = dao.readAccountSubjects();
        List<OpeningBalance> balances = dao.readOpeningBalances();
        List<Voucher> vouchers = dao.readVouchers();

        List<String[]> result = new ArrayList<>();
        result.add(new String[]{"项目", "期初余额", "期末余额", "项目", "期初余额", "期末余额"});

        // --- 资产这边 ---
        result.add(new String[]{"【资产】", "", "", "【负债】", "", ""});

        double assetTotalOpen = 0, assetTotalEnd = 0;
        double liabilityTotalOpen = 0, liabilityTotalEnd = 0;
        double equityTotalOpen = 0, equityTotalEnd = 0;

        // 收集资产项目
        List<String[]> assetItems = new ArrayList<>();
        for (AccountSubject s : subjects) {
            if (s.getCode().startsWith("1") && s.getLevel() == 1) {
                double openBal = getOpeningBalance(s.getCode(), balances);
                double endBal = openBal + getPeriodChange(s.getCode(), vouchers, "借")
                        - getPeriodChange(s.getCode(), vouchers, "贷");
                if (Math.abs(openBal) > 0.001 || Math.abs(endBal) > 0.001) {
                    assetItems.add(new String[]{s.getCode() + " " + s.getName(),
                        String.format("%.2f", Math.abs(openBal)),
                        String.format("%.2f", Math.abs(endBal)), "", "", ""});
                    assetTotalOpen += Math.abs(openBal);
                    assetTotalEnd += Math.abs(endBal);
                }
            }
        }

        // 收集负债项目
        List<String[]> liabilityItems = new ArrayList<>();
        for (AccountSubject s : subjects) {
            if (s.getCode().startsWith("2") && s.getLevel() == 1) {
                double openBal = getOpeningBalance(s.getCode(), balances);
                double endBal = openBal + getPeriodChange(s.getCode(), vouchers, "贷")
                        - getPeriodChange(s.getCode(), vouchers, "借");
                if (Math.abs(openBal) > 0.001 || Math.abs(endBal) > 0.001) {
                    liabilityItems.add(new String[]{"", "", "",
                        s.getCode() + " " + s.getName(),
                        String.format("%.2f", Math.abs(openBal)),
                        String.format("%.2f", Math.abs(endBal))});
                    liabilityTotalOpen += Math.abs(openBal);
                    liabilityTotalEnd += Math.abs(endBal);
                }
            }
        }

        // 收集权益项目
        List<String[]> equityItems = new ArrayList<>();
        for (AccountSubject s : subjects) {
            if ((s.getCode().startsWith("3") || s.getCode().startsWith("4")) && s.getLevel() == 1) {
                double openBal = getOpeningBalance(s.getCode(), balances);
                double endBal = openBal + getPeriodChange(s.getCode(), vouchers, "贷")
                        - getPeriodChange(s.getCode(), vouchers, "借");
                if (Math.abs(openBal) > 0.001 || Math.abs(endBal) > 0.001) {
                    equityItems.add(new String[]{"", "", "",
                        s.getCode() + " " + s.getName(),
                        String.format("%.2f", Math.abs(openBal)),
                        String.format("%.2f", Math.abs(endBal))});
                    equityTotalOpen += Math.abs(openBal);
                    equityTotalEnd += Math.abs(endBal);
                }
            }
        }

        // 合并行
        int maxRows = Math.max(assetItems.size() + 2,
                liabilityItems.size() + equityItems.size() + 3);
        for (int i = 0; i < maxRows; i++) {
            String assetCol1 = "", assetCol2 = "", assetCol3 = "";
            String libCol1 = "", libCol2 = "", libCol3 = "";

            if (i < assetItems.size()) {
                String[] a = assetItems.get(i);
                assetCol1 = a[0]; assetCol2 = a[1]; assetCol3 = a[2];
            } else if (i == maxRows - 1) {
                assetCol1 = "资产总计"; assetCol2 = String.format("%.2f", assetTotalOpen);
                assetCol3 = String.format("%.2f", assetTotalEnd);
            }

            int liabSize = liabilityItems.size();
            if (i < liabSize) {
                String[] l = liabilityItems.get(i);
                libCol1 = l[3]; libCol2 = l[4]; libCol3 = l[5];
            } else if (i == liabSize) {
                libCol1 = "负债合计"; libCol2 = String.format("%.2f", liabilityTotalOpen);
                libCol3 = String.format("%.2f", liabilityTotalEnd);
            } else if (i == liabSize + 1) {
                libCol1 = "【所有者权益】";
            } else if (i > liabSize + 1 && i - liabSize - 2 < equityItems.size()) {
                String[] e = equityItems.get(i - liabSize - 2);
                libCol1 = e[3]; libCol2 = e[4]; libCol3 = e[5];
            } else if (i == maxRows - 1) {
                libCol1 = "负债和所有者权益总计";
                libCol2 = String.format("%.2f", liabilityTotalOpen + equityTotalOpen);
                libCol3 = String.format("%.2f", liabilityTotalEnd + equityTotalEnd);
            }

            if (!assetCol1.isEmpty() || !assetCol2.isEmpty() || !assetCol3.isEmpty()
                || !libCol1.isEmpty() || !libCol2.isEmpty() || !libCol3.isEmpty()) {
                result.add(new String[]{assetCol1, assetCol2, assetCol3, libCol1, libCol2, libCol3});
            }
        }

        return result;
    }

    /**
     * 利润表
     */
    public List<String[]> getProfitStatement() throws Exception {
        List<AccountSubject> subjects = dao.readAccountSubjects();
        List<Voucher> vouchers = dao.readVouchers();

        List<String[]> result = new ArrayList<>();
        result.add(new String[]{"项目", "行次", "本月金额", "本年累计"});

        // 营业收入（60xx开头的科目）
        double revenue = getCategoryTotal(vouchers, "贷", '6', '0', '3');
        double revenueAccum = revenue; // In a real system, accumulate from Jan to current month
        result.add(new String[]{"一、营业收入", "", "", ""});
        result.add(new String[]{"  主营业务收入", "", String.format("%.2f", revenue), String.format("%.2f", revenueAccum)});

        double totalIncome = revenue;

        // 成本和费用
        double mainCost = getCategoryTotal(vouchers, "借", '6', '4', '4'); // 6401-6403
        double otherCost = getCategoryTotal(vouchers, "借", '6', '4', '9'); // 6404-6405
        double taxSurcharge = 0;
        result.add(new String[]{"二、营业成本", "", "", ""});
        result.add(new String[]{"  主营业务成本", "", String.format("%.2f", mainCost), String.format("%.2f", mainCost)});

        double sellingExpense = getCategoryTotal(vouchers, "借", '6', '6', '6'); // 6601
        double adminExpense = getCategoryTotal(vouchers, "借", '6', '6', '6'); // 6602
        double financeExpense = 0; // 6603

        // 费用加起来
        double expenseTotal = 0;
        for (Voucher v : vouchers) {
            String code = v.getAccountCode();
            if (code != null && code.startsWith("6") && code.length() >= 2) {
                char second = code.charAt(1);
                if (second >= '4' || (second == '6')) {
                    expenseTotal += v.getDebitAmount();
                    expenseTotal -= v.getCreditAmount();
                }
                if (second >= '0' && second <= '3') {
                    expenseTotal -= v.getDebitAmount();
                    expenseTotal += v.getCreditAmount();
                }
            }
        }

        double operatingProfit = totalIncome - expenseTotal;
        result.add(new String[]{"三、营业利润（亏损以\"-\"号填列）", "",
            String.format("%.2f", operatingProfit), String.format("%.2f", operatingProfit)});

        double nonOpIncome = 0;
        double nonOpExpense = 0;
        result.add(new String[]{"  加：营业外收入", "",
            String.format("%.2f", nonOpIncome), String.format("%.2f", nonOpIncome)});
        result.add(new String[]{"  减：营业外支出", "",
            String.format("%.2f", nonOpExpense), String.format("%.2f", nonOpExpense)});

        double profitBeforeTax = operatingProfit + nonOpIncome - nonOpExpense;
        result.add(new String[]{"四、利润总额", "",
            String.format("%.2f", profitBeforeTax), String.format("%.2f", profitBeforeTax)});

        double incomeTax = profitBeforeTax * 0.25;
        result.add(new String[]{"  减：所得税费用", "",
            String.format("%.2f", incomeTax), String.format("%.2f", incomeTax)});

        double netProfit = profitBeforeTax - incomeTax;
        result.add(new String[]{"五、净利润", "",
            String.format("%.2f", netProfit), String.format("%.2f", netProfit)});

        return result;
    }

    private double getCategoryTotal(List<Voucher> vouchers, String direction,
                                     char first, char secondStart, char secondEnd) {
        return vouchers.stream()
            .filter(v -> {
                String code = v.getAccountCode();
                if (code == null || code.length() < 2) return false;
                return code.charAt(0) == first
                    && code.charAt(1) >= secondStart && code.charAt(1) <= secondEnd;
            })
            .mapToDouble(v -> "借".equals(direction) ? v.getDebitAmount() - v.getCreditAmount()
                                                     : v.getCreditAmount() - v.getDebitAmount())
            .sum();
    }

    private double getOpeningBalance(String code, List<OpeningBalance> balances) {
        for (OpeningBalance ob : balances) {
            if (ob.getAccountCode().equals(code)) {
                return ob.getAmount();
            }
        }
        return 0;
    }

    private double getPeriodChange(String code, List<Voucher> vouchers, String side) {
        return vouchers.stream()
            .filter(v -> v.getAccountCode() != null && v.getAccountCode().startsWith(code))
            .mapToDouble(v -> "借".equals(side) ? v.getDebitAmount() : v.getCreditAmount())
            .sum();
    }

    private double computeAccountBalance(String code, List<AccountSubject> subjects,
                                          List<OpeningBalance> balances, List<Voucher> vouchers) {
        double openAmt = 0;
        String dir = "借";
        for (OpeningBalance ob : balances) {
            if (ob.getAccountCode().equals(code)) {
                openAmt = ob.getAmount();
                if (ob.getDirection() != null) dir = ob.getDirection();
                break;
            }
        }
        double debitSum = vouchers.stream().filter(v -> code.equals(v.getAccountCode())).mapToDouble(Voucher::getDebitAmount).sum();
        double creditSum = vouchers.stream().filter(v -> code.equals(v.getAccountCode())).mapToDouble(Voucher::getCreditAmount).sum();

        // Sum children balances
        for (AccountSubject child : subjects) {
            if (child.getCode().startsWith(code) && !child.getCode().equals(code)) {
                debitSum += vouchers.stream().filter(v -> child.getCode().equals(v.getAccountCode())).mapToDouble(Voucher::getDebitAmount).sum();
                creditSum += vouchers.stream().filter(v -> child.getCode().equals(v.getAccountCode())).mapToDouble(Voucher::getCreditAmount).sum();
                for (OpeningBalance ob : balances) {
                    if (ob.getAccountCode().equals(child.getCode())) {
                        if ("借".equals(ob.getDirection())) openAmt += ob.getAmount();
                        else openAmt -= ob.getAmount();
                    }
                }
            }
        }

        if ("借".equals(dir)) return openAmt + debitSum - creditSum;
        else return openAmt + creditSum - debitSum;
    }

    private OpeningBalance findBalance(String accountCode) throws Exception {
        return dao.readOpeningBalances().stream()
                .filter(b -> b.getAccountCode().equals(accountCode))
                .findFirst().orElse(null);
    }

    private String findCounterAccount(Voucher currentEntry) throws Exception {
        List<Voucher> all = dao.readVouchers();
        List<AccountSubject> subjects = dao.readAccountSubjects();
        Map<String, String> codeToName = new HashMap<>();
        for (AccountSubject s : subjects) {
            codeToName.put(s.getCode(), s.getName());
        }
        for (Voucher v : all) {
            if (v.getType().equals(currentEntry.getType())
                && v.getNumber() == currentEntry.getNumber()
                && !v.getAccountCode().equals(currentEntry.getAccountCode())) {
                String name = v.getAccountName();
                if (name == null || name.isEmpty()) {
                    name = codeToName.getOrDefault(v.getAccountCode(), "");
                }
                return v.getAccountCode() + " " + name;
            }
        }
        return "";
    }
}
