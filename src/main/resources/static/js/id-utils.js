(function initIdUtils(globalObject) {
    const globalTarget = globalObject || {};

    function normalizeUuid(value) {
        if (value === undefined || value === null) return null;
        const trimmed = String(value).trim();
        if (!trimmed) return null;
        const cleaned = trimmed.replace(/[{}]/g, '');
        const lower = cleaned.toLowerCase();
        const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
        if (uuidPattern.test(lower)) {
            return lower;
        }
        return lower;
    }

    function resolveNormalizedSiteId(record) {
        if (!record || typeof record !== 'object') {
            return null;
        }
        const candidateKeys = ['SITEID', 'siteID', 'SiteID', 'siteId', 'siteid'];
        for (const key of candidateKeys) {
            if (key in record) {
                const normalized = normalizeUuid(record[key]);
                if (normalized) {
                    return normalized;
                }
            }
        }
        return null;
    }

    const api = {
        normalizeUuid,
        resolveNormalizedSiteId,
    };

    if (typeof globalTarget === 'object') {
        globalTarget.appIdUtils = Object.assign({}, globalTarget.appIdUtils || {}, api);
    }

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this));

