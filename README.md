# hmong-db-i18n

数据库字段国际化库：**一张通用翻译表 + `@DataI18n` 标记 + MyBatis-Plus 自动改写 SQL**。

业务原表继续保存原文，需要国际化的展示查询只要加一个注解，就能按请求语言自动返回译文：

```sql
-- 业务 SQL 照旧只写原文列
SELECT d.id AS id, d.name AS name FROM demo_table d WHERE d.level = ?
```

```sql
-- 开关开启且请求语言为 en 时，插件自动改写为（示意）
SELECT d.id AS id, COALESCE(t.translated_text, d.name) AS name
FROM demo_table d
LEFT JOIN sys_data_translation t ON t.biz_type='demo_biz' AND t.biz_id=CAST(d.id AS CHAR)
       AND t.field_code='name' AND t.locale='en' AND t.del_flag='0'
       AND t.source_hash = SHA2(CONVERT(d.name USING utf8mb4), 256)
WHERE d.level = ?
```

---

## 一、能力与特性

- **不影响原表结构**：翻译存独立表，新增语种不需要改表、不需要加语言列；
- **自动改写**：基于 JSQLParser 解析 AST，不使用正则/字符串替换；新参数按最终 SQL 的问号顺序重建绑定，原参数原样复用；
- **语言回退**：`精确语言 → 基础语言 → 原文`（`en-US → en → 原文`），源语言请求直接走原文；
- **原文失效保护**：译文记录翻译时的原文 SHA-256，原文改动后旧译文自动失效（不会展示错译）；
- **运行时开关**：Redis 或本地静态开关，默认**关闭**；关闭时不解析 SQL、不查翻译表；
- **维护闭环**：导出清单（含原文与摘要）→ 人工审核 → 导入/批量保存，写入前在事务内加锁校验摘要，防止旧译文覆盖新原文；
- **安全默认**：只有被 `@DataI18n` 标记的展示查询会被改写；无法安全处理的结构（`SELECT *`、参数错位等）直接报错而不是产出错误 SQL。

## 二、环境要求

| 项 | 要求 |
|---|---|
| JDK | 17+ |
| Spring Boot | 3.x（编译基线 3.4.4，实际使用 3.2+ 亦可） |
| MyBatis-Plus | 3.5.x（提供 `InnerInterceptor` 与 JSQLParser） |
| JSQLParser | 4.7 ~ 4.9（由 MyBatis-Plus 传递引入） |
| 数据库 | MySQL 5.7 / 8.x（使用 `SHA2`、`CAST`、`ON DUPLICATE KEY UPDATE`） |
| 可选 | Redis（多节点共享开关）、PageHelper（顺序助手）、Spring Web（请求语言快照） |

## 三、引入方式

本地安装（安装到本机 Maven 仓库后，同机其他工程可直接依赖）：

```bash
cd hmong-db-i18n && mvn install
```

```xml
<dependency>
    <groupId>com.hmong</groupId>
    <artifactId>hmong-db-i18n</artifactId>
    <version>1.0.0</version>
</dependency>
```

上线到私服后把 `mvn install` 换成 `mvn deploy` 即可；库本身是普通 jar（非可执行 jar），会被 Spring Boot 自动装配。

## 四、快速接入（最小可用）

**第 1 步 建表**：执行 `src/main/resources/db/hmong_db_i18n_schema.sql`（幂等，仅新增 `sys_data_translation`）。

**第 2 步 登记要翻译的字段**（一个纯数据 Bean，不要注入 Mapper/Service）：

```java
@Configuration
public class DemoI18nConfig {
    @Bean
    public DataTranslationRule demoNamesRule() {
        // 业务类型、源表、源表主键列、源语言、可翻译字段（字段标识,列名,译文最大长度）
        return new DataTranslationRule("demo_biz", "demo_table", "id", "zh-CN",
                List.of(new DataTranslationField("name", "name", 100)));
    }
}
```

**第 3 步 给展示查询加注解**（SQL 只写原文列）：

```java
@DataI18n                                                   // 只翻译投影里的登记字段
List<DemoVO> selectDemoList(DemoQuery query);

@DataI18n(keywordColumn = "d.name", keywordParameter = "keyword")
List<DemoOptionVO> selectDemoOptions(@Param("level") int level, @Param("keyword") String keyword);
```

```xml
<select id="selectDemoOptions" resultType="...DemoOptionVO">
    SELECT d.id AS id, d.name AS name
    FROM demo_table d
    WHERE d.del_flag = '0' AND d.level = #{level}
    <if test="keyword != null">
    AND d.name LIKE CONCAT('%', #{keyword}, '%')   <!-- 关键词必须写成 CONCAT(...)，列名与注解一致 -->
    </if>
    ORDER BY d.id
</select>
```

**第 4 步 录入译文**（实现维护适配器，供后台导出/导入用；只做展示可以不实现，但无法通过接口维护译文）：

```java
public class DemoTranslationSourceProvider implements DataTranslationSourceProvider {
    // rule()/countSource()/pageSource() 见“五、维护译文”
    // listSourceByIdsForUpdate(): 事务内加锁读原文，建议
    //   select id, name from demo_table where id in (...) order by id for update
}
```

**第 5 步 打开开关**（默认关闭）：

```java
// Redis 场景：在任意维护接口里调用一次
switchService.setEnabled(true);          // 或 Redis 直接 set 键 data_translation:enabled = true
```

之后带 `?lang=en` 或 `Accept-Language: en` 访问展示接口即可看到译文；不带语言或 `lang=zh-CN` 返回原文。

## 五、维护译文

`DataTranslationMaintenanceService` 提供：`selectItemPage` / `selectItemsForExport` / `saveBatch` / `saveInChunks` / `deleteById` / `logicDeleteBizTranslations` / `checkReadyToEnable`。

典型流程：导出清单 → 人工翻译 → 导入。

```java
// 导出（含尚未翻译的行，最多 10000 行）
DataTranslationQuery query = new DataTranslationQuery();
query.setBizType("demo_biz");
query.setLocale("en");
List<DataTranslationItemVO> rows = maintenanceService.selectItemsForExport(query, 10000);

// 回填译文后提交（expectedSourceHash 必须使用导出行里的值）
DataTranslationSaveItem item = new DataTranslationSaveItem();
item.setBizType(row.getBizType());
item.setBizId(row.getBizId());
item.setFieldCode(row.getFieldCode());
item.setLocale(row.getLocale());
item.setTranslatedText("Wheeled Tractor");
item.setExpectedSourceHash(row.getExpectedSourceHash());
maintenanceService.saveBatch(List.of(item));   // 整批一个事务
```

要点：

- 写入在事务内按主键排序锁定源记录，重新计算摘要；**摘要不一致 / 源记录不存在或已删 / 译文为空 / 超长 / 源语言，都会拒绝并整批回滚**；
- `saveInChunks(items, 200)` 用于 Excel 导入：每批一个事务，返回已提交批次与失败原因；
- 源记录删除时调用 `logicDeleteBizTranslations(bizType, fieldCode, bizIds, operator)` 联动失效译文（共享的字典译文不要删）；
- 操作人默认取 0，业务系统可提供一个 `DataTranslationOperatorProvider` Bean 从登录上下文取用户 ID。

维护适配器（`DataTranslationSourceProvider`）三个方法：

```java
public DataTranslationRule rule();                                   // 返回登记规则
public long countSource(String keyword);                             // 源记录总数
public List<DataTranslationSourceItem> pageSource(String keyword, int offset, int limit);
    // 建议 SQL 直接 limit #{offset}, #{limit}，不要依赖分页插件
public List<DataTranslationSourceItem> listSourceByIdsForUpdate(Collection<String> bizIds);
    // 事务内调用；WHERE id IN (...) ORDER BY id FOR UPDATE，不要 JOIN 其它表，避免锁范围过大
```

> 库只提供 Service，不提供 Controller：`R`/`AjaxResult`、权限注解各项目不同，按自己风格包一层即可（导出可用 EasyExcel 把 `DataTranslationItemVO` 直接写出）。

## 六、配置项

| 配置 | 默认 | 说明 |
|---|---|---|
| `hmong.db-i18n.enabled` | true | 关掉后本库不注册任何 Bean |
| `hmong.db-i18n.switch-key` | `data_translation:enabled` | 运行时开关的 Redis 键 |
| `hmong.db-i18n.store` | auto | `auto`=有 Redis 用 Redis，否则本地静态；`redis`=强制；`static`=强制本地 |
| `hmong.db-i18n.static-switch-enabled` | false | `store=static` 时的初值 |
| `hmong.db-i18n.web-enabled` | true | 是否注册请求快照拦截器（并注册默认 LocaleResolver） |
| `hmong.db-i18n.maintenance-enabled` | true | 是否启用译文维护服务 |
| `hmong.db-i18n.pagehelper-order` | false | 由本库声明 `PageInterceptor` 以固定“翻译先于分页”的顺序 |
| `hmong.db-i18n.verify-plugin-chain` | true | 启动时校验并打印插件链顺序 |

默认语言解析器（`AcceptLanguageLocaleResolver`）只在容器里没有 `LocaleResolver` 时注册；已有自己实现的不会覆盖。

### 与分页插件的顺序

翻译必须发生在分页生成 count/data **之前**。MyBatis 后注册的插件在外层先执行，因此需要
`MybatisPlusInterceptor` 晚于 PageHelper 注册：

- 用 PageHelper 且不自己管顺序：设置 `hmong.db-i18n.pagehelper-order=true`，不要给
  `MybatisPlusInterceptor` Bean 加 `@Order`（保持默认最低优先级）即可；
- 自己管控顺序：让 `MybatisPlusInterceptor` 的 `@Order` 大于 `PageInterceptor` 的 `@Order`（例如 100 / 200）。

启动日志会打印实际顺序：`数据字段国际化插件链顺序校验通过：PageInterceptor -> MybatisPlusInterceptor`；顺序不对会打印 ERROR。

## 七、语言规则

| 请求语言 | 查询顺序 |
|---|---|
| `en-US` | `en-US` → `en` → 原文 |
| `en` | `en` → 原文 |
| `zh-CN`（源语言） | 直接原文，跳过改写 |
| `zh-TW` | `zh-TW` → `zh` → 原文 |
| 其它未维护语种 | 候选未命中 → 原文 |

语言来源：`?lang=xx` > `Accept-Language` > 默认 `zh-CN`；`zh`/`zh-Hans`/`zh-Hant` 归一为简体。
源语言不维护译文；纯编码（型号、编号）不需要译文行，任何语言都显示原文。

## 八、支持范围与硬约束

**支持**：普通 SELECT、显式列投影（`别名.列名 AS 别名`）、普通 JOIN、`WHERE` 中的 `EXISTS(...)` 关联过滤、可选的关键词 `LIKE CONCAT('%', ?, '%')` 扩展。

**标记查询遇到以下情况会直接报错**（不会悄悄产出不完整结果）：

| 场景 | 说明 |
|---|---|
| `SELECT *` / `别名.*` | 请显式列出字段 |
| UNION / CTE / 非普通 SELECT | 暂不支持 |
| 参数映射数量与占位符不一致 | SQL 与绑定不匹配 |
| 注解声明了关键词列但找不到对应 LIKE 谓词 | 列名写错或忘了写 `CONCAT` |

**不处理但不会报错**：表达式/聚合投影、未用别名限定的列、未登记表的列、子查询内的投影——保持原样。

其它约定：
- 列名必须带表别名；同一张表多个别名时按别名分别翻译；
- 关键词只匹配“原文 + 当前语言最终显示值”，不会匹配其它语言的隐藏译文；
- 标记查询请用自动 count（PageHelper / MP 分页），不要手写 count SQL；动态 `orderBy` 不会映射到译文列；
- 展示与编辑要区分：**编辑表单取原文请显式带 `lang=zh-CN`**，否则“按名称新建字典”的写入逻辑会把译文当成新数据。

## 九、排查：切了语言不生效

1. **开关是否为 true**：`GET data_translation:enabled`；默认关闭，键不存在就是关闭；
2. **语言是否传给后端**：请求是否带 `?lang=en` 或 `Accept-Language: en`；只切前端 UI 语言不够；
3. **查询是否标记**、登记是否生效：启动日志 `数据字段国际化登记数量：N`；
4. **译文是否有效**：语言候选是否命中、`del_flag='0'`、译文非空、`source_hash` 是否等于当前原文摘要；
   ```sql
   SELECT s.name, t.source_hash, SHA2(CONVERT(s.name USING utf8mb4), 256) AS 当前摘要
   FROM demo_table s LEFT JOIN sys_data_translation t
     ON t.biz_type='demo_biz' AND t.biz_id=CAST(s.id AS CHAR) AND t.field_code='name' AND t.locale='en'
   WHERE s.id = ?;
   ```
5. **硬约束**：日志出现 `数据字段国际化改写被拒绝，statement=...` 说明踩到了 `SELECT *`、缺别名或关键词写法等问题；
6. 数据量大时用 `EXPLAIN` 确认翻译关联走了 `uk_biz_field_locale` 唯一索引。

## 十、目录结构

```
src/main/java/com/hmong/dbi18n
├── annotation/DataI18n.java                  标记注解
├── core/                                     规则、语言候选、请求快照、摘要工具、异常与文案
├── sql/DataTranslationSqlRewriter.java       AST 改写引擎
├── interceptor/                              改写插件、请求快照拦截器
├── locale/AcceptLanguageLocaleResolver.java  可选默认语言解析器
├── store/                                    开关存储（Redis / 本地静态）与开关服务
├── storage/                                  翻译表仓储、维护服务、DTO/VO、Provider SPI
└── autoconfigure/                            自动装配与配置属性
src/main/resources
├── db/hmong_db_i18n_schema.sql               建表脚本（幂等）
└── META-INF/spring/...AutoConfiguration.imports
```

测试：`mvn test`（26 个用例：语言与摘要规则、SQL 改写、语言解析器、自动装配）。
