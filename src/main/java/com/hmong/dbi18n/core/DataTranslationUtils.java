package com.hmong.dbi18n.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

/**
 * 数据字段国际化的纯内存工具：语言规范化与原文摘要
 * <p>
 * 摘要与数据库 {@code SHA2(CONVERT(column USING utf8mb4), 256)} 保持一致：按原始字符串的
 * UTF-8 字节计算，不 trim、不做 Unicode 正规化，输出 64 位小写十六进制。
 *
 * @author yanx
 */
public final class DataTranslationUtils
{
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private DataTranslationUtils()
    {
    }

    /**
     * 计算原文摘要
     *
     * @param text 原文，可为 null
     * @return 64 位小写十六进制摘要；text 为 null 时返回 null（无原文不建立译文）
     */
    public static String sha256Hex(String text)
    {
        if (text == null)
        {
            return null;
        }
        try
        {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            char[] chars = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++)
            {
                int value = bytes[i] & 0xFF;
                chars[i * 2] = HEX_CHARS[value >>> 4];
                chars[i * 2 + 1] = HEX_CHARS[value & 0x0F];
            }
            return new String(chars);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 规范化语言标签，用于查询与写入翻译表
     *
     * @param locale 语言，可为 null
     * @return BCP 47 语言码；locale 为空时返回 null
     */
    public static String languageTag(Locale locale)
    {
        return locale == null ? null : locale.stripExtensions().toLanguageTag();
    }

    /**
     * 生成译文语言候选
     * <p>
     * 请求语言等于业务源语言时返回空候选（直接使用原文）；否则精确语言优先、基础语言兜底，
     * 两者相同时不重复。
     *
     * @param locale       本次请求语言
     * @param sourceLocale 业务源语言，如 zh-CN
     * @return 语言候选
     */
    public static DataTranslationLocale resolveLocale(Locale locale, String sourceLocale)
    {
        String exact = languageTag(locale);
        if (exact == null)
        {
            return new DataTranslationLocale(null, null);
        }
        if (sourceLocale != null && sourceLocale.equalsIgnoreCase(exact))
        {
            return new DataTranslationLocale(null, null);
        }
        String language = locale.getLanguage();
        if (language == null || language.isEmpty())
        {
            return new DataTranslationLocale(exact, null);
        }
        String baseTag = new Locale(language).toLanguageTag();
        if (exact.equalsIgnoreCase(baseTag))
        {
            return new DataTranslationLocale(exact, null);
        }
        return new DataTranslationLocale(exact, baseTag);
    }

    /**
     * 规范化业务主键字符串，作为翻译表 biz_id
     * <p>
     * 拒绝空白键值以规避 MySQL 尾空格比较语义造成的碰撞；不做大小写转换，
     * 由各业务适配器明确自己的主键规范。
     *
     * @param bizId 原始业务主键
     * @return 规范化主键；非法时返回 null
     */
    public static String normalizeBizId(String bizId)
    {
        if (bizId == null)
        {
            return null;
        }
        String trimmed = bizId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 生成翻译表主键（32 位无横线 UUID）
     *
     * @return 主键字符串
     */
    public static String newTranslationId()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
