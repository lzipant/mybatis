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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 */
// SNIPPET_START GenericTokenParser
public class GenericTokenParser {

  private final String openToken;
  private final String closeToken;
  // 处理openToken和closeToken中间内容的处理器
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    // search open token
    // 找到第一个占位符
    int start = text.indexOf(openToken);
    if (start == -1) {
      return text;
    }
    char[] src = text.toCharArray();
    int offset = 0;
    final StringBuilder builder = new StringBuilder();
    StringBuilder expression = null;
    do {
      if (start > 0 && src[start - 1] == '\\') { // 转义字符的情况
        // this open token is escaped. remove the backslash and continue.
        builder.append(src, offset, start - offset - 1).append(openToken);
        // 跳过占位符
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token.
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          // 重置expression，便于处理下一个占位符
          expression.setLength(0);
        }
        // 将上次处理的末尾到当前这个开始占位符中间的内容拷贝
        builder.append(src, offset, start - offset);
        offset = start + openToken.length();
        // 找到结束占位符
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') { // 转义符的情况
            // this close token is escaped. remove the backslash and continue.
            expression.append(src, offset, end - offset - 1).append(closeToken);
            // 跳过该占位符
            offset = end + closeToken.length();
            // 寻找下一个结束占位符
            end = text.indexOf(closeToken, offset);
          } else {
            // 取得占位符中的参数名，并保存到expression中
            expression.append(src, offset, end - offset);
            break;
          }
        }
        /*
         * 如果没有找到结束占位符，则将剩下的内容直接全部拷贝，并退出循环，结束寻找
         * 所以说，如果只定义了开始占位符，没有定义结束占位符，并不会在MyBatis层面报错，而是让SQL本身就变成有错误的SQL。
         */
        if (end == -1) {
          // close token was not found.
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          /*
           * 取得参数对应的值，并拼接在sql中，占位符中可能并不是简单的参数名（比如还会设置jdbcType，typeHandler等），所以需要handler来处理
           */
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }
      // 从offset处开始往后找下一个开始占位符
      start = text.indexOf(openToken, offset);
    } while (start > -1);
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
// SNIPPET_END GenericTokenParser