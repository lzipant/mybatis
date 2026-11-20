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

import java.util.regex.Pattern;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.scripting.ScriptingException;
import org.apache.ibatis.type.SimpleTypeRegistry;

/**
 * @author Clinton Begin
 */
// SNIPPET_START TextSqlNode
/*
 * TextSqlNode的设计目标是为了处理占位符${}，这也是和StaticTextSqlNode不同的地方。
 * 只要包含${}，就会被认为是动态SQL，而只包含#{}（且没有其他动态SQL XML节点）则不会。
 */
public class TextSqlNode implements SqlNode {
  private final String text;
  private final Pattern injectionFilter;

  public TextSqlNode(String text) {
    this(text, null);
  }

  public TextSqlNode(String text, Pattern injectionFilter) {
    this.text = text;
    this.injectionFilter = injectionFilter;
  }

  // SNIPPET_START isDynamic
  public boolean isDynamic() {
    DynamicCheckerTokenParser checker = new DynamicCheckerTokenParser();
    /*
     * 创建占位符为“${}”的解析器
     * 只要文本中存在占位符就能说明是动态SQL，但是这里只考虑了”${}“，而没有考虑”#{}“，为什么会这样？
     * 这就是MyBatis的专门设计吧，对#{}的处理应该在其他地方。
     */
    GenericTokenParser parser = createParser(checker);
    parser.parse(text);
    return checker.isDynamic();
  }
  // SNIPPET_END isDynamic

  @Override
  public boolean apply(DynamicContext context) {
    GenericTokenParser parser = createParser(new BindingTokenParser(context, injectionFilter));
    context.appendSql(parser.parse(text));
    return true;
  }

  // SNIPPET_START createParser
  private GenericTokenParser createParser(TokenHandler handler) {
    return new GenericTokenParser("${", "}", handler);
  }
  // SNIPPET_END createParser

  private static class BindingTokenParser implements TokenHandler {

    private DynamicContext context;
    private Pattern injectionFilter;

    public BindingTokenParser(DynamicContext context, Pattern injectionFilter) {
      this.context = context;
      this.injectionFilter = injectionFilter;
    }

    @Override
    public String handleToken(String content) {
      // 获取待绑定的参数
      Object parameter = context.getBindings().get("_parameter");
      if (parameter == null) {
        context.getBindings().put("value", null);
      } else if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) { // 如果参数是简单类型
        context.getBindings().put("value", parameter);
      }
      // 执行参数绑定
      Object value = OgnlCache.getValue(content, context.getBindings());
      String srtValue = value == null ? "" : String.valueOf(value); // issue #274 return "" instead of "null"
      checkInjection(srtValue);
      return srtValue;
    }

    private void checkInjection(String value) {
      if (injectionFilter != null && !injectionFilter.matcher(value).matches()) {
        throw new ScriptingException("Invalid input. Please conform to regex" + injectionFilter.pattern());
      }
    }
  }

  // SNIPPET_START DynamicCheckerTokenParser
  private static class DynamicCheckerTokenParser implements TokenHandler {

    private boolean isDynamic;

    public DynamicCheckerTokenParser() {
      // Prevent Synthetic Access
    }

    public boolean isDynamic() {
      return isDynamic;
    }

    @Override
    public String handleToken(String content) {
      // 只要存在token，就说明是动态SQL。
      this.isDynamic = true;
      /* 这里怎么返回null？岂不是会导致SQL字符串中被拼接“null”？
       * 该TokenHandler只有在TextSqlNode的isDynamic方法中会被使用，而且仅用来判断是否是动态SQL，不涉及SQL字符串的拼接。
       *  在拼接SQL的时候调的是BindingTokenParser这个处理器，而不是当前处理器。
       */
      return null;
    }
  }
  // SNIPPET_END DynamicCheckerTokenParser

}
// SNIPPET_END TextSqlNode