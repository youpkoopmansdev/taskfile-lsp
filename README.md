# Taskfile LSP

Language server and IDE extensions for [Taskfile](https://github.com/youpkoopmansdev/taskfile) — a modern task runner.

## Features

- **Syntax highlighting** — keywords, annotations, task names, parameters, embedded bash
- **Diagnostics** — parse errors with line numbers as you type
- **Completions** — keywords (`task`, `export`, `alias`, `include`, `dotenv`, `@description`, `@confirm`) and task names in `depends=[...]`
- **Hover** — task descriptions, parameters, and dependencies
- **Go to definition** — jump to task definitions and included files
- **Document symbols** — outline view of tasks, exports, aliases, includes

## Architecture

```
┌─────────────────────────────────────────────┐
│              taskfile-lsp (Rust)             │
│  Diagnostics · Completions · Hover · GoDef  │
└───────────┬─────────────┬───────────────────┘
            │  LSP (stdio) │
   ┌────────┴──┐    ┌──────┴───────┐    ┌──────────┐
   │  VS Code  │    │  JetBrains   │    │  Neovim   │
   │ Extension │    │   Plugin     │    │  Zed etc  │
   └───────────┘    └──────────────┘    └──────────┘
```

One LSP server powers all editors.

## Install the LSP server

### From source

```sh
cd lsp-server
cargo install --path .
```

This installs the `taskfile-lsp` binary to `~/.cargo/bin/`.

### Verify

```sh
taskfile-lsp --help  # should be in your PATH
```

## VS Code

### Install from source

```sh
cd vscode-extension
npm install
npm run compile
```

Then press **F5** in VS Code to launch an Extension Development Host, or package it:

```sh
npx @vscode/vsce package
code --install-extension taskfile-*.vsix
```

### Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| `taskfile.lspPath` | `taskfile-lsp` | Path to the LSP server binary |

The extension activates for any file named `Taskfile` or `*.Taskfile`.

## JetBrains (RustRover, IntelliJ, WebStorm, GoLand, etc.)

### Install from GitHub Release

1. Go to [Releases](https://github.com/youpkoopmansdev/taskfile-lsp/releases)
2. Download the `taskfile-intellij-*.zip` plugin file
3. In RustRover: **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
4. Select the downloaded ZIP
5. Restart the IDE

### Install the LSP server

The plugin needs `taskfile-lsp` in your PATH:

```sh
# From the same release page, download the binary for your OS, then:
tar xzf taskfile-lsp-macos-aarch64.tar.gz
sudo mv taskfile-lsp /usr/local/bin/
```

Or build from source:

```sh
cd lsp-server
cargo install --path .
```

### Build plugin from source

```sh
cd jetbrains-plugin
./gradlew buildPlugin
# Plugin ZIP → build/distributions/
```

### Requirements

- IntelliJ 2023.2 or later (for built-in LSP support)
- `taskfile-lsp` binary in your PATH

## Neovim

No plugin needed — just configure the LSP client:

```lua
-- ~/.config/nvim/lua/plugins/taskfile.lua (or init.lua)
vim.api.nvim_create_autocmd({ "BufRead", "BufNewFile" }, {
  pattern = { "Taskfile", "*.Taskfile" },
  callback = function()
    vim.bo.filetype = "taskfile"
  end,
})

vim.lsp.config('taskfile', {
  cmd = { 'taskfile-lsp' },
  filetypes = { 'taskfile' },
  root_markers = { 'Taskfile' },
})

vim.lsp.enable('taskfile')
```

For syntax highlighting, copy the TextMate grammar or add a Tree-sitter grammar.

## Zed

Add to your Zed settings (`~/.config/zed/settings.json`):

```json
{
  "lsp": {
    "taskfile-lsp": {
      "binary": { "path": "taskfile-lsp" }
    }
  },
  "file_types": {
    "Taskfile": ["Taskfile", "*.Taskfile"]
  }
}
```

## Helix

Add to `~/.config/helix/languages.toml`:

```toml
[[language]]
name = "taskfile"
scope = "source.taskfile"
file-types = [{ glob = "Taskfile" }, { glob = "*.Taskfile" }]
roots = ["Taskfile"]
language-servers = ["taskfile-lsp"]

[language-server.taskfile-lsp]
command = "taskfile-lsp"
```

## Sublime Text

Install the [LSP](https://packagecontrol.io/packages/LSP) package, then add to LSP settings:

```json
{
  "clients": {
    "taskfile-lsp": {
      "enabled": true,
      "command": ["taskfile-lsp"],
      "selector": "source.taskfile"
    }
  }
}
```

Copy `vscode-extension/syntaxes/taskfile.tmLanguage.json` to your Sublime `Packages/User/` folder for syntax highlighting.

## Project structure

```
taskfile-lsp/
  lsp-server/            Rust LSP server (the brain)
  vscode-extension/      VS Code extension + TextMate grammar
  jetbrains-plugin/      JetBrains IDE plugin
```

## License

MIT
