CREATE TABLE cep_lookup_log (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cep              VARCHAR(8)  NOT NULL,
    queried_at       TIMESTAMPTZ NOT NULL,
    outcome          VARCHAR(20) NOT NULL CHECK (outcome IN ('found', 'not_found', 'provider_error')),
    source           VARCHAR(20) NOT NULL CHECK (source IN ('provider', 'cache')),
    response_payload JSONB
);
