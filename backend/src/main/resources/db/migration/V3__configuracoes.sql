-- Configurações escolhidas no painel (provedor de IA/busca e chaves). Chaves ficam cifradas (ver SecretBox).
CREATE TABLE app_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
