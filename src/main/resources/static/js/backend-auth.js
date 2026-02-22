(function () {
    const BACKEND_USER = 'lifex';
    const BACKEND_PASSWORD = '12345';

    const protectedPrefixes = [
        '/api/',
        '/search',
        '/table/',
        '/row/',
        '/accounts',
        '/projects',
        '/sites',
        '/servers',
        '/clients',
        '/radios',
        '/audio',
        '/phones',
        '/deployment-variants',
        '/servicecontracts',
        '/addresses'
    ];

    const token = 'Basic ' + btoa(BACKEND_USER + ':' + BACKEND_PASSWORD);
    const originalFetch = window.fetch.bind(window);

    const isProtectedPath = (path) => {
        if (!path) return false;
        return protectedPrefixes.some(prefix => path === prefix || path.startsWith(prefix));
    };

    const shouldAttachAuth = (input) => {
        try {
            const rawUrl = typeof input === 'string' ? input : input.url;
            const url = new URL(rawUrl, window.location.origin);
            return url.origin === window.location.origin && isProtectedPath(url.pathname);
        } catch (_) {
            return false;
        }
    };

    window.fetch = function (input, init) {
        if (!shouldAttachAuth(input)) {
            return originalFetch(input, init);
        }

        const request = new Request(input, init || {});
        const headers = new Headers(request.headers || {});
        if (!headers.has('Authorization')) {
            headers.set('Authorization', token);
        }

        const requestWithAuth = new Request(request, { headers });
        return originalFetch(requestWithAuth);
    };
})();
