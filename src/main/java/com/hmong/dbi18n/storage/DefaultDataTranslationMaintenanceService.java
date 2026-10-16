package com.hmong.dbi18n.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionTemplate;

import com.hmong.dbi18n.core.DataTranslationException;
import com.hmong.dbi18n.core.DataTranslationField;
import com.hmong.dbi18n.core.DataTranslationRule;
import com.hmong.dbi18n.core.DataTranslationUtils;
import com.hmong.dbi18n.core.Messages;
import com.hmong.dbi18n.storage.dto.DataTranslationQuery;
import com.hmong.dbi18n.storage.dto.DataTranslationSaveItem;
import com.hmong.dbi18n.storage.vo.DataTranslationItemVO;
import com.hmong.dbi18n.storage.vo.PageResult;

/**
 * 译文维护的默认实现
 * <p>
 * 维护列表与导出以源记录为基准；写入在事务内按固定顺序锁定源记录、校验原文摘要后再批量 upsert，
 * 避免旧译文覆盖新原文。维护过程不参与展示翻译（与开关无关）。
 *
 * @author yanx
 */
public class DefaultDataTranslationMaintenanceService implements DataTranslationMaintenanceService
{
    private static final Logger log = LoggerFactory.getLogger(DefaultDataTranslationMaintenanceService.class);

    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_PAGE_SIZE = 500;

    private static final int DEFAULT_CHUNK_SIZE = 200;

    private static final int UPSERT_BATCH_SIZE = 300;

    private static final int SOURCE_QUERY_BATCH_SIZE = 500;

    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final DataTranslationRepository repository;

    private final TransactionTemplate transactionTemplate;

    private final ObjectProvider<DataTranslationSourceProvider> providerProvider;

    private final DataTranslationOperatorProvider operatorProvider;

    private volatile Map<String, DataTranslationSourceProvider> providerCache;

    public DefaultDataTranslationMaintenanceService(DataTranslationRepository repository,
            TransactionTemplate transactionTemplate, ObjectProvider<DataTranslationSourceProvider> providerProvider,
            DataTranslationOperatorProvider operatorProvider)
    {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.providerProvider = providerProvider;
        this.operatorProvider = operatorProvider;
    }

    @Override
    public PageResult<DataTranslationItemVO> selectItemPage(DataTranslationQuery query)
    {
        DataTranslationSourceProvider provider = requireProvider(query.getBizType());
        String locale = requireQueryLocale(query.getLocale(), provider.rule());
        int pageSize = normalizePageSize(query.getPageSize());
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        String keyword = trimToNull(query.getKeyword());

        List<DataTranslationSourceItem> items = provider.pageSource(keyword, (pageNum - 1) * pageSize, pageSize);
        long total = provider.countSource(keyword);
        return PageResult.of(total, buildRows(provider.rule(), items, locale));
    }

    @Override
    public List<DataTranslationItemVO> selectItemsForExport(DataTranslationQuery query, int maxRows)
    {
        DataTranslationSourceProvider provider = requireProvider(query.getBizType());
        String locale = requireQueryLocale(query.getLocale(), provider.rule());
        String keyword = trimToNull(query.getKeyword());
        int limit = maxRows <= 0 ? DEFAULT_CHUNK_SIZE : maxRows;

        List<DataTranslationItemVO> rows = new ArrayList<>();
        int offset = 0;
        int batch = Math.min(DEFAULT_CHUNK_SIZE, limit);
        while (rows.size() < limit)
        {
            List<DataTranslationSourceItem> items = provider.pageSource(keyword, offset, batch);
            if (items.isEmpty())
            {
                break;
            }
            rows.addAll(buildRows(provider.rule(), items, locale));
            offset += items.size();
            if (items.size() < batch)
            {
                break;
            }
        }
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    @Override
    public Map<String, Object> saveBatch(List<DataTranslationSaveItem> items)
    {
        if (items == null || items.isEmpty())
        {
            throw new DataTranslationException(Messages.ITEMS_EMPTY);
        }
        Map<DataTranslationSourceProvider, List<PreparedItem>> prepared = prepareAll(items);
        Long operator = currentOperator();
        transactionTemplate.executeWithoutResult(status -> {
            List<TranslationRow> rows = new ArrayList<>();
            for (Map.Entry<DataTranslationSourceProvider, List<PreparedItem>> entry : prepared.entrySet())
            {
                rows.addAll(verifyAndBuild(entry.getKey(), entry.getValue(), operator));
            }
            for (int i = 0; i < rows.size(); i += UPSERT_BATCH_SIZE)
            {
                repository.upsert(rows.subList(i, Math.min(i + UPSERT_BATCH_SIZE, rows.size())));
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("successCount", items.size());
        result.put("failCount", 0);
        result.put("failures", new ArrayList<>());
        result.put("message", Messages.of(Messages.SAVE_SUCCESS, items.size()));
        return result;
    }

    @Override
    public Map<String, Object> saveInChunks(List<DataTranslationSaveItem> items, int chunkSize)
    {
        if (items == null || items.isEmpty())
        {
            throw new DataTranslationException(Messages.ITEMS_EMPTY);
        }
        int size = chunkSize <= 0 ? DEFAULT_CHUNK_SIZE : chunkSize;
        int successCount = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        for (int start = 0; start < items.size(); start += size)
        {
            int end = Math.min(start + size, items.size());
            List<DataTranslationSaveItem> chunk = items.subList(start, end);
            try
            {
                saveBatch(chunk);
                successCount += chunk.size();
            }
            catch (Exception e)
            {
                log.error("批量保存译文失败，起始行 {}", start + 1, e);
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("rowStart", start + 1);
                failure.put("rowEnd", end);
                failure.put("reason", e.getMessage());
                failures.add(failure);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", failures.isEmpty());
        result.put("successCount", successCount);
        result.put("failCount", items.size() - successCount);
        result.put("failures", failures);
        result.put("message", Messages.of(Messages.SAVE_CHUNKED, successCount, items.size()));
        return result;
    }

    @Override
    public int deleteById(String id)
    {
        String normalized = DataTranslationUtils.normalizeBizId(id);
        if (normalized == null)
        {
            throw new DataTranslationException(Messages.ID_EMPTY);
        }
        int rows = repository.logicDeleteByIds(List.of(normalized), currentOperator());
        if (rows <= 0)
        {
            throw new DataTranslationException(Messages.NOT_EXIST);
        }
        return rows;
    }

    @Override
    public int logicDeleteBizTranslations(String bizType, String fieldCode, Collection<String> bizIds, Long operator)
    {
        if (bizIds == null || bizIds.isEmpty())
        {
            return 0;
        }
        List<String> normalizedIds = bizIds.stream()
                .map(DataTranslationUtils::normalizeBizId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty())
        {
            return 0;
        }
        int rows = 0;
        for (int i = 0; i < normalizedIds.size(); i += SOURCE_QUERY_BATCH_SIZE)
        {
            List<String> batch = normalizedIds.subList(i, Math.min(i + SOURCE_QUERY_BATCH_SIZE, normalizedIds.size()));
            rows += repository.logicDeleteByBizIds(bizType, fieldCode, batch, operator == null ? 0L : operator);
        }
        return rows;
    }

    @Override
    public boolean isRegisteredBizType(String bizType)
    {
        return bizType != null && providerMap().containsKey(bizType.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public String sourceLocaleOf(String bizType)
    {
        DataTranslationSourceProvider provider = bizType == null ? null
                : providerMap().get(bizType.trim().toLowerCase(Locale.ROOT));
        return provider == null ? null : provider.rule().getSourceLocale();
    }

    @Override
    public void checkReadyToEnable()
    {
        if (providerMap().isEmpty())
        {
            throw new DataTranslationException(Messages.RULE_EMPTY);
        }
        try
        {
            repository.assertTableAvailable();
        }
        catch (Exception e)
        {
            log.error("数据字段国际化就绪校验失败", e);
            throw new DataTranslationException(Messages.STORAGE_UNAVAILABLE, e);
        }
    }

    private List<DataTranslationItemVO> buildRows(DataTranslationRule rule, List<DataTranslationSourceItem> items,
            String locale)
    {
        List<DataTranslationItemVO> rows = new ArrayList<>();
        if (items == null || items.isEmpty())
        {
            return rows;
        }
        List<String> bizIds = items.stream()
                .map(DataTranslationSourceItem::getBizId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> fieldCodes = rule.getFields().stream().map(DataTranslationField::fieldCode).toList();
        Map<String, TranslationRow> translations = loadTranslations(rule.getBizType(), bizIds, fieldCodes,
                List.of(locale));

        for (DataTranslationSourceItem item : items)
        {
            for (DataTranslationField field : rule.getFields())
            {
                String sourceText = item.sourceValue(field.fieldCode());
                String sourceHash = DataTranslationUtils.sha256Hex(sourceText);
                DataTranslationItemVO vo = new DataTranslationItemVO();
                vo.setBizType(rule.getBizType());
                vo.setBizId(item.getBizId());
                vo.setLabel(item.getLabel());
                vo.setFieldCode(field.fieldCode());
                vo.setLocale(locale);
                vo.setSourceText(sourceText);
                vo.setExpectedSourceHash(sourceHash);

                TranslationRow translation = translations.get(translationKey(item.getBizId(), field.fieldCode()));
                if (translation == null)
                {
                    vo.setStatus(DataTranslationItemVO.STATUS_MISSING);
                    vo.setStatusText(Messages.STATUS_MISSING);
                }
                else
                {
                    vo.setTranslationId(translation.getId());
                    vo.setTranslatedText(translation.getTranslatedText());
                    boolean valid = sourceHash != null && sourceHash.equalsIgnoreCase(translation.getSourceHash());
                    vo.setStatus(valid ? DataTranslationItemVO.STATUS_TRANSLATED : DataTranslationItemVO.STATUS_STALE);
                    vo.setStatusText(valid ? Messages.STATUS_TRANSLATED : Messages.STATUS_STALE);
                }
                rows.add(vo);
            }
        }
        return rows;
    }

    private Map<String, TranslationRow> loadTranslations(String bizType, List<String> bizIds,
            List<String> fieldCodes, List<String> locales)
    {
        Map<String, TranslationRow> result = new HashMap<>();
        for (int i = 0; i < bizIds.size(); i += SOURCE_QUERY_BATCH_SIZE)
        {
            List<String> batch = bizIds.subList(i, Math.min(i + SOURCE_QUERY_BATCH_SIZE, bizIds.size()));
            for (TranslationRow row : repository.select(bizType, batch, fieldCodes, locales))
            {
                result.put(translationKey(row.getBizId(), row.getFieldCode()), row);
            }
        }
        return result;
    }

    private Map<DataTranslationSourceProvider, List<PreparedItem>> prepareAll(List<DataTranslationSaveItem> items)
    {
        Map<DataTranslationSourceProvider, List<PreparedItem>> grouped = new LinkedHashMap<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++)
        {
            DataTranslationSaveItem item = items.get(i);
            int rowNum = i + 1;
            DataTranslationSourceProvider provider = requireProvider(item.getBizType());
            DataTranslationRule rule = provider.rule();
            String bizId = DataTranslationUtils.normalizeBizId(item.getBizId());
            if (bizId == null || bizId.length() > 64)
            {
                throw DataTranslationException.of(Messages.ROW_BIZ_ID_INVALID, rowNum);
            }
            DataTranslationField field = rule.findField(item.getFieldCode());
            if (field == null)
            {
                throw DataTranslationException.of(Messages.ROW_FIELD_UNSUPPORTED, rowNum,
                        String.valueOf(item.getFieldCode()));
            }
            String locale = requireRowLocale(item.getLocale(), rule, rowNum);
            String text = item.getTranslatedText();
            if (text == null || text.trim().isEmpty())
            {
                throw DataTranslationException.of(Messages.ROW_TEXT_EMPTY, rowNum);
            }
            if (text.length() > field.maxLength())
            {
                throw DataTranslationException.of(Messages.ROW_TEXT_SIZE, rowNum, field.maxLength());
            }
            String hash = item.getExpectedSourceHash() == null ? null : item.getExpectedSourceHash().trim();
            if (hash == null || !HASH_PATTERN.matcher(hash).matches())
            {
                throw DataTranslationException.of(Messages.ROW_HASH_INVALID, rowNum);
            }
            String key = rule.getBizType() + '|' + bizId + '|' + field.fieldCode() + '|' + locale;
            if (!seenKeys.add(key))
            {
                throw DataTranslationException.of(Messages.ROW_DUPLICATE, rowNum);
            }
            grouped.computeIfAbsent(provider, k -> new ArrayList<>())
                    .add(new PreparedItem(rowNum, bizId, field.fieldCode(), locale, text,
                            hash.toLowerCase(Locale.ROOT)));
        }
        return grouped;
    }

    private List<TranslationRow> verifyAndBuild(DataTranslationSourceProvider provider, List<PreparedItem> prepared,
            Long operator)
    {
        Map<String, PreparedItem> byBizId = new HashMap<>();
        for (PreparedItem item : prepared)
        {
            byBizId.put(item.bizId(), item);
        }
        List<String> sortedIds = byBizId.keySet().stream().sorted(Comparator.naturalOrder()).toList();
        Map<String, DataTranslationSourceItem> sources = new HashMap<>();
        for (int i = 0; i < sortedIds.size(); i += SOURCE_QUERY_BATCH_SIZE)
        {
            List<String> batch = sortedIds.subList(i, Math.min(i + SOURCE_QUERY_BATCH_SIZE, sortedIds.size()));
            for (DataTranslationSourceItem source : provider.listSourceByIdsForUpdate(batch))
            {
                sources.put(source.getBizId(), source);
            }
        }

        List<TranslationRow> rows = new ArrayList<>(prepared.size());
        for (PreparedItem item : prepared)
        {
            DataTranslationSourceItem source = sources.get(item.bizId());
            if (source == null)
            {
                throw DataTranslationException.of(Messages.SOURCE_MISSING, item.rowNum(), item.bizId());
            }
            String sourceText = source.sourceValue(item.fieldCode());
            if (sourceText == null || sourceText.trim().isEmpty())
            {
                throw DataTranslationException.of(Messages.SOURCE_EMPTY, item.rowNum(), item.bizId());
            }
            String currentHash = DataTranslationUtils.sha256Hex(sourceText);
            if (!item.expectedHash().equalsIgnoreCase(currentHash))
            {
                throw DataTranslationException.of(Messages.HASH_CONFLICT, item.rowNum(), item.bizId());
            }

            TranslationRow row = new TranslationRow();
            row.setId(DataTranslationUtils.newTranslationId());
            row.setBizType(provider.rule().getBizType());
            row.setBizId(item.bizId());
            row.setFieldCode(item.fieldCode());
            row.setLocale(item.locale());
            row.setTranslatedText(item.text());
            row.setSourceHash(currentHash);
            row.setCreateBy(operator);
            row.setUpdateBy(operator);
            rows.add(row);
        }
        return rows;
    }

    private DataTranslationSourceProvider requireProvider(String bizType)
    {
        DataTranslationSourceProvider provider = bizType == null ? null
                : providerMap().get(bizType.trim().toLowerCase(Locale.ROOT));
        if (provider == null)
        {
            throw DataTranslationException.of(Messages.BIZ_TYPE_UNSUPPORTED, String.valueOf(bizType));
        }
        return provider;
    }

    private Map<String, DataTranslationSourceProvider> providerMap()
    {
        Map<String, DataTranslationSourceProvider> cached = providerCache;
        if (cached != null)
        {
            return cached;
        }
        synchronized (this)
        {
            if (providerCache == null)
            {
                Map<String, DataTranslationSourceProvider> map = new LinkedHashMap<>();
                for (DataTranslationSourceProvider provider : providerProvider.orderedStream().toList())
                {
                    String key = provider.rule().getBizType().trim().toLowerCase(Locale.ROOT);
                    if (map.put(key, provider) != null)
                    {
                        throw new IllegalStateException("重复登记的数据翻译业务类型：" + key);
                    }
                }
                providerCache = map;
            }
            return providerCache;
        }
    }

    private String requireQueryLocale(String rawLocale, DataTranslationRule rule)
    {
        String locale = canonicalLocale(rawLocale);
        if (locale == null || locale.length() > 35)
        {
            throw new DataTranslationException(Messages.LOCALE_INVALID);
        }
        String sourceLocale = rule.getSourceLocale();
        if (sourceLocale != null && sourceLocale.equalsIgnoreCase(locale))
        {
            throw new DataTranslationException(Messages.LOCALE_SOURCE);
        }
        return locale;
    }

    private String requireRowLocale(String rawLocale, DataTranslationRule rule, int rowNum)
    {
        String locale = canonicalLocale(rawLocale);
        if (locale == null || locale.length() > 35)
        {
            throw DataTranslationException.of(Messages.ROW_LOCALE_INVALID, rowNum);
        }
        String sourceLocale = rule.getSourceLocale();
        if (sourceLocale != null && sourceLocale.equalsIgnoreCase(locale))
        {
            throw DataTranslationException.of(Messages.ROW_LOCALE_SOURCE, rowNum);
        }
        return locale;
    }

    private String canonicalLocale(String rawLocale)
    {
        if (rawLocale == null)
        {
            return null;
        }
        String tag = rawLocale.trim().replace('_', '-');
        if (tag.isEmpty())
        {
            return null;
        }
        Locale locale = Locale.forLanguageTag(tag);
        return locale.getLanguage().isEmpty() ? null : locale.toLanguageTag();
    }

    private int normalizePageSize(Integer pageSize)
    {
        if (pageSize == null || pageSize < 1)
        {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String translationKey(String bizId, String fieldCode)
    {
        return bizId + '|' + fieldCode;
    }

    private Long currentOperator()
    {
        try
        {
            return operatorProvider.currentUserId();
        }
        catch (Exception e)
        {
            return 0L;
        }
    }

    /**
     * 校验并规范化后的待保存条目
     */
    private record PreparedItem(int rowNum, String bizId, String fieldCode, String locale, String text,
            String expectedHash)
    {
    }
}
