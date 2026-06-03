# Настройка Niki Bot — пошагово

Бот работает через **OpenAI API** (platform.openai.com). Подписка **ChatGPT Plus не нужна** — это разные продукты.

---

## Шаг 1. OpenAI API ключ

1. Зайди на [platform.openai.com](https://platform.openai.com) (нужен VPN, если из РФ).
2. **API keys** → **Create new secret key**.
3. Пополни баланс: **Settings → Billing** (без баланса будет ошибка 403/429).
4. Скопируй ключ вида `sk-proj-...` — он показывается один раз.

---

## Шаг 2. Telegram бот

1. Напиши [@BotFather](https://t.me/BotFather) → `/newbot`.
2. Сохрани **token** и **username** (без `@`).

---

## Шаг 3. База Neon

1. [neon.tech](https://neon.tech) → создай проект PostgreSQL.
2. Скопируй connection string:
   `postgresql://user:pass@host/neondb?sslmode=require`
3. Преобразуй в три переменные:

```env
DB_URL=jdbc:postgresql://HOST/neondb?sslmode=require
DB_USERNAME=user
DB_PASSWORD=pass
```

---

## Шаг 4. Локальный запуск

```bash
cd /Users/mikhailov/Desktop/MyProject/niki-bot
cp .env.example .env
# открой .env и вставь TELEGRAM_BOT_TOKEN, OPENAI_API_KEY, DB_*

docker compose up -d postgres
mvn spring-boot:run
```

Локально используется **polling** (`TELEGRAM_DELIVERY_MODE=polling` в `.env.example`).

Напиши боту в Telegram: `/start`, затем любой текст — должен ответить Ники через OpenAI.

---

## Шаг 5. Деплой на Render

1. Репозиторий: [github.com/mikhaylov2001/AI---agent](https://github.com/mikhaylov2001/AI---agent)
2. Render → **New Web Service** → Docker.
3. **Environment Variables:**

| Key | Value |
|-----|--------|
| `TELEGRAM_BOT_TOKEN` | от BotFather |
| `TELEGRAM_BOT_USERNAME` | `NikiMindBot` |
| `TELEGRAM_DELIVERY_MODE` | `webhook` |
| `OPENAI_API_KEY` | `sk-proj-...` |
| `DB_URL` | JDBC Neon |
| `DB_USERNAME` | Neon user |
| `DB_PASSWORD` | Neon password |

4. `TELEGRAM_WEBHOOK_URL` — URL сервиса на Render, например `https://ai-agent-fgo1.onrender.com`  
   (или Render сам задаст `RENDER_EXTERNAL_URL`).

5. После деплоя проверь:
   - `https://ТВОЙ-СЕРВИС.onrender.com/health` → `ok`
   - `https://ТВОЙ-СЕРВИС.onrender.com/health/status` → `"openai": true, "telegram": true, "status": "ready"`

6. В логах Render: `Telegram WEBHOOK установлен: https://.../telegram/webhook`

**Важно:** удали на Render переменные `HIBERNATE_*` / `SPRING_JPA_*` со значением `disabled` — они ломают старт.

---

## Если OpenAI не отвечает

| Симптом | Решение |
|---------|---------|
| «OpenAI не настроен» | Добавь `OPENAI_API_KEY` |
| 401 | Неверный ключ — создай новый |
| 403 | Нет баланса или регион; на Render обычно работает |
| 429 | Лимит — подожди или пополни баланс |
| Локально не работает | VPN или прокси через `OPENAI_API_BASE_URL` |

### OpenRouter (альтернатива OpenAI)

```env
OPENAI_API_BASE_URL=https://openrouter.ai/api/v1
OPENAI_API_KEY=ключ_с_openrouter.ai
OPENAI_MODEL=openai/gpt-4o-mini
```

---

## HH.ru (необязательно)

Без HH работают: `/start`, цели, свободный диалог с Ники.

Для вакансий: [dev.hh.ru](https://dev.hh.ru) → приложение → `HH_CLIENT_ID`, `HH_CLIENT_SECRET`, redirect = `https://ТВОЙ-ДОМЕН/hh/callback`.
