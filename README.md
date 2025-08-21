# SoldinRegister

**Создатель плагина: _Soldi_n .jar_**

Плагин авторизации для Spigot/Paper с поддержкой:
- Регистрация/вход с хешированием паролей (PBKDF2 + соль + итерации)
- 2FA TOTP (Google Authenticator)
- Лимит аккаунтов на один IP
- SQLite/MySQL на выбор
- Блокировка чата/команд/взаимодействия до входа, опционально блок передвижения
- Сессии с TTL
- Таймер авторизации (по умолчанию 60 сек) — кик по истечении
- Ограничение попыток входа: показываются оставшиеся попытки (3→2→1), после 0 — кик
- Команда отвязки 2FA: игрок `/soldinregister 2fa unbind <пароль>`, админ `/soldinregister 2fa reset <ник>`

## Команды для игроков
- `/register <пароль>` (`/reg`) — регистрация
- `/login <пароль> [код2FA]` (`/l`) — вход
- `/changepass <старый> <новый>` — смена пароля
- `/soldinregister 2fa enable` — сгенерировать секрет и ссылку `otpauth://...`
- `/soldinregister 2fa confirm <код>` — подтвердить и включить 2FA
- `/soldinregister 2fa disable <код>` — выключить 2FA
- `/soldinregister 2fa unbind <пароль>` — отвязать 2FA, подтвердив паролем
- `/soldinregister 2faurl` — показать ссылку ещё раз

## Команды для админов (perm: `soldinregister.admin`)
- `/soldinregister reload` — перезагрузка конфига
- `/soldinregister adminchangepass <ник> <новый>` — смена пароля игрока
- `/soldinregister admindelete <ник>` — удалить аккаунт игрока
- `/soldinregister 2fa reset <ник>` — сбросить 2FA игроку

## Конфигурация (config.yml)
Ключевые параметры:
- `storage.type`: `SQLITE` или `MYSQL`
- `limits.max_accounts_per_ip`: максимум аккаунтов на IP (0 = без лимита)
- `locks.*`: блокировки (чат/команды/движение/взаимодействие)
- `session.enable`, `session.ttl_ms`: сессии
- `timeouts.auth_seconds`: время на регистрацию/логин после входа на сервер
- `security.min_password_length`, `security.max_login_attempts`
- `twofa.*`: параметры 2FA
- `messages.*`: тексты, включая строки про попытки и таймаут (везде указан создатель)

## Сборка (GitHub Actions)
В репозитории приложены **pom.xml** и **build.gradle** — можешь собрать Maven или Gradle.
В `.github/workflows/build.yml` присутствует джоб, который собирает **оба** и выкладывает артефакты.

### Локально
- **Maven:** `mvn -B package`
- **Gradle:** `./gradlew build` (или `gradle build`)

## Примечания безопасности
- Пароли не хранятся открыто — только хеши PBKDF2 с солью.
- 2FA — совместима с Google Authenticator и аналогами.