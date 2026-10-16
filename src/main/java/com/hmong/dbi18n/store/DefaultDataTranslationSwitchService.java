package com.hmong.dbi18n.store;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 运行时开关的默认实现
 * <p>
 * 只读取存储后端，不做本地缓存、不回源数据库；读取异常时限频告警并按关闭处理。
 *
 * @author yanx
 */
public class DefaultDataTranslationSwitchService implements DataTranslationSwitchService
{
    private static final Logger log = LoggerFactory.getLogger(DefaultDataTranslationSwitchService.class);

    private static final long WARN_INTERVAL_MILLIS = 60_000L;

    private final DataTranslationSwitchStore store;

    private final AtomicLong lastWarnTime = new AtomicLong(0L);

    public DefaultDataTranslationSwitchService(DataTranslationSwitchStore store)
    {
        this.store = store;
    }

    @Override
    public boolean isEnabled()
    {
        try
        {
            Boolean value = store.read();
            return Boolean.TRUE.equals(value);
        }
        catch (Exception e)
        {
            warnRateLimited("读取数据翻译开关失败，按关闭处理：" + e.getMessage());
            return false;
        }
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        store.write(enabled);
    }

    private void warnRateLimited(String message)
    {
        long now = System.currentTimeMillis();
        long last = lastWarnTime.get();
        if (now - last > WARN_INTERVAL_MILLIS && lastWarnTime.compareAndSet(last, now))
        {
            log.warn(message);
        }
    }
}
