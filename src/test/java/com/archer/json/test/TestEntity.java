package com.archer.json.test;

import java.util.Date;
import java.sql.Timestamp;
import java.time.*;

public class TestEntity {

    private String name;

    private LocalDateTime createTime;

    private LocalDate importTime;

    private Date exportTime;

    private TestStatusMsg statusMsg;

    private TestStatus status;

    public String getName() {
        return name;
    }
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    public LocalDate getImportTime() {
        return importTime;
    }
    public Date getExportTime() {
        return exportTime;
    }
    public TestStatusMsg getStatusMsg() {
        return statusMsg;
    }
    public TestStatus getStatus() {
        return status;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    public void setImportTime(LocalDate importTime) {
        this.importTime = importTime;
    }
    public void setExportTime(Date exportTime) {
        this.exportTime = exportTime;
    }
    public void setStatusMsg(TestStatusMsg statusMsg) {
        this.statusMsg = statusMsg;
    }
    public void setStatus(TestStatus status) {
        this.status = status;
    }

    public static enum TestStatus {
        MSG_PENDING("待处理"),
        MSG_SUCCESS("成功"),
        MSG_FAILED("失败");

        private final String msg;
        TestStatus(String msg) {
            this.msg = msg;
        }
        public String getMsg() {
            return msg;
        }
    }
    

    public static class TestStatusMsg {
        private final TestStatus status;
        private final String msg;
        public TestStatusMsg(TestStatus status, String msg) {
            this.status = status;
            this.msg = msg;
        }
        public TestStatus getStatus() {
            return status;
        }
        public String getMsg() {
            return msg;
        }
    }
}
