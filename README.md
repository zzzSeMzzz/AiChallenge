AIChallenge — консольный ассистент разработчика с RAG и MCP
==========================================================

**AIChallenge** — это консольный ассистент разработчика на Kotlin, который:

- **общается в виде CLI-чата**;
- **хранит и сжимает историю диалога** (CompressedChatMemory);
- **поддерживает несколько LLM-провайдеров**: YandexGPT и Perplexity;
- **использует RAG по локальной документации** (через Ollama и эмбеддинги);
- **умеет отвечать на вопросы по проекту** с указанием **источников**;
- интегрируется с **MCP-серверами** (web-search, GitHub, сохранение файлов);
- содержит инструмент для **AI-код-ревью GitHub PR**.

Проект написан на **Kotlin/JVM**, использует **Ktor HTTP client**, **kotlinx.serialization**, **Model Context Protocol (MCP)** и локальный LLM-движок **Ollama**.


## Основные возможности

- **Консольный чат с LLM** (YandexGPT или Perplexity) с сохранением и сжатием истории.
- **RAG-поиск по документации проекта** (Markdown/текстовые файлы → эмбеддинги → семантический поиск).
- **Dev-help агент**: отвечает на вопросы о проекте, комбинируя RAG и (опционально) git-контекст.
- **GitHub PR review** через MCP-сервер GitHub + локальный RAG-контекст проекта + модель в Ollama.
- **Поддержка MCP-клиентов**:
  - web-search (поиск в интернете);
  - GitHub PR сервер;
  - кастомный сервер сохранения данных в файлы.


## Технологический стек

- **Язык**: Kotlin 2.2 (JVM)
- **Сборка**: Gradle (Kotlin DSL), JDK 17
- **Сетевой слой**: Ktor Client (CIO, JSON, logging)
- **Сериализация**: kotlinx.serialization
- **LLM-провайдеры**:
  - YandexGPT (`https://llm.api.cloud.yandex.net`)
  - Perplexity (`https://api.perplexity.ai`)
  - Ollama (локальный HTTP API, по умолчанию `http://127.0.0.1:11434`)
- **MCP**: `io.modelcontextprotocol:kotlin-sdk` и внешние MCP-серверы (Node.js / Java).


## Архитектура проекта

Код лежит в `src/main/kotlin` и логически разделён на несколько модулей.

- **`Main.kt`**
  - Точка входа приложения (`application.mainClass = "MainKt"`).
  - Настраивает LLM-клиента (YandexGPT), RAG, реестр инструментов и CLI-цикл.
  - Реализует консольный интерфейс (команды `rag:`, `/help`, `/review`, `s:`, `st:`, `exit` и свободный диалог).

- **`core/Const.kt`**
  - Базовые константы URL:
    - `SERVER_URL` — YandexGPT;
    - `SERVER_URL_PERPLEXITY` — Perplexity.

- **`core/data/base/*`** — модель данных для LLM и RAG
  - `ChatMessage` — универсальное сообщение чата (role + content), конвертируется в форматы YandexGPT и Perplexity.
  - `Role` — роль сообщения (USER, ASSISTANT, SYSTEM, TOOL).
  - `LlmClient` — интерфейс для клиента LLM (`suspend fun chat(...)`).
  - `ChunkModels.kt`:
    - `Chunk` — сырой текстовый фрагмент документа;
    - `EmbeddedChunk` — фрагмент с вектором-эмбеддингом;
    - `EmbeddingIndex` — индекс всех эмбеддов (dimension, chunks, totalChunks);
    - `ScoredChunk` — фрагмент + косинусная близость.
  - Дополнительные модели для интеграций: Yandex (`ya/*`), Perplexity (`perplexety/*`), Ollama (`olama/*`).

- **`core/network/*`** — HTTP-клиенты LLM
  - `Client.kt` — клиент к YandexGPT:
    - формирует `YaGptRequest`;
    - отправляет запрос на `/foundationModels/v1/completion`;
    - возвращает объект, реализующий `AiAnswer` (ответ + статистика токенов/стоимости);
    - использует `BuildConfig.YA_API_KEY` и `SERVER_URL`.
  - `PerClient.kt` — клиент к Perplexity API (`/chat/completions`):
    - принимает список сообщений `PerMessage`;
    - возвращает `PerplexityResponse`, также приводит его к `AiAnswer`.
  - `OllamaClient.kt` — клиент к локальному серверу Ollama:
    - `ask(prompt, model)` — генерация ответа (streaming-режим, сбор в одну строку);
    - `embedSingle(text, model)` / `embed(texts, model)` — получение эмбеддингов;
    - RAG-утилиты высокого уровня:
      - `answerWithRag` — простой RAG;
      - `ragAnswerWithSources` — RAG с указанием источников;
      - `answerWithAdvancedRAG` — расширенный режим с порогом похожести и LLM-rerank;
      - `rerankWithLLM` — LLM-реранкер поверх top-K кандидатов.

- **`core/utils/*`**
  - `ClientManager` — фасад над `Client` (YandexGPT) и `PerClient` (Perplexity):
    - выбор клиента по `AiClientType`;
    - единый метод `ask(...)` возвращает `AiAnswer?`.
  - `AiAnswer` — интерфейс для унифицированного ответа LLM.
  - `AiClientType` — перечисление: `PERPLEXITY`, `YANDEX_GPT`.
  - `CompressedChatMemory` — реализация сжатой памяти чата:
    - хранит полную историю (`fullHistory`) и недавние сообщения (`recentMessages`);
    - по достижении `summaryEveryN` сообщений вызывает LLM для создания краткого summary;
    - сохраняет историю в `chat_history.json`, summaries — в `chat_summaries.json`;
    - предоставляет `buildContext(systemPrompt)` для построения компактного контекста диалога.
  - `McpClientManager` — управление подключениями к MCP-серверам:
    - `createWebSearchClient()` — запускает NodeJS-пакет `@guhcostan/web-search-mcp` через `npx`;
    - `createGitHub(githubToken)` — запускает MCP-сервер GitHub `@modelcontextprotocol/server-github`;
    - `createSavingClient()` — запускает кастомный Java MCP-сервер (JAR, путь захардкожен);
    - хранит `clients`, `transports`, `processes`, умеет корректно закрывать процессы (`closeClients`).
  - `Utils.kt` — вспомогательная функция `String.trimUntilKeyword(keyword)` для обрезки путей.

- **`core/utils/rag/*`** — RAG-инфраструктура
  - `CollectChunksUtil.kt`:
    - `buildIndexFromDirectory(rootDir, model, outputFile)` — построение эмбеддинг-индекса из директории:
      - рекурсивно собирает `.md` и `.txt` файлы;
      - разбивает текст на чанки (`RecursiveTextSplitter`);
      - эмбеддит чанки через `OllamaClient.embed`;
      - сохраняет `EmbeddingIndex` в `index.json`.
    - `collectChunks(rootDir, splitter)` — сбор и предобработка чанков.
  - `EmbeddingUtils.kt`:
    - `loadIndex(json, path)` — загрузка индекса из файла;
    - `cosineSim` — косинусная похожесть;
    - `retrieveTopK`, `retrieveTopKWithScore`, `retrieveTopKScoredChunks` — поиск ближайших чанков;
    - `filterByThreshold` — отсеивание по порогу;
    - `buildRagPrompt(question, contexts)` — сбор промпта с контекстом.
  - `RecursiveTextSplitter.kt`:
    - рекурсивный сплиттер текста с учётом токенов;
    - поддерживает `chunkSize`, `chunkOverlap`, `maxContextTokens`;
    - использует `ktoken` для подсчёта токенов;
    - предоставляет `splitTextWithLimits`/`splitTextWithOverlap` и другие вспомогательные методы.
  - `TextPreprocessor.kt`:
    - нормализация текста (BOM, переносы, пробелы);
    - `normalizeMarkdown` — нормализация Markdown-разметки.

- **`core/agent/base/*`** — абстракции инструментов (Tools)
  - `Tool` — интерфейс инструмента (name, description, parameters, `execute`).
  - `ToolParameters` / `ToolType` / `ToolContext` / `SessionContext` — вспомогательные структуры для описания и вызова tools.
  - `ToolRegistry` / `DefaultToolRegistry` — хранение и поиск инструментов.
  - `ToolExecutor` — исполняет `ToolCall`, поддерживает цепочку `ToolInterceptor` для кросс-срезной логики.
  - `ToolCall` — DTO для вызова инструмента из LLM (id, name, arguments).
  - `ToolResult` — результат выполнения (`Ok` с JSON-строкой или `Error` с сообщением).
  - `VectorDb` / `JsonVectorDb` — абстракция и JSON-реализация векторной БД (используется для RAG).

- **`core/agent/tools/*`** — конкретные инструменты агента
  - `RagSearchTool` (`name = "semantic_search"`):
    - выполняет RAG-поиск по `EmbeddingIndex` через `OllamaClient.ragAnswerWithSources`;
    - возвращает JSON с `RagAnswer` (ответ + источники).
  - `HelpTool` (`name = "dev_help"`):
    - принимает вопрос разработчика о проекте (`question`);
    - обращается к `RagSearchTool` и, при наличии, к git tool;
    - собирает `DevHelpPayload` (question + docs + git) и возвращает его как JSON.
  - `GithubPrTool`:
    - через `McpClientManager.createGitHub` подключается к MCP-серверу GitHub;
    - вызывает MCP‑tools: `get_pull_request`, `get_pull_request_files`, `get_pr_diff`;
    - возвращает агрегированный текстовый контекст PR (мета, файлы, diff).
  - `PrReviewTool` (`name = "pr_review"`):
    - использует `GithubPrTool` + `EmbeddingIndex` + `OllamaClient`;
    - формирует промпт для детального ревью PR (общие впечатления, плюсы, проблемы, рекомендации, итог);
    - генерирует Markdown‑отчёт по PR через модель `qwen2.5:3b`.


## Консольный интерфейс (`Main.kt`)

После запуска приложения поднимается консольный чат. Важные элементы настройки:

- **Клиент LLM по умолчанию**: `AiClientType.YANDEX_GPT`
- **Модель YandexGPT**: `"yandexgpt-lite"`
- **Максимум токенов**: `maxTokens = 700`
- **Сжатая память**: `CompressedChatMemory(summaryEveryN = 10)`
- **RAG**:
  - создаётся `OllamaClient(defaultModel = "nomic-embed-text:latest")` для эмбеддингов;
  - индекс загружается из `index.json` (`loadIndex(Json { ignoreUnknownKeys = true }, "index.json")`);
  - поисковый инструмент: `RagSearchTool(ollama, index)`.
- **Реестр инструментов**:
  - `semantic_search` (RAG);
  - `dev_help` (HelpTool);
  - `github_pr` (GithubPrTool);
  - `pr_review` (PrReviewTool).

Поддерживаемые команды в чате:

- **Завершение работы**:
  - `exit`, `выход`, `quit` — выход из чата, печать статистики summary, закрытие клиентов.

- **Обзор/ревью PR**:
  - `/review <owner> <repo>` — вызывает `pr_review` tool:
    - `owner` — владелец репозитория на GitHub;
    - `repo` — имя репозитория;
    - внутри используется `pull_number = 1` (для примера, можно доработать).

- **Dev-help по проекту**:
  - `/help <вопрос>` — вызывает `dev_help` tool:
    - ищет релевантные фрагменты документации через RAG;
    - опционально добавляет git-контекст (если настроен git MCP tool);
    - выводит вопрос, git‑контекст и ответ агента с указанием источников.

- **RAG-запрос напрямую**:
  - `rag: <вопрос>` — использует локальную модель (`qwen2.5:3b`) и RAG индекс:
    - печатает ответ + список источников;
    - сохраняет ответ и источники в историю `CompressedChatMemory`;
    - при отсутствии релевантного контекста дополнительно обращается к выбранному LLM (YandexGPT).

- **Системный промпт**:
  - `s:<текст>` — устанавливает/изменяет текущий системный промпт, который будет добавляться в контекст чата.

- **Статистика памяти**:
  - `st:` — печатает статистику `CompressedChatMemory` (кол-во сообщений, summaries, последние сообщения).

- **Обычный текст** (без префикса):
  - трактуется как обычный запрос к текущему LLM-клиенту (YandexGPT/Perplexity);
  - используется сжатый контекст (`buildContext(systemPrompt)`), включающий историю и summary.


## Подготовка окружения

### Требования

- **JDK 17+**
- **Gradle** (или используйте `gradlew/gradlew.bat` из проекта)
- **Kotlin** (подтягивается плагином Gradle)
- **Node.js + npm/npx** — для MCP-серверов web-search и GitHub
- **Ollama** — локальный LLM-сервер:
  - должен быть запущен и доступен по `http://127.0.0.1:11434`
  - требуемые модели:
    - для эмбеддингов: например `mxbai-embed-large` или `nomic-embed-text:latest`;
    - для генерации и RAG: `qwen2.5:3b` (можно заменить при необходимости).


### Настройка `local.properties`

Файл `local.properties` **обязателен** и не хранится в репозитории (локальный для разработчика). Его читает `build.gradle.kts`, и на его основе генерируется `core.BuildConfig`.

Пример содержимого `local.properties`:

```properties
YA_API_KEY=ваш_ключ_YandexGPT
CLOUD_FOLDER=ваш_cloud_folder_id_в_Yandex_Cloud
PERPLEXITY_API_KEY=ваш_ключ_Perplexity
GITHUB_TOKEN=ghp_ВашGithubToken
```

При сборке Gradle выполняет задачу `generateBuildConfig`, создавая файл `BuildConfig.kt` (в `build/generated/src/main/kotlin`), содержащий константы API-ключей.


### Настройка MCP-серверов

MCP-сервера стартуют из `McpClientManager`:

- **Web-search MCP**:
  - используется команда:
    - `npx -y @guhcostan/web-search-mcp@latest`
  - убедитесь, что пакет устанавливается и корректно запускается.

- **GitHub MCP**:
  - используется команда:
    - `npx -y @modelcontextprotocol/server-github --github-token <GITHUB_TOKEN>`
  - требует действительный `GITHUB_TOKEN` с доступом к нужным репозиториям.

- **Saving MCP (кастомный сервер)**:
  - запускается как Java JAR (`java -jar <path_to_jar>`);
  - путь к JAR сейчас захардкожен в `McpClientManager.createSavingClient()` и, вероятно, потребуется изменить под вашу среду.

Если какой-то MCP-сервер вам не нужен, соответствующие вызовы можно либо закомментировать, либо адаптировать.


## RAG-индекс: генерация и обновление

Для RAG-поиска используется **эмбеддинг-индекс**, который хранится в `index.json` (в корне проекта) и загружается в `Main.kt` через `loadIndex(...)`.

Чтобы **пересобрать индекс**:

1. Подготовьте директорию с документацией, например:
   - `src/main/res/project_descr/`
   - `src/main/res/readme/`
2. Убедитесь, что Ollama запущен и доступна модель для эмбеддингов (по умолчанию `mxbai-embed-large` или `nomic-embed-text:latest`).
3. Вызовите функцию `buildIndexFromDirectory` из `core.utils.rag.CollectChunksUtil` (например, временно из `main` или отдельного утилитарного entry-point):

   ```kotlin
   buildIndexFromDirectory(
       rootDir = "src/main/res/project_descr",
       model = "mxbai-embed-large",
       outputFile = "index.json"
   )
   ```

4. После генерации убедитесь, что `index.json` лежит в корне проекта и доступен приложению.

При запуске основного чата индекс просто считывается:

- `val index = loadIndex(Json { ignoreUnknownKeys = true }, "index.json")`


## Сборка и запуск

Из корня проекта:

- **Сборка**:

```bash
./gradlew build
```

(в Windows: `gradlew.bat build`).

- **Запуск чата**:

```bash
./gradlew run
```

Приложение запустит консольный чат, вы увидите примерно:

- `Консольный чат с YANDEX_GPT, модель yandexgpt-lite, maxTokens 700`
- подсказки по доступным командам (`exit`, `s:`, `rag:` и т.д.).

Также можно запустить собранный JAR из `build/libs`:

```bash
java -jar build/libs/AiChallenge-1.0-SNAPSHOT.jar
```


## Типовой сценарий использования

1. **Подготовить окружение**:
   - установить JDK 17+ и Node.js;
   - настроить `local.properties` с API-ключами;
   - запустить Ollama и скачать нужные модели;
   - при необходимости — пересобрать `index.json` для RAG.
2. **Собрать и запустить проект** (`./gradlew run`).
3. **Общаться с ассистентом**:
   - задавать вопросы в свободной форме (LLM);
   - использовать `rag: вопрос` для RAG‑ответа с источниками;
   - использовать `/help вопрос` для dev-help по проекту;
   - использовать `/review owner repo` для AI‑ревью PR (при настроенном MCP GitHub).


## Дальнейшее развитие

Идеи для расширения проекта:

- добавить больше инструментов MCP (CI/CD, трекеры задач, базы данных);
- расширить поддерживаемые модели (другие провайдеры или локальные LLM);
- вынести генерацию RAG-индекса в отдельный Gradle task или CLI-команду;
- улучшить CLI-интерфейс (цветной вывод, история команд, конфигурация через флаги);
- реализовать веб-интерфейс поверх существующей логики (например, Ktor server или Compose Multiplatform).

Текущая версия уже демонстрирует связку: **CLI-чат → LLM → RAG по локальным документам → MCP-инструменты (GitHub/WebSearch/Save)** для удобной работы разработчика с кодом и документацией.