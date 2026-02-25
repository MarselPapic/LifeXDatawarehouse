(function () {
    const API = {
        data: '/api/reports/data',
        summary: '/api/reports/summary',
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
        endDate: '',
        archiveState: 'ACTIVE'
    };

    const elements = {
        form: document.getElementById('range-form'),
        view: document.getElementById('view'),
        range: document.getElementById('range'),
        archiveState: document.getElementById('archive-state'),
        startDate: document.getElementById('start-date'),
        endDate: document.getElementById('end-date'),
        customStart: document.getElementById('custom-range'),
        customEnd: document.getElementById('custom-range-end'),
        reset: document.getElementById('reset'),
        tableWrapper: document.getElementById('table-wrapper'),
        reportStatus: document.getElementById('report-status'),
        generatedAt: document.getElementById('generated-at'),
        exportPdf: document.getElementById('export-pdf'),
        exportXlsx: document.getElementById('export-xlsx'),
        panel: document.getElementById('report-panel'),
        selectionInfo: document.getElementById('selection-info'),
        reportViewHelpToggle: document.getElementById('report-view-help-toggle'),
        reportViewHelpPanel: document.getElementById('report-view-help'),
        reportViewHelpStatus: document.getElementById('report-view-help-status'),
        summaryTotal: document.getElementById('summary-total'),
        summaryOverdue: document.getElementById('summary-overdue'),
        summaryDue30: document.getElementById('summary-due30'),
        summaryDue90: document.getElementById('summary-due90'),
        summaryAccounts: document.getElementById('summary-accounts'),
        summarySites: document.getElementById('summary-sites'),
        summaryTotalMeta: document.getElementById('summary-total-meta'),
        summaryOverdueMeta: document.getElementById('summary-overdue-meta'),
        summaryDue30Meta: document.getElementById('summary-due30-meta'),
        summaryDue90Meta: document.getElementById('summary-due90-meta'),
        summaryAccountsMeta: document.getElementById('summary-accounts-meta'),
        summarySitesMeta: document.getElementById('summary-sites-meta'),
        summaryTotalMeter: document.getElementById('summary-total-meter'),
        summaryOverdueMeter: document.getElementById('summary-overdue-meter'),
        summaryDue30Meter: document.getElementById('summary-due30-meter'),
        summaryDue90Meter: document.getElementById('summary-due90-meter'),
        summaryAccountsMeter: document.getElementById('summary-accounts-meter'),
        summarySitesMeter: document.getElementById('summary-sites-meter')
    };

    document.addEventListener('DOMContentLoaded', init);

    async function init() {
        document.title = 'LifeX - Reporting';
        wireForm();
        setReportViewHelpExpanded(false);
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
        if (elements.archiveState) {
            elements.archiveState.addEventListener('change', () => {
                state.archiveState = normalizeArchiveState(elements.archiveState.value);
                updateSelectionInfo();
            });
        }
        if (elements.reset) {
            elements.reset.addEventListener('click', () => {
                state.view = DEFAULT_VIEW;
                state.range = DEFAULT_RANGE;
                state.startDate = '';
                state.endDate = '';
                state.archiveState = 'ACTIVE';
                applyStateToForm();
                loadReport();
            });
        }
        if (elements.reportViewHelpToggle) {
            elements.reportViewHelpToggle.addEventListener('click', toggleReportViewHelp);
        }
    }

    function applyStateToForm() {
        const preset = normalizeRange(state.range);
        const view = normalizeView(state.view);
        const archiveState = normalizeArchiveState(state.archiveState);
        state.range = preset;
        state.view = view;
        state.archiveState = archiveState;
        if (elements.range) elements.range.value = preset;
        if (elements.view) elements.view.value = view;
        if (elements.archiveState) elements.archiveState.value = archiveState;
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

    function normalizeArchiveState(raw) {
        const normalized = (raw || '').toString().trim().toUpperCase();
        if (normalized === 'ACTIVE' || normalized === 'ARCHIVED' || normalized === 'ALL') {
            return normalized;
        }
        return 'ACTIVE';
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
        state.archiveState = normalizeArchiveState((elements.archiveState && elements.archiveState.value) || 'ACTIVE');
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
        const summaryUrl = `${API.summary}?${params}`;
        setBusy(true);
        setStatus('Loading...');
        try {
            const [reportRes, summaryRes] = await Promise.all([
                fetch(`${API.data}?${params}`),
                fetch(summaryUrl)
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
            setSummaryMeta(elements.summaryTotalMeta, 'All deployments');
            setSummaryMeta(elements.summaryOverdueMeta, 'No data');
            setSummaryMeta(elements.summaryDue30Meta, 'No data');
            setSummaryMeta(elements.summaryDue90Meta, 'No data');
            setSummaryMeta(elements.summaryAccountsMeta, 'No data');
            setSummaryMeta(elements.summarySitesMeta, 'No data');
            setMeterWidth(elements.summaryTotalMeter, 0);
            setMeterWidth(elements.summaryOverdueMeter, 0);
            setMeterWidth(elements.summaryDue30Meter, 0);
            setMeterWidth(elements.summaryDue90Meter, 0);
            setMeterWidth(elements.summaryAccountsMeter, 0);
            setMeterWidth(elements.summarySitesMeter, 0);
            return;
        }
        const total = asNumber(summary.totalDeployments);
        const overdue = asNumber(summary.overdue);
        const due30 = asNumber(summary.dueIn30Days);
        const due90 = asNumber(summary.dueIn90Days);
        const accounts = asNumber(summary.distinctAccounts);
        const sites = asNumber(summary.distinctSites);

        setSummaryValue(elements.summaryTotal, total);
        setSummaryValue(elements.summaryOverdue, overdue);
        setSummaryValue(elements.summaryDue30, due30);
        setSummaryValue(elements.summaryDue90, due90);
        setSummaryValue(elements.summaryAccounts, accounts);
        setSummaryValue(elements.summarySites, sites);

        const overduePct = toPercent(overdue, total);
        const due30Pct = toPercent(due30, total);
        const due90Pct = toPercent(due90, total);
        const accountSpread = toPercent(accounts, total);
        const siteSpread = toPercent(sites, total);

        setSummaryMeta(elements.summaryTotalMeta, 'All deployments');
        setSummaryMeta(elements.summaryOverdueMeta, `${overduePct}% of deployments`);
        setSummaryMeta(elements.summaryDue30Meta, `${due30Pct}% in immediate horizon`);
        setSummaryMeta(elements.summaryDue90Meta, `${due90Pct}% in planned horizon`);
        setSummaryMeta(elements.summaryAccountsMeta, accounts > 0 ? `${formatNumber(total / accounts)} deployments per account` : 'No account coverage');
        setSummaryMeta(elements.summarySitesMeta, sites > 0 ? `${formatNumber(total / sites)} deployments per site` : 'No site coverage');

        setMeterWidth(elements.summaryTotalMeter, total > 0 ? 100 : 0);
        setMeterWidth(elements.summaryOverdueMeter, overduePct);
        setMeterWidth(elements.summaryDue30Meter, due30Pct);
        setMeterWidth(elements.summaryDue90Meter, due90Pct);
        setMeterWidth(elements.summaryAccountsMeter, accountSpread);
        setMeterWidth(elements.summarySitesMeter, siteSpread);
    }

    function setSummaryValue(el, value) {
        if (!el) return;
        if (typeof value === 'number') {
            el.textContent = formatNumber(value);
            return;
        }
        el.textContent = value != null ? String(value) : '-';
    }

    function setSummaryMeta(el, text) {
        if (!el) return;
        el.textContent = text || '';
    }

    function setMeterWidth(el, percent) {
        if (!el) return;
        const safe = Math.max(0, Math.min(100, Number(percent) || 0));
        const width = safe > 0 && safe < 4 ? 4 : safe;
        el.style.width = `${width}%`;
    }

    function asNumber(value) {
        const n = Number(value);
        return Number.isFinite(n) ? n : 0;
    }

    function toPercent(part, total) {
        if (!total || total <= 0) return 0;
        return Math.round((part / total) * 100);
    }

    function formatNumber(value) {
        return new Intl.NumberFormat('en-US', { maximumFractionDigits: value % 1 === 0 ? 0 : 1 }).format(value);
    }

    function updateGeneratedAt(timestamp) {
        if (!elements.generatedAt) return;
        elements.generatedAt.textContent = timestamp ? `Updated: ${timestamp}` : '';
    }

    function updateExportLinks(queryString) {
        if (elements.exportPdf) elements.exportPdf.href = `${API.pdf}?${queryString}`;
        if (elements.exportXlsx) elements.exportXlsx.href = `${API.xlsx}?${queryString}`;
    }

    function buildParams() {
        const params = new URLSearchParams();
        const view = normalizeView(state.view);
        const preset = normalizeRange(state.range);
        const archiveState = normalizeArchiveState(state.archiveState);
        params.set('view', view);
        params.set('preset', preset);
        params.set('archiveState', archiveState);
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
        const archiveLabels = {
            ACTIVE: 'Active only',
            ARCHIVED: 'Archived only',
            ALL: 'All records'
        };
        const archiveState = normalizeArchiveState(state.archiveState);

        if (preset === 'custom' && state.startDate && state.endDate) {
            elements.selectionInfo.textContent = `${viewLabels[view]} | Custom range: ${state.startDate} - ${state.endDate} | ${archiveLabels[archiveState]}`;
            return;
        }
        elements.selectionInfo.textContent = `${viewLabels[view]} | ${rangeLabels[preset] || ''} | ${archiveLabels[archiveState]}`;
    }

    function setReportViewHelpExpanded(expanded) {
        if (!elements.reportViewHelpPanel || !elements.reportViewHelpToggle) return;
        const isOpen = !!expanded;
        elements.reportViewHelpPanel.hidden = !isOpen;
        elements.reportViewHelpToggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        if (elements.reportViewHelpStatus) {
            elements.reportViewHelpStatus.textContent = isOpen
                ? 'Report view explanation expanded'
                : 'Report view explanation collapsed';
        }
    }

    function toggleReportViewHelp() {
        if (!elements.reportViewHelpPanel) return;
        setReportViewHelpExpanded(elements.reportViewHelpPanel.hidden);
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
