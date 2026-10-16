package com.hmong.dbi18n.core;

/**
 * 库内消息文本
 * <p>
 * 直接以中文常量提供，避免依赖使用方的国际化资源包；需要改文案的业务系统
 * 可以替换维护服务 Bean 或对异常做统一转换。
 *
 * @author yanx
 */
public final class Messages
{
    private Messages()
    {
    }

    public static final String BIZ_TYPE_UNSUPPORTED = "未登记的业务类型：{0}";
    public static final String LOCALE_INVALID = "目标语言不合法";
    public static final String LOCALE_SOURCE = "源语言无需维护译文";
    public static final String ROW_LOCALE_INVALID = "第{0}行目标语言不合法";
    public static final String ROW_LOCALE_SOURCE = "第{0}行目标语言为源语言，无需维护译文";
    public static final String ROW_BIZ_ID_INVALID = "第{0}行业务主键不能为空且长度不能超过64个字符";
    public static final String ROW_FIELD_UNSUPPORTED = "第{0}行字段未登记：{1}";
    public static final String ROW_TEXT_EMPTY = "第{0}行译文不能为空";
    public static final String ROW_TEXT_SIZE = "第{0}行译文长度不能超过{1}个字符";
    public static final String ROW_HASH_INVALID = "第{0}行原文摘要缺失或格式不正确，请重新导出清单后再提交";
    public static final String ROW_DUPLICATE = "第{0}行与本次提交中的其他条目重复";
    public static final String BIZ_ID_INVALID = "业务主键不合法：{0}";
    public static final String SOURCE_MISSING = "第{0}行对应的源记录不存在或已删除：{1}";
    public static final String SOURCE_EMPTY = "第{0}行对应的原文为空，无法维护译文：{1}";
    public static final String HASH_CONFLICT = "第{0}行译文对应的原文已变化，请重新导出清单后再提交：{1}";
    public static final String SAVE_SUCCESS = "译文保存成功，共 {0} 条";
    public static final String SAVE_CHUNKED = "译文导入完成，成功 {0} / {1} 条";
    public static final String ID_EMPTY = "译文主键不能为空";
    public static final String NOT_EXIST = "译文不存在或已删除";
    public static final String RULE_EMPTY = "尚未登记任何可翻译业务，无法开启";
    public static final String STORAGE_UNAVAILABLE = "翻译表不可用，无法开启自动翻译";
    public static final String ITEMS_EMPTY = "译文条目不能为空";
    public static final String STATUS_MISSING = "未维护";
    public static final String STATUS_TRANSLATED = "已维护";
    public static final String STATUS_STALE = "已过期";
    public static final String REWRITE_REJECTED = "数据字段国际化改写失败：{0}";
    public static final String REWRITE_REJECTED_LOG = "数据字段国际化改写被拒绝，statement={0}，原因={1}";
    public static final String PLUGIN_CHAIN_OK = "数据字段国际化插件链顺序校验通过：{0}";
    public static final String PLUGIN_CHAIN_BAD = "插件链顺序不符合数据字段国际化要求：{0}，自动翻译会晚于分页生成 SQL";
    public static final String PLUGIN_CHAIN_DUPLICATE = "检测到多个 PageHelper 分页拦截器，请检查分页插件配置：{0}";
    public static final String RULE_COUNT = "数据字段国际化登记数量：{0}";
    public static final String STATIC_SWITCH_WRITE = "当前使用本地静态开关，开关改动仅对本节点生效";

    /**
     * 按 {@code {0}} 占位符格式化
     *
     * @param template 模板
     * @param args     参数
     * @return 格式化结果
     */
    public static String format(String template, Object... args)
    {
        if (template == null)
        {
            return null;
        }
        if (args == null || args.length == 0)
        {
            return template;
        }
        return java.text.MessageFormat.format(template, args);
    }

    /**
     * 按 {0} 占位符格式化
     *
     * @param template 模板
     * @param args     参数
     * @return 格式化结果
     */
    public static String of(String template, Object... args)
    {
        return format(template, args);
    }
}
