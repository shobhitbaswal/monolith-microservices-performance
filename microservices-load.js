import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  vus: Number(__ENV.VUS || 50),
  duration: __ENV.DURATION || '60s',
};

const BASE_CATALOG = 'http://localhost:8081';
const BASE_ORDER = 'http://localhost:8082';
const BASE_CART = 'http://localhost:8083';

export default function () {
  const userId = ((__VU - 1) % 50) + 1;
  const sessionId = String(userId);

  // 1. List products
  let res = http.get(`${BASE_CATALOG}/products`);
  let ok = check(res, {
    'catalog products status 200': (r) => r.status === 200,
  });
  errorRate.add(!ok);

  // 2. Get product details
  res = http.get(`${BASE_CATALOG}/products/1`);
  ok = check(res, {
    'catalog product detail status 200': (r) => r.status === 200,
  });
  errorRate.add(!ok);

  // 3. Create an order using the same payload shape as the monolith
  const orderPayload = JSON.stringify({
    userId: userId,
    items: [{ productId: 1, quantity: 1 }],
  });

  res = http.post(`${BASE_ORDER}/orders`, orderPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  ok = check(res, {
    'create order status 200': (r) => r.status === 200,
  });
  errorRate.add(!ok);

  // 4. Add an item to the cart
  const cartPayload = JSON.stringify({
    productId: 1,
    quantity: 1,
  });

  res = http.post(`${BASE_CART}/cart/${sessionId}/items`, cartPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  ok = check(res, {
    'add to cart status 200': (r) => r.status === 200,
  });
  errorRate.add(!ok);

  // 5. Retrieve the cart
  res = http.get(`${BASE_CART}/cart/${sessionId}`);
  ok = check(res, {
    'get cart status 200': (r) => r.status === 200,
  });
  errorRate.add(!ok);

  sleep(0.5);
}
