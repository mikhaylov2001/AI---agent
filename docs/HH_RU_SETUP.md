# Настройка HH.ru для Niki Bot

Пошаговая инструкция: OAuth, отклики, алерты вакансий.

---

## Шаг 1. Регистрация приложения на dev.hh.ru

1. Открой [dev.hh.ru](https://dev.hh.ru/admin)
2. Войди под аккаунтом **соискателя** (тот же, что на hh.ru)
3. **Мои приложения** → **Добавить приложение**
4. Заполни:
   - **Название:** Niki Bot (или любое)
   - **Redirect URI:** `https://ТВОЙ-СЕРВИС.onrender.com/hh/callback`  
     Пример: `https://ai-agent-fgo1.onrender.com/hh/callback`
   - **Права (scopes):** минимум доступ к резюме и откликам соискателя
5. Сохрани **Client ID** и **Client Secret**

---

## Шаг 2. Переменные на Render

В **Environment** добавь:

| Key | Value |
|-----|--------|
| `HH_CLIENT_ID` | из dev.hh.ru |
| `HH_CLIENT_SECRET` | из dev.hh.ru |
| `HH_REDIRECT_URI` | `https://ТВОЙ-СЕРВИС.onrender.com/hh/callback` |

**Redirect URI в dev.hh.ru и в Render должны совпадать символ в символ.**

После изменений — **Manual Deploy**.

---

## Шаг 3. Подключение в Telegram

1. Напиши боту: **🔗 HH** или `/connect_hh`
2. Нажми ссылку «Авторизоваться на HH.ru»
3. Войди в HH → разреши доступ
4. Вернись в Telegram — бот напишет «HH.ru подключён»

---

## Шаг 4. Выбор резюме

```
/hh_resumes
```

Скопируй **ID** нужного резюме:

```
/use_resume abc123def456
```

---

## Шаг 5. Отклик на вакансию

1. **💼 Вакансии** — поиск или алерт
2. Скопируй ссылку, например: `https://hh.ru/vacancy/12345678`
3. Отправь:

```
/apply https://hh.ru/vacancy/12345678
```

4. Бот сгенерирует письмо → подтверди:

```
/confirm_apply https://hh.ru/vacancy/12345678
```

---

## Автопилот: вакансии без твоего сообщения

По умолчанию бот **2 раза в день** ищет новые вакансии и присылает в Telegram.

| Команда | Действие |
|---------|----------|
| `/job_query Java backend Spring` | Что искать |
| `/job_alerts on` | Включить алерты |
| `/job_alerts off` | Выключить |
| `/autopilot off` | Выключить все проактивные сообщения |

---

## Частые ошибки

| Проблема | Решение |
|----------|---------|
| «HH не настроен на сервере» | Добавь `HH_CLIENT_ID` и `HH_CLIENT_SECRET` на Render |
| Ошибка после авторизации | Redirect URI не совпадает — проверь dev.hh.ru и `HH_REDIRECT_URI` |
| «HH не подключён» | Снова `/connect_hh` |
| Отклик не уходит | Сначала `/use_resume [id]` |
| 403 от HH | Проверь права приложения на dev.hh.ru |

---

## Локально

В `.env`:

```env
HH_CLIENT_ID=...
HH_CLIENT_SECRET=...
HH_REDIRECT_URI=http://localhost:8080/hh/callback
```

Для OAuth локально нужен **публичный URL** (ngrok) — HH не принимает localhost.  
Проще настраивать HH сразу на Render.
