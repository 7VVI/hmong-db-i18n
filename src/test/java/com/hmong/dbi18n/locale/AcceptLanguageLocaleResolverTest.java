package com.hmong.dbi18n.locale;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 无状态语言解析器测试
 * <p>
 * 覆盖语言来源优先级：lang 参数 &gt; Accept-Language 请求头 &gt; 系统默认语言，
 * 并验证解析过程不读写 session、返回值恒非空。
 *
 * @author yanx
 */
public class AcceptLanguageLocaleResolverTest {

    private final AcceptLanguageLocaleResolver resolver = new AcceptLanguageLocaleResolver();

    /**
     * Accept-Language 请求头生效（复用容器解析出的语言）
     */
    @Test
    public void testResolveLocale_AcceptLanguageHeader() {
        MockHttpServletRequest request = buildRequest(null, "de", Locale.GERMAN);
        assertEquals(Locale.GERMAN, resolver.resolveLocale(request));
    }

    /**
     * 未携带 Accept-Language 请求头时回落到系统默认语言
     * <p>
     * 注意：{@code MockHttpServletRequest} 设置候选语言时会同步写入 Accept-Language 头，
     * 因此这里用不带任何语言信息的请求模拟「客户端未发送 Accept-Language」的场景。
     */
    @Test
    public void testResolveLocale_NoHeaderFallsBackToDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(AcceptLanguageLocaleResolver.DEFAULT_LOCALE, resolver.resolveLocale(request));
    }

    /**
     * Accept-Language 请求头为空白值时同样回落到系统默认语言，不取容器默认语言
     */
    @Test
    public void testResolveLocale_BlankHeaderFallsBackToDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "");
        assertEquals(AcceptLanguageLocaleResolver.DEFAULT_LOCALE, resolver.resolveLocale(request));
    }

    /**
     * 请求头为通配值 {@code *} （客户端不限语言）时保留系统默认语言，不取容器默认语言
     * <p>
     * 此处不设置候选语言：一旦误读容器解析结果，{@code MockHttpServletRequest.getLocale()}
     * 会抛 NoSuchElementException，用例失败即可发现。
     */
    @Test
    public void testResolveLocale_WildcardHeaderFallsBackToDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "*");
        assertEquals(AcceptLanguageLocaleResolver.DEFAULT_LOCALE, resolver.resolveLocale(request));
    }

    /**
     * lang 参数优先级高于 Accept-Language 请求头
     */
    @Test
    public void testResolveLocale_LangParamWinsHeader() {
        MockHttpServletRequest request = buildRequest("en", "de", Locale.GERMAN);
        assertEquals(Locale.ENGLISH, resolver.resolveLocale(request));
    }

    /**
     * lang 参数兼容 zh_CN（下划线）与 zh-CN（BCP47 连字符）两种写法
     */
    @Test
    public void testResolveLocale_LangParamBothSeparators() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(buildRequest("zh_CN", null)));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(buildRequest("zh-CN", null)));
    }

    /**
     * lang 参数为非法语言标签时不抛异常，交由下一级语言来源处理
     */
    @Test
    public void testResolveLocale_InvalidLangParamFallsBackToHeader() {
        MockHttpServletRequest request = buildRequest("not a locale", "fr", Locale.FRANCE);
        assertEquals(Locale.FRANCE, resolver.resolveLocale(request));
    }

    /**
     * 无地区的中文标签归一到简体语言包，避免回落到基础包 messages.properties
     */
    @Test
    public void testResolveLocale_ChineseTagsNormalizedToSimplified() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(buildRequest("zh", null)));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(buildRequest("zh-Hans", null)));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(buildRequest("zh-Hant", null)));
        assertEquals(Locale.SIMPLIFIED_CHINESE,
                resolver.resolveLocale(buildRequest(null, "zh-Hans", Locale.forLanguageTag("zh-Hans"))));
    }

    /**
     * 已显式带地区的中文标签不归一，保留后续新增繁体语言包的接入口
     */
    @Test
    public void testResolveLocale_RegionalChineseTagKept() {
        assertEquals(Locale.TAIWAN, resolver.resolveLocale(buildRequest("zh-TW", null)));
    }

    /**
     * 未预置语言包的语言原样返回，由 MessageSource 回落到基础包，且返回值恒非空
     */
    @Test
    public void testResolveLocale_UnsupportedLocaleNeverNull() {
        MockHttpServletRequest request = buildRequest(null, "pt-BR", new Locale("pt", "BR"));
        assertNotNull(resolver.resolveLocale(request));
        assertEquals(new Locale("pt", "BR"), resolver.resolveLocale(request));
    }

    /**
     * 无状态实现：setLocale 不抛异常，也不会为请求创建 session
     */
    @Test
    public void testSetLocaleDoesNotCreateSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        resolver.setLocale(request, null, Locale.ENGLISH);
        assertNull(request.getSession(false));
        assertEquals(AcceptLanguageLocaleResolver.DEFAULT_LOCALE, resolver.resolveLocale(request));
    }

    /**
     * 构造带 lang 参数与 Accept-Language 请求头的模拟请求
     * <p>
     * {@code MockHttpServletRequest} 会在设置候选语言时用其 language-tag 回写 Accept-Language 头，
     * 因此 {@code header} 需与 {@code locales} 保持一致，以模拟真实容器对请求头的解析结果。
     *
     * @param lang   lang 参数值，可为 null
     * @param header Accept-Language 请求头值，可为 null
     * @param locales 容器解析出的候选语言，可为空
     * @return 模拟请求
     */
    private MockHttpServletRequest buildRequest(String lang, String header, Locale... locales) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (lang != null) {
            request.setParameter(AcceptLanguageLocaleResolver.LOCALE_PARAM_NAME, lang);
        }
        if (header != null) {
            request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, header);
            if (locales != null) {
                for (Locale locale : locales) {
                    request.addPreferredLocale(locale);
                }
            }
        }
        return request;
    }
}
