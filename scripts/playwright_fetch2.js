const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const outDir = path.resolve(__dirname, '..', 'frontend', 'assets', 'products');
if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

const products = [
  { sku: 'SAM-FOLD6-512', page: 'https://www.samsung.com/vn/smartphones/galaxy-z-fold6/', keywords: ['fold6','z-fold6','galaxy-z-fold6'] },
  { sku: 'XIAOMI-14T-PRO-512', page: 'https://www.mi.com/global/product/xiaomi-14t-pro/', keywords: ['14t','14t-pro','xiaomi-14t','14tpro'] },
  { sku: 'GP9P-256', page: 'https://store.google.com/product/pixel_9_pro', keywords: ['pixel-9','pixel9','pixel-9-pro','pixel9pro'] },
  { sku: 'ASUS-TUF-A15-4060', page: 'https://www.asus.com/laptops/for-gaming/tuf-gaming/asus-tuf-gaming-a15-2023/', keywords: ['tuf','a15','tuf-gaming','tuf-gaming-a15'] },
  { sku: 'LEN-L5-4070', page: 'https://www.lenovo.com/us/en/p/laptops/legion-laptops/legion-5-series/', keywords: ['legion','legion-5','lenovo-legion'] },
  { sku: 'DELL-XPS-13', page: 'https://www.dell.com/en-us/shop/dell-laptops/xps-13-laptop/spd/xps-13-9315-laptop', keywords: ['xps-13','xps13','xps'] },
  { sku: 'SONY-WF1000XM5', page: 'https://www.sony.com/electronics/wireless-headphones/wf-1000xm5', keywords: ['wf-1000xm5','wf1000xm5','1000xm5'] },
  { sku: 'JBL-CHARGE-5', page: 'https://www.jbl.com/charge-5.html', keywords: ['charge-5','charge5','jbl-charge'] },
  { sku: 'MARSHALL-EMBER-2', page: 'https://marshallheadphones.com/products/emberton-ii', keywords: ['emberton','emberton-ii','emberton-2'] },
  { sku: 'BOSE-QC-ULTRA', page: 'https://www.bose.com/en_us/products/headphones/over_ear_headphones/quietcomfort-ultra-headphones.html', keywords: ['quietcomfort','quietcomfort-ultra','qc-ultra'] },
  { sku: 'LOGI-GPROX2', page: 'https://www.logitechg.com/en-us/products/gaming-mice/pro-x-superlight-2-wireless-mouse.910-006631.html', keywords: ['pro-x-superlight','superlight-2','pro x superlight'] },
  { sku: 'ANKER-737', page: 'https://us.anker.com/products/a1289', keywords: ['anker','powercore','power bank','powercore-24k','737'] }
];

const userAgent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36';

function isLogoUrl(u){
  if(!u) return true;
  const lower = u.toLowerCase();
  return /logo|icon|badge|flag|thumbnail|placeholder|gov-logos|gov-logo|thumbs_|thumb|sprite|pixel|regionflags/.test(lower);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const results = [];

  for (const p of products) {
    const context = await browser.newContext({ userAgent });
    const page = await context.newPage();
    console.log('Visiting', p.page);
    let resObj = { sku: p.sku, page: p.page, downloaded: false, tried: [], candidates: [] };
    try {
      await page.goto(p.page, { waitUntil: 'domcontentloaded', timeout: 60000 }).catch(e=>{});
      await page.waitForTimeout(2000);
      for (let i = 0; i < 6; i++) { await page.evaluate(() => window.scrollBy(0, window.innerHeight)); await page.waitForTimeout(500); }

      const imgs = await page.evaluate(() => {
        const out = [];
        // collect meta og
        const og = document.querySelector('meta[property="og:image"], meta[name="og:image"]'); if(og && (og.content||og.getAttribute('content'))) out.push({src: og.content||og.getAttribute('content'), alt: 'meta-og'});
        const link = document.querySelector('link[rel="image_src"]'); if(link && (link.href||link.getAttribute('href'))) out.push({src: link.href||link.getAttribute('href'), alt:'link-image_src'});
        document.querySelectorAll('img').forEach(img => { const src = img.currentSrc || img.src || img.getAttribute('data-src') || img.getAttribute('data-lazy-src'); if(src) out.push({src, alt: img.alt || img.title || ''}) });
        document.querySelectorAll('picture source').forEach(s => { if(s.srcset) out.push({src: s.srcset.split(',')[0].split(' ')[0], alt: 'picture-source'}) });
        return out;
      });

      // dedupe
      const seen = new Set();
      const candidates = [];
      for(const im of imgs){ if(!im || !im.src) continue; let u = im.src.trim(); if(u.startsWith('//')) u = new URL(page.url()).protocol + u; if(!seen.has(u)){ seen.add(u); candidates.push({url:u, alt: (im.alt||'').toLowerCase()}); } }
      resObj.candidates = candidates.map(c=>c.url);

      // rank candidates: prefer url or alt containing keywords and not logo
      const kws = p.keywords.map(k=>k.toLowerCase());
      function scoreCandidate(c){ const u = c.url.toLowerCase(); const alt = (c.alt||'').toLowerCase(); let score = 0; for(const k of kws){ if(u.includes(k)) score+=5; if(alt.includes(k)) score+=4; } if(isLogoUrl(u) || isLogoUrl(alt)) score -= 10; // de-prioritize logos
        // prefer larger images by heuristics (width/height patterns)
        if(u.match(/(\d{3,}x\d{3,}|width=\d{3,}|height=\d{3,})/)) score+=1; return score; }

      const scored = candidates.map(c=>({c, s: scoreCandidate(c)})).sort((a,b)=>b.s - a.s);

      for(const item of scored){ const u = item.c.url; resObj.tried.push(u); if(isLogoUrl(u)) { console.log('Skipping logo-like', u); continue; }
        try {
          console.log('Trying', p.sku, u);
          const url = new URL(u, page.url()).toString();
          const r = await page.request.get(url, { timeout: 30000 });
          if(!r.ok()) { console.log('Not ok', r.status()); continue; }
          const ct = r.headers()['content-type']||''; if(!ct.startsWith('image')) { console.log('Not image ct', ct); continue; }
          const buf = await r.body(); if(!buf || buf.length < 2000) { console.log('Too small', buf && buf.length); continue; }
          let ext = 'jpg'; if(ct.includes('png')) ext='png'; else if(ct.includes('webp')) ext='webp'; else if(ct.includes('avif')) ext='avif'; else if(ct.includes('jpeg')) ext='jpg'; else if(url.match(/\.([a-zA-Z0-9]{2,6})(\?|$)/)) ext = url.match(/\.([a-zA-Z0-9]{2,6})(\?|$)/)[1];
          const filename = path.join(outDir, p.sku + '.' + ext);
          fs.writeFileSync(filename, buf);
          const stats = fs.statSync(filename);
          if(stats.size > 5000){ resObj.downloaded = true; resObj.path = filename; resObj.size = stats.size; resObj.url = url; break; } else { fs.unlinkSync(filename); continue; }
        } catch (err){ console.log('Fetch failed', err && err.message); continue; }
      }

    } catch (err) { console.log('Error visiting', p.page, err && err.message); resObj.error = err && err.message; }
    results.push(resObj);
    await page.close(); await context.close();
  }

  await browser.close();
  const outFile = path.join(outDir, 'playwright_results2.json');
  fs.writeFileSync(outFile, JSON.stringify(results, null, 2), 'utf8');
  console.log('Wrote results to', outFile);
})();