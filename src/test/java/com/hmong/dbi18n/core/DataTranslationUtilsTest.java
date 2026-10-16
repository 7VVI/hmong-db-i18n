package com.hmong.dbi18n.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;

import org.junit.jupiter.api.Test;


/**
 * 数据字段国际化语言与摘要规则测试
 * <p>
 * 摘要需与 MySQL {@code SHA2(CONVERT(column USING utf8mb4), 256)} 完全一致，
 * 因此固定断言 UTF-8 字节的 SHA-256 值。
 *
 * @author yanx
 */
public class DataTranslationUtilsTest
{
    @Test
    public void testSha256MatchesMysqlUtf8Bytes()
    {
        assertEquals("ea0e8856cd60bc0cfed0e2535b38a071a75623dad25030edfa17a3a71bf70d5e",
                DataTranslationUtils.sha256Hex("拖拉机"));
        assertEquals("5d32cb5fe43e02313d8f682fe89007ee7316adefe46e7b0f50db2fc6044a0fa9",
                DataTranslationUtils.sha256Hex("约翰迪尔"));
        assertNull(DataTranslationUtils.sha256Hex(null));
    }

    @Test
    public void testResolveLocaleCandidates()
    {
        DataTranslationLocale regionLocale = DataTranslationUtils.resolveLocale(Locale.forLanguageTag("en-US"), "zh-CN");
        assertEquals("en-US", regionLocale.getExact());
        assertEquals("en", regionLocale.getBase());

        DataTranslationLocale languageLocale = DataTranslationUtils.resolveLocale(Locale.forLanguageTag("en"), "zh-CN");
        assertEquals("en", languageLocale.getExact());
        assertNull(languageLocale.getBase());

        DataTranslationLocale sourceLocale = DataTranslationUtils.resolveLocale(Locale.SIMPLIFIED_CHINESE, "zh-CN");
        assertEquals(true, sourceLocale.isSourceLanguage());

        DataTranslationLocale traditionalLocale = DataTranslationUtils.resolveLocale(Locale.forLanguageTag("zh-TW"),
                "zh-CN");
        assertEquals("zh-TW", traditionalLocale.getExact());
        assertEquals("zh", traditionalLocale.getBase());

        DataTranslationLocale unsupportedLocale = DataTranslationUtils.resolveLocale(Locale.forLanguageTag("pt-BR"),
                "zh-CN");
        assertEquals("pt-BR", unsupportedLocale.getExact());
        assertEquals("pt", unsupportedLocale.getBase());
    }

    @Test
    public void testLanguageTagUsesBcp47()
    {
        assertEquals("zh-CN", DataTranslationUtils.languageTag(Locale.SIMPLIFIED_CHINESE));
        assertEquals("en-US", DataTranslationUtils.languageTag(Locale.US));
        assertNull(DataTranslationUtils.languageTag(null));
    }

    @Test
    public void testNormalizeBizIdRejectsBlank()
    {
        assertEquals("12", DataTranslationUtils.normalizeBizId(" 12 "));
        assertNull(DataTranslationUtils.normalizeBizId("   "));
        assertNull(DataTranslationUtils.normalizeBizId(null));
    }
}
