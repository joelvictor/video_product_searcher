-- Link do portal de afiliados de cada loja, mostrado na tela Lojas para o usuário achar o próprio ID.
ALTER TABLE affiliate_platforms ADD COLUMN help_url VARCHAR(500);

UPDATE affiliate_platforms SET help_url = 'https://associados.amazon.com.br/' WHERE slug = 'amazon-br';
UPDATE affiliate_platforms SET help_url = 'https://www.lomadee.com/' WHERE slug = 'lomadee';
UPDATE affiliate_platforms SET help_url = 'https://ui.awin.com/' WHERE slug = 'awin-sephora';
UPDATE affiliate_platforms SET help_url = 'https://www.magazinevoce.com.br/' WHERE slug = 'magalu';
UPDATE affiliate_platforms SET help_url = 'https://affiliate.shopee.com.br/' WHERE slug = 'shopee-br';
UPDATE affiliate_platforms SET help_url = 'https://www.mercadolivre.com.br/l/afiliados-home' WHERE slug = 'mercado-livre';
