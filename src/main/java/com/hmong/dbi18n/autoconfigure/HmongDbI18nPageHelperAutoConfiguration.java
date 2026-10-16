package com.hmong.dbi18n.autoconfigure;

import java.util.Properties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;

import com.github.pagehelper.PageInterceptor;
import com.github.pagehelper.autoconfigure.PageHelperStandardProperties;

/**
 * PageHelper 顺序助手（可选）
 * <p>
 * MyBatis 的 {@code pluginAll} 逐层包装，后注册者在外层先执行。本类以 {@code @Order(100)} 注册
 * PageInterceptor，配合 MybatisPlusInterceptor 的默认顺序（最低优先级），保证“翻译先于分页生成
 * count/data”；PageHelper 自身的自动配置检测到已存在实例后不会再重复追加。
 * <p>
 * 使用方已自行管控插件顺序时，保持 {@code hmong.db-i18n.pagehelper-order=false}（默认）。
 *
 * @author yanx
 */
@AutoConfiguration
@ConditionalOnClass(PageInterceptor.class)
@ConditionalOnProperty(prefix = "hmong.db-i18n", name = "pagehelper-order", havingValue = "true")
public class HmongDbI18nPageHelperAutoConfiguration
{
    @Bean
    @Order(100)
    public PageInterceptor pageInterceptor(ObjectProvider<PageHelperStandardProperties> standardPropertiesProvider)
    {
        PageInterceptor interceptor = new PageInterceptor();
        PageHelperStandardProperties standardProperties = standardPropertiesProvider.getIfAvailable();
        interceptor.setProperties(standardProperties != null ? standardProperties.getProperties() : new Properties());
        return interceptor;
    }
}
