/* ===================== Configuration ===================== */
const API = {
    progress: '/api/index-progress',   // returns IndexProgress.Status
    reindex:  '/api/index/reindex',    // POST → manual reindex
    search:   '/search',               // GET /search?q=...
    suggest:  '/search/suggest',       // GET /search/suggest?q=...
    table:    '/table',                // GET /table/{name}
    siteSoftwareSummary: '/sites/software-summary',
};

/* ===================== DOM references ===================== */
const resultArea  = document.getElementById('resultArea');
const searchInput = document.getElementById('search-input');
const searchBtn   = document.getElementById('search-btn');
const searchScopeIndicator = document.getElementById('search-scope-indicator');

const idxBox  = document.getElementById('idx-box');
const idxBar  = document.querySelector('#idx-bar > span');
const idxText = document.getElementById('idx-text');
const idxBtnSide = document.getElementById('idx-reindex-side');
const helpPanel = document.getElementById('help-panel');
const helpToggle = document.querySelector('.help-toggle');

const sugList = document.getElementById('sug');
const advancedHelpToggle = document.getElementById('advanced-help-toggle');
const advancedHelpPanel = document.getElementById('advanced-help');
const advancedHelpStatus = document.getElementById('advanced-help-status');
const shortcutContainer = document.getElementById('shortcut-container');
const shortcutAddBtn = document.getElementById('shortcut-add');
const shortcutRemoveBtn = document.getElementById('shortcut-remove');

const SEARCH_SCOPE_OPTIONS = {
    all: { key: 'all', label: 'All', type: null },
};

const SCOPE_ALIAS_LOOKUP = new Map();
SCOPE_ALIAS_LOOKUP.set('all', 'all');

let searchScopeKey = 'all';

const appIdUtils = (typeof window !== 'undefined' && window.appIdUtils) || {};

function normalizeSiteIdValue(value) {
    if (appIdUtils.normalizeUuid) {
        return appIdUtils.normalizeUuid(value);
    }
    if (value === undefined || value === null) {
        return null;
    }
    const str = String(value).trim();
    return str ? str.toLowerCase() : null;
}

function getNormalizedSiteId(record) {
    if (appIdUtils.resolveNormalizedSiteId) {
        return appIdUtils.resolveNormalizedSiteId(record);
    }
    if (!record || typeof record !== 'object') {
        return null;
    }
    const raw = record.SITEID ?? record.siteID ?? record.SiteID ?? record.siteId ?? record.siteid;
    return normalizeSiteIdValue(raw);
}

/* ===================== Utils ===================== */
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const debounce = (fn, ms=250) => { let t; return (...a)=>{clearTimeout(t); t=setTimeout(()=>fn(...a),ms);} };
const stGet = (k, d) => { try { const v = localStorage.getItem(k); return v === null ? d : v; } catch { return d; } };
const stSet = (k, v) => { try { localStorage.setItem(k, v); } catch {} };
function escapeHtml(s){ return (s??'').replace(/[&<>"']/g, c=>({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c])); }
function getHighlightTerms(raw){
    if (raw === undefined || raw === null) return [];
    const tokens = Array.isArray(raw)
        ? raw
        : String(raw).split(/\s+/);
    return tokens
        .map(tok => String(tok ?? '').trim())
        .map(tok => tok.replace(/^[*]+|[*]+$/g, ''))
        .filter(Boolean);
}

function highlightMatches(text, terms){
    const source = text ?? '';
    const normalizedTerms = getHighlightTerms(terms);
    if (!normalizedTerms.length){
        return escapeHtml(source);
    }
    const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const pattern = normalizedTerms
        .map(tok => escapeRegExp(tok))
        .filter(Boolean)
        .join('|');
    if (!pattern){
        return escapeHtml(source);
    }
    const regex = new RegExp(pattern, 'gi');
    let lastIndex = 0;
    let result = '';
    let match;
    while ((match = regex.exec(source)) !== null){
        result += escapeHtml(source.slice(lastIndex, match.index));
        result += `<mark class="hit-highlight">${escapeHtml(match[0])}</mark>`;
        lastIndex = regex.lastIndex;
    }
    result += escapeHtml(source.slice(lastIndex));
    return result;
}
function setBusy(el, busy){ if(!el) return; busy ? el.setAttribute('aria-busy','true') : el.removeAttribute('aria-busy'); }

function setAdvancedHelpExpanded(expanded) {
    if (!advancedHelpPanel || !advancedHelpToggle) return;
    const isOpen = !!expanded;
    advancedHelpPanel.hidden = !isOpen;
    advancedHelpToggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
    if (advancedHelpStatus) {
        advancedHelpStatus.textContent = isOpen
            ? 'Advanced search tips expanded'
            : 'Advanced search tips collapsed';
    }
}

function toggleAdvancedHelp() {
    if (!advancedHelpPanel || !advancedHelpToggle) return;
    setAdvancedHelpExpanded(advancedHelpPanel.hidden);
}

const shortcutCache = new Map();
const HELP_COLLAPSE_KEY = 'ui:help-collapsed';
const SHORTCUT_COUNT_KEY = 'sc:count';

function resolveScopeKey(value) {
    const normalized = normalizeTypeKey(value);
    if (!normalized) return null;
    if (SEARCH_SCOPE_OPTIONS[normalized]) return normalized;
    const aliasTarget = SCOPE_ALIAS_LOOKUP.get(normalized);
    if (aliasTarget && SEARCH_SCOPE_OPTIONS[aliasTarget]) return aliasTarget;
    return null;
}

function normalizeScopeKey(value) {
    return resolveScopeKey(value) || 'all';
}

function getScopeOption(key) {
    const resolved = resolveScopeKey(key);
    if (resolved && SEARCH_SCOPE_OPTIONS[resolved]) {
        return SEARCH_SCOPE_OPTIONS[resolved];
    }
    return SEARCH_SCOPE_OPTIONS.all;
}

function getScopeTypeValue() {
    const option = getScopeOption(searchScopeKey);
    return option ? option.type : null;
}

function isScopeSelectable(value) {
    const resolved = resolveScopeKey(value);
    return !!resolved && resolved !== 'all' && !!SEARCH_SCOPE_OPTIONS[resolved];
}

function updateScopeIndicator() {
    if (!searchScopeIndicator) return;
    const option = getScopeOption(searchScopeKey);
    searchScopeIndicator.textContent = option.label;
    const filtered = option.type !== null;
    searchScopeIndicator.classList.toggle('is-filtered', filtered);
    searchScopeIndicator.classList.toggle('is-resettable', filtered);
    searchScopeIndicator.setAttribute('aria-pressed', filtered ? 'true' : 'false');
    searchScopeIndicator.dataset.scope = option.key;
    if (filtered) {
        searchScopeIndicator.title = 'Click to reset the filter';
    } else {
        searchScopeIndicator.removeAttribute('title');
    }
}

function setSearchScope(nextKey, options = {}) {
    const normalized = normalizeScopeKey(nextKey);
    const changed = normalized !== searchScopeKey;
    if (changed) {
        searchScopeKey = normalized;
    }
    updateScopeIndicator();
    if (options.syncUrl !== false) {
        updateUrlState(options.queryOverride);
    }
    return changed;
}

function updateUrlState(overrideQuery) {
    if (typeof window === 'undefined' || !window?.history?.replaceState) return;
    const params = new URLSearchParams(window.location.search);
    const query = (overrideQuery !== undefined && overrideQuery !== null)
        ? String(overrideQuery)
        : (searchInput ? searchInput.value : '');
    const trimmed = query.trim();
    if (trimmed) {
        params.set('q', trimmed);
    } else {
        params.delete('q');
    }
    const scopeOption = getScopeOption(searchScopeKey);
    if (scopeOption.type) {
        params.set('type', scopeOption.key);
    } else {
        params.delete('type');
    }
    const newQuery = params.toString();
    const newUrl = `${window.location.pathname}${newQuery ? '?' + newQuery : ''}`;
    window.history.replaceState(null, '', newUrl);
}

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
        const typeButton = target.closest('.search-type-pill');
        if (typeButton) {
            const type = typeButton.dataset.searchType;
            event.preventDefault();
            setSearchScope(type, { syncUrl: false });
            if (searchInput && typeof searchInput.focus === 'function') {
                searchInput.focus();
            }
            runSearch(searchInput ? searchInput.value : '', { skipUrlUpdate: false });
            return;
        }
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
                    const redundant = val(row,'RedundantServers');
                    const projectId = val(row,'ProjectID');
                    const zone      = val(row,'FireZone');
                    const meta = [];
                    if (zone) meta.push(`Zone ${zone}`);
                    if (tenants !== undefined && tenants !== null && tenants !== '') meta.push(`${tenants} units`);
                    if (redundant !== undefined && redundant !== null && redundant !== '') meta.push(`${redundant} redundant srv`);
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
            btn.addEventListener('click', () => {
                setSearchScope('all', { syncUrl: true });
                runSearch(action.query);
            });
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
    const isInfixMode = s.startsWith('*');
    if (isInfixMode) {
        const tokens = s.replace(/^\*+/, '').split(/\s+/).filter(Boolean);
        if (!tokens.length) return s;
        return tokens.map(tok => `*${tok.replace(/^[*]+|[*]+$/g, '')}*`).join(' ');
    }
    if (looksLikeLucene(s)) return s;
    return s.split(/\s+/).map(tok => /[*?]$/.test(tok) ? tok : (tok + '*')).join(' ');
}

function buildScopedQuery(raw, scopeType) {
    const prepared = buildUserQuery(raw);
    const query = (prepared ?? '').trim();
    if (!scopeType) {
        return query;
    }
    const typeClause = `type:${scopeType}`;
    if (!query) {
        return typeClause;
    }
    return `${typeClause} AND (${query})`;
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
    account: {
        detailType: 'account',
        typeToken: 'type:account',
        table: 'Account',
        detailTable: 'Account',
        aliases: ['account', 'accounts'],
    },
    project: {
        detailType: 'project',
        typeToken: 'type:project',
        table: 'Project',
        detailTable: 'Project',
        aliases: ['project', 'projects'],
    },
    site: {
        detailType: 'site',
        typeToken: 'type:site',
        table: 'Site',
        detailTable: 'Site',
        aliases: ['site', 'sites'],
    },
    server: {
        detailType: 'server',
        typeToken: 'type:server',
        table: 'Server',
        detailTable: 'Server',
        aliases: ['server', 'servers'],
    },
    client: {
        detailType: 'client',
        typeToken: 'type:client',
        table: 'WorkingPosition',
        detailTable: 'Clients',
        aliases: ['client', 'clients', 'workingposition', 'workingpositions'],
    },
    radio: {
        detailType: 'radio',
        typeToken: 'type:radio',
        table: 'Radio',
        detailTable: 'Radio',
        aliases: ['radio', 'radios'],
    },
    audio: {
        detailType: 'audio',
        typeToken: 'type:audio',
        table: 'AudioDevice',
        detailTable: 'AudioDevice',
        aliases: ['audio', 'audiodevice', 'audiodevices'],
    },
    phone: {
        detailType: 'phone',
        typeToken: 'type:phone',
        table: 'PhoneIntegration',
        detailTable: 'PhoneIntegration',
        aliases: ['phone', 'phoneintegration', 'phoneintegrations'],
    },
    country: {
        detailType: 'country',
        typeToken: 'type:country',
        table: 'Country',
        detailTable: 'Country',
        aliases: ['country', 'countries'],
    },
    city: {
        detailType: 'city',
        typeToken: 'type:city',
        table: 'City',
        detailTable: 'City',
        aliases: ['city', 'cities'],
    },
    address: {
        detailType: 'address',
        typeToken: 'type:address',
        table: 'Address',
        detailTable: 'Address',
        aliases: ['address', 'addresses'],
    },
    deploymentvariant: {
        detailType: 'deploymentvariant',
        typeToken: 'type:deploymentvariant',
        table: 'DeploymentVariant',
        detailTable: 'DeploymentVariant',
        aliases: ['deploymentvariant', 'variant', 'deploymentvariants', 'variants'],
    },
    software: {
        detailType: 'software',
        typeToken: 'type:software',
        table: 'Software',
        detailTable: 'Software',
        aliases: ['software', 'softwares'],
    },
    upgradeplan: {
        detailType: 'upgradeplan',
        typeToken: 'type:upgradeplan',
        table: 'UpgradePlan',
        detailTable: 'UpgradePlan',
        aliases: ['upgradeplan', 'upgradeplans'],
    },
    servicecontract: {
        detailType: 'servicecontract',
        typeToken: 'type:servicecontract',
        table: 'ServiceContract',
        detailTable: 'ServiceContract',
        aliases: ['servicecontract', 'servicecontracts', 'contract', 'contracts'],
    },
};

initializeScopeOptionsFromEntityTypes();

function initializeScopeOptionsFromEntityTypes() {
    if (!ENTITY_TYPE_MAP) return;
    Object.entries(ENTITY_TYPE_MAP).forEach(([entryKey, info]) => {
        if (!info) return;
        const canonicalKey = normalizeTypeKey(info.detailType || entryKey);
        if (!canonicalKey || canonicalKey === 'all') return;
        const backendType = info.detailType || canonicalKey;
        const label = deriveScopeLabel(info, entryKey);
        SEARCH_SCOPE_OPTIONS[canonicalKey] = {
            key: canonicalKey,
            label,
            type: backendType,
        };
        registerScopeAlias(canonicalKey, canonicalKey);
        registerScopeAlias(entryKey, canonicalKey);
        registerScopeAlias(info.detailType, canonicalKey);
        registerScopeAlias(info.typeToken, canonicalKey);
        registerScopeAlias(info.table, canonicalKey);
        registerScopeAlias(info.detailTable, canonicalKey);
        if (Array.isArray(info.aliases)) {
            info.aliases.forEach(alias => registerScopeAlias(alias, canonicalKey));
        }
    });
}

function registerScopeAlias(value, canonicalKey) {
    if (!canonicalKey) return;
    const normalized = normalizeTypeKey(value);
    if (normalized && normalized !== 'all') {
        const existing = SCOPE_ALIAS_LOOKUP.get(normalized);
        if (!existing) {
            SCOPE_ALIAS_LOOKUP.set(normalized, canonicalKey);
        } else if (existing !== canonicalKey) {
            return;
        }
    }
    if (value === undefined || value === null) return;
    const raw = String(value).trim();
    if (!raw || raw.startsWith('type:')) return;
    const luceneAlias = normalizeTypeKey(`type:${raw}`);
    if (luceneAlias && !SCOPE_ALIAS_LOOKUP.has(luceneAlias)) {
        SCOPE_ALIAS_LOOKUP.set(luceneAlias, canonicalKey);
    }
}

function deriveScopeLabel(info, fallbackKey) {
    const labelSource = info?.detailTable || info?.table || info?.detailType || fallbackKey;
    const label = humanizeScopeLabel(labelSource);
    if (label) return label;
    const fallback = humanizeScopeLabel(fallbackKey);
    return fallback || (fallbackKey ? String(fallbackKey) : '');
}

function humanizeScopeLabel(value) {
    if (value === undefined || value === null) return '';
    const trimmed = String(value).trim();
    if (!trimmed) return '';
    const spaced = trimmed
        .replace(/[_\s]+/g, ' ')
        .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
        .replace(/([A-Z])([A-Z][a-z])/g, '$1 $2');
    return spaced
        .split(' ')
        .filter(Boolean)
        .map(part => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
        .join(' ');
}

const TABLE_NAME_LOOKUP = entityTypeRegistry?.TABLE_NAME_LOOKUP || (() => {
    const lookup = {};
    Object.entries(ENTITY_TYPE_MAP).forEach(([key, info]) => {
        const aliases = new Set([key, info.table, info.detailType, info.detailTable, ...(info.aliases || [])]);
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
        ['ClientBrand','ClientOS','WorkingPositionType'].forEach(k=>{
            const v=val(row,k); if(v) parts.push(v);
        });
        const other = val(row,'OtherInstalledSoftware'); if(other) parts.push(other);
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

/* ===================== Help panel ===================== */
function syncHelpPanelState(collapsed){
    if (!helpPanel || !helpToggle) return;
    const isCollapsed = !!collapsed;
    const icon = helpToggle.querySelector('.help-toggle__icon');
    helpPanel.classList.toggle('is-collapsed', isCollapsed);
    helpToggle.setAttribute('aria-expanded', isCollapsed ? 'false' : 'true');
    helpToggle.setAttribute('aria-label', isCollapsed ? 'Expand help panel' : 'Collapse help panel');
    if (icon) {
        icon.textContent = isCollapsed ? '❯' : '☰';
    }
}

function toggleHelpPanel(forceState){
    if (!helpPanel || !helpToggle) return;
    const nextState = (forceState === undefined)
        ? !helpPanel.classList.contains('is-collapsed')
        : !!forceState;
    syncHelpPanelState(nextState);
    stSet(HELP_COLLAPSE_KEY, nextState ? 'true' : 'false');
}

function hydrateHelpPanelState(){
    if (!helpPanel || !helpToggle) return;
    const stored = stGet(HELP_COLLAPSE_KEY, 'false') === 'true';
    syncHelpPanelState(stored);
}

/* ===================== Event wiring ===================== */
function wireEvents() {
    // Primary search (button)
    searchBtn.onclick = () => runSearch(searchInput.value);

    if (helpToggle && helpPanel) {
        hydrateHelpPanelState();
        helpToggle.addEventListener('click', () => toggleHelpPanel());
    }

    if (searchScopeIndicator) {
        searchScopeIndicator.addEventListener('click', () => {
            if (searchScopeKey !== 'all') {
                setSearchScope('all');
                runSearch(searchInput ? searchInput.value : '', { skipUrlUpdate: false });
            }
        });
    }

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

    if (advancedHelpToggle && advancedHelpPanel) {
        setAdvancedHelpExpanded(false);
        const handleToggle = () => toggleAdvancedHelp();
        advancedHelpToggle.addEventListener('click', handleToggle);
        advancedHelpPanel.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                setAdvancedHelpExpanded(false);
                if (typeof advancedHelpToggle.focus === 'function') {
                    advancedHelpToggle.focus();
                }
            }
        });
    }

    // Poll progress regularly
    setInterval(pollProgress, 50);
    pollProgress();

    // Initialize shortcuts including ARIA
    setupShortcuts();
}

function bootstrapSearchFromUrl() {
    updateScopeIndicator();
    if (typeof window === 'undefined' || !window?.location) return;
    try {
        const params = new URLSearchParams(window.location.search);
        const typeParam = params.get('type');
        const queryParam = params.get('q');
        const hasQuery = !!(queryParam && queryParam.trim());
        const scopeChanged = typeParam ? setSearchScope(typeParam, { syncUrl: false }) : false;
        if (hasQuery && searchInput) {
            searchInput.value = queryParam;
            runSearch(queryParam, { skipUrlUpdate: true });
        } else if (scopeChanged && searchScopeKey !== 'all') {
            runSearch('', { skipUrlUpdate: true });
        } else if (!typeParam) {
            updateScopeIndicator();
        }
    } catch {
        updateScopeIndicator();
    }
}

document.addEventListener('DOMContentLoaded', () => {
    bootstrapSearchFromUrl();
    wireEvents();
});

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
function readStoredShortcutCount(defaultCount) {
    const stored = Number.parseInt(stGet(SHORTCUT_COUNT_KEY, defaultCount), 10);
    if (Number.isFinite(stored) && stored > 0) return stored;
    return defaultCount;
}

function persistShortcutCount(count) {
    stSet(SHORTCUT_COUNT_KEY, String(count));
}

function getShortcutCountFromDom() {
    return shortcutContainer ? shortcutContainer.querySelectorAll('.shortcut').length : 0;
}

function buildShortcutElement(id, label, defaultQuery = '') {
    const sc = document.createElement('div');
    sc.className = 'shortcut';
    sc.dataset.id = id;
    sc.dataset.default = defaultQuery;
    sc.innerHTML = `
        <button class="head">
            <span class="rename">✏️</span>
            <span class="label">${escapeHtml(label)}</span>
            <span class="chev">▶</span>
        </button>
        <div class="panel"><input placeholder="Lucene query"></div>
    `;
    return sc;
}

function appendShortcutElement(index) {
    if (!shortcutContainer) return null;
    const id = `sc${index}`;
    const el = buildShortcutElement(id, `Shortcut ${index + 1}`);
    shortcutContainer.appendChild(el);
    return el;
}

function restoreShortcutList() {
    if (!shortcutContainer) return;
    const baseCount = getShortcutCountFromDom();
    const storedCount = Math.max(1, readStoredShortcutCount(baseCount));
    const currentCount = baseCount;
    if (currentCount < storedCount) {
        for (let i = currentCount; i < storedCount; i += 1) {
            appendShortcutElement(i);
        }
    } else if (currentCount > storedCount) {
        for (let i = currentCount; i > storedCount; i -= 1) {
            const last = shortcutContainer.querySelector('.shortcut:last-of-type');
            if (!last) break;
            shortcutStorage.setLabel(last.dataset.id, null);
            shortcutStorage.setQuery(last.dataset.id, null);
            last.remove();
        }
    }
    persistShortcutCount(storedCount);
}

function updateShortcutControls() {
    if (!shortcutRemoveBtn) return;
    const count = getShortcutCountFromDom();
    shortcutRemoveBtn.disabled = count <= 1;
}

function removeLastShortcut() {
    if (!shortcutContainer) return;
    const items = shortcutContainer.querySelectorAll('.shortcut');
    if (!items.length || items.length <= 1) return;
    const last = items[items.length - 1];
    const id = last.dataset.id;
    shortcutStorage.setLabel(id, null);
    shortcutStorage.setQuery(id, null);
    last.remove();
    persistShortcutCount(items.length - 1);
    updateShortcutControls();
}

function bindShortcutControls() {
    if (shortcutAddBtn) {
        shortcutAddBtn.addEventListener('click', () => {
            const nextIndex = getShortcutCountFromDom();
            const el = appendShortcutElement(nextIndex);
            if (el) {
                initShortcut(el);
                persistShortcutCount(nextIndex + 1);
                updateShortcutControls();
            }
        });
    }
    if (shortcutRemoveBtn) {
        shortcutRemoveBtn.addEventListener('click', () => removeLastShortcut());
    }
}

function initShortcut(sc) {
    if (!sc || sc.dataset.bound === 'true') return;
    sc.dataset.bound = 'true';
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
            setSearchScope('all', { syncUrl: true });
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
                    setSearchScope('all', { syncUrl: true });
                    runSearch(q);
                }
            } else if (e.key === 'Escape') {
                sc.classList.remove('open');
                headBtn.setAttribute('aria-expanded', false);
            }
        });

        inputEl.addEventListener('blur', () => writeQuery(inputEl.value.trim()));
    }
}

function setupShortcuts() {
    restoreShortcutList();
    document.querySelectorAll('.shortcut').forEach(sc => initShortcut(sc));
    bindShortcutControls();
    updateShortcutControls();
}

/* ===================== Search ===================== */
function runSearch(raw, options = {}) {
    const text = (raw ?? '').toString();
    const highlightTerms = getHighlightTerms(text);
    if (options.updateInput !== false && searchInput) {
        searchInput.value = text;
    }
    const scopeOption = getScopeOption(searchScopeKey);
    const scopeType = scopeOption ? scopeOption.type : null;
    const prepared = buildScopedQuery(text, scopeType);
    if (!prepared) {
        if (!options.skipUrlUpdate) updateUrlState(text);
        return;
    }
    if (!options.skipUrlUpdate) {
        updateUrlState(text);
    }
    runLucene(prepared, scopeOption, highlightTerms);
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

function renderTypeCell(typeValue) {
    const resolved = resolveScopeKey(typeValue);
    if (resolved && resolved !== 'all') {
        const option = getScopeOption(resolved);
        if (option && option.key !== 'all') {
            const label = option.label;
            return `<button type="button" class="search-type-pill" data-search-type="${option.key}">${escapeHtml(label)}</button>`;
        }
    }
    return escapeHtml(typeValue ?? '');
}

function filterHitsByScope(hits, scopeKey) {
    if (!Array.isArray(hits)) return [];
    const canonicalScope = resolveScopeKey(scopeKey);
    if (!canonicalScope || canonicalScope === 'all') return hits;
    return hits.filter(hit => resolveScopeKey(hit?.type) === canonicalScope);
}

async function runLucene(q, scopeOption, highlightTerms) {
    const query = (q ?? '').trim();
    if (!query) {
        resultArea.textContent = '(no matches)';
        return;
    }
    try {
        setBusy(resultArea, true);
        const url = (typeof window !== 'undefined' && window?.location)
            ? new URL(API.search, window.location.origin)
            : new URL(API.search, 'http://localhost');
        url.searchParams.set('q', query);
        const scopeType = scopeOption?.type || null;
        const scopeKey = scopeOption?.key || null;
        if (scopeType) {
            url.searchParams.set('type', scopeType);
        } else {
            url.searchParams.delete('type');
        }
        const res  = await fetch(url.toString());
        const hits = await res.json();
        const filteredHits = filterHitsByScope(hits, scopeKey);
        if (!Array.isArray(filteredHits) || !filteredHits.length) {
            resultArea.textContent = '(no matches)';
            return;
        }

        const rows = filteredHits.map((h, i) => {
            const snippet = (h.snippet ?? '').trim();
            const snippetHtml = snippet ? `<div class="hit-snippet"><small>${highlightMatches(snippet, highlightTerms)}</small></div>` : '';
            const typeArg = JSON.stringify(h.type ?? '');
            const idArg = JSON.stringify(h.id ?? '');
            const idDisplay = renderIdDisplay(h.id);
            return `
      <tr onclick='toDetails(${typeArg},${idArg})' style="cursor:pointer">
        <td>${renderTypeCell(h.type)}</td>
        <td><div class="hit-text">${highlightMatches(h.text ?? '', highlightTerms)}</div></td>
        <td>${snippetHtml}<div id="info-${i}" class="hit-info"></div></td>
        <td title="${escapeHtml(idDisplay.title)}">${idDisplay.inner}</td>
      </tr>`;
        }).join('');

        resultArea.innerHTML = `
      <div class="table-scroll">
        <table>
          <tr><th>Type</th><th>Name / Text</th><th>Snippet / Info</th><th>ID</th></tr>
          ${rows}
        </table>
      </div>`;

        enrichRows(filteredHits);

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

async function fetchSiteSoftwareSummary(statusKey) {
    const params = new URLSearchParams();
    if (statusKey) {
        params.set('status', statusKey);
    }
    const query = params.toString();
    const url = query ? `${API.siteSoftwareSummary}?${query}` : API.siteSoftwareSummary;
    const res = await fetch(url);
    if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
    }
    return res.json();
}

function toSiteSoftwareSummaryMap(entries) {
    const map = new Map();
    if (!Array.isArray(entries)) {
        return map;
    }
    entries.forEach(entry => {
        if (!entry) return;
        const normalizedSiteId = getNormalizedSiteId(entry);
        if (!normalizedSiteId) return;
        const countRaw = entry.count ?? entry.statusCount ?? entry.total ?? 0;
        const parsed = Number(countRaw);
        map.set(normalizedSiteId, Number.isFinite(parsed) ? parsed : 0);
    });
    return map;
}

async function renderSiteTableWithSummary(rows, baseColumns, tableTypeInfo, tableName) {
    const name = tableName || 'Site';

    setBusy(resultArea, true);

    let summaryEntries = [];
    let summaryError = null;
    try {
        summaryEntries = await fetchSiteSoftwareSummary('Installed');
    } catch (err) {
        summaryError = err;
        console.error('Site software summary could not be loaded', err);
    }

    try {
        const summaryMap = toSiteSoftwareSummaryMap(summaryEntries);
        const columnLabel = 'Software – Installed';
        const allColumns = [...baseColumns, columnLabel];

        const formattedRows = rows.map(row => {
            const siteIdKey = getNormalizedSiteId(row);
            const count = siteIdKey ? summaryMap.get(siteIdKey) : undefined;
            let display;
            if (summaryError) {
                display = 'Unavailable';
            } else if (!Number.isFinite(count)) {
                display = '0 records';
            } else if (count === 0) {
                display = '0 records';
            } else if (count === 1) {
                display = '1 record';
            } else {
                display = `${count} records`;
            }
            return {
                ...row,
                [columnLabel]: display,
            };
        });

        const hdr = allColumns.map(c => `<th>${escapeHtml(c)}</th>`).join('');
        const body = formattedRows
            .map(r => `<tr>${allColumns.map(c => renderTableCell(name, c, r[c])).join('')}</tr>`)
            .join('');

        const safeName = escapeHtml(name);
        const titleQuery = tableTypeInfo.typeToken ? escapeHtml(tableTypeInfo.typeToken) : null;
        const titleControl = titleQuery
            ? `<button type="button" class="table-quick-filter" data-query="${titleQuery}">${safeName}</button>`
            : safeName;

        const statusMessage = summaryError
            ? '<div class="empty" role="status">Software summary unavailable.</div>'
            : '';

        resultArea.innerHTML = `
            <h2>${titleControl}</h2>
            ${statusMessage}
            <div class="table-scroll">
                <table>
                    <tr>${hdr}</tr>${body}
                </table>
            </div>`;
    } finally {
        setBusy(resultArea, false);
    }
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
        const tableTypeInfo = getTableTypeInfo(name);
        if (tableTypeInfo.detailType && isScopeSelectable(tableTypeInfo.detailType)) {
            setSearchScope(tableTypeInfo.detailType);
        }
        if (name && name.toLowerCase() === 'site') {
            await renderSiteTableWithSummary(rows, cols, tableTypeInfo, name);
            return;
        }
        const hdr  = cols.map(c => `<th>${escapeHtml(c)}</th>`).join('');
        const body = rows.map(r => `<tr>${cols.map(c => renderTableCell(name, c, r[c])).join('')}</tr>`).join('');
        const { typeToken } = tableTypeInfo;
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