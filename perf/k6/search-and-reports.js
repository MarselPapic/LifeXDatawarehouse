import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from 'k6/encoding';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const BACKEND_USER = __ENV.BACKEND_USER || 'lifex';
const BACKEND_PASSWORD = __ENV.BACKEND_PASSWORD || '12345';
const REPORT_PRESET = __ENV.REPORT_PRESET || 'next90';
const THINK_TIME_MS = Number(__ENV.THINK_TIME_MS || '150');

const SEARCH_VUS = Number(__ENV.SEARCH_VUS || '20');
const SEARCH_DURATION = __ENV.SEARCH_DURATION || '10m';
const REPORT_VUS = Number(__ENV.REPORT_VUS || '10');
const REPORT_DURATION = __ENV.REPORT_DURATION || '10m';

const SEARCH_QUERIES = [
  'site',
  'server',
  'account',
  'lifex',
  'status:active',
  'type:project',
  'support'
];

const REPORT_VIEWS = ['support-end', 'lifecycle-status', 'account-risk'];

const authHeader = `Basic ${encoding.b64encode(`${BACKEND_USER}:${BACKEND_PASSWORD}`)}`;

function backendParams(endpointTag) {
  return {
    headers: {
      Authorization: authHeader
    },
    tags: {
      endpoint: endpointTag
    }
  };
}

export const options = {
  scenarios: {
    search_sla: {
      executor: 'constant-vus',
      exec: 'searchScenario',
      vus: SEARCH_VUS,
      duration: SEARCH_DURATION
    },
    report_sla: {
      executor: 'constant-vus',
      exec: 'reportScenario',
      vus: REPORT_VUS,
      duration: REPORT_DURATION
    }
  },
  thresholds: {
    'http_req_failed{endpoint:search}': ['rate<0.01'],
    'http_req_duration{endpoint:search}': ['p(95)<10000'],
    'http_req_failed{endpoint:reports}': ['rate<0.01'],
    'http_req_duration{endpoint:reports}': ['p(95)<10000']
  }
};

export function setup() {
  const res = http.get(`${BASE_URL}/index.html`, { tags: { endpoint: 'setup' } });
  check(res, {
    'setup: index page reachable': (r) => r.status === 200
  });
}

export function searchScenario() {
  const query = SEARCH_QUERIES[Math.floor(Math.random() * SEARCH_QUERIES.length)];
  const url = `${BASE_URL}/search?q=${encodeURIComponent(query)}`;
  const res = http.get(url, backendParams('search'));

  check(res, {
    'search: status 200': (r) => r.status === 200,
    'search: json body': (r) => {
      try {
        JSON.parse(r.body);
        return true;
      } catch (e) {
        return false;
      }
    }
  });

  sleep(THINK_TIME_MS / 1000);
}

export function reportScenario() {
  const view = REPORT_VIEWS[Math.floor(Math.random() * REPORT_VIEWS.length)];
  const url = `${BASE_URL}/api/reports/data?preset=${encodeURIComponent(REPORT_PRESET)}&view=${encodeURIComponent(view)}`;
  const res = http.get(url, backendParams('reports'));

  check(res, {
    'reports: status 200': (r) => r.status === 200,
    'reports: json body': (r) => {
      try {
        const payload = JSON.parse(r.body);
        return payload && typeof payload === 'object' && payload.table !== undefined;
      } catch (e) {
        return false;
      }
    }
  });

  sleep(THINK_TIME_MS / 1000);
}

