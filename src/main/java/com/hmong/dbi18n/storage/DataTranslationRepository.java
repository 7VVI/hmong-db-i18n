package com.hmong.dbi18n.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 翻译表仓储（Spring JDBC 实现）
 * <p>
 * 刻意不依赖 MyBatis Mapper 扫描，普通 MyBatis、JPA 或纯 JDBC 项目都可以直接使用；
 * 表名固定为 {@code sys_data_translation}（见 db/hmong_db_i18n_schema.sql）。
 *
 * @author yanx
 */
public class DataTranslationRepository
{
    /** 翻译表名 */
    public static final String TABLE_NAME = "sys_data_translation";

    private static final String SELECT_COLUMNS =
            "id, biz_type, biz_id, field_code, locale, translated_text, source_hash, del_flag";

    private static final String SELECT_SQL =
            "select " + SELECT_COLUMNS + " from " + TABLE_NAME
                    + " where del_flag = '0' and biz_type = :bizType"
                    + " and field_code in (:fieldCodes) and locale in (:locales) and biz_id in (:bizIds)";

    private static final String UPSERT_SQL =
            "insert into " + TABLE_NAME
                    + " (id, biz_type, biz_id, field_code, locale, translated_text, source_hash,"
                    + " create_by, create_time, update_by, update_time, del_flag)"
                    + " values (:id, :bizType, :bizId, :fieldCode, :locale, :translatedText, :sourceHash,"
                    + " :createBy, now(), :updateBy, now(), '0')"
                    + " on duplicate key update translated_text = values(translated_text),"
                    + " source_hash = values(source_hash), update_by = values(update_by),"
                    + " update_time = now(), del_flag = '0'";

    private static final String LOGIC_DELETE_BY_IDS_SQL =
            "update " + TABLE_NAME + " set del_flag = '1', update_by = :updateBy, update_time = now()"
                    + " where del_flag = '0' and id in (:ids)";

    private static final String LOGIC_DELETE_BY_BIZ_SQL =
            "update " + TABLE_NAME + " set del_flag = '1', update_by = :updateBy, update_time = now()"
                    + " where del_flag = '0' and biz_type = :bizType and field_code = :fieldCode"
                    + " and biz_id in (:bizIds)";

    private static final RowMapper<TranslationRow> ROW_MAPPER = (rs, rowNum) -> {
        TranslationRow row = new TranslationRow();
        row.setId(rs.getString("id"));
        row.setBizType(rs.getString("biz_type"));
        row.setBizId(rs.getString("biz_id"));
        row.setFieldCode(rs.getString("field_code"));
        row.setLocale(rs.getString("locale"));
        row.setTranslatedText(rs.getString("translated_text"));
        row.setSourceHash(rs.getString("source_hash"));
        row.setDelFlag(rs.getString("del_flag"));
        return row;
    };

    private final NamedParameterJdbcTemplate jdbc;

    public DataTranslationRepository(NamedParameterJdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    /**
     * 按业务记录、字段与语言批量读取有效译文
     *
     * @param bizType    业务类型
     * @param bizIds     业务主键集合
     * @param fieldCodes 字段标识集合
     * @param locales    语言候选集合
     * @return 译文行
     */
    public List<TranslationRow> select(String bizType, Collection<String> bizIds, Collection<String> fieldCodes,
            Collection<String> locales)
    {
        if (bizIds == null || bizIds.isEmpty() || fieldCodes == null || fieldCodes.isEmpty()
                || locales == null || locales.isEmpty())
        {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bizType", bizType)
                .addValue("bizIds", bizIds)
                .addValue("fieldCodes", fieldCodes)
                .addValue("locales", locales);
        return jdbc.query(SELECT_SQL, params, ROW_MAPPER);
    }

    /**
     * 批量新增或更新译文，唯一键冲突时恢复为有效并保留原主键与创建审计
     *
     * @param rows 译文行
     * @return 影响行数
     */
    public int upsert(List<TranslationRow> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return 0;
        }
        List<MapSqlParameterSource> batch = new ArrayList<>(rows.size());
        for (TranslationRow row : rows)
        {
            batch.add(new MapSqlParameterSource()
                    .addValue("id", row.getId())
                    .addValue("bizType", row.getBizType())
                    .addValue("bizId", row.getBizId())
                    .addValue("fieldCode", row.getFieldCode())
                    .addValue("locale", row.getLocale())
                    .addValue("translatedText", row.getTranslatedText())
                    .addValue("sourceHash", row.getSourceHash())
                    .addValue("createBy", row.getCreateBy() == null ? 0L : row.getCreateBy())
                    .addValue("updateBy", row.getUpdateBy() == null ? 0L : row.getUpdateBy()));
        }
        int[] affected = jdbc.batchUpdate(UPSERT_SQL, batch.toArray(new MapSqlParameterSource[0]));
        int total = 0;
        for (int count : affected)
        {
            total += Math.max(count, 0);
        }
        return total;
    }

    /**
     * 按主键逻辑删除译文
     *
     * @param ids      译文主键集合
     * @param updateBy 操作人
     * @return 影响行数
     */
    public int logicDeleteByIds(Collection<String> ids, Long updateBy)
    {
        if (ids == null || ids.isEmpty())
        {
            return 0;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", ids)
                .addValue("updateBy", updateBy == null ? 0L : updateBy);
        return jdbc.update(LOGIC_DELETE_BY_IDS_SQL, params);
    }

    /**
     * 按业务记录批量逻辑删除译文（源记录删除时的联动清理）
     *
     * @param bizType   业务类型
     * @param fieldCode 字段标识
     * @param bizIds    业务主键集合
     * @param updateBy  操作人
     * @return 影响行数
     */
    public int logicDeleteByBizIds(String bizType, String fieldCode, Collection<String> bizIds, Long updateBy)
    {
        if (bizIds == null || bizIds.isEmpty())
        {
            return 0;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bizType", bizType)
                .addValue("fieldCode", fieldCode)
                .addValue("bizIds", bizIds)
                .addValue("updateBy", updateBy == null ? 0L : updateBy);
        return jdbc.update(LOGIC_DELETE_BY_BIZ_SQL, params);
    }

    /**
     * 探测翻译表是否可访问（用于开启开关前的就绪校验）
     *
     * @throws RuntimeException 表不存在或不可访问时抛出
     */
    public void assertTableAvailable()
    {
        jdbc.query("select " + SELECT_COLUMNS + " from " + TABLE_NAME + " where 1 = 0",
                Map.of(), ROW_MAPPER);
    }
}
