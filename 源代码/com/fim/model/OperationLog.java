package com.fim.model;

import java.text.SimpleDateFormat;
import java.util.Date;

public class OperationLog {
    private Date time;
    private String user;
    private String type;
    private String detail;

    public OperationLog() {}

    public OperationLog(String user, String type, String detail) {
        this.time = new Date();
        this.user = user;
        this.type = type;
        this.detail = detail;
    }

    public Date getTime() { return time; }
    public void setTime(Date time) { this.time = time; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getTimeStr() {
        if (time == null) return "";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time);
    }
}
