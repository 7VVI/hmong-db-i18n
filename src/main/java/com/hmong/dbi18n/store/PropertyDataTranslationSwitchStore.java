package com.hmong.dbi18n.store;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hmong.dbi18n.core.Messages;

/**
 * 本地静态开关存储（无 Redis 时使用）
 * <p>
 * 初始值来自配置 {@code hmong.db-i18n.static-switch-enabled}；运行时改动只作用于当前节点，
 * 写入时会打印告警，适合单实例或仅用于应急开关的场景。
 *
 * @author yanx
 */
public class PropertyDataTranslationSwitchStore implements DataTranslationSwitchStore
{
    private static final Logger log = LoggerFactory.getLogger(PropertyDataTranslationSwitchStore.class);

    private final AtomicBoolean enabled;

    public PropertyDataTranslationSwitchStore(boolean initialValue)
    {
        this.enabled = new AtomicBoolean(initialValue);
    }

    @Override
    public Boolean read()
    {
        return enabled.get();
    }

    @Override
    public void write(boolean value)
    {
        enabled.set(value);
        log.warn(Messages.STATIC_SWITCH_WRITE);
    }
}
