const path = require('path');
const { chromium } = require('playwright');

process.env.NO_PROXY = 'localhost,127.0.0.1,::1';
process.env.no_proxy = 'localhost,127.0.0.1,::1';
process.env.HTTP_PROXY = '';
process.env.HTTPS_PROXY = '';

const ROOT = 'D:/JAVA/ideaProjects/cet46-vocab';
const REPORT_DIR = path.join(ROOT, 'docs', 'thesis-assets', 'report-pages');
const SCREENSHOT_DIR = path.join(ROOT, 'docs', 'thesis-assets', 'screenshots');

async function capture(page, htmlName, pngName) {
  const fileUrl = `file:///${path.join(REPORT_DIR, htmlName).replace(/\\/g, '/')}`;
  await page.goto(fileUrl);
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, pngName),
    fullPage: true
  });
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1400 }, deviceScaleFactor: 1.5 });
  try {
    await capture(page, 'test-report.html', 'system-test-report.png');
    await capture(page, 'deploy-report.html', 'system-deploy-report.png');
    await capture(page, 'performance-report.html', 'system-performance-report.png');
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
