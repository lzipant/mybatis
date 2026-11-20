package net.lzip.ant.config.plugin;

import lombok.AllArgsConstructor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Properties;

@Component
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class PageHelper implements Interceptor {

    @AllArgsConstructor
    static class Page {
        int pageNo;
        int pageSize;
    }

    private final static ThreadLocal<Page> pageThreadLocal = new ThreadLocal<>();

    public static void page(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1) {
            throw new IllegalArgumentException("pageNo and pageSize must be greater than 0");
        }
        Page page = new Page(pageNo, pageSize);
        pageThreadLocal.set(page);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();

        Page page = pageThreadLocal.get();
        if (page == null) {
            return invocation.proceed();
        }
        String sql = statementHandler.getBoundSql().getSql();
        sql += " LIMIT " + ((page.pageNo - 1) * page.pageSize) + ", " + page.pageSize;

        Field sqlField = statementHandler.getBoundSql().getClass().getDeclaredField("sql");
        sqlField.setAccessible(true);
        sqlField.set(statementHandler.getBoundSql(), sql);

        pageThreadLocal.remove();

        return invocation.proceed();
    }

    @Override
    public void setProperties(Properties properties) {
        Interceptor.super.setProperties(properties);
    }
}
