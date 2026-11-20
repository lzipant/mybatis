package net.lzip.ant.dao;

import net.lzip.ant.pojo.Dept;

import java.util.List;

public interface DeptDao {
    List<Dept> selectAll();
}
