# HeroCraft24 — Архитектура приложения

> **D&D 2024 Companion App** | Kotlin · Android SDK · XML Layouts · Material 3
>
> Статус: **Проектирование** | Версия документа: 1.0 | 2026-07-28

---

## 1. Философия архитектуры

Проект построен на трёх столпах:

1. **Контент — это данные, а не код.** Любое игровое правило, заклинание или предмет можно добавить, удалить или изменить, не трогая исходники. Приложение — это движок для чтения и отображения данных, а не жёстко закодированная энциклопедия.

2. **Модули — изолированные острова.** Feature-модули не знают друг о друге. Связь между ними только через core-слой и строковые ID. Это позволяет переписывать один модуль, не трогая остальные.

3. **Централизованный источник истины.** Справочник (`reference`) — единственный владелец игровых данных. Все остальные модули получают данные через `ContentRepository`, а не напрямую из JSON. Это исключает дублирование логики загрузки.

---

## 2. Структура модулей

```
HeroCraft24/
├── app/                          # Точка входа, DI (Hilt), навигационный хост
├── core/
│   ├── model/                    # Доменные модели (pure Kotlin, без Android)
│   ├── data/                     # Загрузка JSON, валидация, кэш, поиск
│   ├── database/                 # Room (только пользовательские данные)
│   └── ui/                       # Material 3 тема, общие View/Adapter'ы
├── feature/
│   ├── reference/                # Справочник (классы, виды, бестиарий и т.д.)
│   ├── spells/                   # Заклинания
│   ├── equipment/                # Снаряжение
│   ├── characters/               # Персонажи
│   └── settings/                 # Настройки
└── docs/                         # Документация
```

### 2.1. Граф зависимостей

```
core:model          ← ни от кого не зависит (pure Kotlin)
core:data           ← core:model + kotlinx-serialization
core:database       ← core:model + Room
core:ui             ← core:model + Material 3 + AndroidX

feature:reference   ← core:model + core:data + core:ui
feature:spells      ← core:model + core:data + core:ui + core:database (favorites)
feature:equipment   ← core:model + core:data + core:ui + core:database (favorites)
feature:characters  ← core:model + core:data + core:ui + core:database
feature:settings    ← core:model + core:data + core:ui + core:database

app                 ← все модули (точка сборки)
```

### 2.2. Правила зависимостей

- **Feature-модули никогда не импортируют друг друга.**
- Если `spells` нужно показать класс, к которому относится заклинание — он запрашивает `ContentRepository.getClassById(id)` из `core:data`, а не лезет в `feature:reference`.
- Core-модули не зависят от feature-модулей.
- `app` — единственный модуль, который знает обо всех модулях (DI wiring).

---

## 3. JSON-архитектура

### 3.1. Файловая структура пакета

```
phb2024/                          # ID пакета
├── manifest.json                 # Метаданные всех объектов пакета
├── spells/
│   ├── fireball.json
│   ├── magic_missile.json
│   └── ...
├── items/
│   ├── longsword.json
│   ├── bag_of_holding.json
│   └── ...
├── classes/
│   ├── wizard.json
│   └── ...
├── species/
│   ├── elf_high.json
│   └── ...
├── backgrounds/
│   ├── soldier.json
│   └── ...
├── feats/
│   ├── great_weapon_master.json
│   └── ...
├── monsters/
│   ├── goblin.json
│   └── ...
├── conditions/
│   ├── blinded.json
│   └── ...
├── mechanics/
│   ├── combat_actions.json
│   └── ...
└── glossary/
    ├── armor_class.json
    └── ...
```

### 3.2. ID и перекрёстные ссылки

Все ID — namespace'd: `packId:objectId`

```
phb2024:fireball          # заклинание Fireball из PHB 2024
phb2024:wizard            # класс Wizard
phb2024:longsword         # предмет Longsword
my_homebrew:custom_spell  # пользовательский контент
```

Перекрёстные ссылки — только строковые ID, никогда не display names:

```json
// Правильно
{
  "id": "fireball",
  "classes": ["phb2024:wizard", "phb2024:sorcerer"]
}

// ❌ Запрещено
{
  "id": "fireball",
  "classes": ["Wizard", "Sorcerer"]
}
```

### 3.3. Двухуровневая загрузка

1. **Summary** (из manifest) — id, name, пара ключевых полей. Для списков и поиска. Загружается при старте.
2. **Full** (из JSON) — полный объект. Загружается лениво — только при открытии карточки.

Это радикально снижает потребление памяти и время старта.

### 3.4. Источники данных и приоритет

```
1. Внешние пакеты (external storage)  ← наивысший приоритет
2. Встроенные пакеты (assets)         ← базовый приоритет
```

При конфликте ID (одинаковый `packId:objectId`) — внешний пакет переопределяет встроенный. Это позволяет пользователю патчить/исправлять контент без модификации APK.

---

## 4. ContentRepository — центральный API

Единственная точка доступа к игровым данным для всех feature-модулей:

```kotlin
interface ContentRepository {
    // Загрузка пакетов
    suspend fun loadAllPacks(): Result<List<Pack>>
    suspend fun importPack(directory: File): Result<Pack>
    suspend fun exportPack(packId: String, target: File): Result<Unit>
    suspend fun deletePack(packId: String): Result<Unit>

    // Получение объектов по ID (ленивая загрузка полного JSON)
    suspend fun getSpell(id: String): Result<Spell>
    suspend fun getGameClass(id: String): Result<GameClass>
    suspend fun getItem(id: String): Result<Item>
    suspend fun getSpecies(id: String): Result<Species>
    suspend fun getBackground(id: String): Result<Background>
    suspend fun getFeat(id: String): Result<Feat>
    suspend fun getMonster(id: String): Result<Monster>
    suspend fun getCondition(id: String): Result<Condition>
    suspend fun getMechanic(id: String): Result<Mechanic>

    // Списки (из manifest — быстрые, без парсинга полных JSON)
    fun getAllSpells(): Flow<List<SpellSummary>>
    fun getAllClasses(): Flow<List<ClassSummary>>
    // ...

    // Поиск (FTS по manifest)
    fun search(query: String, type: EntityType?): Flow<List<SearchResult>>

    // Разрешение перекрёстных ссылок
    suspend fun resolveReference(id: String): Result<GameEntity>
}
```

---

## 5. Стратегия навигации

### 5.1. Граф навигации

```
app/src/main/res/navigation/
├── nav_main.xml          # Топ-уровень: BottomNavigation + 5 вкладок
├── nav_reference.xml     # Справочник (feature:reference)
├── nav_spells.xml        # Заклинания (feature:spells)
├── nav_equipment.xml     # Снаряжение (feature:equipment)
├── nav_characters.xml    # Персонажи (feature:characters)
└── nav_settings.xml      # Настройки (feature:settings)
```

### 5.2. Структура экранов

```
MainActivity
└── BottomNavigationView (5 tabs)
    ├── Tab 1: Персонажи
    │   ├── CharacterListFragment          # список персонажей
    │   ├── CharacterCreateFragment        # пошаговый wizard создания
    │   └── CharacterSheetFragment         # чар-лист (управление персонажем)
    │
    ├── Tab 2: Заклинания
    │   ├── SpellListFragment              # список + поиск + фильтры
    │   └── SpellDetailFragment            # карточка заклинания
    │
    ├── Tab 3: Снаряжение
    │   ├── EquipmentListFragment          # список + поиск + фильтры
    │   └── EquipmentDetailFragment        # карточка предмета
    │
    ├── Tab 4: Справочник
    │   ├── ReferenceHomeFragment          # 7 категорий
    │   ├── ClassListFragment → ClassDetailFragment
    │   ├── SpeciesListFragment → SpeciesDetailFragment
    │   ├── BackgroundListFragment → BackgroundDetailFragment
    │   ├── FeatListFragment → FeatDetailFragment
    │   ├── ConditionListFragment → ConditionDetailFragment
    │   ├── BestiaryListFragment → MonsterDetailFragment
    │   └── MechanicsListFragment → MechanicDetailFragment
    │
    └── Tab 5: Настройки
        ├── SettingsFragment
        ├── ImportExportFragment
        └── AboutFragment
```

### 5.3. Правила навигации

- Каждый таб имеет **собственный back stack** (через `NavigationUI.setupWithNavController`)
- При переключении табов back stack сохраняется
- Глубокие ссылки между модулями — через ID объекта и `nav_main.xml` с аргументами

---

## 6. Dependency Injection (Hilt)

```kotlin
// core:data — предоставляет ContentRepository (Singleton)
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun provideContentRepository(
        jsonLoader: JsonLoader,
        schemaValidator: SchemaValidator,
        searchEngine: SearchEngine,
        cache: InMemoryCache
    ): ContentRepository
}

// core:database — предоставляет DAO
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase
}

// feature:spells — предоставляет свои ViewModel
@Module
@InstallIn(ViewModelComponent::class)
object SpellsModule {
    @Provides
    fun provideSpellRepository(
        contentRepository: ContentRepository,
        favoritesDao: FavoritesDao
    ): SpellRepository
}
```

---

## 7. Импорт / Экспорт

### Экспорт пакета
```
1. ContentRepository.getAllObjects(packId) → список ID
2. Для каждого ID: загрузить полный JSON, записать в файл
3. Сгенерировать manifest.json
4. Упаковать в ZIP
5. Отдать через ShareSheet / сохранить
```

### Импорт пакета
```
1. Пользователь выбирает ZIP / директорию
2. SchemaValidator проверяет каждый JSON на соответствие схеме
3. Если ошибки — показать пользователю, предложить исправить
4. Если OK — скопировать в packs/ директорию
5. ContentRepository.invalidateCache() → перестроить индекс
```

### Проверка схемы
Каждый тип объекта имеет JSON Schema. Валидация при импорте и при запуске:

```kotlin
interface SchemaValidator {
    fun validate(packDir: File): List<SchemaError>
    // SchemaError(file=..., line=..., message=..., severity=WARNING|ERROR)
}
```

Ошибки ссылок (битые ID) — **warning, не fatal**. Приложение продолжает работу, битая ссылка отображается как «[Unknown]».

---

## 8. Пользовательские данные (Room)

Room используется **только** для пользовательских данных. Игровая база знаний хранится исключительно в JSON.

```kotlin
// Три таблицы Room
@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey val id: String,       // UUID
    val name: String,
    val jsonData: String,             // полный JSON персонажа (см. Data Specification)
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objectId: String,             // "phb2024:fireball"
    val objectType: String,           // "spell" | "item" | "monster" | "class" | ...
    val addedAt: Long
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,      // "theme", "language", ...
    val value: String
)
```

**Почему персонаж — JSON-блоб, а не реляционная схема:**
- Структура персонажа D&D вариативна и зависит от класса, уровня, мультиклассирования
- 2024 правила могут быть дополнены — реляционная схема потребует миграций
- JSON позволяет гибко хранить любые данные без изменения схемы БД
- Room индексирует `name`, `createdAt`, `updatedAt` — для поиска по персонажам этого достаточно

---

## 9. Summary: почему эта архитектура

| Принцип | Как достигается |
|---|---|
| **Слабая связанность** | Feature-модули не импортируют друг друга. Связь только через core:model и ContentRepository |
| **Расширяемость** | Новый тип объекта = новый data-класс в core:model + схема в core:data + UI в feature. Никакой существующий код не ломается |
| **Контент без кода** | JSON-файлы + manifest + SchemaValidator. Добавление нового заклинания — это создание одного JSON-файла |
| **Переиспользование** | core:ui содержит все общие компоненты (карточки, списки, поиск, фильтры). Feature-модули только собирают из них экраны |
| **Тестируемость** | core:model — pure Kotlin, тестируется без Android. ContentRepository — интерфейс, легко мокается. ViewModel тестируются с fake-репозиториями |
| **Масштабирование команды** | Каждый feature-модуль — независимый. Разработчики могут работать параллельно без конфликтов |
| **Долгосрочная поддержка** | Стабильный core:model (меняется редко), изменчивые feature-модули (меняются часто). Обновление правил D&D не требует переписывания ядра |

---

## 10. Этапы разработки

| Этап | Содержание |
|---|---|
| **1. Инициализация** | Gradle-проект, 10 модулей, Hilt, Navigation, базовые зависимости |
| **2. core:model** | Все data-классы, enum-ы, sealed-иерархии, LocalizedString |
| **3. core:data** | JsonLoader, SchemaValidator, ContentRepository, SearchEngine, InMemoryCache |
| **4. core:database** | Room: DAO, Database, миграции, FavoritesDao, CharacterDao, SettingsDao |
| **5. core:ui** | Material 3 тема, BaseFragment/Activity, общие View, адаптеры, binding |
| **6. feature:reference** | Справочник: 7 категорий, списки, детальные экраны, перекрёстные ссылки |
| **7. feature:spells** | Список, поиск, фильтры, избранное, карточка заклинания |
| **8. feature:equipment** | То же для снаряжения |
| **9. feature:characters** | Список, создание, чар-лист, расчёт модификаторов |
| **10. feature:settings** | Тема, импорт/экспорт, очистка данных |
| **11. Финализация** | Тесты, профилирование, релизный APK |

---

> **Связанный документ:** [Data Specification](data-specification.md) — полная спецификация всех JSON-форматов.