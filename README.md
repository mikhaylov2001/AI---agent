# Niki Bot

Telegram-бот «Ники» — личный наставник: цели, напоминания, диалог через OpenAI, поиск вакансий и отклики на HH.ru.

## Стек

- Java 17, Spring Boot 3.2
- PostgreSQL (Neon)
- Telegram Long Polling
- OpenAI API
- Docker + [Render](https://render.com)

## Быстрый старт локально

```bash
cp .env.example .env
# заполни .env

docker compose up -d postgres
mvn spring-boot:run
```

## Деплой на Render

1. Создай репозиторий на GitHub и запушь этот проект.
2. На [render.com](https://render.com): **New → Web Service** → подключи репозиторий.
3. **Environment: Docker** (Render подхватит `Dockerfile`).
4. Добавь переменные окружения (см. `.env.example`):

| Переменная | Пример |
|------------|--------|
| `TELEGRAM_BOT_TOKEN` | от @BotFather |
| `TELEGRAM_BOT_USERNAME` | `NikiMindBot` |
| `OPENAI_API_KEY` | ключ OpenAI |
| `DB_URL` | `jdbc:postgresql://ep-xxx.neon.tech/neondb?sslmode=require` |
| `DB_USERNAME` | `neondb_owner` |
| `DB_PASSWORD` | пароль Neon |
| `HH_REDIRECT_URI` | `https://твой-сервис.onrender.com/hh/callback` |

5. После деплоя проверь: `https://твой-сервис.onrender.com/health` → `ok`.

**Важно:** в Environment на Render **удали** переменные `SPRING_JPA_PROPERTIES_HIBERNATE` или `HIBERNATE_*` со значением `disabled` / `{}` — они ломают старт.
6. Напиши боту в Telegram: `/start`.

### Neon → JDBC

Строка Neon вида:

`postgresql://USER:PASS@HOST/neondb?sslmode=require`

Преобразуй в:

- `DB_URL=jdbc:postgresql://HOST/neondb?sslmode=require`
- `DB_USERNAME=USER`
- `DB_PASSWORD=PASS`

## Команды бота

- `/start`, `/help`
- `/goals`, `/addgoal [цель]`
- `/jobs [запрос]`
- `/connect_hh`, `/hh_resumes`, `/use_resume [id]`
- `/apply [url]`, `/confirm_apply [url]`

## HH.ru

Зарегистрируй приложение на [dev.hh.ru](https://dev.hh.ru). Redirect URI = `https://<твой-домен>/hh/callback`.
