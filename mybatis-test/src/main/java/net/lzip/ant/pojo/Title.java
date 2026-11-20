package net.lzip.ant.pojo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class Title {
    private String empNo;
    private String title;
    private LocalDate fromDate;
    private LocalDate toDate;
}
