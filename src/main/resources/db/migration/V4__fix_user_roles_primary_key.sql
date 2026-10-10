-- V1 creates user_roles without a primary key.
-- V2 cannot correct it because it uses CREATE TABLE IF NOT EXISTS.
-- Enforce non-null roles and unique user-role pairs.

ALTER TABLE user_roles
    MODIFY COLUMN role VARCHAR(50) NOT NULL,
    ADD PRIMARY KEY (user_id, role);
