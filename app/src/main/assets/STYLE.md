# Asset HTML style guide

These two files (`server_webui.html`, `server_docs.html`) are the bundled UI for the embedded HTTP server. The `:server` process serves them as static strings at `/`, `/webui`, and `/docs`. **They run in third-party browsers on the LAN — they must be self-contained, fast, and indistinguishable in feel from the rest of ToolNeuron.**

## Constraints (non-negotiable)

- **Self-contained.** No CDN fonts, no external JS, no `<link rel="stylesheet">` to anything off-device. Every byte is in the file.
- **No build step.** These are hand-edited HTML — no Tailwind, no preprocessor, no bundler.
- **Vanilla ES5/ES2017.** No JSX, no TypeScript, no top-level await outside `async` functions. Targets every browser that runs on a modern phone or workstation.
- **Single file.** Everything (HTML + CSS + JS) lives in one `<style>` and one `<script>` tag.
- **Mobile-first.** Tested at 360 px wide (a small phone) up to 1920 px (a desktop monitor). The drawer collapses below 880 px.
- **No telemetry.** No analytics, no fonts.googleapis.com, no error reporters. The whole point.
- **Loaded as an asset.** `RemoteServerService.loadWebUiHtml()` reads the file, hands it to native via `nativeSetWebUiHtml(html)`. AIDL has a 1 MB binder limit — keep each file under ~100 KB. Both currently sit ~37–42 KB, well within budget.

## Design tokens

Both files share the same token set. **Don't drift.** If you want to add a token, add it to both.

```css
/* Dark (default) */
--bg:#0A0A0A; --bg-soft:#141414; --surface:#181818; --surface-2:#1F1F1F;
--fg:#F5F5F4; --fg-dim:#D4D4D8; --muted:#A1A1AA;
--border:#27272A; --border-2:#3F3F46;
--accent:#3FE08C; --accent-on:#0A0A0A;
--accent-soft:rgba(63,224,140,0.10); --accent-edge:rgba(63,224,140,0.28);
--code-bg:#161616;

/* Light (auto via @media (prefers-color-scheme: light)) */
--bg:#FAFAF9; --surface:#FFFFFF; --fg:#0A0A0A; --muted:#71717A;
--border:#E4E4E7; --accent:#10A656; --accent-on:#FFFFFF;
--code-bg:#F4F4F6;
```

Both palettes are aligned with the marketing site (`/home/home/WebstormProjects/ToolNeuron-Web`). If you change the website palette, update these too — the user notices.

### Radii

```css
--r-xs:6px; --r-sm:8px; --r-md:10px; --r-lg:14px;
```

Use `--r-sm` for buttons and inputs, `--r-md` for code blocks and small cards, `--r-lg` for the composer, modals, and message bubbles. Pills are `999px`.

### Type scale

- Body: 15px / 1.5
- Code: 12.5px (mono) / 1.55
- Sidebar items: 14px
- Headings (docs): clamp(28px, 4.5vw, 36px) for h1.title, 20px for h2, 15px for h3
- Headings (chat bubble): 19/17/15
- Eyebrow / labels: 11px mono uppercase, letter-spacing 0.06em or 0.16em

### Fonts

System stack only:
- Sans: `ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif`
- Mono: `ui-monospace, "SF Mono", Menlo, Consolas, monospace`

Don't `@import` web fonts. The asset is loaded by clients on every platform; system fonts render instantly with zero network cost.

## Layout primitives

### `.app` grid (both files)

- Desktop: `grid-template-columns: 280px 1fr` — fixed sidebar, fluid main.
- Mobile (< 880 px): single column. Sidebar becomes a fixed-position drawer slid in by `.sidebar.open`.
- A full-screen `.scrim` dims the rest when the drawer is open. Tapping closes it.

### Sidebar

- Brand block at the top (`.brand`), with `.brand-dot` (8 px accent dot with a soft halo) + brand name + meta line.
- Primary action ("New chat" in webui, group headings in docs).
- Scrollable list (`.chat-list` / `.sidebar a`).
- Footer pinned to the bottom in webui (`.sidebar-footer`).

### Main column

- Sticky `.topbar` with backdrop-blur. Includes hamburger (mobile only), title, status pill, secondary actions.
- Scrollable content area (`.messages` for chat, `.main` for docs).
- Composer (chat) or pager (docs) at the bottom.

### Status pill (webui)

`.conn` is a mono-uppercase pill. `.dot` flips between `--danger` (red) and `--accent` (green) on `.live`. The active state has an accent-soft halo around the dot.

### Method badge (docs)

`.method.get` is `--accent` (green). `.method.post` is `--warn` (orange). Method labels are 10.5px uppercase with 0.06em letter-spacing.

## Component patterns

### Buttons

| Class                | Use                                          |
| -------------------- | -------------------------------------------- |
| `.btn`               | Default — surface bg, border, fg.            |
| `.btn.primary`       | Confirm / save — accent bg, accent-on fg.    |
| `.btn.danger`        | Destructive — red text and border on hover.  |
| `.btn.ghost`         | Tertiary — transparent. Pairs with `.danger` for destructive ghosts.   |
| `.icon-btn`          | 28×28 transparent icon-only.                 |
| `.menu-btn`          | 36×36 bordered, mobile-only hamburger.       |
| `.send-btn`          | 36×36 accent-bg send button.                 |
| `.send-btn.stop`     | Same shape, danger bg, when streaming.       |
| `.new-chat`          | Full-width primary in the sidebar.           |

All buttons have `transition: …120ms`. Disabled state: `opacity: 0.45; cursor: not-allowed`. Hover: `filter: brightness(1.06)` for accent, `background: var(--surface-2)` for outlined.

### Cards

`.suggestion`, `.pager a`, `.endpoint` — all share the same recipe:
- `border: 1px solid var(--border)`, `background: var(--surface)`, `border-radius: var(--r-md)` (or `--r-sm` for inline endpoints)
- Hover: `border-color: var(--accent-edge)` + `background: var(--surface-2)` + `transform: translateY(-1px)` (when it makes sense)

### Code blocks

`<pre>` has a monochrome background (`--code-bg`), 1px border, 12.5px mono. `.copy-btn` lives at top-right with `opacity: 0` until hover/focus-within. After click, the button gets `.copied` class (accent text + accent-edge border) and reverts after 1200 ms.

Syntax highlighting in docs uses literal `<span class="kw|str|num|comment">` wrapping. There's no runtime highlighter — the source is hand-marked. If you add a new code sample, mark it the same way the rest is marked.

### Modal

`.modal-scrim` is `display: none` by default, `.open` flips it to `display: flex` and centres the modal. The modal itself is capped at `max-width: 460px` and `max-height: calc(100vh - 32px)` so it doesn't overflow on small screens.

`Esc` closes any open modal (handled in the script).

### Eyebrow (docs)

Mono pill above each page title:

```html
<span class="eyebrow">Endpoints</span>
```

Same style across the site — accent text on accent-soft bg with accent-edge border. Marketing site uses an identical eyebrow pattern.

### Welcome screen (webui)

Three pieces, all centred:
1. `.welcome-eyebrow` — "Private · On-device".
2. `<h2>` — friendly question, 22–30px clamped.
3. `.suggestions` grid — 2 columns on desktop, 1 on phones.

Don't add a fourth element without testing on a 360 px-wide screen.

## Responsive rules

| Breakpoint | Behavior                                                                 |
| ---------- | ------------------------------------------------------------------------ |
| `≥ 880 px` | Two-column app, sidebar always visible.                                  |
| `< 880 px` | Drawer mode. Hamburger visible. Sidebar slides in.                       |
| `≤ 540 px` | Suggestions collapse to 1 column. Composer hint hides. Pager 1-up.       |

Always test at:
- 360 px (small Android phone, portrait)
- 768 px (iPad portrait — drawer still active here, by design)
- 1024 px (small laptop — desktop layout)
- 1440 px+ (large monitor)

## Motion

Cubic-bezier `(.22, 1, .36, 1)` for the drawer. Linear or default for hovers. Duration 100–220 ms. Anything longer feels sluggish on a chat UI.

`@media (prefers-reduced-motion: reduce)` collapses every animation to 0.01 ms. The `.cursor::after` blink becomes a static `opacity: 0.5`. Never delete this rule.

## Accessibility

- Every interactive element has either visible text or `aria-label`.
- `*:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }` — never `outline: none` without re-providing focus.
- `Esc` closes drawer/modal/settings.
- `aria-modal="true"` and `role="dialog"` on modals.
- `aria-live="polite"` on `.conn` and `.toast` so status changes get announced.
- `tabindex="0"` and Enter/Space handlers on chat list rows.
- `aria-hidden="true"` on every decorative SVG.

If you add an interactive element, give it a label, a focus state, and keyboard support. This is non-optional.

## Safe-area insets

Phones with notches/home bars need the layout to respect `env(safe-area-inset-*)`:

```css
--safe-bottom: env(safe-area-inset-bottom, 0px);
--safe-top:    env(safe-area-inset-top, 0px);
```

The composer adds `var(--safe-bottom)` to its bottom padding. The topbar adds `var(--safe-top)` via `padding-top: max(10px, var(--safe-top))`. Don't strip these.

## State persistence (webui only)

| Key             | Where             | Schema                                                           |
| --------------- | ----------------- | ---------------------------------------------------------------- |
| `tn.chats.v3`   | `localStorage`    | `{ chats: [{ id, title, messages: [{id, role, content}] }], activeId }` |
| `tn.token`      | `localStorage`    | The bearer token. `tn_sk_…`. Never POSTed; only sent in `Authorization` headers. |

**Bumping rules.** If the chats schema changes shape, bump `tn.chats.v3` to `tn.chats.v4`. Old data is silently dropped — that's the contract. Don't silently mutate v3 records to a new layout. Don't store anything sensitive beyond the token; this is a third-party browser.

## Behaviors that must not regress

These are the contracts the native HTTP server depends on. Don't change without bumping the server side too.

1. **The bearer token rides on every `/v1/*` request.** Always via `Authorization: Bearer …`. Never as a query param. Never logged.
2. **Streaming uses SSE.** Each chunk is `data: {json}\n\n`. Stream ends with `data: [DONE]`. The buffer split is on `\n\n` — don't change to single `\n`.
3. **Health is polled every 30 s.** `setInterval(checkHealth, 30000)`. Don't make this aggressive.
4. **Model id is fetched lazily** from `/v1/models` when missing, not at boot. Saves one round trip on first open.
5. **AbortController is the only way to stop a stream.** No "send a stop request" backchannel. The send button toggles between Send and Stop based on `state.streaming`.
6. **`closeNav()` runs on every chat select / new chat / suggestion click on mobile.** Otherwise the drawer hangs open over the new conversation.

## File layout

```
assets/
├── STYLE.md            ← this file
├── server_webui.html   ← chat client served at /, /webui, /index.html
└── server_docs.html    ← API reference served at /docs
```

`RemoteServerService.kt` (in `:server`) reads both via `assets.open(name).bufferedReader().readText()` on AIDL `start(configJson)`, then pushes them to native via `nativeSetWebUiHtml` / `nativeSetDocsHtml`. If a read fails, `FALLBACK_WEBUI_HTML` (a smaller inline string) is used instead — keep both files robust to partial reads (close every tag, terminate every script).

## Adding a new page (docs)

1. Add a sidebar `<a data-page="newslug">` row inside the appropriate `.group`.
2. Add a `<section class="page" data-page="newslug">` block with:
   - `<span class="eyebrow">Group name</span>`
   - `<h1 class="title">Page title</h1>`
   - `<p class="lead">One-sentence summary.</p>`
   - Body
   - `<div class="pager">` with prev / next.
3. Update prev/next in the surrounding pages so the chain stays linked.
4. The script auto-wires `data-page` clicks; no JS change needed.

## Adding a new modal (webui)

1. Add a `<div class="modal-scrim" id="myModal">` containing a `.modal` block.
2. Mark it `role="dialog" aria-modal="true" aria-labelledby="…"`.
3. Add `openMyModal()` and `closeMyModal()` and expose them on `window.*`.
4. Add the Esc-handler branch in the keydown listener.
5. Modal-footer buttons: ghost-danger destructive on left (with `margin-right: auto`), Cancel + Primary on right.

## What's intentionally not here

- No client-side syntax highlighter library — manual `<span>` markup is enough for the docs.
- No KaTeX/MathJax — the audience here is API users, not LLM users with formulas.
- No service worker — no offline use case for what's already served from the user's own device.
- No service-side analytics — see "no telemetry."
- No tooltips on buttons — `aria-label` + `title` is enough.

If you find yourself adding any of these, stop and ask whether the asset is the right place. The chat UI inside the app (`com.dark.tool_neuron.ui.screens.home_screen`) is the rich surface. These two files are the polite, lean, embeddable face of the same model.
