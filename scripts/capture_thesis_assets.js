const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const ROOT = 'D:/JAVA/ideaProjects/cet46-vocab';
const DIAGRAM_DIR = path.join(ROOT, 'docs', 'thesis-assets', 'diagrams');
const SCREENSHOT_DIR = path.join(ROOT, 'docs', 'thesis-assets', 'screenshots');
const FRONTEND_URL = 'http://localhost:5173';
const username = `paper_${Date.now()}`;
const password = 'paper123';

process.env.NO_PROXY = 'localhost,127.0.0.1,::1';
process.env.no_proxy = 'localhost,127.0.0.1,::1';
process.env.HTTP_PROXY = '';
process.env.HTTPS_PROXY = '';
process.env.http_proxy = '';
process.env.https_proxy = '';

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

async function waitForStable(page, ms = 1000) {
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(ms);
}

async function captureDiagrams(browser) {
  const diagramNames = ['system-architecture', 'functional-modules', 'er-diagram'];
  for (const name of diagramNames) {
    const page = await browser.newPage({ viewport: { width: 1800, height: 1400 }, deviceScaleFactor: 2 });
    const fileUrl = `file:///${path.join(DIAGRAM_DIR, `${name}.svg`).replace(/\\/g, '/')}`;
    await page.goto(fileUrl);
    const svg = page.locator('svg');
    await svg.waitFor({ timeout: 10000 });
    await svg.screenshot({
      path: path.join(DIAGRAM_DIR, `${name}.png`),
      timeout: 120000
    });
    await page.close();
  }
}

async function registerAndLogin(page) {
  await page.goto(`${FRONTEND_URL}/register`);
  await page.locator('input[placeholder="4-20位用户名"]').fill(username);
  await page.locator('input[placeholder="6-20位密码"]').fill(password);
  await page.locator('input[placeholder="请再次输入密码"]').fill(password);
  await page.locator('input[placeholder="输入昵称（可选）"]').fill('论文截图用户');
  await page.getByRole('button', { name: '注册并登录' }).click();
  await page.waitForURL(/\/onboarding/, { timeout: 20000 });
}

async function completeOnboarding(page) {
  const optionTexts = ['讲一个容易记住的小故事', '生活化场景联想', '温和叙事'];
  for (const text of optionTexts) {
    await page.getByRole('button', { name: text }).click();
  }
  await page.getByRole('button', { name: '本地模型 (Ollama)' }).click();
  await waitForStable(page, 500);
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, 'system-onboarding.png'),
    fullPage: true
  });
  await page.getByRole('button', { name: '完成并进入首页' }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 20000 });
}

async function captureDashboard(page) {
  await waitForStable(page, 1500);
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, 'system-dashboard.png'),
    fullPage: true
  });
}

async function captureWordList(page) {
  await page.goto(`${FRONTEND_URL}/words`);
  await waitForStable(page, 1500);
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, 'system-word-list.png'),
    fullPage: true
  });
}

async function captureWordDetail(page) {
  await page.goto(`${FRONTEND_URL}/words/cet4/1`);
  await waitForStable(page, 2500);
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, 'system-word-detail.png'),
    fullPage: true
  });
}

async function captureQuiz(page) {
  await page.goto(`${FRONTEND_URL}/quiz`);
  await waitForStable(page, 1200);
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, 'system-quiz.png'),
    fullPage: true
  });
}

async function captureAssistant(page) {
  await page.goto(`${FRONTEND_URL}/assistant`);
  await waitForStable(page, 1200);
  await page.locator('textarea').fill('请给我一些四六级单词记忆建议。');
  await page.getByRole('button', { name: '发送' }).click();
  await page.waitForTimeout(3500);
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, 'system-assistant.png'),
    fullPage: true
  });
}

async function main() {
  ensureDir(DIAGRAM_DIR);
  ensureDir(SCREENSHOT_DIR);

  const browser = await chromium.launch({ headless: true });
  try {
    await captureDiagrams(browser);

    const page = await browser.newPage({ viewport: { width: 1600, height: 1200 }, deviceScaleFactor: 1.5 });
    page.setDefaultTimeout(20000);
    await registerAndLogin(page);
    await completeOnboarding(page);
    await captureDashboard(page);
    await captureWordList(page);
    await captureWordDetail(page);
    await captureQuiz(page);
    await captureAssistant(page);
    await page.close();
  } finally {
    await browser.close();
  }

  console.log(JSON.stringify({
    username,
    password,
    diagrams: [
      path.join(DIAGRAM_DIR, 'system-architecture.png'),
      path.join(DIAGRAM_DIR, 'functional-modules.png'),
      path.join(DIAGRAM_DIR, 'er-diagram.png')
    ],
    screenshots: [
      path.join(SCREENSHOT_DIR, 'system-onboarding.png'),
      path.join(SCREENSHOT_DIR, 'system-dashboard.png'),
      path.join(SCREENSHOT_DIR, 'system-word-list.png'),
      path.join(SCREENSHOT_DIR, 'system-word-detail.png'),
      path.join(SCREENSHOT_DIR, 'system-quiz.png'),
      path.join(SCREENSHOT_DIR, 'system-assistant.png')
    ]
  }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
