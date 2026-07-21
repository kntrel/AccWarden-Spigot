CREATE TABLE IF NOT EXISTS account (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    salt TEXT NOT NULL,
    hashed_password TEXT NOT NULL,
    platform_joined INTEGER NOT NULL,
    joined DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_login DATETIME DEFAULT CURRENT_TIMESTAMP,
    CHECK(TRIM(name) = name),
    CHECK(name > 0),
    CHECK(platform_joined BETWEEN 0 AND 2),
    CHECK(TRIM(name) NOT GLOB '*[^A-Za-z0-9_]*')
);

CREATE UNIQUE INDEX IF NOT EXISTS account_name_unique ON account(LOWER(TRIM(name)));
