const ACTOR_STORAGE_KEY = 'procureflow.actor';
const DEFAULT_ACTOR = { orgId: 'Org-001', userId: 'User-001' };
const MAX_ACTOR_ID_LENGTH = 64;
const TOAST_MS = 3500;
const FLASH_MS = 2400;

const STATUS_LABEL = { ACTIVE: '활성', INACTIVE: '비활성' };

const ERROR_HINTS = {
  AUTH_REQUIRED: '조직 ID·사용자 ID 를 확인하세요. 헤더 인증은 백엔드 local 프로파일에서만 켜져 있습니다.',
  NETWORK_ERROR: '서버에 연결할 수 없습니다. front 서버(node front/server.mjs)와 백엔드가 떠 있는지 확인하세요.',
  API_UNREACHABLE: '백엔드 기동 여부와 API_TARGET 을 확인하세요.',
};

const dateFormat = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'short' });

const RESOURCES = [
  {
    id: 'plants',
    label: '사업장',
    path: '/v1/plants',
    codeKey: 'plantCode',
    description: '발주·입고 기준이 되는 사업장입니다. 코드는 조직 안에서 유일해야 합니다.',
    fields: [
      { key: 'plantCode', label: '사업장 코드', type: 'text', maxLength: 32, required: true, mono: true, placeholder: '예) PLANT-01' },
      { key: 'name', label: '사업장명', type: 'text', maxLength: 255, required: true, placeholder: '예) 화성 1공장' },
      { key: 'status', label: '상태', type: 'status' },
    ],
    columns: [
      { label: '코드', render: (r) => mono(r.plantCode) },
      { label: '이름', render: (r) => r.name },
      { label: '상태', render: (r) => statusBadge(r.status) },
      { label: '등록', render: (r) => auditCell(r) },
      { label: 'ID', render: (r) => idCell(r.id) },
    ],
  },
  {
    id: 'warehouses',
    label: '창고',
    path: '/v1/warehouses',
    codeKey: 'warehouseCode',
    description: '발주 입고지로 쓰는 창고입니다. 같은 조직의 사업장에 소속되어야 합니다.',
    fields: [
      { key: 'plantId', label: '소속 사업장', type: 'plant', required: true },
      { key: 'warehouseCode', label: '창고 코드', type: 'text', maxLength: 32, required: true, mono: true, placeholder: '예) WH-RAW-01' },
      { key: 'name', label: '창고명', type: 'text', maxLength: 255, required: true, placeholder: '예) 원자재 창고' },
      { key: 'status', label: '상태', type: 'status' },
    ],
    columns: [
      { label: '코드', render: (r) => mono(r.warehouseCode) },
      { label: '이름', render: (r) => r.name },
      { label: '소속 사업장', render: (r) => plantCell(r.plantId) },
      { label: '상태', render: (r) => statusBadge(r.status) },
      { label: '등록', render: (r) => auditCell(r) },
      { label: 'ID', render: (r) => idCell(r.id) },
    ],
  },
  {
    id: 'suppliers',
    label: '공급사',
    path: '/v1/suppliers',
    codeKey: 'supplierCode',
    description: '발주를 받는 공급사입니다. 비활성 공급사로는 발주할 수 없습니다.',
    fields: [
      { key: 'supplierCode', label: '공급사 코드', type: 'text', maxLength: 64, required: true, mono: true, placeholder: '예) SUP-0001' },
      { key: 'name', label: '공급사명', type: 'text', maxLength: 255, required: true, placeholder: '예) (주)호풍산업' },
      { key: 'status', label: '상태', type: 'status' },
    ],
    columns: [
      { label: '코드', render: (r) => mono(r.supplierCode) },
      { label: '이름', render: (r) => r.name },
      { label: '상태', render: (r) => statusBadge(r.status) },
      { label: '등록', render: (r) => auditCell(r) },
      { label: 'ID', render: (r) => idCell(r.id) },
    ],
  },
  {
    id: 'items',
    label: '품목',
    path: '/v1/items',
    codeKey: 'itemCode',
    description: '발주 대상 품목입니다. 활성이면서 구매 가능인 품목만 발주할 수 있습니다.',
    fields: [
      { key: 'itemCode', label: '품목 코드', type: 'text', maxLength: 64, required: true, mono: true, placeholder: '예) ITEM-BOLT-M8' },
      { key: 'name', label: '품목명', type: 'text', maxLength: 255, required: true, placeholder: '예) 육각 볼트 M8' },
      { key: 'status', label: '상태', type: 'status' },
      { key: 'purchaseEnabled', label: '구매 가능', type: 'checkbox', defaultValue: true, hint: '해제하면 이 품목으로 발주할 수 없습니다.' },
    ],
    columns: [
      { label: '코드', render: (r) => mono(r.itemCode) },
      { label: '이름', render: (r) => r.name },
      { label: '상태', render: (r) => statusBadge(r.status) },
      { label: '구매 가능', render: (r) => yesNoBadge(r.purchaseEnabled) },
      { label: '발주 가능', render: (r) => yesNoBadge(r.status === 'ACTIVE' && r.purchaseEnabled) },
      { label: '등록', render: (r) => auditCell(r) },
      { label: 'ID', render: (r) => idCell(r.id) },
    ],
  },
];

const RESOURCE_BY_ID = Object.fromEntries(RESOURCES.map((r) => [r.id, r]));

const state = {
  actor: loadActor(),
  activeId: RESOURCES[0].id,
  data: {},
  loadErrors: {},
  loading: {},
  flashId: null,
  submitting: false,
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

async function api(method, path, body) {
  const headers = { 'X-Org-Id': state.actor.orgId, 'X-User-Id': state.actor.userId };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  let res;
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
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
  const form = $('actor-form');
  $('actor-org').value = state.actor.orgId;
  $('actor-user').value = state.actor.userId;

  form.addEventListener('submit', (event) => {
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
    state.data = {};
    state.loadErrors = {};
    toast(`조직 ${orgId} 기준으로 다시 불러옵니다.`);
    renderForm();
    loadAll();
  });
}

// ---------- 데이터 ----------

async function load(resourceId) {
  const resource = RESOURCE_BY_ID[resourceId];
  state.loading[resourceId] = true;
  if (resourceId === state.activeId) {
    renderList();
  }
  try {
    state.data[resourceId] = await api('GET', resource.path);
    delete state.loadErrors[resourceId];
  } catch (error) {
    state.data[resourceId] = null;
    state.loadErrors[resourceId] = error;
  } finally {
    state.loading[resourceId] = false;
  }
  renderTabs();
  if (resourceId === state.activeId) {
    renderList();
  }
  if (resourceId === 'plants') {
    refreshPlantOptions();
    if (state.activeId === 'warehouses') {
      renderList();
    }
  }
}

function loadAll() {
  return Promise.all(RESOURCES.map((r) => load(r.id)));
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

function mono(text) {
  return el('span', { class: 'mono' }, text);
}

function statusBadge(status) {
  return el('span', { class: `badge ${status === 'ACTIVE' ? 'active' : 'inactive'}` }, STATUS_LABEL[status] ?? status);
}

function yesNoBadge(value) {
  return el('span', { class: `badge ${value ? 'active' : 'danger'}` }, value ? '가능' : '불가');
}

function auditCell(row) {
  return el('span', {}, formatDate(row.createdAt), el('span', { class: 'cell-sub' }, row.createdBy));
}

function idCell(id) {
  return el('button', {
    type: 'button',
    class: 'btn btn-ghost mono',
    title: `${id} (클릭하여 복사)`,
    onclick: () => copyText(id),
  }, `${id.slice(0, 8)}…`);
}

function plantCell(plantId) {
  const plant = (state.data.plants ?? []).find((p) => p.id === plantId);
  if (!plant) {
    return el('span', { class: 'mono' }, `${plantId.slice(0, 8)}…`);
  }
  return el('span', {}, plant.name, el('span', { class: 'cell-sub mono' }, plant.plantCode));
}

function formatDate(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateFormat.format(date);
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    toast('ID 를 복사했습니다.', 'success');
  } catch {
    toast(text);
  }
}

function toast(message, type = 'info') {
  const node = el('div', { class: `toast ${type}` }, message);
  $('toasts').append(node);
  setTimeout(() => node.remove(), TOAST_MS);
}

// ---------- 탭 ----------

function renderTabs() {
  const tabs = $('tabs');
  tabs.replaceChildren(...RESOURCES.map((resource) => {
    const rows = state.data[resource.id];
    return el('button', {
      type: 'button',
      class: 'tab',
      role: 'tab',
      'aria-selected': String(resource.id === state.activeId),
      onclick: () => {
        location.hash = resource.id;
      },
    }, resource.label, el('span', { class: 'tab-count' }, Array.isArray(rows) ? rows.length : '-'));
  }));
}

function activateFromHash() {
  const id = location.hash.replace('#', '');
  state.activeId = RESOURCE_BY_ID[id] ? id : RESOURCES[0].id;
  $('search').value = '';
  $('status-filter').value = '';
  renderTabs();
  renderForm();
  renderList();
}

// ---------- 등록 폼 ----------

function renderForm() {
  const resource = RESOURCE_BY_ID[state.activeId];
  $('form-title').textContent = `${resource.label} 등록`;
  $('form-desc').textContent = resource.description;
  $('form-fields').replaceChildren(...resource.fields.map(renderField));
  $('submit-btn').disabled = state.submitting;
  hideAlert();
  refreshPlantOptions();
}

function renderField(field) {
  const id = `f-${field.key}`;
  const wrap = el('div', { class: 'field', dataset: { key: field.key } });

  if (field.type === 'checkbox') {
    wrap.append(
      el('label', { class: 'switch', for: id },
        el('input', { id, name: field.key, type: 'checkbox', checked: field.defaultValue, defaultChecked: field.defaultValue }),
        el('span', { class: 'field-label' }, field.label)),
      field.hint && el('span', { class: 'field-hint' }, field.hint),
    );
    return wrap;
  }

  wrap.append(el('label', { class: 'field-label', for: id }, field.label, field.required && el('span', { class: 'req' }, '*')));

  if (field.type === 'status') {
    wrap.append(el('div', { class: 'segmented', role: 'radiogroup', 'aria-label': field.label },
      Object.entries(STATUS_LABEL).map(([value, label], index) => el('label', {},
        el('input', { type: 'radio', name: field.key, value, checked: index === 0, defaultChecked: index === 0 }),
        label))));
    return wrap;
  }

  if (field.type === 'plant') {
    wrap.append(el('select', { id, name: field.key, class: 'input', required: true }));
    wrap.append(el('div', { class: 'field-meta' }, el('span', { class: 'field-error', hidden: true })));
    return wrap;
  }

  const input = el('input', {
    id,
    name: field.key,
    type: 'text',
    class: `input${field.mono ? ' mono' : ''}`,
    maxlength: String(field.maxLength),
    placeholder: field.placeholder,
    autocomplete: 'off',
    required: field.required,
  });
  const counter = el('span', { class: 'field-counter' }, `0 / ${field.maxLength}`);
  input.addEventListener('input', () => {
    counter.textContent = `${input.value.length} / ${field.maxLength}`;
    clearFieldError(field.key);
  });
  wrap.append(input, el('div', { class: 'field-meta' }, el('span', { class: 'field-error', hidden: true }), counter));
  return wrap;
}

function refreshPlantOptions() {
  const select = document.querySelector('#create-form select[name="plantId"]');
  if (!select) {
    return;
  }
  const previous = select.value;
  const plants = state.data.plants;
  const submit = $('submit-btn');

  if (!Array.isArray(plants)) {
    select.replaceChildren(el('option', { value: '' }, state.loadErrors.plants ? '사업장을 불러오지 못했습니다' : '불러오는 중…'));
    select.disabled = true;
    return;
  }
  if (plants.length === 0) {
    select.replaceChildren(el('option', { value: '' }, '등록된 사업장이 없습니다'));
    select.disabled = true;
    submit.disabled = true;
    showAlert('창고를 등록하려면 먼저 사업장을 등록하세요. ', 'info', el('a', { href: '#plants' }, '사업장 등록으로 이동'));
    return;
  }
  select.disabled = false;
  submit.disabled = state.submitting;
  if ($('form-alert').classList.contains('info')) {
    hideAlert();
  }
  select.replaceChildren(
    el('option', { value: '' }, '사업장 선택'),
    ...plants.map((p) => el('option', { value: p.id },
      `${p.plantCode} · ${p.name}${p.status === 'ACTIVE' ? '' : ' (비활성)'}`)),
  );
  if (plants.some((p) => p.id === previous)) {
    select.value = previous;
  }
  select.onchange = () => clearFieldError('plantId');
}

function readForm(resource, form) {
  const body = {};
  for (const field of resource.fields) {
    const control = form.elements[field.key];
    if (field.type === 'checkbox') {
      body[field.key] = control.checked;
    } else if (field.type === 'status' || field.type === 'plant') {
      body[field.key] = control.value;
    } else {
      body[field.key] = control.value.trim();
    }
  }
  return body;
}

function validateForm(resource, body) {
  const errors = {};
  for (const field of resource.fields) {
    const value = body[field.key];
    if (field.required && !value) {
      errors[field.key] = field.type === 'plant' ? '소속 사업장을 선택하세요.' : `${field.label}을(를) 입력하세요.`;
    } else if (field.maxLength && value.length > field.maxLength) {
      errors[field.key] = `${field.maxLength}자 이하로 입력하세요.`;
    }
  }
  return errors;
}

function setFieldError(key, message) {
  const wrap = document.querySelector(`#form-fields .field[data-key="${key}"]`);
  const slot = wrap?.querySelector('.field-error');
  if (!wrap || !slot) {
    return false;
  }
  wrap.classList.add('invalid');
  slot.textContent = message;
  slot.hidden = false;
  return true;
}

function clearFieldError(key) {
  const wrap = document.querySelector(`#form-fields .field[data-key="${key}"]`);
  wrap?.classList.remove('invalid');
  const slot = wrap?.querySelector('.field-error');
  if (slot) {
    slot.hidden = true;
  }
}

function clearAllFieldErrors() {
  document.querySelectorAll('#form-fields .field').forEach((wrap) => clearFieldError(wrap.dataset.key));
}

function showAlert(message, type = 'error', ...extra) {
  const alert = $('form-alert');
  alert.className = `alert${type === 'info' ? ' info' : ''}`;
  alert.replaceChildren(message, ...extra);
  alert.hidden = false;
}

function hideAlert() {
  $('form-alert').hidden = true;
}

/** 서버 400 메시지는 "field: 사유; field: 사유" 형식이다. 아는 필드는 필드 옆에, 나머지는 알림에 보여준다. */
function applyServerError(error) {
  if (error.code !== 'INVALID_REQUEST') {
    showAlert(describeError(error));
    return;
  }
  const leftovers = [];
  for (const part of error.message.split('; ')) {
    const match = part.match(/^([A-Za-z]+): (.+)$/);
    if (!match || !setFieldError(match[1], match[2])) {
      leftovers.push(part);
    }
  }
  if (leftovers.length > 0) {
    showAlert(leftovers.join(' / '));
  }
}

async function onSubmit(event) {
  event.preventDefault();
  if (state.submitting) {
    return;
  }
  const resource = RESOURCE_BY_ID[state.activeId];
  const form = event.currentTarget;
  const body = readForm(resource, form);

  clearAllFieldErrors();
  hideAlert();
  const errors = validateForm(resource, body);
  if (Object.keys(errors).length > 0) {
    Object.entries(errors).forEach(([key, message]) => setFieldError(key, message));
    return;
  }

  const submit = $('submit-btn');
  state.submitting = true;
  submit.disabled = true;
  submit.textContent = '등록 중…';
  try {
    const created = await api('POST', resource.path, body);
    toast(`${resource.label} '${created[resource.codeKey]}' 을(를) 등록했습니다.`, 'success');
    state.flashId = created.id;
    form.reset();
    await load(resource.id);
  } catch (error) {
    applyServerError(error);
  } finally {
    state.submitting = false;
    submit.textContent = '등록';
    submit.disabled = false;
    if (resource.id === 'warehouses') {
      refreshPlantOptions();
    }
  }
}

function setupCreateForm() {
  const form = $('create-form');
  form.addEventListener('submit', onSubmit);
  form.addEventListener('reset', () => {
    clearAllFieldErrors();
    hideAlert();
    // reset 이벤트 뒤에 값이 기본값으로 돌아가므로 다음 틱에 글자 수를 맞춘다.
    setTimeout(() => {
      form.querySelectorAll('input[type="text"]').forEach((input) => input.dispatchEvent(new Event('input')));
      refreshPlantOptions();
    });
  });
}

// ---------- 목록 ----------

function filteredRows(resource) {
  const rows = state.data[resource.id] ?? [];
  const keyword = $('search').value.trim().toLowerCase();
  const status = $('status-filter').value;
  return rows.filter((row) => {
    if (status && row.status !== status) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return String(row[resource.codeKey]).toLowerCase().includes(keyword)
      || String(row.name).toLowerCase().includes(keyword);
  });
}

function renderList() {
  const resource = RESOURCE_BY_ID[state.activeId];
  const rows = state.data[resource.id];
  const error = state.loadErrors[resource.id];
  const empty = $('list-empty');

  $('list-title').textContent = `${resource.label} 목록`;
  $('list-head').replaceChildren(el('tr', {}, resource.columns.map((c) => el('th', {}, c.label))));

  if (!Array.isArray(rows)) {
    $('list-body').replaceChildren();
    $('list-count').textContent = `조직 ${state.actor.orgId}`;
    empty.className = `empty${error ? ' error' : ''}`;
    empty.textContent = error ? describeError(error) : '불러오는 중…';
    empty.hidden = false;
    return;
  }

  const visible = filteredRows(resource);
  $('list-count').textContent = `조직 ${state.actor.orgId} · 전체 ${rows.length}건${visible.length !== rows.length ? ` · 표시 ${visible.length}건` : ''}`;
  $('list-body').replaceChildren(...visible.map((row) => {
    const tr = el('tr', { class: row.id === state.flashId ? 'flash' : null },
      resource.columns.map((column) => el('td', {}, column.render(row))));
    return tr;
  }));
  if (state.flashId && visible.some((row) => row.id === state.flashId)) {
    setTimeout(() => {
      state.flashId = null;
    }, FLASH_MS);
  }

  empty.className = 'empty';
  if (rows.length === 0) {
    empty.textContent = `등록된 ${resource.label}이(가) 없습니다. 왼쪽 폼에서 등록하세요.`;
    empty.hidden = false;
  } else if (visible.length === 0) {
    empty.textContent = '조건에 맞는 항목이 없습니다.';
    empty.hidden = false;
  } else {
    empty.hidden = true;
  }
}

function setupListToolbar() {
  $('search').addEventListener('input', renderList);
  $('status-filter').addEventListener('change', renderList);
  $('refresh-btn').addEventListener('click', () => {
    load(state.activeId);
    if (state.activeId === 'warehouses') {
      load('plants');
    }
  });
}

// ---------- 시작 ----------

setupActorForm();
setupCreateForm();
setupListToolbar();
window.addEventListener('hashchange', activateFromHash);
activateFromHash();
loadAll();
