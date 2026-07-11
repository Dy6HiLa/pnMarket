# pnMarket

<p align="center">
  Аукцион для Minecraft-серверов на Paper 1.21.8+.
  <br>
  GUI рынка, частичная покупка, поиск, собственные категории и SQLite по умолчанию.
</p>

<p align="center">
  <a href="https://github.com/Dy6HiLa/pnMarket/releases/latest">
    <img src="https://img.shields.io/badge/Скачать-v1.0.0-429F91?style=for-the-badge&labelColor=17241F" alt="Скачать pnMarket 1.0.0">
  </a>
  <a href="https://github.com/Dy6HiLa/pnMarket/releases">
    <img src="https://img.shields.io/badge/Releases-GitHub-5A8DEE?style=for-the-badge&labelColor=17241F" alt="GitHub Releases">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-17241F?style=for-the-badge" alt="MIT">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Paper-1.21.8%2B-5A8DEE?style=for-the-badge" alt="Paper 1.21.8+">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge" alt="Java 21">
  <img src="https://img.shields.io/badge/Storage-SQLite%20%7C%20MySQL%20%7C%20MongoDB-429F91?style=for-the-badge" alt="SQLite, MySQL и MongoDB">
</p>

<p align="center">
  <a href="#features">Возможности</a>
  · <a href="#installation">Установка</a>
  · <a href="#commands">Команды</a>
  · <a href="#configuration">Настройка</a>
  · <a href="#storage">Базы данных</a>
  · <a href="#support">Поддержка</a>
</p>

---

<a id="features"></a>
## Возможности

| Возможность | Как работает |
| --- | --- |
| Графический аукцион | Открывается командой `/ah`, поддерживает страницы, сортировку и категории. |
| Продажа предметов | `/ah sell <цена>` выставляет весь стак из основной руки. Цена указывается за весь лот. |
| Частичная покупка | Покупатель выбирает количество предметов в лоте, цена пересчитывается автоматически. |
| Мгновенное GUI-обновление | Новый лот, покупка, возврат и забор предмета обновляют все открытые меню в следующем тике сервера. |
| Поиск и товары продавца | Поиск по названию и Material ID; просмотр всех активных лотов игрока. |
| Собственные категории | Любое число категорий с правилами по Material ID, части ID, названию и съедобности. |
| Защита лотов | Нельзя купить свой лот. Операции покупки резервируют предметы в БД, поэтому один предмет не продаётся дважды. |
| Возврат и срок лота | Лоты истекают через 24 часа; свои активные лоты можно снять, а истёкшие забрать в «Мои товары». |
| Лимиты и цены | Лимиты лотов зависят от основной Vault-группы. Минимальная и максимальная цена настраиваются. |
| Три хранилища | SQLite без отдельного сервера, а также MySQL и MongoDB. |
| Обновления | Проверка GitHub Releases запускается автоматически раз в шесть часов. |

## Как работает рынок

1. Продавец держит предмет в основной руке и выполняет `/ah sell 250`.
2. Плагин сохраняет предмет и цену в выбранную БД, забирает предмет из руки и сразу показывает лот всем игрокам с открытым аукционом.
3. Покупатель открывает лот, выбирает количество и подтверждает покупку.
4. Деньги списываются через Vault, продавец получает оплату, а предмет отправляется покупателю. Если инвентарь заполнен, предмет выпадает рядом с ним.
5. После продажи все открытые меню синхронно отображают остаток лота либо удаляют проданный лот.

<a id="installation"></a>
## Установка

1. Скачайте [`pnMarket-1.0.0.jar`](https://github.com/Dy6HiLa/pnMarket/releases/latest) из GitHub Release.
2. Поместите файл в папку сервера `plugins/`.
3. Установите [Vault](https://www.spigotmc.org/resources/vault.34315/) и совместимый плагин экономики.
4. Запустите сервер. По умолчанию будет создана SQLite-база `plugins/pnMarket/market.db`.
5. При необходимости измените `plugins/pnMarket/config.yml` и выполните полный перезапуск сервера.

**Требования:** Paper 1.21.8 или новее, Java 21, Vault и плагин экономики. Для MySQL и MongoDB нужен доступный внешний сервер БД.

Не используйте PlugMan и аналоги для первой установки или смены типа БД. Выполняйте полный рестарт сервера.

<a id="commands"></a>
## Команды и права

| Команда | Описание |
| --- | --- |
| `/ah` | Открыть аукцион. |
| `/ah sell <цена>` | Выставить предмет из основной руки. |
| `/ah search <название>` | Открыть результаты поиска. |
| `/ah show <игрок>` | Открыть лоты игрока. |
| `/ah reload` | Перезагрузить `config.yml`, `messages.yml`, категории и проверку обновлений. |

| Право | Назначение |
| --- | --- |
| `pnmarket.admin` | Использование `/ah reload` и уведомления о новых версиях. |

<a id="configuration"></a>
## Настройка

### Лимиты лотов

`limits.default` определяет, сколько **активных** лотов может выставить обычный игрок. Если Vault отдаёт игроку основную группу, одноимённый ключ переопределяет этот лимит.

```yml
limits:
  default: 3 # Все игроки без отдельного правила.
  vip: 10    # Основная Vault-группа vip.
  admin: 50  # Основная Vault-группа admin.
```

### Ограничения цены

Цена относится ко всему лоту, а не к одной единице предмета. `maximum: 0` отключает верхний предел.

```yml
listing-price:
  minimum: 10
  maximum: 1000000
```

### Собственные категории

Категории отображаются в порядке из `config.yml`. Первый подходящий фильтр определяет категорию товара. `all` встроена всегда и показывает все лоты.

| Правило | Описание |
| --- | --- |
| `materials` | Точные значения Bukkit Material, например `STONE` или `DIAMOND_SWORD`. |
| `material-contains` | Фрагменты Material ID, например `_PICKAXE`. |
| `name-contains` | Фрагменты пользовательского названия предмета без цветовых кодов. |
| `edible` | Все съедобные предметы. |

```yml
categories:
  building:
    name: "Строительство"
    materials: ["STONE", "OAK_PLANKS", "GLASS"]
  tools:
    name: "Инструменты"
    material-contains: ["_PICKAXE", "_SHOVEL", "_HOE"]
  rare-items:
    name: "Редкие предметы"
    name-contains: ["легендар", "rare"]
  food:
    name: "Еда"
    edible: true
```

После изменения категорий выполните `/ah reload` или перезапустите сервер.

<a id="storage"></a>
## Базы данных

### SQLite

Работает сразу после установки и не требует отдельного сервера.

```yml
storage:
  type: sqlite
  sqlite:
    file: "market.db"
```

### MySQL

```yml
storage:
  type: mysql
  mysql:
    host: "localhost"
    port: 3306
    database: "minecraft"
    username: "pnmarket"
    password: "change-me"
```

Можно использовать полный JDBC-адрес в `storage.mysql.url`.

### MongoDB

```yml
storage:
  type: mongodb
  mongo:
    uri: "mongodb://localhost:27017"
    database: "minecraft"
    collection: "auction"
```

Переменная окружения `PNMARKET_MONGO_URI` имеет приоритет над `storage.mongo.uri`. Не храните production-пароли в публичном репозитории.

## Файлы плагина

```text
plugins/pnMarket/
├── config.yml       # БД, лимиты, цены, категории
├── messages.yml     # Сообщения и названия GUI
├── lang/ru_ru.json  # Русские названия предметов
└── market.db        # Создаётся только при SQLite
```

## Сборка из исходников

```powershell
./gradlew.bat clean test releaseJar
```

Готовый файл появится по пути `release/pnMarket-1.0.0.jar`. В `build/libs/` также создаётся обычный JAR для разработки.

<a id="support"></a>
## Поддержка

Поддержка и предложения по развитию: [Discord pnCases](https://discord.gg/rRbzq6cnc6).

Ошибки и предложения также можно оставить в [GitHub Issues](https://github.com/Dy6HiLa/pnMarket/issues).

## Лицензия

Распространяется по лицензии [MIT](LICENSE).
