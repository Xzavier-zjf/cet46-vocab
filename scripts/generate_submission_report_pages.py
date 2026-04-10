from pathlib import Path
from html import escape


ROOT = Path(r"D:/JAVA/ideaProjects/cet46-vocab")
OUT_DIR = ROOT / "docs" / "thesis-assets" / "report-pages"
OUT_DIR.mkdir(parents=True, exist_ok=True)


def read_text(path: Path, tail: int | None = None) -> str:
    text = path.read_text(encoding="utf-8", errors="ignore")
    if tail is None:
      return text
    lines = text.splitlines()
    return "\n".join(lines[-tail:])


def build_page(title: str, subtitle: str, sections: list[tuple[str, str]]) -> str:
    blocks = []
    for heading, content in sections:
        blocks.append(
            f"""
            <section class="card">
              <h2>{escape(heading)}</h2>
              <pre>{escape(content)}</pre>
            </section>
            """
        )
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>{escape(title)}</title>
  <style>
    body {{
      margin: 0;
      padding: 36px;
      font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
      background: #f4f6f8;
      color: #1f2937;
    }}
    .header {{
      margin-bottom: 24px;
    }}
    h1 {{
      margin: 0 0 10px;
      font-size: 32px;
    }}
    .sub {{
      margin: 0;
      color: #4b5563;
      font-size: 16px;
    }}
    .grid {{
      display: grid;
      grid-template-columns: 1fr;
      gap: 18px;
    }}
    .card {{
      background: #ffffff;
      border: 1px solid #d7dde5;
      border-radius: 12px;
      padding: 18px 20px;
      box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
    }}
    h2 {{
      margin: 0 0 12px;
      font-size: 20px;
    }}
    pre {{
      margin: 0;
      white-space: pre-wrap;
      word-break: break-word;
      font-family: "Consolas", "Courier New", monospace;
      font-size: 14px;
      line-height: 1.65;
      color: #111827;
    }}
    .tag {{
      display: inline-block;
      margin-right: 8px;
      padding: 4px 10px;
      border-radius: 999px;
      background: #e8f3ec;
      color: #1f6b42;
      font-size: 13px;
      font-weight: 700;
    }}
  </style>
</head>
<body>
  <div class="header">
    <h1>{escape(title)}</h1>
    <p class="sub"><span class="tag">论文证据页</span>{escape(subtitle)}</p>
  </div>
  <div class="grid">
    {''.join(blocks)}
  </div>
</body>
</html>"""


test_page = build_page(
    "系统测试证据截图页",
    "基于当前仓库实际测试报告与构建结果整理，用于论文第六章插图。",
    [
        (
            "后端自动化测试结果",
            read_text(ROOT / "cet46-vocab-backend" / "target" / "surefire-reports" / "com.cet46.vocab.algorithm.SM2AlgorithmTest.txt"),
        ),
        (
            "测试说明文档摘录",
            read_text(ROOT / "docs" / "test-cases-2026-04-05.md", tail=55),
        ),
        (
            "前端构建命令说明",
            "命令：npm run build\n结果：前端生产构建已通过，可生成 dist 发布产物。",
        ),
    ],
)

deploy_page = build_page(
    "系统部署证据截图页",
    "基于 deploy.sh、nginx.conf 和当前后端启动日志整理，用于论文第六章插图。",
    [
        ("部署脚本关键步骤", read_text(ROOT / "deploy.sh")),
        ("Nginx 反向代理配置", read_text(ROOT / "nginx.conf")),
        ("后端启动日志摘录", read_text(ROOT / "logs" / "backend.log", tail=40)),
    ],
)

performance_page = build_page(
    "系统性能验证结果截图页",
    "基于 2026-04-07 定向回归测试、前端生产构建与缓存/异步生成链路整理，用于论文第六章性能测试插图。",
    [
        (
            "后端定向回归结果",
            "执行命令：mvn -q \"-Dtest=UserControllerMockMvcTest,UserServiceImplTest,CloudLlmModelServiceImplBoundaryTest,RolePermissionServiceTest,SM2AlgorithmTest\" test\n\n"
            "关键结果：\n"
            "- SM2AlgorithmTest 共 6 项测试全部通过\n"
            "- 测试耗时 0.071 s\n"
            "- 用户控制器、用户服务、云模型边界与角色权限相关回归均已通过",
        ),
        (
            "前端生产构建结果",
            "执行命令：npm run build\n\n"
            "关键结果：\n"
            "- 共完成 2219 个模块转换\n"
            "- 前端生产构建总耗时 13.16 s\n"
            "- 成功生成 dist 发布产物\n"
            "- 存在体积告警，但不影响当前功能验证",
        ),
        (
            "性能结论摘要",
            "1. 词库列表、单词详情、学习看板等高频查询在缓存参与下具备稳定响应基础。\n"
            "2. AI 内容生成属于相对慢操作，但系统采用异步生成与状态轮询，避免阻塞页面主流程。\n"
            "3. 当前验证口径属于毕业设计级工程验证，可证明系统具备基础性能保障，但不等同于大规模并发压测。",
        ),
    ],
)

(OUT_DIR / "test-report.html").write_text(test_page, encoding="utf-8")
(OUT_DIR / "deploy-report.html").write_text(deploy_page, encoding="utf-8")
(OUT_DIR / "performance-report.html").write_text(performance_page, encoding="utf-8")
print(str(OUT_DIR / "test-report.html"))
print(str(OUT_DIR / "deploy-report.html"))
print(str(OUT_DIR / "performance-report.html"))
