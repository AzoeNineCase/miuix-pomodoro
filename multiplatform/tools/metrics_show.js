/** 打印 web_metrics.js 导出的几何数据（可带过滤子串）。
 * 用法: node tools/metrics_show.js <metrics.json> [filterSubstring]
 */
const fs = require('fs');

const file = process.argv[2];
const filter = process.argv[3] || '';
const data = JSON.parse(fs.readFileSync(file, 'utf8'));
if (data.ink) {
  console.log('-- ink --');
  for (const [k, v] of Object.entries(data.ink)) {
    if (v) console.log(`${k.padEnd(10)} x=${v.tx} y=${v.ty} w=${v.tw} h=${v.th}`);
  }
}
console.log('-- bands --');
for (const b of data.bands) {
  if (filter && !b.sel.includes(filter)) continue;
  const t = b.text ? ` | text x=${b.text.tx} y=${b.text.ty} w=${b.text.tw} h=${b.text.th}` : '';
  console.log(
    `${b.sel}[${b.i}]`.padEnd(26) +
    `x=${String(b.x).padStart(8)} y=${String(b.y).padStart(8)} w=${String(b.w).padStart(7)} h=${String(b.h).padStart(6)}` +
    ` fs=${b.fs} fw=${b.fw} lh=${b.lh} ls=${b.ls} gap=${b.gap} pad=${b.pad} rad=${b.rad}` + t,
  );
}
