package com.hmong.dbi18n.storage.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页结果
 *
 * @param <T> 行类型
 * @author yanx
 */
public class PageResult<T> implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 当前页数据 */
    private List<T> rows;

    public PageResult()
    {
    }

    public PageResult(long total, List<T> rows)
    {
        this.total = total;
        this.rows = rows;
    }

    public static <T> PageResult<T> of(long total, List<T> rows)
    {
        return new PageResult<>(total, rows);
    }

    public long getTotal()
    {
        return total;
    }

    public void setTotal(long total)
    {
        this.total = total;
    }

    public List<T> getRows()
    {
        return rows;
    }

    public void setRows(List<T> rows)
    {
        this.rows = rows;
    }
}
