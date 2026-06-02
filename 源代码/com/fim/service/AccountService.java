package com.fim.service;

import com.fim.dao.ExcelDAO;
import com.fim.model.AccountSubject;
import com.fim.model.OpeningBalance;

import java.util.*;
import java.util.stream.Collectors;

public class AccountService {
    private final ExcelDAO dao;
    private LogService logService;

    public AccountService(ExcelDAO dao) {
        this.dao = dao;
    }

    public void setLogService(LogService logService) {
        this.logService = logService;
    }

    private void log(String type, String detail) {
        if (logService != null) {
            logService.addLog("系统", type, detail);
        }
    }

    public List<AccountSubject> getAllSubjects() throws Exception {
        List<AccountSubject> list = dao.readAccountSubjects();
        list.sort(Comparator.comparing(AccountSubject::getCode));
        return list;
    }

    public AccountSubject findByCode(String code) throws Exception {
        return dao.readAccountSubjects().stream()
                .filter(a -> a.getCode().equals(code))
                .findFirst().orElse(null);
    }

    public List<AccountSubject> searchByName(String keyword) throws Exception {
        return dao.readAccountSubjects().stream()
                .filter(a -> a.getName().contains(keyword))
                .collect(Collectors.toList());
    }

    public void addSubject(AccountSubject subject) throws Exception {
        List<AccountSubject> list = dao.readAccountSubjects();
        if (list.stream().anyMatch(a -> a.getCode().equals(subject.getCode()))) {
            throw new Exception("科目编码 " + subject.getCode() + " 已存在！");
        }
        list.add(subject);
        list.sort(Comparator.comparing(AccountSubject::getCode));
        dao.writeAccountSubjects(list);
        syncToBalance(subject);
        log("新增科目", "新增科目 " + subject.getCode() + " " + subject.getName());
    }

    public void updateSubject(String oldCode, AccountSubject updated) throws Exception {
        List<AccountSubject> list = dao.readAccountSubjects();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getCode().equals(oldCode)) {
                list.set(i, updated);
                list.sort(Comparator.comparing(AccountSubject::getCode));
                dao.writeAccountSubjects(list);
                syncToBalance(updated);
                log("修改科目", "修改科目 " + oldCode + " -> " + updated.getCode() + " " + updated.getName());
                return;
            }
        }
        throw new Exception("科目 " + oldCode + " 不存在！");
    }

    public void deleteSubject(String code) throws Exception {
        List<AccountSubject> list = dao.readAccountSubjects();
        if (list.stream().anyMatch(a -> a.getCode().startsWith(code) && !a.getCode().equals(code))) {
            throw new Exception("该科目存在子科目，请先删除子科目！");
        }
        list.removeIf(a -> a.getCode().equals(code));
        list.sort(Comparator.comparing(AccountSubject::getCode));
        dao.writeAccountSubjects(list);
        removeFromBalance(code);
        log("删除科目", "删除科目 " + code);
    }

    private void syncToBalance(AccountSubject subject) throws Exception {
        List<OpeningBalance> balances = dao.readOpeningBalances();
        boolean found = false;
        for (OpeningBalance ob : balances) {
            if (ob.getAccountCode().equals(subject.getCode())) {
                ob.setAccountName(subject.getName());
                ob.setDirection(subject.getDirection());
                found = true;
                break;
            }
        }
        if (!found) {
            OpeningBalance ob = new OpeningBalance();
            ob.setAccountCode(subject.getCode());
            ob.setAccountName(subject.getName());
            ob.setDirection(subject.getDirection());
            ob.setAmount(0);
            balances.add(ob);
            balances.sort(Comparator.comparing(OpeningBalance::getAccountCode));
        }
        dao.writeOpeningBalances(balances);
    }

    private void removeFromBalance(String code) throws Exception {
        List<OpeningBalance> balances = dao.readOpeningBalances();
        balances.removeIf(b -> b.getAccountCode().equals(code));
        dao.writeOpeningBalances(balances);
    }

}
