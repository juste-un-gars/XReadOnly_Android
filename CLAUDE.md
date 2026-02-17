# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## QUICK REFERENCE - READ FIRST

**Critical rules that apply to EVERY session:**

1. **Incremental only** → Max 150 lines per iteration, STOP for validation
2. **No hardcoding** → No secrets, paths, credentials in code (use BuildConfig / local.properties)
3. **Logs from day 1** → Configurable logging via `Log` wrapper or Timber
4. **Security audit** → MANDATORY before "project complete" status
5. **Stop points** → Wait for "OK"/"validated" after each module

**If unsure, read the relevant section below.**

---

## Project Context

**Project Name:** XReadOnly_Android
**Tech Stack:** Android native (WebView-based)
**Primary Language:** Kotlin
**Min SDK:** 26 (Android 8.0)
**Core Component:** `android.webkit.WebView` — no backend, fully client-side
**Build System:** Gradle (standard Android project)
**Architecture Pattern:** Single-activity WebView app with two-layer interaction blocking
**Full Specification:** `PROJET.md`

---

## Build & Development Commands

```bash
# Build
./gradlew assembleDebug

# Run tests
./gradlew test                                    # All unit tests
./gradlew testDebugUnitTest                       # Debug variant only
./gradlew test --tests "*.RequestBlockerTest"     # Single test class

# Lint
./gradlew lint
./gradlew lintDebug

# Install on device
./gradlew installDebug

# Clean
./gradlew clean
```

---

## Architecture

Single-activity WebView app that loads Twitter/X in **read-only mode**. Users can browse, search, and view content with their account, but all interactions (like, retweet, reply, etc.) are blocked.

### Layer 1: UI Blocking (CSS/JS Injection)
- `assets/inject.css` — hides interaction buttons via `data-testid` selectors
- `assets/inject.js` — MutationObserver that continuously hides elements during infinite scroll + click interception fallback
- Injected via `evaluateJavascript()` after page load

### Layer 2: Network Blocking (API Endpoint Interception)
- `RequestBlocker.kt` — blocks POST requests to Twitter GraphQL mutation endpoints (`CreateTweet`, `FavoriteTweet`, `CreateRetweet`, `CreateBookmark`, `CreateDM`, etc.) and REST API write endpoints
- Implemented via `shouldInterceptRequest()` in `WebViewClient`
- This is the robust safety layer — even if CSS hiding fails, the action is blocked at network level

### Key Classes (under `com/example/xreadonly/`)

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Main activity hosting the WebView |
| `ReadOnlyWebViewClient.kt` | URL interception, external link routing, request blocking |
| `ReadOnlyWebChromeClient.kt` | Popup handling |
| `RequestBlocker.kt` | API endpoint blocking logic |
| `InjectionScripts.kt` | CSS/JS injection helpers |

### External Link Handling
Links outside `x.com`/`twitter.com` are intercepted via `shouldOverrideUrlLoading()` and opened in **Firefox Focus** (`org.mozilla.focus`), which is a privacy-focused browser where every session is ephemeral by default. Falls back to system default browser if Firefox Focus is not installed. **Note:** Chrome's incognito intent extra (`EXTRA_OPEN_NEW_INCOGNITO_TAB`) is restricted to first-party Google apps and does not work for third-party apps — Firefox Focus is the recommended solution. Users should install Firefox Focus on their device for the best experience.

### Deep Links (Twitter/X URL Handling)
The app registers intent filters for `x.com`, `www.x.com`, `twitter.com`, `www.twitter.com`, and `mobile.twitter.com` so that clicking a Twitter link anywhere on the device can open it in XReadOnly instead of the default browser. Since we don't own these domains, **Android App Links verification is not possible** — these are unverified deep links.

- **Android 11 and below:** The system shows a chooser dialog ("Open with: XReadOnly / Chrome / ...").
- **Android 12+:** Unverified links don't trigger a chooser by default. The user must manually enable it: **Settings > Apps > XReadOnly > "Open by default" > enable x.com and twitter.com domains.**

The `launchMode="singleTask"` ensures that if the app is already running, new deep links are delivered via `onNewIntent()` instead of creating a new activity. The `getDeepLinkUrl()` method validates the incoming URL against the allowed Twitter domains before loading.

### WebView Configuration
JavaScript and DOM Storage must be enabled. Cookies are persisted via `CookieManager`. User-Agent must be set to standard Chrome mobile UA so Twitter serves the full mobile web version.

### Important: Twitter Selector Stability
Twitter/X uses `data-testid` attributes that are relatively stable but may change. If interaction buttons reappear, check and update selectors in `inject.css`, `inject.js`, and `InjectionScripts.kt`. The network-level blocking in `RequestBlocker.kt` is the failsafe.

---

## Development Philosophy

### Golden Rule: Incremental Development

**NEVER write large amounts of code without validation.**

```
One module → Test → User validates → Next module
```

**Per iteration limits:**
- 1-3 related files maximum
- ~50-150 lines of new code
- Must be independently testable

### Mandatory Stop Points

Claude MUST stop and wait for user validation after:
- WebView configuration changes
- Injection script changes (CSS/JS)
- Request blocking logic changes
- External link handling changes
- Any security-sensitive code

**Stop format:**
```
[Module] complete.

**Test it:**
1. [Step 1]
2. [Step 2]
Expected: [Result]

Waiting for your validation before continuing.
```

### Code Hygiene Rules (MANDATORY)

**Goal: Application must be portable and buildable anywhere without code changes.**

**NEVER hardcode in source files:**
- Passwords, API keys, tokens, secrets
- Absolute paths (`C:\Users\...`, `/home/user/...`)
- Environment-specific URLs

**ALWAYS use instead:**
- `local.properties` (never committed) for local config
- `BuildConfig` fields for build-time configuration
- `gradle.properties` for non-sensitive build config
- Relative paths or configurable base paths

### Development Order (Enforce)

1. **Foundation first** — Project structure, Gradle config, WebView setup
2. **Test foundation** — Don't continue if WebView doesn't load
3. **Core features** — Blocking layers, one by one, tested
4. **Advanced features** — Only after core works (external links, settings, etc.)

### File Size Guidelines

**Target sizes (lines of code):**
- **< 300** : ideal
- **300-500** : acceptable
- **500-800** : consider splitting
- **> 800** : must split

**Naming convention for split files:**
```
RequestBlocker.kt          → Core blocking logic
RequestBlockerGraphQL.kt   → GraphQL endpoint patterns
RequestBlockerRest.kt      → REST endpoint patterns
```

---

## Logging Standards

**Use Android `Log` or Timber for structured logging.**

**Log Levels:**
```
VERBOSE → Everything (dev only)
DEBUG   → Detailed flow (dev only)
INFO    → Normal operations (blocked requests, page loads)
WARN    → Suspicious behavior (unknown endpoints, selector failures)
ERROR   → Handled errors (WebView errors, injection failures)
```

**What to Log:**
- Blocked API requests (endpoint, operation name)
- Injection events (CSS/JS loaded, selectors applied)
- Navigation events (URL changes, external link redirects)
- Errors with context

**NEVER Log:**
- User cookies, session tokens
- Full request/response bodies
- Personal user data from Twitter

**Disable verbose/debug logs in release builds** via `BuildConfig.DEBUG` checks or ProGuard/R8 stripping.

---

## Session Management

### Quick Start

**Continue work:** `"continue"` or `"let's continue"`
**New session:** `"new session: Feature Name"`

### File Structure

- **SESSION_STATE.md** (root) — Overview and session index
- **.claude/sessions/SESSION_XXX_[name].md** — Detailed session logs

**Naming:** `SESSION_001_project_setup.md`

### SESSION_STATE.md Header (Required)

SESSION_STATE.md **must** start with this reminder block:

```markdown
# XReadOnly_Android - Session State

> **Claude : Appliquer le protocole de session (CLAUDE.md)**
> - Créer/mettre à jour la session en temps réel
> - Valider après chaque module avec : [Module] complete. **Test it:** [...] Waiting for validation.
> - Ne pas continuer sans validation utilisateur
```

### Session Template

```markdown
# Session XXX: [Feature Name]

## Meta
- **Date:** YYYY-MM-DD
- **Goal:** [Brief description]
- **Status:** In Progress / Blocked / Complete

## Current Module
**Working on:** [Module name]
**Progress:** [Status]

## Module Checklist
- [ ] Module planned (files, dependencies, test procedure)
- [ ] Code written
- [ ] Self-tested by Claude
- [ ] User validated ← **REQUIRED before next module**

## Completed Modules
| Module | Validated | Date |
|--------|-----------|------|
| WebView Setup | ? | YYYY-MM-DD |
| CSS Injection | ? | YYYY-MM-DD |

## Next Modules (Prioritized)
1. [ ] [Next module]
2. [ ] [Following module]

## Technical Decisions
- **[Decision]:** [Reason]

## Issues & Solutions
- **[Issue]:** [Solution]

## Files Modified
- `path/file.ext` — [What/Why]

## Handoff Notes
[Critical context for next session]
```

### Session Rules

**MUST DO:**
1. Read CLAUDE.md and current session first
2. Update session file in real-time
3. Wait for validation after each module
4. Fix bugs before new features

**NEW SESSION when:**
- New major feature/module
- Current session goal complete
- Different project area

---

## Module Workflow

### 1. Plan (Before Coding)

```markdown
**Module:** [Name]
**Purpose:** [One sentence]
**Files:** [List]
**Depends on:** [Previous modules]
**Test procedure:** [How to verify]
**Security concerns:** [If any]
```

### 2. Implement

- Write minimal working code
- Include error handling
- Add KDoc for public APIs

### 3. Validate

**Functional:**
- [ ] Builds without errors (`./gradlew assembleDebug`)
- [ ] Runs on device/emulator without crashes
- [ ] Expected behavior verified
- [ ] Errors handled gracefully

**Security (if applicable):**
- [ ] No hardcoded secrets or credentials
- [ ] WebView requests properly filtered
- [ ] External links properly intercepted
- [ ] No data leaks via logs

### 4. User Confirmation

**DO NOT proceed until user says "OK", "validated", or "continue"**

---

## Build Order — XReadOnly_Android

```
Stage 1: Foundation (validate before Stage 2)
├── [ ] Android project structure + Gradle config → builds without error
├── [ ] .gitignore comprehensive for Android
├── [ ] MainActivity + basic WebView → loads x.com
├── [ ] WebView settings (JS, DOM storage, cookies, UA)
├── [ ] Cookie persistence → session survives app restart
└── [ ] SECURITY REVIEW

Stage 2: Core Blocking (validate before Stage 3)
├── [ ] CSS injection (inject.css) → interaction buttons hidden
├── [ ] JS injection (inject.js) → MutationObserver active on scroll
├── [ ] RequestBlocker → GraphQL mutation endpoints blocked
├── [ ] RequestBlocker → REST API write endpoints blocked
└── [ ] Full blocking test (UI + network layers combined)

Stage 3: Navigation & Polish
├── [ ] External link interception → opens in Chrome incognito
├── [ ] Fallback to default browser if Chrome unavailable
├── [ ] Back button navigation within WebView
└── [ ] Error/offline handling

Stage 4: Pre-Launch (MANDATORY)
├── [ ] Full security audit (see checklist)
├── [ ] Dependency audit
├── [ ] Test on multiple Android versions (SDK 26+)
├── [ ] ProGuard/R8 rules for release build
├── [ ] All issues fixed or documented
└── [ ] Final validation
```

---

## Documentation Standards

### File Header (Required for Kotlin)

```kotlin
/**
 * @file FileName.kt
 * @description Brief purpose
 */
```

### Function Documentation (Required for public APIs)

```kotlin
/**
 * Brief description.
 * @param name Description
 * @return Description
 */
```

---

## Pre-Launch Security Audit

### When to Run

**MANDATORY before any deployment or "project complete" status.**

### Security Audit Checklist

#### 1. Code Review (Full Scan)
- [ ] No hardcoded secrets (API keys, passwords, tokens)
- [ ] No hardcoded paths
- [ ] No sensitive data in logs
- [ ] All WebView inputs/URLs validated
- [ ] No debug/dev code left in release
- [ ] `.gitignore` excludes sensitive files

#### 2. WebView Security
- [ ] JavaScript injection is scoped correctly
- [ ] `shouldInterceptRequest()` blocks all mutation endpoints
- [ ] `shouldOverrideUrlLoading()` catches all external URLs
- [ ] No unintended data exposure through WebView
- [ ] Cookie handling is secure
- [ ] No mixed content issues

#### 3. Android-Specific
- [ ] Proper permissions declared in AndroidManifest.xml (INTERNET only)
- [ ] No unnecessary permissions
- [ ] ProGuard/R8 minification for release
- [ ] debuggable=false in release builds
- [ ] Network security config if needed

#### 4. Dependency Audit
```bash
./gradlew dependencies
# Check for known vulnerabilities in dependencies
```
- [ ] All critical/high vulnerabilities addressed
- [ ] Outdated packages updated or justified

---

## Git Best Practices & .gitignore

### Critical Rules (MANDATORY)

**NEVER commit:**
- `.env` files, secrets, signing keystores
- `local.properties` (contains SDK path)
- Build outputs (`/build/`, `.gradle/`)
- IDE configs (`.idea/` user-specific files)

**ALWAYS commit:**
- `.gitignore` (comprehensive)
- `README.md`, `CLAUDE.md`, `PROJET.md`
- Source code, resources, assets
- Gradle wrapper (`gradle/wrapper/`)
- `gradle.properties` (non-sensitive only)

### Android .gitignore Essentials

```gitignore
# Android/Gradle
*.iml
.gradle/
/local.properties
/.idea/
/build/
/app/build/
/captures
.externalNativeBuild/
.cxx/

# Signing
*.jks
*.keystore
signing.properties

# Secrets
.env
*.key
*.pem

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/
```

### Branch Naming
`feature/session-XXX-brief-name`

### Commit Message
```
Session XXX: [Summary]

- Change 1
- Change 2
```

### Pre-Commit Checklist

**Before every commit, verify:**
- [ ] No secrets or signing keys in staged files
- [ ] No `local.properties` staged
- [ ] No absolute paths in code
- [ ] No temporary debug code (`Log.d` with sensitive data)
- [ ] `.gitignore` is up to date
- [ ] Commit message follows convention

---

## Quick Commands

| Command | Action |
|---------|--------|
| `continue` | Resume current session |
| `new session: [name]` | Start new session |
| `save progress` | Update session file |
| `validate` | Mark current module as validated |
| `show plan` | Display remaining modules |
| `security audit` | Run full pre-launch security checklist |
| `dependency check` | Audit dependencies for vulnerabilities |

---

## File Standards

- **Encoding:** UTF-8 with LF line endings
- **Timestamps:** ISO 8601 (YYYY-MM-DD HH:mm)
- **Time format:** 24-hour
- **Kotlin style:** Follow Android Kotlin style guide (ktlint)

---

**Last Updated:** 2026-02-17
**Version:** 1.0.0
