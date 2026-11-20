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
package org.apache.ibatis.mapping;

/**
 * Represents the content of a mapped statement read from an XML file or an annotation.
 * It creates the SQL that will be passed to the database out of the input parameter received from the user.
 *
 * @author Clinton Begin
 */
/*
 * XML Mapper文件中语句的SqlSource创建路径：
 * XMLStatementBuilder(parseStatementNode) -> XMLLanguageDriver(createSqlSource) -> （委托） -> XMLScriptBuilder(parseScriptNode)
 *  XMLScriptBuilder会判断XML内容中是否有动态SQL标签，然后创建DynamicSqlSource（动态）或RawSqlSource（静态）。
 */
public interface SqlSource {

  BoundSql getBoundSql(Object parameterObject);

}
