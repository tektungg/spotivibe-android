# Handoff package — Spotivibe redesign

## What's in here

| File | Purpose |
|---|---|
| **`claude-code-prompt.md`** | The prompt — paste the whole thing into Claude Code as your first message |
| **`tokens.kt.md`** | Color, Type, Spacing, Radius, Motion tokens in Kotlin/Compose form — drop into `ui/theme/` |
| **`implementation-guide.md`** | Table mapping every design decision → files in the Android repo, plus animation specs and Material 3 swap-outs |
| **`replacements.md`** | 17 visual decisions that changed from the current app, with reasoning |

## What else Claude Code needs

Two folders alongside `handoff/`:

1. **Your Android repo** — the current Spotivibe codebase
2. **The whole design canvas project** (this entire project, NOT just screenshots) — Claude Code can render `index.html` in a sandboxed browser AND read the JSX source, which is much higher fidelity than static PNGs. Each screen is a real React component that maps almost 1:1 to a Compose Composable.

To grab the canvas project: download this entire project (or zip its root). The relevant files for porting are `index.html`, `tokens.css`, `app.jsx`, and `screens-*.jsx`. The rest (`design-canvas.jsx`, `tweaks-panel.jsx`, `.design-canvas.state.json`) are runtime — Claude Code can ignore them.

## Recommended flow

1. **Download this project** as a zip (or just the files listed above).
2. **Open a new Claude Code session** in agent mode at the root of your Android repo.
3. **Paste `claude-code-prompt.md`** as the first message.
4. **Attach** the `handoff/` folder + the design canvas files (or the whole zipped project).
5. **Wait for the architecture summary** (step 1 in the prompt). Sanity-check it before approving anything.
6. **Approve `tokens.kt`** when Claude Code drops it.
7. **Go screen by screen**. After each, ask Claude Code to render a Compose Preview, screenshot it, and compare to the matching artboard in the canvas. Approve or iterate.

## Why source-attached beats PNG-attached

| Attach screenshots | Attach the source (`tokens.css` + `screens-*.jsx`) |
|---|---|
| Claude Code reverse-engineers spacing | Claude Code reads the exact pixel values |
| Color requires eyedropper | Color is in `var(--accent)` etc. in `tokens.css` |
| Each variant = one PNG | All variants live in one component, parameterized |
| 20+ files to manage | 5 source files cover everything |

## When something doesn't match

The canvas (this project) is the source of truth for **visual decisions**.
The repo is the source of truth for **functional contracts** (sync offset behavior, premium gating, etc).
`replacements.md` resolves any conflict between them.

## Iteration loop after the first pass

1. Run the app, screenshot the problem.
2. Open this canvas, find the artboard that shows what you wanted.
3. New Claude Code chat: both screenshots side by side + "make A match B".

For larger directional changes (e.g. "make light mode warmer"), come back to this canvas first, tweak `tokens.css` here, push the updated `tokens.kt.md` to Claude Code.
