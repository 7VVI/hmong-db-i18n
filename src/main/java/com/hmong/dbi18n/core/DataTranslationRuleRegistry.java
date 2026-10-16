package com.hmong.dbi18n.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据字段国际化登记表
 * <p>
 * 只持有纯数据规则，不依赖 Mapper 或业务服务；一张源表只允许登记一条规则，
 * 同一张表被两套业务类型登记时启动即报错。
 *
 * @author yanx
 */
public class DataTranslationRuleRegistry
{
    private final Map<String, DataTranslationRule> rulesByTable;

    private final Map<String, DataTranslationRule> rulesByBizType;

    public DataTranslationRuleRegistry(List<DataTranslationRule> rules)
    {
        Map<String, DataTranslationRule> byTable = new LinkedHashMap<>();
        Map<String, DataTranslationRule> byBizType = new LinkedHashMap<>();
        if (rules != null)
        {
            for (DataTranslationRule rule : rules)
            {
                String tableKey = normalizeTable(rule.getTableName());
                if (byTable.put(tableKey, rule) != null)
                {
                    throw new IllegalStateException("重复登记的数据翻译源表：" + tableKey);
                }
                String bizKey = rule.getBizType().toLowerCase(Locale.ROOT);
                if (byBizType.put(bizKey, rule) != null)
                {
                    throw new IllegalStateException("重复登记的数据翻译业务类型：" + bizKey);
                }
            }
        }
        this.rulesByTable = Collections.unmodifiableMap(byTable);
        this.rulesByBizType = Collections.unmodifiableMap(byBizType);
    }

    /**
     * 按物理表名查找登记
     *
     * @param tableName 表名，允许带 schema、引号与大小写差异
     * @return 登记规则；未登记返回 null
     */
    public DataTranslationRule findByTable(String tableName)
    {
        return tableName == null ? null : rulesByTable.get(normalizeTable(tableName));
    }

    /**
     * 按业务类型查找登记
     *
     * @param bizType 业务类型
     * @return 登记规则；未登记返回 null
     */
    public DataTranslationRule findByBizType(String bizType)
    {
        return bizType == null ? null : rulesByBizType.get(bizType.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 全部登记规则
     *
     * @return 规则集合
     */
    public Collection<DataTranslationRule> all()
    {
        return rulesByTable.values();
    }

    private static String normalizeTable(String tableName)
    {
        String name = tableName == null ? "" : tableName.trim();
        int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0)
        {
            name = name.substring(lastDot + 1);
        }
        return stripDelimiters(name).toLowerCase(Locale.ROOT);
    }

    private static String stripDelimiters(String name)
    {
        String result = name;
        while (result.length() >= 2)
        {
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);
            boolean quoted = (first == '`' && last == '`') || (first == '"' && last == '"')
                    || (first == '\'' && last == '\'');
            if (!quoted)
            {
                break;
            }
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }
}
