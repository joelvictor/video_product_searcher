FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
# checkout no Windows pode trazer CRLF, e aí o shebang do gradlew quebra ("not found")
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon quarkusBuild -x test

# whisper.cpp compilado para CPU (transcrição local da fala, sem custo de API)
FROM eclipse-temurin:21-jre AS whisper
ARG WHISPER_VERSION=v1.7.5
ARG WHISPER_MODEL=base
RUN apt-get update && apt-get install -y --no-install-recommends build-essential cmake git curl ca-certificates && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch ${WHISPER_VERSION} https://github.com/ggml-org/whisper.cpp /whisper \
 && cmake -S /whisper -B /whisper/build -DBUILD_SHARED_LIBS=OFF -DWHISPER_BUILD_TESTS=OFF -DGGML_NATIVE=OFF \
 && cmake --build /whisper/build -j --config Release --target whisper-cli \
 && mkdir -p /models && curl -fL -o /models/ggml-${WHISPER_MODEL}.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-${WHISPER_MODEL}.bin

FROM eclipse-temurin:21-jre
ARG WHISPER_MODEL=base
# ffmpeg (quadros e áudio), yt-dlp num venv isolado (vídeos por link) e Deno, que o yt-dlp usa para o YouTube
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg curl unzip python3 python3-venv libgomp1 ca-certificates \
 && python3 -m venv /opt/yt-dlp && /opt/yt-dlp/bin/pip install --no-cache-dir --upgrade "yt-dlp[default]" \
 && ln -s /opt/yt-dlp/bin/yt-dlp /usr/local/bin/yt-dlp \
 && curl -fsSL https://deno.land/install.sh | DENO_INSTALL=/usr/local sh -s -- -y \
 && apt-get purge -y unzip && rm -rf /var/lib/apt/lists/*
COPY --from=whisper /whisper/build/bin/whisper-cli /usr/local/bin/whisper-cli
COPY --from=whisper /models /models
# processo do backend roda sem root
RUN useradd --system --uid 10001 --home /data vps && mkdir -p /data && chown vps /data
WORKDIR /app
COPY --from=build /src/build/quarkus-app/ ./
ENV STORAGE_DIR=/data/media VPS_WHISPER_MODEL=/models/ggml-${WHISPER_MODEL}.bin
USER vps
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=12 CMD curl -fs http://localhost:8080/q/health/ready || exit 1
CMD ["java", "-jar", "quarkus-run.jar"]
