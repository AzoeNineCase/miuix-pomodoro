// 运行 Gradle 并抓取完整（不截断）的编译错误
const cp = require('child_process');
const fs = require('fs');

const task = process.argv[2] || ':composeApp:compileKotlinDesktop';
const outFile = process.env.TEMP + '/gradle-out.txt';

const env = { ...process.env, JAVA_HOME: 'C:\\Program Files\\Microsoft\\jdk-21.0.12.101-hotspot' };
let out = '';
try {
  out = String(cp.execSync(
    `"e:\\ke\\cook\\multiplatform\\gradlew.bat" ${task} --console=plain`,
    { cwd: 'e:\\ke\\cook\\multiplatform', env, maxBuffer: 64 * 1024 * 1024 },
  ));
} catch (e) {
  out = String(e.stdout || '') + '\n' + String(e.stderr || '');
  console.log('EXIT CODE:', e.status);
}
fs.writeFileSync(outFile, out, 'utf8');

const errors = out.split(/\r?\n/).filter((l) => /^e: /.test(l.trim()));
const warns = out.split(/\r?\n/).filter((l) => /^w: .*\.kt:/.test(l.trim()));
console.log('errors:', errors.length, 'warnings:', warns.length);
console.log(errors.slice(0, 60).join('\n'));
