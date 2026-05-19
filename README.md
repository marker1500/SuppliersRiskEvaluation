# Supply & Contract Risk System

Курсовой проект: клиент-серверная система учета поставок и контрактов с модулем оценки рисков по срокам, объёму заказа и этапу исполнения.

## Что реализовано

- Архитектура client-server (JavaFX desktop client + TCP server)
- Асинхронный обмен JSON по TCP/IP сокетам (в ответах для пользователя текст без JSON на основных экранах)
- Многопоточная обработка запросов на сервере (`FixedThreadPool`)
- ORM слой (Hibernate/JPA)
- Аутентификация и авторизация с ролями
- Бизнесовые команды, включая `GET_ORDERS` (список заказов с расчётом риска)
- Модуль риска по Strategy (`WeightedRiskStrategy`) с учётом объёма единиц товара, сроков и статусов поставок
- Unit tests (JUnit)
- Dockerfile + Docker Compose (server + PostgreSQL 17)
- Пакет диаграмм: UML / BPMN / IDEF0 / IDEF1X

## Стек

- Java 21
- Maven
- JavaFX
- Hibernate 6 + JPA
- **PostgreSQL** (основная БД приложения при локальной разработке)
- H2 (runtime-драйвер; можно использовать только если передать `-Dapp.db.url=jdbc:h2:...` — основной режим всё равно PostgreSQL)
- JUnit 5, Mockito

## Локальный запуск с PostgreSQL

По умолчанию сервер подключается к:

- `jdbc:postgresql://localhost:5432/supply_db`
- пользователь `supply_user`, пароль `1111` (переопределите через `APP_DB_PASSWORD`, если у вас другой)

**Сначала поднимите Postgres** (проще всего через Compose — только база):

```bash
docker compose up -d postgres
```

Подождите, пока контейнер примет подключение на порту **5432**, затем запустите сервер и клиент на хосте.

1) Сервер:

```powershell
mvn -DskipTests exec:java "-Dexec.mainClass=by.bsuir.coursework.server.ServerMain"
```

2) Клиент (JavaFX):

```powershell
mvn -DskipTests javafx:run
```

Параметры БД можно переопределить: `APP_DB_URL`, `APP_DB_USER`, `APP_DB_PASSWORD` или `--app.db.url=...`.

Для временного перехода на H2 можно явно указать URL H2 через `--app.db.url=jdbc:h2:file:./data/supplydb;AUTO_SERVER=TRUE` (техническая опция для отладки, не является основной).

## Запуск через Docker (PostgreSQL + сервер)

```bash
docker compose up --build
```

Сервис `server` использует Postgres из compose: пользователь `supply_user`, пароль **`1111`**.

Если контейнер Postgres уже создавался со старым паролем, удалите том: `docker compose down -v`, затем `docker compose up -d postgres` заново.

## Сборка jar

```bash
mvn -DskipTests package
```

Итоговый fat-jar: `target/supply-contract-risk-system-1.0.0.jar`

## Бизнес-поля при создании контракта

В команде `CREATE_CONTRACT` поддерживаются поля `number`, `supplierId`, `dueDate`, `amount`, при желании **`quantityUnits`** (объём заказа в единицах товара; по умолчанию `1000`).

## Основные команды (частичный список)

- `LOGIN`, `REGISTER`
- `GET_ORDERS`
- `GET_DASHBOARD`
- `CALCULATE_CONTRACT_RISK`
- `CREATE_SUPPLIER`, `CREATE_CONTRACT`, `CREATE_SHIPMENT`, ...
- см. код `CommandType` и `RequestRouter`

## Документация/модели

- UML: `docs/uml/`
- BPMN: `docs/bpmn/overdue-handling.bpmn`
- IDEF0: `docs/idef/idef0.md`
- IDEF1X: `docs/idef/idef1x.md`
- SQL baseline: `db/init/V1__baseline.sql`
