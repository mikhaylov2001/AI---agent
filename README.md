# Niki Bot

Telegram-бот «Ники» — личный наставник: цели, память, диалог через **Perplexity Sonar**, вакансии HH.ru.

> **Мозг бота:** [Perplexity API](https://docs.perplexity.ai) (модель `sonar` по умолчанию).  
> Ключ: perplexity.ai → Settings → API. OpenAI/ChatGPT **не нужны**.

📖 **Настройка:** [docs/SETUP_RU.md](docs/SETUP_RU.md)

## Стек

- Java 17, Spring Boot 3.2
- PostgreSQL (Neon)
- **Perplexity Sonar API** (OpenAI-compatible)
- Telegram + Render

## Быстрый старт

```bash
cp .env.example .env
# PERPLEXITY_API_KEY, TELEGRAM_BOT_TOKEN, DB_*

docker compose up -d postgres
mvn spring-boot:run
```

## Render

Переменные: `PERPLEXITY_API_KEY`, `TELEGRAM_BOT_TOKEN`, `DB_*`, `LLM_MODEL=sonar`.

Проверка: `/health/status` → `"llmProvider":"perplexity", "llm":true`.

## Переключить обратно на OpenAI

```env
LLM_PROVIDER=openai
LLM_API_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4o-mini
OPENAI_API_KEY=sk-proj-...
```
