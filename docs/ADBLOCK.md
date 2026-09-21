# Shields: ad and tracker blocking

Shields is AA Browser's built-in content blocker. It understands the uBlock Origin / Adblock Plus
static filter syntax, so it can use the same public filter lists as desktop ad blockers, and it runs
entirely inside the app: no VPN, no proxy, no extension.

## What it does

| Layer | Where | What |
|---|---|---|
| Network blocking | `WebViewClient.shouldInterceptRequest` (every tab) and a `ServiceWorkerClientCompat` for service-worker fetches | Sub-resource requests that match a blocking rule get a typed empty response (empty script / stylesheet, 1x1 GIF, or 204). Main-frame navigations are never blocked. |
| Cosmetic filtering | `assets/adblock/cosmetic-runtime.js`, registered with `WebViewCompat.addDocumentStartJavaScript` so it runs in every frame before any page script | Site-specific `##` rules and the un-keyed generic rules are applied before first paint through a constructed stylesheet. Generic rules keyed by a class or id are fetched only for tokens that actually appear on the page (initial scan + `MutationObserver`, batched and capped). |
| Scriptlets | `assets/adblock/ubo-scriptlets.js`, same document-start mechanism | The upstream uBlock Origin scriptlet registry, bundled from the vendored sources in `third_party/ublock`. Only bundled rules and the official uBO lists may invoke *trusted* scriptlets. |

Both page-side scripts talk to the native side through one synchronous JavaScript interface
(`__aabrowserScriptletBridge`). Its calls run on WebView's JavaBridge thread, read an immutable
engine snapshot and are size-capped, so a page cannot stall the UI thread or make the bridge do
unbounded work. The bridge only ever reveals public filter-list data.

## Supported filter syntax

Network rules: `||host^`, `|start`, `end|`, `*`, `^`, `/regex/`, plain hosts-file lines, `@@`
exceptions, `$third-party`/`$3p`/`$1p`, `$domain=`/`$from=`, resource types (`script`, `image`,
`stylesheet`, `font`, `media`, `xhr`, `subdocument`, `other`, ...), `$match-case`, `$important`
(with uBO priority semantics), `$badfilter`, and the content exceptions `$document`, `$elemhide`,
`$generichide`, `$specifichide` on `@@` rules. `$popup`, `$ping` and `$websocket` are accepted but
inert, because WebView never reports those request kinds.

Cosmetic rules: `##selector`, `#@#selector`, domain lists with `~` negation and `entity.*`.
Procedural operators (`:has-text()`, `:matches-css()`, `:style()`, ...) are not supported and are
skipped at parse time.

Not supported (the rule is dropped rather than widened): `$redirect`, `$removeparam`, `$csp`,
`$header`, `$denyallow`, `$to`, `$method`, `$replace`, `$permissions`.

## Filter lists

Six subscriptions are enabled by default, all served from the uBlock Origin `uAssets` mirror:
uBlock filters (Ads, Privacy, Unbreak, Quick fixes), EasyList and EasyPrivacy. A small offline
list ships in `assets/adblock/blocklist.txt` so blocking works before the first download.

`FilterListUpdateWorker` refreshes enabled lists every 7 days through WorkManager (network
connected, battery not low, exponential backoff) and on demand from Settings. Downloads are
conditional (`ETag` / `If-Modified-Since`), capped at 16 MB, written to a temp file and moved into
place atomically, so the last good copy is always available offline. Users can add their own HTTPS
lists in Settings; custom lists never get trusted-scriptlet rights.

Parsed rules are compiled into an immutable `FilterEngine` and cached as a binary snapshot under
`files/adblock/`; the snapshot is invalidated by the app version, the bundled list and the
subscription state.

## Controls

- **Settings → Shields**: global on/off, blocked-this-session counter, manage / add / update lists.
- **Browser menu → Shields button**: switches blocking off or on for the current site (host and its
  subdomains) and reloads. Stored alongside the other per-site lists in `BrowserPreferences`.
- `@@||site^$document` rules from the lists switch blocking and cosmetic filtering off for a site
  the same way.

## Development

- Unit tests: `./gradlew testDebugUnitTest` (`FilterEngineTest`, `RemoteFilterListManagerTest`).
- Cosmetic runtime: `NODE_PATH=<dir with node_modules/jsdom> node scripts/test_cosmetic_runtime.cjs`.
- Scriptlet registry: `scripts/generate_ubo_scriptlets.sh` rebuilds `ubo-scriptlets.js` from
  `third_party/ublock` (esbuild via `npx`); `node scripts/test_ubo_scriptlet_compatibility.cjs`
  checks it against the pinned upstream revision. The Gradle `test` task runs that check only when
  Node.js is on the PATH.
- The bundled uBO sources are GPL-3.0 (`third_party/ublock/LICENSE.txt`), the same licence as AA
  Browser. Update `third_party/ublock/README.md` when bumping the revision.

## Credits

The filter engine, list manager and scriptlet runtime were written by
[MaoMaoCake](https://github.com/MaoMaoCake/AABrowser), building on
[Yash-v-maurya](https://github.com/Yash-v-maurya/AABrowser)'s first Shields commit, and were
imported with their authorship preserved. The generic-cosmetic pipeline, `$important` / content
exception handling, WorkManager scheduling, service-worker filtering, the per-site switch and the
parsing hardening were added on top.
