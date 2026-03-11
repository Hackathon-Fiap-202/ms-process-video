# process-video

Microsserviço de processamento de vídeos do projeto **nexTime-frame**. Consome mensagens SQS geradas por eventos S3, baixa o vídeo original, extrai frames em paralelo, empacota em ZIP e publica o resultado em outra fila SQS para notificação ao usuário.

## Sumário

- [Visão Geral](#visão-geral)
- [Arquitetura](#arquitetura)
- [Fluxo de Processamento](#fluxo-de-processamento)
- [Mensageria SQS](#mensageria-sqs)
- [Armazenamento S3](#armazenamento-s3)
- [Pré-requisitos](#pré-requisitos)
- [Variáveis de Ambiente](#variáveis-de-ambiente)
- [Desenvolvimento Local](#desenvolvimento-local)
- [Build e Testes](#build-e-testes)
- [Docker](#docker)
- [CI/CD](#cicd)
- [Contribuição](#contribuição)

---

## Visão Geral

- **Linguagem / Framework**: Java 21 + Spring Boot 4.0.2
- **Porta**: `8080`
- **Arquitetura**: Hexagonal (Ports & Adapters)
- **Trigger**: Consumidor SQS com modo de confirmação **MANUAL** (`acknowledgement-mode: MANUAL`)
- **Observabilidade**: Datadog APM via `dd-java-agent.jar`

---

## Arquitetura

```
infrastructure/
  sqs/
    listener/     ← VideoProcessCommandListener (consome video-process-command)
    producer/     ← Publica em video-updated-event
  s3/             ← S3Client (download do MP4, upload do ZIP)

domain/
  service/        ← VideoProcessorService (orquestra extração de frames)
  model/          ← VideoProcessCommand, VideoUpdatedEvent
  port/           ← interfaces de entrada e saída
```

Dependências fluem somente de fora para dentro: `infrastructure → domain/service → domain/model`.

---

## Fluxo de Processamento

```
S3 Event → SQS: video-process-command
                      │
                      ▼  (max-concurrent-messages: 1)
        VideoProcessCommandListener
                      │
                      ▼
        Download MP4 do S3
        video-input-storage/start-process/{uuid}.mp4
                      │
                      ▼
        Extração de frames (thread pool size: 4)
        JCodec — frame a cada N milissegundos
                      │
                      ▼
        Empacotamento em ZIP (em memória)
                      │
                      ▼
        Upload ZIP para S3
        video-processed-storage/end-process/{uuid}.zip
                      │
                      ▼
        Publicação em SQS: video-updated-event
        { "cognitoUserId": "...", "key": "{uuid}.zip", "status": "COMPLETED" }
                      │
                      ▼
        ACK manual da mensagem (DeleteMessage)
```

Em caso de falha em qualquer etapa, o ACK **não** é enviado e a mensagem retorna à fila após o timeout de visibilidade.

---

## Mensageria SQS

| Operação | Fila | Conteúdo |
|---|---|---|
| **Consome** | `video-process-command` | Evento S3 gerado automaticamente quando um arquivo é salvo em `video-input-storage/start-process/` |
| **Publica** | `video-updated-event` | `{ cognitoUserId, key, status }` após conclusão do processamento |

### Configuração do Listener SQS

| Parâmetro | Valor | Descrição |
|---|---|---|
| `max-concurrent-messages` | `1` | Processa um vídeo por vez (intensivo em CPU/memória) |
| `max-messages-per-poll` | `1` | Busca uma mensagem por polling |
| `poll-timeout` | `10` s | Timeout do long polling |
| `acknowledgement-mode` | `MANUAL` | ACK explícito após processamento completo |

---

## Armazenamento S3

| Path | Uso |
|---|---|
| `video-input-storage/start-process/{uuid}.mp4` | Vídeo original baixado para processamento |
| `video-processed-storage/end-process/{uuid}.zip` | ZIP com todos os frames extraídos |

---

## Pré-requisitos

- Java 21
- Maven 3.8+
- Docker e Docker Compose (para dependências locais)

---

## Variáveis de Ambiente

| Variável | Descrição | Padrão (dev) |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Profile ativo (`dev` ou `prod`) | `dev` |
| `APP_BUCKETS_VIDEO_BUCKET_NAME` | Nome do bucket S3 | `nextime-frame-video-storage` |
| `APP_BUCKETS_VIDEO_INPUT_PREFIX` | Prefixo S3 dos vídeos originais | `video-input-storage/` |
| `APP_BUCKETS_VIDEO_PROCESSED_PREFIX` | Prefixo S3 dos ZIPs processados | `video-processed-storage/` |
| `SQS_VIDEO_PROCESS_COMMAND_URL` | URL da fila `video-process-command` | `http://localhost:4566/000000000000/video-process-command` |
| `SQS_VIDEO_UPDATED_EVENT_URL` | URL da fila `video-updated-event` | `http://localhost:4566/000000000000/video-updated-event` |
| `APP_FRAME_EXTRACTION_THREAD_POOL_SIZE` | Número de threads para extração de frames | `4` (dev) / `2` (prod) |
| `APP_FRAME_EXTRACTION_QUEUE_CAPACITY` | Capacidade da fila interna do thread pool | `100` |

> **Atenção**: o arquivo `application.yaml` define `spring.profiles.active: dev` em hard-code. Em produção, a variável de ambiente `SPRING_PROFILES_ACTIVE=prod` sobrescreve esse valor. Ao executar localmente sem essa variável, o profile `dev` será usado.

---

## Desenvolvimento Local

O `docker-compose.yml` sobe o LocalStack (SQS + S3) e o próprio serviço:

```bash
cd process-video

# Copiar variáveis de ambiente
cp .env.example .env
# Editar .env com os valores desejados

# Subir dependências + aplicação
docker compose up -d

# OU executar apenas dependências e rodar a aplicação via Maven
docker compose up localstack -d
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

A aplicação estará disponível em `http://localhost:8080`.

Health check: `http://localhost:8080/actuator/health`

LocalStack: `http://localhost:4566`

---

## Build e Testes

```bash
cd process-video

# Build completo + testes + checkstyle (equivalente ao CI)
mvn clean verify

# Apenas testes
mvn clean test

# Teste de uma classe específica
mvn test -Dtest=VideoProcessorServiceTest

# Teste de um método específico
mvn test -Dtest=VideoProcessorServiceTest#shouldSuccessfullyProcessVideo

# Teste de uma classe aninhada (nested)
mvn test -Dtest="VideoProcessorServiceTest\$SuccessPathTests"

# Build sem testes
mvn clean package -DskipTests

# Apenas checkstyle
mvn checkstyle:check
```

Cobertura mínima: **80%** de linhas no bundle (JaCoCo), verificada em `mvn verify`.

---

## Docker

```bash
cd process-video

# Build da imagem local
docker build -t process-video:local .

# Executar (requer LocalStack rodando)
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e SQS_VIDEO_PROCESS_COMMAND_URL=http://host.docker.internal:4566/000000000000/video-process-command \
  -e SQS_VIDEO_UPDATED_EVENT_URL=http://host.docker.internal:4566/000000000000/video-updated-event \
  process-video:local
```

O Dockerfile inclui o `dd-java-agent.jar` e configura o ENTRYPOINT com `-javaagent:/dd-java-agent.jar` para instrumentação Datadog APM.

---

## CI/CD

O pipeline `.github/workflows/cd-main.yml` é acionado em push para `main`.

| Etapa | Descrição |
|---|---|
| Testes + SonarCloud | `mvn clean verify` + análise SonarCloud (`Hackathon-Fiap-202_ms-process-video`) |
| Build Docker | `docker build -t process-video:$GITHUB_SHA .` |
| Push ECR | Tag e push para o repositório ECR do `infra-core` |
| Deploy ECS | `aws ecs update-service --force-new-deployment` no serviço `nextime-frame-process-video-service` |

**Secrets do GitHub necessários:**

| Secret | Descrição |
|---|---|
| `AWS_ACCOUNT_ID` | ID da conta AWS |
| `AWS_ROLE_ARN` | ARN da role com permissões de deploy |
| `SONAR_TOKEN` | Token de autenticação do SonarCloud |

---

## Estrutura do Projeto

```
process-video/
├── src/
│   ├── main/
│   │   ├── java/com/nextimefood/processvideo/
│   │   │   ├── domain/              # Modelos, interfaces de porta e serviço de processamento
│   │   │   └── infrastructure/      # Listeners SQS, produtores SQS, cliente S3
│   │   └── resources/
│   │       ├── application.yaml     # Configuração base (profile dev)
│   │       └── application-prod.yml # Overrides para produção
│   └── test/                        # Testes unitários (JUnit 5 + Mockito)
├── Dockerfile
├── docker-compose.yml               # LocalStack para dev local
├── .env.example                     # Exemplo de variáveis de ambiente
├── pom.xml
└── README.md
```

---

## Contribuição

Este repositório faz parte do hackathon FIAP — nexTime-frame. Siga o padrão de commits convencional (`feat:`, `fix:`, `docs:`, `chore:`) e as convenções de código definidas no `checkstyle.xml` (Google Java Style estendido, 4 espaços, max 150 colunas). Note que este serviço também proíbe `catch (Exception e)` — trate exceções com tipos específicos de domínio.
