# AGENTS.md — Taskfile LSP & IDE Plugins

## Project Overview

This is the IDE language support project for the [Taskfile](https://github.com/youpkoopmansdev/taskfile) task runner. It provides syntax highlighting, error checking, completions, hover docs, go-to-definition, and document symbols for `Taskfile` and `*.Taskfile` files.

- **Repository:** `github.com/youpkoopmansdev/taskfile-lsp`
- **Current version:** 0.1.0
- **Three components:**
  1. `lsp-server/` — Rust LSP server (the core engine)
  2. `vscode-extension/` — VS Code extension (TextMate grammar + LSP client)
  3. `jetbrains-plugin/` — JetBrains plugin (custom Kotlin lexer + LSP client) for RustRover, IntelliJ Ultimate, WebStorm, etc.

## Project Structure

```
lsp-server/                    — Rust LSP server
  src/
    main.rs                    — Entry point, tower-lsp stdio transport
    backend.rs                 — LanguageServer trait implementation
    parser/
      mod.rs                   — Error-recovering parser (never fails, collects diagnostics)
      ast.rs                   — AST types with Span info for all nodes

vscode-extension/              — VS Code / compatible editors
  src/
    extension.ts               — LSP client activation (finds taskfile-lsp binary)
  syntaxes/
    taskfile.tmLanguage.json   — TextMate grammar (embeds source.shell in task bodies)
  package.json                 — Extension manifest
  language-configuration.json  — Bracket matching, comments, auto-closing

jetbrains-plugin/              — JetBrains IDEs (RustRover, IntelliJ Ultimate, etc.)
  src/main/kotlin/dev/youpkoopmans/taskfile/
    TaskfileLanguage.kt        — Language singleton
    TaskfileFileType.kt        — File type registration (Taskfile, *.Taskfile)
    TaskfileIcons.kt           — Plugin icons
    TaskfileParserDefinition.kt — PSI parser definition using TaskfileLexer
    TaskfileLexer.kt           — Hand-written lexer for syntax highlighting
    TaskfileTokenTypes.kt      — IElementType token constants
    TaskfileSyntaxHighlighter.kt     — Maps tokens to IDE color text attributes
    TaskfileSyntaxHighlighterFactory.kt — Factory for syntax highlighter
    TaskfileLspServerSupportProvider.kt — LSP client connecting to taskfile-lsp binary
  src/main/resources/META-INF/
    plugin.xml                 — Plugin descriptor (extensions, dependencies)
    pluginIcon.svg             — Plugin icon
  build.gradle.kts             — Gradle build configuration
  settings.gradle.kts          — Gradle settings
  gradle.properties            — Gradle/Kotlin properties

.github/workflows/
  ci.yml                       — CI for all 3 components
  release.yml                  — Release workflow (LSP binaries + VSIX + JetBrains ZIP)
```

## LSP Server

### Technology Stack
- **Rust** (edition 2024)
- `tower-lsp` v0.20 — LSP framework
- `tokio` v1 (full features) — Async runtime
- `serde` + `serde_json` — JSON serialization
- No other dependencies.

### Parser Design

The LSP parser is a **separate implementation** from the CLI's parser. Key difference: it is **error-recovering** — it never returns `Err`, instead collecting diagnostics in the AST.

```rust
pub struct Ast {
    pub tasks: Vec<Task>,
    pub exports: Vec<Export>,
    pub aliases: Vec<Alias>,
    pub includes: Vec<Include>,
    pub dotenv: Vec<DotEnv>,
    pub diagnostics: Vec<Diagnostic>,  // ← errors collected here, not returned
}
```

All AST nodes carry **`Span`** information (start_line, start_col, end_line, end_col) for precise editor integration.

```rust
pub struct Span {
    pub start_line: u32,
    pub start_col: u32,
    pub end_line: u32,
    pub end_col: u32,
}
```

### LSP Features Implemented

| Feature | How |
|---------|-----|
| **Diagnostics** | Parser collects errors → published via `publishDiagnostics` |
| **Completions** | Keywords (task, export, alias, include, dotenv, @description, @confirm), task names in `depends=[...]` context, `depends`/`depends_parallel` snippets on task header lines |
| **Hover** | Shows task name, description, parameters, dependencies on hover |
| **Go-to-definition** | Task names → jump to task definition; include paths → jump to included file |
| **Document symbols** | Tasks (function), exports (variable), aliases (function), includes (module), dotenv (file) |

### Document Storage

```rust
documents: RwLock<HashMap<Url, (String, Ast)>>
```
Caches both raw text and parsed AST per open document. Updated on `didOpen` and `didChange` (full sync mode).

### Testing

- 7 unit tests in `lsp-server/src/parser/mod.rs`
- Run: `cd lsp-server && cargo test`

## VS Code Extension

### How It Works

1. **TextMate grammar** (`syntaxes/taskfile.tmLanguage.json`) provides syntax highlighting. Task bodies embed `source.shell` for full bash highlighting.
2. **LSP client** (`src/extension.ts`) finds and launches the `taskfile-lsp` binary (looks in PATH, then common install locations).
3. File associations: `Taskfile`, `*.Taskfile`

### Build & Package

```bash
cd vscode-extension
npm ci
npm run compile        # TypeScript → JavaScript
npx vsce package       # → taskfile-lsp-*.vsix
```

### Key files

- `package.json`: Extension manifest with `activationEvents`, `contributes.languages`, `contributes.grammars`
- `language-configuration.json`: Bracket pairs, comment toggling, auto-closing pairs
- `tsconfig.json`: TypeScript config targeting ES2020

## JetBrains Plugin

### Compatibility

- **Requires commercial JetBrains IDEs** — RustRover, IntelliJ Ultimate, WebStorm, GoLand, PyCharm Professional, etc.
- **NOT compatible with Community Edition** — LSP API (`com.intellij.modules.lsp`) is only available in commercial products.
- `sinceBuild`: 242 (2024.2+), `untilBuild`: 262.* (up to 2026.2.x)

### Build Configuration (CRITICAL — Hard-Won Knowledge)

These exact versions are required and were determined through multiple CI iterations:

| Component | Version | Why |
|-----------|---------|-----|
| `org.jetbrains.intellij.platform` Gradle plugin | **2.14.0** | Current stable; requires Gradle 9.0+ |
| `org.jetbrains.kotlin.jvm` | **2.3.20** | IntelliJ 2026.1 ships Kotlin 2.3.0 metadata; must match or exceed |
| Gradle wrapper | **9.0** | Required by IntelliJ Platform Gradle Plugin 2.14.0 |
| Java (JVM toolchain + CI) | **21** | Required for Gradle 9 + Kotlin 2.3 |
| `intellijIdea("2026.1")` | Platform target | Use `intellijIdea()` (not `intellijIdeaCommunity()` or `intellijIdeaUltimate()`) |

**`gradle.properties` must include:**
```properties
kotlin.stdlib.default.dependency = false
```
This avoids Kotlin stdlib conflicts with the version bundled in IntelliJ.

### Plugin Architecture

1. **Custom Lexer** (`TaskfileLexer.kt`): Hand-written `com.intellij.lexer.Lexer` subclass that tokenizes Taskfile syntax into: KEYWORD, ANNOTATION, COMMENT, STRING, BRACE, BRACKET, IDENTIFIER, OPERATOR, TASK_BODY, WHITESPACE, BAD_CHARACTER.
2. **Syntax Highlighter** (`TaskfileSyntaxHighlighter.kt`): Maps token types to IntelliJ `TextAttributesKey` for color scheme integration.
3. **LSP Client** (`TaskfileLspServerSupportProvider.kt`): Implements `LspServerSupportProvider` → creates `ProjectWideLspServerDescriptor` that launches `taskfile-lsp --stdio`.

### plugin.xml Dependencies

```xml
<depends>com.intellij.modules.platform</depends>
<depends>com.intellij.modules.lsp</depends>
```

**IMPORTANT:** `com.intellij.modules.platform` is a **module dependency** for plugin.xml, NOT a Gradle `bundledPlugin()`. Never add `bundledPlugin("com.intellij.modules.platform")` to `build.gradle.kts` — it will fail.

### Build & Package

```bash
cd jetbrains-plugin
./gradlew buildPlugin
# Output: build/distributions/taskfile-jetbrains-*.zip
```

User installs via: RustRover → Settings → Plugins → ⚙️ → Install Plugin from Disk → select ZIP.

## CI/CD

### CI (`.github/workflows/ci.yml`)

Three parallel jobs:
1. **LSP Server** — fmt + clippy + test + build on ubuntu/macos/windows
2. **VS Code Extension** — npm ci + compile
3. **JetBrains Plugin** — Java 21 + Gradle + `./gradlew buildPlugin`

### Release (`.github/workflows/release.yml`)

Triggered by `v*` tags. Builds:

**LSP Server binaries (5 targets):**
- linux-x86_64, linux-aarch64 (cross-compiled), macos-x86_64, macos-aarch64, windows-x86_64
- Packaged as `.tar.gz` (unix) or `.zip` (windows)

**VS Code Extension:**
- `npx vsce package` → `.vsix` artifact

**JetBrains Plugin:**
- `./gradlew buildPlugin` → `.zip` artifact

All artifacts uploaded to GitHub Release with auto-generated notes.

## Common Gotchas

### JetBrains Plugin Build Failures (Lessons Learned)

These were discovered through iterative CI debugging:

1. **Kotlin version mismatch**: IntelliJ 2026.1 uses Kotlin 2.3.0 metadata. If your Kotlin plugin is 1.9.x, you get hundreds of "incompatible metadata version" errors. Must use Kotlin 2.3.20+.

2. **Gradle version requirement**: IntelliJ Platform Gradle Plugin 2.14.0 requires Gradle 9.0+. Plugin 2.2.1 worked with Gradle 8.5, but couldn't resolve 2026.1 platform.

3. **`bundledPlugin("com.intellij.modules.platform")`**: This is WRONG. `com.intellij.modules.platform` is a plugin.xml `<depends>` module, not a Gradle dependency. Adding it to `build.gradle.kts` causes "Could not find bundled plugin" errors.

4. **`instrumentationTools()` deprecated**: Don't call it in `build.gradle.kts`. It was needed in older plugin versions but causes warnings/errors in 2.x+.

5. **TextMate `TextMateBundleProvider` API**: Unreliable for plugins — requires `com.intellij.textmate` dependency and has awkward `Path` requirements for JAR-bundled resources. We replaced it with a custom Kotlin Lexer + SyntaxHighlighter which is more reliable and has zero external deps.

6. **Community Edition**: Does NOT have LSP support (`com.intellij.modules.lsp`). The plugin only works in commercial JetBrains IDEs.

7. **cwm-plugin bug**: IntelliJ Community 2023.2 has an invalid `cwm-plugin` descriptor that causes Gradle configure failures. Use 2024.1+ platform version to avoid this.

8. **Gradle wrapper script**: Must be the official script from `github.com/gradle/gradle`. Hand-written scripts break on JVM option quoting (`-Xmx64m` class-not-found error).

### LSP Parser vs CLI Parser

The two parsers are **independent implementations** with different designs:

| | CLI Parser (`taskfile/src/parser/`) | LSP Parser (`taskfile-lsp/lsp-server/src/parser/`) |
|---|---|---|
| Error handling | Returns `Result<Ast, ParseError>` — fails on first error | Never fails — collects `Vec<Diagnostic>` in AST |
| Span info | Only `line: usize` on tasks/includes | Full `Span` (start_line, start_col, end_line, end_col) on all nodes |
| Purpose | Execution (needs to be correct or fail) | Editor support (needs to be resilient) |

If you add a new Taskfile construct, **you must update BOTH parsers**.

### TextMate Grammar

The VS Code TextMate grammar (`taskfile.tmLanguage.json`) embeds `source.shell` inside task body braces. This gives full bash syntax highlighting inside tasks. The grammar uses `begin`/`end` patterns with brace counting.

## Modifying the Project

### Adding a new Taskfile keyword

1. **LSP Parser** (`lsp-server/src/parser/mod.rs`): Add parsing logic
2. **LSP AST** (`lsp-server/src/parser/ast.rs`): Add node type with Span
3. **LSP Backend** (`lsp-server/src/backend.rs`): Add completions, hover, symbols support
4. **VS Code Grammar** (`vscode-extension/syntaxes/taskfile.tmLanguage.json`): Add to keyword patterns
5. **JetBrains Lexer** (`jetbrains-plugin/.../TaskfileLexer.kt`): Add to keyword recognition
6. **JetBrains Token Types** (`jetbrains-plugin/.../TaskfileTokenTypes.kt`): Add token type if needed
7. **Tests**: Add parser tests in both LSP and CLI projects

### Adding a new LSP feature

1. Update `ServerCapabilities` in `backend.rs` `initialize()`
2. Implement the corresponding `LanguageServer` trait method
3. The JetBrains and VS Code clients automatically pick up new LSP capabilities — no client-side changes needed
