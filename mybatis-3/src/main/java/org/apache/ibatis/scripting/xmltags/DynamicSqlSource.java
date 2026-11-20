/*
 *    Copyright 2009-2022 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
// SNIPPET_START DynamicSqlSource
/*
 * 用于描述XML中的动态SQL，比如在SQL中用了变量
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  private final SqlNode rootSqlNode;

  /*
   * 在解析mapper时，在XMLScriptBuilder中的parseScriptNode方法中会调用该构造器。
   */
  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // 创建一个上下文对象，用来解析SQL
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    // 调用各SqlNode来处理动态SQL的各个部分
    rootSqlNode.apply(context);
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    /*
     * 判断参数对象的类型，在上层MapperMethod中利用了参数解析器来将mapper方法的实参进行处理，
     * 因为参数有各种情况，所以是以Object一路传下来的。
     *
     * 这里从实参来获取参数类型，而不是配置中的parameterType属性，是和RawSqlSource不同的地方。
     * 原因是DynamicSqlSource每次运行时解析，而RawSqlSource是解析时运行一次解析后不再重复解析。
     */
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    /*
     * 创建SqlSource对象，用于创建BoundSql对象
     * 当前类就是一个SqlSource，为什么还要创建SqlSource？
     * 别忘了，所有的SqlNode中都是没有处理#{}的，所以这里创建的SqlSource是在处理占位符#{}。
     * 这里返回的SqlSource是StaticSqlSource类型的！
     */
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    // 正式创建BoundSql对象，再次强调，这里的sqlSource是StaticSqlSource
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // 将参数映射也设置到boundSql对象中
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
// SNIPPET_END DynamicSqlSource