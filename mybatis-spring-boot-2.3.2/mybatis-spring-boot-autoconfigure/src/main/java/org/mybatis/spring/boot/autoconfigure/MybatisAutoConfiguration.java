/*
 *    Copyright 2015-2022 the original author or authors.
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
package org.mybatis.spring.boot.autoconfigure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.beans.PropertyDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@link EnableAutoConfiguration Auto-Configuration} for Mybatis. Contributes a {@link SqlSessionFactory} and a
 * {@link SqlSessionTemplate}. If {@link org.mybatis.spring.annotation.MapperScan} is used, or a configuration file is
 * specified as a property, those will be considered, otherwise this auto-configuration will attempt to register mappers
 * based on the interface definitions in or under the root auto-configuration package.
 *
 * @author Eddú Meléndez
 * @author Josh Long
 * @author Kazuki Shimizu
 * @author Eduardo Macarrón
 */
@org.springframework.context.annotation.Configuration
@ConditionalOnClass({ SqlSessionFactory.class, SqlSessionFactoryBean.class })
@ConditionalOnSingleCandidate(DataSource.class)
/*
 * 配置会封装成MybatisProperties对象
 * 该注解的工作原理是怎样的？
 */
@EnableConfigurationProperties(MybatisProperties.class)
@AutoConfigureAfter({ DataSourceAutoConfiguration.class, MybatisLanguageDriverAutoConfiguration.class })
public class MybatisAutoConfiguration implements InitializingBean {

  private static final Logger logger = LoggerFactory.getLogger(MybatisAutoConfiguration.class);

  private final MybatisProperties properties;

  private final Interceptor[] interceptors;

  private final TypeHandler[] typeHandlers;

  private final LanguageDriver[] languageDrivers;

  private final ResourceLoader resourceLoader;

  private final DatabaseIdProvider databaseIdProvider;

  // 可以创建ConfigurationCustomizer来对Configuration对象做扩展，比如设置拦截器
  private final List<ConfigurationCustomizer> configurationCustomizers;

  private final List<SqlSessionFactoryBeanCustomizer> sqlSessionFactoryBeanCustomizers;

  // 会构造器注入这些依赖
  public MybatisAutoConfiguration(MybatisProperties properties, ObjectProvider<Interceptor[]> interceptorsProvider,
      ObjectProvider<TypeHandler[]> typeHandlersProvider, ObjectProvider<LanguageDriver[]> languageDriversProvider,
      ResourceLoader resourceLoader, ObjectProvider<DatabaseIdProvider> databaseIdProvider,
      ObjectProvider<List<ConfigurationCustomizer>> configurationCustomizersProvider,
      ObjectProvider<List<SqlSessionFactoryBeanCustomizer>> sqlSessionFactoryBeanCustomizers) {
    this.properties = properties;
    this.interceptors = interceptorsProvider.getIfAvailable();
    this.typeHandlers = typeHandlersProvider.getIfAvailable();
    this.languageDrivers = languageDriversProvider.getIfAvailable();
    this.resourceLoader = resourceLoader;
    this.databaseIdProvider = databaseIdProvider.getIfAvailable();
    this.configurationCustomizers = configurationCustomizersProvider.getIfAvailable();
    this.sqlSessionFactoryBeanCustomizers = sqlSessionFactoryBeanCustomizers.getIfAvailable();
  }

  @Override
  public void afterPropertiesSet() {
    checkConfigFileExists();
  }

  private void checkConfigFileExists() {
    // 需要检查主配置文件是否存在（默认是false）且配置了主配置文件的位置
    if (this.properties.isCheckConfigLocation() && StringUtils.hasText(this.properties.getConfigLocation())) {
      // 加载主配置文件
      Resource resource = this.resourceLoader.getResource(this.properties.getConfigLocation());
      Assert.state(resource.exists(),
          "Cannot find config location: " + resource + " (please add config file or check your Mybatis configuration)");
    }
  }

  // SNIPPET_START sqlSessionFactory
  /*
   * 创建sqlSessionFactory
   */
  @Bean
  @ConditionalOnMissingBean
  public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
    // 创建FactoryBean
    SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
    // 设置数据源
    factory.setDataSource(dataSource);
    // 设置MyBatis使用的虚拟文件系统类型
    factory.setVfs(SpringBootVFS.class);
    // 如果配置了主配置文件的位置
    if (StringUtils.hasText(this.properties.getConfigLocation())) {
      // 设置主配置文件的位置
      factory.setConfigLocation(this.resourceLoader.getResource(this.properties.getConfigLocation()));
    }
    // 将Configuration对象设置到factory的属性中
    applyConfiguration(factory);
    if (this.properties.getConfigurationProperties() != null) {
      factory.setConfigurationProperties(this.properties.getConfigurationProperties());
    }
    // 将拦截器设置到工厂bean中，最后会设置到Configuration对象中，如果设置了的话
    if (!ObjectUtils.isEmpty(this.interceptors)) {
      factory.setPlugins(this.interceptors);
    }
    if (this.databaseIdProvider != null) {
      factory.setDatabaseIdProvider(this.databaseIdProvider);
    }
    if (StringUtils.hasLength(this.properties.getTypeAliasesPackage())) {
      factory.setTypeAliasesPackage(this.properties.getTypeAliasesPackage());
    }
    if (this.properties.getTypeAliasesSuperType() != null) {
      factory.setTypeAliasesSuperType(this.properties.getTypeAliasesSuperType());
    }
    // 设置类型处理器的包名，如果设置了的话
    if (StringUtils.hasLength(this.properties.getTypeHandlersPackage())) {
      factory.setTypeHandlersPackage(this.properties.getTypeHandlersPackage());
    }
    // 设置类型处理器，如果设置了的话
    if (!ObjectUtils.isEmpty(this.typeHandlers)) {
      factory.setTypeHandlers(this.typeHandlers);
    }
    // 解析mapper文件的位置
    Resource[] mapperLocations = this.properties.resolveMapperLocations();
    if (!ObjectUtils.isEmpty(mapperLocations)) {
      // 设置mapper文件的位置
      factory.setMapperLocations(mapperLocations);
    }
    // 获取SqlSessionFactoryBean类中的字段名称
    Set<String> factoryPropertyNames = Stream
        .of(new BeanWrapperImpl(SqlSessionFactoryBean.class).getPropertyDescriptors()).map(PropertyDescriptor::getName)
        .collect(Collectors.toSet());
    // 获取默认的脚本语言驱动，默认是null
    Class<? extends LanguageDriver> defaultLanguageDriver = this.properties.getDefaultScriptingLanguageDriver();
    // 默认情况下this.languageDrivers是null，所以不满足这里的if条件
    if (factoryPropertyNames.contains("scriptingLanguageDrivers") && !ObjectUtils.isEmpty(this.languageDrivers)) {
      // Need to mybatis-spring 2.0.2+
      factory.setScriptingLanguageDrivers(this.languageDrivers);
      if (defaultLanguageDriver == null && this.languageDrivers.length == 1) {
        defaultLanguageDriver = this.languageDrivers[0].getClass();
      }
    }
    if (factoryPropertyNames.contains("defaultScriptingLanguageDriver")) {
      // Need to mybatis-spring 2.0.2+
      factory.setDefaultScriptingLanguageDriver(defaultLanguageDriver);
    }
    // 默认没有操作
    applySqlSessionFactoryBeanCustomizers(factory);
    // 创建SqlSessionFactory
    return factory.getObject();
  }
  // SNIPPET_END sqlSessionFactory

  private void applyConfiguration(SqlSessionFactoryBean factory) {
    // 从配置属性中获取Configuration对象
    Configuration configuration = this.properties.getConfiguration();
    /*
     * 默认情况下configuration就是null，所以满足条件;
     * 另外，如果指定了configLocation，那么不会创建Configuration对象
     */
    if (configuration == null && !StringUtils.hasText(this.properties.getConfigLocation())) {
      // 创建默认的空Configuration对象
      configuration = new Configuration();
    }
    // 默认configurationCustomizers是null， 所以这里不满足条件
    if (configuration != null && !CollectionUtils.isEmpty(this.configurationCustomizers)) {
      // 如果存在配置自定义器，则应用
      for (ConfigurationCustomizer customizer : this.configurationCustomizers) {
        customizer.customize(configuration);
      }
    }
    // 将Configuration对象设置到factory中
    factory.setConfiguration(configuration);
  }

  private void applySqlSessionFactoryBeanCustomizers(SqlSessionFactoryBean factory) {
    // 默认情况下这里不满足条件
    if (!CollectionUtils.isEmpty(this.sqlSessionFactoryBeanCustomizers)) {
      for (SqlSessionFactoryBeanCustomizer customizer : this.sqlSessionFactoryBeanCustomizers) {
        customizer.customize(factory);
      }
    }
  }

  // SNIPPET_START sqlSessionTemplate
  @Bean
  @ConditionalOnMissingBean
  public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    ExecutorType executorType = this.properties.getExecutorType();
    /*
     * 创建SqlSessionTemplate对象，后续创建Mapper代理对象会被用到。
     */
    if (executorType != null) {
      return new SqlSessionTemplate(sqlSessionFactory, executorType);
    } else {
      return new SqlSessionTemplate(sqlSessionFactory);
    }
  }
  // SNIPPET_END sqlSessionTemplate

  /**
   * This will just scan the same base package as Spring Boot does. If you want more power, you can explicitly use
   * {@link org.mybatis.spring.annotation.MapperScan} but this will get typed mappers working correctly, out-of-the-box,
   * similar to using Spring Data JPA repositories.
   */
  /*
   * 该类实现了ImportBeanDefinitionRegistrar，虽然下面有个内部类@Import了该类，但是在默认情况下其条件注解不满足，所以不会执行。
   * 所以默认情况下，虽然该类会被加载到容器中，但是不会被被调用registerBeanDefinitions方法。
   *
   * 该类的主要目的是注册MapperScannerConfigurer这个bean，既然这里不会注册，那这个bean又是怎么被加载的？
   * 实际上，如果使用了@MapperScan注解，则会通过MapperScannerRegistrar来注册这个bean；
   * 如果没有使用@MapperScan注解，才会通过该类来注册这个bean；
   */
  public static class AutoConfiguredMapperScannerRegistrar
      implements BeanFactoryAware, EnvironmentAware, ImportBeanDefinitionRegistrar {

    private BeanFactory beanFactory;
    private Environment environment;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

      if (!AutoConfigurationPackages.has(this.beanFactory)) {
        logger.debug("Could not determine auto-configuration package, automatic mapper scanning disabled.");
        return;
      }

      logger.debug("Searching for mappers annotated with @Mapper");

      List<String> packages = AutoConfigurationPackages.get(this.beanFactory);
      if (logger.isDebugEnabled()) {
        packages.forEach(pkg -> logger.debug("Using auto-configuration base package '{}'", pkg));
      }

      BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
      /*
       * 设置一些属性，这些属性会被设置为MapperScannerConfigurer的属性值。
       */
      builder.addPropertyValue("processPropertyPlaceHolders", true);

      /*
       * 设置过滤接口注解类型，这样只有被@Mapper注解修饰的接口才会被注册；
       * 注意如果使用了@MapperScan注解，那么就不需要用@Mapper注解，
       *    因为默认情况下这种方式创建的MapperScannerConfigurer没有设置annotationClass属性，
       *    当然如果设置了特定的注解，那么也只会过滤被此注解修饰的接口。
       */
      builder.addPropertyValue("annotationClass", Mapper.class);
      // 将集合拼接成一条字符串
      builder.addPropertyValue("basePackage", StringUtils.collectionToCommaDelimitedString(packages));
      BeanWrapper beanWrapper = new BeanWrapperImpl(MapperScannerConfigurer.class);
      Set<String> propertyNames = Stream.of(beanWrapper.getPropertyDescriptors()).map(PropertyDescriptor::getName)
          .collect(Collectors.toSet());
      if (propertyNames.contains("lazyInitialization")) {
        // Need to mybatis-spring 2.0.2+
        builder.addPropertyValue("lazyInitialization", "${mybatis.lazy-initialization:false}");
      }
      if (propertyNames.contains("defaultScope")) {
        // Need to mybatis-spring 2.0.6+
        builder.addPropertyValue("defaultScope", "${mybatis.mapper-default-scope:}");
      }

      // for spring-native
      // 是否在扫描mapper的过程中注入sqlSession，默认是true
      boolean injectSqlSession = environment.getProperty("mybatis.inject-sql-session-on-mapper-scan", Boolean.class,
          Boolean.TRUE);
      if (injectSqlSession && this.beanFactory instanceof ListableBeanFactory) {
        ListableBeanFactory listableBeanFactory = (ListableBeanFactory) this.beanFactory;
        // 从bean工厂中获取sqlSessionTemplate和SqlSessionFactory的bean名称
        Optional<String> sqlSessionTemplateBeanName = Optional
            .ofNullable(getBeanNameForType(SqlSessionTemplate.class, listableBeanFactory));
        Optional<String> sqlSessionFactoryBeanName = Optional
            .ofNullable(getBeanNameForType(SqlSessionFactory.class, listableBeanFactory));

        // 如果存在这两种bean，则分别设置到Configurer这个bean的属性中
        if (sqlSessionTemplateBeanName.isPresent() || !sqlSessionFactoryBeanName.isPresent()) {
          builder.addPropertyValue("sqlSessionTemplateBeanName",
              sqlSessionTemplateBeanName.orElse("sqlSessionTemplate"));
        } else {
          builder.addPropertyValue("sqlSessionFactoryBeanName", sqlSessionFactoryBeanName.get());
        }
      }
      builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

      // 加载MapperScannerConfigurer
      registry.registerBeanDefinition(MapperScannerConfigurer.class.getName(), builder.getBeanDefinition());
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
    }

    @Override
    public void setEnvironment(Environment environment) {
      this.environment = environment;
    }

    private String getBeanNameForType(Class<?> type, ListableBeanFactory factory) {
      String[] beanNames = factory.getBeanNamesForType(type);
      return beanNames.length > 0 ? beanNames[0] : null;
    }

  }

  /**
   * If mapper registering configuration or mapper scanning configuration not present, this configuration allow to scan
   * mappers based on the same component-scanning path as Spring Boot itself.
   */
  @org.springframework.context.annotation.Configuration
  @Import(AutoConfiguredMapperScannerRegistrar.class)
  /*
   * 如果使用了@MapperScan注解，那么该配置类不会生效（但还是会被加载和解析，只是在condition的evaluate的时候通不过，不会被创建bean），
   * 因为@MapperScan引入了MapperScannerRegistrar类，而该类又会引入，下面的两种类型的bean存在
   */
  @ConditionalOnMissingBean({ MapperFactoryBean.class, MapperScannerConfigurer.class })
  public static class MapperScannerRegistrarNotFoundConfiguration implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
      logger.debug(
          "Not found configuration for registering mapper bean using @MapperScan, MapperFactoryBean and MapperScannerConfigurer.");
    }

  }

}
