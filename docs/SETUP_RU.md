# Настройка Niki Bot

**ИИ по умолчанию:** Groq (бесплатный tier).

---

## Groq API ключ

1. [console.groq.com](https://console.groq.com) → войти
2. **API Keys** → Create API Key
3. Скопируй `gsk_...`

---

## Переменные

```env
GROQ_API_KEY=gsk_...
LLM_PROVIDER=groq
LLM_MODEL=llama-3.3-70b-versatile
TELEGRAM_BOT_TOKEN=...
DB_URL=jdbc:postgresql://...
DB_USERNAME=...
DB_PASSWORD=...
```

**Модели Groq:**

| Модель | Описание |
|--------|----------|
| `llama-3.3-70b-versatile` | По умолчанию, умная |
| `llama-3.1-8b-instant` | Быстрее, легче |

---

## Render

| Key | Value |
|-----|--------|
| `GROQ_API_KEY` | `gsk_...` |
| `LLM_PROVIDER` | `groq` |
| `LLM_MODEL` | `llama-3.3-70b-versatile` |
| `TELEGRAM_BOT_TOKEN` | от BotFather |
| `DB_*` | Neon |

`/health/status` → `"llm": true, "llmProvider": "groq"`

---

## Perplexity / OpenAI (опционально)

```env
LLM_PROVIDER=perplexity
LLM_API_BASE_URL=https://api.perplexity.ai
LLM_MODEL=sonar
PERPLEXITY_API_KEY=pplx-...
```

---

## Ошибки

| Симптом | Решение |
|---------|---------|
| «ИИ не настроен» | `GROQ_API_KEY` на Render |
| 401 | Неверный ключ — создай новый |
| 429 | Лимит free tier — подожди 1–2 мин |

**Безопасность:** не публикуй ключ в чатах и GitHub. Только `.env` и Render Environment.
