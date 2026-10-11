import http from 'k6/http';
import { check } from 'k6';

const vus = Number(__ENV.VUS || 5);
const duration = __ENV.DURATION || '60s';

export const options = {
  vus,
  duration,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const response = http.get(
    'http://127.0.0.1:8080/api/v1/products/active/paged?page=0&size=20'
  );

  check(response, {
    'status is 200': (r) => r.status === 200,
    'catalogue contains 30 products': (r) =>
      r.json('data.totalElements') === 30,
  });
}
