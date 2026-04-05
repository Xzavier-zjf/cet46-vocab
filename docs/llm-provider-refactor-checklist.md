# LLM 多供应商与凭证分层改造清单

## 1. 接口字段清单

### 1.1 管理员全局模型接口 `/admin/llm/cloud-models`

- `POST /admin/llm/cloud-models` 请求字段
  - `provider: string`（必填，支持内置与 `custom`）
  - `modelKey: string`（必填，模型ID）
  - `displayName: string`（可选）
  - `enabled: boolean`（可选，默认 true）
  - `isDefault: boolean`（可选）
  - `baseUrl: string`（可选，未填时回退全局配置）
  - `path: string`（可选，默认 `/chat/completions`）
  - `protocol: string`（可选，默认 `openai-compatible`）
  - `apiKey: string`（可选，仅写入）
  - `clearApiKey: boolean`（可选，true 时清空）
- `PUT /admin/llm/cloud-models/{id}` 请求字段与 `POST` 一致
- `GET /admin/llm/cloud-models` 返回字段新增
  - `baseUrl: string`
  - `path: string`
  - `protocol: string`
  - `hasApiKey: boolean`
  - `apiKeyMask: string`

### 1.2 用户私有模型接口 `/user/llm/cloud-models/private`

- `POST /user/llm/cloud-models/private` 请求字段
  - `provider: string`（可选，默认 `bailian`）
  - `modelKey: string`（必填）
  - `displayName: string`（可选）
  - `enabled: boolean`（可选，默认 true）
  - `baseUrl: string`（可选）
  - `path: string`（可选）
  - `protocol: string`（可选）
  - `apiKey: string`（可选，仅写入）
  - `clearApiKey: boolean`（可选）
- `PUT /user/llm/cloud-models/private/{id}` 与 `/full` 支持同字段
- `GET /user/llm/cloud-models/private` 返回字段新增
  - `baseUrl: string`
  - `path: string`
  - `protocol: string`
  - `hasApiKey: boolean`
  - `apiKeyMask: string`

### 1.3 云模型列表与健康检查接口

- `GET /user/llm/cloud-models` 返回字段新增
  - `runtimeSource: string`（`USER_PRIVATE` / `GLOBAL_MODEL` / `SYSTEM_CONFIG`）
  - `selectedProvider: string`
  - `selectedBaseUrl: string`
- `GET /user/llm/cloud-health` 返回字段增强
  - `currentProvider` 使用“实际生效供应商”
  - `baseUrl` 使用“实际生效地址”

## 2. 数据库 SQL 清单（增量迁移）

### 2.1 `cloud_llm_model` 新增字段

```sql
ALTER TABLE cloud_llm_model ADD COLUMN base_url VARCHAR(255) NULL AFTER model_key;
ALTER TABLE cloud_llm_model ADD COLUMN path VARCHAR(128) NULL AFTER base_url;
ALTER TABLE cloud_llm_model ADD COLUMN protocol VARCHAR(32) NOT NULL DEFAULT 'openai-compatible' AFTER path;
ALTER TABLE cloud_llm_model ADD COLUMN api_key_ciphertext TEXT NULL AFTER protocol;
ALTER TABLE cloud_llm_model ADD COLUMN api_key_mask VARCHAR(32) NULL AFTER api_key_ciphertext;
ALTER TABLE cloud_llm_model ADD COLUMN extra_headers_json TEXT NULL AFTER api_key_mask;
```

### 2.2 数据回填

```sql
UPDATE cloud_llm_model
SET protocol = 'openai-compatible'
WHERE protocol IS NULL OR protocol = '';
```

### 2.3 兼容性约束

- 保持唯一索引：`uk_cloud_llm_model_visibility_owner_model (visibility, owner_user_id, model_key)`
- 不新增破坏性非空列（避免老数据迁移失败）

## 3. 权限矩阵（RBAC）

- 现有权限保留：
  - `PRIVATE_CLOUD_MODEL_CREATE`
  - `PRIVATE_CLOUD_MODEL_EDIT`
  - `PRIVATE_CLOUD_MODEL_DELETE`
  - `PRIVATE_CLOUD_MODEL_TOGGLE`
- 新增权限（用于细分凭证管理）：
  - `GLOBAL_CLOUD_MODEL_MANAGE`
  - `GLOBAL_CLOUD_CREDENTIAL_MANAGE`
  - `PRIVATE_CLOUD_CREDENTIAL_MANAGE`

## 4. 配置优先级与回退规则

- 凭证与端点优先级
  - 用户私有模型配置（同 modelKey，启用且配置完整）
  - 管理员全局模型配置（同 modelKey，启用且配置完整）
  - 系统配置 `llm.cloud.*`
- 失败回退
  - `assistant/chat`：云失败可回退本地
  - `word/llm/*`：默认不自动回退，返回明确错误

## 5. 测试用例清单（新增/修改）

### 5.1 Service 测试

- `CloudLlmModelServiceImplBoundaryTest`
  - `createPrivateShouldSaveApiKeyMaskWhenApiKeyProvided`
  - `updatePrivateShouldKeepApiKeyWhenApiKeyNotProvided`
  - `updatePrivateShouldClearApiKeyWhenClearApiKeyTrue`
  - `createShouldRejectUnsupportedProvider`
- `CloudLlmRuntimeConfigResolverTest`（新增）
  - `resolveShouldPreferPrivateModelCredentialsOverGlobalAndSystem`
  - `resolveShouldFallbackToGlobalModelWhenPrivateMissingCredentials`
  - `resolveShouldFallbackToSystemConfigWhenModelConfigIncomplete`
  - `resolveShouldThrowWhenNoAvailableCloudRuntime`

### 5.2 Controller 测试

- `UserControllerMockMvcTest`
  - `createPrivateCloudModel_shouldAcceptCredentialFields`
  - `updatePrivateCloudModelFull_shouldSupportClearApiKey`
- `AdminControllerMockMvcTest`（新增）
  - `createCloudModel_shouldAcceptCredentialFields`
  - `listCloudModels_shouldNotExposeApiKeyPlaintext`

### 5.3 集成/回归

- `UserServiceImplTest`
  - `checkCloudLlmHealth_shouldUseResolvedRuntimeConfig`
- 命令
  - `mvn -q test`
  - `npm run build`

## 6. 代码审查检查点

- 不返回任何明文 API Key。
- 新字段默认值与旧数据兼容。
- 云模型调用链统一走 `CloudLlmRuntimeConfigResolver`，避免分叉逻辑。
- `assistant` 与 `word` 的现有功能行为不回归。
