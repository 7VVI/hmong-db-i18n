package com.hmong.dbi18n.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.hmong.dbi18n.core.DataTranslationField;
import com.hmong.dbi18n.core.DataTranslationRule;
import com.hmong.dbi18n.core.DataTranslationRuleRegistry;
import com.hmong.dbi18n.interceptor.DataTranslationInnerInterceptor;
import com.hmong.dbi18n.store.DefaultDataTranslationSwitchService;
import com.hmong.dbi18n.store.PropertyDataTranslationSwitchStore;
import com.hmong.dbi18n.store.DataTranslationSwitchStore;

/**
 * 自动装配冒烟测试
 * <p>
 * 覆盖最容易出错的接线：翻译插件必须被插到 MybatisPlusInterceptor 内层拦截器的最前面、
 * 无 Redis 时自动退化为本地静态开关、重复登记源表时启动即失败。
 *
 * @author yanx
 */
public class HmongDbI18nAutoConfigurationTest
{
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HmongDbI18nAutoConfiguration.class));

    @Test
    public void testInnerInterceptorIsInsertedFirst()
    {
        runner.withUserConfiguration(RuleConfiguration.class, MybatisPlusConfiguration.class)
                .withPropertyValues("hmong.db-i18n.store=static")
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(DataTranslationRuleRegistry.class).size(),
                            "登记表应被创建且唯一");
                    MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
                    List<InnerInterceptor> inner = interceptor.getInterceptors();
                    assertInstanceOf(DataTranslationInnerInterceptor.class, inner.get(0),
                            "翻译插件必须排在内层拦截器最前面（早于分页）");
                    assertInstanceOf(ExistingInnerInterceptor.class, inner.get(1), "原有内层拦截器应被保留");
                    assertEquals(2, inner.size(), "原有内层拦截器应被保留");
                });
    }

    @Test
    public void testStaticSwitchStoreWhenNoRedis()
    {
        runner.withUserConfiguration(RuleConfiguration.class)
                .withPropertyValues("hmong.db-i18n.store=static", "hmong.db-i18n.static-switch-enabled=true")
                .run(context -> {
                    assertInstanceOf(PropertyDataTranslationSwitchStore.class,
                            context.getBean(DataTranslationSwitchStore.class));
                    DefaultDataTranslationSwitchService service =
                            context.getBean(DefaultDataTranslationSwitchService.class);
                    assertTrue(service.isEnabled(), "静态开关应读取到配置的初值");
                    service.setEnabled(false);
                    assertTrue(!service.isEnabled(), "静态开关应支持运行时切换");
                });
    }

    @Test
    public void testDuplicateTableRegistrationFailsFast()
    {
        runner.withUserConfiguration(RuleConfiguration.class, DuplicateRuleConfiguration.class)
                .run(context -> assertTrue(context.getStartupFailure() != null,
                        "同一张表被重复登记时应当启动失败"));
    }

    @Test
    public void testDisabledByProperty()
    {
        runner.withUserConfiguration(RuleConfiguration.class)
                .withPropertyValues("hmong.db-i18n.enabled=false")
                .run(context -> assertTrue(context.getBeansOfType(DataTranslationRuleRegistry.class).isEmpty(),
                        "关闭开关后不应注册任何 Bean"));
    }

    @Configuration(proxyBeanMethods = false)
    static class RuleConfiguration
    {
        @Bean
        DataTranslationRule demoRule()
        {
            return new DataTranslationRule("demo_biz", "demo_table", "id", "zh-CN",
                    List.of(new DataTranslationField("name", "name", 100)));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateRuleConfiguration
    {
        @Bean
        DataTranslationRule duplicateTableRule()
        {
            return new DataTranslationRule("another_biz", "demo_table", "id", "zh-CN",
                    List.of(new DataTranslationField("name", "name", 100)));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MybatisPlusConfiguration
    {
        @Bean
        ExistingInnerInterceptor existingInnerInterceptor()
        {
            return new ExistingInnerInterceptor();
        }

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor(ExistingInnerInterceptor existingInnerInterceptor)
        {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(existingInnerInterceptor);
            return interceptor;
        }
    }

    /**
     * 模拟使用方已有的内层拦截器（如分页）
     */
    static class ExistingInnerInterceptor implements InnerInterceptor
    {
    }
}
