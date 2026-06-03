# Niki Bot

Telegram-бот «Ники» — наставник с **Groq** (бесплатный tier) по умолчанию.

> Ключ: [console.groq.com](https://console.groq.com) → API Keys → `GROQ_API_KEY`

## Render

```
GROQ_API_KEY=gsk_...
LLM_PROVIDER=groq
LLM_MODEL=llama-3.3-70b-versatile
TELEGRAM_BOT_TOKEN=...
DB_URL=...
```

Проверка: `/health/status` → `"llmProvider":"groq", "llm":true`

## Локально

```bash
cp .env.example .env
# заполни GROQ_API_KEY, TELEGRAM_BOT_TOKEN, DB_*
mvn spring-boot:run
```

Подробнее: [docs/SETUP_RU.md](docs/SETUP_RU.md)
