# Свой цвет + свой трек по тапу — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** выбор собственного цвета устройства (виден всей группе) + выбор себя из карточки кластера показывает свой трек, как у чужих.

**Architecture:** чистая логика цвета — в `Format.kt` (без Android-зависимостей, тестируемо JUnit); цвет хранится в `Prefs`, публикуется полем `color` в `devices/{id}/current` Firebase, читается в `Repo.DeviceState`; UI-фикс выбора — два точечных изменения в `MainScreen.kt`.

**Tech Stack:** Kotlin, Compose M3, Firebase RTDB, Yandex MapKit 4.45.

**Spec:** `docs/superpowers/specs/2026-11-18-own-color-and-track-design.md`

## Global Constraints

- Строки UI — только в `app/src/main/res/values/strings.xml` (локаль одна, RU).
- MapKit-листенеры — только WeakReference + сильная ссылка в remember.
- Иконки — только material-icons-core (extended нет).
- `Format.kt` — чистый Kotlin, без Android-импортов.
- Сборка (WSL): `export JAVA_HOME=/home/filatov/android/jdk-17.0.21+9 && ./gradlew assembleDebug testDebugUnitTest` (~1 мин).
- Коммиты — русские сообщения; версия ГГГГ.ММ.П, versionCode = ГГГГММ*100+патч (патч = день месяца).

## Review Focus

1. Мусорная строка `color` в БД (сторонняя правка, старый формат) → `parseColorHex` даёт null → устройство остаётся на авто-цвете, не краш. Тест: Task 1.
2. Цвет, выбранный одним устройством, не должен доставаться другим в автоназначении. Тест: Task 1 (пропуск занятых).
3. Кастомных цветов 16 (= вся палитра) → автоназначение cycling, повторы допустимы. Тест: Task 1.
4. `publishCurrent` перезаписывает `current` целиком — без `color` в payload цвет слетал бы при каждой публикации точки. Проверка: код-ревью Task 2 + на железе в Task 6.
5. `setColor` до первой публикации точки создаёт `current` без lat — `observe()` обязан такое пропускать (`lat ?: continue` уже есть). Проверка: код-ревью Task 2.

---

### Task 1: Чистая логика цвета в Format.kt (TDD)

**Files:**
- Modify: `app/src/main/java/com/locationshare/app/ui/Format.kt`
- Test: `app/src/test/java/com/locationshare/app/ui/ColorMapTest.kt` (создать)

**Interfaces:**
- Produces (для Tasks 2, 4, 5):
  - `internal val PALETTE_COLORS: List<Int>` — те же 16 ARGB, что в приватном `PALETTE`
  - `internal fun colorHex(c: Int): String` — `"#RRGGBB"`
  - `internal fun parseColorHex(s: String?): Int?` — строгий формат `#` + 6 hex-цифр (регистр любой), иначе null
  - `internal fun colorMapFor(ids: Collection<String>, custom: Map<String, Int> = emptyMap()): Map<String, Int>` — старые вызовы без второго аргумента не меняются

- [ ] **Step 1: Написать падающий тест**

```kotlin
package com.locationshare.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorMapTest {
    private val p = PALETTE_COLORS

    // parseColorHex / colorHex
    @Test fun parse_valid() = assertEquals(0xFF43A047.toInt(), parseColorHex("#43A047"))
    @Test fun parse_lowercase() = assertEquals(0xFF43A047.toInt(), parseColorHex("#43a047"))
    @Test fun parse_rejects() {
        assertNull(parseColorHex(null)); assertNull(parseColorHex("43A047"))
        assertNull(parseColorHex("#ZZZZZZ")); assertNull(parseColorHex("#43A04")); assertNull(parseColorHex(""))
    }
    @Test fun hex_roundtrip() = assertEquals("#43A047", colorHex(parseColorHex("#43a047")!!))

    // colorMapFor
    @Test fun map_noCustom_matchesOldBehavior() =
        assertEquals(mapOf("a" to p[0], "b" to p[1]), colorMapFor(listOf("b", "a")))

    @Test fun map_customWins_othersSkipTaken() {
        // m фиксирует p[3]; a (без кастома) получает p[0] НО пропускает p[3] при раздаче
        val r = colorMapFor(listOf("a", "m"), custom = mapOf("m" to p[3]))
        assertEquals(p[3], r["m"])
        assertEquals(p[0], r["a"])
        val r2 = colorMapFor(listOf("m", "z"), custom = mapOf("m" to p[0]))
        assertEquals(p[1], r2["z"]) // p[0] занят кастомом m
    }

    @Test fun map_allTaken_cycles() {
        // 16 кастомных заняли всю палитру → авто-устройству достаётся p[0] (цикл, повтор допустим)
        val custom = p.indices.associate { "c$it" to p[it] }
        val r = colorMapFor(listOf("zz") + custom.keys, custom)
        assertEquals(p[0], r["zz"])
    }

    @Test fun map_twoDevices_sameCustomColor_allowed() {
        val r = colorMapFor(listOf("x", "y"), custom = mapOf("x" to p[5], "y" to p[5]))
        assertEquals(p[5], r["x"]); assertEquals(p[5], r["y"])
    }
}
```

- [ ] **Step 2: Прогнать, убедиться в падении**

Run: `export JAVA_HOME=/home/filatov/android/jdk-17.0.21+9 && ./gradlew testDebugUnitTest --tests 'com.locationshare.app.ui.ColorMapTest'`
Expected: FAIL — unresolved reference `PALETTE_COLORS` / `parseColorHex` / `colorHex` / параметр `custom`.

- [ ] **Step 3: Реализовать в Format.kt**

`PALETTE_COLORS = PALETTE.toList()`; `colorHex` через `"#%06X".format(c and 0xFFFFFF)`; `parseColorHex` — null/длина≠7/не `#`/не hex → null, иначе `(0xFF shl 24) or rgb`; `colorMapFor`: ids сортируются; устройства из `custom` получают свой цвет; остальным — палитра по порядку, пропуская `custom.values.toSet()`, циклично.

- [ ] **Step 4: Прогнать тесты**

Run: та же команда.
Expected: PASS (все тесты ColorMapTest).

- [ ] **Step 5: Коммит**

`git add app/src/main/java/com/locationshare/app/ui/Format.kt app/src/test/java/com/locationshare/app/ui/ColorMapTest.kt && git commit -m "цвет: hex-парсер и colorMapFor с кастомными цветами (пропуск занятых)"`

### Task 2: Слой данных — Prefs.customColor + поле color в Repo

**Files:**
- Modify: `app/src/main/java/com/locationshare/app/data/Prefs.kt`
- Modify: `app/src/main/java/com/locationshare/app/data/Repo.kt`

**Interfaces:**
- Consumes: ничего из Task 1 (слой данных хранит/возит строку `"#RRGGBB"` как есть).
- Produces (для Tasks 4, 5):
  - `Prefs.customColor: String?` — var, ключ SP `"color"`, null = авто
  - `Repo.DeviceState(…, val color: String? = null)`
  - `Repo.setColor(code: String, id: String, hex: String?)` — hex != null → `devices/{code}/{id}/current/color` setValue; null → removeValue

- [ ] **Step 1: Prefs.customColor**

По образцу `lastUpdateCheck` (обычный var, без Flow — экран настроек хранит локальное состояние): get `sp.getString("color", null)`, set `sp.edit().putString/remove`.

- [ ] **Step 2: Repo — публикация и чтение**

- `DeviceState`: поле `color: String? = null` в конец.
- `publishCurrent`: payload собирается в `mutableMapOf`; при `Prefs.customColor != null` добавить `"color"` (current перезаписывается целиком — без этого поле стиралось бы).
- `observe`: `color = cur.child("color").getValue(String::class.java)`.
- `setColor(code, id, hex)`: как в Interfaces. Вызов до первой публикации точки создаст `current` без lat — `observe()` такое пропускает (`lat ?: continue`), вреда нет.

- [ ] **Step 3: Сборка + все тесты**

Run: `export JAVA_HOME=/home/filatov/android/jdk-17.0.21+9 && ./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Коммит**

`git add app/src/main/java/com/locationshare/app/data/Prefs.kt app/src/main/java/com/locationshare/app/data/Repo.kt && git commit -m "данные: кастомный цвет устройства — Prefs + поле color в Firebase current"`

### Task 3: UI-фикс — выбор из карточки кластера и видимость своего пина

**Files:**
- Modify: `app/src/main/java/com/locationshare/app/ui/MainScreen.kt` (groupCard, ~строка 275; MapArea update, ~строка 1281)

**Interfaces:**
- Consumes: ничего нового.
- Produces: ничего для других задач. Юнит-шва нет (лямбды Compose) — проверяется сборкой здесь и на железе в Task 6.

- [ ] **Step 1: Строка карточки группы выбирает устройство**

В `groupCard` у строки устройства `.clickable { focusId = d.id; focusTrigger++ }` → `selectedId = d.id; selectedGroup = null; focusId = d.id; focusTrigger++` (фокус камеры сохраняем).

- [ ] **Step 2: Пин выбранного устройства не прячется в кластере**

В `MapArea.update`: `placemarks[d.id]?.setVisible(d.id !in groupedIds)` → `…setVisible(d.id !in groupedIds || d.id == selectedId)`.

- [ ] **Step 3: Сборка**

Run: `export JAVA_HOME=/home/filatov/android/jdk-17.0.21+9 && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Коммит**

`git add app/src/main/java/com/locationshare/app/ui/MainScreen.kt && git commit -m "карточка группы выбирает устройство — трек доступен и для себя; пин выбранного не прячется в кластере"`

### Task 4: Цвет в UI — customColors() + оба вызова colorMapFor

**Files:**
- Modify: `app/src/main/java/com/locationshare/app/ui/Format.kt`
- Modify: `app/src/main/java/com/locationshare/app/ui/MainScreen.kt` (два вызова `colorMapFor`: MainScreen ~222, MapArea ~995)
- Test: `app/src/test/java/com/locationshare/app/ui/ColorMapTest.kt`

**Interfaces:**
- Consumes: `parseColorHex`, `colorMapFor(ids, custom)` из Task 1; `Repo.DeviceState.color` из Task 2.
- Produces (для Task 5): `internal fun customColors(devices: List<Repo.DeviceState>): Map<String, Int>` — только валидные кастомные.

- [ ] **Step 1: Падающий тест**

```kotlin
    @Test fun customColorsOfDevices_parsesSkipsGarbage() {
        val d = { id: String, c: String? -> com.locationshare.app.data.Repo.DeviceState(id, id, null, 0f, null, c) }
        val devs = listOf(d("a", "#43A047"), d("b", null), d("c", "мусор"))
        assertEquals(mapOf("a" to 0xFF43A047.toInt()), customColors(devs))
    }
```

- [ ] **Step 2: Прогнать, убедиться в падении**

Run: `./gradlew testDebugUnitTest --tests 'com.locationshare.app.ui.ColorMapTest'` (с JAVA_HOME).
Expected: FAIL — unresolved `customColors` / нет параметра `color`.

- [ ] **Step 3: Реализовать**

`Format.kt`: `customColors(devices)` = `buildMap { devices.forEach { parseColorHex(it.color)?.let { c -> put(it.id, c) } } }` (импорт `Repo` допустим — как в TrackAlphaTest). Оба вызова в MainScreen.kt: `remember(devices) { colorMapFor(devices.map { it.id }, customColors(devices)) }`. trackKey и pinColors уже завязаны на colorMap — трек/пин/чипы/карточки перекрасятся сами.

- [ ] **Step 4: Прогнать тесты + сборка**

Run: `./gradlew assembleDebug testDebugUnitTest` (с JAVA_HOME).
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 5: Коммит**

`git add app/src/main/java/com/locationshare/app/ui/Format.kt app/src/main/java/com/locationshare/app/ui/MainScreen.kt app/src/test/java/com/locationshare/app/ui/ColorMapTest.kt && git commit -m "цвет устройств из Firebase: colorMapFor с кастомными, перекраска пинов/чипов/трека"`

### Task 5: Настройки — палитра «Цвет на карте» + «Авто»

**Files:**
- Modify: `app/src/main/java/com/locationshare/app/ui/MainScreen.kt` (SettingsScreen, после поля имени ~627)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PALETTE_COLORS`, `colorHex` (Task 1); `Prefs.customColor`, `Repo.setColor` (Task 2).

- [ ] **Step 1: Строки**

`settings_color_label` = «Цвет на карте», `settings_color_auto` = «Авто».

- [ ] **Step 2: UI выбора**

После `OutlinedTextField` имени: подпись `settings_color_label` (стиль как у `settings_admin_section`) и `FlowRow` (spacedBy(8.dp), paddings как у соседних блоков):
- «Авто»: элемент с иконкой `Icons.Filled.Check` (material-icons-core) при `picked == null`, иначе пустое место той же ширины; тап → `picked = null; Prefs.customColor = null; Repo.setColor(group, myId, null)`.
- 16 кружков `PALETTE_COLORS`: `Box` 40.dp, `CircleShape`, фон `Color(c)`, выбран — `border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)`; тап → `picked = colorHex(c); Prefs.customColor = colorHex(c); Repo.setColor(group, myId, colorHex(c))`.
- `var picked by remember { mutableStateOf(Prefs.customColor) }`.

- [ ] **Step 3: Сборка**

Run: `./gradlew assembleDebug` (с JAVA_HOME).
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Коммит**

`git add app/src/main/java/com/locationshare/app/ui/MainScreen.kt app/src/main/res/values/strings.xml && git commit -m "настройки: выбор цвета на карте — палитра + Авто, мгновенная публикация в группу"`

### Task 6: Проверка на железе + релиз

**Files:**
- Modify: `app/build.gradle.kts` (версия)

- [ ] **Step 1: Установка debug-сборки на телефон**

`/mnt/c/Users/afilatov/platform-tools/adb.exe install -r app/build/outputs/apk/debug/app-debug.apk` (на MIUI может требовать подтверждение на экране).

- [ ] **Step 2: Проверка трека из кластера**

Открыть приложение → тап по кластерному кружку → в карточке группы тап по строке «Папа (Вы)» → появляется карточка устройства, пин видим, рядом с пином зелёный отрезок трека (пиксельная проверка `/tmp/opencode/pngcount.py` на 0x43A047 при необходимости).

- [ ] **Step 3: Проверка цвета**

Настройки → «Цвет на карте» → выбрать, например, оранжевый → пин/чип/трек перекрасились; `curl …/devices/ed945070.json` показывает `color`; на другом устройстве группы (или после перезапуска) цвет тот же. Вернуть прежний выбор.

- [ ] **Step 4: Релиз**

Версия: `ГГГГ.ММ.П` по текущей дате, `versionCode = ГГГГММ*100 + П` → `assembleRelease` → коммит «ГГГГ.ММ.П: свой цвет устройства + свой трек доступен из карточки группы» → `git push` → `gh release create <версия> app/build/outputs/apk/release/*.apk --notes-file <файл>` (заметки: два пункта из этого шага).
