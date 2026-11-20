package net.lzip.ant.dao;

import net.lzip.ant.config.plugin.PageHelper;
import net.lzip.ant.pojo.Dept;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class TestDeptDao {

    @Autowired
    private DeptDao deptDao;

    @Test
    public void selectAll() {
        PageHelper.page(1, 2);
        List<Dept> depts = deptDao.selectAll();
        for (Dept dept : depts) {
            System.out.println(dept);
        }
    }
}
