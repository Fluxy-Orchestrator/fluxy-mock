const API_BASE = '/api/mock/admin';

const state = {
  currentEndpointId: null,
  endpoints: []
};

const $ = (id) => document.getElementById(id);

const elements = {
  endpointList: $('endpointList'),
  endpointCount: $('endpointCount'),
  status: $('status'),
  editorTitle: $('editorTitle'),
  endpointId: $('endpointId'),
  triggerTypeHelp: $('triggerTypeHelp'),
  name: $('name'),
  httpMethod: $('httpMethod'),
  triggerType: $('triggerType'),
  mode: $('mode'),
  pathPattern: $('pathPattern'),
  targetBaseUrl: $('targetBaseUrl'),
  httpTriggerConfig: $('httpTriggerConfig'),
  eventTriggerConfig: $('eventTriggerConfig'),
  triggerBinding: $('triggerBinding'),
  eventTargetType: $('eventTargetType'),
  eventTargetDestination: $('eventTargetDestination'),
  enabled: $('enabled'),
  responseJsonFieldOverrides: $('responseJsonFieldOverrides'),
  responses: $('responses'),
  mockForm: $('mockForm'),
  saveMockBtn: $('saveMockBtn'),
  deleteMockBtn: $('deleteMockBtn'),
  newMockBtn: $('newMockBtn'),
  addResponseBtn: $('addResponseBtn'),
  refreshListBtn: $('refreshListBtn')
};

const HTTP_TRIGGER_HELP = 'HTTP usa la request entrante; EVENT muestra campos adicionales de mensajería.';
const EVENT_TRIGGER_HELP = 'EVENT necesita el binding de origen y el destino al que se publicará la respuesta.';

const DEFAULT_RESPONSE = () => ({
  description: '',
  active: true,
  httpStatus: 200,
  responseHeaders: {},
  bodyTemplate: '',
  latencyMs: 0,
  requestMatcher: null,
  postActions: []
});

const DEFAULT_ENDPOINT = () => ({
  name: '',
  httpMethod: 'GET',
  triggerType: 'HTTP',
  mode: 'STATIC_JSON',
  responseJsonFieldOverrides: {},
  pathPattern: '',
  triggerBinding: '',
  eventTargetType: 'SQS',
  eventTargetDestination: '',
  targetBaseUrl: '',
  enabled: true,
  responses: [DEFAULT_RESPONSE()]
});

function setStatus(message, kind = '') {
  elements.status.textContent = message || '';
  elements.status.className = `status ${kind}`.trim();
}

function prettyJson(value) {
  return JSON.stringify(value ?? {}, null, 2);
}

function parseJsonField(value, fallback) {
  if (!value || !value.trim()) {
    return fallback;
  }
  return JSON.parse(value);
}

function clearResponses() {
  elements.responses.innerHTML = '';
}

function updateTriggerVisibility(triggerType) {
  const isEvent = triggerType === 'EVENT';
  elements.httpTriggerConfig.hidden = isEvent;
  elements.eventTriggerConfig.hidden = !isEvent;
  elements.triggerTypeHelp.textContent = isEvent ? EVENT_TRIGGER_HELP : HTTP_TRIGGER_HELP;
}

function createPostActionElement(data = {}) {
  const template = $('postActionTemplate');
  const node = template.content.firstElementChild.cloneNode(true);

  node.querySelector('[data-field="delayMs"]').value = data.delayMs ?? 0;
  node.querySelector('[data-field="sqsQueueUrl"]').value = data.sqsQueueUrl ?? '';
  node.querySelector('[data-field="sqsMessageTemplate"]').value = data.sqsMessageTemplate ?? '';

  node.querySelector('.remove-post-action').addEventListener('click', () => node.remove());
  return node;
}

function createResponseCard(data = DEFAULT_RESPONSE()) {
  const template = $('responseTemplate');
  const node = template.content.firstElementChild.cloneNode(true);

  node.querySelector('[data-field="description"]').value = data.description ?? '';
  node.querySelector('[data-field="active"]').checked = data.active ?? false;
  node.querySelector('[data-field="httpStatus"]').value = data.httpStatus ?? 200;
  node.querySelector('[data-field="latencyMs"]').value = data.latencyMs ?? 0;
  node.querySelector('[data-field="bodyTemplate"]').value = data.bodyTemplate ?? '';
  node.querySelector('[data-json-field="responseHeaders"]').value = prettyJson(data.responseHeaders ?? {});

  const matcher = data.requestMatcher ?? {};
  node.querySelector('[data-json-field="matchHeaders"]').value = prettyJson(matcher.matchHeaders ?? {});
  node.querySelector('[data-json-field="matchQueryParams"]').value = prettyJson(matcher.matchQueryParams ?? {});
  node.querySelector('[data-field="matchBodyContains"]').value = matcher.matchBodyContains ?? '';
  node.querySelector('[data-json-field="matchPathVariables"]').value = prettyJson(matcher.matchPathVariables ?? {});

  const postActionsContainer = node.querySelector('.post-actions');
  const actions = data.postActions ?? [];
  if (actions.length === 0) {
    postActionsContainer.appendChild(createPostActionElement());
  } else {
    actions.forEach((action) => postActionsContainer.appendChild(createPostActionElement(action)));
  }

  node.querySelector('.response-remove').addEventListener('click', () => node.remove());
  node.querySelector('.add-post-action').addEventListener('click', () => {
    postActionsContainer.appendChild(createPostActionElement());
  });

  return node;
}

function serializeResponseCard(card) {
  const description = card.querySelector('[data-field="description"]').value.trim();
  const active = card.querySelector('[data-field="active"]').checked;
  const httpStatus = Number(card.querySelector('[data-field="httpStatus"]').value || 200);
  const latencyMs = Number(card.querySelector('[data-field="latencyMs"]').value || 0);
  const bodyTemplate = card.querySelector('[data-field="bodyTemplate"]').value || null;
  const responseHeaders = parseJsonField(card.querySelector('[data-json-field="responseHeaders"]').value, {});

  const matcher = {
    matchHeaders: parseJsonField(card.querySelector('[data-json-field="matchHeaders"]').value, {}),
    matchQueryParams: parseJsonField(card.querySelector('[data-json-field="matchQueryParams"]').value, {}),
    matchBodyContains: card.querySelector('[data-field="matchBodyContains"]').value.trim(),
    matchPathVariables: parseJsonField(card.querySelector('[data-json-field="matchPathVariables"]').value, {})
  };

  const hasMatcherData = Object.values(matcher.matchHeaders).length > 0
    || Object.values(matcher.matchQueryParams).length > 0
    || matcher.matchBodyContains.length > 0
    || Object.values(matcher.matchPathVariables).length > 0;

  const postActions = Array.from(card.querySelectorAll('.post-action')).map((action) => ({
    delayMs: Number(action.querySelector('[data-field="delayMs"]').value || 0),
    sqsQueueUrl: action.querySelector('[data-field="sqsQueueUrl"]').value.trim() || null,
    sqsMessageTemplate: action.querySelector('[data-field="sqsMessageTemplate"]').value || null
  }));

  return {
    description: description || null,
    active,
    httpStatus,
    responseHeaders,
    bodyTemplate,
    latencyMs,
    requestMatcher: hasMatcherData ? matcher : null,
    postActions: postActions.filter((action) => action.sqsQueueUrl || action.sqsMessageTemplate)
  };
}

function renderEndpointList() {
  elements.endpointList.innerHTML = '';
  elements.endpointCount.textContent = `${state.endpoints.length} endpoint${state.endpoints.length === 1 ? '' : 's'}`;

  if (state.endpoints.length === 0) {
    const empty = document.createElement('p');
    empty.textContent = 'Todavía no hay mocks creados.';
    empty.className = 'subtitle';
    elements.endpointList.appendChild(empty);
    return;
  }

  state.endpoints.forEach((endpoint) => {
    const item = document.createElement('article');
    item.className = `endpoint-item ${state.currentEndpointId === endpoint.id ? 'active' : ''}`;

    const responsesCount = endpoint.responses ? endpoint.responses.length : 0;
    item.innerHTML = `
      <h4>${endpoint.name || 'Sin nombre'} #${endpoint.id}</h4>
      <p>${endpoint.httpMethod} · ${endpoint.pathPattern} · ${endpoint.mode} · ${responsesCount} response${responsesCount === 1 ? '' : 's'}</p>
      <p>${endpoint.enabled ? 'Activo' : 'Desactivado'}</p>
      <div class="endpoint-actions">
        <button type="button" class="secondary">Editar</button>
        <button type="button" class="secondary">${endpoint.enabled ? 'Desactivar' : 'Activar'}</button>
      </div>
    `;

    const buttons = item.querySelectorAll('button');
    buttons[0].addEventListener('click', () => loadEndpoint(endpoint.id));
    buttons[1].addEventListener('click', async () => {
      await toggleEndpoint(endpoint.id);
    });

    elements.endpointList.appendChild(item);
  });
}

function readFormData() {
  const triggerType = elements.triggerType.value;
  const isEventTrigger = triggerType === 'EVENT';
  const httpMethod = isEventTrigger ? null : elements.httpMethod.value;

  return {
    name: elements.name.value.trim() || null,
    httpMethod,
    triggerType,
    mode: elements.mode.value,
    responseJsonFieldOverrides: parseJsonField(elements.responseJsonFieldOverrides.value, {}),
    pathPattern: elements.pathPattern.value.trim(),
    triggerBinding: isEventTrigger ? (elements.triggerBinding.value.trim() || null) : null,
    eventTargetType: isEventTrigger ? elements.eventTargetType.value : null,
    eventTargetDestination: isEventTrigger ? (elements.eventTargetDestination.value.trim() || null) : null,
    targetBaseUrl: elements.targetBaseUrl.value.trim() || null,
    enabled: elements.enabled.checked,
    responses: Array.from(elements.responses.querySelectorAll('.response-card')).map(serializeResponseCard)
  };
}

function renderEndpointForm(endpoint) {
  const data = endpoint || DEFAULT_ENDPOINT();
  state.currentEndpointId = endpoint?.id ?? null;

  elements.endpointId.value = data.id ?? '';
  elements.name.value = data.name ?? '';
  elements.triggerType.value = data.triggerType ?? 'HTTP';
  updateTriggerVisibility(elements.triggerType.value);
  elements.httpMethod.value = data.httpMethod ?? 'GET';
  elements.mode.value = data.mode ?? 'STATIC_JSON';
  elements.pathPattern.value = data.pathPattern ?? '';
  elements.targetBaseUrl.value = data.targetBaseUrl ?? '';
  elements.triggerBinding.value = data.triggerBinding ?? '';
  elements.eventTargetType.value = data.eventTargetType ?? 'SQS';
  elements.eventTargetDestination.value = data.eventTargetDestination ?? '';
  elements.enabled.checked = data.enabled ?? true;
  elements.responseJsonFieldOverrides.value = prettyJson(data.responseJsonFieldOverrides ?? {});

  clearResponses();
  const responses = data.responses && data.responses.length > 0 ? data.responses : [DEFAULT_RESPONSE()];
  responses.forEach((response) => elements.responses.appendChild(createResponseCard(response)));

  elements.editorTitle.textContent = endpoint ? `Editando mock #${endpoint.id}` : 'Nuevo mock';
  elements.deleteMockBtn.disabled = !endpoint;
  renderEndpointList();
}

async function fetchJson(url, options) {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  });

  const isJson = response.headers.get('content-type')?.includes('application/json');
  const body = isJson ? await response.json() : await response.text();

  if (!response.ok) {
    const message = typeof body === 'string' ? body : (body.message || response.statusText);
    throw new Error(message);
  }

  return body;
}

async function loadEndpoints() {
  state.endpoints = await fetchJson(`${API_BASE}/endpoints`);
  renderEndpointList();
}

async function loadEndpoint(id) {
  setStatus(`Cargando mock #${id}...`);
  const endpoint = await fetchJson(`${API_BASE}/endpoints/${id}`);
  renderEndpointForm(endpoint);
  setStatus(`Mock #${id} listo para editar.`, 'success');
}

async function toggleEndpoint(id) {
  await fetchJson(`${API_BASE}/endpoints/${id}/toggle`, { method: 'PATCH' });
  await loadEndpoints();
  if (state.currentEndpointId === id) {
    await loadEndpoint(id);
  }
}

async function deleteEndpoint() {
  if (!state.currentEndpointId) {
    return;
  }

  if (!confirm(`Eliminar el mock #${state.currentEndpointId}?`)) {
    return;
  }

  await fetchJson(`${API_BASE}/endpoints/${state.currentEndpointId}`, { method: 'DELETE' });
  setStatus('Mock eliminado.', 'success');
  renderEndpointForm(null);
  await loadEndpoints();
}

async function saveEndpoint() {
  try {
    const payload = readFormData();
    if (!payload.pathPattern) {
      throw new Error('pathPattern es obligatorio.');
    }

    setStatus('Guardando mock...');

    const url = state.currentEndpointId
      ? `${API_BASE}/endpoints/${state.currentEndpointId}`
      : `${API_BASE}/full`;
    const method = state.currentEndpointId ? 'PUT' : 'POST';

    const saved = await fetchJson(url, {
      method,
      body: JSON.stringify(payload)
    });

    setStatus('Mock guardado correctamente.', 'success');
    await loadEndpoints();
    renderEndpointForm(saved);
  } catch (error) {
    setStatus(error.message, 'error');
  }
}

function newMock() {
  renderEndpointForm(null);
  setStatus('Preparado para crear un mock nuevo.');
}

async function bootstrap() {
  elements.saveMockBtn.addEventListener('click', saveEndpoint);
  elements.deleteMockBtn.addEventListener('click', deleteEndpoint);
  elements.newMockBtn.addEventListener('click', newMock);
  elements.refreshListBtn.addEventListener('click', loadEndpoints);
  elements.triggerType.addEventListener('change', () => {
    updateTriggerVisibility(elements.triggerType.value);
  });
  elements.addResponseBtn.addEventListener('click', () => {
    elements.responses.appendChild(createResponseCard());
  });

  elements.mockForm.addEventListener('submit', (event) => {
    event.preventDefault();
    saveEndpoint();
  });

  renderEndpointForm(null);
  await loadEndpoints();
}

bootstrap().catch((error) => {
  setStatus(error.message, 'error');
});
