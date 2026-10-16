package com.hmong.dbi18n.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.hmong.dbi18n.core.DataTranslationRule;
import com.hmong.dbi18n.core.DataTranslationRuleRegistry;
import com.hmong.dbi18n.core.Messages;
import com.hmong.dbi18n.interceptor.DataTranslationInnerInterceptor;
import com.hmong.dbi18n.interceptor.DataTranslationRequestInterceptor;
import com.hmong.dbi18n.locale.AcceptLanguageLocaleResolver;
import com.hmong.dbi18n.store.DataTranslationSwitchService;
import com.hmong.dbi18n.store.DataTranslationSwitchStore;
import com.hmong.dbi18n.store.DefaultDataTranslationSwitchService;
import com.hmong.dbi18n.store.PropertyDataTranslationSwitchStore;
import com.hmong.dbi18n.store.RedisDataTranslationSwitchStore;
import com.hmong.dbi18n.storage.DataTranslationMaintenanceService;
import com.hmong.dbi18n.storage.DataTranslationOperatorProvider;
import com.hmong.dbi18n.storage.DataTranslationRepository;
import com.hmong.dbi18n.storage.DataTranslationSourceProvider;
import com.hmong.dbi18n.storage.DefaultDataTranslationMaintenanceService;

/**
 * hmong-db-i18n 自动装配
 * <p>
 * 装配内容：
 * <ol>
 *     <li>登记表与 SQL 改写插件（自动插到 {@link MybatisPlusInterceptor} 内层拦截器最前面，早于分页）；</li>
 *     <li>运行时开关（Redis 或本地静态）与请求快照拦截器；</li>
 *     <li>译文维护服务（Spring JDBC 访问翻译表，无需 Mapper 扫描）；</li>
 *     <li>可选的默认语言解析器、PageHelper 顺序助手、启动期插件链校验。</li>
 * </ol>
 *
 * @author yanx
 */
@AutoConfiguration
@AutoConfigureBefore(WebMvcAutoConfiguration.class)
@EnableConfigurationProperties(HmongDbI18nProperties.class)
@ConditionalOnProperty(prefix = "hmong.db-i18n", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HmongDbI18nAutoConfiguration
{
    private static final Logger log = LoggerFactory.getLogger(HmongDbI18nAutoConfiguration.class);

    /**
     * 登记表：使用方以纯数据 Bean 的方式提供 {@link DataTranslationRule}
     */
    @Bean
    @ConditionalOnMissingBean
    public DataTranslationRuleRegistry dataTranslationRuleRegistry(ObjectProvider<DataTranslationRule> rules)
    {
        List<DataTranslationRule> ruleList = rules.orderedStream().toList();
        log.info(Messages.RULE_COUNT, ruleList.size());
        return new DataTranslationRuleRegistry(ruleList);
    }

    /**
     * SQL 改写插件
     */
    @Bean
    @ConditionalOnMissingBean
    public DataTranslationInnerInterceptor dataTranslationInnerInterceptor(DataTranslationRuleRegistry registry)
    {
        return new DataTranslationInnerInterceptor(registry);
    }

    /**
     * 使用方没有自定义 MybatisPlusInterceptor 时，由本库提供一个只含翻译插件的拦截器
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataTranslationInnerInterceptor innerInterceptor)
    {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(innerInterceptor);
        return interceptor;
    }

    /**
     * 把翻译插件放到内层拦截器列表最前面，保证早于分页插件执行
     */
    @Bean
    public static BeanPostProcessor dataTranslationInnerInterceptorRegistrar(
            ObjectProvider<DataTranslationInnerInterceptor> innerInterceptorProvider)
    {
        return new BeanPostProcessor()
        {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName)
            {
                if (bean instanceof MybatisPlusInterceptor mybatisPlusInterceptor)
                {
                    DataTranslationInnerInterceptor inner = innerInterceptorProvider.getIfAvailable();
                    if (inner == null)
                    {
                        return bean;
                    }
                    List<InnerInterceptor> existing = new ArrayList<>(mybatisPlusInterceptor.getInterceptors());
                    existing.removeIf(DataTranslationInnerInterceptor.class::isInstance);
                    List<InnerInterceptor> rebuilt = new ArrayList<>(existing.size() + 1);
                    rebuilt.add(inner);
                    rebuilt.addAll(existing);
                    mybatisPlusInterceptor.setInterceptors(rebuilt);
                }
                return bean;
            }
        };
    }

    /**
     * 运行时开关存储：默认有 Redis 用 Redis，否则退化为本地静态开关
     */
    @Bean
    @ConditionalOnMissingBean(DataTranslationSwitchStore.class)
    public DataTranslationSwitchStore dataTranslationSwitchStore(HmongDbI18nProperties properties,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider)
    {
        String storeType = properties.getStore() == null ? "auto" : properties.getStore().trim().toLowerCase();
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        return switch (storeType)
        {
            case "static" -> new PropertyDataTranslationSwitchStore(properties.isStaticSwitchEnabled());
            case "redis" -> new RedisDataTranslationSwitchStore(requireRedis(redisTemplate), properties.getSwitchKey());
            default -> redisTemplate != null
                    ? new RedisDataTranslationSwitchStore(redisTemplate, properties.getSwitchKey())
                    : new PropertyDataTranslationSwitchStore(properties.isStaticSwitchEnabled());
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public DataTranslationSwitchService dataTranslationSwitchService(DataTranslationSwitchStore store)
    {
        return new DefaultDataTranslationSwitchService(store);
    }

    /**
     * Web 环境：注册请求快照拦截器，并提供默认语言解析器
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer")
    @ConditionalOnProperty(prefix = "hmong.db-i18n", name = "web-enabled", havingValue = "true", matchIfMissing = true)
    static class WebSupportConfiguration
    {
        @Bean
        @ConditionalOnMissingBean
        public DataTranslationRequestInterceptor dataTranslationRequestInterceptor(
                DataTranslationSwitchService switchService)
        {
            return new DataTranslationRequestInterceptor(switchService);
        }

        /**
         * 使用方未配置 LocaleResolver 时，提供 zh-CN 默认、支持 lang/Accept-Language 的实现
         */
        @Bean
        @ConditionalOnMissingBean(LocaleResolver.class)
        public LocaleResolver localeResolver()
        {
            return new AcceptLanguageLocaleResolver();
        }

        @Bean
        public WebMvcConfigurer dataTranslationWebMvcConfigurer(
                DataTranslationRequestInterceptor requestInterceptor)
        {
            return new WebMvcConfigurer()
            {
                @Override
                public void addInterceptors(InterceptorRegistry registry)
                {
                    registry.addInterceptor(requestInterceptor).addPathPatterns("/**");
                }
            };
        }
    }

    /**
     * 译文维护：源码层用 Spring JDBC 访问翻译表，避免依赖 Mapper 扫描
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(NamedParameterJdbcTemplate.class)
    @ConditionalOnSingleCandidate(DataSource.class)
    @ConditionalOnBean(TransactionTemplate.class)
    @ConditionalOnProperty(prefix = "hmong.db-i18n", name = "maintenance-enabled", havingValue = "true",
            matchIfMissing = true)
    static class MaintenanceConfiguration
    {
        @Bean
        @ConditionalOnMissingBean
        public DataTranslationRepository dataTranslationRepository(DataSource dataSource)
        {
            return new DataTranslationRepository(new NamedParameterJdbcTemplate(dataSource));
        }

        @Bean
        @ConditionalOnMissingBean
        public DataTranslationOperatorProvider dataTranslationOperatorProvider()
        {
            return DataTranslationOperatorProvider.system();
        }

        @Bean
        @ConditionalOnMissingBean
        public DataTranslationMaintenanceService dataTranslationMaintenanceService(
                DataTranslationRepository repository, TransactionTemplate transactionTemplate,
                ObjectProvider<DataTranslationSourceProvider> sourceProviders,
                DataTranslationOperatorProvider operatorProvider)
        {
            return new DefaultDataTranslationMaintenanceService(repository, transactionTemplate, sourceProviders,
                    operatorProvider);
        }
    }

    /**
     * 启动后校验并打印实际插件链顺序，避免仅依赖 @Order 的假设失效
     */
    @Bean
    @ConditionalOnClass(ApplicationRunner.class)
    @ConditionalOnProperty(prefix = "hmong.db-i18n", name = "verify-plugin-chain", havingValue = "true",
            matchIfMissing = true)
    public ApplicationRunner dataTranslationPluginChainVerifier(ObjectProvider<List<SqlSessionFactory>> factories)
    {
        return args -> {
            List<SqlSessionFactory> factoryList = factories.getIfAvailable();
            if (factoryList == null || factoryList.isEmpty())
            {
                return;
            }
            for (SqlSessionFactory factory : factoryList)
            {
                List<org.apache.ibatis.plugin.Interceptor> interceptors =
                        new ArrayList<>(factory.getConfiguration().getInterceptors());
                int mybatisPlusIndex = indexOf(interceptors, MybatisPlusInterceptor.class);
                int pageHelperIndex = indexOfByName(interceptors, "com.github.pagehelper.PageInterceptor");
                if (countByName(interceptors, "com.github.pagehelper.PageInterceptor") > 1)
                {
                    log.error(Messages.PLUGIN_CHAIN_DUPLICATE, interceptorNames(interceptors));
                }
                if (mybatisPlusIndex >= 0 && pageHelperIndex >= 0 && mybatisPlusIndex < pageHelperIndex)
                {
                    log.error(Messages.PLUGIN_CHAIN_BAD, interceptorNames(interceptors));
                }
                else
                {
                    log.info(Messages.PLUGIN_CHAIN_OK, interceptorNames(interceptors));
                }
            }
        };
    }

    private static StringRedisTemplate requireRedis(StringRedisTemplate redisTemplate)
    {
        if (redisTemplate == null)
        {
            throw new IllegalStateException("hmong.db-i18n.store=redis 需要容器中存在 StringRedisTemplate");
        }
        return redisTemplate;
    }

    private static int indexOf(List<org.apache.ibatis.plugin.Interceptor> interceptors, Class<?> type)
    {
        for (int i = 0; i < interceptors.size(); i++)
        {
            if (type.isInstance(interceptors.get(i)))
            {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfByName(List<org.apache.ibatis.plugin.Interceptor> interceptors, String className)
    {
        for (int i = 0; i < interceptors.size(); i++)
        {
            if (matches(interceptors.get(i), className))
            {
                return i;
            }
        }
        return -1;
    }

    private static int countByName(List<org.apache.ibatis.plugin.Interceptor> interceptors, String className)
    {
        int count = 0;
        for (org.apache.ibatis.plugin.Interceptor interceptor : interceptors)
        {
            if (matches(interceptor, className))
            {
                count++;
            }
        }
        return count;
    }

    private static boolean matches(org.apache.ibatis.plugin.Interceptor interceptor, String className)
    {
        Class<?> type = interceptor.getClass();
        while (type != null)
        {
            if (className.equals(type.getName()))
            {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static String interceptorNames(List<org.apache.ibatis.plugin.Interceptor> interceptors)
    {
        return interceptors.stream().map(interceptor -> interceptor.getClass().getSimpleName())
                .reduce((a, b) -> a + " -> " + b).orElse("");
    }
}
