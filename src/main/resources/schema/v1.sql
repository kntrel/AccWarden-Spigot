CREATE TABLE IF NOT EXISTS account (
    id INTEGER PRIMARY KEY,
    java_uuid TEXT UNIQUE,
    bedrock_uuid TEXT UNIQUE,
    name TEXT NOT NULL,
    salt TEXT NOT NULL,
    hashed_password TEXT NOT NULL,
    joined DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_login DATETIME DEFAULT CURRENT_TIMESTAMP,
    CHECK(java_uuid IS NOT NULL OR bedrock_uuid IS NOT NULL)
);
