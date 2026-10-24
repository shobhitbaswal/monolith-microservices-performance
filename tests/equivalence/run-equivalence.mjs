// Black-box equivalence checks: same scenarios against the monolith and the microservices.
// Node >= 18, no dependencies.   node tests/equivalence/run-equivalence.mjs [--write]
//
// Default mode is READ-ONLY (GETs, plus checkout requests that must fail before persisting anything
// on the microservices side). --write also adds cart items and creates orders for ONE throwaway
// userId (TEST_USER_ID, default random >= 900000000, never the benchmark users 1..50). Order rows
// are NOT deleted afterwards: run --write only against scratch databases.
const WRITE = process.argv.includes('--write');
const env = (k, d) => process.env[k] || d;
const MONO = env('MONO_URL', 'http://localhost:8080');
const CAT = env('CATALOG_URL', 'http://localhost:8081');
const ORD = env('ORDER_URL', 'http://localhost:8082');
const CART = env('CART_URL', 'http://localhost:8083');
const U = Number(env('TEST_USER_ID', String(900000000 + Math.floor(Math.random() * 99999999))));
const MISSING_USER = U + 1;

const targets = {
  mono: { products: MONO, orders: MONO, cart: MONO },
  micro: { products: CAT, orders: ORD, cart: CART },
};

let failed = 0;
const ok = (name, cond, detail = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${name}${cond ? '' : '  ' + detail}`);
  if (!cond) failed++;
};
const info = (name, detail) => console.log(`INFO  ${name}  ${detail}`);

async function call(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* non-JSON body */ }
  return { status: res.status, json, text };
}

// Normalisation: drop generated ids/timestamps, map the documented renames onto one shape.
const nProduct = (p) => ({ name: p.name, description: p.description, price: Number(p.price), stock: p.stock });
const nCart = (c) => ({ owner: String(c.userId ?? c.sessionId), productId: c.productId, quantity: c.quantity });
const nOrder = (o) => ({ userId: o.userId, status: o.status, total: Number(o.totalAmount ?? o.totalPrice) });
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

async function both(fn) {
  const out = {};
  for (const [name, t] of Object.entries(targets)) out[name] = await fn(t);
  return out;
}

async function main() {
  for (const [n, u] of [['monolith', MONO], ['catalog', CAT], ['order', ORD], ['cart', CART]]) {
    try { await call('GET', `${u}/actuator/health`); }
    catch (e) { console.error(`Cannot reach ${n} at ${u}: ${e.cause?.code || e.message}. Nothing was tested.`); process.exit(2); }
  }
  console.log(`mode=${WRITE ? 'WRITE' : 'read-only'}  testUserId=${U}\n`);

  // ---- products (identical code in both) ----
  let r = await both((t) => call('GET', `${t.products}/products`));
  ok('GET /products status 200 on both', r.mono.status === 200 && r.micro.status === 200, JSON.stringify([r.mono.status, r.micro.status]));
  const list = (x) => (x.json || []).map(nProduct).sort((a, b) => a.name.localeCompare(b.name));
  ok('GET /products same normalized content', same(list(r.mono), list(r.micro)));

  r = await both((t) => call('GET', `${t.products}/products/1`));
  ok('GET /products/1 same status and content', r.mono.status === r.micro.status && same(r.mono.json && nProduct(r.mono.json), r.micro.json && nProduct(r.micro.json)));

  r = await both((t) => call('GET', `${t.products}/products/999999`));
  ok('GET /products/999999 same status (unhandled orElseThrow)', r.mono.status === r.micro.status, JSON.stringify([r.mono.status, r.micro.status]));

  // ---- empty reads ----
  r = await both((t) => call('GET', `${t.orders}/orders/user/${MISSING_USER}`));
  ok('GET /orders/user/{unknown} -> 200 [] on both', r.mono.status === 200 && r.micro.status === 200 && same(r.mono.json, []) && same(r.micro.json, []), JSON.stringify([r.mono.status, r.micro.status]));
  r = await both((t) => call('GET', `${t.cart}/cart/${MISSING_USER}`));
  ok('GET /cart/{unknown} -> 200 [] on both', r.mono.status === 200 && r.micro.status === 200 && same(r.mono.json, []) && same(r.micro.json, []));

  // ---- failing checkout that must not persist anything ----
  r = await both((t) => call('POST', `${t.orders}/orders`, { userId: MISSING_USER, items: [{ productId: 999999, quantity: 1 }] }));
  ok('checkout with missing product -> same error status', r.mono.status === r.micro.status && r.mono.status >= 400, JSON.stringify([r.mono.status, r.micro.status]));
  r = await call('GET', `${ORD}/orders/user/${MISSING_USER}`);
  ok('micro: failed checkout left no order', r.status === 200 && same(r.json, []));

  // ---- known difference: stricter micro validation (decision 2). Micro only; monolith side is write-mode. ----
  for (const [label, body] of [
    ['empty items', { userId: MISSING_USER, items: [] }],
    ['quantity 0', { userId: MISSING_USER, items: [{ productId: 1, quantity: 0 }] }],
    ['missing userId', { items: [{ productId: 1, quantity: 1 }] }],
  ]) {
    r = await call('POST', `${ORD}/orders`, body);
    ok(`micro rejects invalid order (${label})`, r.status >= 400, String(r.status));
    if (WRITE) { // the monolith may accept some of these and persist an order: documented difference
      const m = await call('POST', `${MONO}/orders`, { ...body, userId: body.userId ?? (U + 2) });
      info(`KNOWN-DIFF monolith (${label})`, `status ${m.status} (micro ${r.status})`);
    }
  }

  if (!WRITE) { console.log('\nread-only mode: skipped cart/checkout/history writes (use --write on scratch DBs).'); return; }

  // ---- cart ----
  r = await both((t) => call('POST', `${t.cart}/cart/${U}/items`, { productId: 1, quantity: 2 }));
  ok('add to cart WITHOUT price -> 200 on both (price removal precondition)', r.mono.status === 200 && r.micro.status === 200, JSON.stringify([r.mono.status, r.micro.status]));
  ok('add to cart same normalized response', same(nCart(r.mono.json || {}), nCart(r.micro.json || {})));
  r = await both((t) => call('GET', `${t.cart}/cart/${U}`));
  ok('get cart: one item, same content', r.mono.json?.length === 1 && r.micro.json?.length === 1 && same(r.mono.json.map(nCart), r.micro.json.map(nCart)));

  // ---- checkout ----
  const order = { userId: U, items: [{ productId: 1, quantity: 2 }, { productId: 2, quantity: 1 }] };
  r = await both((t) => call('POST', `${t.orders}/orders`, order));
  ok('checkout 200 on both', r.mono.status === 200 && r.micro.status === 200, JSON.stringify([r.mono.status, r.micro.status]));
  ok('checkout same normalized order (PAID, total from catalog prices)', same(nOrder(r.mono.json || {}), nOrder(r.micro.json || {})), JSON.stringify([nOrder(r.mono.json || {}), nOrder(r.micro.json || {})]));
  r = await both((t) => call('GET', `${t.cart}/cart/${U}`));
  ok('cart cleared after checkout on both', same(r.mono.json, []) && same(r.micro.json, []));

  // ---- history ----
  r = await both((t) => call('GET', `${t.orders}/orders/user/${U}`));
  ok('order history: one order, same content', r.mono.json?.length === 1 && r.micro.json?.length === 1 && same(r.mono.json.map(nOrder), r.micro.json.map(nOrder)));
}

main().then(() => { console.log(failed ? `\n${failed} check(s) FAILED` : '\nall checks passed'); process.exit(failed ? 1 : 0); })
  .catch((e) => { console.error(e); process.exit(2); });
