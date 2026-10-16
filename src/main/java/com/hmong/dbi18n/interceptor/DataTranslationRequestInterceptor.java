package com.hmong.dbi18n.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hmong.dbi18n.core.DataTranslationContext;
import com.hmong.dbi18n.core.DataTranslationContextHolder;
import com.hmong.dbi18n.store.DataTranslationSwitchService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 数据字段国际化请求拦截器
 * <p>
 * 在进入业务代码、启动分页之前读取一次开关并冻结请求语言，形成本次请求的快照；
 * SQL 改写插件只读该快照，不再访问 Redis 或数据库。开关切换只影响之后读取快照的新请求，
 * 在途请求保持原快照，避免同一次查询口径不一致。
 *
 * @author yanx
 */
public class DataTranslationRequestInterceptor implements HandlerInterceptor
{
    private static final Logger log = LoggerFactory.getLogger(DataTranslationRequestInterceptor.class);

    private final DataTranslationSwitchService switchService;

    public DataTranslationRequestInterceptor(DataTranslationSwitchService switchService)
    {
        this.switchService = switchService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        boolean enabled = false;
        try
        {
            enabled = switchService.isEnabled();
        }
        catch (Exception e)
        {
            log.warn("读取数据翻译开关异常，本次请求按关闭处理：{}", e.getMessage());
        }
        DataTranslationContextHolder.set(new DataTranslationContext(enabled, LocaleContextHolder.getLocale()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex)
    {
        DataTranslationContextHolder.clear();
    }
}
