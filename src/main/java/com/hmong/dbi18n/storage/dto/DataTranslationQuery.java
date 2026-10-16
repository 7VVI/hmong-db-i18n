package com.hmong.dbi18n.storage.dto;

import java.io.Serializable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 译文维护列表查询参数（以源记录分页）
 *
 * @author yanx
 */
public class DataTranslationQuery implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 已登记的业务类型 */
    @NotBlank(message = "业务类型不能为空")
    @Size(max = 64, message = "业务类型长度不能超过64个字符")
    private String bizType;

    /** 目标语言（如 en、en-US） */
    @NotBlank(message = "目标语言不能为空")
    @Size(max = 35, message = "目标语言长度不能超过35个字符")
    private String locale;

    /** 源记录辅助关键词，可为空 */
    @Size(max = 100, message = "辅助关键词长度不能超过100个字符")
    private String keyword;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 500, message = "每页条数不能超过500")
    private Integer pageSize = 10;

    public String getBizType()
    {
        return bizType;
    }

    public void setBizType(String bizType)
    {
        this.bizType = bizType;
    }

    public String getLocale()
    {
        return locale;
    }

    public void setLocale(String locale)
    {
        this.locale = locale;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public Integer getPageNum()
    {
        return pageNum;
    }

    public void setPageNum(Integer pageNum)
    {
        this.pageNum = pageNum;
    }

    public Integer getPageSize()
    {
        return pageSize;
    }

    public void setPageSize(Integer pageSize)
    {
        this.pageSize = pageSize;
    }
}
