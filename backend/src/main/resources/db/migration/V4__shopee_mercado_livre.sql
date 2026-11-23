-- Shopee e Mercado Livre. Os formatos de link abaixo seguem os parâmetros de afiliado conhecidos de cada programa,
-- mas devem ser conferidos com um link gerado no painel de afiliado de cada loja; o template se ajusta na tela Lojas.
INSERT INTO affiliate_platforms (slug, display_name, integration_type, domain_regex, search_domain,
                                 required_credential_fields, affiliate_url_template, extra_params,
                                 ios_url_scheme, android_intent_package)
VALUES
('shopee-br', 'Shopee Brasil', 'URL_TEMPLATE',
 '(shopee\.com\.br|shope\.ee|s\.shopee\.com\.br)', 'shopee.com.br',
 '[{"field":"affiliate_id","label":"Seu ID de afiliado Shopee","required":true}]',
 'https://shope.ee/an_redir?origin_link={encoded_clean_url}&affiliate_id={influencer.affiliate_id}', '{}',
 NULL, 'com.shopee.br'),

('mercado-livre', 'Mercado Livre', 'URL_TEMPLATE',
 '(mercadolivre\.com(\.br)?|mercadolibre\.com|meli\.la)', 'mercadolivre.com.br',
 '[{"field":"matt_tool","label":"Seu matt_tool (ID da ferramenta)","required":true},{"field":"matt_word","label":"Seu matt_word (nome de afiliado)","required":true}]',
 '{clean_url}?matt_tool={influencer.matt_tool}&matt_word={influencer.matt_word}', '{}',
 NULL, 'com.mercadolibre')
ON CONFLICT (slug) DO NOTHING;
