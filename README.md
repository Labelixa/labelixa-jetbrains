# Labelixa ZPL for JetBrains IDEs

See the Zebra **ZPL** label you are editing — rendered, without a printer —
and get the label checked with real line and column positions, in IntelliJ
IDEA, PyCharm, WebStorm, Rider, GoLand and the other JetBrains IDEs
(platform 2024.1 and newer).

## What it does

**Preview the label under your cursor.** Put the caret anywhere inside a
`^XA … ^XZ` block and run **Labelixa: Preview Label Under Cursor**
(`Ctrl+Alt+Shift+P`, also in the editor context menu and the Tools menu).
The block is rendered by the [Labelixa](https://labelixa.com) API at the
printer's real resolution and shown in the *Labelixa Preview* tool window.

If the cursor is *between* labels, nothing is rendered and the plugin says
so. Showing the nearest label instead would let you edit one label while
looking at another — a quiet way to ship the wrong thing.

**Validate.** **Labelixa: Validate ZPL** (`Ctrl+Alt+Shift+V`) checks the
whole file: unknown commands, unterminated fields, out-of-range parameters,
RFID notes and more. Each finding is an editor marker with its real line and
column; the tooltip links to the rule page
(`labelixa.com/zpl/rules/<code>`), and findings that carry a fix offer it as
a quick-fix (`Alt+Enter`). Findings stay until the next validation.

**Lint while typing** is off by default. When you turn it on in the settings
the file is re-checked after you stop editing. Validation does not consume
label quota, only the request rate limit.

**Syntax highlighting** for `.zpl` files: command codes and `^FX` comments.

## Settings

*Settings → Tools → Labelixa*

| Setting | Default | Notes |
|---|---|---|
| API key | empty | Optional. Without a key the free, rate-limited anonymous tier is used. Kept in the IDE password safe, never in a settings file. |
| Base URL | `https://api.labelixa.com` | Change only for a self-hosted deployment. |
| Print density | 8 dots/mm | 8 = 203 dpi, 12 = 300 dpi, 24 = 600 dpi. |
| Label width / height | 4 × 6 in | Used for preview and validation. |
| Lint while typing | off | See above. |

## What is sent where

Nothing leaves the IDE until you run an action or turn on lint while
typing. Then the text of the current label (preview) or of the current file
(validation) is sent over HTTPS to the base URL above, and the answer is
shown. There is no telemetry, no account requirement and no local storage
of your labels by the plugin. Requests carry a `User-Agent` and an
`X-Client: jetbrains/<version>` header so the service can count plugin use
in aggregate.

## Building from source

```
./gradlew buildPlugin      # -> build/distributions/labelixa-zpl-<version>.zip
./gradlew test             # unit tests for the editor-independent core
./gradlew runIde           # start a sandbox IDE with the plugin installed
```

Requires JDK 17+ and network access for the IntelliJ Platform artifacts.

## License

MIT — see `LICENSE`.
