/*
 * Copyright 2010-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mybatis.spring.support;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.support.DaoSupport;

/**
 * Convenient super class for MyBatis SqlSession data access objects. It gives you access to the template which can then
 * be used to execute SQL methods.
 * <p>
 * This class needs a SqlSessionTemplate or a SqlSessionFactory. If both are set the SqlSessionFactory will be ignored.
 * <p>
 *
 * @author Putthiphong Boonphong
 * @author Eduardo Macarron
 *
 * @see #setSqlSessionFactory
 * @see #setSqlSessionTemplate
 * @see SqlSessionTemplate
 */
public abstract class SqlSessionDaoSupport extends DaoSupport {

  // SNIPPET_START fields
  private SqlSessionTemplate sqlSessionTemplate;
  // SNIPPET_END fields

  /**
   * Set MyBatis SqlSessionFactory to be used by this DAO. Will automatically create SqlSessionTemplate for the given
   * SqlSessionFactory.
   *
   * @param sqlSessionFactory
   *          a factory of SqlSession
   */
  // SNIPPET_START constructor
  // 会被setter注入，MyBatisAutoConfiguration中会注册SqlSessionFactory
  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    if (this.sqlSessionTemplate == null || sqlSessionFactory != this.sqlSessionTemplate.getSqlSessionFactory()) {
      /*
       * 从SqlSessionFactory创建SqlSessionTemplate，但是在Spring Boot中，
       * 因为会注入一个SqlSessionTemplate（在下面的setter方法上打个断点就知道了），所以这里设置的其实无效。
       */
      this.sqlSessionTemplate = createSqlSessionTemplate(sqlSessionFactory);
    }
  }
  // SNIPPET_END constructor

  /**
   * Create a SqlSessionTemplate for the given SqlSessionFactory. Only invoked if populating the DAO with a
   * SqlSessionFactory reference!
   * <p>
   * Can be overridden in subclasses to provide a SqlSessionTemplate instance with different configuration, or a custom
   * SqlSessionTemplate subclass.
   *
   * @param sqlSessionFactory
   *          the MyBatis SqlSessionFactory to create a SqlSessionTemplate for
   *
   * @return the new SqlSessionTemplate instance
   *
   * @see #setSqlSessionFactory
   */
  @SuppressWarnings("WeakerAccess")
  protected SqlSessionTemplate createSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    // 创建SqlSession实例
    return new SqlSessionTemplate(sqlSessionFactory);
  }

  /**
   * Return the MyBatis SqlSessionFactory used by this DAO.
   *
   * @return a factory of SqlSession
   */
  public final SqlSessionFactory getSqlSessionFactory() {
    return (this.sqlSessionTemplate != null ? this.sqlSessionTemplate.getSqlSessionFactory() : null);
  }

  /**
   * Set the SqlSessionTemplate for this DAO explicitly, as an alternative to specifying a SqlSessionFactory.
   *
   * @param sqlSessionTemplate
   *          a template of SqlSession
   *
   * @see #setSqlSessionFactory
   */
  // SNIPPET_START setSqlSessionTemplate
  public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
    this.sqlSessionTemplate = sqlSessionTemplate;
  }
  // SNIPPET_END setSqlSessionTemplate

  /**
   * Users should use this method to get a SqlSession to call its statement methods This is SqlSession is managed by
   * spring. Users should not commit/rollback/close it because it will be automatically done.
   *
   * @return Spring managed thread safe SqlSession
   */
  // SNIPPET_START getSqlSession
  public SqlSession getSqlSession() {
    return this.sqlSessionTemplate;
  }
  // SNIPPET_END getSqlSession

  /**
   * Return the SqlSessionTemplate for this DAO, pre-initialized with the SessionFactory or set explicitly.
   * <p>
   * <b>Note: The returned SqlSessionTemplate is a shared instance.</b> You may introspect its configuration, but not
   * modify the configuration (other than from within an {@link #initDao} implementation). Consider creating a custom
   * SqlSessionTemplate instance via {@code new SqlSessionTemplate(getSqlSessionFactory())}, in which case you're
   * allowed to customize the settings on the resulting instance.
   *
   * @return a template of SqlSession
   */
  public SqlSessionTemplate getSqlSessionTemplate() {
    return this.sqlSessionTemplate;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void checkDaoConfig() {
    notNull(this.sqlSessionTemplate, "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required");
  }

}
