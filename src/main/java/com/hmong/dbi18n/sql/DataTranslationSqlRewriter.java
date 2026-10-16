package com.hmong.dbi18n.sql;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;

import com.hmong.dbi18n.core.DataTranslationField;
import com.hmong.dbi18n.core.DataTranslationRuleRegistry;
import com.hmong.dbi18n.core.DataTranslationLocale;
import com.hmong.dbi18n.core.DataTranslationRule;
import com.hmong.dbi18n.core.DataTranslationUtils;
import com.hmong.dbi18n.core.Messages;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;
import net.sf.jsqlparser.util.deparser.StatementDeParser;

/**
 * 数据字段国际化的 SQL 改写引擎
 * <p>
 * 仅处理带 {@code @DataI18n} 标记且确实引用了登记源表的普通 SELECT：为登记字段的投影追加
 * 精确语言与基础语言的译文关联，把显示值替换为 {@code COALESCE(精确译文, 基础译文, 原文)}；
 * 受控关键词谓词扩展为“原文或最终显示值”匹配。
 * <p>
 * 参数安全：不拼接任何外部输入，所有业务值与语言值都作为新参数绑定；新占位符按最终 SQL 的
 * 出现顺序重建 {@link ParameterMapping}，原参数复用其原有映射，foreach/bind 附加参数保留。
 * 出现无法安全处理的结构时直接抛错，绝不悄悄产生不完整查询。
 *
 * @author yanx
 */
public class DataTranslationSqlRewriter
{
    private static final String TRANSLATION_TABLE = "sys_data_translation";

    private static final String SAFE_IDENTIFIER_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile(SAFE_IDENTIFIER_PATTERN);

    private static final String ADDITIONAL_PARAM_PREFIX = "__dataI18nParam";

    private static final int EXACT_JOIN_PARAM_COUNT = 3;

    /**
     * 执行改写
     *
     * @param request 改写请求
     * @return 改写结果；无需改写时返回 null
     */
    public RewriteResult rewrite(RewriteRequest request)
    {
        Statement statement = parseStatement(request.getSql());
        if (!(statement instanceof Select))
        {
            return null;
        }
        PlainSelect plainSelect;
        try
        {
            plainSelect = ((Select) statement).getPlainSelect();
        }
        catch (RuntimeException e)
        {
            throw unsupported("仅支持普通 SELECT 的自动翻译：" + e.getMessage());
        }
        if (plainSelect == null)
        {
            return null;
        }

        RewriteSession session = new RewriteSession(request, statement, plainSelect);
        session.collectBindings();
        if (session.bindings.isEmpty())
        {
            return null;
        }
        session.buildOriginalParameterOrdinals();
        session.rewriteSelectItems();
        session.rewriteKeywordPredicate();
        if (session.targets.isEmpty())
        {
            return null;
        }
        session.appendTranslationJoins();
        return session.render();
    }

    private Statement parseStatement(String sql)
    {
        try
        {
            return CCJSqlParserUtil.parse(sql);
        }
        catch (JSQLParserException e)
        {
            throw unsupported("SQL 解析失败：" + e.getMessage());
        }
    }

    private static IllegalStateException unsupported(String message)
    {
        return new IllegalStateException(Messages.of(Messages.REWRITE_REJECTED, message));
    }

    /**
     * 改写请求
     */
    public static final class RewriteRequest
    {
        private final String sql;

        private final List<ParameterMapping> parameterMappings;

        private final Map<String, Object> additionalParameters;

        private final Object parameterObject;

        private final Configuration configuration;

        private final DataTranslationRuleRegistry registry;

        private final String keywordColumn;

        private final String keywordParameter;

        private final Locale locale;

        public RewriteRequest(String sql, List<ParameterMapping> parameterMappings,
                Map<String, Object> additionalParameters, Object parameterObject, Configuration configuration,
                DataTranslationRuleRegistry registry, String keywordColumn, String keywordParameter, Locale locale)
        {
            this.sql = sql;
            this.parameterMappings = parameterMappings;
            this.additionalParameters = additionalParameters;
            this.parameterObject = parameterObject;
            this.configuration = configuration;
            this.registry = registry;
            this.keywordColumn = keywordColumn;
            this.keywordParameter = keywordParameter;
            this.locale = locale;
        }

        public String getSql()
        {
            return sql;
        }

        public List<ParameterMapping> getParameterMappings()
        {
            return parameterMappings;
        }

        public Map<String, Object> getAdditionalParameters()
        {
            return additionalParameters;
        }

        public Object getParameterObject()
        {
            return parameterObject;
        }

        public Configuration getConfiguration()
        {
            return configuration;
        }

        public DataTranslationRuleRegistry getRegistry()
        {
            return registry;
        }

        public String getKeywordColumn()
        {
            return keywordColumn;
        }

        public String getKeywordParameter()
        {
            return keywordParameter;
        }

        public Locale getLocale()
        {
            return locale;
        }
    }

    /**
     * 改写结果
     */
    public static final class RewriteResult
    {
        private final String sql;

        private final List<ParameterMapping> parameterMappings;

        private final Map<String, Object> additionalValues;

        public RewriteResult(String sql, List<ParameterMapping> parameterMappings,
                Map<String, Object> additionalValues)
        {
            this.sql = sql;
            this.parameterMappings = parameterMappings;
            this.additionalValues = additionalValues;
        }

        public String getSql()
        {
            return sql;
        }

        public List<ParameterMapping> getParameterMappings()
        {
            return parameterMappings;
        }

        public Map<String, Object> getAdditionalValues()
        {
            return additionalValues;
        }
    }

    /**
     * 源表别名绑定
     */
    private static final class SourceBinding
    {
        private final String alias;

        private final DataTranslationRule rule;

        private SourceBinding(String alias, DataTranslationRule rule)
        {
            this.alias = alias;
            this.rule = rule;
        }
    }

    /**
     * 一个待翻译的源字段（同一字段在一条 SQL 内只关联一次）
     */
    private static final class TranslationTarget
    {
        private final SourceBinding binding;

        private final DataTranslationField field;

        private final DataTranslationLocale locale;

        private final String exactAlias;

        private final String baseAlias;

        private TranslationTarget(SourceBinding binding, DataTranslationField field, DataTranslationLocale locale,
                String exactAlias, String baseAlias)
        {
            this.binding = binding;
            this.field = field;
            this.locale = locale;
            this.exactAlias = exactAlias;
            this.baseAlias = baseAlias;
        }

        /**
         * 最终显示表达式：精确语言优先、基础语言兜底、最后回退原文
         */
        private String displayExpression()
        {
            StringBuilder builder = new StringBuilder("COALESCE(");
            if (exactAlias != null)
            {
                builder.append(exactAlias).append(".translated_text, ");
            }
            if (baseAlias != null)
            {
                builder.append(baseAlias).append(".translated_text, ");
            }
            builder.append(binding.alias).append('.').append(field.getColumnName()).append(')');
            return builder.toString();
        }
    }

    /**
     * 新增绑定参数
     */
    private static final class NewParameter
    {
        private final String name;

        private final Object value;

        private NewParameter(String name, Object value)
        {
            this.name = name;
            this.value = value;
        }
    }

    /**
     * 关键词谓词命中
     */
    private static final class KeywordHit
    {
        private final LikeExpression likeExpression;

        private final TranslationTarget target;

        private KeywordHit(LikeExpression likeExpression, TranslationTarget target)
        {
            this.likeExpression = likeExpression;
            this.target = target;
        }
    }

    /**
     * 收集占位符顺序的表达式反解析器，用于按最终 SQL 的问号顺序重建参数映射
     */
    private static final class CollectingExpressionDeParser extends ExpressionDeParser
    {
        private final List<JdbcParameter> collected = new ArrayList<>();

        private CollectingExpressionDeParser(net.sf.jsqlparser.statement.select.SelectVisitor selectVisitor,
                StringBuilder buffer)
        {
            super(selectVisitor, buffer);
        }

        @Override
        public void visit(JdbcParameter jdbcParameter)
        {
            collected.add(jdbcParameter);
            super.visit(jdbcParameter);
        }
    }

    /**
     * 单次改写会话
     */
    private final class RewriteSession
    {
        private final RewriteRequest request;

        private final Statement statement;

        private final PlainSelect plainSelect;

        private final Map<String, SourceBinding> bindings = new LinkedHashMap<>();

        private final Map<String, TranslationTarget> targets = new LinkedHashMap<>();

        private final IdentityHashMap<JdbcParameter, Integer> originalOrdinals = new IdentityHashMap<>();

        private final IdentityHashMap<JdbcParameter, NewParameter> newParameters = new IdentityHashMap<>();

        private int aliasSequence;

        private int parameterSequence;

        private RewriteSession(RewriteRequest request, Statement statement, PlainSelect plainSelect)
        {
            this.request = request;
            this.statement = statement;
            this.plainSelect = plainSelect;
        }

        /**
         * 登记本查询引用的源表别名（只识别顶层 FROM/JOIN，子查询内的表不参与展示翻译）
         */
        private void collectBindings()
        {
            registerFromItem(plainSelect.getFromItem());
            List<Join> joins = plainSelect.getJoins();
            if (joins != null)
            {
                for (Join join : joins)
                {
                    registerFromItem(join.getRightItem());
                }
            }
        }

        private void registerFromItem(FromItem fromItem)
        {
            if (!(fromItem instanceof Table table))
            {
                return;
            }
            DataTranslationRule rule = request.getRegistry().findByTable(table.getName());
            if (rule == null)
            {
                return;
            }
            String alias = table.getAlias() != null ? table.getAlias().getName() : table.getName();
            String normalized = stripDelimiters(alias).toLowerCase(Locale.ROOT);
            if (!SAFE_IDENTIFIER.matcher(normalized).matches())
            {
                throw unsupported("源表别名不合法：" + alias);
            }
            bindings.putIfAbsent(normalized, new SourceBinding(stripDelimiters(alias), rule));
        }

        /**
         * 建立原参数在 SQL 中的顺序，用于复用原有 ParameterMapping
         */
        private void buildOriginalParameterOrdinals()
        {
            List<JdbcParameter> parameters = collectParameters(statement);
            List<ParameterMapping> mappings = request.getParameterMappings();
            if (mappings == null || parameters.size() != mappings.size())
            {
                throw unsupported("参数映射数量与 SQL 占位符不一致，拒绝改写");
            }
            for (int i = 0; i < parameters.size(); i++)
            {
                originalOrdinals.put(parameters.get(i), i);
            }
        }

        /**
         * 替换登记字段的直接投影
         */
        private void rewriteSelectItems()
        {
            List<SelectItem<?>> items = plainSelect.getSelectItems();
            if (items == null)
            {
                return;
            }
            for (int i = 0; i < items.size(); i++)
            {
                SelectItem<?> item = items.get(i);
                Expression expression = item.getExpression();
                if (expression instanceof AllColumns)
                {
                    throw unsupported("不支持 SELECT * / 别名.* 的自动翻译，请显式列出字段");
                }
                if (!(expression instanceof Column column))
                {
                    continue;
                }
                SourceBinding binding = resolveBinding(column);
                if (binding == null)
                {
                    continue;
                }
                DataTranslationField field = binding.rule.findField(column.getColumnName());
                if (field == null)
                {
                    continue;
                }
                TranslationTarget target = targetFor(binding, field);
                if (target == null)
                {
                    continue;
                }
                Alias alias = item.getAlias() != null ? item.getAlias()
                        : new Alias(stripDelimiters(column.getColumnName()));
                items.set(i, SelectItem.from(parseExpression(target.displayExpression()), alias));
            }
        }

        /**
         * 受控关键词谓词扩展为“原文字段或最终显示值”匹配
         */
        private void rewriteKeywordPredicate()
        {
            String keywordColumn = trimToNull(request.getKeywordColumn());
            String keywordParameter = trimToNull(request.getKeywordParameter());
            if (keywordColumn == null)
            {
                return;
            }
            if (keywordParameter == null)
            {
                throw unsupported("标记了关键词列但未声明关键词参数");
            }
            boolean parameterBound = originalOrdinals.values().stream()
                    .map(index -> request.getParameterMappings().get(index).getProperty())
                    .anyMatch(keywordParameter::equals);
            if (!parameterBound)
            {
                return;
            }

            Expression where = plainSelect.getWhere();
            if (where == null)
            {
                throw unsupported("关键词扩展失败：查询缺少 WHERE 条件");
            }
            KeywordHit hit = findKeywordLike(where, keywordColumn, keywordParameter);
            if (hit == null)
            {
                throw unsupported("关键词扩展失败：未找到 " + keywordColumn + " 上绑定 " + keywordParameter + " 的 LIKE 谓词");
            }
            if (hit.target == null)
            {
                throw unsupported("关键词列未登记为可翻译字段：" + keywordColumn);
            }

            Object keywordValue = resolveKeywordValue(keywordParameter);
            Expression extension = buildKeywordExtension(hit.target, keywordValue);
            Expression newWhere = replaceExpression(where, hit.likeExpression,
                    new Parenthesis(new OrExpression(hit.likeExpression, extension)));
            if (newWhere == null)
            {
                throw unsupported("关键词谓词替换失败");
            }
            plainSelect.setWhere(newWhere);
        }

        private KeywordHit findKeywordLike(Expression expression, String keywordColumn, String keywordParameter)
        {
            if (expression == null)
            {
                return null;
            }
            if (expression instanceof LikeExpression likeExpression)
            {
                Expression left = likeExpression.getLeftExpression();
                if (left instanceof Column column && matchesKeywordColumn(column, keywordColumn))
                {
                    JdbcParameter parameter = findOriginalParameter(likeExpression.getRightExpression(),
                            keywordParameter);
                    if (parameter != null)
                    {
                        SourceBinding binding = resolveBinding(column);
                        TranslationTarget target = null;
                        if (binding != null)
                        {
                            DataTranslationField field = binding.rule.findField(column.getColumnName());
                            if (field != null)
                            {
                                target = targetFor(binding, field);
                            }
                        }
                        return new KeywordHit(likeExpression, target);
                    }
                }
                return null;
            }
            if (expression instanceof BinaryExpression binary)
            {
                KeywordHit hit = findKeywordLike(binary.getLeftExpression(), keywordColumn, keywordParameter);
                if (hit != null)
                {
                    return hit;
                }
                return findKeywordLike(binary.getRightExpression(), keywordColumn, keywordParameter);
            }
            if (expression instanceof Parenthesis parenthesis)
            {
                return findKeywordLike(parenthesis.getExpression(), keywordColumn, keywordParameter);
            }
            if (expression instanceof NotExpression notExpression)
            {
                return findKeywordLike(notExpression.getExpression(), keywordColumn, keywordParameter);
            }
            return null;
        }

        private boolean matchesKeywordColumn(Column column, String keywordColumn)
        {
            int dot = keywordColumn.indexOf('.');
            if (dot <= 0 || dot == keywordColumn.length() - 1 || column.getTable() == null
                    || column.getTable().getName() == null)
            {
                throw unsupported("关键词列必须写成 别名.列名 的形式：" + keywordColumn);
            }
            String alias = stripDelimiters(keywordColumn.substring(0, dot));
            String name = stripDelimiters(keywordColumn.substring(dot + 1));
            return alias.equalsIgnoreCase(stripDelimiters(column.getTable().getName()))
                    && name.equalsIgnoreCase(stripDelimiters(column.getColumnName()));
        }

        private JdbcParameter findOriginalParameter(Expression expression, String property)
        {
            if (!(expression instanceof Function function) || function.getParameters() == null)
            {
                return null;
            }
            ExpressionList<?> parameters = function.getParameters();
            for (Expression item : parameters)
            {
                if (item instanceof JdbcParameter parameter)
                {
                    Integer ordinal = originalOrdinals.get(parameter);
                    if (ordinal != null && property.equals(request.getParameterMappings().get(ordinal).getProperty()))
                    {
                        return parameter;
                    }
                }
            }
            return null;
        }

        private Expression buildKeywordExtension(TranslationTarget target, Object keywordValue)
        {
            String fragment = "select 1 where " + target.displayExpression() + " like concat('%', ?, '%')";
            Statement fragmentStatement = parseStatement(fragment);
            PlainSelect fragmentSelect = ((Select) fragmentStatement).getPlainSelect();
            List<JdbcParameter> parameters = collectParameters(fragmentStatement);
            if (parameters.size() != 1)
            {
                throw unsupported("关键词扩展占位符数量异常");
            }
            newParameters.put(parameters.get(0), newParameter(keywordValue));
            return fragmentSelect.getWhere();
        }

        private Object resolveKeywordValue(String property)
        {
            if (request.getParameterObject() != null)
            {
                try
                {
                    Object value = request.getConfiguration().newMetaObject(request.getParameterObject())
                            .getValue(property);
                    if (value != null)
                    {
                        return value;
                    }
                }
                catch (RuntimeException ignored)
                {
                    // 属性不存在时继续尝试附加参数
                }
            }
            Map<String, Object> additional = request.getAdditionalParameters();
            if (additional != null && additional.containsKey(property))
            {
                return additional.get(property);
            }
            throw unsupported("关键词参数取值失败：" + property);
        }

        /**
         * 追加译文关联，条件放在 ON 中，避免过滤无译文的业务行
         */
        private void appendTranslationJoins()
        {
            List<Join> joins = plainSelect.getJoins();
            if (joins == null)
            {
                joins = new ArrayList<>();
                plainSelect.setJoins(joins);
            }
            for (TranslationTarget target : targets.values())
            {
                if (target.exactAlias != null)
                {
                    joins.add(buildTranslationJoin(target, target.exactAlias, target.locale.getExact()));
                }
                if (target.baseAlias != null)
                {
                    joins.add(buildTranslationJoin(target, target.baseAlias, target.locale.getBase()));
                }
            }
        }

        private Join buildTranslationJoin(TranslationTarget target, String translationAlias, String localeTag)
        {
            String sourceRef = target.binding.alias + "." + target.binding.rule.getIdColumn();
            String columnRef = target.binding.alias + "." + target.field.getColumnName();
            String alias = translationAlias;
            String condition = alias + ".biz_type = ?"
                    + " and " + alias + ".biz_id = cast(" + sourceRef + " as char)"
                    + " and " + alias + ".field_code = ?"
                    + " and " + alias + ".locale = ?"
                    + " and " + alias + ".del_flag = '0'"
                    + " and length(" + alias + ".translated_text) > 0"
                    + " and " + alias + ".source_hash = sha2(convert(" + columnRef + " using utf8mb4), 256)";
            String fragment = "select 1 from " + target.binding.rule.getTableName() + " __dt_source"
                    + " left join " + TRANSLATION_TABLE + " " + alias + " on " + condition;
            Statement fragmentStatement = parseStatement(fragment);
            List<JdbcParameter> parameters = collectParameters(fragmentStatement);
            if (parameters.size() != EXACT_JOIN_PARAM_COUNT)
            {
                throw unsupported("译文关联占位符数量异常");
            }
            newParameters.put(parameters.get(0), newParameter(target.binding.rule.getBizType()));
            newParameters.put(parameters.get(1), newParameter(target.field.getFieldCode()));
            newParameters.put(parameters.get(2), newParameter(localeTag));
            return ((Select) fragmentStatement).getPlainSelect().getJoins().get(0);
        }

        /**
         * 复用或创建一个源字段的译文关联
         */
        private TranslationTarget targetFor(SourceBinding binding, DataTranslationField field)
        {
            String key = binding.alias.toLowerCase(Locale.ROOT) + '|'
                    + field.getColumnName().toLowerCase(Locale.ROOT);
            if (targets.containsKey(key))
            {
                return targets.get(key);
            }
            DataTranslationLocale locale = DataTranslationUtils.resolveLocale(request.getLocale(),
                    binding.rule.getSourceLocale());
            if (locale.isSourceLanguage())
            {
                return null;
            }
            String exactAlias = locale.getExact() == null ? null : "i18n_e" + aliasSequence++;
            String baseAlias = locale.getBase() == null ? null : "i18n_b" + aliasSequence++;
            TranslationTarget target = new TranslationTarget(binding, field, locale, exactAlias, baseAlias);
            targets.put(key, target);
            return target;
        }

        private SourceBinding resolveBinding(Column column)
        {
            if (column.getTable() == null || column.getTable().getName() == null)
            {
                return null;
            }
            String alias = stripDelimiters(column.getTable().getName()).toLowerCase(Locale.ROOT);
            return bindings.get(alias);
        }

        /**
         * 按最终 SQL 的问号顺序重建参数映射
         */
        private RewriteResult render()
        {
            List<JdbcParameter> ordered = collectParameters(statement);
            List<ParameterMapping> mappings = new ArrayList<>(ordered.size());
            Map<String, Object> additionalValues = new LinkedHashMap<>();
            Set<Integer> reused = new LinkedHashSet<>();
            for (JdbcParameter parameter : ordered)
            {
                Integer ordinal = originalOrdinals.get(parameter);
                if (ordinal != null)
                {
                    if (!reused.add(ordinal))
                    {
                        throw unsupported("原参数被重复绑定，拒绝改写");
                    }
                    mappings.add(request.getParameterMappings().get(ordinal));
                    continue;
                }
                NewParameter newParameter = newParameters.get(parameter);
                if (newParameter == null)
                {
                    throw unsupported("存在无法定位绑定的占位符，拒绝改写");
                }
                mappings.add(new ParameterMapping.Builder(request.getConfiguration(), newParameter.name,
                        String.class).build());
                additionalValues.put(newParameter.name, newParameter.value);
            }
            if (reused.size() != request.getParameterMappings().size())
            {
                throw unsupported("原参数未被完整保留，拒绝改写");
            }
            String sql = deparse(statement);
            if (countPlaceholders(sql) != mappings.size())
            {
                throw unsupported("改写后占位符数量与参数映射不一致，拒绝改写");
            }
            return new RewriteResult(sql, mappings, additionalValues);
        }

        private NewParameter newParameter(Object value)
        {
            String name;
            do
            {
                name = ADDITIONAL_PARAM_PREFIX + parameterSequence++;
            }
            while (request.getAdditionalParameters() != null && request.getAdditionalParameters().containsKey(name));
            return new NewParameter(name, value);
        }

        private Expression parseExpression(String fragment)
        {
            try
            {
                return CCJSqlParserUtil.parseExpression(fragment);
            }
            catch (JSQLParserException e)
            {
                throw unsupported("表达式解析失败：" + e.getMessage());
            }
        }

        /**
         * 在表达式树中替换指定节点，返回新的根节点；未找到返回 null
         */
        private Expression replaceExpression(Expression root, Expression target, Expression replacement)
        {
            if (root == target)
            {
                return replacement;
            }
            if (root instanceof BinaryExpression binary)
            {
                Expression left = replaceExpression(binary.getLeftExpression(), target, replacement);
                if (left != null)
                {
                    binary.setLeftExpression(left);
                    return root;
                }
                Expression right = replaceExpression(binary.getRightExpression(), target, replacement);
                if (right != null)
                {
                    binary.setRightExpression(right);
                    return root;
                }
                return null;
            }
            if (root instanceof Parenthesis parenthesis)
            {
                Expression inner = replaceExpression(parenthesis.getExpression(), target, replacement);
                if (inner != null)
                {
                    parenthesis.setExpression(inner);
                    return root;
                }
                return null;
            }
            if (root instanceof NotExpression notExpression)
            {
                Expression inner = replaceExpression(notExpression.getExpression(), target, replacement);
                if (inner != null)
                {
                    notExpression.setExpression(inner);
                    return root;
                }
                return null;
            }
            if (root instanceof ExistsExpression existsExpression)
            {
                Expression inner = replaceExpression(existsExpression.getRightExpression(), target, replacement);
                if (inner != null)
                {
                    existsExpression.setRightExpression(inner);
                    return root;
                }
                return null;
            }
            if (root instanceof Function function && function.getParameters() != null)
            {
                ExpressionList parameters = function.getParameters();
                for (int i = 0; i < parameters.size(); i++)
                {
                    Expression replaced = replaceExpression((Expression) parameters.get(i), target, replacement);
                    if (replaced != null)
                    {
                        parameters.set(i, replaced);
                        return root;
                    }
                }
                return null;
            }
            if (root instanceof InExpression inExpression)
            {
                Expression left = replaceExpression(inExpression.getLeftExpression(), target, replacement);
                if (left != null)
                {
                    inExpression.setLeftExpression(left);
                    return root;
                }
                return null;
            }
            return null;
        }
    }

    /**
     * 按 SQL 文本顺序收集占位符（与最终输出顺序一致）
     */
    private static List<JdbcParameter> collectParameters(Statement statement)
    {
        StringBuilder buffer = new StringBuilder();
        SelectDeParser selectDeParser = new SelectDeParser(buffer);
        CollectingExpressionDeParser expressionDeParser = new CollectingExpressionDeParser(selectDeParser, buffer);
        selectDeParser.setExpressionVisitor(expressionDeParser);
        StatementDeParser statementDeParser = new StatementDeParser(expressionDeParser, selectDeParser, buffer);
        statement.accept(statementDeParser);
        return expressionDeParser.collected;
    }

    /**
     * 反解析为 SQL 文本
     */
    private static String deparse(Statement statement)
    {
        StringBuilder buffer = new StringBuilder();
        SelectDeParser selectDeParser = new SelectDeParser(buffer);
        CollectingExpressionDeParser expressionDeParser = new CollectingExpressionDeParser(selectDeParser, buffer);
        selectDeParser.setExpressionVisitor(expressionDeParser);
        StatementDeParser statementDeParser = new StatementDeParser(expressionDeParser, selectDeParser, buffer);
        statement.accept(statementDeParser);
        return buffer.toString();
    }

    /**
     * 统计未包含在引号内的问号数量
     */
    private static int countPlaceholders(String sql)
    {
        int count = 0;
        char quote = 0;
        for (int i = 0; i < sql.length(); i++)
        {
            char c = sql.charAt(i);
            if (quote != 0)
            {
                if (c == quote)
                {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote)
                    {
                        i++;
                    }
                    else
                    {
                        quote = 0;
                    }
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`')
            {
                quote = c;
            }
            else if (c == '?')
            {
                count++;
            }
        }
        return count;
    }

    private static String stripDelimiters(String name)
    {
        String result = name == null ? null : name.trim();
        while (result != null && result.length() >= 2)
        {
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);
            boolean quoted = (first == '`' && last == '`') || (first == '"' && last == '"')
                    || (first == '\'' && last == '\'');
            if (!quoted)
            {
                break;
            }
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
