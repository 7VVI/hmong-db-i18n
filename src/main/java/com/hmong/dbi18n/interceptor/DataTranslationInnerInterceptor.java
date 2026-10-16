package com.hmong.dbi18n.interceptor;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.hmong.dbi18n.annotation.DataI18n;
import com.hmong.dbi18n.core.DataTranslationContext;
import com.hmong.dbi18n.core.Messages;
import com.hmong.dbi18n.core.DataTranslationContextHolder;
import com.hmong.dbi18n.core.DataTranslationRuleRegistry;
import com.hmong.dbi18n.sql.DataTranslationSqlRewriter;

/**
 * 数据字段国际化 SQL 改写插件
 * <p>
 * 只有同时满足“请求快照启用 + 存在请求上下文 + Mapper 方法带 {@link DataI18n} + 是 SELECT”
 * 才进入改写；其余查询保持原业务 SQL 与绑定不变。关闭开关时直接跳过解析，
 * 不访问 Redis、数据库或任何 Mapper。
 * <p>
 * 必须排在 MP 分页插件之前，保证翻译发生在分页 count 与数据 SQL 生成之前。
 *
 * @author yanx
 */
public class DataTranslationInnerInterceptor implements InnerInterceptor
{
    private static final Logger log = LoggerFactory.getLogger(DataTranslationInnerInterceptor.class);

    private final DataTranslationRuleRegistry registry;

    private final DataTranslationSqlRewriter rewriter = new DataTranslationSqlRewriter();

    private final ConcurrentHashMap<String, Optional<DataI18n>> markerCache = new ConcurrentHashMap<>();

    public DataTranslationInnerInterceptor(DataTranslationRuleRegistry registry)
    {
        this.registry = registry;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
            ResultHandler resultHandler, BoundSql boundSql) throws SQLException
    {
        DataTranslationContext context = DataTranslationContextHolder.get();
        if (context == null || !context.isEnabled() || ms.getSqlCommandType() != SqlCommandType.SELECT)
        {
            return;
        }
        DataI18n marker = resolveMarker(ms);
        if (marker == null)
        {
            return;
        }
        try
        {
            rewrite(ms, parameter, boundSql, marker, context);
        }
        catch (IllegalStateException e)
        {
            log.error(Messages.REWRITE_REJECTED_LOG, ms.getId(), e.getMessage());
            throw e;
        }
    }

    private void rewrite(MappedStatement ms, Object parameter, BoundSql boundSql, DataI18n marker,
            DataTranslationContext context)
    {
        DataTranslationSqlRewriter.RewriteRequest request = new DataTranslationSqlRewriter.RewriteRequest(
                boundSql.getSql(), boundSql.getParameterMappings(), boundSql.getAdditionalParameters(), parameter,
                ms.getConfiguration(), registry, marker.keywordColumn(), marker.keywordParameter(),
                context.getLocale() == null ? Locale.SIMPLIFIED_CHINESE : context.getLocale());
        DataTranslationSqlRewriter.RewriteResult result = rewriter.rewrite(request);
        if (result == null)
        {
            return;
        }
        PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
        mpBoundSql.sql(result.getSql());
        mpBoundSql.parameterMappings(result.getParameterMappings());
        mpBoundSql.additionalParameters().putAll(result.getAdditionalValues());
    }

    /**
     * 解析 Mapper 方法上的标记；未标记、重载歧义或非 Mapper 方法都返回 null
     */
    private DataI18n resolveMarker(MappedStatement ms)
    {
        return markerCache.computeIfAbsent(ms.getId(), id -> Optional.ofNullable(loadMarker(id))).orElse(null);
    }

    private DataI18n loadMarker(String statementId)
    {
        int lastDot = statementId.lastIndexOf('.');
        if (lastDot <= 0)
        {
            return null;
        }
        String className = statementId.substring(0, lastDot);
        String methodName = statementId.substring(lastDot + 1);
        Class<?> mapperClass = loadClass(className);
        if (mapperClass == null || !mapperClass.isInterface())
        {
            return null;
        }
        DataI18n found = null;
        for (Method method : mapperClass.getMethods())
        {
            if (!method.getName().equals(methodName))
            {
                continue;
            }
            DataI18n annotation = method.getAnnotation(DataI18n.class);
            if (annotation == null)
            {
                continue;
            }
            if (found != null)
            {
                log.warn("Mapper 方法存在重载且多个重载带 @DataI18n，已忽略标记：{}", statementId);
                return null;
            }
            found = annotation;
        }
        return found;
    }

    private Class<?> loadClass(String className)
    {
        try
        {
            return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        }
        catch (ClassNotFoundException e)
        {
            try
            {
                return Class.forName(className);
            }
            catch (ClassNotFoundException ignored)
            {
                return null;
            }
        }
    }
}
