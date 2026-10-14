-- Accounts. username is unique case-insensitively, so Rob and rob are one account.
CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT    NOT NULL,
    admin         INTEGER NOT NULL DEFAULT 0,
    created_at    INTEGER NOT NULL
);
