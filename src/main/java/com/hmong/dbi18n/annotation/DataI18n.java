package com.hmong.dbi18n.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据字段国际化标记，只加在 Mapper 查询方法上
 * <p>
 * 标记的方法在“全局开关开启 + 存在请求语言快照 + 查询引用了已登记的表”时，由
 * {@code DataTranslationInnerInterceptor} 自动为登记字段追加译文关联，
 * 并把显示值改写为 {@code COALESCE(精确语言译文, 基础语言译文, 原文)}。
 * <p>
 * 只应在<i>展示</i>查询上标记。按原文加载、名称去重、新增/编辑、导入等写入链路的读取
 * 不能标记，否则会把展示译文当成业务原文使用。
 *
 * @author yanx
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataI18n
{
    /**
     * 需要同时按“原文或最终显示值”扩展的关键词列，形如 {@code d.name}
     * <p>
     * 必须指向登记字段，且 SQL 中该列的关键词谓词必须写成
     * {@code d.name LIKE CONCAT('%', #{keyword}, '%')}；为空表示该查询不扩展关键词。
     *
     * @return 关键词列，默认不扩展
     */
    String keywordColumn() default "";

    /**
     * 关键词在 Mapper 参数中的绑定名，如 {@code keyword}
     * <p>
     * {@link #keywordColumn()} 非空时必填；若该参数已绑定进 SQL（即关键词条件生效）
     * 但找不到对应的 LIKE 谓词，会直接报错，避免静默产生不完整查询。
     *
     * @return 关键词参数绑定名，默认空
     */
    String keywordParameter() default "";
}
