package com.hmong.dbi18n.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import com.hmong.dbi18n.core.DataTranslationField;
import com.hmong.dbi18n.core.DataTranslationRuleRegistry;
import com.hmong.dbi18n.core.DataTranslationRule;

/**
 * 数据字段国际化 SQL 改写引擎测试
 * <p>
 * 覆盖：投影与关联改写、参数顺序重建、关键词扩展、源语言跳过、非登记表跳过、
 * 以及无法安全处理时的明确报错。
 *
 * @author yanx
 */
public class DataTranslationSqlRewriterTest
{
    private static final DataTranslationRuleRegistry REGISTRY = new DataTranslationRuleRegistry(List.of(
            new DataTranslationRule("spline_dict", "app_spline", "spline_id", "zh-CN",
                    List.of(new DataTranslationField("name", "name", 100))),
            new DataTranslationRule("spline_record", "app_spline_record", "id", "zh-CN",
                    List.of(new DataTranslationField("remark", "remark", 500)))));

    private static final String LIST_SQL = "SELECT r.id AS splineId, r.spline_id AS splineModelId, "
            + "s4.name AS splineModelName, s5.name AS bracketModelName, r.remark AS remark, "
            + "r.create_time AS createTime FROM app_spline_record r "
            + "JOIN app_spline s4 ON r.spline_id = s4.spline_id AND s4.del_flag = '0' "
            + "JOIN app_spline s5 ON r.bracket_id = s5.spline_id AND s5.del_flag = '0' "
            + "WHERE r.type_id = ? AND r.brand_id = ? AND r.model_id = ? ORDER BY r.create_time DESC";

    private static final String DROPDOWN_SQL = "SELECT d.spline_id AS id, d.name AS name FROM app_spline d "
            + "WHERE d.del_flag = '0' AND d.level = ? AND d.name LIKE CONCAT('%', ?, '%') ORDER BY d.spline_id";

    private final Configuration configuration = new Configuration();

    private final DataTranslationSqlRewriter rewriter = new DataTranslationSqlRewriter();

    @Test
    public void testRewriteListProjectionsAndParameterOrder()
    {
        List<ParameterMapping> mappings = List.of(mapping("typeId", Long.class), mapping("brandId", Long.class),
                mapping("modelId", Long.class));

        DataTranslationSqlRewriter.RewriteResult result = rewriter.rewrite(new DataTranslationSqlRewriter.RewriteRequest(
                LIST_SQL, mappings, new LinkedHashMap<>(), Map.of("typeId", 1L), configuration, REGISTRY, "", "",
                Locale.forLanguageTag("en-US")));

        assertNotNull(result);
        String sql = normalize(result.getSql());
        assertEquals(6, countOccurrences(sql, "sys_data_translation"));
        assertEquals("coalesce(i18n_e0.translated_text, i18n_b1.translated_text, s4.name) as splinemodelname", findProjection(sql, "splinemodelname"));
        assertEquals("coalesce(i18n_e2.translated_text, i18n_b3.translated_text, s5.name) as bracketmodelname", findProjection(sql, "bracketmodelname"));
        assertEquals("coalesce(i18n_e4.translated_text, i18n_b5.translated_text, r.remark) as remark", findProjection(sql, "remark"));

        // 新关联参数在前，原三参数保持原有映射对象、顺序不变
        List<ParameterMapping> resultMappings = result.getParameterMappings();
        assertEquals(21, resultMappings.size());
        assertSame(mappings.get(0), resultMappings.get(18));
        assertSame(mappings.get(1), resultMappings.get(19));
        assertSame(mappings.get(2), resultMappings.get(20));

        List<Object> values = new ArrayList<>(result.getAdditionalValues().values());
        assertEquals(List.of("spline_dict", "name", "en-US", "spline_dict", "name", "en",
                "spline_dict", "name", "en-US", "spline_dict", "name", "en",
                "spline_record", "remark", "en-US", "spline_record", "remark", "en"), values);
    }

    @Test
    public void testRewriteKeywordPredicate()
    {
        List<ParameterMapping> mappings = List.of(mapping("level", Integer.class), mapping("keyword", String.class));
        Map<String, Object> parameterObject = Map.of("level", 1, "keyword", "拖拉机");

        DataTranslationSqlRewriter.RewriteResult result = rewriter.rewrite(new DataTranslationSqlRewriter.RewriteRequest(
                DROPDOWN_SQL, mappings, new LinkedHashMap<>(), parameterObject, configuration, REGISTRY, "d.name",
                "keyword", Locale.forLanguageTag("en")));

        assertNotNull(result);
        String sql = normalize(result.getSql());
        assertEquals(1, countOccurrences(sql, "sys_data_translation"));
        assertEquals(true, sql.contains("or coalesce(i18n_e0.translated_text, d.name) like concat('%', ?, '%')"));
        assertEquals("coalesce(i18n_e0.translated_text, d.name) as name", findProjection(sql, "name"));

        List<ParameterMapping> resultMappings = result.getParameterMappings();
        // 关联3个 + 原level + 原keyword + 关键词复制1个
        assertEquals(6, resultMappings.size());
        assertSame(mappings.get(0), resultMappings.get(3));
        assertSame(mappings.get(1), resultMappings.get(4));

        List<Object> values = new ArrayList<>(result.getAdditionalValues().values());
        assertEquals(List.of("spline_dict", "name", "en", "拖拉机"), values);
    }

    @Test
    public void testSourceLanguageSkipsRewrite()
    {
        assertNull(rewriteListProjections(Locale.SIMPLIFIED_CHINESE));
    }

    @Test
    public void testUnregisteredTableSkipsRewrite()
    {
        String sql = "SELECT u.user_id AS id, u.user_name AS name FROM sys_user u WHERE u.user_id = ?";
        DataTranslationSqlRewriter.RewriteResult result = rewriter.rewrite(
                new DataTranslationSqlRewriter.RewriteRequest(sql, List.of(mapping("userId", Long.class)),
                        new LinkedHashMap<>(), Map.of(), configuration, REGISTRY, "", "",
                        Locale.forLanguageTag("en-US")));
        assertNull(result);
    }

    @Test
    public void testParameterCountMismatchRejected()
    {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> rewriter.rewrite(new DataTranslationSqlRewriter.RewriteRequest(LIST_SQL,
                        List.of(mapping("typeId", Long.class)), new LinkedHashMap<>(), Map.of(), configuration,
                        REGISTRY, "", "", Locale.forLanguageTag("en-US"))));
        assertEquals(true, exception.getMessage().contains("参数映射数量"));
    }

    @Test
    public void testSelectAllColumnsRejected()
    {
        String sql = "SELECT d.* FROM app_spline d WHERE d.level = ?";
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> rewriter.rewrite(new DataTranslationSqlRewriter.RewriteRequest(sql,
                        List.of(mapping("level", Integer.class)), new LinkedHashMap<>(), Map.of(), configuration,
                        REGISTRY, "", "", Locale.forLanguageTag("en-US"))));
        assertEquals(true, exception.getMessage().contains("SELECT *"));
    }

    @Test
    public void testKeywordExpansionSkippedWhenParameterNotBound()
    {
        String sql = "SELECT d.spline_id AS id, d.name AS name FROM app_spline d WHERE d.level = ?";
        DataTranslationSqlRewriter.RewriteResult result = rewriter.rewrite(
                new DataTranslationSqlRewriter.RewriteRequest(sql, List.of(mapping("level", Integer.class)),
                        new LinkedHashMap<>(), Map.of("level", 1), configuration, REGISTRY, "d.name", "keyword",
                        Locale.forLanguageTag("en")));
        assertNotNull(result);
        String normalized = normalize(result.getSql());
        assertEquals(false, normalized.contains(" or "));
        assertEquals("coalesce(i18n_e0.translated_text, d.name) as name", findProjection(normalized, "name"));
    }

    /**
     * 归一化为小写并压平空白，便于断言（不依赖反解析器的换行与大小写）
     */
    private String normalize(String sql)
    {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private DataTranslationSqlRewriter.RewriteResult rewriteListProjections(Locale locale)
    {
        List<ParameterMapping> mappings = List.of(mapping("typeId", Long.class), mapping("brandId", Long.class),
                mapping("modelId", Long.class));
        return rewriter.rewrite(new DataTranslationSqlRewriter.RewriteRequest(LIST_SQL, mappings,
                new LinkedHashMap<>(), Map.of(), configuration, REGISTRY, "", "", locale));
    }

    private ParameterMapping mapping(String property, Class<?> javaType)
    {
        return new ParameterMapping.Builder(configuration, property, javaType).build();
    }

    /**
     * 取指定别名的选择列文本（按顶层逗号切分，避免被 COALESCE 内层逗号截断）
     */
    private String findProjection(String sql, String alias)
    {
        int selectIndex = sql.indexOf("select ");
        int fromIndex = sql.indexOf(" from ");
        assertEquals(true, selectIndex >= 0 && fromIndex > selectIndex, "SQL 结构不符合预期");
        String projectionPart = sql.substring(selectIndex + "select ".length(), fromIndex);
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < projectionPart.length(); i++)
        {
            char c = projectionPart.charAt(i);
            if (c == '(')
            {
                depth++;
            }
            else if (c == ')')
            {
                depth--;
            }
            if (c == ',' && depth == 0)
            {
                items.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        items.add(current.toString().trim());
        for (String item : items)
        {
            if (item.endsWith(" as " + alias))
            {
                return item;
            }
        }
        throw new AssertionError("未找到投影：" + alias + "，实际：" + items);
    }

    private int countOccurrences(String text, String token)
    {
        int count = 0;
        int index = text.indexOf(token);
        while (index >= 0)
        {
            count++;
            index = text.indexOf(token, index + token.length());
        }
        return count;
    }
}
