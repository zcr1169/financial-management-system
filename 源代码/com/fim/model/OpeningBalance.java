package com.fim.model;

public class OpeningBalance {
    private String accountCode;
    private String accountName;
    private String direction; // 借 or 贷
    private double amount;

    public OpeningBalance() {}

    public OpeningBalance(String accountCode, String accountName, String direction, double amount) {
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.direction = direction;
        this.amount = amount;
    }

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}
