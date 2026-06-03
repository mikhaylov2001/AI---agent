# Niki Bot

Telegram-бот «Ники» — личный наставник: цели, напоминания, диалог через **OpenAI API**, поиск вакансий и отклики на HH.ru.

> **ChatGPT Plus не нужен.** Нужен ключ с [platform.openai.com](https://platform.openai.com/api-keys) и баланс на аккаунте.

📖 **Пошаговая настройка:** [docs/SETUP_RU.md](docs/SETUP_RU.md)

## Стек

- Java 17, Spring Boot 3.2
- PostgreSQL (Neon)
- Telegram (webhook на Render / polling локально)
- OpenAI API
- Docker + [Render](https://render.com)

## Быстрый старт локально

```bash
cp .env.example .env
# заполни TELEGRAM_BOT_TOKEN, OPENAI_API_KEY, DB_*

docker compose up -d postgres
mvn spring-boot:run
```

Напиши боту `/start` и любой текст.

## Деплой на Render

1. Подключи репозиторий [AI---agent](https://github.com/mikhaylov2001/AI---agent).
2. **Environment: Docker**.
3. Переменные — см. `.env.example` и [docs/SETUP_RU.md](docs/SETUP_RU.md).
4. Проверка: `/health` → `ok`, `/health/status` → `"status":"ready"`.

## Команды бота

- `/start`, `/help`
- `/goals`, `/addgoal [цель]`
- `/jobs [запрос]`
- `/connect_hh`, `/hh_resumes`, `/use_resume [id]`
- `/apply [url]`, `/confirm_apply [url]`

Любой текст без команды — диалог с Ники через OpenAI.

## HH.ru

[dev.hh.ru](https://dev.hh.ru) — Redirect URI = `https://<домен>/hh/callback`.
