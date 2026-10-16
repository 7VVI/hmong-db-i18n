package com.hmong.dbi18n.core;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 一个业务类型（通常对应一张源表）的字段翻译登记
 * <p>
 * 纯数据元信息，不依赖 Mapper 或业务服务，可在 SqlSessionFactory 构建期间安全使用。
 * 一张源表只登记一条规则；同一张表出现多个别名时，插件按各自别名分别关联译文。
 *
 * @author yanx
 */
public class DataTranslationRule implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 业务类型标识，翻译表 biz_type */
    private final String bizType;

    /** 源表物理表名（不含 schema，大小写不敏感） */
    private final String tableName;

    /** 源表主键列，翻译表 biz_id 的来源 */
    private final String idColumn;

    /** 业务源语言（BCP 47，如 zh-CN）：请求该语言直接返回原文，不建译文 */
    private final String sourceLocale;

    /** 可翻译字段：列名（小写）到字段登记的映射 */
    private final Map<String, DataTranslationField> fields;

    public DataTranslationRule(String bizType, String tableName, String idColumn, String sourceLocale,
            List<DataTranslationField> fields)
    {
        if (bizType == null || bizType.isBlank())
        {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        if (fields == null || fields.isEmpty())
        {
            throw new IllegalArgumentException("可翻译字段不能为空");
        }
        this.bizType = bizType.trim();
        this.tableName = normalize(tableName);
        this.idColumn = normalize(idColumn);
        this.sourceLocale = sourceLocale == null ? null : sourceLocale.trim();
        Map<String, DataTranslationField> fieldMap = new LinkedHashMap<>();
        for (DataTranslationField field : fields)
        {
            fieldMap.put(normalize(field.columnName()), field);
        }
        this.fields = Collections.unmodifiableMap(fieldMap);
    }

    /**
     * 按列名查找字段登记
     *
     * @param columnName 源列名，大小写不敏感
     * @return 字段登记；未登记返回 null
     */
    public DataTranslationField findField(String columnName)
    {
        return fields.get(normalize(columnName));
    }

    public String getBizType()
    {
        return bizType;
    }

    public String getTableName()
    {
        return tableName;
    }

    public String getIdColumn()
    {
        return idColumn;
    }

    public String getSourceLocale()
    {
        return sourceLocale;
    }

    public List<DataTranslationField> getFields()
    {
        return List.copyOf(fields.values());
    }

    private static String normalize(String identifier)
    {
        return identifier == null ? null : identifier.trim().toLowerCase(Locale.ROOT);
    }
}
