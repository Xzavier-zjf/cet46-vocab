# CET46 Vocab

一个面向大学英语四/六级（CET-4/CET-6）的单词学习系统，包含前后端分离架构与本地/云端双模型能力。

## 项目功能

- 用户注册、登录与个人偏好设置
- 单词学习、详情查看、复习与测验
- AI 辅助内容生成（例句、近义词、词根联想、智能释义）
- 学习助手对话（支持本地模型与云端 API）
- 管理端词库与用户管理

## 技术栈

- 前端：Vue 3 + Vite + Element Plus + Pinia + Axios
- 后端：Spring Boot 3.3 + MyBatis-Plus + MySQL + Redis + JWT
- 模型接入：本地模型 Ollama（`llm.ollama`），云端模型 OpenAI 兼容协议（当前示例为 DashScope，`llm.cloud`）

## 目录结构

```text
cet46-vocab/
├─ cet46-vocab-frontend/   # Vue 前端
├─ cet46-vocab-backend/    # Spring Boot 后端
├─ cet46-vocab-database/     # 数据库 SQL
├─ nginx.conf              # Nginx 反向代理示例
└─ deploy.sh               # Linux 服务器部署脚本
```

## 环境要求

- Node.js 18+
- JDK 17
- Maven 3.8+
- MySQL 8.x
- Redis 6.x/7.x
- 可选：Ollama（本地模型）

## 快速开始

### 1. 初始化数据库

1. 创建数据库：`cet46_vocab`
2. 导入 SQL：`cet46-vocab-database/cet46_vocab.sql`

### 2. 配置后端

编辑文件：`cet46-vocab-backend/src/main/resources/application.yml`

主要关注以下配置：

- MySQL：`spring.datasource.*`
- Redis：`spring.data.redis.*`
- 本地模型：`llm.ollama.base-url`、`llm.ollama.model`
- 云端模型：`llm.cloud.enabled`、`llm.cloud.base-url`、`llm.cloud.path`、`llm.cloud.model`、`llm.cloud.api-key`

### 3. 启动后端

```bash
cd cet46-vocab-backend
mvn spring-boot:run
```

默认地址：

- 后端服务：`http://localhost:8080/api`
- 接口文档：`http://localhost:8080/api/doc.html`

### 4. 启动前端

```bash
cd cet46-vocab-frontend
npm install
npm run dev
```

默认地址：

- 前端页面：`http://localhost:5173`

## 构建与部署

### 前端构建

```bash
cd cet46-vocab-frontend
npm run build
```

### 后端打包

```bash
cd cet46-vocab-backend
mvn clean package -DskipTests
```

### 服务器部署（可选）

- 使用仓库根目录的 `deploy.sh` 进行一键部署
- 使用仓库根目录的 `nginx.conf` 作为反向代理参考
- 部署前请将脚本与 Nginx 配置中的路径替换为你的真实路径

## 提交到仓库前建议

- 将配置文件（如 `application.yml` / `application-*.yml`）中的敏感信息（数据库密码、Redis 密码、云端 API Key）替换为占位符
- 增加本地私有配置文件（如 `application-local.yml`），避免提交真实凭据
- 检查 `git status`，确认不提交日志文件与本地运行产物

## License

本项目仅用于学习与演示，使用 MIT License。
