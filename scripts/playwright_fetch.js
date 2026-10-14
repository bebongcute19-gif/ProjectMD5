const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const outDir = path.resolve(__dirname, '..', 'frontend', 'assets', 'products');
if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

const products = [
  { sku: 'SAM-FOLD6-512', page: 'https://www.samsung.com/vn/smartphones/galaxy-z-fold6/' },
  { sku: 'XIAOMI-14T-PRO-512', page: 'https://www.mi.com/global/product/xiaomi-14t-pro/' },
  { sku: 'GP9P-256', page: 'https://store.google.com/product/pixel_9_pro' },
  { sku: 'ASUS-TUF-A15-4060', page: 'https://www.asus.com/laptops/for-gaming/tuf-gaming/asus-tuf-gaming-a15-2023/' },
  { sku: 'LEN-L5-4070', page: 'https://www.lenovo.com/us/en/p/laptops/legion-laptops/legion-5-series/' },
  { sku: 'DELL-XPS-13', page: 'https://www.dell.com/en-us/shop/dell-laptops/xps-13-laptop/spd/xps-13-9315-laptop' },
  { sku: 'SONY-WF1000XM5', page: 'https://www.sony.com/electronics/wireless-headphones/wf-1000xm5' },
  { sku: 'JBL-CHARGE-5', page: 'https://www.jbl.com/charge-5.html' },
  { sku: 'MARSHALL-EMBER-2', page: 'https://marshallheadphones.com/products/emberton-ii' },
  { sku: 'BOSE-QC-ULTRA', page: 'https://www.bose.com/en_us/products/headphones/over_ear_headphones/quietcomfort-ultra-headphones.html' },
  { sku: 'LOGI-GPROX2', page: 'https://www.logitechg.com/en-us/products/gaming-mice/pro-x-superlight-2-wireless-mouse.910-006631.html' },
  { sku: 'ANKER-737', page: 'https://us.anker.com/products/a1289' }
];

const userAgent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36';

(async () => {
  const browser = await chromium.launch({ headless: true });
  const results = [];

  for (const p of products) {
    const context = await browser.newContext({ userAgent });
    const page = await context.newPage();
    console.log('Visiting', p.page);
    let resObj = { sku: p.sku, page: p.page, downloaded: false, candidates: [] };
    try {
      const resp = await page.goto(p.page, { waitUntil: 'domcontentloaded', timeout: 60000 });
      // wait a bit for JS galleries
      await page.waitForTimeout(2000);
      // scroll to bottom slowly to trigger lazy loads
      for (let i = 0; i < 6; i++) {
        await page.evaluate(() => window.scrollBy(0, window.innerHeight));
        await page.waitForTimeout(500);
      }

      // collect candidate image URLs from meta, link, img, data-src, picture
      const candidates = await page.evaluate(() => {
        const set = new Set();
        // og:image
        const og = document.querySelector('meta[property="og:image"], meta[name="og:image"]');
        if (og) set.add(og.getAttribute('content') || og.content);
        const link = document.querySelector('link[rel="image_src"]');
        if (link) set.add(link.href || link.getAttribute('href'));
        // images
        document.querySelectorAll('img').forEach(img => {
          if (img.currentSrc) set.add(img.currentSrc);
          if (img.src) set.add(img.src);
          if (img.getAttribute('data-src')) set.add(img.getAttribute('data-src'));
          if (img.getAttribute('data-lazy-src')) set.add(img.getAttribute('data-lazy-src'));
        });
        // picture > source
        document.querySelectorAll('picture source').forEach(s => { if (s.srcset) set.add(s.srcset.split(',')[0].split(' ')[0]); });
        return Array.from(set).filter(u => !!u);
      });

      // normalize and filter
      const filtered = candidates.map(u => (u||'').trim()).filter(u => u && (u.match(/\.(jpg|jpeg|png|webp|avif)(?:[?]|$)/i) || u.toLowerCase().includes('format') || u.toLowerCase().includes('image')));
      resObj.candidates = filtered;

      // avoid unsplash and previously failing unsplash-like urls
      const badPatterns = [/images\.unsplash\.com/i, /cdn\.shopify/i];
      const goodCandidates = filtered.filter(u => !badPatterns.some(r => r.test(u)));

      for (const u of goodCandidates) {
        try {
          // resolve relative URLs
          const url = new URL(u, page.url()).toString();
          console.log('Trying candidate', p.sku, url);
          // fetch via page to reuse browser UA
          const fetchResp = await page.request.get(url, { timeout: 30000 });
          if (!fetchResp.ok()) { console.log('Not ok', fetchResp.status()); continue; }
          const ct = fetchResp.headers()['content-type'] || '';
          if (!ct.startsWith('image')) { console.log('Not image ct', ct); continue; }
          const buf = await fetchResp.body();
          // determine extension
          let ext = 'jpg';
          if (ct.includes('png')) ext = 'png';
          else if (ct.includes('webp')) ext = 'webp';
          else if (ct.includes('avif')) ext = 'avif';
          else if (ct.includes('jpeg')) ext = 'jpg';
          else if (url.match(/\.([^\.\?]{2,6})(?:[\?|$])/)) {
            const m = url.match(/\.([^\.\?]{2,6})(?:[\?|$])/);
            ext = m[1];
          }
          const filename = path.join(outDir, p.sku + '.' + ext);
          fs.writeFileSync(filename, buf);
          // basic verification: file exists and non-zero
          const stats = fs.statSync(filename);
          if (stats.size > 100) {
            resObj.downloaded = true;
            resObj.path = filename;
            resObj.size = stats.size;
            resObj.url = url;
            break;
          } else {
            fs.unlinkSync(filename);
            continue;
          }
        } catch (err) {
          console.log('Candidate failed', err && err.message);
          continue;
        }
      }

    } catch (err) {
      console.log('Error visiting', p.page, err && err.message);
      resObj.error = err && err.message;
    }

    results.push(resObj);
    await page.close();
    await context.close();
  }

  await browser.close();
  // write results
  const outFile = path.join(outDir, 'playwright_results.json');
  fs.writeFileSync(outFile, JSON.stringify(results, null, 2), 'utf8');
  console.log('Wrote results to', outFile);
  console.log('Done');
})();