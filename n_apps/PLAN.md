# NApp System — Current State & Upgrade Plan

## Table of Contents

- [Part A: Current State](#part-a-current-state)
- [Part B: Upgrade Plan](#part-b-upgrade-plan)

---

# Part A: Current State

## Architecture Overview

```
n_apps module
├── schema/          Schema definitions (data classes)
├── expression/      Expression language (tokenizer → parser → evaluator)
├── runtime/         State store + action executor + expression resolver
├── renderer/        Compose UI rendering (component registry + 4 renderer files)
├── agent/           AI tool-calling agent (LLM loop + tool executor + tool definitions)
├── network/         API clients (LLMClient for OpenAI-compatible APIs)
├── workspace/       Project CRUD (filesystem-based, 4 JSON files per project)
├── vcs/             Version control (full snapshots, linear history)
├── data/            DataStore preferences (API key, URL, model)
└── ui/              Compose screens (NAppScreen, ProjectPicker, VersionHistory, ToolActivity)
```

---

## 1. Schema Layer

### Files

| File                     | Purpose                                               |
|--------------------------|-------------------------------------------------------|
| `schema/NAppManifest.kt` | App identity (id, name, version, description, author) |
| `schema/NAppState.kt`    | State field definitions (type + default + nested)     |
| `schema/NAppUI.kt`       | Component tree + layout sections                      |
| `schema/NAppActions.kt`  | Named action definitions                              |
| `schema/NAppParser.kt`   | Parses 3 JSON files → validated `NApp` object         |

### State Types (5)

| Type      | Default | Notes                            |
|-----------|---------|----------------------------------|
| `number`  | `0`     | Double precision                 |
| `string`  | `""`    |                                  |
| `boolean` | `false` |                                  |
| `array`   | `[]`    | Supports `items` sub-schema      |
| `object`  | `{}`    | Supports `properties` sub-schema |

Computed fields: `"computed": { "doubled": "count * 2" }` — expression strings re-evaluated on
access.

### Component Types (26)

**Input (8)** — bind to state via `stateKey`:

| Type           | Key Fields                                                       |
|----------------|------------------------------------------------------------------|
| `text_input`   | label, placeholder, stateKey, disabled                           |
| `text_area`    | label, placeholder, stateKey, maxLines, disabled                 |
| `number_input` | label, stateKey, min, max, step, disabled                        |
| `slider`       | label, stateKey, min, max, step, disabled                        |
| `dropdown`     | label, stateKey, options [{label, value}], placeholder, disabled |
| `checkbox`     | label, stateKey, disabled                                        |
| `radio_group`  | label, stateKey, options [{label, value}], disabled              |
| `switch`       | label, stateKey, disabled                                        |

**Display (8)** — read-only content:

| Type         | Key Fields                                                       |
|--------------|------------------------------------------------------------------|
| `text`       | content, style (h1/h2/h3/body/caption/label/overline), maxLines  |
| `markdown`   | content (renders as plain text — no actual markdown parsing yet) |
| `code_block` | content, language (unused — monospace display only)              |
| `divider`    | —                                                                |
| `spacer`     | height (dp)                                                      |
| `card`       | title, subtitle, headerIcon, children                            |
| `progress`   | progress, maxProgress, style (linear/circular), indeterminate    |
| `icon`       | icon (material name), height                                     |

**Action (4)** — trigger actions via `actionId`:

| Type           | Key Fields                                                          |
|----------------|---------------------------------------------------------------------|
| `button`       | text, actionId, icon, style (primary/outlined/text/tonal), disabled |
| `icon_button`  | icon, actionId, disabled                                            |
| `fab`          | text, icon, actionId, disabled                                      |
| `submit_group` | children (button IDs)                                               |

**Layout (6)** — structure via `children`:

| Type          | Key Fields                                         |
|---------------|----------------------------------------------------|
| `row`         | children, spacing, alignment (center/top/bottom)   |
| `column`      | children, spacing, padding                         |
| `grid`        | children, columns, spacing                         |
| `tabs`        | tabs [{id, label, icon, components}]               |
| `accordion`   | sections [{id, label, icon, expanded, components}] |
| `scroll_area` | children, maxHeight                                |

**Common fields on all components:**

- `id` (required, unique)
- `type` (required)
- `visible` (expression string)
- `disabled` (expression string)

### Action Types (12)

**State (4):**

| Type           | Fields                                |
|----------------|---------------------------------------|
| `set_state`    | target, value (literal or `{{expr}}`) |
| `toggle_state` | target                                |
| `increment`    | target, amount (default 1)            |
| `decrement`    | target, amount (default 1)            |

**Control (3):**

| Type          | Fields                                               |
|---------------|------------------------------------------------------|
| `batch`       | actions [action_ids] — runs all                      |
| `sequence`    | actions [action_ids] — runs in order                 |
| `conditional` | condition (expr), then (action_id), else (action_id) |

**Array (4):**

| Type           | Fields               |
|----------------|----------------------|
| `array_push`   | target, item         |
| `array_remove` | target, index        |
| `array_clear`  | target               |
| `array_set`    | target, index, value |

**System (1):**

| Type    | Fields                                           |
|---------|--------------------------------------------------|
| `toast` | message (supports `{{}}`), duration (short/long) |

**AI (1):**

| Type      | Fields                                                |
|-----------|-------------------------------------------------------|
| `ai_call` | prompt (supports `{{}}`), resultTarget, loadingTarget |

---

## 2. Expression Engine

Full recursive-descent expression language.

**Operators:** `+` `-` `*` `/` `%` `==` `!=` `<` `>` `<=` `>=` `&&` `||` `!` `?:` `??`

**Literals:** numbers, strings (single/double quotes), booleans, null, arrays

**Variables:** Direct state access (`count`, `name`, `items`)

**Property access:** `obj.field`, `arr[0]`, `str[2]`

**String methods (10):** `length`, `trim()`, `toUpperCase()`, `toLowerCase()`, `includes(s)`,
`replace(a,b)`, `substring(start,end)`, `split(sep)`, `startsWith(s)`, `endsWith(s)`

**Array methods (5):** `length`, `includes(v)`, `indexOf(v)`, `join(sep)`, `slice(start,end)`

**Math functions (6):** `abs()`, `ceil()`, `floor()`, `round()`, `min(a,b)`, `max(a,b)`

**Conversions (2):** `toString()`, `toNumber()`

**Template syntax:** `{{expression}}` in content/visible/disabled strings. Supports interpolation:
`"Hello {{name}}, you have {{count}} items"`

---

## 3. Runtime

| File                            | Purpose                                                     |
|---------------------------------|-------------------------------------------------------------|
| `runtime/NAppStateStore.kt`     | `SnapshotStateMap`-backed state with computed fields        |
| `runtime/ActionExecutor.kt`     | Dispatches action types, resolves expressions in values     |
| `runtime/ExpressionResolver.kt` | Resolves `{{expr}}` templates to string/boolean/any         |
| `runtime/NAppRuntime.kt`        | Orchestrator: loads NApp, wires state + actions + callbacks |

---

## 4. Renderer

| File                                       | Purpose                                                      |
|--------------------------------------------|--------------------------------------------------------------|
| `renderer/NAppRenderer.kt`                 | Top-level composable: renders component tree with visibility |
| `renderer/ComponentRegistry.kt`            | Type → renderer mapping (registered at init)                 |
| `renderer/IconResolver.kt`                 | 60+ material icon name → `ImageVector` mapping               |
| `renderer/components/InputComponents.kt`   | 8 input renderers                                            |
| `renderer/components/DisplayComponents.kt` | 8 display renderers                                          |
| `renderer/components/ActionComponents.kt`  | 4 action renderers                                           |
| `renderer/components/LayoutComponents.kt`  | 6 layout renderers                                           |

### Icons (60+)

add, arrow_back, arrow_forward, arrow_downward, arrow_upward, bookmark, build, call, camera, check,
check_circle, clear, close, code, content_copy, copy, create, date_range, delete, done, download,
edit, email, error, favorite, favorite_border, filter_list, folder, folder_open, home, image, info,
keyboard_arrow_down, keyboard_arrow_up, link, list, location_on, lock, menu, more_vert,
notifications, pause, person, phone, place, play_arrow, refresh, remove, search, send, settings,
share, shopping_cart, star, stop, thumb_up, upload, visibility, visibility_off, warning

---

## 5. Agent System

### Agent Loop (`NAppToolAgent`)

```
1. User message → add to history
2. Build messages: [system] + [history]
3. Call LLM via Chat Completions API (with tools)
4. If no tool_calls → return final text (EXIT)
5. Execute each tool → add results to history
6. If iterations >= 10 → force stop
7. GOTO 2
```

Config: MAX_ITERATIONS=10, MAX_HISTORY_MESSAGES=60

### Tools (15)

| #  | Tool                 | Category  | Params                                               |
|----|----------------------|-----------|------------------------------------------------------|
| 1  | `read_file`          | File      | file: state.json/ui.json/actions.json/manifest.json  |
| 2  | `write_file`         | File      | file, content (complete JSON with wrapper key)       |
| 3  | `list_components`    | Component | —                                                    |
| 4  | `add_component`      | Component | component: {id, type, ...}                           |
| 5  | `update_component`   | Component | id, updates: {...}                                   |
| 6  | `remove_component`   | Component | id                                                   |
| 7  | `add_action`         | Action    | id, action: {type, ...}                              |
| 8  | `update_action`      | Action    | id, updates: {...}                                   |
| 9  | `remove_action`      | Action    | id                                                   |
| 10 | `set_state_field`    | State     | key, field: {type, default}                          |
| 11 | `remove_state_field` | State     | key                                                  |
| 12 | `commit`             | VCS       | message                                              |
| 13 | `list_versions`      | VCS       | —                                                    |
| 14 | `revert`             | VCS       | version (int)                                        |
| 15 | `get_schema_help`    | Info      | topic? (components/actions/state/expressions/layout) |

### Validations

- `write_file` validates JSON structure (correct top-level keys: `components`, `schema`, `actions`)
- After write, runs `NAppParser.parse()` and returns warnings to the LLM
- Component CRUD checks for duplicate IDs, missing IDs
- Action CRUD checks for duplicate/missing action IDs

### System Prompt

- Full schema reference (component fields, action fields, state format)
- Build strategy (prefer `write_file` for new apps, CRUD for small edits)
- Rules: unique IDs, flat values, style as string preset, commit after changes

---

## 6. Network

| File                               | Object            | Purpose                                   |
|------------------------------------|-------------------|-------------------------------------------|
| `network/OpenAIResponsesClient.kt` | `LLMClient`       | OpenAI-compatible Chat Completions client |
| `network/SarvamApiClient.kt`       | `SarvamApiClient` | Legacy Sarvam client (dead code)          |

**LLMClient features:**

- `POST {baseUrl}/v1/chat/completions`
- Native function/tool calling (`tool_choice: "auto"`)
- Auto-retry on HTTP 429 (up to 3 retries, parses "try again in Xs" delay)
- Auto-retry on IOException (exponential backoff)
- Configurable: apiKey, baseUrl, model

---

## 7. Workspace + VCS

**Workspace** (`workspace/`):

- Filesystem: `context.filesDir/napp_projects/<uuid>/`
- 4 files per project: manifest.json, state.json, ui.json, actions.json
- Project index: `napp_project_index.json`
- CRUD: list, create, open, save, delete

**VCS** (`vcs/`):

- Filesystem: `<project>/versions/v001.json, v002.json, ...`
- Full snapshots (not diffs)
- Linear integer versioning
- Operations: commit, listHistory, getVersion, revert, diff

---

## 8. UI Screens

| File                        | Purpose                                                 |
|-----------------------------|---------------------------------------------------------|
| `ui/NAppScreen.kt`          | Main IDE: canvas/code/split modes, agent chat, settings |
| `ui/NAppViewModel.kt`       | ViewModel: workspace + VCS + agent + runtime state      |
| `ui/ProjectPicker.kt`       | Bottom sheet: project list, create, delete              |
| `ui/VersionHistorySheet.kt` | Bottom sheet: version list, commit, revert              |
| `ui/ToolActivityPanel.kt`   | Tool execution log with progress                        |

**IDE Modes:** CANVAS (preview), CODE (3-file JSON editor), SPLIT (both)

**Settings:** API URL, Model, API Key (DataStore persisted)

---

## 9. Dead Code

| File                         | Status                                                                     |
|------------------------------|----------------------------------------------------------------------------|
| `network/SarvamApiClient.kt` | Replaced by `LLMClient` — can delete                                       |
| `agent/NAppAgent.kt`         | Legacy single-shot agent — can delete                                      |
| `agent/ToolCallParser.kt`    | XML `<tool_call>` parser — replaced by native function calling, can delete |

---

---

# Part B: Upgrade Plan

## Phase 1: New Component Types

### 1.1 Input Components (+6 = 14 total)

| Type           | Fields                                            | Renders As                               |
|----------------|---------------------------------------------------|------------------------------------------|
| `date_picker`  | label, stateKey, format?                          | Date field → opens MaterialDatePicker    |
| `time_picker`  | label, stateKey, is24Hour?                        | Time field → opens TimePickerDialog      |
| `color_picker` | label, stateKey                                   | Color swatch → opens color palette sheet |
| `rating`       | label, stateKey, maxStars (default 5), allowHalf? | Star row (tap to rate)                   |
| `stepper`      | label, stateKey, min, max, step                   | \[−\] value \[+\] row                    |
| `search_input` | label, placeholder, stateKey, icon?               | Text field with search icon + clear      |

### 1.2 Display Components (+6 = 14 total)

| Type          | Fields                                                              | Renders As                          |
|---------------|---------------------------------------------------------------------|-------------------------------------|
| `image`       | src (URL or expression), width?, height?, fit (cover/contain/fill)? | AsyncImage (Coil)                   |
| `badge`       | content, color? (primary/error/success)                             | Small pill label                    |
| `chip`        | text, icon?, selected? (expression), stateKey?, actionId?           | Material chip (input/filter/action) |
| `avatar`      | icon? or src? or text?, size? (small/medium/large)                  | Circular icon/image/initials        |
| `alert`       | content, severity (info/warning/error/success), icon?               | Colored banner with icon            |
| `empty_state` | title, subtitle?, icon?, actionId?, actionText?                     | Centered placeholder illustration   |

### 1.3 Layout Components (+4 = 10 total)

| Type            | Fields                                                   | Renders As                               |
|-----------------|----------------------------------------------------------|------------------------------------------|
| `lazy_column`   | stateKey (array), itemTemplate (component id), spacing?  | RecyclerView-style list from array state |
| `bottom_sheet`  | children, peekHeight?, title?                            | ModalBottomSheet container               |
| `dialog`        | children, title?, visible (expression), onDismissAction? | AlertDialog container                    |
| `swipe_dismiss` | children, onSwipeAction?                                 | SwipeToDismiss wrapper                   |

### 1.4 Action Components (+1 = 5 total)

| Type               | Fields                                    | Renders As                            |
|--------------------|-------------------------------------------|---------------------------------------|
| `segmented_button` | stateKey, options [{label, value, icon?}] | Material SegmentedButton toggle group |

---

## Phase 2: New Action Types

### 2.1 State Actions (+2 = 6 total)

| Type           | Fields                    | Purpose                                      |
|----------------|---------------------------|----------------------------------------------|
| `set_multiple` | fields: {key: value, ...} | Set multiple state fields atomically         |
| `reset_state`  | targets?: [keys]          | Reset fields to defaults (all if no targets) |

### 2.2 Array Actions (+3 = 7 total)

| Type            | Fields                          | Purpose                         |
|-----------------|---------------------------------|---------------------------------|
| `array_insert`  | target, index, item             | Insert at specific position     |
| `array_sort`    | target, key?, order (asc/desc)? | Sort array (by key for objects) |
| `array_reverse` | target                          | Reverse array in-place          |

### 2.3 Navigation Actions (+2 = 2 new)

| Type       | Fields                | Purpose                      |
|------------|-----------------------|------------------------------|
| `navigate` | page (string)         | Switch to named page/tab     |
| `open_url` | url (supports `{{}}`) | Open URL in external browser |

### 2.4 System Actions (+4 = 5 total)

| Type             | Fields                            | Purpose                    |
|------------------|-----------------------------------|----------------------------|
| `copy_clipboard` | text (supports `{{}}`)            | Copy to clipboard          |
| `share`          | text (supports `{{}}`), title?    | Android share sheet        |
| `vibrate`        | pattern? (short/medium/long)      | Haptic feedback            |
| `delay`          | duration (ms), action (action_id) | Execute action after delay |

### 2.5 HTTP Actions (+1 = 1 new)

| Type           | Fields                                                                              | Purpose                              |
|----------------|-------------------------------------------------------------------------------------|--------------------------------------|
| `http_request` | url, method (GET/POST), body?, headers?, resultTarget, errorTarget?, loadingTarget? | Make API call, store result in state |

### 2.6 Control Actions (+1 = 4 total)

| Type   | Fields                                                   | Purpose                |
|--------|----------------------------------------------------------|------------------------|
| `loop` | times (number or expr), action (action_id), indexTarget? | Execute action N times |

---

## Phase 3: New Expression Features

### 3.1 New String Methods

| Method                | Example                           |
|-----------------------|-----------------------------------|
| `repeat(n)`           | `"abc".repeat(3)` → `"abcabcabc"` |
| `padStart(len, char)` | `"5".padStart(2, "0")` → `"05"`   |
| `padEnd(len, char)`   | `"hi".padEnd(5, ".")` → `"hi..."` |
| `charAt(n)`           | `"hello".charAt(1)` → `"e"`       |

### 3.2 New Array Methods

| Method         | Example                          |
|----------------|----------------------------------|
| `reverse()`    | `[3,1,2].reverse()` → `[2,1,3]`  |
| `sort()`       | `[3,1,2].sort()` → `[1,2,3]`     |
| `filter(expr)` | Future — needs lambda support    |
| `map(expr)`    | Future — needs lambda support    |
| `find(expr)`   | Future — needs lambda support    |
| `last()`       | `[1,2,3].last()` → `3`           |
| `first()`      | `[1,2,3].first()` → `1`          |
| `flat()`       | `[[1,2],[3]].flat()` → `[1,2,3]` |
| `contains(v)`  | Alias for `includes()`           |
| `isEmpty()`    | `[].isEmpty()` → `true`          |

### 3.3 New Utility Functions

| Function                    | Example                                  |
|-----------------------------|------------------------------------------|
| `len(x)`                    | `len("hello")` → `5`, `len([1,2])` → `2` |
| `type(x)`                   | `type(42)` → `"number"`                  |
| `random()`                  | `random()` → 0.0–1.0                     |
| `randomInt(min, max)`       | `randomInt(1, 10)` → 1–10                |
| `now()`                     | `now()` → current timestamp (ms)         |
| `formatNumber(n, decimals)` | `formatNumber(3.14159, 2)` → `"3.14"`    |
| `JSON.parse(s)`             | Parse JSON string to value               |
| `JSON.stringify(v)`         | Value to JSON string                     |
| `if(cond, a, b)`            | Function-style ternary                   |

---

## Phase 4: New Agent Tools

### 4.1 Component Tools (+4 = 7 total)

| Tool                  | Params                            | Purpose                                |
|-----------------------|-----------------------------------|----------------------------------------|
| `duplicate_component` | id, newId                         | Clone component with new ID            |
| `move_component`      | id, position (index)              | Reorder in components array            |
| `find_components`     | type?, hasStateKey?, hasActionId? | Search/filter components               |
| `bulk_update`         | updates: [{id, changes}]          | Update multiple components in one call |

### 4.2 Utility Tools (+3 = 3 new)

| Tool                | Params                                      | Purpose                                                |
|---------------------|---------------------------------------------|--------------------------------------------------------|
| `validate_project`  | —                                           | Run full validation, return all errors/warnings        |
| `get_project_stats` | —                                           | Component count, action count, state field count, etc. |
| `rename_id`         | type (component/action/state), oldId, newId | Rename with reference updates                          |

---

## Phase 5: Multi-Page Support

### 5.1 Schema Changes

Add `pages` concept to ui.json:

```json
{
  "pages": {
    "home": {
      "label": "Home",
      "icon": "home",
      "components": [
        "title",
        "btn_row",
        ...
      ]
    },
    "settings": {
      "label": "Settings",
      "icon": "settings",
      "components": [
        "settings_title",
        ...
      ]
    }
  },
  "navigation": {
    "type": "bottom_nav",
    "default": "home"
  },
  "components": [
    ...
  ]
}
```

### 5.2 Navigation

- Internal state field `__currentPage` managed by runtime
- `navigate` action switches pages
- Bottom nav / tab nav / drawer rendered from `navigation` config
- Page transitions with shared element support

---

## Phase 6: Theming

### 6.1 Theme Schema

Add to manifest.json:

```json
{
  "theme": {
    "colorScheme": "dynamic"
    |
    "custom",
    "colors": {
      "primary": "#6200EE",
      "secondary": "#03DAC6",
      "background": "#FFFFFF",
      "surface": "#FFFFFF",
      "error": "#B00020"
    },
    "typography": {
      "fontFamily": "default"
      |
      "serif"
      |
      "monospace"
    },
    "shape": {
      "cornerRadius": 6
    }
  }
}
```

### 6.2 Style Prop Upgrade

Allow `style` on components to be either a string preset OR an object:

```json
{
  "style": "h1"
}
OR
{
  "style": {
    "fontSize": 24,
    "color": "#FF0000",
    "fontWeight": "bold"
  }
}
```

---

## Phase 7: Agent Improvements

### 7.1 Streaming Responses

- Switch LLMClient to SSE streaming for real-time text display
- Show partial text in chat as it arrives
- Stream tool call arguments for faster perceived response

### 7.2 System Prompt Enhancements

- Auto-include current project state summary in system prompt
- Include list of existing component IDs, state fields, action IDs
- Include recent validation errors so the agent knows what to fix

### 7.3 Template Library

- Pre-built app templates the agent can start from:
    - Counter, Todo List, Calculator, Timer, Quiz, Notes, Settings Panel
- `load_template` tool to initialize from templates

### 7.4 Conversation Memory

- Persist agent conversation history per project
- Resume where you left off after app restart

---

## Implementation Priority

| Priority | Phase         | Scope                                                         | Effort |
|----------|---------------|---------------------------------------------------------------|--------|
| **P0**   | Cleanup       | Delete dead code (SarvamApiClient, NAppAgent, ToolCallParser) | Small  |
| **P1**   | Phase 1.1-1.2 | Input + display components (12 new)                           | Medium |
| **P1**   | Phase 2.1-2.4 | New action types (13 new)                                     | Medium |
| **P1**   | Phase 4.1-4.2 | New agent tools (7 new)                                       | Medium |
| **P2**   | Phase 1.3     | Layout components (lazy_column, dialog, bottom_sheet)         | Large  |
| **P2**   | Phase 2.5     | HTTP action (http_request)                                    | Medium |
| **P2**   | Phase 3       | Expression upgrades                                           | Medium |
| **P2**   | Phase 5       | Multi-page support                                            | Large  |
| **P3**   | Phase 6       | Theming                                                       | Medium |
| **P3**   | Phase 7       | Agent improvements (streaming, templates, memory)             | Large  |

---

## File Count Summary

| Layer            | Current               | After All Phases          |
|------------------|-----------------------|---------------------------|
| Schema files     | 5                     | 6 (+page schema)          |
| Expression files | 4                     | 4 (enhanced)              |
| Runtime files    | 4                     | 5 (+page navigator)       |
| Renderer files   | 7                     | 11 (+new component files) |
| Agent files      | 8 → 5 (after cleanup) | 7                         |
| Network files    | 2 → 1 (after cleanup) | 1 (enhanced)              |
| Workspace files  | 2                     | 3 (+templates)            |
| VCS files        | 2                     | 2                         |
| Data files       | 1                     | 1                         |
| UI files         | 4                     | 5 (+page editor)          |
| **Total**        | **39 → 33**           | **~45**                   |

---

## Current Numbers

| What                 | Count                    |
|----------------------|--------------------------|
| Component types      | 26                       |
| Action types         | 12                       |
| State value types    | 5                        |
| Expression operators | 17                       |
| Expression functions | 24 (methods + functions) |
| Agent tools          | 15                       |
| Material icons       | 60+                      |
| Renderer files       | 7                        |

## After All Phases

| What                 | Count         |
|----------------------|---------------|
| Component types      | **43** (+17)  |
| Action types         | **25** (+13)  |
| State value types    | 5             |
| Expression operators | 17            |
| Expression functions | **40+** (+16) |
| Agent tools          | **22** (+7)   |
| Material icons       | 60+           |
| Renderer files       | 11 (+4)       |
