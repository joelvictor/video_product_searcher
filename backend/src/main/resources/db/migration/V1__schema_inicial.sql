-- Lojas parceiras (mesmo modelo do StoryShop) e credenciais de afiliado da conta única do painel.
CREATE TABLE affiliate_platforms (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                       VARCHAR(50) UNIQUE NOT NULL,
    display_name               VARCHAR(100) NOT NULL,
    logo_url                   TEXT,
    is_active                  BOOLEAN NOT NULL DEFAULT TRUE,
    integration_type           VARCHAR(30) NOT NULL DEFAULT 'URL_TEMPLATE',
    domain_regex               TEXT NOT NULL,
    search_strategy            VARCHAR(30) NOT NULL DEFAULT 'SHOPPING_DOMAIN',
    search_domain              VARCHAR(120),                      -- usado em site:{search_domain}
    required_credential_fields JSONB NOT NULL DEFAULT '[]',
    affiliate_url_template     TEXT NOT NULL,
    extra_params               JSONB NOT NULL DEFAULT '{}',
    ios_url_scheme             TEXT,
    android_intent_package     TEXT,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE store_credentials (
    platform_id       UUID PRIMARY KEY REFERENCES affiliate_platforms(id) ON DELETE CASCADE,
    credential_values JSONB NOT NULL DEFAULT '{}',
    is_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    priority          INT NOT NULL DEFAULT 100,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE short_links (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(16) UNIQUE NOT NULL,
    platform_id   UUID REFERENCES affiliate_platforms(id) ON DELETE SET NULL,
    affiliate_url TEXT NOT NULL,
    click_count   BIGINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Uma busca = um vídeo (upload ou URL) analisado no modo automático e/ou manual.
CREATE TABLE searches (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type      VARCHAR(10) NOT NULL,             -- 'UPLOAD' | 'URL'
    source_url       TEXT,
    title            TEXT,
    caption          TEXT,
    mode             VARCHAR(10) NOT NULL,             -- 'AUTO' | 'MANUAL'
    status           VARCHAR(20) NOT NULL,             -- RECEIVED | DOWNLOADING | READY | PROCESSING | DONE | FAILED
    progress         VARCHAR(120),
    error_message    TEXT,
    video_key        TEXT,
    duration_seconds DOUBLE PRECISION,
    frames_sampled   INT,
    frames_analyzed  INT,
    transcript       TEXT,
    media_deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX searches_created_idx ON searches (created_at DESC);

CREATE TABLE search_items (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    search_id          UUID NOT NULL REFERENCES searches(id) ON DELETE CASCADE,
    item_number        INT NOT NULL,
    origin             VARCHAR(10) NOT NULL,           -- 'AUTO' | 'MANUAL'
    label              TEXT NOT NULL,
    brand              TEXT,
    search_query       TEXT NOT NULL,
    relevance_layer    VARCHAR(20) NOT NULL,
    detection_type     VARCHAR(30) NOT NULL,
    category           VARCHAR(80),
    confidence         NUMERIC(4,3) NOT NULL,
    timestamps         JSONB NOT NULL DEFAULT '[]',    -- segundos do vídeo em que o produto aparece
    bounding_box       JSONB NOT NULL DEFAULT '[]',
    frame_key          TEXT,
    crop_key           TEXT,
    status             VARCHAR(20) NOT NULL,           -- 'FOUND' | 'NOT_FOUND'
    platform_id        UUID REFERENCES affiliate_platforms(id) ON DELETE SET NULL,
    product_title      TEXT,
    product_image_url  TEXT,
    price_display      VARCHAR(50),
    original_url       TEXT,
    short_link_id      UUID REFERENCES short_links(id) ON DELETE SET NULL,
    affiliate_url      TEXT,
    alternative_offers JSONB NOT NULL DEFAULT '[]',
    note               TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX search_items_search_idx ON search_items (search_id, item_number);
