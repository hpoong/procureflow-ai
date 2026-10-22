// 로컬 전용 개발 서버. front/ 정적 파일을 제공하고 /v1/* 요청은 백엔드로 그대로 넘긴다(CORS 불필요).
// 실행: node front/server.mjs   (환경 변수: FRONT_PORT=5173, API_TARGET=http://localhost:8080)
import { createServer, request as httpRequest } from 'node:http';
import { request as httpsRequest } from 'node:https';
import { readFile } from 'node:fs/promises';
import { extname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('.', import.meta.url));
const HOST = '127.0.0.1';
const PORT = Number(process.env.FRONT_PORT ?? 5173);
const API_TARGET = new URL(process.env.API_TARGET ?? 'http://localhost:8080');
const API_PREFIX = '/v1/';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
};

function sendJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}

function proxy(req, res) {
  const send = API_TARGET.protocol === 'https:' ? httpsRequest : httpRequest;
  const upstream = send({
    protocol: API_TARGET.protocol,
    hostname: API_TARGET.hostname,
    port: API_TARGET.port,
    method: req.method,
    path: req.url,
    headers: { ...req.headers, host: API_TARGET.host },
  }, (upstreamRes) => {
    res.writeHead(upstreamRes.statusCode ?? 502, upstreamRes.headers);
    upstreamRes.pipe(res);
  });
  upstream.on('error', () => {
    if (!res.headersSent) {
      sendJson(res, 502, { code: 'API_UNREACHABLE', message: `백엔드(${API_TARGET.origin})에 연결할 수 없습니다.` });
    } else {
      res.destroy();
    }
  });
  req.pipe(upstream);
}

async function serveStatic(req, res) {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    sendJson(res, 405, { code: 'METHOD_NOT_ALLOWED', message: 'Method Not Allowed' });
    return;
  }
  let pathname;
  try {
    pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
  } catch {
    sendJson(res, 400, { code: 'INVALID_REQUEST', message: 'Bad Request' });
    return;
  }
  const filePath = resolve(ROOT, `.${pathname === '/' ? '/index.html' : pathname}`);
  if (!filePath.startsWith(ROOT.endsWith(sep) ? ROOT : ROOT + sep)) {
    sendJson(res, 403, { code: 'FORBIDDEN', message: 'Forbidden' });
    return;
  }
  try {
    const body = await readFile(filePath);
    res.writeHead(200, {
      'Content-Type': MIME[extname(filePath)] ?? 'application/octet-stream',
      'Cache-Control': 'no-store',
    });
    res.end(req.method === 'HEAD' ? undefined : body);
  } catch {
    sendJson(res, 404, { code: 'NOT_FOUND', message: 'Not Found' });
  }
}

createServer((req, res) => {
  if (req.url?.startsWith(API_PREFIX)) {
    proxy(req, res);
  } else {
    serveStatic(req, res);
  }
}).listen(PORT, HOST, () => {
  console.log(`ProcureFlow 기준정보 화면: http://${HOST}:${PORT}  (API → ${API_TARGET.origin})`);
});
