package com.hmong.dbi18n.store;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 开关存储
 * <p>
 * 使用 {@link StringRedisTemplate} 读写固定键（保存 {@code true}/{@code false} 字符串），
 * 不依赖使用方 RedisTemplate 的序列化方式；同时兼容被写成 JSON 字符串（带引号）
 * 或布尔字面量的值。
 *
 * @author yanx
 */
public class RedisDataTranslationSwitchStore implements DataTranslationSwitchStore
{
    private final StringRedisTemplate redisTemplate;

    private final String switchKey;

    public RedisDataTranslationSwitchStore(StringRedisTemplate redisTemplate, String switchKey)
    {
        this.redisTemplate = redisTemplate;
        this.switchKey = switchKey;
    }

    @Override
    public Boolean read()
    {
        String raw = redisTemplate.opsForValue().get(switchKey);
        if (raw == null)
        {
            return null;
        }
        String text = raw.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\""))
        {
            text = text.substring(1, text.length() - 1).trim();
        }
        if ("true".equalsIgnoreCase(text))
        {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text))
        {
            return Boolean.FALSE;
        }
        throw new IllegalStateException("取消预期的数据翻译开关值：" + raw);
    }

    @Override
    public void write(boolean enabled)
    {
        redisTemplate.opsForValue().set(switchKey, enabled ? "true" : "false");
    }
}
