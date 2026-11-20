package net.lzip.ant.pojo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class Salary {
    private String empNo;
    private int salary;
    private LocalDate fromDate;
    private LocalDate toDate;
}
