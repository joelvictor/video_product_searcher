# Video Product Searcher

Encontra os produtos que aparecem em um vídeo e devolve os links personalizados (de afiliado) das lojas.
O vídeo chega por **upload** ou por **link** (TikTok, YouTube, Instagram, Facebook, X, Kwai, Pinterest, Vimeo ou link direto
para o arquivo). A busca de produtos é a mesma do [StoryShop AI](https://github.com/joelvictor/storyshopai), extraída
para um produto próprio, com a mesma arquitetura e o layout civitas.

## Como funciona

```
vídeo (upload ou link) ──► download isolado (yt-dlp) ──► varredura em CPU (ffmpeg + nitidez + dHash)
        │                                                   │
        │                                     quadros por cena + transcrição (whisper.cpp)
        ▼                                                   ▼
 modo manual: o usuário escolhe       IA em lotes (Gemini ou DeepSeek) ──► junta produtos repetidos
 quadros e marca a área do produto ──►                                         │
                                                                               ▼
                          Rota A (texto por loja) / Rota B (imagem) ──► oferta + link afiliado curto (/l/xxxx)
```

- **Modo automático.** O ffmpeg lê o vídeo inteiro (2 quadros por segundo). Cada quadro recebe nitidez (variância do
  Laplaciano) e hash perceptual, em paralelo nos núcleos da CPU. A linha do tempo é dividida em cenas, quadros borrados
  ou repetidos saem, e de cada cena ficam os mais nítidos. O número de quadros cresce com a duração
  (padrão: um a cada 2,5 s, no máximo 60). Os quadros vão para a IA em lotes de 8, em paralelo, e o mesmo produto visto
  em vários lotes vira um item só, com todos os momentos em que aparece.
- **Modo manual.** O painel mostra o vídeo. O usuário pausa, captura o quadro e, se quiser, arrasta sobre o produto
  para marcar a área. Só esses quadros vão para a IA. Uma área marcada em que a IA não reconheça nada ainda vira busca
  por imagem do recorte. O modo manual também pode ser usado depois de uma busca automática, para completar.
- **Fala.** A fala é transcrita uma vez, em CPU, pelo whisper.cpp (embutido na imagem Docker), e vai como texto para
  todos os lotes. Sem whisper.cpp, o Gemini transcreve; o DeepSeek não ouve áudio.
- **Lojas e links.** Igual ao StoryShop: Rota A procura o produto em cada loja ativada (`site:dominio consulta`), em
  paralelo e por prioridade; Rota B (moda e decoração sem marca) faz busca reversa pelo recorte e fica só com resultados
  das lojas ativadas. A melhor oferta vira link afiliado pelo template da loja (`{asin}`, `{encoded_original_url}`,
  `{influencer.campo}`…) e ganha um link curto `/l/{código}` com contagem de cliques. As outras lojas aparecem como
  alternativas e podem virar a principal com um toque.

## Estrutura

| Pasta | O quê |
| --- | --- |
| `backend/` | Kotlin + Quarkus, Postgres (Flyway), JobRunr (fila no Postgres), mídia em disco/volume |
| `web/` | React + Vite + Tailwind/shadcn com o layout civitas (claro, escuro e alto contraste), responsivo |
| `packages/shared/` | Tipos e cliente HTTP do painel |

Pacotes do backend (`ai.videosearch.*`): `ai` (Gemini, DeepSeek), `search` (SerpAPI, Brave, DeepSeek e o
`OfferResolver`), `affiliate` e `links` (lojas, templates, links curtos), `media` (download, varredura, transcrição,
mídia assinada), `pipeline` (fluxo da busca e API), `security` (senha do painel).

## Rodando com Docker

```bash
cp .env.example .env      # preencha a senha, a chave de assinatura e as chaves de API
docker compose up --build
```

Painel em <http://localhost:8080>. Na primeira vez, entre em **Lojas**, preencha as credenciais de afiliado e ative as
lojas onde a busca deve procurar.

## Desenvolvimento

Requisitos: JDK 21, Node 22 + pnpm, Postgres 16, ffmpeg. Opcionais: yt-dlp (links de redes sociais) e whisper.cpp.

```bash
# banco local
createuser -P vps && createdb -O vps vps        # senha "vps"

# backend em http://localhost:8080
cd backend && ./gradlew quarkusDev

# painel em http://localhost:5173 (proxy para /api, /l e /m)
pnpm install && pnpm web
```

Testes: `cd backend && ./gradlew test` (usa o Postgres local, `DB_URL`/`DB_USER`/`DB_PASSWORD`, e o ffmpeg) e
`pnpm build` para o painel.

## Variáveis de ambiente

| Variável | Padrão | Para quê |
| --- | --- | --- |
| `VPS_ADMIN_PASSWORD` | vazio | Senha única do painel. Vazio deixa o painel aberto (só para desenvolvimento) |
| `VPS_SIGNING_KEY` | gerada sozinha | HMAC das URLs assinadas de vídeo, quadros e recortes. Vazio: o backend gera uma chave aleatória e a guarda no volume de mídia. Só defina com várias instâncias |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | Base dos links curtos e das URLs que o Google Lens acessa |
| `VPS_AI_PROVIDER` | `gemini` | `gemini` ou `deepseek` |
| `VPS_AI_GEMINI_API_KEY` | | Gemini (quadros + transcrição quando não há whisper.cpp) |
| `VPS_AI_DEEPSEEK_API_KEY` | | Chave do provedor DeepSeek: análise de quadros e, com `VPS_SEARCH_PROVIDER=deepseek`, a busca |
| `VPS_AI_DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | Qualquer API compatível com a da OpenAI (parte da URL antes de `/chat/completions`): OpenRouter, Together, Fireworks, Azure, vLLM, Ollama... Sem chave, só para servidor próprio |
| `VPS_AI_DEEPSEEK_MODEL` | `deepseek-flash` | Nome do modelo nesse provedor (ex.: `deepseek/deepseek-chat` no OpenRouter). Precisa aceitar imagens |
| `VPS_SEARCH_PROVIDER` | `serpapi` | `serpapi`, `brave` ou `deepseek` |
| `VPS_SEARCH_DEEPSEEK_BASE_URL` / `_MODEL` / `_API_KEY` | herda da análise | Endpoint da busca `deepseek`, que usa a ferramenta `deepseek_web_search` (a API oficial tem). A chave da análise só é reaproveitada no mesmo host |
| `VPS_SEARCH_SERPAPI_KEY` | | Google por domínio (Rota A) e Google Lens (Rota B) |
| `VPS_SEARCH_BRAVE_KEY` | | Brave Search (só texto; a Rota B cai na busca por texto) |
| `VPS_SCAN_FPS` | `2` | Quadros por segundo lidos na varredura |
| `VPS_SCAN_SECONDS_PER_FRAME` | `2.5` | Um quadro para a IA a cada N segundos de vídeo (antes do teto) |
| `VPS_SCAN_MAX_FRAMES` | `60` | Teto de quadros enviados à IA por vídeo (controla custo) |
| `VPS_SCAN_BATCH_SIZE` / `VPS_SCAN_PARALLEL_BATCHES` | `8` / `3` | Tamanho dos lotes e quantos vão à IA ao mesmo tempo |
| `VPS_MAX_PRODUCTS` | `25` | Máximo de produtos por busca |
| `VPS_ALLOWED_DOMAINS` | redes listadas acima | Sites baixados pelo yt-dlp; outros links só se forem arquivo de vídeo direto |
| `VPS_MAX_DURATION_SECONDS` / `VPS_MAX_DOWNLOAD_BYTES` | `900` / 300 MB | Limites do vídeo |
| `VPS_MEDIA_RETENTION` | `24h` | Tempo que o vídeo original fica guardado para o modo manual |
| `VPS_WHISPER_MODEL` | `/models/ggml-base.bin` na imagem | Modelo do whisper.cpp; vazio desliga a transcrição local |

## Provedor e chaves pelo painel

Em **Configurações** dá para escolher o provedor de IA (Gemini ou DeepSeek/compatível) e de busca (SerpAPI, Brave ou
DeepSeek) e informar as chaves, sem editar o `.env`. O que for salvo ali vale no lugar das variáveis `VPS_*`; remover
volta ao valor do ambiente, e a troca vale já na próxima busca. As chaves ficam cifradas (AES-256-GCM, com chave derivada
da chave de assinatura) no banco e nunca voltam para a tela: ela mostra só os 4 últimos caracteres. Se a chave de
assinatura mudar (por exemplo, o volume de mídia foi apagado), as chaves salvas no painel não podem mais ser lidas e é
preciso informá-las de novo.

## Segurança do download

- yt-dlp roda como processo separado, sem shell, com `--ignore-config`, sem cookies nem login, em diretório temporário
  próprio, com ambiente mínimo (nenhum segredo do backend), limites de tamanho, duração e tempo, e sem playlists ou lives.
- Só domínios da lista vão para o yt-dlp. Outros links precisam responder com um arquivo de vídeo e passam por bloqueio
  de hosts internos a cada redirecionamento.
- O backend roda sem root na imagem e o vídeo original é apagado depois da retenção.
- Posts privados ou que pedem login não são baixados: o painel orienta a usar o upload.

## Documentação

- [Teste de ponta a ponta com as contas reais](docs/TESTE-DE-FUMACA.md)
