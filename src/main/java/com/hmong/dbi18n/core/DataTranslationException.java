package com.hmong.dbi18n.core;

/**
 * 数据字段国际化的异常
 * <p>
 * 改写失败、维护校验不通过等场景统一抛出本异常；消息为中文原文，业务系统可按需转换。
 *
 * @author yanx
 */
public class DataTranslationException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public DataTranslationException(String message)
    {
        super(message);
    }

    public DataTranslationException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * 拼装带参数的消息
     *
     * @param template 形如 {@code 第{0}行译文为空} 的模板
     * @param args     参数
     * @return 消息文本
     */
    public static DataTranslationException of(String template, Object... args)
    {
        return new DataTranslationException(Messages.format(template, args));
    }
}
