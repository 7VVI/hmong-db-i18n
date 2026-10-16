-- ----------------------------
-- hmong-db-i18n 通用业务字段翻译表
-- 原文保留在业务原表，本表只存译文；唯一定位：biz_type + biz_id + field_code + locale
-- 可重复执行（IF NOT EXISTS），不包含 DROP，不影响既有业务表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_data_translation` (
  `id` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键，32位UUID',
  `biz_type` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务类型标识',
  `biz_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '源记录规范主键',
  `field_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务字段标识',
  `locale` varchar(35) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范语言码',
  `translated_text` text NOT NULL COMMENT '译文内容',
  `source_hash` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原文UTF8字节的SHA256小写摘要',
  `create_by` bigint NOT NULL DEFAULT 0 COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL DEFAULT 0 COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` varchar(1) NOT NULL DEFAULT '0' COMMENT '删除标志，0有效1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_field_locale` (`biz_type`, `biz_id`, `field_code`, `locale`) COMMENT '业务记录字段语言唯一'
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通用业务字段翻译表';
