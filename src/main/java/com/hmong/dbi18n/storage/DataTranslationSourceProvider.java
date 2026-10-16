package com.hmong.dbi18n.storage;

import java.util.Collection;
import java.util.List;

import com.hmong.dbi18n.core.DataTranslationRule;

/**
 * 源业务适配器
 * <p>
 * 由使用方为每个登记业务实现：提供源记录的分页导出、写入前的加锁读取与（可选的）权限校验。
 * 展示查询的翻译不需要本接口，它只服务于译文维护。
 *
 * @author yanx
 */
public interface DataTranslationSourceProvider
{
    /**
     * 本适配器负责的翻译登记
     *
     * @return 翻译规则
     */
    DataTranslationRule rule();

    /**
     * 统计源记录数
     *
     * @param keyword 辅助关键词，可为空
     * @return 源记录数
     */
    long countSource(String keyword);

    /**
     * 分页读取源记录（含原文）
     * <p>
     * 建议直接使用 {@code limit #{offset}, #{limit}}，不要依赖分页插件。
     *
     * @param keyword 辅助关键词，可为空
     * @param offset  起始位置
     * @param limit   最大条数
     * @return 源记录列表
     */
    List<DataTranslationSourceItem> pageSource(String keyword, int offset, int limit);

    /**
     * 按主键批量读取源记录并加行锁，用于写入前的原文校验
     * <p>
     * 必须在事务内调用；不返回不存在或已删除的记录。语句建议
     * {@code ... WHERE id IN (...) ORDER BY id FOR UPDATE}，并且不要 JOIN 其它表，
     * 避免连带锁住无关记录。
     *
     * @param bizIds 业务主键集合
     * @return 源记录列表（含原文）
     */
    List<DataTranslationSourceItem> listSourceByIdsForUpdate(Collection<String> bizIds);
}
