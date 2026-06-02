package com.fim.service;

import com.fim.dao.ExcelDAO;
import com.fim.model.OperationLog;
import com.fim.model.User;

import java.util.ArrayList;
import java.util.List;

public class AuthService {
    private final ExcelDAO dao;
    private User currentUser;
    private LogService logService;

    public AuthService(ExcelDAO dao) {
        this.dao = dao;
    }

    public void setLogService(LogService logService) {
        this.logService = logService;
    }

    public User login(String account, String password) throws Exception {
        List<User> users = dao.readUsers();
        User found = null;
        for (User u : users) {
            if (u.getAccount().equals(account)) {
                found = u;
                break;
            }
        }
        if (found == null) {
            throw new Exception("用户名不存在！");
        }
        if (found.isLocked()) {
            throw new Exception("账号已被锁定，请联系管理员解锁！");
        }
        if (!found.getPassword().equals(password)) {
            found.setFailedAttempts(found.getFailedAttempts() + 1);
            if (found.getFailedAttempts() >= 5) {
                found.setLocked(true);
                dao.writeUsers(users);
                throw new Exception("口令错误！密码连续错误5次，账号已被锁定！");
            }
            dao.writeUsers(users);
            throw new Exception("口令错误！剩余尝试次数: " + (5 - found.getFailedAttempts()));
        }
        // 登录成功，清零失败次数
        found.setFailedAttempts(0);
        found.setLocked(false);
        dao.writeUsers(users);
        currentUser = found;
        if (logService != null) {
            logService.addLog(found.getName(), "用户登录", "用户 " + found.getName() + " 登录系统");
        }
        return found;
    }

    public void logout() {
        if (logService != null && currentUser != null) {
            logService.addLog(currentUser.getName(), "用户登出", "用户 " + currentUser.getName() + " 退出系统");
        }
        currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public List<User> getAllUsers() throws Exception {
        return dao.readUsers();
    }

    public void addUser(User user) throws Exception {
        List<User> users = dao.readUsers();
        for (User u : users) {
            if (u.getAccount().equals(user.getAccount())) {
                throw new Exception("账号已存在！");
            }
        }
        user.setFailedAttempts(0);
        user.setLocked(false);
        users.add(user);
        writeUsersToSheet(users);
        if (logService != null && currentUser != null) {
            logService.addLog(currentUser.getName(), "新增用户", "新增用户 " + user.getAccount() + "(" + user.getName() + ") 角色: " + user.getRole());
        }
    }

    public void updateUser(User user) throws Exception {
        List<User> users = dao.readUsers();
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getAccount().equals(user.getAccount())) {
                // 保留原来的锁定状态
                user.setFailedAttempts(users.get(i).getFailedAttempts());
                user.setLocked(users.get(i).isLocked());
                users.set(i, user);
                writeUsersToSheet(users);
                if (logService != null && currentUser != null) {
                    logService.addLog(currentUser.getName(), "修改用户", "修改用户 " + user.getAccount() + "(" + user.getName() + ")");
                }
                return;
            }
        }
        throw new Exception("用户不存在！");
    }

    public void deleteUser(String account) throws Exception {
        if (currentUser != null && currentUser.getAccount().equals(account)) {
            throw new Exception("不能删除当前登录用户！");
        }
        List<User> users = dao.readUsers();
        users.removeIf(u -> u.getAccount().equals(account));
        writeUsersToSheet(users);
        if (logService != null && currentUser != null) {
            logService.addLog(currentUser.getName(), "删除用户", "删除用户 " + account);
        }
    }

    public void unlockUser(String account) throws Exception {
        List<User> users = dao.readUsers();
        for (User u : users) {
            if (u.getAccount().equals(account)) {
                u.setFailedAttempts(0);
                u.setLocked(false);
                writeUsersToSheet(users);
                if (logService != null && currentUser != null) {
                    logService.addLog(currentUser.getName(), "解锁账号", "解锁用户 " + account + "(" + u.getName() + ")");
                }
                return;
            }
        }
        throw new Exception("用户不存在！");
    }

    private void writeUsersToSheet(List<User> users) throws Exception {
        dao.writeUsers(users);
    }
}
