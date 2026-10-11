START TRANSACTION;

INSERT INTO categories
    (name, description, status, created_at, created_by, updated_at, updated_by)
SELECT
    seed.name, seed.description, 'ACTIVE',
    '2026-01-01 00:00:00', 'research-seed',
    '2026-01-01 00:00:00', 'research-seed'
FROM (
    SELECT 'Research Electronics' AS name,
           'Seed data for repeatable performance experiments' AS description
    UNION ALL
    SELECT 'Research Home',
           'Seed data for repeatable performance experiments'
    UNION ALL
    SELECT 'Research Accessories',
           'Seed data for repeatable performance experiments'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.name = seed.name
);

INSERT INTO brands
    (name, description, country, status, created_at, created_by, updated_at, updated_by)
SELECT
    seed.name, seed.description, 'Research', 'ACTIVE',
    '2026-01-01 00:00:00', 'research-seed',
    '2026-01-01 00:00:00', 'research-seed'
FROM (
    SELECT 'Research Brand A' AS name,
           'Seed brand for performance experiments' AS description
    UNION ALL
    SELECT 'Research Brand B',
           'Seed brand for performance experiments'
    UNION ALL
    SELECT 'Research Brand C',
           'Seed brand for performance experiments'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM brands b WHERE b.name = seed.name
);

INSERT INTO products
    (name, description, price_amount, price_currency, sku, image_url,
     status, category_id, brand_id, seller_user_id,
     created_at, created_by, updated_at, updated_by)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 30
)
SELECT
    CONCAT('Research Product ', LPAD(seq.n, 3, '0')),
    'Synthetic catalogue record for controlled research benchmarks',
    100.00 + seq.n,
    'INR',
    CONCAT('RESEARCH-SKU-', LPAD(seq.n, 3, '0')),
    NULL,
    'ACTIVE',
    c.id,
    b.id,
    NULL,
    TIMESTAMP('2026-01-01 00:00:00') + INTERVAL seq.n MINUTE,
    'research-seed',
    TIMESTAMP('2026-01-01 00:00:00') + INTERVAL seq.n MINUTE,
    'research-seed'
FROM seq
JOIN categories c
  ON c.name = CASE MOD(seq.n - 1, 3)
      WHEN 0 THEN 'Research Electronics'
      WHEN 1 THEN 'Research Home'
      ELSE 'Research Accessories'
  END
JOIN brands b
  ON b.name = CASE MOD(seq.n - 1, 3)
      WHEN 0 THEN 'Research Brand A'
      WHEN 1 THEN 'Research Brand B'
      ELSE 'Research Brand C'
  END
WHERE NOT EXISTS (
    SELECT 1
    FROM products p
    WHERE p.sku = CONCAT('RESEARCH-SKU-', LPAD(seq.n, 3, '0'))
);

COMMIT;
