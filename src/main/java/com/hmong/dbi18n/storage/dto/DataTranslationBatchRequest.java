package com.hmong.dbi18n.storage.dto;

import java.io.Serializable;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 译文批量保存请求（整批一个事务）
 *
 * @author yanx
 */
public class DataTranslationBatchRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Valid
    @NotEmpty(message = "译文条目不能为空")
    @Size(max = 500, message = "单次批量提交不能超过500条")
    private List<DataTranslationSaveItem> items;

    public List<DataTranslationSaveItem> getItems()
    {
        return items;
    }

    public void setItems(List<DataTranslationSaveItem> items)
    {
        this.items = items;
    }
}
