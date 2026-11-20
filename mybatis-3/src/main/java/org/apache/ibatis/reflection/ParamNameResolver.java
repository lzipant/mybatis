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
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

public class ParamNameResolver {

  public static final String GENERIC_NAME_PREFIX = "param";

  private final boolean useActualParamName;

  /**
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  private final SortedMap<Integer, String> names;

  private boolean hasParamAnnotation;

  // SNIPPET_START constructor
  public ParamNameResolver(Configuration config, Method method) {
    // 判断是否使用实际的参数名称，默认为true
    this.useActualParamName = config.isUseActualParamName();
    // 获取方法参数类型
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 获取方法中各个参数的注解
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    final SortedMap<Integer, String> map = new TreeMap<>();
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    // 遍历参数
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 判断是不是特殊参数，如果是则跳过（里面判断了两种类型，大部分参数都不会是这里的特殊参数）
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }
      String name = null;
      // 遍历方法参数的注解
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        // 如果是@Param注解
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          // 获取通过注解配置的参数名称
          name = ((Param) annotation).value();
          break;
        }
      }
      // 没有使用@Param注解，或者使用了注解但没有指定参数名称
      if (name == null) {
        // @Param was not specified.
        // 如果使用参数名，则获取参数名称
        if (useActualParamName) {
          name = getActualParamName(method, paramIndex);
        }
        // 如果不使用实际参数名，那么使用参数序号作为参数名
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      // 保存参数名称
      map.put(paramIndex, name);
    }
    names = Collections.unmodifiableSortedMap(map);
  }
  // SNIPPET_END constructor

  // SNIPPET_START getActualParamName
  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }
  // SNIPPET_END getActualParamName

  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   *
   * @return the names
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * <p>
   * A single non-special parameter is returned without a name.
   * Multiple parameters are named using the naming rule.
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   *
   * @param args
   *          the args
   * @return the named params
   */
  // SNIPPET_START getNamedParams
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    if (args == null || paramCount == 0) {
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) { // 如果参数个数为1, 而且没有@Param注解
      Object value = args[names.firstKey()];
      // 根据参数值类型建立其他的名称映射
      return wrapToMapIfCollection(value, useActualParamName ? names.get(names.firstKey()) : null);
    } else {
      // 创建参数名和值的映射
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      // 遍历names
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        // 添加参数名-值键值对
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        // 创建通用参数名，即前缀（param）+ 序号
        final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
        // ensure not to overwrite parameter named with @Param
        /*
         * 如果通用参数名没有被使用，则添加进去；
         * 从这里也可以看出来，对于每个参数，都会有一个通用参数名；
         */
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }
  // SNIPPET_END getNamedParams

  /**
   * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
   *
   * @param object a parameter object
   * @param actualParamName an actual parameter name
   *                        (If specify a name, set an object to {@link ParamMap} with specified name)
   * @return a {@link ParamMap}
   * @since 3.5.5
   */
  // SNIPPET_START wrapToMapIfCollection
  public static Object wrapToMapIfCollection(Object object, String actualParamName) {
    // 如果是Collection及其子类型
    if (object instanceof Collection) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("collection", object);
      if (object instanceof List) { // 如果是list，则同时添加这种参数名映射
        map.put("list", object);
      }
      // 实际参数名映射
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    } else if (object != null && object.getClass().isArray()) { // 如果是数组类型
      ParamMap<Object> map = new ParamMap<>();
      map.put("array", object);
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }
    return object;
  }
  // SNIPPET_END wrapToMapIfCollection

}
