/**
 * 全链路静态契约核对（验收阶段任务 3）
 * ---------------------------------------------------------------------------
 * 1) 扫描 web-user/src/api、web-admin/src/api 里所有 http.get/post/put/delete/patch 调用，
 *    提取请求路径（含模板字符串 ${id}），按 VITE_API_BASE=/api 还原成真实请求路径。
 * 2) 扫描 server/src/main/java 中所有 @RestController/@Controller，
 *    解析类级 @RequestMapping 与方法级 @GetMapping/@PostMapping/... 拼出完整路径。
 * 3) 归一化动态段（前端 ${x} / 后端 {x} → *），按「HTTP 方法 + 路径」交叉比对，输出两份清单：
 *      - 前端调用但后端没写
 *      - 后端写了但前端没用
 *
 * 用法：node e2e/api-audit.mjs [仓库根路径]
 * 输出：控制台清单 + e2e/api-audit.json（含补充扫描：src/api 之外的 http 调用）
 */

import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(process.argv[2] ?? path.resolve(import.meta.dirname, '..'));
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');
const walk = (dir) => {
  const out = [];
  const abs = path.join(ROOT, dir);
  if (!fs.existsSync(abs)) return out;
  for (const entry of fs.readdirSync(abs, { withFileTypes: true })) {
    const rel = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(rel));
    else out.push(rel);
  }
  return out;
};
const posix = (p) => p.split(path.sep).join('/');

/** 把 ${id} / {id} 归一成 *，去掉查询串、统一前导斜杠与重复斜杠 */
function normalize(raw) {
  let s = String(raw ?? '').split('?')[0];
  s = s.replace(/\$\{[^}]*\}/g, '*').replace(/\{[^}]*\}/g, '*');
  if (!s.startsWith('/')) s = '/' + s;
  s = s.replace(/\/{2,}/g, '/');
  return s.length > 1 ? s.replace(/\/+$/, '') : s;
}
function joinPath(base, sub) {
  const b = (base || '').replace(/\/+$/, '');
  const s = (sub || '').replace(/^\/+/, '');
  return normalize(`${b}/${s}`);
}
/** 从注解参数里抓出所有字符串字面量（支持 value = {"/a","/b"}） */
function literals(args) {
  return [...String(args ?? '').matchAll(/["']([^"']*)["']/g)].map((m) => m[1]);
}

// ---------------------------------------------------------------------------
// 1) 前端 HTTP 调用
// ---------------------------------------------------------------------------
function scanFrontend(dirs) {
  const calls = [];
  for (const dir of dirs) {
    for (const file of walk(dir).filter((f) => f.endsWith('.ts'))) {
      const text = read(file);
      const re = /http\.(get|post|put|patch|delete|request)\b/g;
      let m;
      while ((m = re.exec(text))) {
        const method = m[1].toUpperCase() === 'REQUEST' ? 'ANY' : m[1].toUpperCase();
        let i = m.index + m[0].length;
        // 跳过泛型 <...>（可能有嵌套 >>）
        if (text[i] === '<') {
          let depth = 0;
          while (i < text.length) {
            if (text[i] === '<') depth += 1;
            else if (text[i] === '>') {
              depth -= 1;
              if (depth === 0) { i += 1; break; }
            }
            i += 1;
          }
        }
        while (i < text.length && /\s/.test(text[i])) i += 1;
        if (text[i] !== '(') continue;
        i += 1;
        while (i < text.length && /\s/.test(text[i])) i += 1;
        const quote = text[i];
        if (!["'", '"', '`'].includes(quote)) continue;
        let j = i + 1;
        let raw = '';
        while (j < text.length && text[j] !== quote) { raw += text[j]; j += 1; }
        const line = text.slice(0, m.index).split('\n').length;
        calls.push({
          side: file.includes('web-admin') ? 'admin' : 'user',
          file: posix(file),
          line,
          method,
          raw,
          path: `/api${normalize(raw)}`,
        });
      }
    }
  }
  return calls;
}

// ---------------------------------------------------------------------------
// 2) 后端 Controller 端点
// ---------------------------------------------------------------------------
function scanBackend() {
  const endpoints = [];
  const files = walk('server/src/main/java')
    .filter((f) => f.endsWith('.java') && /@(RestController|Controller)\b/.test(read(f)));
  for (const file of files) {
    const text = read(file);
    const classIdx = text.search(/public\s+(?:final\s+)?class\s/);
    const head = classIdx >= 0 ? text.slice(0, classIdx) : text;
    const body = classIdx >= 0 ? text.slice(classIdx) : text;
    const classAnno = [...head.matchAll(/@RequestMapping\s*\(([^)]*)\)/g)];
    const classBases = classAnno.length
      ? literals(classAnno[classAnno.length - 1][1])
      : [''];
    const methodRe = /@(Get|Post|Put|Delete|Patch|Request)Mapping\s*(?:\(([^)]*)\))?/g;
    let m;
    while ((m = methodRe.exec(body))) {
      const verb = m[1] === 'Request' ? 'ANY' : m[1].toUpperCase();
      const paths = m[2] === undefined ? [''] : literals(m[2]);
      if (paths.length === 0) paths.push('');
      for (const base of classBases.length ? classBases : ['']) {
        for (const sub of paths) {
          const full = sub.startsWith('/api')
            ? normalize(sub)
            : joinPath(base.startsWith('/') ? base : `/${base}`, sub);
          endpoints.push({
            file: posix(file),
            controller: path.basename(file, '.java'),
            method: verb,
            raw: `${base || '(none)'} + ${sub || '(none)'}`,
            path: full.startsWith('/api') ? full : `/api${full}`,
          });
        }
      }
    }
  }
  return endpoints;
}

// ---------------------------------------------------------------------------
// 3) 交叉比对
// ---------------------------------------------------------------------------
const frontend = scanFrontend(['web-user/src/api', 'web-admin/src/api']);
const extra = scanFrontend(['web-user/src/utils', 'web-admin/src/utils',
  'web-user/src/composables', 'web-admin/src/composables']);
const backend = scanBackend();

const key = (m, p) => `${m} ${p}`;
const backendKeys = new Set();
const backendPathMethods = new Map();
for (const e of backend) {
  backendKeys.add(key(e.method, e.path));
  if (!backendPathMethods.has(e.path)) backendPathMethods.set(e.path, new Set());
  backendPathMethods.get(e.path).add(e.method);
}
const frontendKeys = new Set();
for (const c of frontend) frontendKeys.add(key(c.method, c.path));
// 严格口径（用户要求）只看 src/api；宽松口径把 utils/composables 里的调用也算「前端已用」，
// 避免把 utils/confirm.ts 的确认令牌端点误报成「后端写了前端没用」。
const extraKeys = new Set(extra.map((c) => key(c.method, c.path)));
const frontendKeysAll = new Set([...frontendKeys, ...extraKeys]);

const missingBackend = [];
const seenMissing = new Set();
for (const c of frontend) {
  const k = key(c.method, c.path);
  if (backendKeys.has(k) || seenMissing.has(k)) continue;
  seenMissing.add(k);
  const alt = backendPathMethods.get(c.path);
  missingBackend.push({
    method: c.method,
    path: c.path,
    calledBy: frontend.filter((x) => key(x.method, x.path) === k)
      .map((x) => `${x.side}:${x.file}:${x.line}`),
    note: alt ? `路径存在但方法不匹配（后端仅有 ${[...alt].join('/')}）` : '后端无此路径',
  });
}

const unusedBackend = [];
const unusedInApiDirOnly = [];
const unusedSeen = new Set();
for (const e of backend) {
  const k = key(e.method, e.path);
  if (frontendKeysAll.has(k) || unusedSeen.has(k)) continue;
  unusedSeen.add(k);
  unusedBackend.push({ method: e.method, path: e.path, controller: e.controller, file: e.file });
}
for (const e of backend) {
  const k = key(e.method, e.path);
  if (frontendKeys.has(k) || unusedInApiDirOnly.some((x) => key(x.method, x.path) === k)) continue;
  unusedInApiDirOnly.push({
    method: e.method,
    path: e.path,
    controller: e.controller,
    usedOutsideApiDir: extraKeys.has(k),
    note: extraKeys.has(k)
      ? 'src/api 未封装，但由 src/utils 调用（不算真正闲置）'
      : '前端完全未调用',
  });
}

const sortBy = (a, b) => a.path.localeCompare(b.path) || a.method.localeCompare(b.method);
missingBackend.sort(sortBy);
unusedBackend.sort(sortBy);
unusedInApiDirOnly.sort(sortBy);

const result = {
  generatedAt: new Date().toISOString(),
  root: ROOT,
  counts: {
    frontendCalls: frontend.length,
    frontendUnique: frontendKeys.size,
    backendEndpoints: backend.length,
    backendUnique: backendKeys.size,
    missingBackend: missingBackend.length,
    unusedBackend: unusedBackend.length,
    unusedInApiDirOnly: unusedInApiDirOnly.length,
    extraHttpCallsOutsideApiDir: extra.length,
  },
  missingBackend,
  unusedBackend,
  unusedInApiDirOnly,
  extraHttpCallsOutsideApiDir: extra,
  allFrontend: frontend,
  allBackend: backend,
};
fs.writeFileSync(path.join(ROOT, 'e2e/api-audit.json'), JSON.stringify(result, null, 2), 'utf8');

console.log('=== 契约核对统计 ===');
console.log(JSON.stringify(result.counts, null, 2));
console.log(`\n=== A. 前端调用但后端没写（${missingBackend.length}）===`);
for (const x of missingBackend) {
  console.log(`${x.method.padEnd(7)} ${x.path.padEnd(52)} ${x.note}`);
  console.log(`        ${x.calledBy.join(', ')}`);
}
console.log(`\n=== B. 后端写了但前端完全没用（${unusedBackend.length}，含 src/utils 补充扫描）===`);
for (const x of unusedBackend) {
  console.log(`${x.method.padEnd(7)} ${x.path.padEnd(52)} ${x.controller}`);
}
console.log(`\n=== B2. 严格按 src/api 口径「未被封装」的后端端点（${unusedInApiDirOnly.length}）===`);
for (const x of unusedInApiDirOnly) {
  console.log(`${x.method.padEnd(7)} ${x.path.padEnd(52)} ${x.note}`);
}
console.log(`\n=== C. src/api 之外的 http 调用（${extra.length}）===`);
for (const x of extra) console.log(`${x.method.padEnd(7)} ${x.path.padEnd(52)} ${x.file}:${x.line}`);
console.log('\n完整结果：e2e/api-audit.json');
