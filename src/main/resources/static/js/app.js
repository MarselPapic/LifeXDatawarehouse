/* ===================== Configuration ===================== */
const API = {
    progress: '/api/index-progress',   // returns IndexProgress.Status
    reindex:  '/api/index/reindex',    // POST → manual reindex
    search:   '/search',               // GET /search?q=...
    suggest:  '/search/suggest',       // GET /search/suggest?q=...
    table:    '/table',                // GET /table/{name}
};

/* ===================== DOM references ===================== */
const resultArea  = document.getElementById('resultArea');
const searchInput = document.getElementById('search-input');
const searchBtn   = document.getElementById('search-btn');

const idxBox  = document.getElementById('idx-box');
const idxBar  = document.querySelector('#idx-bar > span');
const idxText = document.getElementById('idx-text');
const idxBtnSide = document.getElementById('idx-reindex-side');

const sugList = document.getElementById('sug');

/* ===================== Utils ===================== */
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const debounce = (fn, ms=250) => { let t; return (...a)=>{clearTimeout(t); t=setTimeout(()=>fn(...a),ms);} };
const stGet = (k, d) => { try { const v = localStorage.getItem(k); return v === null ? d : v; } catch { return d; } };
const stSet = (k, v) => { try { localStorage.setItem(k, v); } catch {} };
function escapeHtml(s){ return (s??'').replace(/[&<>"']/g, c=>({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c])); }
function setBusy(el, busy){ if(!el) return; busy ? el.setAttribute('aria-busy','true') : el.removeAttribute('aria-busy'); }

const shortcutCache = new Map();

const shortcutStorage = (() => {
    const prefix = 'sc:';
    const keyFor = (id, kind) => `${prefix}${id}:${kind}`;
    const read = (key, fallback) => {
        try {
            const stored = localStorage.getItem(key);
            return stored === null ? fallback : stored;
        } catch {
            return fallback;
        }
    };
    const write = (key, value) => {
        try {
            if (value === undefined || value === null) {
                localStorage.removeItem(key);
            } else {
                localStorage.setItem(key, value);
            }
        } catch {}
    };
    return {
        getLabel(id, fallback = '') {
            return read(keyFor(id, 'label'), fallback);
        },
        setLabel(id, value) {
            write(keyFor(id, 'label'), (value ?? '').toString());
        },
        getQuery(id, fallback = '') {
            return read(keyFor(id, 'query'), fallback);
        },
        setQuery(id, value) {
            write(keyFor(id, 'query'), (value ?? '').toString());
        },
    };
})();

if (resultArea) {
    resultArea.addEventListener('click', (event) => {
        let target = event.target;
        if (target && typeof target.closest !== 'function' && target.parentElement) {
            target = target.parentElement;
        }
        if (!target || typeof target.closest !== 'function') return;
        const button = target.closest('.table-quick-filter');
        if (!button) return;
        const query = button.dataset.query;
        if (!query) return;
        event.preventDefault();
        if (searchInput) {
            searchInput.value = query;
            if (typeof searchInput.focus === 'function') searchInput.focus();
        }
    });
}

function parseBool(value){
    if (typeof value === 'boolean') return value;
    const normalized = String(value ?? '').trim().toLowerCase();
    if (!normalized) return false;
    return ['true','1','yes','y','ja','wahr'].includes(normalized);
}

function normalizeLifecycleStatus(value){
    return (value ?? '').toString().trim().toUpperCase();
}

function isLifecycleStatusOperational(value){
    const status = normalizeLifecycleStatus(value);
    return status === 'ACTIVE' || status === 'MAINTENANCE';
}

function formatDateLabel(value){
    if (value === null || value === undefined) return '';
    const str = String(value).trim();
    if (!str) return '';
    const timestamp = Date.parse(str);
    if (!Number.isNaN(timestamp)){
    return new Date(timestamp).toLocaleDateString('en-GB', { year: 'numeric', month: '2-digit', day: '2-digit' });
    }
    return str.length > 16 ? str.slice(0, 16) : str;
}

function formatDateRange(start, end){
    const from = formatDateLabel(start);
    const to   = formatDateLabel(end);
    if (from && to) return `${from} → ${to}`;
    return from || to || '';
}

async function getShortcutItems(kind){
    if (!kind) return [];
    const key = kind.toString();
    if (!shortcutCache.has(key)){
        shortcutCache.set(key, loadShortcutItems(key).catch(err => { shortcutCache.delete(key); throw err; }));
    }
    return shortcutCache.get(key);
}

async function loadShortcutItems(kind){
    const key = (kind || '').toString().toLowerCase();
    switch (key){
        case 'projects-active': {
            const res = await fetch('/projects');
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const rows = await res.json();
            return rows
                .filter(row => isLifecycleStatusOperational(val(row,'LifecycleStatus','lifecycleStatus','lifecycle_status')))
                .map(row => {
                    const id       = val(row,'ProjectID');
                    const name     = val(row,'ProjectName');
                    const sap      = val(row,'ProjectSAPID');
                    const bundle   = val(row,'BundleType');
                    const variant  = val(row,'DeploymentVariantID');
                    const meta = [];
                    if (sap) meta.push(`SAP ${sap}`);
                    if (bundle) meta.push(bundle);
                    if (variant) meta.push(`Var. ${shortUuid(variant)}`);
                    const primary = (name && name.trim()) || (sap ? `Project ${sap}` : (id ? `Project ${shortUuid(id)}` : 'Project'));
                    return {
                        primary,
                        secondary: meta.join(' · ') || null,
                        action: id ? { type: 'details', entity: 'project', id } : null,
                    };
                })
                .sort((a, b) => (a.primary || '').localeCompare(b.primary || '', 'en', { sensitivity: 'base' }));
        }
        case 'servicecontracts-progress': {
            const res = await fetch(`${API.table}/servicecontract?limit=200`);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const rows = await res.json();
            return rows
                .filter(row => {
                    const status = (val(row,'Status') ?? '').toString().trim().toLowerCase();
                    return status === 'inprogress';
                })
                .map(row => {
                    const id        = val(row,'ContractID');
                    const number    = val(row,'ContractNumber');
                    const projectId = val(row,'ProjectID');
                    const siteId    = val(row,'SiteID');
                    const range     = formatDateRange(val(row,'StartDate'), val(row,'EndDate'));
                    const meta = [];
                    if (range) meta.push(range);
                    if (projectId) meta.push(`Project ${shortUuid(projectId)}`);
                    if (siteId) meta.push(`Site ${shortUuid(siteId)}`);
                    const primary = number ? `Contract ${number}` : (id ? `Contract ${shortUuid(id)}` : 'Contract');
                    const query   = id ? `id:"${id}"` : null;
                    return {
                        primary,
                        secondary: meta.join(' · ') || null,
                        action: query ? { type: 'search', query } : null,
                    };
                })
                .sort((a, b) => (a.primary || '').localeCompare(b.primary || '', 'en', { sensitivity: 'base' }));
        }
        case 'sites-bravo': {
            const res = await fetch('/sites');
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const rows = await res.json();
            return rows
                .filter(row => {
                    const zone = (val(row,'FireZone') ?? '').toString().trim().toLowerCase();
                    return zone === 'bravo';
                })
                .map(row => {
                    const id        = val(row,'SiteID');
                    const name      = val(row,'SiteName');
                    const tenants   = val(row,'TenantCount');
                    const projectId = val(row,'ProjectID');
                    const zone      = val(row,'FireZone');
                    const meta = [];
                    if (zone) meta.push(`Zone ${zone}`);
                    if (tenants !== undefined && tenants !== null && tenants !== '') meta.push(`${tenants} units`);
                    if (projectId) meta.push(`Project ${shortUuid(projectId)}`);
                    const primary = (name && name.trim()) || (id ? `Site ${shortUuid(id)}` : 'Site');
                    return {
                        primary,
                        secondary: meta.join(' · ') || null,
                        action: id ? { type: 'details', entity: 'site', id } : null,
                    };
                })
                .sort((a, b) => (a.primary || '').localeCompare(b.primary || '', 'en', { sensitivity: 'base' }));
        }
        default:
            return [];
    }
}

function renderShortcutItems(listEl, items){
    if (!listEl) return;
    if (!Array.isArray(items) || !items.length){
        listEl.innerHTML = '<p class="sc-status empty">(no entries)</p>';
        return;
    }
    const summary = `<div class="sc-summary">${items.length} entries</div>`;
    const list = items.map((item, idx) => `
        <li role="listitem">
            <button type="button" class="sc-item${item.action ? '' : ' is-static'}" data-idx="${idx}">
                <span class="sc-item-primary">${escapeHtml(item.primary || '')}</span>
                ${item.secondary ? `<span class="sc-item-secondary">${escapeHtml(item.secondary)}</span>` : ''}
            </button>
        </li>`).join('');
    listEl.innerHTML = `${summary}<ul class="sc-list">${list}</ul>`;
    const buttons = listEl.querySelectorAll('.sc-item');
    buttons.forEach(btn => {
        const idx = Number(btn.dataset.idx);
        const item = items[idx];
        if (!item || !item.action){
            btn.disabled = true;
            btn.classList.add('is-static');
            return;
        }
        const action = item.action;
        if (action.type === 'details' && action.entity && action.id){
            btn.addEventListener('click', () => toDetails(action.entity, action.id));
        } else if (action.type === 'search' && action.query){
            btn.addEventListener('click', () => runSearch(action.query));
        } else {
            btn.disabled = true;
            btn.classList.add('is-static');
        }
    });
}

async function renderShortcutList(sc, listEl){
    if (!sc || !listEl) return;
    const kind = sc.dataset.list;
    if (!kind){
        listEl.innerHTML = '<p class="sc-status empty">(no data source)</p>';
        return;
    }
    listEl.innerHTML = '<p class="sc-status">Loading …</p>';
    try {
        const items = await getShortcutItems(kind);
        renderShortcutItems(listEl, items);
    } catch (err){
        console.error('Shortcut list could not be loaded', err);
        listEl.innerHTML = '<p class="sc-status error">Error loading data.</p>';
    }
}

/* ---------- Lucene heuristics & QoL query builder ---------- */
function looksLikeLucene(q){
    if (!q) return false;
    const s = q.trim();
    return s.includes(':') || s.includes('"') || s.includes(' AND ') || s.includes(' OR ') || s.endsWith('*');
}
/** User-friendly: automatically use prefix search (token*) for plain text */
function buildUserQuery(raw){
    const s = (raw || '').trim();
    if (!s) return s;
    if (looksLikeLucene(s)) return s;
    return s.split(/\s+/).map(tok => /[*?]$/.test(tok) ? tok : (tok + '*')).join(' ');
}

/* ===================== Result preview (enrichment) ===================== */
const entityTypeRegistry = window.EntityTypeRegistry || null;

function normalizeTypeKey(value) {
    if (entityTypeRegistry && typeof entityTypeRegistry.normalizeTypeKey === 'function') {
        return entityTypeRegistry.normalizeTypeKey(value);
    }
    if (value === undefined || value === null) return '';
    return String(value).trim().toLowerCase().replace(/[^a-z0-9]/g, '');
}

const ENTITY_TYPE_MAP = entityTypeRegistry?.ENTITY_TYPE_MAP || {
    account: { detailType: 'account', typeToken: 'type:account', table: 'Account', aliases: ['account', 'accounts'] },
    project: { detailType: 'project', typeToken: 'type:project', table: 'Project', aliases: ['project', 'projects'] },
    site:    { detailType: 'site',    typeToken: 'type:site',    table: 'Site',    aliases: ['site', 'sites'] },
    server:  { detailType: 'server',  typeToken: 'type:server',  table: 'Server',  aliases: ['server', 'servers'] },
    client:  { detailType: 'client',  typeToken: 'type:client',  table: 'WorkingPosition', aliases: ['client', 'clients', 'workingposition'] },
    radio:   { detailType: 'radio',   typeToken: 'type:radio',   table: 'Radio',   aliases: ['radio', 'radios'] },
    audio:   { detailType: 'audio',   typeToken: 'type:audio',   table: 'AudioDevice', aliases: ['audio', 'audiodevice', 'audiodevices'] },
    phone:   { detailType: 'phone',   typeToken: 'type:phone',   table: 'PhoneIntegration', aliases: ['phone', 'phoneintegration', 'phoneintegrations'] },
    country: { detailType: 'country', typeToken: 'type:country', table: 'Country', aliases: ['country', 'countries'] },
    city:    { detailType: 'city',    typeToken: 'type:city',    table: 'City',    aliases: ['city', 'cities'] },
    address: { detailType: 'address', typeToken: 'type:address', table: 'Address', aliases: ['address', 'addresses'] },
    deploymentvariant: {
        detailType: 'deploymentvariant',
        typeToken: 'type:deploymentvariant',
        table: 'DeploymentVariant',
        aliases: ['deploymentvariant', 'variant', 'deploymentvariants', 'variants']
    },
    software: {
        detailType: 'software',
        typeToken: 'type:software',
        table: 'Software',
        aliases: ['software', 'softwares']
    },
    installedsoftware: {
        detailType: 'installedsoftware',
        typeToken: 'type:installedsoftware',
        table: 'InstalledSoftware',
        aliases: ['installedsoftware', 'installations']
    },
    upgradeplan: {
        detailType: 'upgradeplan',
        typeToken: 'type:upgradeplan',
        table: 'UpgradePlan',
        aliases: ['upgradeplan', 'upgradeplans']
    },
    servicecontract: {
        detailType: 'servicecontract',
        typeToken: 'type:servicecontract',
        table: 'ServiceContract',
        aliases: ['servicecontract', 'servicecontracts', 'contract', 'contracts']
    },
};

const TABLE_NAME_LOOKUP = entityTypeRegistry?.TABLE_NAME_LOOKUP || (() => {
    const lookup = {};
    Object.entries(ENTITY_TYPE_MAP).forEach(([key, info]) => {
        const aliases = new Set([key, info.table, info.detailType, ...(info.aliases || [])]);
        aliases.forEach(alias => {
            const normalized = normalizeTypeKey(alias);
            if (normalized) lookup[normalized] = info;
        });
    });
    return lookup;
})();

const COLUMN_DETAIL_TYPE_OVERRIDE_SUFFIXES = [
    ['deploymentvariantguid', 'deploymentvariant'],
    ['deploymentvariantid', 'deploymentvariant'],
    ['servicecontractguid', 'servicecontract'],
    ['servicecontractid', 'servicecontract'],
    ['contractguid', 'servicecontract'],
    ['contractid', 'servicecontract'],
    ['projectguid', 'project'],
    ['projectid', 'project'],
    ['addressguid', 'address'],
    ['addressid', 'address'],
    ['accountguid', 'account'],
    ['accountid', 'account'],
    ['siteguid', 'site'],
    ['siteid', 'site'],
    ['assignedclientguid', 'client'],
    ['assignedclientid', 'client'],
    ['clientguid', 'client'],
    ['clientid', 'client'],
    ['installedsoftwareguid', 'installedsoftware'],
    ['installedsoftwareid', 'installedsoftware'],
    ['softwareguid', 'software'],
    ['softwareid', 'software'],
    ['countrycode', 'country'],
    ['countryid', 'country'],
    ['cityid', 'city'],
];

function resolveColumnDetailType(columnName, fallbackDetailType) {
    const fallback = fallbackDetailType || null;
    const normalized = normalizeTypeKey(columnName);
    if (!normalized) return fallback;
    for (const [suffix, detailType] of COLUMN_DETAIL_TYPE_OVERRIDE_SUFFIXES) {
        if (normalized.endsWith(suffix)) {
            return detailType;
        }
    }
    return fallback;
}

function getTypeTokenForDetailType(detailType) {
    const key = normalizeTypeKey(detailType);
    if (!key) return null;
    const info = ENTITY_TYPE_MAP[key];
    if (info && info.typeToken) return info.typeToken;
    return `type:${key}`;
}

function tableForType(t){
    if (entityTypeRegistry && typeof entityTypeRegistry.canonicalTableForType === 'function') {
        return entityTypeRegistry.canonicalTableForType(t);
    }
    const key = normalizeTypeKey(t);
    const info = ENTITY_TYPE_MAP[key];
    if (info && info.table) return info.table;
    return t;
}

function getTableTypeInfo(tableName) {
    const key = normalizeTypeKey(tableName);
    if (!key) {
        return { detailType: null, typeToken: null };
    }
    const info = TABLE_NAME_LOOKUP[key];
    if (info) {
        return { detailType: info.detailType, typeToken: info.typeToken };
    }
    const detailType = key || null;
    return { detailType, typeToken: detailType ? `type:${detailType}` : null };
}
function val(row, ...keys){
    for(const k of keys){
        if (row[k] !== undefined) return row[k];
        const u = k.toUpperCase(), l = k.toLowerCase();
        for (const kk in row) { if (kk === u || kk === l) return row[kk]; }
    }
    return undefined;
}
function formatPreview(type, row){
    const t=(type||'').toLowerCase();
    const parts=[];
    if (t==='account'){
        const contact = val(row,'ContactName') || val(row,'AccountName');
        if (contact) parts.push(contact);
        const country = val(row,'Country'); if (country) parts.push(country);
        const email   = val(row,'ContactEmail'); if (email) parts.push(email);
    } else if (t==='project'){
        const v=val(row,'DeploymentVariant'); if (v) parts.push(v);
        const sap=val(row,'ProjectSAPID');    if (sap) parts.push('SAP '+sap);
    } else if (t==='site'){
        const fz=val(row,'FireZone'); if (fz) parts.push('Zone '+fz);
        const tc=val(row,'TenantCount'); if (tc!=null) parts.push(tc+' Tenants');
    } else if (t==='server'){
        ['ServerBrand','ServerOS','VirtualPlatform'].forEach(k=>{
            const v=val(row,k); if(v) parts.push(v);
        });
    } else if (t==='client'){
        ['ClientBrand','ClientOS'].forEach(k=>{
            const v=val(row,k); if(v) parts.push(v);
        });
    } else if (t==='radio'){
        const br=val(row,'RadioBrand'); if (br) parts.push(br);
        const md=val(row,'Mode'); if (md) parts.push(md);
        const ds=val(row,'DigitalStandard'); if (ds) parts.push(ds);
    } else if (t==='audio'){
        const br=val(row,'AudioDeviceBrand'); if (br) parts.push(br);
        const dir=val(row,'Direction'); if (dir) parts.push(dir);
    } else if (t==='phone'){
        const br=val(row,'PhoneBrand'); if (br) parts.push(br);
        const tp=val(row,'PhoneType'); if (tp) parts.push(tp);
    } else if (t==='country'){
        const code = val(row,'CountryCode'); if (code) parts.push(`Code ${code}`);
        const name = val(row,'CountryName'); if (name) parts.push(name);
    } else if (t==='city'){
        const name = val(row,'CityName'); if (name) parts.push(name);
        const cc = val(row,'CountryCode'); if (cc) parts.push(`Country ${cc}`);
    } else if (t==='address'){
        const street = val(row,'Street'); if (street) parts.push(street);
        const cityId = val(row,'CityID'); if (cityId) parts.push(`City ${shortUuid(cityId)}`);
    } else if (t==='deploymentvariant'){
        const variant = val(row,'VariantName'); if (variant) parts.push(variant);
        const code = val(row,'VariantCode'); if (code) parts.push(`#${code}`);
        const active = val(row,'IsActive');
        if (active !== undefined && active !== null) parts.push(parseBool(active) ? 'active' : 'inactive');
    } else if (t==='software'){
        const name = val(row,'Name'); if (name) parts.push(name);
        const release = val(row,'Release'); if (release) parts.push(`Release ${release}`);
        const revision = val(row,'Revision'); if (revision) parts.push(`Rev ${revision}`);
        const phase = val(row,'SupportPhase'); if (phase) parts.push(phase);
        const vendor = val(row,'ThirdParty');
        if (vendor !== undefined) {
            parts.push(parseBool(vendor) ? 'Third-party' : 'First-party');
        }
    } else if (t==='installedsoftware'){
        const siteId = val(row,'SiteID'); if (siteId) parts.push(`Site ${shortUuid(siteId)}`);
        const swId = val(row,'SoftwareID'); if (swId) parts.push(`Software ${shortUuid(swId)}`);
    } else if (t==='upgradeplan'){
        const status = val(row,'Status'); if (status) parts.push(status);
        const window = formatDateRange(val(row,'PlannedWindowStart'), val(row,'PlannedWindowEnd')); if (window) parts.push(window);
        const createdBy = val(row,'CreatedBy'); if (createdBy) parts.push(`by ${createdBy}`);
    } else if (t==='servicecontract'){
        const number = val(row,'ContractNumber'); if (number) parts.push(`Contract ${number}`);
        const status = val(row,'Status'); if (status) parts.push(status);
        const duration = formatDateRange(val(row,'StartDate'), val(row,'EndDate')); if (duration) parts.push(duration);
    }
    return parts.join(' · ');
}
async function enrichRows(hits){
    const jobs = hits.map(async (h, i) => {
        try{
            const table = tableForType(h.type);
            const res = await fetch(`/row/${table}/${h.id}`);
            if (!res.ok) return;
            const row = await res.json();
            const info = formatPreview(h.type, row) || '';
            const cell = document.getElementById(`info-${i}`);
            if (cell) cell.textContent = info;
        } catch {}
    });
    await Promise.allSettled(jobs);
}

/* ===================== Event wiring ===================== */
function wireEvents() {
    // Primary search (button)
    searchBtn.onclick = () => runSearch(searchInput.value);

    // Enter → search | Tab → accept top suggestion + search immediately
    searchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            runSearch(searchInput.value);
        } else if (e.key === 'Tab') {
            const completed = completeFromSuggestions();
            if (completed) {
                e.preventDefault();
                runSearch(searchInput.value);
            }
        }
    });

    // Selecting a datalist entry via mouse → search automatically
    searchInput.addEventListener('change', () => {
        if ((searchInput.value || '').trim()) runSearch(searchInput.value);
    });

    // Autocomplete (debounced)
    searchInput.addEventListener('input', debounce(async () => {
        const q = (searchInput.value || '').trim();
        if (q.length < 2) { if (sugList) sugList.innerHTML = ''; return; }
        try {
            const res = await fetch(`${API.suggest}?q=${encodeURIComponent(q)}&max=8`);
            if (!res.ok) return;
            const arr = await res.json();
            if (sugList) sugList.innerHTML = arr.map(s => `<option value="${s}">`).join('');
        } catch {}
    }, 180));

    // Reindex (right button)
    if (idxBtnSide) idxBtnSide.onclick = () => startReindex(idxBtnSide);

    // Poll progress regularly
    setInterval(pollProgress, 50);
    pollProgress();

    // Initialize shortcuts including ARIA
    setupShortcuts();
}
document.addEventListener('DOMContentLoaded', wireEvents);

// Find/apply top suggestion (case-insensitive); true = applied
function completeFromSuggestions(){
    if (!sugList) return false;
    const cur = (searchInput.value || '').trim().toLowerCase();
    if (!cur) return false;
    let best = null;
    [...sugList.options].some(opt => {
        const v = (opt.value || '').toLowerCase();
        if (v.startsWith(cur)) { best = opt.value; return true; }
        return false;
    });
    if (!best) return false;
    searchInput.value = best;
    return true;
}

// Start the reindex and immediately trigger polling
async function startReindex(btn) {
    if (btn) btn.disabled = true;
    try {
        await fetch(API.reindex, { method: 'POST' });
        await pollProgress();
    } catch (e) {
        console.error('Reindex start failed', e);
    } finally {
        await sleep(150);
        if (btn) btn.disabled = false;
    }
}

/* ===================== Shortcuts ===================== */
function setupShortcuts() {
    document.querySelectorAll('.shortcut').forEach(sc => {
        const id        = sc.dataset.id;
        const headBtn   = sc.querySelector('.head');
        const labelEl   = sc.querySelector('.label');
        const renameBtn = sc.querySelector('.rename');
        const chevBtn   = sc.querySelector('.chev');
        const panel     = sc.querySelector('.panel');
        const listEl    = panel ? panel.querySelector('[data-role="list"]') : null;
        const hasList   = !!(sc.dataset.list && listEl);
        const inputEl   = hasList ? null : (panel ? panel.querySelector('input') : null);
        const defVal    = (sc.dataset.default || '').trim();

        const readLabel = () => shortcutStorage.getLabel(id, labelEl.textContent);
        const readQuery = () => shortcutStorage.getQuery(id, defVal);
        const writeLabel = (value) => shortcutStorage.setLabel(id, value);
        const writeQuery = (value) => shortcutStorage.setQuery(id, value);

        labelEl.textContent = readLabel();

        if (hasList) {
            if (listEl && !listEl.innerHTML.trim()) {
                listEl.innerHTML = '<p class="sc-status empty">No data loaded yet.</p>';
            }
        } else if (inputEl) {
            inputEl.value = readQuery();
            inputEl.placeholder = 'Lucene query';
            inputEl.setAttribute('aria-label','Lucene query');
        }

        if (panel) {
            const hid = `sc-head-${id}`;
            const pid = `sc-panel-${id}`;
            headBtn.id = hid;
            headBtn.setAttribute('aria-controls', pid);
            headBtn.setAttribute('aria-expanded', sc.classList.contains('open'));
            panel.id = pid;
            panel.setAttribute('role','region');
            panel.setAttribute('aria-labelledby', hid);
        }

        headBtn.addEventListener('click', (ev) => {
            if (ev.target === renameBtn || ev.target === chevBtn) return;
            const q = hasList ? defVal : (((inputEl && inputEl.value) || '').trim() || defVal);
            if (q) {
                if (!hasList) writeQuery(q);
                runSearch(q);
            }
        });

        chevBtn.addEventListener('click', (ev) => {
            ev.stopPropagation();
            sc.classList.toggle('open');
            const open = sc.classList.contains('open');
            headBtn.setAttribute('aria-expanded', open);
            if (!open) return;
            if (hasList && listEl) {
                renderShortcutList(sc, listEl);
            } else if (inputEl) {
                inputEl.focus();
                inputEl.select();
            }
        });

        renameBtn.addEventListener('click', (ev) => {
            ev.stopPropagation();
            const current = labelEl.textContent.trim();
            const name = prompt('New name for the shortcut:', current);
            if (name && name.trim()) {
                labelEl.textContent = name.trim();
                writeLabel(labelEl.textContent);
            }
        });

        if (inputEl) {
            inputEl.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    const q = inputEl.value.trim();
                    if (q) {
                        writeQuery(q);
                        runSearch(q);
                    }
                } else if (e.key === 'Escape') {
                    sc.classList.remove('open');
                    headBtn.setAttribute('aria-expanded', false);
                }
            });

            inputEl.addEventListener('blur', () => writeQuery(inputEl.value.trim()));
        }
    });
}

/* ===================== Search ===================== */
function runSearch(raw){
    const prepared = buildUserQuery(raw);
    runLucene(prepared);
}

function shortUuid(value) {
    if (value === null || value === undefined) return '';
    const str = String(value);
    const uuidPattern = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
    if (!uuidPattern.test(str)) return str;
    const idx = str.lastIndexOf('-');
    if (idx === -1 || idx === str.length - 1) return str;
    const lastSegment = str.slice(idx + 1);
    const trimmed = lastSegment.replace(/^0+/, '');
    return trimmed || '0';
}

function renderIdChip(value, displayOverride) {
    const fallback = (value === null || value === undefined) ? '' : String(value);
    let display = displayOverride;
    if (display === undefined || display === null || display === '') display = shortUuid(value);
    const text = display || fallback;
    if (!text) return '';
    return `<span class="id-chip">${escapeHtml(text)}</span>`;
}

function renderIdDisplay(value) {
    const fallback = (value === null || value === undefined) ? '' : String(value);
    const display = shortUuid(value);
    const chip = renderIdChip(value, display);
    if (chip) {
        return { inner: chip, title: fallback };
    }
    const safeText = escapeHtml(display || fallback);
    return { inner: safeText, title: fallback };
}

async function runLucene(q) {
    const query = (q ?? '').trim();
    if (!query) return;
    try {
        setBusy(resultArea, true);
        const res  = await fetch(`${API.search}?q=${encodeURIComponent(query)}`);
        const hits = await res.json();
        if (!Array.isArray(hits) || !hits.length) {
            resultArea.textContent = '(no matches)';
            return;
        }

        const rows = hits.map((h, i) => {
            const snippet = (h.snippet ?? '').trim();
            const snippetHtml = snippet ? `<div class="hit-snippet"><small>${escapeHtml(snippet)}</small></div>` : '';
            const typeArg = JSON.stringify(h.type ?? '');
            const idArg = JSON.stringify(h.id ?? '');
            const idDisplay = renderIdDisplay(h.id);
            return `
      <tr onclick='toDetails(${typeArg},${idArg})' style="cursor:pointer">
        <td>${escapeHtml(h.type)}</td>
        <td title="${escapeHtml(idDisplay.title)}">${idDisplay.inner}</td>
        <td><div class="hit-text">${escapeHtml(h.text ?? '')}</div>${snippetHtml}</td>
        <td id="info-${i}"></td>
      </tr>`;
        }).join('');

        resultArea.innerHTML = `
      <div class="table-scroll">
        <table>
          <tr><th>Type</th><th>ID</th><th>Text / Snippet</th><th>Info</th></tr>
          ${rows}
        </table>
      </div>`;

        enrichRows(hits);

    } catch (e) {
        resultArea.innerHTML = `<p id="error" role="alert">Error: ${e}</p>`;
    } finally {
        setBusy(resultArea, false);
    }
}

/* Table viewer (100-row preview) */
function isIdColumnName(columnKey) {
    const normalized = normalizeTypeKey(columnKey);
    if (!normalized) return false;
    if (/(id|guid)$/.test(normalized)) return true;
    return normalized === 'countrycode';
}

function tableQuickFilterQuery(typeToken, columnKey, rawValue) {
    const column = (columnKey === undefined || columnKey === null) ? '' : String(columnKey);
    const raw = (rawValue === undefined || rawValue === null) ? '' : String(rawValue).trim();
    if (!column || !raw) return null;

    const typeFilter = typeToken || '';

    const isIdColumn = isIdColumnName(column);
    if (isIdColumn) {
        const escaped = raw.replace(/"/g, '\\"');
        const idQuery = `id:"${escaped}"`;
        return typeFilter ? `${typeFilter} AND ${idQuery}` : idQuery;
    }

    if (/^stillactive$/i.test(column)) {
        const active = parseBool(rawValue);
        const statusToken = active ? 'statusactive' : 'statusinactive';
        return typeFilter ? `${typeFilter} AND ${statusToken}` : statusToken;
    }

    if (/^status$/i.test(column)) {
        const normalized = raw.replace(/[^a-z0-9]+/gi, '').toLowerCase();
        if (!normalized) return null;
        const statusToken = `status${normalized}`;
        return typeFilter ? `${typeFilter} AND ${statusToken}` : statusToken;
    }

    if (/^thirdparty$/i.test(column)) {
        const flag = parseBool(rawValue) ? 'true' : 'false';
        const vendorToken = `thirdparty${flag}`;
        return typeFilter ? `${typeFilter} AND ${vendorToken}` : vendorToken;
    }

    const prepared = buildUserQuery(raw);
    if (!prepared) return null;
    return typeFilter ? `${typeFilter} AND ${prepared}` : prepared;
}

function renderTableCell(tableName, columnName, value) {
    const key = (columnName === undefined || columnName === null) ? '' : String(columnName);
    const raw = (value === undefined || value === null) ? '' : String(value);
    const isIdColumn = isIdColumnName(key);
    const tableTypeInfo = getTableTypeInfo(tableName);
    const columnDetailType = resolveColumnDetailType(key, tableTypeInfo.detailType);
    const columnTypeToken = columnDetailType === tableTypeInfo.detailType
        ? tableTypeInfo.typeToken
        : getTypeTokenForDetailType(columnDetailType);

    if (isIdColumn && raw) {
        const rendered = renderIdDisplay(value);
        const titleAttr = escapeHtml(rendered.title);
        if (columnDetailType) {
            const href = `/details.html?type=${encodeURIComponent(columnDetailType)}&id=${encodeURIComponent(raw)}`;
            const hrefAttr = escapeHtml(href);
            return `<td title="${titleAttr}"><a class="table-id-link" href="${hrefAttr}">${rendered.inner}</a></td>`;
        }
        return `<td title="${titleAttr}">${rendered.inner}</td>`;
    }

    const query = tableQuickFilterQuery(columnTypeToken, key, value);
    const queryAttr = query ? escapeHtml(query) : '';

    if (query && raw) {
        return `<td><button type="button" class="table-quick-filter" data-query="${queryAttr}">${escapeHtml(raw)}</button></td>`;
    }

    return `<td>${escapeHtml(raw)}</td>`;
}

async function showTable(name) {
    try {
        setBusy(resultArea, true);
        const res  = await fetch(`${API.table}/${encodeURIComponent(name)}`);
        const rows = await res.json();
        if (!Array.isArray(rows) || !rows.length) { resultArea.textContent = '(empty)'; return; }

        const cols = Object.keys(rows[0]);
        const hdr  = cols.map(c => `<th>${escapeHtml(c)}</th>`).join('');
        const body = rows.map(r => `<tr>${cols.map(c => renderTableCell(name, c, r[c])).join('')}</tr>`).join('');
        const { typeToken } = getTableTypeInfo(name);
        const safeName = escapeHtml(name);
        const titleQuery = typeToken ? escapeHtml(typeToken) : null;
        const titleControl = titleQuery
            ? `<button type="button" class="table-quick-filter" data-query="${titleQuery}">${safeName}</button>`
            : safeName;

        resultArea.innerHTML =
            `<h2>${titleControl}</h2>
       <div class="table-scroll">
         <table>
           <tr>${hdr}</tr>${body}
         </table>
       </div>`;
    } catch (e) {
        resultArea.innerHTML = `<p id="error" role="alert">Error: ${e}</p>`;
    } finally {
        setBusy(resultArea, false);
    }
}

/* Details navigation (details.html) */
function toDetails(type, id) {
    location.href = `/details.html?type=${encodeURIComponent(type)}&id=${id}`;
}
window.toDetails = toDetails;

/* ===================== Progress indicator ===================== */
async function pollProgress() {
    try {
        const r = await fetch(API.progress);
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        const p = await r.json(); // IndexProgress.Status

        const running = !!p.active;
        const pct     = Math.max(0, Math.min(100, p.percent || 0));
        const done    = p.totalDone ?? 0;
        const total   = p.grandTotal ?? 0;

        if (running || (done < total)) {
            idxBox.classList.add('active');
            idxBar.style.width = pct.toFixed(1) + '%';
            idxBar.setAttribute('aria-valuenow', pct.toFixed(0));
            idxBox.setAttribute('aria-busy','true');
            idxText.textContent = `Index: ${pct.toFixed(0)}% (${done}/${total})`;
        } else {
            idxBox.classList.remove('active');
            idxBar.style.width = '0%';
            idxBar.setAttribute('aria-valuenow', '0');
            idxBox.removeAttribute('aria-busy');
            idxText.textContent = '';
        }
    } catch {
        // remain quiet if the endpoint is not reachable yet
    }
}