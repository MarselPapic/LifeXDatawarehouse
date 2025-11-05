(function(){
    const API = {
        options: '/api/reports/options',
        data: '/api/reports/data',
        csv: '/api/reports/export/csv',
        pdf: '/api/reports/export/pdf'
    };

    const DEFAULT_PERIOD = 'quarter';
    const numberFormat = new Intl.NumberFormat('en-US');

    const state = {
        type: 'DIFFERENCE',
        period: DEFAULT_PERIOD,
        query: '',
        variant: '',
        status: '',
        from: '',
        to: ''
    };

    const elements = {
        typeList: document.getElementById('type-list'),
        tabbar: document.getElementById('tabbar'),
        period: document.getElementById('period'),
        query: document.getElementById('query'),
        variant: document.getElementById('variant'),
        status: document.getElementById('install-status'),
        from: document.getElementById('from'),
        to: document.getElementById('to'),
        customRange: document.getElementById('custom-range'),
        form: document.getElementById('filter-form'),
        reset: document.getElementById('reset-filter'),
        kpiGrid: document.getElementById('kpi-grid'),
        chart: document.getElementById('chart'),
        chartTitle: document.getElementById('chart-title'),
        generatedAt: document.getElementById('generated-at'),
        tableWrapper: document.getElementById('table-wrapper'),
        reportStatus: document.getElementById('report-status'),
        panel: document.getElementById('report-panel'),
        filterInfo: document.getElementById('filter-info'),
        refreshInfo: document.getElementById('refresh-info'),
        exportCsv: document.getElementById('export-csv'),
        exportPdf: document.getElementById('export-pdf'),
        quickLinks: document.querySelectorAll('.quick-link')
    };

    let options;
    let fetchToken = 0;

    document.addEventListener('DOMContentLoaded', init);

    async function init(){
        document.title = 'LifeX – Reports';
        wireForm();
        wireQuickLinks();
        try {
            await loadOptions();
            applyStateToForm();
            setActiveType(state.type, {skipReload: true});
            await loadReport();
        } catch (err) {
            console.error('Options/initial data failed', err);
            showError('Metadata could not be loaded. Please try again later.');
        }
    }

    function wireForm(){
        if (!elements.form) return;
        elements.form.addEventListener('submit', evt => {
            evt.preventDefault();
            readFormState();
            loadReport();
        });
        elements.period.addEventListener('change', () => {
            toggleCustomRange(elements.period.value === 'custom');
        });
        if (elements.reset) {
            elements.reset.addEventListener('click', () => {
                state.period = DEFAULT_PERIOD;
                state.query = '';
                state.variant = '';
                state.status = '';
                state.from = '';
                state.to = '';
                applyStateToForm();
                loadReport();
            });
        }
    }

    function wireQuickLinks(){
        elements.quickLinks.forEach(btn => {
            btn.addEventListener('click', () => {
                const type = btn.dataset.type;
                const period = btn.dataset.period || DEFAULT_PERIOD;
                state.period = period;
                state.from = '';
                state.to = '';
                applyStateToForm();
                setActiveType(type, {skipReload: true});
                loadReport();
            });
        });
    }

    async function loadOptions(){
        const res = await fetch(API.options);
        if (!res.ok) throw new Error('options HTTP ' + res.status);
        options = await res.json();
        populateTypes(options.types || []);
        populateTabs(options.types || []);
        populatePeriods(options.periods || {});
        populateVariants(options.variants || []);
        populateStatuses(options.installStatuses || []);
    }

    function populateTypes(types){
        if (!elements.typeList) return;
        elements.typeList.innerHTML = '';
        types.forEach(info => {
            const li = document.createElement('li');
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'type-btn';
            btn.dataset.type = info.id;
            btn.textContent = info.label;
            btn.title = info.description || '';
            btn.addEventListener('click', () => {
                setActiveType(info.id);
            });
            li.appendChild(btn);
            elements.typeList.appendChild(li);
        });
    }

    function populateTabs(types){
        if (!elements.tabbar) return;
        elements.tabbar.innerHTML = '';
        types.forEach(info => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.id = `tab-${info.id.toLowerCase()}`;
            btn.dataset.type = info.id;
            btn.setAttribute('role', 'tab');
            btn.setAttribute('aria-selected', 'false');
            btn.textContent = info.label;
            btn.addEventListener('click', () => setActiveType(info.id));
            elements.tabbar.appendChild(btn);
        });
    }

    function populatePeriods(periods){
        if (!elements.period) return;
        elements.period.innerHTML = '';
        Object.entries(periods).forEach(([value, label]) => {
            const opt = document.createElement('option');
            opt.value = value;
            opt.textContent = label;
            elements.period.appendChild(opt);
        });
        if (!periods[DEFAULT_PERIOD]) {
            const opt = document.createElement('option');
            opt.value = DEFAULT_PERIOD;
            opt.textContent = 'This quarter';
            elements.period.appendChild(opt);
        }
    }

    function populateVariants(variants){
        if (!elements.variant) return;
        elements.variant.innerHTML = '<option value="">All</option>';
        variants.forEach(v => {
            const opt = document.createElement('option');
            opt.value = v.code;
            opt.textContent = v.label + (v.active ? '' : ' (inactive)');
            elements.variant.appendChild(opt);
        });
    }

    function populateStatuses(statuses){
        if (!elements.status) return;
        elements.status.innerHTML = '<option value="">All</option>';
        statuses.forEach(status => {
            const opt = document.createElement('option');
            const value = status.code || status;
            opt.value = value;
            opt.textContent = status.label || value;
            elements.status.appendChild(opt);
        });
    }

    function applyStateToForm(){
        if (!elements.form) return;
        elements.period.value = state.period || DEFAULT_PERIOD;
        elements.query.value = state.query || '';
        elements.variant.value = state.variant || '';
        if (elements.status) elements.status.value = state.status || '';
        elements.from.value = state.from || '';
        elements.to.value = state.to || '';
        toggleCustomRange(elements.period.value === 'custom');
    }

    function toggleCustomRange(show){
        if (!elements.customRange) return;
        elements.customRange.hidden = !show;
        if (!show) {
            elements.from.value = '';
            elements.to.value = '';
        }
    }

    function readFormState(){
        state.period = elements.period.value || DEFAULT_PERIOD;
        state.query = elements.query.value.trim();
        state.variant = elements.variant.value;
        state.status = elements.status ? elements.status.value : '';
        if (state.period === 'custom') {
            state.from = elements.from.value;
            state.to = elements.to.value;
        } else {
            state.from = '';
            state.to = '';
        }
    }

    function setActiveType(type, opts = {}){
        state.type = type;
        if (elements.typeList) {
            elements.typeList.querySelectorAll('.type-btn').forEach(btn => {
                btn.classList.toggle('active', btn.dataset.type === type);
            });
        }
        if (elements.tabbar) {
            elements.tabbar.querySelectorAll('button').forEach(btn => {
                const active = btn.dataset.type === type;
                btn.setAttribute('aria-selected', active ? 'true' : 'false');
                if (active && elements.panel) {
                    elements.panel.setAttribute('aria-labelledby', btn.id);
                }
            });
        }
        updateFilterInfo();
        if (!opts.skipReload) {
            loadReport();
        }
    }

    async function loadReport(){
        const params = buildParams();
        const token = ++fetchToken;
        setBusy(true);
        try {
            const res = await fetch(`${API.data}?${params}`);
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            if (token !== fetchToken) return;
            renderReport(data);
            updateExports();
        } catch (err) {
            if (token !== fetchToken) return;
            console.error('Report fetch failed', err);
            showError('Report could not be loaded.');
        } finally {
            if (token === fetchToken) {
                setBusy(false);
            }
        }
    }

    function renderReport(data){
        if (elements.reportStatus) {
            elements.reportStatus.classList.remove('error');
        }
        renderKpis(data.kpis || []);
        renderChart(data.chart || [], data.chartTitle || '');
        renderTable(data.table);
        updateGeneratedAt(data.generatedAt);
        updateFilterInfo();
        updateRefreshInfo(data.generatedAt);
    }

    function renderKpis(kpis){
        if (!elements.kpiGrid) return;
        if (!kpis.length) {
            elements.kpiGrid.innerHTML = '';
            return;
        }
        const frag = document.createDocumentFragment();
        kpis.forEach(kpi => {
            const card = document.createElement('div');
            card.className = 'kpi-card';
            const label = document.createElement('div');
            label.className = 'kpi-label';
            label.textContent = kpi.label || '';
            const value = document.createElement('div');
            value.className = 'kpi-value';
            value.textContent = kpi.value || '';
            card.append(label, value);
            if (kpi.hint) {
                const hint = document.createElement('div');
                hint.className = 'kpi-hint';
                hint.textContent = kpi.hint;
                card.appendChild(hint);
            }
            frag.appendChild(card);
        });
        elements.kpiGrid.innerHTML = '';
        elements.kpiGrid.appendChild(frag);
    }

    function renderChart(chart, title){
        if (!elements.chart) return;
        elements.chart.innerHTML = '';
        elements.chartTitle.textContent = chart.length ? title : '';
        if (!chart.length) {
            const empty = document.createElement('div');
            empty.className = 'chart-empty';
            empty.textContent = 'No chart data available.';
            elements.chart.appendChild(empty);
            return;
        }
        const max = Math.max(...chart.map(slice => slice.value || 0));
        chart.forEach(slice => {
            const row = document.createElement('div');
            row.className = 'chart-row';
            const label = document.createElement('span');
            label.className = 'chart-label';
            label.textContent = slice.label;
            const bar = document.createElement('div');
            bar.className = 'chart-bar';
            const fill = document.createElement('span');
            fill.className = 'chart-fill';
            const ratio = max > 0 ? Math.max((slice.value || 0) / max, 0.05) : 0;
            fill.style.width = (ratio * 100) + '%';
            fill.textContent = numberFormat.format(Math.round(slice.value || 0));
            bar.appendChild(fill);
            row.append(label, bar);
            elements.chart.appendChild(row);
        });
    }

    function renderTable(table){
        if (!elements.tableWrapper) return;
        elements.tableWrapper.innerHTML = '';
        if (!table || !Array.isArray(table.columns)) {
            elements.reportStatus.textContent = 'No data found.';
            return;
        }
        if (!table.rows || !table.rows.length) {
            const empty = document.createElement('div');
            empty.className = 'chart-empty';
            empty.textContent = table.emptyMessage || 'No data in the selected period.';
            elements.tableWrapper.appendChild(empty);
            elements.reportStatus.textContent = empty.textContent;
            return;
        }

        const tbl = document.createElement('table');
        if (table.caption) {
            const caption = document.createElement('caption');
            caption.textContent = table.caption;
            tbl.appendChild(caption);
        }
        const thead = document.createElement('thead');
        const headerRow = document.createElement('tr');
        table.columns.forEach(col => {
            const th = document.createElement('th');
            th.scope = 'col';
            th.textContent = col.label || col.key;
            headerRow.appendChild(th);
        });
        thead.appendChild(headerRow);
        tbl.appendChild(thead);

        const tbody = document.createElement('tbody');
        table.rows.forEach(row => {
            const tr = document.createElement('tr');
            table.columns.forEach(col => {
                const td = document.createElement('td');
                const value = row[col.key];
                td.textContent = value != null ? value : '';
                if (col.key === 'severity' || col.key === 'status' || col.key === 'compliance') {
                    const slug = slugify(String(value || ''));
                    if (slug) td.classList.add(`severity-${slug}`);
                }
                if (col.align === 'right') {
                    td.style.textAlign = 'right';
                }
                tr.appendChild(td);
            });
            tbody.appendChild(tr);
        });
        tbl.appendChild(tbody);
        elements.tableWrapper.appendChild(tbl);

        const count = table.rows.length;
        elements.reportStatus.classList.remove('error');
        elements.reportStatus.textContent = `${count} record${count === 1 ? '' : 's'} loaded.`;
    }

    function updateGeneratedAt(timestamp){
        if (!elements.generatedAt) return;
        elements.generatedAt.textContent = timestamp ? `Updated: ${timestamp}` : '';
    }

    function updateFilterInfo(){
        if (!elements.filterInfo || !options) return;
        const typeInfo = (options.types || []).find(t => t.id === state.type);
        const typeLabel = typeInfo ? typeInfo.label : state.type;
        const periods = options.periods || {};
        const periodLabel = periods[state.period] || periods[DEFAULT_PERIOD] || '';
        const pieces = [typeLabel];
        if (periodLabel) pieces.push(periodLabel);
        if (state.variant) pieces.push(`Variant: ${state.variant}`);
        if (state.status) {
            const statuses = options.installStatuses || [];
            const info = statuses.find(s => s.code === state.status);
            const label = info ? info.label : state.status;
            pieces.push(`Status: ${label}`);
        }
        if (state.query) pieces.push(`Search: ${state.query}`);
        elements.filterInfo.textContent = pieces.join(' · ');
    }

    function updateRefreshInfo(timestamp){
        if (!elements.refreshInfo) return;
        elements.refreshInfo.textContent = timestamp ? `Last updated on ${timestamp}.` : '';
    }

    function updateExports(){
        const queryString = buildParams();
        if (elements.exportCsv) {
            elements.exportCsv.href = `${API.csv}?${queryString}`;
        }
        if (elements.exportPdf) {
            elements.exportPdf.href = `${API.pdf}?${queryString}`;
        }
    }

    function buildParams(){
        const params = new URLSearchParams();
        params.set('type', state.type);
        params.set('period', state.period || DEFAULT_PERIOD);
        if (state.query) params.set('query', state.query);
        if (state.variant) params.set('variant', state.variant);
        if (state.status) params.set('installStatus', state.status);
        if (state.period === 'custom') {
            if (state.from) params.set('from', state.from);
            if (state.to) params.set('to', state.to);
        }
        return params.toString();
    }

    function setBusy(busy){
        if (!elements.panel) return;
        if (busy) {
            elements.panel.setAttribute('aria-busy', 'true');
        } else {
            elements.panel.setAttribute('aria-busy', 'false');
        }
    }

    function showError(message){
        if (!elements.reportStatus) return;
        elements.reportStatus.textContent = message;
        elements.reportStatus.classList.add('error');
    }

    function slugify(value){
        return value
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/^-+|-+$/g, '');
    }
})();
