# SearchEngine

Учебный проект поискового движка: обход сайтов по ссылкам, построение поискового индекса на леммах и поиск по этому индексу.

## Возможности

- Индексация сайтов из конфигурации (многопоточно).
- Остановка индексации.
- Индексация/переиндексация отдельной страницы.
- Статистика по сайтам/страницам/леммам.
- Поиск по запросу с релевантностью и сниппетами (подсветка совпадений тегом `<b>`).

## Стек

- Java 17+
- Spring Boot 2.7.x
- Spring Web, Spring Data JPA (Hibernate)
- MySQL
- Thymeleaf (веб-интерфейс)
- Jsoup (загрузка/парсинг HTML)
- Apache Lucene Morphology (лемматизация)
- Lombok
- Maven

## Локальный запуск

### 1) Требования

- Java 17+
- Maven 3.8+
- MySQL 8+

### 2) Настройка MySQL

Создай базу данных:

```sql
CREATE DATABASE IF NOT EXISTS search_engine
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Параметры подключения задаются через переменные окружения (либо можно изменить `src/main/resources/application.yaml`):

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

Пример (macOS/Linux):

```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/search_engine?useSSL=false&requireSSL=false&allowPublicKeyRetrieval=true"
export SPRING_DATASOURCE_USERNAME="root"
export SPRING_DATASOURCE_PASSWORD="your_password"
```

Альтернатива: локальный профиль (чтобы не держать пароль в Git).  
Создай файл `src/main/resources/application-local.yaml` по примеру `src/main/resources/application-local.yaml.example`
и запускай с профилем:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 3) Подключение библиотек лемматизации (Maven settings.xml)

Для зависимостей лемматизации используется приватный Maven-репозиторий Skillbox GitLab.
Нужно добавить заголовок `Private-Token` в `~/.m2/settings.xml` (файл не должен попадать в Git).

Пример (вставь актуальный токен из методички/курса):

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>skillbox-gitlab</id>
      <configuration>
        <httpHeaders>
          <property>
            <name>Private-Token</name>
            <value>PUT_YOUR_TOKEN_HERE</value>
          </property>
        </httpHeaders>
      </configuration>
    </server>
  </servers>
</settings>
```

Если ловишь 401 при скачивании зависимостей — очисти кэш Maven (`~/.m2/repository`) и проверь токен.

### 4) Сборка и запуск

Сборка:

```bash
mvn -DskipTests package
```

Запуск:

```bash
mvn spring-boot:run
```

После старта открой:

- `http://localhost:8080/` — веб-интерфейс

## API

- `GET /api/statistics` — статистика
- `GET /api/startIndexing` — старт индексации
- `GET /api/stopIndexing` — остановка индексации
- `POST /api/indexPage?url=...` — индексация одной страницы
- `GET /api/search?query=...&site=...&offset=0&limit=20` — поиск

Пример поиска:

```bash
curl -G "http://localhost:8080/api/search" --data-urlencode "query=телефон" --data-urlencode "limit=20"
```

## Примечания

- Список сайтов для индексации задаётся в `src/main/resources/application.yaml` (блок `indexing-settings.sites`).
- Поиск по сайту работает только после успешной индексации (статус сайта `INDEXED`).
