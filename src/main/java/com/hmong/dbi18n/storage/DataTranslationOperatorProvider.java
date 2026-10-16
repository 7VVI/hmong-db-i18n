package com.hmong.dbi18n.storage;

/**
 * 译文维护的操作人来源
 * <p>
 * 默认返回 0（系统），使用方可提供一个 Bean 从当前登录上下文取用户 ID；
 * 译文表审计字段不接受客户端传入。
 *
 * @author yanx
 */
@FunctionalInterface
public interface DataTranslationOperatorProvider
{
    /**
     * 当前操作人 ID
     *
     * @return 用户 ID；无登录上下文返回 0
     */
    Long currentUserId();

    /**
     * 默认实现：返回 0
     *
     * @return 0
     */
    static DataTranslationOperatorProvider system()
    {
        return () -> 0L;
    }
}
