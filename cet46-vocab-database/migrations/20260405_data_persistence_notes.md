# 数据入库改造说明（2026-04-05）

## 1. 本次改造目标
为避免关键业务数据仅存在前端本地或 Redis，补齐数据库持久化，支持：
- 会话历史可恢复、可审计（Assistant）
- 测验记录可追溯（Quiz）
- 云端模型调用统计可复盘（LLM Usage）
- 每日计划完成率可复盘（Daily Plan Cache）

## 2. 新增/变更的迁移脚本
- `20260405_add_quiz_and_usage_persistence.sql`
  - `quiz_session_record`：测验历史持久化
  - `llm_usage_daily`：云端模型调用日聚合持久化
- `20260405_add_assistant_persistence.sql`
  - `assistant_chat_session`：助手会话元数据
  - `assistant_chat_message`：助手消息明细
- `20260405_add_daily_plan_cache.sql`
  - `daily_plan_cache`：每日计划与完成率缓存

## 3. 各表口径说明

### 3.1 quiz_session_record
用途：保存测验提交结果，支持历史记录与详情回看。
核心字段：
- `quiz_id`：一次测验会话ID
- `user_id`
- `word_type`：`cet4/cet6/mixed`
- `quiz_type`：`choice/fill`
- `total/correct/wrong_count`
- `details_json`：逐题明细快照
- `created_at`

口径：以用户点击提交时的结果为准，写入一次不可逆（除非手工清理）。

### 3.2 llm_usage_daily
用途：按“用户+模型+日期”统计云端模型调用次数，用于“今日/7天/30天/趋势图”。
核心字段：
- `date_key`：`yyyyMMdd`
- `user_id`
- `scope`：`public/private`
- `provider/model_key`
- `source/runtime_source`
- `calls`
- `last_used_at`

口径：每次云端模型调用成功记录一次；按日 upsert 聚合。

### 3.3 assistant_chat_session / assistant_chat_message
用途：将 Assistant 会话从前端 localStorage 迁移至后端持久化。

`assistant_chat_session` 核心字段：
- `user_id`
- `client_session_id`：前端会话ID（迁移兼容）
- `title/updated_at/has_interaction/pinned`
- `group_id/group_name`
- `context_json`

`assistant_chat_message` 核心字段：
- `session_id`
- `client_message_id`：前端消息ID（迁移兼容）
- `role/content`
- `feedback`
- `auto_continued/continuation_rounds`
- `sort_order`

口径：
- 前端状态变更后自动同步后端快照
- 首次进入若后端无数据，自动将本地历史无感迁移上行
- 删除会话时级联删除其消息

### 3.4 daily_plan_cache
用途：缓存每日计划及完成率，支持“复盘计划完成率”。
核心字段：
- `user_id + plan_date`（唯一）
- `due_count`：当日计划待复习总量（默认以 00:00 快照为主）
- `reviewed_count`：当日已复习数
- `daily_target`：当日目标
- `completion_rate`：`reviewed_count / due_count * 100`，保留2位小数，封顶100
- `generated_at/last_synced_at`

口径：
- 每日 00:00 定时任务生成/更新当日计划快照
- 每次复习提交后实时回填 `reviewed_count` 与 `completion_rate`
- Dashboard 优先读此表；缺失时回源计算并补写

## 4. 代码侧对应关系（摘要）
- Assistant 持久化
  - `GET /assistant/state`
  - `PUT /assistant/state`
- Quiz 持久化
  - `POST /quiz/submit` 写 `quiz_session_record`
  - `GET /quiz/history` / `GET /quiz/history/{id}` 读历史
- LLM 用量
  - 云端调用链记录 `llm_usage_daily`
  - 用量查询优先 DB，Redis 兜底
- Daily Plan
  - `DailyPlanScheduler` 每日生成缓存
  - `ReviewServiceImpl.submitReview` 实时回填
  - `DashboardController.overview` 输出计划完成率字段

## 5. 上线步骤
1. 执行上述 3 个 SQL 迁移脚本。
2. 重启后端服务。
3. 验证：
   - Assistant 会话可跨端/重登保留
   - Quiz 历史可查看
   - 模型用量“今日/7天/30天/趋势”随调用增长
   - Dashboard 返回 `dailyPlanTotal/dailyPlanReviewed/dailyPlanCompletionRate`

## 6. 注意事项
- 本次为“新增表 + 新增字段口径”，不破坏原有业务表。
- 若需要回滚，优先停用新接口/查询逻辑，不建议直接删表（避免数据丢失）。
- `daily_plan_cache.due_count` 是“计划快照口径”，并非实时剩余量。
