package com.fim.model;

public class AccountSubject {
    private String code;
    private String name;
    private String auxType;
    private String direction; // 借 or 贷
    private boolean bankAccount;
    private boolean journal;

    public AccountSubject() {}

    public AccountSubject(String code, String name, String auxType, String direction,
                          boolean bankAccount, boolean journal) {
        this.code = code;
        this.name = name;
        this.auxType = auxType;
        this.direction = direction;
        this.bankAccount = bankAccount;
        this.journal = journal;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAuxType() { return auxType; }
    public void setAuxType(String auxType) { this.auxType = auxType; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public boolean isBankAccount() { return bankAccount; }
    public void setBankAccount(boolean bankAccount) { this.bankAccount = bankAccount; }
    public boolean isJournal() { return journal; }
    public void setJournal(boolean journal) { this.journal = journal; }

    public boolean isCashAccount() {
        return "1001".equals(code);
    }

    public boolean isBankDepositAccount() {
        return code != null && code.startsWith("1002");
    }

    public boolean isCashOrBank() {
        return isCashAccount() || isBankDepositAccount();
    }

    public String getAssetLiabilityType() {
        if (code == null || code.isEmpty()) return "";
        char first = code.charAt(0);
        switch (first) {
            case '1': return "资产";
            case '2': return "负债";
            case '3': return "权益";
            case '4': return "成本";
            case '5': return "损益";
            case '6':
                if (code.length() >= 2) {
                    char second = code.charAt(1);
                    if (second >= '0' && second <= '3') return "收入";
                    else return "费用";
                }
                return "损益";
            default: return "";
        }
    }

    public int getLevel() {
        if (code == null || code.isEmpty()) return 0;
        return (code.length() - 1) / 2;
    }

    @Override
    public String toString() {
        return code + " " + name;
    }
}
