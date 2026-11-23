-- Lojas iniciais (as mesmas do StoryShop). Novas lojas entram pela tela Lojas, sem migration.
INSERT INTO affiliate_platforms (slug, display_name, integration_type, domain_regex, search_domain,
                                 required_credential_fields, affiliate_url_template, extra_params,
                                 ios_url_scheme, android_intent_package)
VALUES
('amazon-br', 'Amazon Brasil', 'URL_TEMPLATE',
 '(amazon\.com\.br|amzn\.to)', 'amazon.com.br',
 '[{"field":"amazon_tag","label":"Sua tag de associado (ex.: fulana-20)","required":true}]',
 'https://www.amazon.com.br/dp/{asin}?tag={influencer.amazon_tag}', '{}',
 'com.amazon.mobile.shopping', 'com.amazon.mShop.android.shopping'),

('lomadee', 'Lomadee', 'URL_TEMPLATE',
 '(lomadee\.com)', NULL,
 '[{"field":"source_id","label":"Seu sourceId Lomadee","required":true}]',
 'https://redir.lomadee.com/v2/deeplink?url={encoded_original_url}&sourceId={influencer.source_id}', '{}',
 NULL, NULL),

('awin-sephora', 'Sephora (Awin)', 'URL_TEMPLATE',
 '(sephora\.com\.br)', 'sephora.com.br',
 '[{"field":"awin_id","label":"Seu ID de afiliado Awin","required":true}]',
 'https://www.awin1.com/cread.php?awinmid={merchant_id}&awinaffid={influencer.awin_id}&ued={encoded_original_url}',
 '{"merchant_id":"CONFIGURAR"}', NULL, NULL),

('magalu', 'Magalu (Parceiro Magalu)', 'URL_TEMPLATE',
 '(magazineluiza\.com\.br|magalu\.com)', 'magazineluiza.com.br',
 '[{"field":"vitrine_id","label":"Nome da sua vitrine Magalu","required":true}]',
 'https://www.magazinevoce.com.br/{influencer.vitrine_id}{original_path}', '{}',
 NULL, 'com.luizalabs.mlapp');
