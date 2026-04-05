# CET46 Vocabulary 测试用例与执行记录（2026-04-05）

## 1. 测试范围
本轮覆盖以下变更：
- 云端模型新增时的连通自检（测速）能力。
- 云端模型在“我的资料”中公有/私有显示区分，修复 `glm-5` 私有模型被误显示为公有的问题。
- 管理员与用户新增私有云端模型时，协议从手输改为可选列表（并支持补全协议）。
- 登录页、注册页深色/浅色主题切换与趣味动画联动。

## 2. 测试环境
- 日期：2026-04-05
- 代码目录：`D:\JAVA\ideaProjects\cet46-vocab`
- 后端：Spring Boot + Maven
- 前端：Vue3 + Vite + Element Plus

## 3. 功能测试用例（手工）

### TC-LLM-001 用户新增私有云端模型并进行连通自检
- 前置条件：普通用户已登录；进入“我的资料-云端模型配置”。
- 步骤：
1. 点击“新增私有云端模型”。
2. 填写 `provider/modelKey/baseUrl/path/protocol/apiKey`。
3. 点击“连通自检/测速”按钮。
4. 查看自检结果提示与耗时。
5. 保存模型。
- 预期结果：
1. 自检请求成功返回健康状态与耗时信息。
2. 不可用时给出失败提示，不会误导为可用。
3. 保存成功后模型出现在私有模型列表。

### TC-LLM-002 管理员新增云端模型并进行连通自检
- 前置条件：管理员已登录；进入“我的资料-云端模型配置（管理员模式）”。
- 步骤：
1. 点击“新增云端模型”。
2. 填写配置。
3. 点击“连通自检/测速”。
4. 保存。
- 预期结果：
1. 自检流程可执行且返回明确结果。
2. 保存成功，模型可在云端模型列表中查询。

### TC-LLM-003 公私有模型显示区分（含 `glm-5`）
- 前置条件：存在同名模型（如 `glm-5`）且一条为私有、一条为公有。
- 步骤：
1. 进入“我的资料”中的云端模型列表。
2. 观察列表模型名称前缀标签。
- 预期结果：
1. 私有模型显示 `[私有]`。
2. 公有模型显示 `[公有]`。
3. 对 `user-private / user_private / private` 都能正确识别为私有。

### TC-LLM-004 协议列表可选 + 可扩展输入
- 前置条件：进入新增私有云端模型弹窗。
- 步骤：
1. 打开“协议”下拉。
2. 验证包含：`openai-compatible/azure-openai/anthropic/google-gemini/cohere/xai/ollama`。
3. 输入一个列表外新协议值并保存。
- 预期结果：
1. 默认值为 `openai-compatible`。
2. 可从下拉选择，也可手动补全输入（allow-create）。
3. 保存后协议字段按输入值落库/回显。

### TC-UI-001 登录页主题切换与动画联动
- 前置条件：进入登录页。
- 步骤：
1. 点击右上角主题切换按钮。
2. 观察背景渐变、气泡、网格波纹、卡片、文案颜色变化。
- 预期结果：
1. 不仅输入框变色，整页视觉（含趣味动画）同步切换。
2. 深浅主题切换后按钮图标与 aria 文案同步变化。

### TC-UI-002 注册页主题切换与动画联动
- 前置条件：进入注册页。
- 步骤：
1. 点击右上角主题切换按钮。
2. 观察背景渐变、气泡、网格波纹、卡片、文案颜色变化。
- 预期结果：
1. 注册页风格与登录页一致。
2. 深浅主题切换后动画与页面主题协调。

## 4. 自动化测试清单

### 后端（单元/集成）
- 命令：
```bash
mvn -q "-Dtest=UserControllerMockMvcTest,UserServiceImplTest,CloudLlmModelServiceImplBoundaryTest,RolePermissionServiceTest" test
```
- 目标：覆盖用户云模型接口、服务逻辑、边界校验与权限相关回归。

### 前端（构建校验）
- 命令：
```bash
npm run build
```
- 目标：校验登录/注册页主题动画改造后编译产物正常。

## 5. 本次执行记录
- 状态定义：`PASS/FAIL/BLOCKED`
- 执行时间：2026-04-05
- 说明：当前 Codex 执行环境对本机 HTTP 调用返回 `502 Bad Gateway`，因此未能完成浏览器/API 直连型手工验证；改以后端 MockMvc/Service 自动化回归 + 前端构建校验完成本轮验证。

| 编号 | 类型 | 执行方式 | 结果 | 备注 |
|---|---|---|---|---|
| AUTO-BE-001 | 后端自动化 | Maven 测试 | PASS | `UserControllerMockMvcTest, UserServiceImplTest, CloudLlmModelServiceImplBoundaryTest, RolePermissionServiceTest` 全部通过 |
| AUTO-FE-001 | 前端自动化 | Vite 构建 | PASS | 构建完成；存在既有 chunk size warning，不影响功能 |
| FUNC-AUTO-LLM | 功能回归 | 基于 Controller/Service 自动化用例 | PASS | 覆盖云模型查询/新增/更新、公私有识别与相关边界校验 |
| MANUAL-LLM-001~004 | 功能测试 | 手工步骤 | READY | 用例已编写，可在联调环境逐条勾测 |
| MANUAL-UI-001~002 | 功能测试 | 手工步骤 | READY | 用例已编写，可在浏览器逐条勾测 |
