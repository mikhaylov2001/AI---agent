# Настройка Niki Bot

**ИИ:** Perplexity Sonar (не ChatGPT, не OpenAI по умолчанию).

---

## Шаг 1. Ключ Perplexity

1. Зайди на [perplexity.ai](https://www.perplexity.ai)
2. **Settings → API** → Create API key
3. Скопируй ключ `pplx-...`
4. Пополни баланс / проверь лимиты на аккаунте

---

## Шаг 2. Telegram + Neon

См. предыдущие шаги в этом файле — без изменений.

---

## Шаг 3. Переменные окружения

```env
PERPLEXITY_API_KEY=pplx-...
TELEGRAM_BOT_TOKEN=...
TELEGRAM_BOT_USERNAME=NikiMindBot
DB_URL=jdbc:postgresql://...
DB_USERNAME=...
DB_PASSWORD=...
TELEGRAM_DELIVERY_MODE=polling   # локально
```

**Модели Perplexity:**

| Модель | Описание |
|--------|----------|
| `sonar` | Быстрая, дешевле (по умолчанию) |
| `sonar-pro` | Умнее, с поиском в интернете |

```env
LLM_MODEL=sonar-pro
```

---

## Шаг 4. Render

| Key | Value |
|-----|--------|
| `PERPLEXITY_API_KEY` | `pplx-...` |
| `LLM_PROVIDER` | `perplexity` |
| `LLM_MODEL` | `sonar` |
| `TELEGRAM_BOT_TOKEN` | от BotFather |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Neon |

Проверка: `https://СЕРВИС.onrender.com/health/status`

```json
{
  "status": "ready",
  "llm": true,
  "llmProvider": "perplexity",
  "llmModel": "sonar"
}
```

---

## OpenAI вместо Perplexity (опционально)

```env
LLM_PROVIDER=openai
LLM_API_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4o-mini
OPENAI_API_KEY=sk-proj-...
```

---

## Ошибки

| Симптом | Решение |
|---------|---------|
| «ИИ не настроен» | Добавь `PERPLEXITY_API_KEY` |
| 401 | Неверный ключ |
| 403 | Нет баланса / лимит на perplexity.ai |
| 429 | Подожди или смени модель на `sonar` |
