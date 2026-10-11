import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 2,
  duration: '10s',
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
    'API reports success': (r) => r.json('success') === true,
    'catalogue contains 30 products': (r) =>
      r.json('data.totalElements') === 30,
    'first page contains 20 products': (r) =>
      r.json('data.content').length === 20,
  });

  sleep(1);
}
