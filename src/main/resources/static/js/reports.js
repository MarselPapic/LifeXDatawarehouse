(function () {
    const API = {
        data: '/api/reports/data',
        summary: '/api/reports/summary',
        csv: '/api/reports/export/csv',
        pdf: '/api/reports/export/pdf',
        xlsx: '/api/reports/export/xlsx'
    };

    const DEFAULT_RANGE = 'next90';
    const DEFAULT_VIEW = 'support-end';
    const PRESETS = ['last30', 'next30', 'next90', 'next180', 'quarter', 'custom'];
    const VIEWS = ['support-end', 'lifecycle-status', 'account-risk'];

    const state = {
        view: DEFAULT_VIEW,
        range: DEFAULT_RANGE,
        startDate: '',
        endDate: ''
    };

    const elements = {
        form: document.getElementById('range-form'),
        view: document.getElementById('view'),
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
        exportPdf: document.getElementById('export-pdf'),
        exportXlsx: document.getElementById('export-xlsx'),
        panel: document.getElementById('report-panel'),
        selectionInfo: document.getElementById('selection-info'),
        summaryTotal: document.getElementById('summary-total'),
        summaryOverdue: document.getElementById('summary-overdue'),
        summaryDue30: document.getElementById('summary-due30'),
        summaryDue90: document.getElementById('summary-due90'),
        summaryAccounts: document.getElementById('summary-accounts'),
        summarySites: document.getElementById('summary-sites')
    };

    document.addEventListener('DOMContentLoaded', init);

    async function init() {
        document.title = 'LifeX - Reporting';
        wireForm();
        applyStateToForm();
        await loadReport();
    }

    function wireForm() {
        if (!elements.form) return;
        elements.form.addEventListener('submit', evt => {
            evt.preventDefault();
            readFormState();
            loadReport();
        });
        if (elements.range) {
            elements.range.addEventListener('change', () => {
                toggleCustomRange(elements.range.value === 'custom');
            });
        }
        if (elements.view) {
            elements.view.addEventListener('change', () => {
                state.view = normalizeView(elements.view.value);
                updateSelectionInfo();
            });
        }
        if (elements.reset) {
            elements.reset.addEventListener('click', () => {
                state.view = DEFAULT_VIEW;
                state.range = DEFAULT_RANGE;
                state.startDate = '';
                state.endDate = '';
                applyStateToForm();
                loadReport();
            });
        }
    }

    function applyStateToForm() {
        const preset = normalizeRange(state.range);
        const view = normalizeView(state.view);
        state.range = preset;
        state.view = view;
        if (elements.range) elements.range.value = preset;
        if (elements.view) elements.view.value = view;
        if (elements.startDate) elements.startDate.value = state.startDate;
        if (elements.endDate) elements.endDate.value = state.endDate;
        toggleCustomRange(preset === 'custom');
        updateSelectionInfo();
    }

    function normalizeRange(range) {
        if (PRESETS.includes(range)) return range;
        return DEFAULT_RANGE;
    }

    function normalizeView(view) {
        if (VIEWS.includes(view)) return view;
        return DEFAULT_VIEW;
    }

    function toggleCustomRange(show) {
        if (elements.customStart) elements.customStart.hidden = !show;
        if (elements.customEnd) elements.customEnd.hidden = !show;
        if (!show) {
            if (elements.startDate) elements.startDate.value = '';
            if (elements.endDate) elements.endDate.value = '';
        }
    }

    function readFormState() {
        state.view = normalizeView((elements.view && elements.view.value) || DEFAULT_VIEW);
        state.range = normalizeRange((elements.range && elements.range.value) || DEFAULT_RANGE);
        if (state.range === 'custom') {
            state.startDate = elements.startDate ? elements.startDate.value : '';
            state.endDate = elements.endDate ? elements.endDate.value : '';
        } else {
            state.startDate = '';
            state.endDate = '';
        }
        updateSelectionInfo();
    }

    async function loadReport() {
        const params = buildParams();
        const summaryParams = buildSummaryParams();
        setBusy(true);
        setStatus('Loading...');
        try {
            const [reportRes, summaryRes] = await Promise.all([
                fetch(`${API.data}?${params}`),
                fetch(`${API.summary}?${summaryParams}`)
            ]);

            if (!reportRes.ok) {
                if (reportRes.status >= 400 && reportRes.status < 500) {
                    throw Object.assign(new Error('Client error'), { status: reportRes.status });
                }
                throw new Error('HTTP ' + reportRes.status);
            }
            if (!summaryRes.ok) {
                throw new Error('Summary HTTP ' + summaryRes.status);
            }

            const payload = await reportRes.json();
            const summary = await summaryRes.json();
            renderTable(payload.table || payload);
            renderSummary(summary);
            updateGeneratedAt(payload.generatedAt);
            updateExportLinks(params);
        } catch (err) {
            console.error('Report fetch failed', err);
            const message = err.status ? 'No deployments found for the selected range.' : 'Report could not be loaded.';
            showError(message);
        } finally {
            setBusy(false);
        }
    }

    function renderTable(table) {
        if (!elements.tableWrapper) return;
        elements.tableWrapper.innerHTML = '';
        if (!table || !Array.isArray(table.columns)) {
            setStatus('No data found.');
            return;
        }
        if (!table.rows || !table.rows.length) {
            const empty = document.createElement('div');
            empty.className = 'chart-empty';
            empty.textContent = table.emptyMessage || 'No rows found for the selected period.';
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

    function renderSummary(summary) {
        if (!summary || typeof summary !== 'object') {
            setSummaryValue(elements.summaryTotal, '-');
            setSummaryValue(elements.summaryOverdue, '-');
            setSummaryValue(elements.summaryDue30, '-');
            setSummaryValue(elements.summaryDue90, '-');
            setSummaryValue(elements.summaryAccounts, '-');
            setSummaryValue(elements.summarySites, '-');
            return;
        }
        setSummaryValue(elements.summaryTotal, summary.totalDeployments);
        setSummaryValue(elements.summaryOverdue, summary.overdue);
        setSummaryValue(elements.summaryDue30, summary.dueIn30Days);
        setSummaryValue(elements.summaryDue90, summary.dueIn90Days);
        setSummaryValue(elements.summaryAccounts, summary.distinctAccounts);
        setSummaryValue(elements.summarySites, summary.distinctSites);
    }

    function setSummaryValue(el, value) {
        if (!el) return;
        el.textContent = value != null ? String(value) : '-';
    }

    function updateGeneratedAt(timestamp) {
        if (!elements.generatedAt) return;
        elements.generatedAt.textContent = timestamp ? `Updated: ${timestamp}` : '';
    }

    function updateExportLinks(queryString) {
        if (elements.exportCsv) elements.exportCsv.href = `${API.csv}?${queryString}`;
        if (elements.exportPdf) elements.exportPdf.href = `${API.pdf}?${queryString}`;
        if (elements.exportXlsx) elements.exportXlsx.href = `${API.xlsx}?${queryString}`;
    }

    function buildParams() {
        const params = new URLSearchParams();
        const view = normalizeView(state.view);
        const preset = normalizeRange(state.range);
        params.set('view', view);
        params.set('preset', preset);
        if (preset === 'custom') {
            if (state.startDate) params.set('from', state.startDate);
            if (state.endDate) params.set('to', state.endDate);
        }
        return params.toString();
    }

    function buildSummaryParams() {
        const params = new URLSearchParams();
        const preset = normalizeRange(state.range);
        params.set('preset', preset);
        if (preset === 'custom') {
            if (state.startDate) params.set('from', state.startDate);
            if (state.endDate) params.set('to', state.endDate);
        }
        return params.toString();
    }

    function updateSelectionInfo() {
        if (!elements.selectionInfo) return;
        const preset = normalizeRange(state.range);
        const view = normalizeView(state.view);
        const viewLabels = {
            'support-end': 'Support end details',
            'lifecycle-status': 'Lifecycle status distribution',
            'account-risk': 'Account risk overview'
        };
        const rangeLabels = {
            last30: 'Last 30 days',
            next30: 'Next 30 days',
            next90: 'Next 90 days',
            next180: 'Next 180 days',
            quarter: 'Current quarter',
            custom: 'Custom'
        };

        if (preset === 'custom' && state.startDate && state.endDate) {
            elements.selectionInfo.textContent = `${viewLabels[view]} | Custom range: ${state.startDate} - ${state.endDate}`;
            return;
        }
        elements.selectionInfo.textContent = `${viewLabels[view]} | ${rangeLabels[preset] || ''}`;
    }

    function setStatus(message) {
        if (!elements.reportStatus) return;
        elements.reportStatus.textContent = message || '';
        elements.reportStatus.classList.remove('error');
    }

    function showError(message) {
        if (!elements.reportStatus) return;
        elements.reportStatus.textContent = message;
        elements.reportStatus.classList.add('error');
    }

    function setBusy(busy) {
        if (!elements.panel) return;
        elements.panel.setAttribute('aria-busy', busy ? 'true' : 'false');
    }
})();
