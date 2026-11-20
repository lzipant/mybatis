package net.lzip.ant.pojo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DeptEmp {
    private String empNo;
    private String deptNo;
    private LocalDate fromDate;
    private LocalDate toDate;

    @Data
    static class DeptManager {
        private String empNo;
        private String deptNo;
        private LocalDate fromDate;
        private LocalDate toDate;
    }
}
