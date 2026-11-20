package net.lzip.ant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = "net.lzip.ant.dao")
public class MyBatisTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyBatisTestApplication.class, args);
    }

}
