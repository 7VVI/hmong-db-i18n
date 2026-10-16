package com.hmong.dbi18n.storage;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.hmong.dbi18n.storage.dto.DataTranslationQuery;
import com.hmong.dbi18n.storage.dto.DataTranslationSaveItem;
import com.hmong.dbi18n.storage.vo.DataTranslationItemVO;
import com.hmong.dbi18n.storage.vo.PageResult;

/**
 * 译文维护
 * <p>
 * 维护列表与导出以源记录为基准（包含尚无译文的字段）；写入前必须校验源记录当前原文摘要，
 * 防止把针对旧原文的译文直接盖上新的摘要。
 *
 * @author yanx
 */
public interface DataTranslationMaintenanceService
{
    /**
     * 分页查询维护视图（源记录分页，按目标语言补齐译文与状态）
     *
     * @param query 查询参数
     * @return 分页视图
     */
    PageResult<DataTranslationItemVO> selectItemPage(DataTranslationQuery query);

    /**
     * 导出翻译清单（含尚无译文的行）
     *
     * @param query   查询参数
     * @param maxRows 最大导出行数
     * @return 导出行
     */
    List<DataTranslationItemVO> selectItemsForExport(DataTranslationQuery query, int maxRows);

    /**
     * 批量保存译文（整批一个事务，任一条失败即整批回滚）
     *
     * @param items 译文条目
     * @return 保存结果摘要：success / successCount / failCount / failures / message
     */
    Map<String, Object> saveBatch(List<DataTranslationSaveItem> items);

    /**
     * 分批保存译文（Excel 导入场景），每批一个事务
     *
     * @param items     译文条目
     * @param chunkSize 每批条数
     * @return 结果摘要：success / successCount / failCount / failures（含批次范围与原因）
     */
    Map<String, Object> saveInChunks(List<DataTranslationSaveItem> items, int chunkSize);

    /**
     * 逻辑删除译文（回退原文）
     *
     * @param id 译文主键
     * @return 影响行数
     */
    int deleteById(String id);

    /**
     * 源记录删除时联动逻辑删除译文
     *
     * @param bizType   业务类型
     * @param fieldCode 字段标识
     * @param bizIds    业务主键集合
     * @param operator  操作人
     * @return 影响行数
     */
    int logicDeleteBizTranslations(String bizType, String fieldCode, Collection<String> bizIds, Long operator);

    /**
     * 业务类型是否已登记
     *
     * @param bizType 业务类型
     * @return 是否已登记
     */
    boolean isRegisteredBizType(String bizType);

    /**
     * 已登记业务类型的源语言
     *
     * @param bizType 业务类型
     * @return 源语言；未登记返回 null
     */
    String sourceLocaleOf(String bizType);

    /**
     * 开启自动翻译前的就绪校验（至少登记一个业务且翻译表可访问）
     */
    void checkReadyToEnable();
}
