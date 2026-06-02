package com.fim.model;

import java.util.Date;

public class Voucher {
    private String type;       // 收/付/转
    private int number;
    private Date date;
    private String description;
    private String accountCode;
    private String accountName;
    private double debitAmount;
    private double creditAmount;
    private String maker;      // 制单人
    private String reviewer;   // 审核人
    private String cashier;    // 出纳签字人
    private String poster;     // 记账人
    private boolean reviewed;
    private boolean signed;
    private boolean posted;
    private boolean closed;    // 是否已结账
    private int closeMonth;    // 结账月份

    public Voucher() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public double getDebitAmount() { return debitAmount; }
    public void setDebitAmount(double debitAmount) { this.debitAmount = debitAmount; }
    public double getCreditAmount() { return creditAmount; }
    public void setCreditAmount(double creditAmount) { this.creditAmount = creditAmount; }
    public String getMaker() { return maker; }
    public void setMaker(String maker) { this.maker = maker; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public String getCashier() { return cashier; }
    public void setCashier(String cashier) { this.cashier = cashier; }
    public String getPoster() { return poster; }
    public void setPoster(String poster) { this.poster = poster; }
    public boolean isReviewed() { return reviewed; }
    public void setReviewed(boolean reviewed) { this.reviewed = reviewed; }
    public boolean isSigned() { return signed; }
    public void setSigned(boolean signed) { this.signed = signed; }
    public boolean isPosted() { return posted; }
    public void setPosted(boolean posted) { this.posted = posted; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
    public int getCloseMonth() { return closeMonth; }
    public void setCloseMonth(int closeMonth) { this.closeMonth = closeMonth; }

    public int getMonth() {
        if (date == null) return 0;
        return date.toInstant().atZone(java.time.ZoneId.systemDefault()).getMonthValue();
    }

    public boolean isLocked() {
        return reviewed || signed || posted || closed;
    }
}
