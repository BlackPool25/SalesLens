CREATE TABLE users (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name  VARCHAR(255),
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(255),

    UNIQUE (username),
    UNIQUE (email)
);
