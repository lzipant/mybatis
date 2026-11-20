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
package org.apache.ibatis.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * References a generic type.
 *
 * @param <T> the referenced type
 * @since 3.1.0
 * @author Simone Tripodi
 */
public abstract class TypeReference<T> {

  private final Type rawType;

  protected TypeReference() {
    rawType = getSuperclassTypeParameter(getClass());
  }

  Type getSuperclassTypeParameter(Class<?> clazz) {
    // 获取父类型
    Type genericSuperclass = clazz.getGenericSuperclass();
    // 如果父类型是具体的类，不是泛型类，或者继承泛型父类时没有指定泛型实参
    if (genericSuperclass instanceof Class) {
      // try to climb up the hierarchy until meet something useful
      // 如果父类还不是TypeReference，则递归查找
      if (TypeReference.class != genericSuperclass) {
        // 递归处理父类
        return getSuperclassTypeParameter(clazz.getSuperclass());
      }

      // 如果在继承链上找到了TypeReference，但没有该类的泛型信息，则抛出异常
      throw new TypeException("'" + getClass() + "' extends TypeReference but misses the type parameter. "
        + "Remove the extension or add a type parameter to it.");
    }

    // 获取泛型实参
    Type rawType = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
    // TODO remove this when Reflector is fixed to return Types
    // 如果泛型实参又是一个参数化类型，比如List<String>
    if (rawType instanceof ParameterizedType) {
      // 则提取其原始类型，比如List.class
      rawType = ((ParameterizedType) rawType).getRawType();
    }

    return rawType;
  }

  public final Type getRawType() {
    return rawType;
  }

  @Override
  public String toString() {
    return rawType.toString();
  }

}
