const ACTOR_STORAGE_KEY = 'procureflow.actor';
const RESULTS_STORAGE_KEY = 'procureflow.po.results';
const DEFAULT_ACTOR = { orgId: 'Org-001', userId: 'User-001' };
const MAX_ACTOR_ID_LENGTH = 64;
const TOAST_MS = 3500;
const NUMBER_MARK = '@@pf-number@@';

const DECIMAL_PATTERN = /^\d+(\.\d+)?$/;
const INTEGER_PATTERN = /^\d+$/;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const ERROR_HINTS = {
  AUTH_REQUIRED: '조직 ID·사용자 ID 를 확인하세요. 헤더 인증은 백엔드 local 프로파일에서만 켜져 있습니다.',
  NETWORK_ERROR: '서버에 연결할 수 없습니다. front 서버(node front/server.mjs)와 백엔드가 떠 있는지 확인하세요.',
  API_UNREACHABLE: '백엔드 기동 여부와 API_TARGET 을 확인하세요.',
};

const STATUS_LABEL = { DRAFT: '초안', PENDING_APPROVAL: '승인 대기', APPROVED: '승인', SENT: '송부', REJECTED: '반려' };

const ROUTING_LABEL = { STANDARD: '일반 입고', DIRECT: '직납', INSPECTION: '검사 입고' };

const LINE_FIELDS = [
  { key: 'itemId', label: '품목', type: 'item', required: true, place: 'row' },
  { key: 'orderedQty', label: '수량', type: 'decimal', required: true, place: 'row', placeholder: '0', positive: true, num: true },
  { key: 'purchaseUom', label: '단위', type: 'text', required: true, place: 'row', maxLength: 20, defaultValue: 'EA' },
  { key: 'unitPrice', label: '단가', type: 'decimal', required: true, place: 'row', placeholder: '0', num: true },
  { key: 'taxCode', label: '세금 코드', type: 'text', required: true, place: 'detail', maxLength: 50, defaultValue: 'VAT10' },
  { key: 'taxRate', label: '세율 (0~1)', type: 'decimal', required: true, place: 'detail', defaultValue: '0.1', max: 1 },
  { key: 'requestedDate', label: '요청 납기', type: 'date', place: 'detail' },
  { key: 'promisedDate', label: '약속 납기', type: 'date', place: 'detail' },
  { key: 'receiptRouting', label: '입고 경로', type: 'routing', required: true, place: 'detail', defaultValue: 'STANDARD' },
  { key: 'inspectionSpecRevisionId', label: '검사 규격 개정 ID', type: 'uuid', place: 'detail', placeholder: '검사 입고면 필수' },
  { key: 'inventoryUom', label: '재고 단위', type: 'text', required: true, place: 'detail', maxLength: 20, defaultValue: 'EA' },
  { key: 'conversionNumerator', label: '환산 분자', type: 'integer', required: true, place: 'detail', defaultValue: '1' },
  { key: 'conversionDenominator', label: '환산 분모', type: 'integer', required: true, place: 'detail', defaultValue: '1' },
  { key: 'overReceiptPct', label: '초과 입고 허용 (%)', type: 'decimal', place: 'detail', placeholder: '0~100', max: 100 },
];

const HEADER_TEXT_FIELDS = ['paymentTermsCode', 'incotermsCode', 'incotermsPlace'];

const MASTER_PATHS = { plants: '/v1/plants', warehouses: '/v1/warehouses', suppliers: '/v1/suppliers', items: '/v1/items' };

const timeFormat = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'short' });
const plainNumber = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 6 });

const state = {
  actor: loadActor(),
  master: null,
  masterError: null,
  submitting: false,
  lineSeq: 0,
  attempt: null,
  results: loadResults(),
};

const $ = (id) => document.getElementById(id);

// ---------- API ----------

class ApiError extends Error {
  constructor(code, message, status) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

/** BigDecimal 정밀도를 지키려고 숫자는 입력 문자열 그대로 JSON 숫자로 내보낸다. */
class JsonNumber {
  constructor(text) {
    this.text = text;
  }

  toJSON() {
    return `${NUMBER_MARK}${this.text}`;
  }
}

function toJson(body) {
  return JSON.stringify(body).replace(new RegExp(`"${NUMBER_MARK}([0-9.]+)"`, 'g'), '$1');
}

async function api(method, path, payload, extraHeaders = {}) {
  const headers = { 'X-Org-Id': state.actor.orgId, 'X-User-Id': state.actor.userId, ...extraHeaders };
  if (payload !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  let res;
  try {
    res = await fetch(path, { method, headers, body: payload });
  } catch {
    throw new ApiError('NETWORK_ERROR', ERROR_HINTS.NETWORK_ERROR, 0);
  }
  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  if (!res.ok) {
    const code = data?.code ?? `HTTP_${res.status}`;
    throw new ApiError(code, data?.message ?? `요청이 실패했습니다. (HTTP ${res.status})`, res.status);
  }
  return data;
}

function describeError(error) {
  const hint = ERROR_HINTS[error.code];
  return hint && hint !== error.message ? `${error.message} ${hint}` : error.message;
}

function newUuid() {
  if (crypto.randomUUID) {
    return crypto.randomUUID();
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * 같은 요청자·같은 본문이면 같은 키를 쓴다. 응답 유실 후 재전송이나 성공 뒤 재클릭은 같은 발주로 재생된다.
 * 같은 내용으로 새 발주를 만들려면 "처음부터 다시" 로 키를 버린다.
 */
function idempotencyKeyFor(payload) {
  const fingerprint = `${state.actor.orgId}\n${state.actor.userId}\n${payload}`;
  if (state.attempt?.fingerprint !== fingerprint) {
    state.attempt = { fingerprint, key: newUuid() };
  }
  return state.attempt.key;
}

// ---------- 요청자 ----------

function loadActor() {
  try {
    const saved = JSON.parse(localStorage.getItem(ACTOR_STORAGE_KEY) ?? 'null');
    if (saved?.orgId && saved?.userId) {
      return { orgId: saved.orgId, userId: saved.userId };
    }
  } catch {
    // 저장값이 깨졌으면 기본값을 쓴다.
  }
  return { ...DEFAULT_ACTOR };
}

function validateActorId(label, value) {
  if (!value) {
    return `${label}를 입력하세요.`;
  }
  if (value.length > MAX_ACTOR_ID_LENGTH) {
    return `${label}는 ${MAX_ACTOR_ID_LENGTH}자 이하여야 합니다.`;
  }
  // fetch 헤더 값은 ISO-8859-1 범위만 허용한다.
  if (!/^[\x21-\x7E]+$/.test(value)) {
    return `${label}는 공백 없는 영문·숫자·기호만 쓸 수 있습니다.`;
  }
  return null;
}

function setupActorForm() {
  $('actor-org').value = state.actor.orgId;
  $('actor-user').value = state.actor.userId;
  $('actor-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const orgId = $('actor-org').value.trim();
    const userId = $('actor-user').value.trim();
    const error = validateActorId('조직 ID', orgId) ?? validateActorId('사용자 ID', userId);
    if (error) {
      toast(error, 'error');
      return;
    }
    state.actor = { orgId, userId };
    localStorage.setItem(ACTOR_STORAGE_KEY, JSON.stringify(state.actor));
    toast(`조직 ${orgId} 기준으로 기준정보를 다시 불러옵니다.`);
    loadMaster();
  });
}

// ---------- DOM 도우미 ----------

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) {
      continue;
    }
    if (key === 'class') {
      node.className = value;
    } else if (key === 'dataset') {
      Object.assign(node.dataset, value);
    } else if (key.startsWith('on')) {
      node.addEventListener(key.slice(2).toLowerCase(), value);
    } else if (key in node && typeof value !== 'string') {
      node[key] = value;
    } else {
      node.setAttribute(key, value === true ? '' : value);
    }
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) {
      continue;
    }
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

function toast(message, type = 'info') {
  const node = el('div', { class: `toast ${type}` }, message);
  $('toasts').append(node);
  setTimeout(() => node.remove(), TOAST_MS);
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    toast('ID 를 복사했습니다.', 'success');
  } catch {
    toast(text);
  }
}

function showAlert(message) {
  const alert = $('form-alert');
  alert.textContent = message;
  alert.hidden = false;
}

function hideAlert() {
  $('form-alert').hidden = true;
}

function currentCurrency() {
  const value = $('f-currency').value.trim().toUpperCase();
  return /^[A-Z]{3}$/.test(value) ? value : null;
}

function money(value) {
  const currency = currentCurrency();
  if (currency) {
    try {
      return new Intl.NumberFormat('ko-KR', { style: 'currency', currency, maximumFractionDigits: 6 }).format(value);
    } catch {
      // 브라우저가 모르는 통화 코드면 숫자 + 코드로 보여준다.
    }
  }
  return `${plainNumber.format(value)}${currency ? ` ${currency}` : ''}`;
}

// ---------- 기준정보 ----------

async function loadMaster() {
  state.master = null;
  state.masterError = null;
  renderMasterOptions();
  try {
    const entries = await Promise.all(Object.entries(MASTER_PATHS).map(async ([key, path]) => [key, await api('GET', path)]));
    state.master = Object.fromEntries(entries);
  } catch (error) {
    state.masterError = error;
  }
  renderMasterOptions();
}

function inactiveSuffix(row) {
  return row.status === 'ACTIVE' ? '' : ' (비활성)';
}

function fillSelect(select, rows, placeholder, emptyText, label) {
  const previous = select.value;
  if (!state.master) {
    select.replaceChildren(el('option', { value: '' }, state.masterError ? '불러오지 못했습니다' : '불러오는 중…'));
    select.disabled = true;
    return;
  }
  if (rows.length === 0) {
    select.replaceChildren(el('option', { value: '' }, emptyText));
    select.disabled = true;
    return;
  }
  select.disabled = false;
  select.replaceChildren(el('option', { value: '' }, placeholder), ...rows.map((row) => el('option', { value: row.id }, label(row))));
  if (rows.some((row) => row.id === previous)) {
    select.value = previous;
  }
}

function renderMasterOptions() {
  const master = state.master ?? { plants: [], warehouses: [], suppliers: [], items: [] };
  fillSelect($('f-supplierId'), master.suppliers, '공급사 선택', '등록된 공급사가 없습니다',
    (s) => `${s.name} (${s.supplierCode})${inactiveSuffix(s)}`);
  fillSelect($('f-plantId'), master.plants, '사업장 선택', '등록된 사업장이 없습니다',
    (p) => `${p.name} (${p.plantCode})${inactiveSuffix(p)}`);
  renderWarehouseOptions();
  document.querySelectorAll('#lines select[name="itemId"]').forEach(renderItemOptions);

  const alert = $('master-alert');
  const missing = state.master
    ? [['suppliers', '공급사'], ['plants', '사업장'], ['warehouses', '창고'], ['items', '품목']]
      .filter(([key]) => state.master[key].length === 0).map(([, label]) => label)
    : [];
  if (state.masterError) {
    alert.className = 'alert';
    alert.replaceChildren(`기준정보를 불러오지 못했습니다. ${describeError(state.masterError)}`);
    alert.hidden = false;
  } else if (missing.length > 0) {
    alert.className = 'alert info';
    alert.replaceChildren(`등록된 ${missing.join('·')}이(가) 없어 발주를 만들 수 없습니다. `,
      el('a', { href: './index.html' }, '기준정보 등록으로 이동'));
    alert.hidden = false;
  } else {
    alert.hidden = true;
  }
  updateSummary();
}

function renderWarehouseOptions() {
  const plantId = $('f-plantId').value;
  const select = $('f-shipToWarehouseId');
  if (state.master && !plantId) {
    select.replaceChildren(el('option', { value: '' }, '사업장을 먼저 고르세요'));
    select.disabled = true;
    return;
  }
  const rows = (state.master?.warehouses ?? []).filter((w) => w.plantId === plantId);
  fillSelect(select, rows, '창고 선택', '이 사업장에 등록된 창고가 없습니다',
    (w) => `${w.name} (${w.warehouseCode})${inactiveSuffix(w)}`);
}

function renderItemOptions(select) {
  fillSelect(select, state.master?.items ?? [], '품목 선택', '등록된 품목이 없습니다', (i) => {
    const notes = [i.status === 'ACTIVE' ? null : '비활성', i.purchaseEnabled ? null : '구매 불가'].filter(Boolean);
    return `${i.name} (${i.itemCode})${notes.length > 0 ? ` · ${notes.join(', ')}` : ''}`;
  });
}

// ---------- 품목 라인 ----------

function renderLineField(field, lineSeq) {
  const id = `line${lineSeq}-${field.key}`;
  const wrap = el('div', { class: 'field', dataset: { key: field.key } });
  if (field.place === 'detail') {
    wrap.append(el('label', { class: 'field-label', for: id }, field.label, field.required && el('span', { class: 'req' }, '*')));
  }

  let control;
  if (field.type === 'item') {
    control = el('select', { id, name: field.key, class: 'input', 'aria-label': field.label });
    renderItemOptions(control);
  } else if (field.type === 'routing') {
    control = el('select', { id, name: field.key, class: 'input' },
      Object.entries(ROUTING_LABEL).map(([value, label]) => el('option', { value, title: value }, label)));
    control.value = field.defaultValue;
  } else {
    control = el('input', {
      id,
      name: field.key,
      type: field.type === 'date' ? 'date' : 'text',
      class: `input${field.num ? ' num' : ''}${['decimal', 'integer', 'uuid'].includes(field.type) ? ' mono' : ''}`,
      inputmode: field.type === 'decimal' ? 'decimal' : field.type === 'integer' ? 'numeric' : undefined,
      maxlength: field.maxLength ? String(field.maxLength) : undefined,
      placeholder: field.placeholder,
      autocomplete: 'off',
      'aria-label': field.place === 'row' ? field.label : undefined,
      value: field.defaultValue ?? '',
    });
  }
  wrap.append(control, el('span', { class: 'field-error', hidden: true }));
  return wrap;
}

function addLine() {
  state.lineSeq += 1;
  const lineSeq = state.lineSeq;
  const fields = Object.fromEntries(LINE_FIELDS.map((field) => [field.key, renderLineField(field, lineSeq)]));
  const detailBtn = el('button', { type: 'button', class: 'btn btn-ghost btn-sm', 'aria-expanded': 'false' }, '상세');
  const removeBtn = el('button', { type: 'button', class: 'btn-icon', title: '품목 삭제', 'aria-label': '품목 삭제' }, '×');
  const detail = el('div', { class: 'line-detail', hidden: true },
    LINE_FIELDS.filter((f) => f.place === 'detail').map((f) => fields[f.key]));

  const line = el('div', { class: 'line', role: 'row' },
    el('div', { class: 'line-row' },
      el('span', { class: 'line-no' }),
      el('div', { class: 'line-item' }, fields.itemId, el('span', { class: 'line-meta' })),
      fields.orderedQty,
      fields.purchaseUom,
      fields.unitPrice,
      el('span', { class: 'line-amount num' }, '-'),
      el('div', { class: 'line-actions' }, detailBtn, removeBtn)),
    detail);

  detailBtn.addEventListener('click', () => toggleDetail(line));
  removeBtn.addEventListener('click', () => {
    line.remove();
    renumberLines();
    updateSummary();
  });
  line.addEventListener('input', (event) => onLineEdit(line, event));
  line.addEventListener('change', (event) => onLineEdit(line, event));

  $('lines').append(line);
  syncInspectionField(line);
  renumberLines();
  updateSummary();
  return line;
}

function onLineEdit(line, event) {
  const wrap = event.target.closest('.field');
  if (wrap) {
    clearFieldError(wrap);
  }
  if (event.target.name === 'receiptRouting') {
    syncInspectionField(line);
  }
  updateLine(line);
  updateSummary();
}

function toggleDetail(line, open) {
  const detail = line.querySelector('.line-detail');
  const next = open ?? detail.hidden;
  detail.hidden = !next;
  line.querySelector('[aria-expanded]').setAttribute('aria-expanded', String(next));
  line.classList.toggle('open', next);
}

function renumberLines() {
  const lines = [...$('lines').children];
  lines.forEach((line, index) => {
    line.querySelector('.line-no').textContent = String(index + 1);
    line.querySelector('.btn-icon').disabled = lines.length === 1;
  });
}

function syncInspectionField(line) {
  const inspection = line.querySelector('[name="receiptRouting"]').value === 'INSPECTION';
  const wrap = line.querySelector('.field[data-key="inspectionSpecRevisionId"]');
  wrap.hidden = !inspection;
  if (!inspection) {
    wrap.querySelector('input').value = '';
    clearFieldError(wrap);
  }
}

function lineValue(line, key) {
  return line.querySelector(`[name="${key}"]`).value.trim();
}

function lineAmounts(line) {
  const qty = lineValue(line, 'orderedQty');
  const price = lineValue(line, 'unitPrice');
  const rate = lineValue(line, 'taxRate');
  if (!DECIMAL_PATTERN.test(qty) || !DECIMAL_PATTERN.test(price)) {
    return null;
  }
  const net = Number(qty) * Number(price);
  return { net, tax: DECIMAL_PATTERN.test(rate) ? net * Number(rate) : 0 };
}

function updateLine(line) {
  const amounts = lineAmounts(line);
  line.querySelector('.line-amount').textContent = amounts ? money(amounts.net) : '-';

  const rate = lineValue(line, 'taxRate');
  const routing = lineValue(line, 'receiptRouting');
  const due = lineValue(line, 'promisedDate') || lineValue(line, 'requestedDate');
  const meta = [
    `${lineValue(line, 'taxCode') || '세금 코드 없음'}${DECIMAL_PATTERN.test(rate) ? ` ${plainNumber.format(Number(rate) * 100)}%` : ''}`,
    ROUTING_LABEL[routing] ?? routing,
    due ? `납기 ${due}` : null,
  ].filter(Boolean);
  line.querySelector('.line-meta').textContent = meta.join(' · ');
}

// ---------- 요약 ----------

function selectedLabel(id) {
  const select = $(id);
  return select.value ? select.selectedOptions[0].textContent : '-';
}

function updateSummary() {
  $('sum-supplier').textContent = selectedLabel('f-supplierId');
  const warehouse = selectedLabel('f-shipToWarehouseId');
  $('sum-shipto').textContent = warehouse === '-' ? selectedLabel('f-plantId') : `${selectedLabel('f-plantId')} / ${warehouse}`;

  const lines = [...$('lines').children];
  const amounts = lines.map(lineAmounts);
  const priced = amounts.filter(Boolean);
  $('sum-lines').textContent = `${lines.length}건${priced.length < lines.length ? ` (금액 미입력 ${lines.length - priced.length}건)` : ''}`;
  lines.forEach(updateLine);

  if (priced.length === 0) {
    ['sum-net', 'sum-tax', 'sum-total'].forEach((id) => { $(id).textContent = '-'; });
    return;
  }
  const net = priced.reduce((sum, a) => sum + a.net, 0);
  const tax = priced.reduce((sum, a) => sum + a.tax, 0);
  $('sum-net').textContent = money(net);
  $('sum-tax').textContent = money(tax);
  $('sum-total').textContent = money(net + tax);
}

// ---------- 본문 만들기 ----------

function readDecimal(raw, { required, positive, max }, errors, path) {
  if (!raw) {
    if (required) {
      errors[path] = '값을 입력하세요.';
    }
    return undefined;
  }
  if (!DECIMAL_PATTERN.test(raw)) {
    errors[path] = '숫자만 입력하세요.';
    return undefined;
  }
  if (positive && Number(raw) <= 0) {
    errors[path] = '0보다 커야 합니다.';
    return undefined;
  }
  if (max !== undefined && Number(raw) > max) {
    errors[path] = `${max} 이하여야 합니다.`;
    return undefined;
  }
  return new JsonNumber(raw);
}

function readLine(line, index, errors) {
  const body = {};
  for (const field of LINE_FIELDS) {
    const path = `lines[${index}].${field.key}`;
    const raw = lineValue(line, field.key);

    if (field.type === 'decimal') {
      body[field.key] = readDecimal(raw, field, errors, path);
    } else if (field.type === 'integer') {
      if (!INTEGER_PATTERN.test(raw) || Number(raw) <= 0) {
        errors[path] = '1 이상의 정수를 입력하세요.';
      } else {
        body[field.key] = new JsonNumber(raw);
      }
    } else if (field.type === 'uuid') {
      const inspection = lineValue(line, 'receiptRouting') === 'INSPECTION';
      if (inspection && !UUID_PATTERN.test(raw)) {
        errors[path] = raw ? 'UUID 형식이 아닙니다.' : '검사 입고면 필요합니다.';
      } else if (inspection) {
        body[field.key] = raw;
      }
    } else if (field.required && !raw) {
      errors[path] = field.type === 'item' ? '품목을 고르세요.' : '값을 입력하세요.';
    } else if (raw) {
      body[field.key] = raw;
    }
  }
  return body;
}

function readBody() {
  const errors = {};
  const value = (key) => $(`f-${key}`).value.trim();
  const body = {};

  for (const key of ['plantId', 'shipToWarehouseId', 'supplierId']) {
    body[key] = value(key) || undefined;
    if (!body[key]) {
      errors[key] = '선택하세요.';
    }
  }

  const currency = value('currency').toUpperCase();
  $('f-currency').value = currency;
  if (!/^[A-Z]{3}$/.test(currency)) {
    errors.currency = '영문 대문자 3자리 (예: KRW, USD)';
  }
  body.currency = currency;
  body.exchangeRate = readDecimal(value('exchangeRate'), { positive: true }, errors, 'exchangeRate');

  for (const key of HEADER_TEXT_FIELDS) {
    body[key] = value(key) || undefined;
  }

  const snapshot = value('paymentTermsSnapshot');
  if (snapshot) {
    try {
      const parsed = JSON.parse(snapshot);
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        errors.paymentTermsSnapshot = 'JSON object 만 입력할 수 있습니다.';
      } else {
        body.paymentTermsSnapshot = parsed;
      }
    } catch {
      errors.paymentTermsSnapshot = 'JSON 형식이 올바르지 않습니다.';
    }
  }

  body.lines = [...$('lines').children].map((line, index) => readLine(line, index, errors));
  return { body, errors };
}

// ---------- 오류 표시 ----------

function findFieldWrap(path) {
  const lineMatch = path.match(/^lines\[(\d+)\]\.(\w+)$/);
  if (lineMatch) {
    const line = $('lines').children[Number(lineMatch[1])];
    const key = lineMatch[2] === 'inspectionSpecConsistent' ? 'inspectionSpecRevisionId' : lineMatch[2];
    return line?.querySelector(`.field[data-key="${key}"]`) ?? null;
  }
  const key = path === 'currencySupported' ? 'currency' : path;
  return document.querySelector(`#po-form .step-body .field[data-key="${key}"]`);
}

function setFieldError(path, message) {
  const wrap = findFieldWrap(path);
  const slot = wrap?.querySelector('.field-error');
  if (!wrap || !slot || wrap.hidden) {
    return false;
  }
  const detail = wrap.closest('.line-detail');
  if (detail) {
    toggleDetail(detail.closest('.line'), true);
  }
  wrap.classList.add('invalid');
  slot.textContent = message;
  slot.hidden = false;
  return true;
}

function clearFieldError(wrap) {
  wrap.classList.remove('invalid');
  const slot = wrap.querySelector('.field-error');
  if (slot) {
    slot.hidden = true;
  }
}

function clearAllFieldErrors() {
  document.querySelectorAll('#po-form .field.invalid').forEach(clearFieldError);
}

function showFieldErrors(errors) {
  const leftovers = [];
  for (const [path, message] of Object.entries(errors)) {
    if (!setFieldError(path, message)) {
      leftovers.push(`${path}: ${message}`);
    }
  }
  document.querySelector('#po-form .field.invalid')?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  return leftovers;
}

/** 서버 400 메시지는 "field: 사유; field: 사유" 형식이다. 라인 필드는 lines[0].orderedQty 처럼 온다. */
function applyServerError(error) {
  if (error.status === 0 || error.status >= 500) {
    showAlert(`${describeError(error)} 내용을 바꾸지 않고 다시 누르면 중복 없이 처리됩니다.`);
    return;
  }
  if (error.code !== 'INVALID_REQUEST') {
    showAlert(describeError(error));
    return;
  }
  const errors = {};
  const leftovers = [];
  for (const part of error.message.split('; ')) {
    const match = part.match(/^([A-Za-z0-9_\-[\].]+): (.+)$/);
    if (match) {
      errors[match[1]] = match[2];
    } else {
      leftovers.push(part);
    }
  }
  leftovers.push(...showFieldErrors(errors));
  showAlert(leftovers.length > 0 ? leftovers.join(' / ') : '표시된 항목을 확인하세요.');
}

// ---------- 최근 생성 ----------

function loadResults() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(RESULTS_STORAGE_KEY) ?? '[]');
    return Array.isArray(saved) ? saved : [];
  } catch {
    return [];
  }
}

/** 같은 발주 ID 가 이미 있으면 재생 응답으로 보고 갱신만 한다. */
function recordResult(created) {
  const entry = {
    po: created,
    orgId: state.actor.orgId,
    supplier: selectedLabel('f-supplierId'),
    total: $('sum-total').textContent,
  };
  const index = state.results.findIndex((r) => r.po?.id === created.id);
  const replayed = index >= 0;
  if (replayed) {
    state.results.splice(index, 1);
  }
  state.results.unshift(entry);
  sessionStorage.setItem(RESULTS_STORAGE_KEY, JSON.stringify(state.results));
  renderResults(created.id);
  return replayed;
}

function renderResults(flashId) {
  const entries = state.results.filter((r) => r.po);
  $('results-empty').hidden = entries.length > 0;
  $('results').replaceChildren(...entries.map(({ po, orgId, supplier, total }) => el('article',
    { class: `recent-item${po.id === flashId ? ' flash' : ''}` },
    el('div', { class: 'recent-top' },
      el('span', { class: 'mono recent-number' }, po.poNumber),
      el('span', { class: 'badge info' }, STATUS_LABEL[po.status] ?? po.status)),
    el('div', { class: 'recent-sub' }, `${supplier} · 품목 ${po.lines.length}건${total && total !== '-' ? ` · ${total}` : ''}`),
    el('div', { class: 'recent-sub muted' },
      `${timeFormat.format(new Date(po.createdAt))} · ${orgId}`,
      el('button', {
        type: 'button',
        class: 'link-btn',
        title: `${po.id} (클릭하여 복사)`,
        onclick: () => copyText(po.id),
      }, 'ID 복사')),
  )));
}

// ---------- 제출 ----------

function setSubmitting(submitting) {
  state.submitting = submitting;
  const submit = $('submit-btn');
  submit.disabled = submitting;
  submit.textContent = submitting ? '생성 중…' : '발주 생성';
}

async function onSubmit(event) {
  event.preventDefault();
  if (state.submitting) {
    return;
  }
  clearAllFieldErrors();
  hideAlert();

  const { body, errors } = readBody();
  if (Object.keys(errors).length > 0) {
    const leftovers = showFieldErrors(errors);
    showAlert(leftovers.length > 0 ? leftovers.join(' / ') : `입력이 필요한 항목이 ${Object.keys(errors).length}개 있습니다.`);
    return;
  }

  const payload = toJson(body);
  setSubmitting(true);
  try {
    const created = await api('POST', '/v1/purchase-orders', payload, {
      'Idempotency-Key': idempotencyKeyFor(payload),
      'X-Request-Id': newUuid(),
    });
    const replayed = recordResult(created);
    toast(replayed ? `이미 생성된 발주 ${created.poNumber} 입니다.` : `발주 ${created.poNumber} 를 만들었습니다.`, 'success');
  } catch (error) {
    applyServerError(error);
  } finally {
    setSubmitting(false);
  }
}

function resetForm() {
  $('po-form').reset();
  $('f-currency').value = 'KRW';
  $('lines').replaceChildren();
  addLine();
  clearAllFieldErrors();
  hideAlert();
  state.attempt = null;
  renderMasterOptions();
}

// ---------- 시작 ----------

setupActorForm();
$('po-form').addEventListener('submit', onSubmit);
$('po-form').addEventListener('input', (event) => {
  if (event.target.closest('.step-body')) {
    clearFieldError(event.target.closest('.field'));
    updateSummary();
  }
});
$('po-form').addEventListener('change', (event) => {
  if (!event.target.closest('.step-body')) {
    return;
  }
  clearFieldError(event.target.closest('.field'));
  if (event.target.id === 'f-plantId') {
    renderWarehouseOptions();
  }
  updateSummary();
});
$('add-line').addEventListener('click', () => addLine().querySelector('select[name="itemId"]').focus());
$('reset-btn').addEventListener('click', resetForm);
$('reload-master').addEventListener('click', loadMaster);

addLine();
renderResults();
loadMaster();
