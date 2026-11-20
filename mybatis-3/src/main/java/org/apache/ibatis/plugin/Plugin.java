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
package org.apache.ibatis.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.util.MapUtil;

/**
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

  private final Object target;
  private final Interceptor interceptor;
  private final Map<Class<?>, Set<Method>> signatureMap;

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  public static Object wrap(Object target, Interceptor interceptor) {
    // 获取拦截器中的方法集合
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
    // 获取目标对象的类型
    Class<?> type = target.getClass();
    // 获取目标类型实现的且被拦截的接口类型
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
    // 如果类实现了接口以及有命中的拦截器
    if (interfaces.length > 0) {
      // 创建代理对象
      return Proxy.newProxyInstance(
          type.getClassLoader(),
          interfaces,
          // 创建执行器
          new Plugin(target, interceptor, signatureMap));
    }
    // 如果类没有实现接口，或者没有拦截器命中该对象类型，则返回原对象
    return target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // 获取目标方法所在类的被拦截的方法集合
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      // 如果拦截器能够拦截该方法
      if (methods != null && methods.contains(method)) {
        // 则执行拦截器的拦截逻辑
        return interceptor.intercept(new Invocation(target, method, args));
      }
      // 否则直接调用目标方法
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    // 获取拦截器类上的@Intercepts注解
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
    // issue #251
    if (interceptsAnnotation == null) {
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
    }
    // 获取该注解的value属性，即@Signature注解数组
    Signature[] sigs = interceptsAnnotation.value();
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
    // 遍历@Signature注解
    for (Signature sig : sigs) {
      // 按照类进行分类，每个类对应一个方法集合
      Set<Method> methods = MapUtil.computeIfAbsent(signatureMap, sig.type(), k -> new HashSet<>());
      try {
        // 通过反射来获取方法
        Method method = sig.type().getMethod(sig.method(), sig.args());
        // 将方法添加到集合中
        methods.add(method);
      } catch (NoSuchMethodException e) { // 如果配置的所要拦截的方法不存在，则抛出异常
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }
    return signatureMap;
  }

  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    Set<Class<?>> interfaces = new HashSet<>();
    while (type != null) {
      // 遍历类所实现的接口
      for (Class<?> c : type.getInterfaces()) {
        // 如果拦截器能够拦截包含该接口类型，所以拦截器上@Intercepts注解中的@Signature注解中的type字段需要是接口，否则不生效
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      // 继续判断父类
      type = type.getSuperclass();
    }
    return interfaces.toArray(new Class<?>[0]);
  }

}
