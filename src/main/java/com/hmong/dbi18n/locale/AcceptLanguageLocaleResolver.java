package com.hmong.dbi18n.locale;

import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 无状态的国际化语言解析器（可选默认实现）
 * <p>
 * 语言来源优先级：
 * <ol>
 *     <li>请求参数 {@code lang}（如 {@code ?lang=en}、{@code ?lang=zh_CN}）；</li>
 *     <li>请求头 {@code Accept-Language}（复用容器解析结果，不按 q 值加权）；</li>
 *     <li>默认语言 {@link #DEFAULT_LOCALE}（简体中文）。</li>
 * </ol>
 * 中文无地区标签（{@code zh}、{@code zh-Hans}、{@code zh-Hant}）统一归一到简体，
 * 便于命中同一套数据译文；返回值恒非空，避免回落到容器默认语言。
 * <p>
 * 使用方已有自己的 LocaleResolver 时，本类不会被自动注册（{@code @ConditionalOnMissingBean}）。
 *
 * @author yanx
 */
public class AcceptLanguageLocaleResolver implements LocaleResolver
{
    /** 显式指定语言的请求参数名，优先级高于 Accept-Language */
    public static final String LOCALE_PARAM_NAME = "lang";

    /** 系统默认语言 */
    public static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    private static final char LOCALE_TAG_SEPARATOR = '-';

    private static final String WILDCARD_LANGUAGE = "*";

    private static final String LANGUAGE_CHINESE = "zh";

    @Override
    public Locale resolveLocale(HttpServletRequest request)
    {
        Locale locale = parseLocale(request.getParameter(LOCALE_PARAM_NAME));
        if (locale == null && hasConcreteLanguage(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)))
        {
            locale = request.getLocale();
        }
        if (locale == null)
        {
            locale = DEFAULT_LOCALE;
        }
        return normalizeLocale(locale);
    }

    /**
     * 无状态实现：不做任何持久化，语言只由当次请求的参数/请求头决定
     */
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale)
    {
        // 无状态设计，忽略写入
    }

    private Locale parseLocale(String languageTag)
    {
        if (languageTag == null || languageTag.isBlank())
        {
            return null;
        }
        Locale locale = Locale.forLanguageTag(languageTag.trim().replace('_', LOCALE_TAG_SEPARATOR));
        return locale.getLanguage().isEmpty() ? null : locale;
    }

    private Locale normalizeLocale(Locale locale)
    {
        boolean noCountry = locale.getCountry() == null || locale.getCountry().isBlank();
        return noCountry && LANGUAGE_CHINESE.equals(locale.getLanguage()) ? DEFAULT_LOCALE : locale;
    }

    private boolean hasConcreteLanguage(String header)
    {
        if (header == null || header.isBlank())
        {
            return false;
        }
        for (String candidate : header.split(","))
        {
            String tag = candidate.split(";")[0].trim();
            if (!tag.isEmpty() && !WILDCARD_LANGUAGE.equals(tag))
            {
                return true;
            }
        }
        return false;
    }
}
