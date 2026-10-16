package com.hmong.dbi18n.storage.dto;

import java.io.Serializable;

import jakarta.validation.constraints.NotNull;

/**
 * 运行时开关请求
 *
 * @author yanx
 */
public class DataTranslationSwitchRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    @NotNull(message = "开关状态不能为空")
    private Boolean enabled;

    public Boolean getEnabled()
    {
        return enabled;
    }

    public void setEnabled(Boolean enabled)
    {
        this.enabled = enabled;
    }
}
