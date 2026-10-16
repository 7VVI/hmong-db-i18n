package com.hmong.dbi18n.core;

import java.io.Serializable;

/**
 * 单个可翻译字段的登记信息
 *
 * @param fieldCode  字段标识，翻译表 field_code
 * @param columnName 源表物理列名
 * @param maxLength  译文允许的最大长度，维护时按此校验
 * @author yanx
 */
public record DataTranslationField(String fieldCode, String columnName, int maxLength) implements Serializable
{
    public DataTranslationField
    {
        if (fieldCode == null || fieldCode.isBlank())
        {
            throw new IllegalArgumentException("字段标识不能为空");
        }
        if (columnName == null || columnName.isBlank())
        {
            throw new IllegalArgumentException("源列名不能为空");
        }
        if (maxLength <= 0)
        {
            throw new IllegalArgumentException("最大长度必须大于 0");
        }
    }

    /**
     * JavaBean 形式的字段标识
     *
     * @return 字段标识
     */
    public String getFieldCode()
    {
        return fieldCode;
    }

    /**
     * JavaBean 形式的源列名
     *
     * @return 源表物理列名
     */
    public String getColumnName()
    {
        return columnName;
    }

    /**
     * JavaBean 形式的最大长度
     *
     * @return 译文最大长度
     */
    public int getMaxLength()
    {
        return maxLength;
    }
}
