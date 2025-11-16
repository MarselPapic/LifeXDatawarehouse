(function(){
    const API = {
        data: '/api/reports/data',
        csv: '/api/reports/export/csv'
    };

    const DEFAULT_RANGE = 'next90';

    const PRESETS = ['next30', 'next90', 'next180', 'custom'];

    const state = {
        range: DEFAULT_RANGE,
        startDate: '',
        endDate: ''
    };

    const elements = {
        form: document.getElementById('range-form'),
        range: document.getElementById('range'),
        startDate: document.getElementById('start-date'),
        endDate: document.getElementById('end-date'),
        customStart: document.getElementById('custom-range'),
        customEnd: document.getElementById('custom-range-end'),
        reset: document.getElementById('reset'),
        tableWrapper: document.getElementById('table-wrapper'),
        reportStatus: document.getElementById('report-status'),
        generatedAt: document.getElementById('generated-at'),
        exportCsv: document.getElementById('export-csv'),
        panel: document.getElementById('report-panel'),
        selectionInfo: document.getElementById('selection-info')
    };

    document.addEventListener('DOMContentLoaded', init);

    async function init(){
        document.title = 'LifeX – Support end reports';
        wireForm();
        applyStateToForm();
        await loadReport();
    }

    function wireForm(){
        if (!elements.form) return;
        elements.form.addEventListener('submit', evt => {
            evt.preventDefault();
            readFormState();
            loadReport();
        });
        elements.range.addEventListener('change', () => {
            toggleCustomRange(elements.range.value === 'custom');
        });
        elements.reset.addEventListener('click', () => {
            state.range = DEFAULT_RANGE;
            state.startDate = '';
            state.endDate = '';
            applyStateToForm();
            loadReport();
        });
    }

    function applyStateToForm(){
        const preset = normalizeRange(state.range);
        state.range = preset;
        elements.range.value = preset;
        elements.startDate.value = state.startDate;
        elements.endDate.value = state.endDate;
        toggleCustomRange(preset === 'custom');
        updateSelectionInfo();
    }

    function normalizeRange(range){
        if (PRESETS.includes(range)) return range;
        return DEFAULT_RANGE;
    }

    function toggleCustomRange(show){
        if (elements.customStart) elements.customStart.hidden = !show;
        if (elements.customEnd) elements.customEnd.hidden = !show;
        if (!show) {
            elements.startDate.value = '';
            elements.endDate.value = '';
        }
    }

    function readFormState(){
        state.range = normalizeRange(elements.range.value || DEFAULT_RANGE);
        if (state.range === 'custom') {
            state.startDate = elements.startDate.value;
            state.endDate = elements.endDate.value;
        } else {
            state.startDate = '';
            state.endDate = '';
        }
        updateSelectionInfo();
    }

    async function loadReport(){
        const params = buildParams();
        setBusy(true);
        setStatus('Loading…');
        try {
            const res = await fetch(`${API.data}?${params}`);
            if (!res.ok) {
                if (res.status >= 400 && res.status < 500) {
                    throw Object.assign(new Error('Client error'), { status: res.status });
                }
                throw new Error('HTTP ' + res.status);
            }
            const payload = await res.json();
            renderTable(payload.table || payload);
            updateGeneratedAt(payload.generatedAt);
            updateExportLink(params);
        } catch (err) {
            console.error('Report fetch failed', err);
            const message = err.status ? 'No software releases found for the selected range.' : 'Report could not be loaded.';
            showError(message);
        } finally {
            setBusy(false);
        }
    }

    function renderTable(table){
        elements.tableWrapper.innerHTML = '';
        if (!table || !Array.isArray(table.columns)) {
            setStatus('No data found.');
            return;
        }
        if (!table.rows || !table.rows.length) {
            const empty = document.createElement('div');
            empty.className = 'chart-empty';
            empty.textContent = table.emptyMessage || 'No software found for the selected period.';
            elements.tableWrapper.appendChild(empty);
            setStatus(empty.textContent);
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
                tr.appendChild(td);
            });
            tbody.appendChild(tr);
        });
        tbl.appendChild(tbody);
        elements.tableWrapper.appendChild(tbl);

        const count = table.rows.length;
        setStatus(`${count} record${count === 1 ? '' : 's'} loaded.`);
    }

    function updateGeneratedAt(timestamp){
        if (!elements.generatedAt) return;
        elements.generatedAt.textContent = timestamp ? `Updated: ${timestamp}` : '';
    }

    function updateExportLink(queryString){
        if (!elements.exportCsv) return;
        elements.exportCsv.href = `${API.csv}?${queryString}`;
    }

    function buildParams(){
        const params = new URLSearchParams();
        const preset = normalizeRange(state.range);
        params.set('preset', preset);
        if (preset === 'custom') {
            if (state.startDate) params.set('from', state.startDate);
            if (state.endDate) params.set('to', state.endDate);
        }
        return params.toString();
    }

    function updateSelectionInfo(){
        if (!elements.selectionInfo) return;
        const preset = normalizeRange(state.range);
        if (preset === 'custom' && state.startDate && state.endDate) {
            elements.selectionInfo.textContent = `Custom range: ${state.startDate} – ${state.endDate}`;
        } else {
            const labels = {
                next30: 'Next 30 days',
                next90: 'Next 90 days',
                next180: 'Next 180 days',
                custom: 'Custom'
            };
            elements.selectionInfo.textContent = labels[preset] || '';
        }
    }

    function setStatus(message){
        if (!elements.reportStatus) return;
        elements.reportStatus.textContent = message || '';
        elements.reportStatus.classList.remove('error');
    }

    function showError(message){
        if (!elements.reportStatus) return;
        elements.reportStatus.textContent = message;
        elements.reportStatus.classList.add('error');
    }

    function setBusy(busy){
        if (!elements.panel) return;
        elements.panel.setAttribute('aria-busy', busy ? 'true' : 'false');
    }
})();
