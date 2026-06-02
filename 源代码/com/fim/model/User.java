package com.fim.model;

public class User {
    private String account;
    private String password;
    private String role;
    private String name;
    private int failedAttempts;
    private boolean locked;

    public static final String ROLE_ADMIN = "系统管理员";
    public static final String ROLE_MANAGER = "账套主管";
    public static final String ROLE_ACCOUNTANT = "财务";
    public static final String ROLE_CASHIER = "出纳";
    public static final String ROLE_NORMAL = "普通用户";

    public User() {}

    public User(String account, String password, String role, String name) {
        this.account = account;
        this.password = password;
        this.role = role;
        this.name = name;
        this.failedAttempts = 0;
        this.locked = false;
    }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    // admin仅拥有用户账号管理权限
    public boolean canManageAccounts() {
        return ROLE_MANAGER.equals(role);
    }

    public boolean canCreateVoucher() {
        return ROLE_MANAGER.equals(role) || ROLE_ACCOUNTANT.equals(role);
    }

    public boolean canReview() {
        return ROLE_MANAGER.equals(role);
    }

    public boolean canSign() {
        return ROLE_CASHIER.equals(role);
    }

    public boolean canPost() {
        return ROLE_MANAGER.equals(role) || ROLE_ACCOUNTANT.equals(role);
    }

    public boolean canClosePeriod() {
        return ROLE_MANAGER.equals(role) || ROLE_ACCOUNTANT.equals(role);
    }

    public boolean canManageUsers() {
        return ROLE_ADMIN.equals(role);
    }

    public boolean canViewReports() {
        return true;
    }

    public boolean canBackup() {
        return ROLE_MANAGER.equals(role);
    }

    public boolean canViewLogs() {
        return ROLE_ADMIN.equals(role) || ROLE_MANAGER.equals(role);
    }

    /** Users who can access the voucher processing panel (view/sign/review/post) */
    public boolean canAccessVoucherProcess() {
        return canCreateVoucher() || canReview() || canSign() || canPost();
    }
}
