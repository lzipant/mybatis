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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 */

// SNIPPET_START SqlSourceBuilder
public class SqlSourceBuilder extends BaseBuilder {

  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  // SNIPPET_START parse
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {

    // 创建参数映射处理器
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);

    /*
     * 这里只有处理#{}，那又在哪里处理${}呢？ 答案就是在TextSqlNode中。
     * 而且是这里之前就处理过了，originalSql就是各SqlNode处理之后的结果。
     */
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);

    /*
     * 重点：
     *  解析SQL语句，替换动态SQL中的占位符“#{}”，得到SQL的字符串形式;
     *  既然这里都已经得到了SQL的字符串形式，为什么下面还需要封装为SqlSource，以及后续为什么还要转为BoundSql对象呢？
     */
    String sql;
    if (configuration.isShrinkWhitespacesInSql()) { // 是否移除SQL中的空格
      sql = parser.parse(removeExtraWhitespaces(originalSql));
    } else {
      sql = parser.parse(originalSql);
    }
    // 封装解析结果
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }
  // SNIPPET_END parse

  public static String removeExtraWhitespaces(String original) {
    // 将SQL字符串根据空格拆分成多个有效内容部分
    StringTokenizer tokenizer = new StringTokenizer(original);
    StringBuilder builder = new StringBuilder();
    boolean hasMoreTokens = tokenizer.hasMoreTokens();
    while (hasMoreTokens) {
      builder.append(tokenizer.nextToken());
      hasMoreTokens = tokenizer.hasMoreTokens();
      if (hasMoreTokens) {
        // 每部分用单个空格拼接
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {

    private final List<ParameterMapping> parameterMappings = new ArrayList<>();
    private final Class<?> parameterType;
    private final MetaObject metaParameters;

    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    @Override
    public String handleToken(String content) {
      // 解析占位符内容
      parameterMappings.add(buildParameterMapping(content));
      // jdbc的参数占位符
      return "?";
    }

    private ParameterMapping buildParameterMapping(String content) {
      Map<String, String> propertiesMap = parseParameterMapping(content);
      // 获取参数名称
      String property = propertiesMap.get("property");
      /*
       * 判断参数的类型
       * 这里会根据实际参数类型来判断javaType，所以编码时没有指定javaType，MyBatis也能正确判断参数类型。
       */
      Class<?> propertyType;
      // 引用<bind/>标签定义的参数，参考DynamicSqlSource中调用SqlSourceBuilder的parse方法的第三个参数
      if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) { // 如果全局注册的类型处理器支持当前参数类型，最常见的情况
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        propertyType = java.sql.ResultSet.class;
      } else if (property == null || Map.class.isAssignableFrom(parameterType)) { // 如果参数是null或者参数类型被封装为Map（具体要参考上层MapperMethod参数处理器中的逻辑）
        propertyType = Object.class;
      } else {
        MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
        // 引用对象中的字段，如常使用的透传dto存controller层到dao层
        if (metaClass.hasGetter(property)) { // 如果有getter方法，则通过getter的返回值类型来判断参数类型
          propertyType = metaClass.getGetterType(property);
        } else { // 直接引用实参
          propertyType = Object.class;
        }
      }
      /*
       * 这里传入了propertyType，会被设置为javaType，如果没有显示指定，那么就是使用的这个自动推断出来的javaType。
       * 如果显式指定了，那么下面解析时会覆盖这里设置的。
       */
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
      /*
       * 遍历占位符中的每部分
       * 常见和常用的就是javaType，jdbcType以及typeHandler
       * 另外mode主要用于存储过程，numericScale用来调整小数的精度；
       * resultMap不清楚什么场景会被用到！
       */
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
        }
      }
      if (typeHandlerAlias != null) {
        /*
         * 解析并设置指定的typeHandler，
         * 如果没有指定，那么在下面的build过程中会尝试查找默认的typeHandler，一般常见的类型都是默认支持的，具体参考TypeHandlerRegistry。
         * 如果最后没有找到typeHandler，则会抛出异常。
         */
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }

}
// SNIPPET_END SqlSourceBuilder