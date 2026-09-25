# Changelog

All notable changes to the Labelixa ZPL plugin for JetBrains IDEs are
documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project
uses [Semantic Versioning](https://semver.org/).

## [0.1.1] - 2026-09-25

### Fixed
- **Labelixa: Validate ZPL** was missing from the Tools and editor context
  menus, and validation findings were never shown in the editor. `.zpl`
  files had no parser definition, so the platform opened them as plain
  text and every ZPL-language extension (the external annotator, the
  validate action's language check) was skipped. A minimal parser
  definition now gives the file the ZPL language; the API still does the
  real parsing. Lint while typing works for the same reason.

## [0.1.0] - 2026-09-23

### Added
- ZPL file type (`.zpl`) with syntax highlighting for command codes and
  `^FX` comments.
- **Labelixa: Preview Label Under Cursor** renders the `^XA … ^XZ` block
  under the caret with the Labelixa API and shows it in the *Labelixa
  Preview* tool window at the printer's real pixel size.
- **Labelixa: Validate ZPL** checks the file and shows findings as editor
  markers with their real line and column, a link to the rule page and a
  quick-fix where the API provides one.
- Optional lint while typing (off by default).
- Settings: API key (IDE password safe), base URL, print density, label
  size, lint while typing.
