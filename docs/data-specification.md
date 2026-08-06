# HeroCraft24 — Data Specification

> **Единственный источник истины для всех игровых данных приложения**
>
> Версия документа: 1.0 | 2026-07-28 | Статус: **Проектирование**

---

## Содержание

1. [Общие принципы](#1-общие-принципы)
2. [Базовая структура объектов](#2-базовая-структура-объектов)
3. [Общие типы данных](#3-общие-типы-данных)
4. [Системные файлы](#4-системные-файлы)
   - [4.1. manifest.json](#41-manifestjson)
   - [4.2. pack.json](#42-packjson)
5. [Игровые объекты](#5-игровые-объекты)
   - [5.1. spell.json](#51-spelljson)
   - [5.2. item.json](#52-itemjson)
   - [5.3. class.json](#53-classjson)
   - [5.4. subclass.json](#54-subclassjson)
   - [5.5. species.json](#55-speciesjson)
   - [5.6. background.json](#56-backgroundjson)
   - [5.7. feat.json](#57-featjson)
   - [5.8. condition.json](#58-conditionjson)
   - [5.9. monster.json](#59-monsterjson)
   - [5.10. mechanic.json](#510-mechanicjson)
   - [5.11. glossary.json](#511-glossaryjson)
6. [Пользовательские данные](#6-пользовательские-данные)
   - [6.1. character.json](#61-characterjson)
   - [6.2. favorite.json](#62-favoritejson)
   - [6.3. settings.json](#63-settingsjson)
7. [Форматы обмена](#7-форматы-обмена)
   - [7.1. Импорт/экспорт пакетов](#71-импортэкспорт-пакетов)
   - [7.2. Импорт/экспорт персонажей](#72-импортэкспорт-персонажей)
   - [7.3. Экспорт настроек](#73-экспорт-настроек)
8. [Правила валидации](#8-правила-валидации)
9. [Версионирование и обратная совместимость](#9-версионирование-и-обратная-совместимость)
10. [Система перекрёстных ссылок](#10-система-перекрёстных-ссылок)
11. [Индексация для поиска и фильтрации](#11-индексация-для-поиска-и-фильтрации)
12. [Приложение: Все enum-значения](#12-приложение-все-enum-значения)

---

## 1. Общие принципы

### 1.1. Философия данных

- Каждый игровой объект — **ровно один JSON-файл**.
- Запрещены объединённые файлы (списки заклинаний в одном файле и т.п.).
- Все связи между объектами — **только через строковые ID** с namespace.
- Текстовые названия (display names) никогда не используются для связи объектов.
- Любой файл должен быть **самодостаточным** — его можно скопировать в другой пакет, и он останется валидным.

### 1.2. Пространства имён (Namespace)

Каждый объект идентифицируется полным ID формата:

```
packId:objectId
```

Примеры:
- `phb2024:fireball`
- `dmg2024:bag_of_holding`
- `homebrew:shadow_knight`

Локальный ID (`objectId`) уникален внутри пакета. Полный ID — глобально уникален.

### 1.3. Структура каталогов

```
packs/
├── phb2024/                      # ID пакета
│   ├── manifest.json             # Индекс всех объектов пакета
│   ├── spells/                   # Заклинания
│   │   ├── fireball.json
│   │   └── ...
│   ├── items/                    # Снаряжение
│   │   ├── longsword.json
│   │   └── ...
│   ├── classes/                  # Классы
│   │   ├── wizard.json
│   │   └── ...
│   ├── species/                  # Виды
│   │   ├── elf_high.json
│   │   └── ...
│   ├── backgrounds/              # Происхождения
│   │   ├── soldier.json
│   │   └── ...
│   ├── feats/                    # Черты
│   │   ├── great_weapon_master.json
│   │   └── ...
│   ├── monsters/                 # Бестиарий
│   │   ├── goblin.json
│   │   └── ...
│   ├── conditions/               # Состояния
│   │   ├── blinded.json
│   │   └── ...
│   ├── mechanics/                # Игровые механики
│   │   ├── combat_actions.json
│   │   └── ...
│   └── glossary/                 # Глоссарий
│       ├── armor_class.json
│       └── ...
├── dmg2024/
├── mm2024/
└── homebrew/                     # Пользовательские пакеты
```

### 1.4. Источники данных и приоритет

| Приоритет | Источник | Путь |
|---|---|---|
| 1 (высший) | Внешние пакеты | `Android/data/com.herocraft24/files/packs/` |
| 2 (базовый) | Встроенные пакеты | `assets/packs/` |

При конфликте ID — внешний пакет переопределяет встроенный. Это позволяет пользователю патчить контент без модификации APK.

### 1.5. Кодировка и формат

- Кодировка: **UTF-8 без BOM**
- Формат: **JSON** (RFC 8259)
- Отступы: 2 пробела
- Конец строки: LF (`\n`)
- Имена файлов: **snake_case** (только `[a-z0-9_]`), расширение `.json`

---

## 2. Базовая структура объектов

### 2.1. Базовые поля (присутствуют во всех игровых объектах)

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID объекта внутри пакета (snake_case, `[a-z0-9_]+`) |
| `type` | `string` | ✅ | Тип объекта (см. раздел 2.2) |
| `format_version` | `int` | ✅ | Версия формата данных (начинается с 1) |
| `name` | `LocalizedString` | ✅ | Название объекта (как минимум `en`) |
| `short_description` | `LocalizedString` | ❌ | Краткое описание для карточек списка (1-2 предложения) |
| `description` | `LocalizedString` | ✅ | Полное описание |
| `source` | `SourceInfo` | ✅ | Информация об источнике |
| `tags` | `string[]` | ❌ | Теги для поиска и группировки |
| `references` | `Reference[]` | ❌ | Явные перекрёстные ссылки на другие объекты |
| `metadata` | `object` | ❌ | Расширяемые пользовательские метаданные |
| `image` | `ImageInfo` | ❌ | Информация об изображении |

### 2.2. Значения поля `type`

| Значение | Назначение |
|---|---|
| `"spell"` | Заклинание |
| `"item"` | Предмет / снаряжение |
| `"class"` | Класс персонажа |
| `"subclass"` | Подкласс |
| `"species"` | Вид / раса |
| `"background"` | Происхождение |
| `"feat"` | Черта |
| `"condition"` | Состояние |
| `"monster"` | Монстр / NPC |
| `"mechanic"` | Игровая механика / правило |
| `"glossary"` | Термин глоссария |

### 2.3. Поле `SourceInfo`

```json
{
  "book": { "en": "Player's Handbook (2024)", "ru": "Книга игрока (2024)" },
  "abbreviation": "PHB 2024",
  "page": 242,
  "url": "https://dnd.wizards.com/products/phb-2024",
  "release_date": "2024-09-17"
}
```

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `book` | `LocalizedString` | ✅ | Название книги-источника |
| `abbreviation` | `string` | ✅ | Сокращение книги (напр. "PHB 2024") |
| `page` | `int` | ❌ | Номер страницы |
| `url` | `string` | ❌ | Ссылка на официальный источник |
| `release_date` | `string` | ❌ | Дата выхода (ISO 8601: `YYYY-MM-DD`) |

### 2.4. Поле `Reference`

```json
{
  "type": "spell",
  "id": "phb2024:fireball",
  "relationship": "requires",
  "context": "When casting this spell..."
}
```

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `type` | `string` | ✅ | Тип целевого объекта (значения из 2.2) |
| `id` | `string` | ✅ | Полный ID целевого объекта |
| `relationship` | `string` | ❌ | Тип связи: `"requires"`, `"related"`, `"variant"`, `"enhances"`, `"counters"` |
| `context` | `string` | ❌ | Пояснение контекста связи |

### 2.5. Поле `ImageInfo`

```json
{
  "path": "images/fireball.webp",
  "artist": "John Doe",
  "license": "WotC",
  "caption": { "en": "A bright streak flashes..." }
}
```

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `path` | `string` | ✅ | Относительный путь к файлу изображения внутри пакета |
| `artist` | `string` | ❌ | Имя художника |
| `license` | `string` | ❌ | Тип лицензии |
| `caption` | `LocalizedString` | ❌ | Подпись к изображению |

---

## 3. Общие типы данных

### 3.1. LocalizedString

Локализованная строка. Минимально — английский (`en`). Дополнительные языки — опционально.

```json
{
  "en": "Fireball",
  "ru": "Огненный шар",
  "de": "Feuerball"
}
```

**Правила:**
- Ключ `en` обязателен всегда.
- Ключи языков — ISO 639-1 (двухбуквенные: `en`, `ru`, `de`, `fr`, `es`, `it`, `pt`, `ja`, `ko`, `zh`).
- Если перевод отсутствует — поле не включается в объект.
- Приложение всегда показывает значение для текущего языка; если перевода нет — показывает `en`.

### 3.2. Dice

Бросок кубика. Используется для урона, хитов, лечения.

```json
{
  "count": 8,
  "sides": 6,
  "bonus": 0
}
```

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `count` | `int` | ✅ | Количество кубиков (≥ 1) |
| `sides` | `int` | ✅ | Количество граней (2, 4, 6, 8, 10, 12, 20, 100) |
| `bonus` | `int` | ❌ | Модификатор (по умолчанию 0) |

**Строковое представление:** `"8d6"`, `"2d8+3"`, `"1d20"`

### 3.3. DiceString

Строковое представление броска (для сложных случаев, не укладывающихся в `Dice`):

```json
"2d8 + 1d6 + 3"
```

Используется там, где формула не сводится к простому `N × dS + B`.

### 3.4. Cost

Стоимость в игровой валюте.

```json
{
  "amount": 50.0,
  "unit": "gp"
}
```

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `amount` | `float` | ✅ | Количество (≥ 0) |
| `unit` | `string` | ✅ | Валюта: `"cp"`, `"sp"`, `"gp"`, `"pp"` |

**Строковое представление:** `"50 gp"`, `"15 sp"`, `"0.5 gp"`

### 3.5. Weight

Вес в фунтах.

```json
{
  "amount": 3.0,
  "unit": "lb"
}
```

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `amount` | `float` | ✅ | Вес (≥ 0) |
| `unit` | `string` | ✅ | Всегда `"lb"` (фунты) |

---

## 4. Системные файлы

### 4.1. manifest.json

**Назначение:** Индекс всех объектов пакета. Загружается при старте, обеспечивает быстрый доступ к спискам и поиску без парсинга сотен JSON-файлов.

**Место хранения:** `packs/{packId}/manifest.json`

**Обязательные поля:**

| Поле | Тип | Описание |
|---|---|---|
| `pack_id` | `string` | ID пакета (совпадает с именем директории) |
| `name` | `LocalizedString` | Название пакета |
| `version` | `string` | Семантическая версия пакета (`"1.0.0"`) |
| `format_version` | `int` | Версия формата данных |
| `objects` | `object` | Индексы объектов по типам (см. ниже) |

**Необязательные поля:**

| Поле | Тип | Описание |
|---|---|---|
| `description` | `LocalizedString` | Описание пакета |
| `authors` | `string[]` | Авторы контента |
| `license` | `string` | Тип лицензии (`"official"`, `"homebrew"`, `"ogl"`, `"cc-by"`, `"custom"`) |
| `website` | `string` | Сайт пакета |
| `dependencies` | `string[]` | ID пакетов, от которых зависит этот пакет |
| `language` | `string` | Основной язык пакета (ISO 639-1) |
| `locales` | `string[]` | Доступные локализации |
| `rules_version` | `string` | Версия правил (`"dnd2024"`, `"dnd5e"`, `"custom"`) |
| `created_at` | `string` | Дата создания (ISO 8601) |
| `updated_at` | `string` | Дата обновления (ISO 8601) |
| `total_objects` | `int` | Общее количество объектов (вычисляется автоматически) |

**Структура `objects`:**

```json
{
  "objects": {
    "spells": [
      {
        "id": "fireball",
        "name": { "en": "Fireball", "ru": "Огненный шар" },
        "level": 3,
        "school": "evocation",
        "ritual": false,
        "concentration": false,
        "classes": ["wizard", "sorcerer"],
        "tags": ["fire", "damage", "area", "evocation"]
      }
    ],
    "items": [
      {
        "id": "longsword",
        "name": { "en": "Longsword", "ru": "Длинный меч" },
        "category": "weapon",
        "rarity": "common",
        "tags": ["weapon", "martial", "melee", "versatile", "slashing"]
      }
    ],
    "classes": [
      {
        "id": "wizard",
        "name": { "en": "Wizard", "ru": "Волшебник" },
        "hit_die": 6,
        "primary_ability": "intelligence",
        "tags": ["arcane", "spellcaster", "full-caster"]
      }
    ],
    "species": [
      {
        "id": "elf_high",
        "name": { "en": "High Elf", "ru": "Высший эльф" },
        "type": "humanoid",
        "size": "medium",
        "speed": 30,
        "tags": ["elf", "fey", "magic"]
      }
    ],
    "backgrounds": [
      {
        "id": "soldier",
        "name": { "en": "Soldier", "ru": "Солдат" },
        "tags": ["martial", "military"]
      }
    ],
    "feats": [
      {
        "id": "great_weapon_master",
        "name": { "en": "Great Weapon Master", "ru": "Мастер большого оружия" },
        "tags": ["combat", "damage", "heavy-weapon"]
      }
    ],
    "monsters": [
      {
        "id": "goblin",
        "name": { "en": "Goblin", "ru": "Гоблин" },
        "size": "small",
        "type": "humanoid",
        "challenge_rating": 0.25,
        "tags": ["goblinoid", "low-level", "humanoid"]
      }
    ],
    "conditions": [
      {
        "id": "blinded",
        "name": { "en": "Blinded", "ru": "Ослепление" },
        "tags": ["senses", "disadvantage"]
      }
    ],
    "mechanics": [
      {
        "id": "combat_actions",
        "name": { "en": "Actions in Combat", "ru": "Действия в бою" },
        "category": "combat",
        "tags": ["combat", "actions", "core"]
      }
    ],
    "glossary": [
      {
        "id": "armor_class",
        "name": { "en": "Armor Class", "ru": "Класс брони" },
        "tags": ["combat", "defense", "core"]
      }
    ]
  }
}
```

**Поля в summary-объектах (внутри `objects.{type}[]`):**

Каждый тип имеет свой набор полей в summary (см. раздел 5 для каждого типа). Общие правила:
- `id` и `name` — всегда присутствуют.
- `tags` — всегда присутствуют (для поиска).
- Специфичные для фильтрации поля (например, `level` и `school` для spells) — присутствуют.
- Все поля summary также присутствуют в полном JSON-файле объекта.

**Полный пример:** см. [Приложение A](#appendix-a-пример-manifestjson).

---

### 4.2. pack.json

**Назначение:** Метаданные пакета. Используется при импорте/экспорте для идентификации пакета без разбора manifest.

**Место хранения:** `packs/{packId}/pack.json` (рядом с manifest.json)

```json
{
  "pack_id": "phb2024",
  "name": {
    "en": "Player's Handbook 2024",
    "ru": "Книга игрока 2024"
  },
  "version": "1.0.0",
  "format_version": 1,
  "rules_version": "dnd2024",
  "authors": ["Wizards of the Coast"],
  "license": "official",
  "website": "https://dnd.wizards.com",
  "description": {
    "en": "Core rules for Dungeons & Dragons 2024",
    "ru": "Основные правила Dungeons & Dragons 2024"
  },
  "dependencies": [],
  "language": "en",
  "locales": ["en", "ru"],
  "created_at": "2024-09-17T00:00:00Z",
  "updated_at": "2024-09-17T00:00:00Z",
  "total_objects": 350
}
```

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `pack_id` | `string` | ✅ | Уникальный ID пакета |
| `name` | `LocalizedString` | ✅ | Название |
| `version` | `string` | ✅ | Семантическая версия (`"1.0.0"`) |
| `format_version` | `int` | ✅ | Версия формата данных |
| `rules_version` | `string` | ✅ | `"dnd2024"`, `"dnd5e"`, `"custom"` |
| `authors` | `string[]` | ❌ | Авторы |
| `license` | `string` | ❌ | `"official"`, `"homebrew"`, `"ogl"`, `"cc-by"`, `"custom"` |
| `website` | `string` | ❌ | URL |
| `description` | `LocalizedString` | ❌ | Описание |
| `dependencies` | `string[]` | ❌ | ID пакетов-зависимостей |
| `language` | `string` | ❌ | Основной язык (по умолчанию `"en"`) |
| `locales` | `string[]` | ❌ | Доступные локализации |
| `created_at` | `string` | ❌ | ISO 8601 |
| `updated_at` | `string` | ❌ | ISO 8601 |
| `total_objects` | `int` | ❌ | Вычисляется при экспорте |

---

## 5. Игровые объекты

### 5.1. spell.json

**Назначение:** Заклинание D&D 2024.

**Место хранения:** `packs/{packId}/spells/{id}.json`

**Структура:**

| Поле | Тип | Обязательное | Описание | Поиск | Фильтр | Сортировка | Карточка | Детали |
|---|---|---|---|---|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID | ✅ | — | — | — | — |
| `type` | `string` | ✅ | Всегда `"spell"` | — | — | — | — | — |
| `format_version` | `int` | ✅ | Версия формата | — | — | — | — | — |
| `name` | `LocalizedString` | ✅ | Название | ✅ | — | ✅ | ✅ | ✅ |
| `short_description` | `LocalizedString` | ❌ | Краткое описание | ✅ | — | — | ✅ | — |
| `description` | `LocalizedString` | ✅ | Полное описание | ✅ | — | — | — | ✅ |
| `source` | `SourceInfo` | ✅ | Источник | — | ✅ | — | — | ✅ |
| `tags` | `string[]` | ❌ | Теги | ✅ | ✅ | — | — | — |
| `references` | `Reference[]` | ❌ | Ссылки | — | — | — | — | ✅ |
| `metadata` | `object` | ❌ | Метаданные | — | — | — | — | — |
| `image` | `ImageInfo` | ❌ | Изображение | — | — | — | — | ✅ |
| `level` | `int` | ✅ | Уровень (0 = заговор) | — | ✅ | ✅ | ✅ | ✅ |
| `school` | `string` | ✅ | Школа магии | — | ✅ | ✅ | ✅ | ✅ |
| `casting_time` | `string` | ✅ | Время накладывания | — | — | — | — | ✅ |
| `range` | `SpellRange` | ✅ | Дистанция | — | — | — | ✅ | ✅ |
| `components` | `string[]` | ✅ | Компоненты: `["V", "S", "M"]` | — | ✅ | — | ✅ | ✅ |
| `material` | `string` | ❌ | Описание мат. компонента | — | — | — | — | ✅ |
| `duration` | `string` | ✅ | Длительность | — | — | — | ✅ | ✅ |
| `concentration` | `bool` | ✅ | Требует концентрации | — | ✅ | — | ✅ | ✅ |
| `ritual` | `bool` | ✅ | Ритуальное | — | ✅ | — | ✅ | ✅ |
| `saving_throw` | `string` | ❌ | Характеристика спасброска | — | ✅ | — | — | ✅ |
| `attack_type` | `string` | ❌ | Тип атаки: `"melee"`, `"ranged"` | — | ✅ | — | — | ✅ |
| `damage` | `SpellDamage` | ❌ | Урон | — | ✅ | — | — | ✅ |
| `area_of_effect` | `AreaOfEffect` | ❌ | Область воздействия | — | ✅ | — | — | ✅ |
| `higher_levels` | `LocalizedString` | ❌ | Эффект на высоких уровнях | — | — | — | — | ✅ |
| `classes` | `string[]` | ✅ | ID классов (массив ссылок) | — | ✅ | — | ✅ | ✅ |
| `subclasses` | `string[]` | ❌ | ID подклассов (массив ссылок) | — | — | — | — | ✅ |
| `source_class` | `string` | ❌ | Ссылка на класс-источник | — | — | — | — | ✅ |

**SpellRange:**

```json
{
  "type": "range",
  "distance": 150,
  "text": "150 feet"
}
```

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `type` | `string` | ✅ | `"self"`, `"touch"`, `"sight"`, `"unlimited"`, `"range"`, `"special"` |
| `distance` | `int` | ❌ | Дистанция в футах (только для `"range"`) |
| `text` | `string` | ❌ | Текстовое описание для специальных случаев |

**SpellDamage:**

```json
{
  "damage_type": "fire",
  "damage_at_slot_level": {
    "3": "8d6",
    "4": "9d6",
    "5": "10d6"
  },
  "damage_at_character_level": null,
  "save": "half"
}
```

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `damage_type` | `string` | ✅ | Тип урона (см. §12) |
| `damage_at_slot_level` | `object` | ❌ | Урон по уровням ячейки (`{"3": "8d6", ...}`) |
| `damage_at_character_level` | `object` | ❌ | Урон по уровням персонажа (для заговоров: `{"5": "2d8", "11": "3d8", ...}`) |
| `save` | `string` | ❌ | `"half"` — половинный урон при успешном спасброске; `null` — без спасброска |

**AreaOfEffect:**

```json
{
  "type": "sphere",
  "size": 20
}
```

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `type` | `string` | ✅ | `"cube"`, `"cone"`, `"cylinder"`, `"line"`, `"sphere"`, `"emanation"` |
| `size` | `int` | ✅ | Размер в футах |

**Полный пример — fireball.json:**

```json
{
  "id": "fireball",
  "type": "spell",
  "format_version": 1,
  "name": { "en": "Fireball", "ru": "Огненный шар" },
  "short_description": {
    "en": "A bright streak flashes to a point, then explodes in flame.",
    "ru": "Яркая вспышка устремляется в точку и взрывается пламенем."
  },
  "description": {
    "en": "A bright streak flashes from your pointing finger to a point you choose within range and then blossoms with a low roar into an explosion of flame. Each creature in a 20-foot-radius Sphere centered on that point must make a Dexterity saving throw. A target takes 8d6 Fire damage on a failed save, or half as much damage on a successful one.",
    "ru": "Яркая вспышка устремляется от вашего указывающего пальца к выбранной точке в пределах дистанции и расцветает с низким рёвом во взрыв пламени. Каждое существо в 20-футовой сфере с центром в этой точке должно совершить спасбросок Ловкости. Цель получает 8d6 урона огнём при провале или половину этого урона при успехе."
  },
  "source": {
    "book": { "en": "Player's Handbook (2024)", "ru": "Книга игрока (2024)" },
    "abbreviation": "PHB 2024",
    "page": 242
  },
  "tags": ["fire", "damage", "area", "evocation", "iconic"],
  "references": [
    { "type": "mechanic", "id": "phb2024:saving_throws", "relationship": "related" },
    { "type": "mechanic", "id": "phb2024:spellcasting", "relationship": "related" },
    { "type": "condition", "id": "phb2024:invisible", "relationship": "counters", "context": "Fireball's flash can be seen through invisibility" }
  ],
  "level": 3,
  "school": "evocation",
  "casting_time": "1 action",
  "range": {
    "type": "range",
    "distance": 150,
    "text": "150 feet"
  },
  "components": ["V", "S", "M"],
  "material": "a tiny ball of bat guano and sulfur",
  "duration": "Instantaneous",
  "concentration": false,
  "ritual": false,
  "saving_throw": "dexterity",
  "attack_type": null,
  "damage": {
    "damage_type": "fire",
    "damage_at_slot_level": {
      "3": "8d6",
      "4": "9d6",
      "5": "10d6",
      "6": "11d6",
      "7": "12d6",
      "8": "13d6",
      "9": "14d6"
    },
    "save": "half"
  },
  "area_of_effect": {
    "type": "sphere",
    "size": 20
  },
  "higher_levels": {
    "en": "When you cast this spell using a spell slot of 4th level or higher, the damage increases by 1d6 for each slot level above 3rd.",
    "ru": "При использовании ячейки 4-го уровня или выше урон увеличивается на 1d6 за каждый уровень ячейки выше 3-го."
  },
  "classes": ["phb2024:wizard", "phb2024:sorcerer"],
  "subclasses": ["phb2024:light_domain", "phb2024:fiend_patron", "phb2024:genie_patron"],
  "source_class": null
}
```

**Рекомендации по расширению:**
- Добавление новых полей (например, `is_cantrip_damage_scaling`) — обратно совместимо, старые парсеры игнорируют неизвестные поля.
- Изменение типа существующего поля — **запрещено** без увеличения `format_version`.
- Новые значения enum-ов (например, новый тип школы магии) — добавлять в конец, не менять порядок.

---

### 5.2. item.json

**Назначение:** Предмет снаряжения (волшебный или обычный).

**Место хранения:** `packs/{packId}/items/{id}.json`

**Структура:**

| Поле | Тип | Обязательное | Описание | Поиск | Фильтр | Сортировка | Карточка | Детали |
|---|---|---|---|---|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID | ✅ | — | — | — | — |
| `type` | `string` | ✅ | Всегда `"item"` | — | — | — | — | — |
| `format_version` | `int` | ✅ | Версия формата | — | — | — | — | — |
| `name` | `LocalizedString` | ✅ | Название | ✅ | — | ✅ | ✅ | ✅ |
| `short_description` | `LocalizedString` | ❌ | Краткое описание | ✅ | — | — | ✅ | — |
| `description` | `LocalizedString` | ✅ | Полное описание | ✅ | — | — | — | ✅ |
| `source` | `SourceInfo` | ✅ | Источник | — | ✅ | — | — | ✅ |
| `tags` | `string[]` | ❌ | Теги | ✅ | ✅ | — | — | — |
| `references` | `Reference[]` | ❌ | Ссылки | — | — | — | — | ✅ |
| `metadata` | `object` | ❌ | Метаданные | — | — | — | — | — |
| `image` | `ImageInfo` | ❌ | Изображение | — | — | — | — | ✅ |
| `category` | `string` | ✅ | Категория (см. ниже) | — | ✅ | ✅ | ✅ | ✅ |
| `subcategory` | `string` | ❌ | Подкатегория | — | ✅ | — | ✅ | ✅ |
| `rarity` | `string` | ✅ | Редкость | — | ✅ | ✅ | ✅ | ✅ |
| `magic` | `bool` | ✅ | Волшебный предмет | — | ✅ | — | ✅ | ✅ |
| `attunement` | `bool` | ❌ | Требует настройки | — | ✅ | — | — | ✅ |
| `attunement_requirements` | `LocalizedString` | ❌ | Требования для настройки | — | — | — | — | ✅ |
| `cost` | `Cost` | ✅ | Стоимость | — | ✅ | ✅ | ✅ | ✅ |
| `weight` | `Weight` | ❌ | Вес | — | — | ✅ | ✅ | ✅ |
| `properties` | `string[]` | ❌ | Свойства (для оружия/брони) | — | ✅ | — | ✅ | ✅ |
| `damage` | `WeaponDamage` | ❌ | Урон (для оружия) | — | — | — | ✅ | ✅ |
| `armor_class` | `ArmorClass` | ❌ | КБ (для брони) | — | — | — | ✅ | ✅ |
| `range` | `WeaponRange` | ❌ | Дистанция (для дальнобойного оружия) | — | — | — | ✅ | ✅ |
| `charges` | `int` | ❌ | Количество зарядов | — | — | — | — | ✅ |
| `recharge` | `string` | ❌ | Правило перезарядки | — | — | — | — | ✅ |
| `requirements` | `LocalizedString` | ❌ | Требования к использованию | — | — | — | — | ✅ |
| `curse` | `LocalizedString` | ❌ | Проклятие | — | — | — | — | ✅ |
| `effects` | `LocalizedString[]` | ❌ | Магические эффекты | ✅ | — | — | — | ✅ |
| `quantity` | `int` | ❌ | Количество в упаковке (для расходников) | — | — | — | — | ✅ |

**Значения `category`:**

| Значение | Описание |
|---|---|
| `"weapon"` | Оружие (простое/воинское) |
| `"armor"` | Броня |
| `"shield"` | Щит |
| `"adventuring_gear"` | Снаряжение путешественника |
| `"ammunition"` | Боеприпасы |
| `"arcane_focus"` | Магический фокус |
| `"druidic_focus"` | Друидический фокус |
| `"holy_symbol"` | Священный символ |
| `"tool"` | Инструмент |
| `"artisan_tool"` | Инструмент ремесленника |
| `"gaming_set"` | Игровой набор |
| `"musical_instrument"` | Музыкальный инструмент |
| `"mount"` | Скакун |
| `"vehicle"` | Транспорт |
| `"tack_and_harness"` | Упряжь |
| `"food_and_drink"` | Еда и напитки |
| `"poison"` | Яд |
| `"potion"` | Зелье |
| `"scroll"` | Свиток |
| `"wand"` | Волшебная палочка |
| `"rod"` | Жезл |
| `"staff"` | Посох |
| `"ring"` | Кольцо |
| `"wondrous_item"` | Чудесный предмет |
| `"consumable"` | Расходник |
| `"treasure"` | Сокровище |
| `"other"` | Прочее |

**Значения `rarity`:**

| Значение | Описание |
|---|---|
| `"common"` | Обычный |
| `"uncommon"` | Необычный |
| `"rare"` | Редкий |
| `"very_rare"` | Очень редкий |
| `"legendary"` | Легендарный |
| `"artifact"` | Артефакт |
| `"varies"` | Зависит от обстоятельств |
| `"unknown"` | Неизвестно |

**Значения `properties` (для оружия/брони):**

| Значение | Описание |
|---|---|
| `"ammunition"` | Боеприпас |
| `"finesse"` | Фехтовальное |
| `"heavy"` | Тяжёлое |
| `"light"` | Лёгкое |
| `"loading"` | Заряжаемое |
| `"range"` | Дальнобойное |
| `"reach"` | Досягаемость |
| `"special"` | Особое |
| `"thrown"` | Метательное |
| `"two_handed"` | Двуручное |
| `"versatile"` | Универсальное |
| `"silvered"` | Посеребрённое |
| `"masterwork"` | Искусная работа |

**WeaponDamage:**

```json
{
  "damage_dice": "1d8",
  "damage_type": "slashing",
  "versatile_dice": "1d10"
}
```

**ArmorClass:**

```json
{
  "base": 18,
  "dex_bonus": false,
  "max_dex": null,
  "min_strength": 15,
  "stealth_disadvantage": true
}
```

**WeaponRange:**

```json
{
  "normal": 150,
  "long": 600
}
```

**Полный пример — longsword.json:**

```json
{
  "id": "longsword",
  "type": "item",
  "format_version": 1,
  "name": { "en": "Longsword", "ru": "Длинный меч" },
  "short_description": {
    "en": "A versatile martial melee weapon.",
    "ru": "Универсальное воинское оружие ближнего боя."
  },
  "description": {
    "en": "A straight-bladed, double-edged sword about 3 feet in length. It is a versatile weapon, allowing it to be used with one or two hands.",
    "ru": "Прямой обоюдоострый меч длиной около 3 футов. Это универсальное оружие, позволяющее использовать его одной или двумя руками."
  },
  "source": {
    "book": { "en": "Player's Handbook (2024)", "ru": "Книга игрока (2024)" },
    "abbreviation": "PHB 2024",
    "page": 215
  },
  "tags": ["weapon", "martial", "melee", "versatile", "slashing", "sword"],
  "category": "weapon",
  "subcategory": "martial_melee",
  "rarity": "common",
  "magic": false,
  "cost": { "amount": 15, "unit": "gp" },
  "weight": { "amount": 3, "unit": "lb" },
  "properties": ["versatile"],
  "damage": {
    "damage_dice": "1d8",
    "damage_type": "slashing",
    "versatile_dice": "1d10"
  }
}
```

**Полный пример — bag_of_holding.json (магический предмет):**

```json
{
  "id": "bag_of_holding",
  "type": "item",
  "format_version": 1,
  "name": { "en": "Bag of Holding", "ru": "Сумка хранения" },
  "description": {
    "en": "This bag has an interior space considerably larger than its outside dimensions...",
    "ru": "Эта сумка имеет внутреннее пространство значительно больше её внешних размеров..."
  },
  "source": {
    "book": { "en": "Dungeon Master's Guide (2024)", "ru": "Руководство Мастера (2024)" },
    "abbreviation": "DMG 2024",
    "page": 153
  },
  "tags": ["wondrous", "storage", "extradimensional", "utility"],
  "category": "wondrous_item",
  "rarity": "uncommon",
  "magic": true,
  "cost": { "amount": 400, "unit": "gp" },
  "weight": { "amount": 15, "unit": "lb" },
  "effects": [
    { "en": "Can hold up to 500 pounds, not exceeding 64 cubic feet.", "ru": "Может вмещать до 500 фунтов, не превышая 64 кубических фута." },
    { "en": "The bag weighs 15 pounds, regardless of its contents.", "ru": "Сумка весит 15 фунтов независимо от содержимого." }
  ],
  "references": [
    { "type": "item", "id": "dmg2024:handy_haversack", "relationship": "related" },
    { "type": "item", "id": "dmg2024:portable_hole", "relationship": "related", "context": "Putting a Bag of Holding into a Portable Hole destroys both and opens a gate to the Astral Plane" }
  ]
}
```

---

### 5.3. class.json

**Назначение:** Класс персонажа D&D 2024.

**Место хранения:** `packs/{packId}/classes/{id}.json`

**Структура:**

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID |
| `type` | `string` | ✅ | Всегда `"class"` |
| `format_version` | `int` | ✅ | Версия формата |
| `name` | `LocalizedString` | ✅ | Название класса |
| `short_description` | `LocalizedString` | ❌ | Краткое описание |
| `description` | `LocalizedString` | ✅ | Полное описание |
| `source` | `SourceInfo` | ✅ | Источник |
| `tags` | `string[]` | ❌ | Теги |
| `references` | `Reference[]` | ❌ | Ссылки |
| `metadata` | `object` | ❌ | Метаданные |
| `image` | `ImageInfo` | ❌ | Изображение |
| `hit_die` | `int` | ✅ | Кость хитов (6, 8, 10, 12) |
| `primary_ability` | `string` | ✅ | Основная характеристика |
| `saving_throws` | `string[]` | ✅ | Спасброски (2 характеристики) |
| `skills` | `SkillChoice` | ✅ | Выбор навыков |
| `starting_proficiencies` | `Proficiencies` | ✅ | Начальные владения |
| `starting_equipment` | `EquipmentChoice[]` | ✅ | Начальное снаряжение |
| `subclass_title` | `LocalizedString` | ✅ | Название подкласса (напр. "Arcane Tradition") |
| `subclass_level` | `int` | ✅ | Уровень получения подкласса |
| `features` | `ClassFeature[]` | ✅ | Умения класса |
| `spellcasting` | `SpellcastingInfo` | ❌ | Информация о заклинаниях (null для не-кастеров) |
| `class_table` | `ClassTable` | ✅ | Таблица развития класса |
| `multiclass_requirements` | `MulticlassRequirements` | ✅ | Требования для мультиклассирования |
| `multiclass_proficiencies` | `Proficiencies` | ✅ | Владения при мультиклассировании |

**SkillChoice:**

```json
{
  "count": 2,
  "from": ["arcana", "history", "insight", "investigation", "medicine", "religion"]
}
```

**Proficiencies:**

```json
{
  "armor": [],
  "weapons": ["dagger", "dart", "sling", "quarterstaff", "light_crossbow"],
  "tools": [],
  "saving_throws": ["intelligence", "wisdom"],
  "skills": []
}
```

**EquipmentChoice:**

```json
{
  "description": { "en": "Choose a weapon", "ru": "Выберите оружие" },
  "count": 1,
  "options": [
    { "item_id": "phb2024:quarterstaff", "quantity": 1 },
    { "item_id": "phb2024:dagger", "quantity": 1 }
  ]
}
```

| Поле | Тип | Описание |
|---|---|---|
| `description` | `LocalizedString` | Описание выбора |
| `count` | `int` | Количество выбираемых опций |
| `options` | `EquipmentOption[]` | Варианты выбора |
| `default` | `string` | ❌ Рекомендация по умолчанию |

**EquipmentOption:**

| Поле | Тип | Описание |
|---|---|---|
| `item_id` | `string` | ❌ ID предмета |
| `description` | `LocalizedString` | ❌ Текстовое описание (если не конкретный предмет) |
| `quantity` | `int` | Количество (по умолчанию 1) |

**ClassFeature:**

```json
{
  "id": "spellcasting",
  "name": { "en": "Spellcasting", "ru": "Заклинательство" },
  "level": 1,
  "description": { "en": "As a student of arcane magic, you have a spellbook...", "ru": "..." },
  "optional": false,
  "replaces": null,
  "choices": null
}
```

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID умения внутри класса |
| `name` | `LocalizedString` | ✅ | Название |
| `level` | `int` | ✅ | Уровень получения |
| `description` | `LocalizedString` | ✅ | Описание |
| `optional` | `bool` | ✅ | Опциональное умение (можно заменить) |
| `replaces` | `string` | ❌ | ID умения, которое заменяет это |
| `choices` | `FeatureChoice[]` | ❌ | Варианты выбора внутри умения |

**FeatureChoice:**

```json
{
  "description": { "en": "Choose a skill proficiency", "ru": "Выберите владение навыком" },
  "count": 1,
  "options": [
    { "id": "skill:arcana", "name": { "en": "Arcana", "ru": "Магия" } },
    { "id": "skill:history", "name": { "en": "History", "ru": "История" } }
  ]
}
```

**SpellcastingInfo:**

```json
{
  "ability": "intelligence",
  "type": "prepared",
  "spell_list": "wizard",
  "cantrips_known": [
    { "level": 1, "count": 3 },
    { "level": 4, "count": 4 },
    { "level": 10, "count": 5 }
  ],
  "spells_known": null,
  "spell_slots": {
    "full_caster": true,
    "slots": [
      { "level": 1, "slots": {"1": 2} },
      { "level": 2, "slots": {"1": 3} },
      { "level": 3, "slots": {"1": 4, "2": 2} }
    ]
  }
}
```

| Поле | Тип | Описание |
|---|---|---|
| `ability` | `string` | Характеристика для заклинаний |
| `type` | `string` | `"prepared"`, `"known"`, `"none"` |
| `spell_list` | `string` | ID списка заклинаний (`"wizard"`, `"cleric"`, ...) или `null` для фиксированного списка |
| `cantrips_known` | `ProgressionValue[]` | Прогрессия известных заговоров |
| `spells_known` | `ProgressionValue[]` | ❌ Прогрессия известных заклинаний (для known-кастеров) |
| `spell_slots` | `SpellSlotsTable` | Таблица ячеек заклинаний |

**ProgressionValue:**

```json
{ "level": 1, "count": 3 }
```

**SpellSlotsTable:**

```json
{
  "full_caster": true,
  "pact_magic": false,
  "slots": [
    { "level": 1, "slots": { "1": 2 } },
    { "level": 2, "slots": { "1": 3 } },
    { "level": 3, "slots": { "1": 4, "2": 2 } }
  ]
}
```

**ClassTable:**

```json
{
  "columns": [
    { "key": "proficiency_bonus", "name": { "en": "Prof. Bonus", "ru": "Бонус мастерства" } },
    { "key": "features", "name": { "en": "Features", "ru": "Умения" } },
    { "key": "cantrips_known", "name": { "en": "Cantrips", "ru": "Заговоры" } }
  ],
  "rows": [
    { "level": 1, "values": { "proficiency_bonus": "+2", "features": "Spellcasting, Arcane Recovery", "cantrips_known": "3" } },
    { "level": 2, "values": { "proficiency_bonus": "+2", "features": "Scholar", "cantrips_known": "3" } }
  ]
}
```

**MulticlassRequirements:**

```json
{
  "ability_scores": {
    "intelligence": 13
  }
}
```

**Полный пример — wizard.json (сокращённо):**

```json
{
  "id": "wizard",
  "type": "class",
  "format_version": 1,
  "name": { "en": "Wizard", "ru": "Волшебник" },
  "short_description": {
    "en": "A scholarly magic-user capable of manipulating the structures of reality.",
    "ru": "Учёный заклинатель, способный манипулировать структурами реальности."
  },
  "description": {
    "en": "Wizards are supreme magic-users, defined and united as a class by the spells they cast...",
    "ru": "Волшебники — высшие заклинатели, определённые и объединённые как класс заклинаниями, которые они творят..."
  },
  "source": {
    "book": { "en": "Player's Handbook (2024)", "ru": "Книга игрока (2024)" },
    "abbreviation": "PHB 2024",
    "page": 55
  },
  "tags": ["arcane", "spellcaster", "full-caster", "intelligence", "prepared", "scholar"],
  "references": [
    { "type": "mechanic", "id": "phb2024:spellcasting", "relationship": "requires" },
    { "type": "spell", "id": "phb2024:fireball", "relationship": "related" }
  ],
  "hit_die": 6,
  "primary_ability": "intelligence",
  "saving_throws": ["intelligence", "wisdom"],
  "skills": {
    "count": 2,
    "from": ["arcana", "history", "insight", "investigation", "medicine", "religion"]
  },
  "starting_proficiencies": {
    "armor": [],
    "weapons": ["dagger", "dart", "sling", "quarterstaff", "light_crossbow"],
    "tools": [],
    "saving_throws": ["intelligence", "wisdom"],
    "skills": []
  },
  "starting_equipment": [
    {
      "description": { "en": "Starting weapon", "ru": "Начальное оружие" },
      "count": 1,
      "options": [
        { "item_id": "phb2024:quarterstaff", "quantity": 1 },
        { "item_id": "phb2024:dagger", "quantity": 1 }
      ]
    },
    {
      "description": { "en": "Arcane focus", "ru": "Магический фокус" },
      "count": 1,
      "options": [
        { "item_id": "phb2024:component_pouch", "quantity": 1 },
        { "item_id": "phb2024:arcane_focus_crystal", "quantity": 1 }
      ]
    },
    {
      "item_id": "phb2024:spellbook",
      "quantity": 1
    },
    {
      "item_id": "phb2024:scholars_pack",
      "quantity": 1
    }
  ],
  "subclass_title": { "en": "Arcane Tradition", "ru": "Магическая традиция" },
  "subclass_level": 3,
  "features": [
    {
      "id": "spellcasting",
      "name": { "en": "Spellcasting", "ru": "Заклинательство" },
      "level": 1,
      "description": { "en": "As a student of arcane magic, you have a spellbook containing spells that show the first glimmerings of your true power.", "ru": "..." },
      "optional": false
    },
    {
      "id": "arcane_recovery",
      "name": { "en": "Arcane Recovery", "ru": "Магическое восстановление" },
      "level": 1,
      "description": { "en": "You can recover some of your magical energy by studying your spellbook. Once per day when you finish a Short Rest, you can choose expended spell slots to recover...", "ru": "..." },
      "optional": false
    }
  ],
  "spellcasting": {
    "ability": "intelligence",
    "type": "prepared",
    "spell_list": "wizard",
    "cantrips_known": [
      { "level": 1, "count": 3 },
      { "level": 4, "count": 4 },
      { "level": 10, "count": 5 }
    ],
    "spell_slots": {
      "full_caster": true,
      "slots": [
        { "level": 1, "slots": { "1": 2 } },
        { "level": 2, "slots": { "1": 3 } },
        { "level": 3, "slots": { "1": 4, "2": 2 } }
      ]
    }
  },
  "class_table": {
    "columns": [
      { "key": "proficiency_bonus", "name": { "en": "Prof. Bonus", "ru": "БМ" } },
      { "key": "features", "name": { "en": "Features", "ru": "Умения" } }
    ],
    "rows": [
      { "level": 1, "values": { "proficiency_bonus": "+2", "features": "Spellcasting, Arcane Recovery" } },
      { "level": 2, "values": { "proficiency_bonus": "+2", "features": "Scholar" } },
      { "level": 3, "values": { "proficiency_bonus": "+2", "features": "Arcane Tradition" } }
    ]
  },
  "multiclass_requirements": {
    "ability_scores": { "intelligence": 13 }
  },
  "multiclass_proficiencies": {
    "armor": [],
    "weapons": [],
    "tools": [],
    "saving_throws": [],
    "skills": []
  }
}
```

---

### 5.4. subclass.json

**Назначение:** Подкласс (архетип) класса.

**Место хранения:** `packs/{packId}/classes/subclasses/{id}.json`

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID |
| `type` | `string` | ✅ | Всегда `"subclass"` |
| `format_version` | `int` | ✅ | Версия формата |
| `name` | `LocalizedString` | ✅ | Название подкласса |
| `description` | `LocalizedString` | ✅ | Описание |
| `source` | `SourceInfo` | ✅ | Источник |
| `tags` | `string[]` | ❌ | Теги |
| `references` | `Reference[]` | ❌ | Ссылки |
| `class` | `string` | ✅ | ID родительского класса |
| `features` | `ClassFeature[]` | ✅ | Умения подкласса |

**Пример — evoker.json:**

```json
{
  "id": "evoker",
  "type": "subclass",
  "format_version": 1,
  "name": { "en": "Evoker", "ru": "Эвокатор" },
  "description": {
    "en": "You focus your study on magic that creates powerful elemental effects such as bitter cold, searing flame, rolling thunder, crackling lightning, and burning acid.",
    "ru": "Вы фокусируетесь на магии, создающей мощные элементальные эффекты..."
  },
  "source": {
    "book": { "en": "Player's Handbook (2024)", "ru": "Книга игрока (2024)" },
    "abbreviation": "PHB 2024",
    "page": 62
  },
  "tags": ["evocation", "damage", "elemental", "blaster"],
  "class": "phb2024:wizard",
  "features": [
    {
      "id": "evocation_savant",
      "name": { "en": "Evocation Savant", "ru": "Знаток эвокации" },
      "level": 3,
      "description": { "en": "The gold and time you must spend to copy an Evocation spell into your spellbook is halved.", "ru": "..." },
      "optional": false
    },
    {
      "id": "sculpt_spells",
      "name": { "en": "Sculpt Spells", "ru": "Лепка заклинаний" },
      "level": 3,
      "description": { "en": "You can create pockets of relative safety within the effects of your evocation spells...", "ru": "..." },
      "optional": false
    }
  ]
}
```

---

### 5.5. species.json

**Назначение:** Вид (раса) персонажа.

**Место хранения:** `packs/{packId}/species/{id}.json`

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID |
| `type` | `string` | ✅ | Всегда `"species"` |
| `format_version` | `int` | ✅ | Версия формата |
| `name` | `LocalizedString` | ✅ | Название |
| `description` | `LocalizedString` | ✅ | Описание |
| `source` | `SourceInfo` | ✅ | Источник |
| `tags` | `string[]` | ❌ | Теги |
| `references` | `Reference[]` | ❌ | Ссылки |
| `creature_type` | `string` | ✅ | Тип существа (`"humanoid"`, `"dragon"`, `"fey"`, ...) |
| `size` | `string` | ✅ | Размер: `"tiny"`, `"small"`, `"medium"`, `"large"` |
| `speed` | `int` | ✅ | Базовая скорость ходьбы (футы) |
| `speeds_other` | `object` | ❌ | Другие скорости: `{"climb": 30, "fly": 60, "swim": 30}` |
| `darkvision` | `int` | ❌ | Дистанция тёмного зрения в футах |
| `traits` | `SpeciesTrait[]` | ✅ | Видовые черты |
| `ability_score_increases` | `AbilityScoreIncrease[]` | ✅ | Увеличения характеристик |
| `languages` | `LanguageChoice` | ✅ | Языки |
| `average_lifespan` | `string` | ❌ | Средняя продолжительность жизни |
| `average_height` | `string` | ❌ | Средний рост |
| `average_weight` | `string` | ❌ | Средний вес |
| `subspecies` | `SubspeciesInfo[]` | ❌ | Подвиды / варианты |

**SpeciesTrait:**

```json
{
  "name": { "en": "Fey Ancestry", "ru": "Наследие фей" },
  "description": { "en": "You have Advantage on saving throws to avoid or end the Charmed condition.", "ru": "..." },
  "level": null
}
```

| Поле | Тип | Описание |
|---|---|---|
| `name` | `LocalizedString` | Название черты |
| `description` | `LocalizedString` | Описание |
| `level` | `int` | ❌ Уровень получения (если зависит от уровня) |

**AbilityScoreIncrease:**

```json
{
  "ability": "intelligence",
  "increase": 2,
  "optional": false
}
```

| Поле | Тип | Описание |
|---|---|---|
| `ability` | `string` | Характеристика |
| `increase` | `int` | Увеличение (обычно 1 или 2) |
| `optional` | `bool` | Можно ли перенести в другую характеристику (Tasha's-style) |

**LanguageChoice:**

```json
{
  "count": 2,
  "from": null,
  "default": ["common", "elvish"]
}
```

| Поле | Тип | Описание |
|---|---|---|
| `count` | `int` | Количество выбираемых языков (0 = только default) |
| `from` | `string[]` | ❌ Список доступных языков (null = любые) |
| `default` | `string[]` | Языки по умолчанию |

**SubspeciesInfo:**

```json
{
  "id": "elf_high",
  "name": { "en": "High Elf", "ru": "Высший эльф" },
  "description": { "en": "..." },
  "traits": [...],
  "ability_score_increases": [...]
}
```

**Полный пример — elf_high.json:**

```json
{
  "id": "elf_high",
  "type": "species",
  "format_version": 1,
  "name": { "en": "High Elf", "ru": "Высший эльф" },
  "short_description": {
    "en": "Elves with a keen intellect and natural affinity for magic.",
    "ru": "Эльфы с острым интеллектом и природной склонностью к магии."
  },
  "description": {
    "en": "High elves are the most common elves, known for their intelligence, grace, and affinity for magic...",
    "ru": "Высшие эльфы — наиболее распространённые эльфы, известные своим интеллектом, грацией и склонностью к магии..."
  },
  "source": {
    "book": { "en": "Player's Handbook (2024)", "ru": "Книга игрока (2024)" },
    "abbreviation": "PHB 2024",
    "page": 12
  },
  "tags": ["elf", "fey", "magic", "intelligence", "common"],
  "creature_type": "humanoid",
  "size": "medium",
  "speed": 30,
  "darkvision": 60,
  "traits": [
    {
      "name": { "en": "Fey Ancestry", "ru": "Наследие фей" },
      "description": { "en": "You have Advantage on saving throws to avoid or end the Charmed condition.", "ru": "..." }
    },
    {
      "name": { "en": "Keen Senses", "ru": "Острые чувства" },
      "description": { "en": "You have Proficiency in the Perception skill.", "ru": "..." }
    },
    {
      "name": { "en": "Trance", "ru": "Транс" },
      "description": { "en": "You don't need to sleep and instead enter a trance for 4 hours to gain the benefits of a Long Rest.", "ru": "..." }
    },
    {
      "name": { "en": "Elven Lineage", "ru": "Эльфийское происхождение" },
      "description": { "en": "You gain one cantrip from the Wizard spell list. Intelligence is your spellcasting ability for it.", "ru": "..." }
    }
  ],
  "ability_score_increases": [
    { "ability": "dexterity", "increase": 2, "optional": false },
    { "ability": "intelligence", "increase": 1, "optional": false }
  ],
  "languages": {
    "count": 1,
    "from": null,
    "default": ["common", "elvish"]
  },
  "average_lifespan": "750 years",
  "average_height": "5'4\" – 6'2\"",
  "average_weight": "100 – 145 lb"
}
```

---

### 5.6. background.json

**Назначение:** Происхождение персонажа.

**Место хранения:** `packs/{packId}/backgrounds/{id}.json`

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID |
| `type` | `string` | ✅ | Всегда `"background"` |
| `format_version` | `int` | ✅ | Версия формата |
| `name` | `LocalizedString` | ✅ | Название |
| `description` | `LocalizedString` | ✅ | Описание |
| `source` | `SourceInfo` | ✅ | Источник |
| `tags` | `string[]` | ❌ | Теги |
| `references` | `Reference[]` | ❌ | Ссылки |
| `ability_score_increases` | `AbilityScoreIncrease[]` | ✅ | Увеличения характеристик |
| `skill_proficiencies` | `string[]` | ✅ | Владения навыками |
| `tool_proficiencies` | `string[]` | ❌ | Владения инструментами |
| `languages` | `LanguageChoice` | ❌ | Дополнительные языки |
| `equipment` | `EquipmentChoice[]` | ✅ | Начальное снаряжение |
| `feat` | `string` | ❌ | ID черты происхождения |
| `feature` | `BackgroundFeature` | ✅ | Умение происхождения |
| `characteristics` | `Characteristics` | ✅ | Таблицы характеристик |

**BackgroundFeature:**

```json
{
  "name": { "en": "Military Rank", "ru": "Военное звание" },
  "description": { "en": "You have a military rank from your career as a soldier...", "ru": "..." }
}
```

**Characteristics:**

```json
{
  "personality_traits": {
    "d8": [
      "I'm always polite and respectful.",
      "I'm haunted by memories of war."
    ]
  },
  "ideals": {
    "d6": [
      "Greater Good. Our lot is to lay down our lives in defense of others.",
      "Responsibility. I do what I must and obey just authority."
    ]
  },
  "bonds": {
    "d6": [
      "I would still lay down my life for the people I served with.",
      "Someone saved my life on the battlefield."
    ]
  },
  "flaws": {
    "d6": [
      "The monstrous enemy we faced in battle still leaves me quivering with fear.",
      "I have little respect for anyone who is not a proven warrior."
    ]
  }
}
```

**Полный пример — soldier.json:**

```json
{
  "id": "soldier",
  "type": "background",
  "format_version": 1,
  "name": { "en": "Soldier", "ru": "Солдат" },
  "description": {
    "en": "War has been your life for as long as you care to remember. You trained as a youth, studied the use of weapons and armor, learned basic survival techniques...",
    "ru": "Война была вашей жизнью столько, сколько вы себя помните..."
  },
  "source": {
    "book": { "en": "Player's Handbook (2024)", "ru": "Книга игрока (2024)" },
    "abbreviation": "PHB 2024",
    "page": 38
  },
  "tags": ["martial", "military", "strength", "constitution"],
  "ability_score_increases": [
    { "ability": "strength", "increase": 2, "optional": true },
    { "ability": "constitution", "increase": 1, "optional": true }
  ],
  "skill_proficiencies": ["athletics", "intimidation"],
  "tool_proficiencies": ["vehicles_land", "gaming_set_dice"],
  "languages": null,
  "equipment": [
    { "item_id": "phb2024:shortsword", "quantity": 1 },
    { "item_id": "phb2024:shield", "quantity": 1 },
    { "item_id": "phb2024:explorers_pack", "quantity": 1 }
  ],
  "feat": "phb2024:savage_attacker",
  "feature": {
    "name": { "en": "Military Rank", "ru": "Военное звание" },
    "description": { "en": "You have a military rank from your career as a soldier. Soldiers loyal to your former military organization still recognize your authority and influence, and they defer to you if they are of a lower rank.", "ru": "..." }
  },
  "characteristics": {
    "personality_traits": {
      "d8": [
        "I'm always polite and respectful.",
        "I'm haunted by memories of war. I can't get the images of violence out of my mind.",
        "I've lost too many friends, and I'm slow to make new ones.",
        "I'm full of inspiring and cautionary tales from my military experience.",
        "I can stare down a hell hound without flinching.",
        "I enjoy being strong and like breaking things.",
        "I have a crude sense of humor.",
        "I face problems head-on. A simple, direct solution is the best path to success."
      ]
    },
    "ideals": {
      "d6": [
        "Greater Good. Our lot is to lay down our lives in defense of others. (Good)",
        "Responsibility. I do what I must and obey just authority. (Lawful)",
        "Independence. When people follow orders blindly, they embrace a kind of tyranny. (Chaotic)",
        "Might. In life as in war, the stronger force wins. (Evil)",
        "Live and Let Live. Ideals aren't worth killing over or going to war for. (Neutral)",
        "Nation. My city, nation, or people are all that matter. (Any)"
      ]
    },
    "bonds": {
      "d6": [
        "I would still lay down my life for the people I served with.",
        "Someone saved my life on the battlefield. To this day, I will never leave a friend behind.",
        "My honor is my life.",
        "I'll never forget the crushing defeat my company suffered or the enemies who dealt it.",
        "Those who fight beside me are those worth dying for.",
        "I fight for those who cannot fight for themselves."
      ]
    },
    "flaws": {
      "d6": [
        "The monstrous enemy we faced in battle still leaves me quivering with fear.",
        "I have little respect for anyone who is not a proven warrior.",
        "I made a terrible mistake in battle that cost many lives — and I would do anything to keep that mistake secret.",
        "My hatred of my enemies is blind and unreasoning.",
        "I obey the law, even if the law causes misery.",
        "I'd rather eat my armor than admit when I'm wrong."
      ]
    }
  }
}
```

---

### 5.7. feat.json

**Назначение:** Черта персонажа.

**Место хранения:** `packs/{packId}/feats/{id}.json`

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID |
| `type` | `string` | ✅ | Всегда `"feat"` |
| `format_version` | `int` | ✅ | Версия формата |
| `name` | `LocalizedString` | ✅ | Название |
| `description` | `LocalizedString` | ✅ | Описание |
| `source` | `SourceInfo` | ✅ | Источник |
| `tags` | `string[]` | ❌ | Теги |
| `references` | `Reference[]` | ❌ | Ссылки |
| `category` | `string` | ✅ | Категория: `"origin"`, `"general"`, `"fighting_style"`, `"epic_boon"` |
| `prerequisite` | `LocalizedString` | ❌ | Требование |
| `ability_score_increase` | `AbilityScoreIncrease[]` | ❌ | Увеличение характеристик |
| `repeatable` | `bool` | ✅ | Можно ли брать несколько раз |
| `benefits` | `FeatBenefit[]` | ✅ | Преимущества черты |

**FeatBenefit:**

```json
{
  "name": { "en": "Heavy Weapon Mastery", "ru": "Мастерство тяжёлого оружия" },
  "description": { "en": "When you hit a creature with a weapon that has the Heavy property, you can...", "ru": "..." }
}
```

**Полный пример — great_weapon_master.json:**

```json
{
  "id": "great_weapon_master",
  "type": "feat",
  "format_version": 1,
  "name": { "en": "Great Weapon Master", "ru": "Мастер большого оружия" },
  "description": {
    "en": "You've learned to use the weight of a weapon to your advantage, letting its momentum empower your strikes.",
    "ru": "Вы научились использовать вес оружия в своих интересах, позволяя его инерции усиливать ваши удары."
  },
  "source": {
    "book": { "en": "Player's Handbook (2024)", "ru": "Книга игрока (2024)" },
    "abbreviation": "PHB 2024",
    "page": 200
  },
  "tags": ["combat", "damage", "heavy-weapon", "melee", "strength"],
  "category": "general",
  "prerequisite": { "en": "Strength 13+", "ru": "Сила 13+" },
  "ability_score_increase": [
    { "ability": "strength", "increase": 1, "optional": false }
  ],
  "repeatable": false,
  "benefits": [
    {
      "name": { "en": "Heavy Weapon Mastery", "ru": "Мастерство тяжёлого оружия" },
      "description": { "en": "When you hit a creature with a weapon that has the Heavy property, you can add your Proficiency Bonus to the damage roll. You can use this benefit only once per turn.", "ru": "..." }
    },
    {
      "name": { "en": "Cleave", "ru": "Рассечение" },
      "description": { "en": "When you reduce a creature to 0 Hit Points with a melee weapon that has the Heavy property, you can make one additional attack with the same weapon against a different creature within your reach.", "ru": "..." }
    }
  ]
}
```

---

### 5.8. condition.json

**Назначение:** Состояние, накладываемое на существ.

**Место хранения:** `packs/{packId}/conditions/{id}.json`

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID |
| `type` | `string` | ✅ | Всегда `"condition"` |
| `format_version` | `int` | ✅ | Версия формата |
| `name` | `LocalizedString` | ✅ | Название |
| `description` | `LocalizedString` | ✅ | Полное описание эффекта |
| `source` | `SourceInfo` | ✅ | Источник |
| `tags` | `string[]` | ❌ | Теги |
| `references` | `Reference[]` | ❌ | Ссылки на связанные условия/механики |
| `effects` | `LocalizedString[]` | ✅ | Список механических эффектов |
| `mechanical` | `bool` | ✅ | Чисто механическое состояние (не требует отыгрыша) |

**Полный пример — blinded.json:**

```json
{
  "id": "blinded",
  "type": "condition",
  "format_version": 1,
  "name": { "en": "Blinded", "ru": "Ослепление" },
  "description": {
    "en": "While you have the Blinded condition, you experience the following effects.",
    "ru": "Пока вы находитесь в состоянии Ослепления, вы испытываете следующие эффекты."
  },
  "source": {
    "book": { "en": "Player's Handbook (2024)", "ru": "Книга игрока (2024)" },
    "abbreviation": "PHB 2024",
    "page": 363
  },
  "tags": ["senses", "disadvantage", "combat"],
  "references": [
    { "type": "mechanic", "id": "phb2024:advantage", "relationship": "related" },
    { "type": "condition", "id": "phb2024:invisible", "relationship": "related" }
  ],
  "effects": [
    { "en": "You can't see and automatically fail any ability check that requires sight.", "ru": "..." },
    { "en": "Attack rolls against you have Advantage, and your attack rolls have Disadvantage.", "ru": "..." }
  ],
  "mechanical": true
}
```

---

### 5.9. monster.json

**Назначение:** Монстр, NPC или существо.

**Место хранения:** `packs/{packId}/monsters/{id}.json`

| Поле | Тип | Обязательное | Описание | Поиск | Фильтр | Сортировка | Карточка | Детали |
|---|---|---|---|---|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID | ✅ | — | — | — | — |
| `type` | `string` | ✅ | Всегда `"monster"` | — | — | — | — | — |
| `format_version` | `int` | ✅ | Версия формата | — | — | — | — | — |
| `name` | `LocalizedString` | ✅ | Название | ✅ | — | ✅ | ✅ | ✅ |
| `short_description` | `LocalizedString` | ❌ | Краткое описание | ✅ | — | — | ✅ | — |
| `description` | `LocalizedString` | ✅ | Полное описание (flavor text) | ✅ | — | — | — | ✅ |
| `source` | `SourceInfo` | ✅ | Источник | — | ✅ | — | — | ✅ |
| `tags` | `string[]` | ❌ | Теги | ✅ | ✅ | — | ✅ | — |
| `references` | `Reference[]` | ❌ | Ссылки | — | — | — | — | ✅ |
| `image` | `ImageInfo` | ❌ | Изображение | — | — | — | — | ✅ |
| `size` | `string` | ✅ | Размер: `"tiny"`, `"small"`, `"medium"`, `"large"`, `"huge"`, `"gargantuan"` | — | ✅ | ✅ | ✅ | ✅ |
| `creature_type` | `string` | ✅ | Тип существа: `"aberration"`, `"beast"`, `"celestial"`, `"construct"`, `"dragon"`, `"elemental"`, `"fey"`, `"fiend"`, `"giant"`, `"humanoid"`, `"monstrosity"`, `"ooze"`, `"plant"`, `"undead"` | — | ✅ | ✅ | ✅ | ✅ |
| `subtype` | `string` | ❌ | Подтип (напр. `"goblinoid"`, `"shapechanger"`) | — | ✅ | — | ✅ | ✅ |
| `alignment` | `string` | ✅ | Мировоззрение | — | ✅ | — | — | ✅ |
| `armor_class` | `int` | ✅ | Класс брони | — | — | ✅ | ✅ | ✅ |
| `armor_class_description` | `string` | ❌ | Описание КБ (напр. `"natural armor"`) | — | — | — | — | ✅ |
| `hit_points` | `string` | ✅ | Хиты (напр. `"33 (6d8 + 6)"`) | — | — | — | ✅ | ✅ |
| `hit_dice` | `string` | ✅ | Кости хитов (напр. `"6d8"`) | — | — | — | — | ✅ |
| `speed` | `MonsterSpeed` | ✅ | Скорости | — | — | — | ✅ | ✅ |
| `ability_scores` | `object` | ✅ | Характеристики (`{"strength": 10, ...}`) | — | — | — | — | ✅ |
| `saving_throws` | `object` | ❌ | Спасброски (`{"wisdom": 3, ...}`) | — | — | — | — | ✅ |
| `skills` | `object` | ❌ | Навыки (`{"stealth": 6, "perception": 3}`) | — | — | — | — | ✅ |
| `damage_vulnerabilities` | `string[]` | ❌ | Уязвимости к урону | — | ✅ | — | — | ✅ |
| `damage_resistances` | `string[]` | ❌ | Сопротивления урону | — | ✅ | — | — | ✅ |
| `damage_immunities` | `string[]` | ❌ | Иммунитеты к урону | — | ✅ | — | — | ✅ |
| `condition_immunities` | `string[]` | ❌ | Иммунитеты к состояниям (ID условий) | — | ✅ | — | — | ✅ |
| `senses` | `object` | ❌ | Чувства (`{"darkvision": 60, "passive_perception": 10}`) | — | — | — | — | ✅ |
| `languages` | `string` | ✅ | Языки | — | ✅ | — | — | ✅ |
| `challenge_rating` | `float` | ✅ | Опасность (CR) | — | ✅ | ✅ | ✅ | ✅ |
| `xp` | `int` | ✅ | Опыт | — | — | ✅ | ✅ | ✅ |
| `proficiency_bonus` | `int` | ✅ | Бонус мастерства | — | — | — | — | ✅ |
| `traits` | `MonsterAbility[]` | ❌ | Особые черты | — | — | — | — | ✅ |
| `actions` | `MonsterAbility[]` | ❌ | Действия | — | — | — | — | ✅ |
| `bonus_actions` | `MonsterAbility[]` | ❌ | Бонусные действия | — | — | — | — | ✅ |
| `reactions` | `MonsterAbility[]` | ❌ | Реакции | — | — | — | — | ✅ |
| `legendary_actions` | `LegendaryActions` | ❌ | Легендарные действия | — | — | — | — | ✅ |
| `mythic_actions` | `MonsterAbility[]` | ❌ | Мифические действия | — | — | — | — | ✅ |
| `lair_actions` | `MonsterAbility[]` | ❌ | Действия логова | — | — | — | — | ✅ |
| `environment` | `string[]` | ❌ | Среда обитания | — | ✅ | — | — | ✅ |
| `treasure` | `string` | ❌ | Сокровища | — | — | — | — | ✅ |

**MonsterSpeed:**

```json
{
  "walk": 30,
  "fly": 60,
  "hover": true,
  "swim": null,
  "climb": null,
  "burrow": null
}
```

**MonsterAbility:**

```json
{
  "name": { "en": "Nimble Escape", "ru": "Проворное бегство" },
  "description": { "en": "The goblin can take the Disengage or Hide action as a Bonus Action on each of its turns.", "ru": "..." },
  "attack": null
}
```

Для атакующего действия добавляется поле `attack`:

```json
{
  "name": { "en": "Scimitar", "ru": "Скимитар" },
  "description": { "en": "Melee Weapon Attack: +4 to hit, reach 5 ft., one target. Hit: 5 (1d6 + 2) slashing damage.", "ru": "..." },
  "attack": {
    "type": "melee",
    "attack_bonus": 4,
    "reach": 5,
    "targets": "one target",
    "damage": [
      { "damage_dice": "1d6", "damage_type": "slashing", "damage_bonus": 2 }
    ]
  }
}
```

**MonsterAttackDamage:**

| Поле | Тип | Описание |
|---|---|---|
| `damage_dice` | `string` | Формула урона |
| `damage_type` | `string` | Тип урона |
| `damage_bonus` | `int` | Бонус к урону |

**LegendaryActions:**

```json
{
  "count": 3,
  "description": { "en": "The dragon can take 3 legendary actions...", "ru": "..." },
  "actions": [...]
}
```

**Полный пример — goblin.json:**

```json
{
  "id": "goblin",
  "type": "monster",
  "format_version": 1,
  "name": { "en": "Goblin", "ru": "Гоблин" },
  "short_description": {
    "en": "A small, nimble humanoid with a malicious grin.",
    "ru": "Маленький, проворный гуманоид со злобной ухмылкой."
  },
  "description": {
    "en": "Goblins are small, black-hearted humanoids that lair in despoiled dungeons and other dismal settings. Individually weak, they gather in large numbers to torment other creatures.",
    "ru": "Гоблины — маленькие, злобные гуманоиды, обитающие в разорённых подземельях и других мрачных местах."
  },
  "source": {
    "book": { "en": "Monster Manual (2025)", "ru": "Бестиарий (2025)" },
    "abbreviation": "MM 2025",
    "page": 142
  },
  "tags": ["goblinoid", "low-level", "humanoid", "evil", "pack-tactics"],
  "size": "small",
  "creature_type": "humanoid",
  "subtype": "goblinoid",
  "alignment": "neutral evil",
  "armor_class": 15,
  "armor_class_description": "leather armor, shield",
  "hit_points": "7 (2d6)",
  "hit_dice": "2d6",
  "speed": { "walk": 30 },
  "ability_scores": {
    "strength": 8,
    "dexterity": 14,
    "constitution": 10,
    "intelligence": 10,
    "wisdom": 8,
    "charisma": 8
  },
  "saving_throws": null,
  "skills": { "stealth": 6 },
  "damage_vulnerabilities": null,
  "damage_resistances": null,
  "damage_immunities": null,
  "condition_immunities": null,
  "senses": { "darkvision": 60, "passive_perception": 9 },
  "languages": "Common, Goblin",
  "challenge_rating": 0.25,
  "xp": 50,
  "proficiency_bonus": 2,
  "traits": [
    {
      "name": { "en": "Nimble Escape", "ru": "Проворное бегство" },
      "description": { "en": "The goblin can take the Disengage or Hide action as a Bonus Action on each of its turns.", "ru": "..." }
    }
  ],
  "actions": [
    {
      "name": { "en": "Scimitar", "ru": "Скимитар" },
      "description": { "en": "Melee Weapon Attack: +4 to hit, reach 5 ft., one target. Hit: 5 (1d6 + 2) slashing damage.", "ru": "..." },
      "attack": {
        "type": "melee",
        "attack_bonus": 4,
        "reach": 5,
        "targets": "one target",
        "damage": [{ "damage_dice": "1d6", "damage_type": "slashing", "damage_bonus": 2 }]
      }
    },
    {
      "name": { "en": "Shortbow", "ru": "Короткий лук" },
      "description": { "en": "Ranged Weapon Attack: +4 to hit, range 80/320 ft., one target. Hit: 5 (1d6 + 2) piercing damage.", "ru": "..." },
      "attack": {
        "type": "ranged",
        "attack_bonus": 4,
        "range_normal": 80,
        "range_long": 320,
        "targets": "one target",
        "damage": [{ "damage_dice": "1d6", "damage_type": "piercing", "damage_bonus": 2 }]
      }
    }
  ],
  "environment": ["forest", "hill", "underdark", "urban"]
}
```

---

### 5.10. mechanic.json

**Назначение:** Игровая механика, правило или раздел правил.

**Место хранения:** `packs/{packId}/mechanics/{id}.json`

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID |
| `type` | `string` | ✅ | Всегда `"mechanic"` |
| `format_version` | `int` | ✅ | Версия формата |
| `name` | `LocalizedString` | ✅ | Название |
| `description` | `LocalizedString` | ✅ | Полный текст правил |
| `source` | `SourceInfo` | ✅ | Источник |
| `tags` | `string[]` | ❌ | Теги |
| `references` | `Reference[]` | ❌ | Ссылки на связанные механики |
| `category` | `string` | ✅ | Категория |
| `subcategory` | `string` | ❌ | Подкатегория |
| `related` | `string[]` | ❌ | ID связанных механик |

**Значения `category`:**

| Значение | Описание |
|---|---|
| `"combat"` | Боевые правила |
| `"exploration"` | Исследование |
| `"social"` | Социальное взаимодействие |
| `"spellcasting"` | Заклинательство |
| `"adventuring"` | Приключения |
| `"conditions"` | Состояния (общие правила) |
| `"ability_scores"` | Характеристики |
| `"skills"` | Навыки |
| `"equipment"` | Снаряжение (общие правила) |
| `"rest"` | Отдых |
| `"death"` | Смерть и умирание |
| `"travel"` | Путешествия |
| `"hazards"` | Опасности и ловушки |
| `"rewards"` | Награды и опыт |
| `"other"` | Прочее |

**Полный пример — combat_actions.json:**

```json
{
  "id": "combat_actions",
  "type": "mechanic",
  "format_version": 1,
  "name": { "en": "Actions in Combat", "ru": "Действия в бою" },
  "description": {
    "en": "When you take your action on your turn, you can take one of the actions presented here, an action you gained from your class or a special feature, or an action that you improvise...",
    "ru": "Когда вы совершаете действие в свой ход, вы можете выбрать одно из представленных здесь действий..."
  },
  "source": {
    "book": { "en": "Player's Handbook (2024)", "ru": "Книга игрока (2024)" },
    "abbreviation": "PHB 2024",
    "page": 192
  },
  "tags": ["combat", "actions", "core", "reference"],
  "category": "combat",
  "subcategory": "actions",
  "references": [
    { "type": "mechanic", "id": "phb2024:bonus_actions", "relationship": "related" },
    { "type": "mechanic", "id": "phb2024:reactions", "relationship": "related" }
  ],
  "related": ["phb2024:bonus_actions", "phb2024:reactions", "phb2024:movement", "phb2024:attack_action"]
}
```

---

### 5.11. glossary.json

**Назначение:** Термин глоссария / ключевое понятие.

**Место хранения:** `packs/{packId}/glossary/{id}.json`

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | `string` | ✅ | Локальный ID |
| `type` | `string` | ✅ | Всегда `"glossary"` |
| `format_version` | `int` | ✅ | Версия формата |
| `name` | `LocalizedString` | ✅ | Термин |
| `description` | `LocalizedString` | ✅ | Определение |
| `source` | `SourceInfo` | ✅ | Источник |
| `tags` | `string[]` | ❌ | Теги |
| `references` | `Reference[]` | ❌ | Ссылки |
| `category` | `string` | ❌ | Категория термина |
| `see_also` | `string[]` | ❌ | ID связанных терминов/механик |

**Полный пример — armor_class.json:**

```json
{
  "id": "armor_class",
  "type": "glossary",
  "format_version": 1,
  "name": { "en": "Armor Class", "ru": "Класс брони" },
  "description": {
    "en": "Armor Class (AC) is a numerical rating measuring how difficult a creature is to hit in combat. An attack roll must equal or exceed the target's AC to hit. AC can be calculated from armor, natural armor, or the formula 10 + Dexterity modifier when unarmored.",
    "ru": "Класс брони (КБ) — это числовой показатель, измеряющий, насколько сложно попасть по существу в бою..."
  },
  "source": {
    "book": { "en": "Player's Handbook (2024)", "ru": "Книга игрока (2024)" },
    "abbreviation": "PHB 2024",
    "page": 361
  },
  "tags": ["combat", "defense", "core", "reference"],
  "category": "combat",
  "see_also": ["phb2024:attack_roll", "phb2024:armor_training"],
  "references": [
    { "type": "mechanic", "id": "phb2024:combat_basics", "relationship": "related" }
  ]
}
```

---

## 6. Пользовательские данные

### 6.1. character.json

**Назначение:** Персонаж игрока. Хранится в Room как JSON-блоб в таблице `characters`.

**Место хранения:** `characters` table → колонка `json_data`

**Структура:**

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | `string` | ✅ | UUID персонажа |
| `name` | `string` | ✅ | Имя персонажа |
| `version` | `int` | ✅ | Версия формата данных персонажа |
| `created_at` | `string` | ✅ | ISO 8601 дата создания |
| `updated_at` | `string` | ✅ | ISO 8601 дата обновления |
| `class_id` | `string` | ✅ | ID класса |
| `level` | `int` | ✅ | Уровень (1–20) |
| `subclass_id` | `string` | ❌ | ID подкласса (если выбран) |
| `species_id` | `string` | ✅ | ID вида |
| `background_id` | `string` | ✅ | ID происхождения |
| `alignment` | `string` | ❌ | Мировоззрение |
| `experience` | `int` | ✅ | Опыт (≥ 0) |
| `ability_scores` | `object` | ✅ | `{"strength": 10, "dexterity": 14, ...}` |
| `hit_points` | `HitPoints` | ✅ | Хиты |
| `hit_dice` | `HitDiceState` | ✅ | Состояние костей хитов |
| `armor_class` | `int` | ✅ | Текущий КБ |
| `initiative` | `int` | ✅ | Модификатор инициативы |
| `speed` | `int` | ✅ | Скорость |
| `proficiency_bonus` | `int` | ✅ | Бонус мастерства |
| `skills` | `SkillState[]` | ✅ | Состояние навыков |
| `saving_throws` | `string[]` | ✅ | Владения спасбросками |
| `feats` | `string[]` | ❌ | ID выбранных черт |
| `features` | `string[]` | ❌ | ID активных умений |
| `equipment` | `CharacterEquipment[]` | ✅ | Снаряжение |
| `spells` | `CharacterSpells` | ❌ | Заклинания (null для не-кастеров) |
| `spell_slots` | `object` | ❌ | Состояние ячеек (`{"1": {"total": 4, "used": 0}, ...}`) |
| `currency` | `Currency` | ✅ | Валюта |
| `appearance` | `string` | ❌ | Описание внешности |
| `backstory` | `string` | ❌ | Предыстория |
| `notes` | `string` | ❌ | Заметки |
| `inspiration` | `bool` | ✅ | Вдохновение (по умолчанию false) |
| `death_saves` | `DeathSaves` | ✅ | Спасброски от смерти |
| `conditions` | `string[]` | ✅ | ID активных состояний (по умолчанию []) |
| `exhaustion` | `int` | ✅ | Уровень истощения (0–6, по умолчанию 0) |
| `portrait` | `string` | ❌ | Путь к портрету (локальный файл) |

**HitPoints:**

```json
{
  "max": 32,
  "current": 32,
  "temporary": 0
}
```

**HitDiceState:**

```json
{
  "total": "5d6",
  "remaining": 5
}
```

**SkillState:**

```json
{
  "skill": "arcana",
  "proficient": true,
  "expertise": false,
  "bonus": 7
}
```

**CharacterEquipment:**

```json
{
  "item_id": "phb2024:quarterstaff",
  "quantity": 1,
  "equipped": true,
  "notes": "Family heirloom"
}
```

**CharacterSpells:**

```json
{
  "cantrips": ["phb2024:fire_bolt", "phb2024:mage_hand", "phb2024:prestidigitation"],
  "prepared": ["phb2024:magic_missile", "phb2024:shield", "phb2024:fireball"],
  "known": ["phb2024:magic_missile", "phb2024:shield", "phb2024:fireball", "phb2024:counterspell", "phb2024:fly"]
}
```

| Поле | Тип | Описание |
|---|---|---|
| `cantrips` | `string[]` | Известные заговоры |
| `prepared` | `string[]` | Подготовленные заклинания (для prepared-кастеров) |
| `known` | `string[]` | Все известные заклинания (для known-кастеров) |

**Currency:**

```json
{
  "cp": 0,
  "sp": 0,
  "gp": 150,
  "pp": 0
}
```

**DeathSaves:**

```json
{
  "successes": 0,
  "failures": 0
}
```

**Полный пример — wizard_level5.json:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Gandalf",
  "version": 1,
  "created_at": "2024-01-15T10:30:00Z",
  "updated_at": "2024-06-20T18:00:00Z",
  "class_id": "phb2024:wizard",
  "level": 5,
  "subclass_id": "phb2024:evoker",
  "species_id": "phb2024:human",
  "background_id": "phb2024:sage",
  "alignment": "neutral good",
  "experience": 6500,
  "ability_scores": {
    "strength": 10,
    "dexterity": 14,
    "constitution": 13,
    "intelligence": 18,
    "wisdom": 12,
    "charisma": 10
  },
  "hit_points": { "max": 32, "current": 32, "temporary": 0 },
  "hit_dice": { "total": "5d6", "remaining": 5 },
  "armor_class": 12,
  "initiative": 2,
  "speed": 30,
  "proficiency_bonus": 3,
  "skills": [
    { "skill": "arcana", "proficient": true, "expertise": false, "bonus": 7 },
    { "skill": "history", "proficient": true, "expertise": false, "bonus": 7 },
    { "skill": "investigation", "proficient": true, "expertise": false, "bonus": 7 },
    { "skill": "insight", "proficient": true, "expertise": false, "bonus": 4 },
    { "skill": "perception", "proficient": false, "expertise": false, "bonus": 1 },
    { "skill": "stealth", "proficient": false, "expertise": false, "bonus": 2 }
  ],
  "saving_throws": ["intelligence", "wisdom"],
  "feats": ["phb2024:keen_mind"],
  "features": ["phb2024:spellcasting", "phb2024:arcane_recovery", "phb2024:evocation_savant", "phb2024:sculpt_spells"],
  "equipment": [
    { "item_id": "phb2024:quarterstaff", "quantity": 1, "equipped": true },
    { "item_id": "phb2024:spellbook", "quantity": 1, "equipped": false },
    { "item_id": "phb2024:component_pouch", "quantity": 1, "equipped": true },
    { "item_id": "phb2024:scholars_pack", "quantity": 1, "equipped": false },
    { "item_id": "phb2024:dagger", "quantity": 1, "equipped": false }
  ],
  "spells": {
    "cantrips": ["phb2024:fire_bolt", "phb2024:mage_hand", "phb2024:prestidigitation", "phb2024:light"],
    "prepared": ["phb2024:magic_missile", "phb2024:shield", "phb2024:mage_armor", "phb2024:fireball", "phb2024:counterspell", "phb2024:fly", "phb2024:haste", "phb2024:dispel_magic", "phb2024:misty_step"],
    "known": null
  },
  "spell_slots": {
    "1": { "total": 4, "used": 0 },
    "2": { "total": 3, "used": 0 },
    "3": { "total": 2, "used": 0 }
  },
  "currency": { "cp": 0, "sp": 0, "gp": 150, "pp": 0 },
  "appearance": "A tall figure with a long grey beard, piercing blue eyes, and a weathered grey cloak.",
  "backstory": "A wizard of great power, sent to Middle-earth to aid in the fight against Sauron...",
  "notes": "Has a ring of power — Narya, the Ring of Fire.",
  "inspiration": false,
  "death_saves": { "successes": 0, "failures": 0 },
  "conditions": [],
  "exhaustion": 0
}
```

---

### 6.2. favorite.json

**Назначение:** Избранный объект. Хранится в Room в таблице `favorites`.

**Место хранения:** `favorites` table

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `object_id` | `string` | ✅ | Полный ID объекта (`"phb2024:fireball"`) |
| `object_type` | `string` | ✅ | Тип объекта (`"spell"`, `"item"`, `"monster"`, ...) |
| `added_at` | `string` | ✅ | ISO 8601 дата добавления |

**Room entity:**

```json
{
  "object_id": "phb2024:fireball",
  "object_type": "spell",
  "added_at": "2024-06-20T18:00:00Z"
}
```

---

### 6.3. settings.json

**Назначение:** Настройки приложения. Хранятся в Room в таблице `settings` (key-value).

**Место хранения:** `settings` table

**Стандартные ключи:**

| Ключ | Тип значения | По умолчанию | Описание |
|---|---|---|---|
| `theme` | `string` | `"system"` | `"system"`, `"light"`, `"dark"` |
| `language` | `string` | `"en"` | ISO 639-1 код языка |
| `font_size` | `string` | `"medium"` | `"small"`, `"medium"`, `"large"` |
| `show_dice_roller` | `bool` | `true` | Показывать встроенный дайс-роллер |
| `default_pack_filter` | `string` | `"all"` | Фильтр пакетов по умолчанию |
| `last_used_character_id` | `string` | `null` | ID последнего открытого персонажа |
| `auto_save_interval` | `int` | `300` | Интервал автосохранения (сек, 0 = выкл) |

**Пример:**

```json
{
  "theme": "dark",
  "language": "ru",
  "font_size": "large",
  "show_dice_roller": true,
  "default_pack_filter": "phb2024",
  "last_used_character_id": "550e8400-e29b-41d4-a716-446655440000",
  "auto_save_interval": 120
}
```

---

## 7. Форматы обмена

### 7.1. Импорт/экспорт пакетов

**Формат:** ZIP-архив.

**Структура ZIP:**

```
my_pack.zip
├── pack.json                     # Метаданные пакета (обязательно)
├── manifest.json                 # Индекс объектов (обязательно)
├── spells/
│   ├── my_spell.json
│   └── ...
├── items/
│   └── ...
├── classes/
│   └── ...
├── images/                       # Изображения (опционально)
│   └── ...
└── schemas/                      # Пользовательские схемы (опционально)
    └── ...
```

**Процесс импорта:**
1. Распаковать ZIP во временную директорию
2. Проверить наличие `pack.json` → прочитать `pack_id`
3. Проверить `format_version` на совместимость
4. Проверить наличие `manifest.json`
5. Для каждого объекта в manifest: найти JSON-файл, проверить схему
6. Проверить перекрёстные ссылки (warnings, не errors)
7. Проверить зависимости пакета (если указаны)
8. Скопировать в `packs/{pack_id}/`
9. Перестроить индекс ContentRepository

**Обработка конфликтов:**
- Если пакет с таким `pack_id` уже существует → спросить пользователя: перезаписать, объединить или отменить
- Если merge: объекты с одинаковыми ID перезаписываются новыми; объекты только в новом пакете добавляются; объекты только в старом остаются без изменений

**Процесс экспорта:**
1. Собрать все JSON-файлы пакета из ContentRepository
2. Сгенерировать актуальный `manifest.json`
3. Обновить `pack.json` (updated_at, total_objects)
4. Упаковать в ZIP
5. Отдать через Android ShareSheet

### 7.2. Импорт/экспорт персонажей

**Формат:** Одиночный JSON-файл (`.json`) или ZIP с JSON + портретом.

**Структура JSON:** Полная структура [character.json](#61-characterjson).

**Процесс импорта персонажа:**
1. Прочитать JSON
2. Валидировать структуру
3. Проверить ссылки на класс, вид, происхождение, заклинания (warnings)
4. Присвоить новый UUID (если конфликтует с существующим)
5. Сохранить в Room

**Процесс экспорта персонажа:**
1. Загрузить из Room
2. Записать в JSON-файл
3. (Опционально) приложить портрет
4. Отдать через ShareSheet

### 7.3. Экспорт настроек

**Формат:** JSON-файл `herocraft24_settings.json`.

**Структура:** Все key-value пары из таблицы `settings`.

**Процесс импорта:** Слияние (merge) с существующими настройками — новые ключи добавляются, существующие перезаписываются.

---

## 8. Правила валидации

### 8.1. Валидация при загрузке

Каждый JSON-файл проходит следующие проверки:

| Проверка | Уровень | Описание |
|---|---|---|
| Валидный JSON | **ERROR** | Файл должен быть валидным JSON (RFC 8259) |
| Обязательные поля | **ERROR** | Все поля с пометкой «Обязательное» должны присутствовать |
| Типы полей | **ERROR** | Каждое поле должно соответствовать указанному типу |
| Формат ID | **ERROR** | `id` должен соответствовать `[a-z0-9_]+` |
| Значения enum | **ERROR** | Enum-поля должны содержать только допустимые значения |
| Числовые диапазоны | **ERROR** | `level` 0–9, `hit_die` ∈ {6, 8, 10, 12}, `challenge_rating` ≥ 0 |
| Перекрёстные ссылки | **WARNING** | Ссылка на несуществующий объект — warning, не ошибка |
| Дубликаты ID | **ERROR** | Два объекта одного типа в одном пакете не могут иметь одинаковый `id` |
| Локализация | **WARNING** | Отсутствует перевод для текущего языка приложения |

### 8.2. Schema Validation

Каждый тип объекта имеет JSON Schema (хранится в `core:data`). Schema определяет:
- Обязательные поля
- Типы полей
- Допустимые значения enum
- Числовые ограничения
- Формат строковых полей

Schema используется:
1. При импорте пакета — полная валидация
2. При загрузке объекта — быстрая проверка critical-полей

### 8.3. Cross-Reference Resolution

При загрузке объекта все поля-ссылки проверяются:
- Если целевой объект существует → OK
- Если целевой объект в другом пакете, который ещё не загружен → отложенная проверка
- Если целевой объект не существует → **warning**, ссылка отображается как `[Unknown]`

---

## 9. Версионирование и обратная совместимость

### 9.1. `format_version`

Каждый JSON-файл содержит поле `format_version` (целое число, начиная с 1).

**Правила изменения версии:**
- Добавление нового необязательного поля → **не меняет** `format_version`
- Изменение типа существующего поля → **увеличивает** `format_version`
- Удаление поля → **увеличивает** `format_version`
- Изменение семантики поля → **увеличивает** `format_version`

### 9.2. Миграция

Приложение должно уметь читать все предыдущие `format_version`. Для каждого типа объекта определяется маппинг миграций:

```
format_version 1 → 2: поле "description" переименовано в "full_description"
format_version 2 → 3: добавлено поле "short_description" (обратно совместимо, не требует миграции)
```

Миграция выполняется «на лету» при загрузке JSON (не модифицирует исходный файл).

### 9.3. Стратегия расширения

Для обеспечения обратной совместимости:
- Все новые поля — **опциональные** (с разумными значениями по умолчанию)
- Поле `metadata` (`object`) — свободное пространство для пользовательских расширений
- Поле `tags` (`string[]`) — для добавления новых категорий без изменения структуры
- Приложение **игнорирует** неизвестные поля в JSON (не падает)

---

## 10. Система перекрёстных ссылок

### 10.1. Формат ссылки

Все ссылки между объектами — строковые ID в формате `packId:objectId`.

### 10.2. Типы ссылок

| Тип ссылки | Пример | Где используется |
|---|---|---|
| Прямая ссылка | `"class": "phb2024:wizard"` | `subclass.class`, `character.class_id` |
| Ссылка-массив | `"classes": ["phb2024:wizard", "phb2024:sorcerer"]` | `spell.classes` |
| Ссылка в Reference | `{"type": "spell", "id": "phb2024:fireball"}` | `references[]` |
| Ссылка в опциях | `"item_id": "phb2024:quarterstaff"` | `EquipmentOption.item_id` |

### 10.3. Resolution API

```kotlin
// ContentRepository предоставляет:
suspend fun resolveReference(id: String): Result<GameEntity>
suspend fun resolveReferences(ids: List<String>): Result<List<GameEntity>>
```

Разрешение ссылки:
1. Парсим `packId:objectId`
2. Ищем объект в кэше
3. Если нет в кэше — загружаем JSON из пакета
4. Возвращаем объект

### 10.4. Обратные ссылки

При загрузке пакета строится индекс обратных ссылок (какие объекты ссылаются на данный). Это позволяет:
- Показать «Где используется это заклинание?» (классы, подклассы, предметы)
- Предупредить при удалении пакета о битых ссылках

---

## 11. Индексация для поиска и фильтрации

### 11.1. Поисковый индекс

Строится на основе `manifest.json` всех загруженных пакетов:

| Поле индекса | Источник |
|---|---|
| `full_id` | `pack_id + ":" + object.id` |
| `name_en` | `object.name.en` |
| `name_ru` | `object.name.ru` (и другие локали) |
| `short_description_en` | `object.short_description.en` |
| `description_en` | `object.description.en` (первые 500 символов) |
| `tags` | `object.tags[]` |
| `type` | `object.type` |

**Поиск:** полнотекстовый (FTS) по всем текстовым полям. Результаты ранжируются по релевантности.

### 11.2. Поля для фильтрации

Каждый тип объекта имеет набор полей, доступных для фильтрации (помечены в таблицах раздела 5 как «Фильтр»):

| Тип объекта | Поля фильтрации |
|---|---|
| `spell` | `level`, `school`, `concentration`, `ritual`, `components`, `classes`, `saving_throw`, `attack_type`, `damage.damage_type`, `area_of_effect.type`, `source` |
| `item` | `category`, `subcategory`, `rarity`, `magic`, `attunement`, `properties`, `cost`, `source` |
| `monster` | `size`, `creature_type`, `subtype`, `alignment`, `challenge_rating`, `damage_resistances`, `damage_immunities`, `condition_immunities`, `languages`, `environment`, `source` |

### 11.3. Поля для сортировки

| Тип объекта | Поля сортировки |
|---|---|
| `spell` | `name` (по умолчанию), `level`, `school` |
| `item` | `name` (по умолчанию), `rarity`, `cost`, `weight`, `category` |
| `monster` | `name` (по умолчанию), `challenge_rating`, `xp`, `size`, `armor_class` |

---

## 12. Приложение: Все enum-значения

### 12.1. MagicSchool (школа магии)
```
abjuration, conjuration, divination, enchantment, evocation, illusion, necromancy, transmutation
```

### 12.2. AbilityScore (характеристика)
```
strength, dexterity, constitution, intelligence, wisdom, charisma
```

### 12.3. Skill (навык)
```
acrobatics, animal_handling, arcana, athletics, deception, history, insight, intimidation, investigation, medicine, nature, perception, performance, persuasion, religion, sleight_of_hand, stealth, survival
```

### 12.4. DamageType (тип урона)
```
acid, bludgeoning, cold, fire, force, lightning, necrotic, piercing, poison, psychic, radiant, slashing, thunder
```

### 12.5. SpellComponent (компонент заклинания)
```
V, S, M
```

### 12.6. SpellRangeType (тип дистанции)
```
self, touch, sight, unlimited, range, special
```

### 12.7. AreaOfEffectType (тип области)
```
cube, cone, cylinder, line, sphere, emanation
```

### 12.8. ItemCategory (категория предмета)
```
weapon, armor, shield, adventuring_gear, ammunition, arcane_focus, druidic_focus, holy_symbol, tool, artisan_tool, gaming_set, musical_instrument, mount, vehicle, tack_and_harness, food_and_drink, poison, potion, scroll, wand, rod, staff, ring, wondrous_item, consumable, treasure, other
```

### 12.9. ItemRarity (редкость предмета)
```
common, uncommon, rare, very_rare, legendary, artifact, varies, unknown
```

### 12.10. ItemProperty (свойство предмета)
```
ammunition, finesse, heavy, light, loading, range, reach, special, thrown, two_handed, versatile, silvered, masterwork
```

### 12.11. CreatureType (тип существа)
```
aberration, beast, celestial, construct, dragon, elemental, fey, fiend, giant, humanoid, monstrosity, ooze, plant, undead
```

### 12.12. CreatureSize (размер)
```
tiny, small, medium, large, huge, gargantuan
```

### 12.13. Alignment (мировоззрение)
```
lawful_good, neutral_good, chaotic_good, lawful_neutral, true_neutral, chaotic_neutral, lawful_evil, neutral_evil, chaotic_evil, unaligned, any
```

### 12.14. SpellcastingType (тип заклинательства)
```
prepared, known, none
```

### 12.15. FeatCategory (категория черты)
```
origin, general, fighting_style, epic_boon
```

### 12.16. MechanicCategory (категория механики)
```
combat, exploration, social, spellcasting, adventuring, conditions, ability_scores, skills, equipment, rest, death, travel, hazards, rewards, other
```

### 12.17. Currency (валюта)
```
cp, sp, gp, pp
```

### 12.18. License (лицензия пакета)
```
official, homebrew, ogl, cc-by, custom
```

### 12.19. RulesVersion (версия правил)
```
dnd2024, dnd5e, custom
```

---

## Appendix A: Пример manifest.json (полный)

```json
{
  "pack_id": "phb2024",
  "name": {
    "en": "Player's Handbook 2024",
    "ru": "Книга игрока 2024"
  },
  "version": "1.0.0",
  "format_version": 1,
  "rules_version": "dnd2024",
  "authors": ["Wizards of the Coast"],
  "license": "official",
  "website": "https://dnd.wizards.com",
  "description": {
    "en": "Core rules for Dungeons & Dragons 2024, including classes, species, spells, feats, and equipment.",
    "ru": "Основные правила Dungeons & Dragons 2024, включая классы, виды, заклинания, черты и снаряжение."
  },
  "dependencies": [],
  "language": "en",
  "locales": ["en", "ru"],
  "created_at": "2024-09-17T00:00:00Z",
  "updated_at": "2024-09-17T00:00:00Z",
  "total_objects": 350,
  "objects": {
    "spells": [
      {
        "id": "fireball",
        "name": { "en": "Fireball", "ru": "Огненный шар" },
        "level": 3,
        "school": "evocation",
        "ritual": false,
        "concentration": false,
        "classes": ["wizard", "sorcerer"],
        "tags": ["fire", "damage", "area", "evocation"]
      },
      {
        "id": "magic_missile",
        "name": { "en": "Magic Missile", "ru": "Волшебная стрела" },
        "level": 1,
        "school": "evocation",
        "ritual": false,
        "concentration": false,
        "classes": ["wizard", "sorcerer"],
        "tags": ["force", "damage", "auto-hit"]
      }
    ],
    "items": [
      {
        "id": "longsword",
        "name": { "en": "Longsword", "ru": "Длинный меч" },
        "category": "weapon",
        "rarity": "common",
        "tags": ["weapon", "martial", "melee", "versatile", "slashing"]
      },
      {
        "id": "bag_of_holding",
        "name": { "en": "Bag of Holding", "ru": "Сумка хранения" },
        "category": "wondrous_item",
        "rarity": "uncommon",
        "tags": ["wondrous", "storage", "extradimensional", "utility"]
      }
    ],
    "classes": [
      {
        "id": "wizard",
        "name": { "en": "Wizard", "ru": "Волшебник" },
        "hit_die": 6,
        "primary_ability": "intelligence",
        "tags": ["arcane", "spellcaster", "full-caster"]
      },
      {
        "id": "fighter",
        "name": { "en": "Fighter", "ru": "Воин" },
        "hit_die": 10,
        "primary_ability": "strength",
        "tags": ["martial", "weapon-master", "combat"]
      }
    ],
    "species": [
      {
        "id": "elf_high",
        "name": { "en": "High Elf", "ru": "Высший эльф" },
        "type": "humanoid",
        "size": "medium",
        "speed": 30,
        "tags": ["elf", "fey", "magic"]
      },
      {
        "id": "human",
        "name": { "en": "Human", "ru": "Человек" },
        "type": "humanoid",
        "size": "medium",
        "speed": 30,
        "tags": ["human", "versatile", "common"]
      }
    ],
    "backgrounds": [
      {
        "id": "soldier",
        "name": { "en": "Soldier", "ru": "Солдат" },
        "tags": ["martial", "military"]
      },
      {
        "id": "sage",
        "name": { "en": "Sage", "ru": "Мудрец" },
        "tags": ["knowledge", "scholar", "intelligence"]
      }
    ],
    "feats": [
      {
        "id": "great_weapon_master",
        "name": { "en": "Great Weapon Master", "ru": "Мастер большого оружия" },
        "category": "general",
        "tags": ["combat", "damage", "heavy-weapon"]
      },
      {
        "id": "keen_mind",
        "name": { "en": "Keen Mind", "ru": "Острый ум" },
        "category": "general",
        "tags": ["intelligence", "knowledge", "utility"]
      }
    ],
    "monsters": [
      {
        "id": "goblin",
        "name": { "en": "Goblin", "ru": "Гоблин" },
        "size": "small",
        "type": "humanoid",
        "challenge_rating": 0.25,
        "tags": ["goblinoid", "low-level", "humanoid"]
      }
    ],
    "conditions": [
      {
        "id": "blinded",
        "name": { "en": "Blinded", "ru": "Ослепление" },
        "tags": ["senses", "disadvantage"]
      },
      {
        "id": "charmed",
        "name": { "en": "Charmed", "ru": "Очарование" },
        "tags": ["social", "control"]
      }
    ],
    "mechanics": [
      {
        "id": "combat_actions",
        "name": { "en": "Actions in Combat", "ru": "Действия в бою" },
        "category": "combat",
        "tags": ["combat", "actions", "core"]
      },
      {
        "id": "spellcasting",
        "name": { "en": "Spellcasting Rules", "ru": "Правила заклинательства" },
        "category": "spellcasting",
        "tags": ["spellcasting", "core", "magic"]
      }
    ],
    "glossary": [
      {
        "id": "armor_class",
        "name": { "en": "Armor Class", "ru": "Класс брони" },
        "tags": ["combat", "defense", "core"]
      },
      {
        "id": "advantage",
        "name": { "en": "Advantage", "ru": "Преимущество" },
        "tags": ["core", "dice", "mechanics"]
      }
    ]
  }
}
```

---

> **Документ завершён.** Это полная спецификация данных для HeroCraft24.
>
> Все решения о структурах, типах и валидации, принятые здесь, являются окончательными для реализации приложения.
>
> Любые изменения в структуре данных должны отражаться в этом документе через увеличение `format_version` и обновление соответствующих разделов.