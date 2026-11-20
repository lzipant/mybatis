package net.lzip.ant.pojo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class Employee {
    private int empNo;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private LocalDate hireDate;
    private Gender gender;
}
