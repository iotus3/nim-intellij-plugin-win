# Nim IntelliJ Plugin

Nim language support for IntelliJ IDEA and other JetBrains IDEs.

Supports three approaches since Nim's tooling is notoriously bad:
 * [nimlangserver](https://github.com/nim-lang/langserver) via [LSP4IJ](https://github.com/redhat-developer/lsp4ij).
 * [nimlsp](https://github.com/PMunch/nimlsp) via [LSP4IJ](https://github.com/redhat-developer/lsp4ij).
 * No LSP, just "nim check"

This is not official in any way.
It is just a slopped together plugin that works well enough.

## Features

Provided by the plugin:

- Syntax highlighting
- File type recognition (`.nim`, `.nims`, `.nimble`, `nimscript`)
- Project setup from `.nimble` file 
  - srcDir, binDir, tests/
  - external libraries list
  - standard library list
- Code formatting via `nph` or `nimpretty` (Reformat Code / Ctrl+Alt+L)
- Comment/uncomment (Ctrl+/)
- Auto-close quotes and brackets
- Runner/test runner. _Right-click a `.nimble` file for "nimble run"/"nimble test", or any `.nim` file for "nim r"._
- New Project wizard (File → New Project → Nim)
- Option to not use an LSP, and use "nim check" instead

Provided by nimlangserver over LSP:

- Code completion
- Hover documentation
- Go to definition
- Find usages
- Highlight all occurrences of symbol under cursor
- Go to symbol
- Diagnostics
- Inlay hints
- Folding
- Structure view
- Signature help
- Nimble/standard libraries

## Requirements

- IntelliJ IDEA 2026.1 (Community or Ultimate)
- [Nim and Nimble](https://nim-lang.org/install.html) installed
- [nimlangserver](https://github.com/nim-lang/langserver) — auto-installed by the plugin if missing; or install manually: `nimble install nimlangserver`
  - alternatively, use [nimlsp](https://github.com/PMunch/nimlsp)
- [nph](https://github.com/arnetheduck/nph) or [nimpretty](https://nim-lang.org/docs/nimpretty.html) for formatting: `nimble install nph` (optional)

## Installation

Install from the JetBrains Marketplace (search "Nim"), or build from source:

```bash
./gradlew buildPlugin
```

Then install the ZIP from `build/distributions/` via **Settings → Plugins → Install Plugin from Disk**.

## Configuration

**Settings → Languages & Frameworks → Nim**:

- **Nim SDK path** — Nim installation root, containing `bin/` and `lib/`. Pick it with the browse button.
- **Nim toolchain path** — Nimble's `bin` directory holding the installed tools (usually `~/.nimble/bin`). Pick it with the browse button.
- **nimlangserver or nimlsp executable name**, **nimble**, **nph or nimpretty**, **nimsuggest** — absolute paths, filled in automatically from the two paths above. `nimlangserver` wins over `nimlsp`, and `nph` wins over `nimpretty`.

Selecting either path auto-fills the executables. **Auto-detect executables** re-runs the search, and **Reset to Defaults** re-detects everything. Detection looks at the configured paths first, then `where` (Windows) / `whereis` (Unix), then common install locations and choosenim toolchains — if it comes up empty, browse to the paths manually.

Leave the language server executable empty (button: **Set to use nim check on save**) to skip the LSP server entirely; the plugin then runs `nim check` on each save and marks the results in the editor. **Set to use nph** / **Set to use nimpretty** switch the formatter.

## Contributing

Contributions are welcome. Bug reports, pull requests, and feature suggestions are all appreciated.

## License

EPL-2.0 — see [LICENSE](LICENSE). Third-party asset attributions in [NOTICE](NOTICE).
