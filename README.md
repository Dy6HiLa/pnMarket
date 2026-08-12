# pnMarket

<p align="center">
  Современный GUI-аукцион для Minecraft-серверов Paper.<br>
  Несколько валют, уведомления о товарах, комиссии, наборы и полностью настраиваемый интерфейс.
</p>

<p align="center">
  <a href="https://github.com/Dy6HiLa/pnMarket/releases/latest"><img src="https://img.shields.io/badge/Скачать-v1.0.3-68FB3C?style=for-the-badge&labelColor=17241F" alt="Скачать pnMarket 1.0.3"></a>
  <a href="https://discord.gg/SZxPP9surw"><img src="https://img.shields.io/badge/Discord-Поддержка-5865F2?style=for-the-badge&labelColor=17241F" alt="Discord"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-17241F?style=for-the-badge" alt="MIT"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Paper-1.16.5--1.21.x-5A8DEE?style=flat-square" alt="Paper 1.16.5–1.21.x">
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square" alt="Java 17+">
  <img src="https://img.shields.io/badge/Storage-SQLite%20%7C%20MySQL%20%7C%20MongoDB-429F91?style=flat-square" alt="SQLite, MySQL и MongoDB">
  <img src="https://img.shields.io/badge/Economy-Vault%20%7C%20PlayerPoints%20%7C%20ExcellentEconomy-D66CFF?style=flat-square" alt="Поддерживаемые экономики">
</p>

<p align="center">
  <a href="#возможности">Возможности</a> ·
  <a href="#скриншоты">Скриншоты</a> ·
  <a href="#команды">Команды</a> ·
  <a href="#настройка">Настройка</a> ·
  <a href="#установка">Установка</a>
</p>

---

## Возможности

| Раздел | Возможности |
| --- | --- |
| Аукцион | Страницы, категории, сортировка, поиск, просмотр продавца и частичная покупка стака. |
| Продажа | Обычные лоты, наборы предметов, автоматическая оценка `/ah sell auto` и перевыставление истёкших товаров. |
| Уведомления | GUI-каталог всех предметов, уведомления о новых лотах и снижении цены, доставка сообщений после входа на сервер. |
| Зачарования | Один профиль может требовать несколько совместимых чар нужного уровня на одном предмете. |
| Зелья | Обычные, усиленные, длительные, взрывные и туманные зелья, а также стрелы со всеми эффектами. |
| Валюты | Независимая настройка `/ah` и `/dah` через Vault, PlayerPoints или ExcellentEconomy. |
| Цены | Полный и сокращённый формат чисел: `K`, `M`, `B`, `T`, `Q`. |
| Комиссии | Отдельный процент за выставление и продажу, включая скидки для групп привилегий. |
| Интерфейс | Плавные анимации, русская локализация, настраиваемые материалы, текстуры, lore и расположение слотов. |
| Хранилище | SQLite без отдельного сервера, MySQL и MongoDB. |

## Скриншоты

<table>
  <tr>
    <td align="center" width="50%">
      <strong>Каталог предметов</strong><br><br>
      <img src="docs/screenshots/notification-catalog.png" alt="Каталог предметов pnMarket" width="100%">
    </td>
    <td align="center" width="50%">
      <strong>Выбор категории</strong><br><br>
      <img src="docs/screenshots/notification-categories.png" alt="Категории уведомлений pnMarket" width="100%">
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <strong>Управление уведомлениями</strong><br><br>
      <img src="docs/screenshots/notification-menu-lore.png" alt="Описание меню уведомлений pnMarket" height="210">
    </td>
    <td align="center" width="50%">
      <strong>Описание категории</strong><br><br>
      <img src="docs/screenshots/notification-category-lore.png" alt="Описание категории pnMarket" height="210">
    </td>
  </tr>
</table>

## Каталог уведомлений

- Откройте каталог командой `/ah notify` или кнопкой «Избранное и уведомления».
- Нажмите ЛКМ по предмету, чтобы получать сообщения обо всех новых лотах этого типа.
- Нажмите ПКМ, чтобы выбрать обязательные зачарования и минимальные уровни.
- Все выбранные чары должны одновременно находиться на одном продаваемом предмете.
- Несовместимые зачарования нельзя объединить в один профиль.
- Активные профили можно просмотреть и удалить в отдельном меню.

## Команды

| Команда | Описание |
| --- | --- |
| `/ah` | Открыть обычный аукцион. |
| `/dah` | Открыть донат-аукцион. |
| `/ah sell <цена>` | Выставить предмет из основной руки. |
| `/ah sell auto` | Рассчитать цену по похожим активным лотам. |
| `/ah kit <цена> [название]` | Создать лот-набор из предметов основного инвентаря. |
| `/ah notify` | Открыть каталог уведомлений. |
| `/ah search [название]` | Найти товар; без названия используется предмет в руке. |
| `/ah show <игрок>` | Посмотреть активные товары игрока. |
| `/pnmarket` | Показать информацию о плагине и обновлении. |
| `/pnmarket reload` | Перезагрузить конфигурацию. |
| `/pnmarket machine` | Открыть редактор разметки GUI. |

Для донат-аукциона команды `sell`, `sell auto`, `kit`, `notify`, `search` и `show` аналогично доступны через `/dah`.

| Право | Назначение |
| --- | --- |
| `pnmarket.admin` | Панель администратора, перезагрузка, Machine и уведомления об обновлении. |
| `pnmarket.sell.auto` | Автоматическая оценка через `sell auto`. |

## Настройка

### Валюты

Обычный и донат-аукцион включаются и настраиваются независимо.

```yml
currency:
  default:
    enabled: true
    type: vault # vault, playerpoints или excellent
    format: "&a{amount}⛃"
    excellent-id: coins
  donate:
    enabled: true
    type: playerpoints
    format: "&d{amount} PP"
    excellent-id: points
```

### Формат и ограничения цены

```yml
price:
  mode: short # short: 1M, full: 1000000
  short:
    decimals: 1
    thousand: K
    million: M
    billion: B
    trillion: T
    quadrillion: Q
  limits:
    default: { min: 1, max: "1M" }
    donate: { min: 1, max: "1M" }
```

### Сроки и лимиты лотов

```yml
sell:
  limits:
    default: 3
    vip: 10
  expiration:
    default: 24h
    groups:
      default: 24h
      vip: 48h
      admin: 7d
```

### Комиссии

```yml
commission:
  enabled: true
  groups:
    default: { listing: 2.0, sale: 5.0 }
    vip: { listing: 1.0, sale: 3.0 }
    premium: { listing: 0.0, sale: 1.0 }
```

### Файлы

```text
plugins/pnMarket/
├── config.yml
├── gui.yml
├── messages.yml
├── favorites.yml
├── pending-notifications.yml
├── lang/ru_ru.json
└── market.db
```

## Установка

1. Скачайте [`pnMarket-1.0.3.jar`](https://github.com/Dy6HiLa/pnMarket/releases/latest).
2. Поместите JAR в папку `plugins/`.
3. Установите выбранный плагин экономики и его зависимости: Vault, PlayerPoints или ExcellentEconomy.
4. Запустите сервер и настройте `config.yml`, `gui.yml` и `messages.yml`.
5. Выполните полный перезапуск сервера. Не используйте PlugMan для первой установки или смены хранилища.

Требования: Paper 1.16.5–1.21.x и Java 17 или новее.

## Сборка

```powershell
./gradlew.bat --no-daemon shadowJar
```

Готовый файл: `build/libs/pnMarket-1.0.3.jar`.

## Поддержка

- [Discord](https://discord.gg/SZxPP9surw)
- [GitHub Issues](https://github.com/Dy6HiLa/pnMarket/issues)

## Лицензия

[MIT](LICENSE)
