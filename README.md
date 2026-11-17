# xiaomi-home

## REA

This repository pins [REA](https://rea.tools/) to version `6.9.0` so every contributor uses the same release.

### Prerequisites

- Node.js `22.23.4+`, `24.22.1+`, or `26.12.1+`
- npm

### Install

```bash
npm install
```

Connect REA to the coding agent you use:

```bash
# Codex
npm run rea:setup:codex

# Claude Code
npm run rea:setup:claude
```

REA shows its setup plan and asks for approval before changing the selected agent's local configuration. Restart that agent after setup.

### Verify

```bash
# Codex
npm run rea:doctor:codex

# Claude Code
npm run rea:doctor:claude
```

A successful client check reports `"healthy": true`.
