const ACTOR_STORAGE_KEY = 'procureflow.actor';
const DEFAULT_ACTOR = { orgId: 'Org-001', userId: 'User-001' };
const NUMBER_MARK = '@@pf-number@@';
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DECIMAL_PATTERN = /^\d+(\.\d+)?$/;
const INTEGER_PATTERN = /^\d+$/;
const STATUS_LABEL = { DRAFT: '초안', PENDING_APPROVAL: '승인 대기', APPROVED: '승인', SENT: '송부', REJECTED: '반려' };
const ROUTING_LABEL = { STANDARD: '일반 입고', DIRECT: '직납', INSPECTION: '검사 입고' };
const ERROR_HINTS = {
  AUTH_REQUIRED: '조직 ID·사용자 ID를 확인하세요. 헤더 인증은 백엔드 local 프로파일에서만 켜져 있습니다.',
  NETWORK_ERROR: '서버에 연결할 수 없습니다. front 서버와 백엔드가 떠 있는지 확인하세요.',
  API_UNREACHABLE: '백엔드 기동 여부와 API_TARGET을 확인하세요.',
  PO_VERSION_CONFLICT: '최신 발주 version을 확인해 다시 입력하세요.',
  OPEN_REVISION_EXISTS: '진행 중인 개정은 한 건만 둘 수 있습니다.',
  REVISION_NO_CHANGE: '현재 유효본과 다른 값을 한 가지 이상 입력하세요.',
};
const LINE_FIELDS = [
  ['orderedQty', '수량', 'decimal'], ['purchaseUom', '구매 단위', 'text'], ['inventoryUom', '재고 단위', 'text'],
  ['unitPrice', '단가', 'decimal'], ['taxCode', '세금 코드', 'text'], ['taxRate', '세율 (0~1)', 'decimal'],
  ['requestedDate', '요청 납기', 'date'], ['promisedDate', '약속 납기', 'date'],
  ['conversionNumerator', '환산 분자', 'integer'], ['conversionDenominator', '환산 분모', 'integer'],
  ['overReceiptPct', '초과 입고 (%)', 'decimal'], ['inspectionSpecRevisionId', '검사 규격 개정 ID', 'uuid'],
];

const state = { actor: loadActor(), poId: null, history: null, currentDetail: null, master: { warehouses: [], items: [] }, attempt: null, submitting: false };
const $ = (id) => document.getElementById(id);

class ApiError extends Error {
  constructor(code, message, status) { super(message); this.code = code; this.status = status; }
}
class JsonNumber {
  constructor(text) { this.text = text; }
  toJSON() { return `${NUMBER_MARK}${this.text}`; }
}
function toJson(body) { return JSON.stringify(body).replace(new RegExp(`"${NUMBER_MARK}([0-9.]+)"`, 'g'), '$1'); }
function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  Object.entries(props).forEach(([key, value]) => {
    if (value === undefined || value === null || value === false) return;
    if (key === 'class') node.className = value;
    else if (key === 'dataset') Object.assign(node.dataset, value);
    else if (key.startsWith('on')) node.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key in node && typeof value !== 'string') node[key] = value;
    else node.setAttribute(key, value === true ? '' : value);
  });
  children.flat().forEach((child) => { if (child !== null && child !== undefined) node.append(child instanceof Node ? child : document.createTextNode(String(child))); });
  return node;
}
async function api(method, path, payload, extraHeaders = {}) {
  const headers = { 'X-Org-Id': state.actor.orgId, 'X-User-Id': state.actor.userId, ...extraHeaders };
  if (payload !== undefined) headers['Content-Type'] = 'application/json';
  let response;
  try { response = await fetch(path, { method, headers, body: payload }); }
  catch { throw new ApiError('NETWORK_ERROR', ERROR_HINTS.NETWORK_ERROR, 0); }
  const text = await response.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { /* 응답 형식 오류는 HTTP 상태로 표시한다. */ }
  if (!response.ok) throw new ApiError(data?.code ?? `HTTP_${response.status}`, data?.message ?? `요청 실패 (HTTP ${response.status})`, response.status);
  return data;
}
function describeError(error) { return [error.message, ERROR_HINTS[error.code]].filter((v, i, a) => v && a.indexOf(v) === i).join(' '); }
function newUuid() {
  if (crypto.randomUUID) return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((value) => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
function toast(message, type = 'info') {
  const node = el('div', { class: `toast ${type}` }, message); $('toasts').append(node); setTimeout(() => node.remove(), 3500);
}
function showAlert(id, message) { const node = $(id); node.textContent = message; node.hidden = false; }
function hideAlert(id) { $(id).hidden = true; }
function loadActor() {
  try { const saved = JSON.parse(localStorage.getItem(ACTOR_STORAGE_KEY) ?? 'null'); if (saved?.orgId && saved?.userId) return saved; } catch { /* 기본값 사용 */ }
  return { ...DEFAULT_ACTOR };
}
function setupActor() {
  $('actor-org').value = state.actor.orgId; $('actor-user').value = state.actor.userId;
  $('actor-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const orgId = $('actor-org').value.trim(); const userId = $('actor-user').value.trim();
    if (!orgId || !userId || !/^[\x21-\x7e]+$/.test(orgId + userId)) return toast('조직 ID와 사용자 ID는 공백 없는 영문·숫자·기호로 입력하세요.', 'error');
    state.actor = { orgId, userId }; localStorage.setItem(ACTOR_STORAGE_KEY, JSON.stringify(state.actor));
    clearWorkspace(); masterReady = loadMaster(); toast('요청자를 적용했습니다. 발주를 다시 조회하세요.');
  });
}

async function loadMaster() {
  try {
    const [warehouses, items] = await Promise.all([api('GET', '/v1/warehouses'), api('GET', '/v1/items')]);
    state.master = { warehouses, items };
  } catch (error) {
    state.master = { warehouses: [], items: [] };
    toast(`기준정보를 불러오지 못했습니다. ${describeError(error)}`, 'error');
  }
}
function optionLabel(row, codeKey) { return `${row.name} (${row[codeKey]})${row.status === 'ACTIVE' ? '' : ' · 비활성'}`; }

function clearWorkspace() {
  state.poId = null; state.history = null; state.currentDetail = null; state.attempt = null;
  $('revision-workspace').hidden = true; hideAlert('lookup-alert');
}
async function lookup(event) {
  event?.preventDefault(); hideAlert('lookup-alert');
  const poId = $('po-id').value.trim();
  if (!UUID_PATTERN.test(poId)) return showAlert('lookup-alert', '발주 ID를 UUID 형식으로 입력하세요.');
  $('lookup-btn').disabled = true;
  try {
    await masterReady;
    const history = await api('GET', `/v1/purchase-orders/${poId}/revisions`);
    state.poId = poId; state.history = history; state.attempt = null;
    $('revision-workspace').hidden = false; renderHistory();
    const current = history.revisions.find((row) => row.revision === history.currentRevision);
    if (!current) throw new ApiError('REVISION_NOT_FOUND', '현재 유효 개정을 찾을 수 없습니다.', 404);
    const detail = await loadDetail(current.revision);
    state.currentDetail = detail; fillRevisionForm(detail.afterSnapshot);
    $('revision-form').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) { clearWorkspace(); showAlert('lookup-alert', describeError(error)); }
  finally { $('lookup-btn').disabled = false; }
}
async function refreshHistory() {
  if (!state.poId) return;
  try { state.history = await api('GET', `/v1/purchase-orders/${state.poId}/revisions`); renderHistory(); toast('개정 이력을 새로고침했습니다.'); }
  catch (error) { showAlert('lookup-alert', describeError(error)); }
}
function renderHistory() {
  $('po-meta').textContent = `${state.history.poNumber} · 현재 유효 개정 ${state.history.currentRevision} · ${state.history.revisions.length}건`;
  $('revision-list').replaceChildren(...state.history.revisions.map((row) => el('button', {
    type: 'button', class: 'revision-item', onclick: () => loadDetail(row.revision), dataset: { revision: row.revision },
  },
  el('span', { class: 'revision-number' }, `개정 ${row.revision}`),
  el('span', { class: `badge ${row.revisionStatus === 'DRAFT' ? 'info' : 'active'}` }, STATUS_LABEL[row.revisionStatus] ?? row.revisionStatus),
  el('span', { class: 'revision-reason' }, row.changeReasonCode ?? (row.revision === 1 ? '최초 생성' : '-')),
  el('span', { class: 'revision-by' }, `${row.createdBy} · ${formatTime(row.createdAt)}`))));
}
async function loadDetail(revision) {
  document.querySelectorAll('.revision-item').forEach((node) => node.classList.toggle('selected', node.dataset.revision === String(revision)));
  try {
    const detail = await api('GET', `/v1/purchase-orders/${state.poId}/revisions/${revision}`);
    renderDetail(detail); return detail;
  } catch (error) { showAlert('lookup-alert', describeError(error)); throw error; }
}
function formatTime(value) { return value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '-'; }
function renderDetail(detail) {
  $('detail-empty').hidden = true; $('detail-content').hidden = false;
  $('detail-meta').textContent = `개정 ${detail.revision} · ${STATUS_LABEL[detail.revisionStatus] ?? detail.revisionStatus} · ${detail.createdBy} · ${formatTime(detail.createdAt)}`;
  $('detail-summary').textContent = detail.changeReasonCode ? `${detail.changeReasonCode}${detail.changeReasonDetail ? ` — ${detail.changeReasonDetail}` : ''}` : '최초 생성';
  renderSnapshot($('before-snapshot'), detail.beforeSnapshot, '이전 스냅샷이 없습니다.');
  renderSnapshot($('after-snapshot'), detail.afterSnapshot, '스냅샷이 없습니다.');
}
function renderSnapshot(target, snapshot, emptyText) {
  if (!snapshot) { target.replaceChildren(el('div', { class: 'empty small' }, emptyText)); return; }
  const h = snapshot.header ?? {}; const lines = snapshot.lines ?? [];
  target.replaceChildren(
    el('dl', { class: 'snapshot-terms' },
      pair('입고 창고', h.shipToWarehouseId), pair('환율', h.exchangeRate), pair('결제 조건', h.paymentTermsCode), pair('인코텀즈', [h.incotermsCode, h.incotermsPlace].filter(Boolean).join(' '))),
    el('div', { class: 'snapshot-lines' }, ...lines.map((line) => el('div', { class: `snapshot-line${line.lineStatus === 'CANCELLED' ? ' cancelled' : ''}` },
      el('b', {}, `#${line.lineNo} ${itemName(line.itemId)}`),
      el('span', {}, `${line.orderedQty} ${line.purchaseUom} × ${line.unitPrice}`),
      el('span', { class: 'cell-sub' }, `${line.lineStatus} · ${ROUTING_LABEL[line.receiptRouting] ?? line.receiptRouting}`)))));
}
function pair(label, value) { return el('div', {}, el('dt', {}, label), el('dd', {}, value ?? '-')); }
function itemName(id) { const row = state.master.items.find((item) => item.id === id); return row ? `${row.name} (${row.itemCode})` : id; }

function fillRevisionForm(snapshot) {
  const h = snapshot.header; state.attempt = null;
  $('fixed-terms').textContent = `${h.poNumber} · 공급사 ${h.supplierId} · 사업장 ${h.plantId} · ${h.currency}`;
  fillWarehouseSelect(h.plantId, h.shipToWarehouseId);
  setValue('exchangeRate', h.exchangeRate); setValue('paymentTermsCode', h.paymentTermsCode); setValue('incotermsCode', h.incotermsCode); setValue('incotermsPlace', h.incotermsPlace);
  $('f-paymentTermsSnapshot').value = h.paymentTermsSnapshot ? JSON.stringify(h.paymentTermsSnapshot) : '';
  $('f-changeReasonCode').value = ''; $('f-changeReasonDetail').value = '';
  $('revision-lines').replaceChildren();
  snapshot.lines.filter((line) => line.lineStatus === 'ACTIVE').forEach((line) => addRevisionLine(line));
  clearErrors(); hideAlert('form-alert');
}
function setValue(key, value) { $(`f-${key}`).value = value ?? ''; }
function fillWarehouseSelect(plantId, selected) {
  const rows = state.master.warehouses.filter((row) => row.plantId === plantId);
  const select = $('f-shipToWarehouseId');
  select.replaceChildren(...rows.map((row) => el('option', { value: row.id }, optionLabel(row, 'warehouseCode'))));
  if (!rows.some((row) => row.id === selected)) select.append(el('option', { value: selected }, `${selected} (현재 창고)`));
  select.value = selected;
}
function addRevisionLine(source = null) {
  const existing = Boolean(source?.id);
  const line = el('article', { class: 'revision-line', dataset: { lineId: source?.id ?? '' } });
  const itemSelect = el('select', { name: 'itemId', class: 'input', disabled: existing });
  if (existing) itemSelect.append(el('option', { value: source.itemId }, itemName(source.itemId)));
  else itemSelect.append(el('option', { value: '' }, '품목 선택'), ...state.master.items.map((item) => el('option', { value: item.id }, optionLabel(item, 'itemCode'))));
  const cancel = existing ? el('label', { class: 'cancel-toggle' }, el('input', { type: 'checkbox', name: 'cancelled' }), ' 이 라인 취소') : null;
  const remove = existing ? null : el('button', { type: 'button', class: 'btn-icon', title: '신규 라인 삭제', onclick: () => line.remove() }, '×');
  line.append(el('div', { class: 'revision-line-head' },
    el('div', {}, el('b', {}, existing ? `기존 #${source.lineNo}` : '신규 품목'), el('span', { class: 'cell-sub mono' }, source?.id ?? '저장 시 라인 번호 부여')),
    el('div', { class: 'revision-line-item field', dataset: { key: 'itemId' } }, itemSelect, el('span', { class: 'field-error', hidden: true })), cancel, remove));
  const fields = el('div', { class: 'revision-line-fields' });
  LINE_FIELDS.forEach(([key, label, type]) => {
    const hidden = key === 'inspectionSpecRevisionId' && (source?.receiptRouting ?? 'STANDARD') !== 'INSPECTION';
    fields.append(el('label', { class: 'field', dataset: { key }, hidden }, el('span', { class: 'field-label' }, label),
      el('input', { name: key, class: 'input', type: type === 'date' ? 'date' : 'text', inputmode: ['decimal', 'integer'].includes(type) ? 'decimal' : undefined, value: source?.[key] ?? defaultLineValue(key) }),
      el('span', { class: 'field-error', hidden: true })));
  });
  const routing = el('label', { class: 'field', dataset: { key: 'receiptRouting' } }, el('span', { class: 'field-label' }, '입고 경로'),
    el('select', { name: 'receiptRouting', class: 'input' }, ...Object.entries(ROUTING_LABEL).map(([value, label]) => el('option', { value }, label))), el('span', { class: 'field-error', hidden: true }));
  routing.querySelector('select').value = source?.receiptRouting ?? 'STANDARD'; fields.append(routing); line.append(fields);
  line.addEventListener('change', (event) => {
    if (event.target.name === 'cancelled') line.classList.toggle('cancelled', event.target.checked);
    if (event.target.name === 'receiptRouting') {
      const inspection = event.target.value === 'INSPECTION'; const field = line.querySelector('[data-key="inspectionSpecRevisionId"]'); field.hidden = !inspection;
      if (!inspection) field.querySelector('input').value = '';
    }
  });
  $('revision-lines').append(line); return line;
}
function defaultLineValue(key) { return ({ purchaseUom: 'EA', inventoryUom: 'EA', taxCode: 'VAT10', taxRate: '0.1', conversionNumerator: '1', conversionDenominator: '1' })[key] ?? ''; }

function fieldValue(line, key) { return line.querySelector(`[name="${key}"]`)?.value.trim() ?? ''; }
function readDecimal(raw, errors, path, { required = false, positive = false, max } = {}) {
  if (!raw) { if (required) errors[path] = '값을 입력하세요.'; return undefined; }
  if (!DECIMAL_PATTERN.test(raw) || (positive && Number(raw) <= 0) || (max !== undefined && Number(raw) > max)) { errors[path] = `올바른 숫자를 입력하세요${max !== undefined ? ` (${max} 이하)` : ''}.`; return undefined; }
  return new JsonNumber(raw);
}
function readLine(line, index, errors) {
  const body = {}; const lineId = line.dataset.lineId;
  if (lineId) body.lineId = lineId;
  body.itemId = fieldValue(line, 'itemId'); if (!body.itemId) errors[`lines[${index}].itemId`] = '품목을 고르세요.';
  body.orderedQty = readDecimal(fieldValue(line, 'orderedQty'), errors, `lines[${index}].orderedQty`, { required: true, positive: true });
  body.unitPrice = readDecimal(fieldValue(line, 'unitPrice'), errors, `lines[${index}].unitPrice`, { required: true });
  body.taxRate = readDecimal(fieldValue(line, 'taxRate'), errors, `lines[${index}].taxRate`, { required: true, max: 1 });
  body.overReceiptPct = readDecimal(fieldValue(line, 'overReceiptPct'), errors, `lines[${index}].overReceiptPct`, { max: 100 });
  for (const key of ['purchaseUom', 'inventoryUom', 'taxCode', 'receiptRouting']) { body[key] = fieldValue(line, key); if (!body[key]) errors[`lines[${index}].${key}`] = '값을 입력하세요.'; }
  for (const key of ['conversionNumerator', 'conversionDenominator']) { const raw = fieldValue(line, key); if (!INTEGER_PATTERN.test(raw) || Number(raw) <= 0) errors[`lines[${index}].${key}`] = '1 이상의 정수'; else body[key] = new JsonNumber(raw); }
  for (const key of ['requestedDate', 'promisedDate']) { const raw = fieldValue(line, key); if (raw) body[key] = raw; }
  const inspection = fieldValue(line, 'receiptRouting') === 'INSPECTION'; const spec = fieldValue(line, 'inspectionSpecRevisionId');
  if (inspection && !UUID_PATTERN.test(spec)) errors[`lines[${index}].inspectionSpecRevisionId`] = '검사 입고면 UUID가 필요합니다.'; else if (inspection) body.inspectionSpecRevisionId = spec;
  return body;
}
function readBody() {
  const errors = {}; const body = {};
  const version = $('f-expectedVersion').value.trim(); if (!INTEGER_PATTERN.test(version)) errors.expectedVersion = '0 이상의 정수를 입력하세요.'; else body.expectedVersion = new JsonNumber(version);
  body.changeReasonCode = $('f-changeReasonCode').value.trim(); if (!body.changeReasonCode) errors.changeReasonCode = '사유 코드를 입력하세요.';
  body.changeReasonDetail = $('f-changeReasonDetail').value.trim() || undefined;
  body.shipToWarehouseId = $('f-shipToWarehouseId').value || undefined; if (!body.shipToWarehouseId) errors.shipToWarehouseId = '창고를 고르세요.';
  body.exchangeRate = readDecimal($('f-exchangeRate').value.trim(), errors, 'exchangeRate', { positive: true });
  for (const key of ['paymentTermsCode', 'incotermsCode', 'incotermsPlace']) body[key] = $(`f-${key}`).value.trim() || undefined;
  const snapshot = $('f-paymentTermsSnapshot').value.trim();
  if (snapshot) { try { const parsed = JSON.parse(snapshot); if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error(); body.paymentTermsSnapshot = parsed; } catch { errors.paymentTermsSnapshot = 'JSON object 형식으로 입력하세요.'; } }
  const all = [...$('revision-lines').children]; const active = all.filter((line) => !line.querySelector('[name="cancelled"]')?.checked);
  body.cancelLineIds = all.filter((line) => line.querySelector('[name="cancelled"]')?.checked).map((line) => line.dataset.lineId);
  body.lines = active.map((line, index) => readLine(line, index, errors)); if (active.length === 0) errors.lines = '유지하거나 새로 추가할 라인이 하나 이상 필요합니다.';
  return { body, errors };
}
function clearErrors() { document.querySelectorAll('#revision-form .field.invalid').forEach((field) => { field.classList.remove('invalid'); field.querySelector('.field-error').hidden = true; }); }
function showErrors(errors) {
  const leftovers = [];
  Object.entries(errors).forEach(([path, message]) => {
    const match = path.match(/^lines\[(\d+)]\.(\w+)$/); let field;
    if (match) field = [...$('revision-lines').children].filter((line) => !line.querySelector('[name="cancelled"]')?.checked)[Number(match[1])]?.querySelector(`[data-key="${match[2]}"]`);
    else field = document.querySelector(`#revision-form .field[data-key="${path}"]`);
    const slot = field?.querySelector('.field-error'); if (!slot) leftovers.push(`${path}: ${message}`); else { field.classList.add('invalid'); slot.textContent = message; slot.hidden = false; }
  });
  document.querySelector('#revision-form .field.invalid')?.scrollIntoView({ behavior: 'smooth', block: 'center' }); return leftovers;
}
function idempotencyKey(payload) {
  const fingerprint = `${state.actor.orgId}\n${state.actor.userId}\n${state.poId}\n${payload}`;
  if (state.attempt?.fingerprint !== fingerprint) state.attempt = { fingerprint, key: newUuid() };
  return state.attempt.key;
}
async function submitRevision(event) {
  event.preventDefault(); if (state.submitting) return; clearErrors(); hideAlert('form-alert');
  const { body, errors } = readBody();
  if (Object.keys(errors).length) { const rest = showErrors(errors); return showAlert('form-alert', rest.join(' / ') || '표시된 입력 항목을 확인하세요.'); }
  const payload = toJson(body); state.submitting = true; $('submit-btn').disabled = true; $('submit-btn').textContent = '저장 중…';
  try {
    const created = await api('POST', `/v1/purchase-orders/${state.poId}/revisions`, payload, { 'Idempotency-Key': idempotencyKey(payload), 'X-Request-Id': newUuid() });
    toast(`개정 ${created.revision} 초안을 저장했습니다.`, 'success'); $('f-expectedVersion').value = created.poVersion; await refreshHistory(); await loadDetail(created.revision);
  } catch (error) {
    if (error.code === 'INVALID_REQUEST') {
      const parsed = {}; error.message.split('; ').forEach((part) => { const m = part.match(/^([A-Za-z0-9_\-[\].]+): (.+)$/); if (m) parsed[m[1]] = m[2]; });
      showErrors(parsed);
    }
    showAlert('form-alert', `${describeError(error)}${error.status === 0 || error.status >= 500 ? ' 같은 내용으로 다시 저장하면 중복 없이 처리됩니다.' : ''}`);
  } finally { state.submitting = false; $('submit-btn').disabled = false; $('submit-btn').textContent = '개정 초안 저장'; }
}

setupActor();
$('lookup-form').addEventListener('submit', lookup);
$('refresh-history').addEventListener('click', refreshHistory);
$('add-revision-line').addEventListener('click', () => addRevisionLine().querySelector('[name="itemId"]').focus());
$('restore-current').addEventListener('click', () => { if (state.currentDetail) fillRevisionForm(state.currentDetail.afterSnapshot); });
$('revision-form').addEventListener('submit', submitRevision);
$('revision-form').addEventListener('input', (event) => { const field = event.target.closest('.field'); if (field?.classList.contains('invalid')) { field.classList.remove('invalid'); field.querySelector('.field-error').hidden = true; } });
let masterReady = loadMaster();
