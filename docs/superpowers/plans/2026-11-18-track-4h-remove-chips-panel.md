# Трек 4 часа + убрать нижнюю панель чипов — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Глубина трека — 4 часа вместо 2; убрать нижнюю панель с чипами устройств, кнопку настроек перенести в рядок кнопок карты.

**Architecture:** Окно истории задаётся единственной константой `Repo.HIST_WINDOW_MS` — чистка Firebase, `trackAlpha` и тесты наследуют её автоматически. Панель чипов — самостоятельный `Surface` в нижнем столбце `MainScreen`; её функции (выбор устройства, прозвон) дублируются пинами карты и карточками, поэтому панель удаляется целиком.

**Tech Stack:** Kotlin + Compose M3, Yandex MapKit, Firebase RTDB.

**Spec:** обсуждение в сессии (утверждено «да»), короткий дизайн — в шапке плана.

## Global Constraints

- Строки UI — в strings.xml; иконки только material-icons-core.
- Сборка: `JAVA_HOME=/home/filatov/android/jdk-17.0.21+9 ./gradlew assembleDebug testDebugUnitTest`.
- Версия релиза строго больше последней в git log (2026.11.26 → 2026.11.27), не по `date`.

## Review Focus

- Кнопка настроек должна остаться доступной в обеих ориентациях (панель была общая для портрета/ландшафта).
- Кнопки «Все»/«центрировать» не должны прилипнуть к системной навигации (navigationBarsPadding раньше давала удаляемые чипы).
- `trackAlpha(0)` = 255 и монотонность по возрасту — окно выросло вдвое, но кривая задаётся через константу (тест уже есть, адаптируется сам).
- Хвосты: `DeviceChip`, импорты (`LazyRow`, `items`, `combinedClickable`, `alpha`, `ExperimentalFoundationApi`), строка `chip_waiting_for_points` — не должны остаться мёртвым кодом.

---

### Task 1: HIST_WINDOW_MS = 4 часа

**Files:**
- Modify: `app/src/main/java/com/locationshare/app/data/Repo.kt:17`

**Interfaces:**
- Produces: `HIST_WINDOW_MS = 4 * 3_600_000L` (internal const, потребители: чистка в `appendHist`, `trackAlpha` в MainScreen, `TrackAlphaTest`).

- [ ] **Step 1: Поменять константу и комментарий** — `2 * 3_600_000L` → `4 * 3_600_000L`.
- [ ] **Step 2: Прогнать юнит-тесты** — `./gradlew testDebugUnitTest`, ожидается PASS (тест читает константу).
- [ ] **Step 3: Commit** — `трек: глубина истории 4 часа`.

### Task 2: Убрать панель чипов, настройки — в рядок кнопок

**Files:**
- Modify: `app/src/main/java/com/locationshare/app/ui/MainScreen.kt` (панель ~455–496, `DeviceChip` ~907–944, импорты, комментарии 397/413/1144)
- Modify: `app/src/main/res/values/strings.xml` (удалить `chip_waiting_for_points`)

**Interfaces:**
- Consumes: `showSettings`, `R.string.cd_settings`, стиль круглой кнопки «Все» (Surface + CircleShape, 48.dp).

- [ ] **Step 1: Удалить Surface с LazyRow чипов и IconButton настроек** (строки ~455–496).
- [ ] **Step 2: Добавить шестерёнку в рядок кнопок** слева от «Все»: Surface(shape=CircleShape, 48.dp) c `Icons.Filled.Settings`, onClick = `showSettings = true`; рядку дать `navigationBarsPadding().padding(bottom = 12.dp)` вместо нынешнего `bottom = 6.dp`.
- [ ] **Step 3: Удалить DeviceChip и мёртвые импорты** (`LazyRow`, `items`, `combinedClickable`, `alpha`, `ExperimentalFoundationApi` — проверить, что нигде больше не используются), удалить `chip_waiting_for_points` из strings.xml, поправить комментарии про «чипы» (397, 413, 1144).
- [ ] **Step 4: Собрать и протестировать** — `./gradlew assembleDebug testDebugUnitTest`, ожидается BUILD SUCCESSFUL.
- [ ] **Step 5: Commit + релиз** — версия 2026.11.27, `assembleRelease`, коммит «2026.11.27: трек 4 часа, убрал панель чипов (настройки — к кнопкам карты)», push, `gh release create`.
