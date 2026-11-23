# Teste de fumaça com as contas reais

Nada foi testado ainda contra as APIs reais (Gemini, DeepSeek, SerpAPI, Brave) nem contra as redes sociais.
Este roteiro valida cada integração com as contas fornecidas.

## 1. Subir

1. `cp .env.example .env` e preencha `VPS_ADMIN_PASSWORD`, `VPS_SIGNING_KEY` e as chaves de API.
2. `docker compose up --build` e abra <http://localhost:8080>.
3. Em **Lojas**, preencha a credencial de afiliado de cada loja, marque "Procurar nesta loja" e salve.
   O selo deve ficar "Ativa". Lojas sem `Domínio de busca` (ex.: Lomadee) só servem para links colados e busca por imagem.
4. Em **Gerar link**, cole um link de produto de cada loja e confira se o link afiliado sai com o seu ID.

## 2. Download por link

Teste um vídeo público de cada rede: TikTok, YouTube (Shorts e vídeo normal), Instagram (Reels), e um link direto `.mp4`.
Esperado: a busca passa de "Baixando" para "Analisando". Instagram costuma exigir login para alguns posts; nesse caso a
mensagem pede o upload. Se o YouTube falhar, atualize o yt-dlp (`docker compose build --no-cache backend`), porque as redes
mudam com frequência.

## 3. Modo automático

1. Envie um vídeo de 30 a 60 s com produtos de marca visível (ex.: rotina de skincare) e com fala.
2. Confira na tela do resultado: quadros lidos (cerca de 2 por segundo), quadros analisados (até 24 para 60 s),
   transcrição da fala no fim da página e produtos com os momentos em que aparecem.
3. Clique num link curto: deve abrir a loja com o seu ID de afiliado.

## 4. Modo manual

1. Crie uma busca no modo manual. Quando ficar "Pronto para escolher quadros", pause num produto, marque a área e capture.
2. Capture mais um quadro sem área e clique em "Buscar produtos nesses quadros".
3. Em um resultado automático, use "Escolher quadros manualmente" para completar com um produto que faltou.

## 5. Provedores alternativos

- `VPS_AI_PROVIDER=deepseek` + `VPS_AI_DEEPSEEK_API_KEY`: o DeepSeek não ouve áudio, então a fala vem do whisper.cpp.
- `VPS_SEARCH_PROVIDER=deepseek`: a busca usa a ferramenta `deepseek_web_search` (texto e imagem). Confira nos logs do
  backend se a resposta traz `server_tool_calls[].sources`; se o formato vier diferente da documentação, o parser é
  `DeepSeekProductSearch.parseSources`.
- `VPS_SEARCH_PROVIDER=brave`: só texto; itens de moda caem na busca por texto.
- A Rota B com SerpAPI (Google Lens) precisa que `PUBLIC_BASE_URL` seja acessível pela internet.

## Custos

Cada vídeo no automático gera uma chamada à IA por lote de 8 quadros (até 8 lotes com o teto de 60) e uma busca por
produto e loja ativada. Ajuste `VPS_SCAN_MAX_FRAMES` e `VPS_MAX_PRODUCTS` para controlar.
