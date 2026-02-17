# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

---

## 🚨 QUICK REFERENCE - READ FIRST

**Critical rules that apply to EVERY session:**

1. **Incremental only** → Max 150 lines per iteration, STOP for validation
2. **No hardcoding** → No secrets, paths, credentials in code (use .env)
3. **Logs from day 1** → Configurable via LOG_LEVEL, LOG_TO_FILE, LOG_PATH
4. **Security audit** → MANDATORY before "project complete" status
5. **Stop points** → Wait for "OK"/"validated" after each module

**If unsure, read the relevant section below.**

---

## Project Context

**Project Name:** [To be filled]  
**Tech Stack:** [To be filled]  
**Primary Language(s):** [To be filled]  
**Key Dependencies:** [To be filled]  
**Architecture Pattern:** [To be filled]

---

## ⚠️ Development Philosophy

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
- Database connection/schema changes
- Authentication/authorization code
- Each API endpoint or route group
- File system or external service integrations
- Any security-sensitive code

**Stop format:**
```
✅ [Module] complete. 

**Test it:**
1. [Step 1]
2. [Step 2]
Expected: [Result]

Waiting for your validation before continuing.
```

### Code Hygiene Rules (MANDATORY)

**Goal: Application must be portable and deployable anywhere without code changes.**

**NEVER hardcode in source files:**
- ❌ Passwords, API keys, tokens, secrets
- ❌ Database credentials or connection strings
- ❌ Absolute paths (`C:\Users\...`, `/home/user/...`)
- ❌ IP addresses, hostnames, ports (production)
- ❌ Email addresses, usernames for services
- ❌ Environment-specific URLs (dev, staging, prod)

**ALWAYS use instead:**
- ✅ Environment variables (`.env` files, never committed)
- ✅ Configuration files (with `.example` templates)
- ✅ Relative paths or configurable base paths
- ✅ Secret managers for production (Vault, AWS Secrets, etc.)

**Project must include:**
```
├── .env.example          # Template with ALL variables, placeholder values
├── .gitignore            # Excludes .env, secrets, logs, build artifacts
├── config/               # Centralized configuration module
│   ├── index.js          # Loads from env vars with defaults
│   └── config.example.json  # Template if using JSON config
└── README.md             # Setup instructions with env vars list
```

**Portability Checklist:**
- [ ] App starts with only `.env` configuration (no code edits)
- [ ] All paths relative or from env vars (`DATA_DIR`, `LOG_PATH`)
- [ ] Database connection string from env (`DATABASE_URL`)
- [ ] External service URLs from env (`API_BASE_URL`, `SMTP_HOST`)
- [ ] Port configurable (`PORT=3000`)
- [ ] Works on Windows, Linux, macOS (if cross-platform)

**Config Module Pattern:**
```javascript
// config/index.js - Example pattern
module.exports = {
  port: process.env.PORT || 3000,
  db: {
    url: process.env.DATABASE_URL || 'sqlite://local.db',
  },
  dataDir: process.env.DATA_DIR || './data',
  logLevel: process.env.LOG_LEVEL || 'info',
};
```

### Logging Standards

**Goal: Comprehensive, configurable logging for debugging, auditing, and monitoring.**

**MUST configure logging infrastructure in Stage 1 (Foundation)** before any other code.

**Log Levels (configurable via `LOG_LEVEL` env var):**
```
DEBUG   → Everything (dev only, verbose)
INFO    → Normal operations (API calls, user actions)
WARN    → Suspicious behavior (rate limit hits, deprecated usage)
ERROR   → Handled errors (connection failures, validation errors)
FATAL   → Unrecoverable errors (app crash)
```

**Environment Variables (add to `.env.example`):**
```env
LOG_LEVEL=info              # debug|info|warn|error|fatal
LOG_TO_FILE=false           # true = file, false = console
LOG_PATH=./logs             # Where to store log files
LOG_MAX_SIZE=10M            # Max file size before rotation
LOG_MAX_FILES=7             # Keep last N files
LOG_FORMAT=json             # json|text (json for prod monitoring)
```

**What to Log:**
- âœ… API requests (route, method, status code, response time)
- âœ… Auth events (login, logout, failed attempts, token refresh)
- âœ… Database operations (if DEBUG level)
- âœ… File operations (create, read, update, delete)
- âœ… External service calls (API calls, webhooks)
- âœ… Errors with stack traces
- âœ… Security events (injection attempts, unauthorized access)

**NEVER Log (Security Critical):**
- âŒ Passwords, tokens, API keys, secrets
- âŒ Credit card numbers, SSNs, personal IDs
- âŒ Session tokens, JWTs (log hash/ID only)
- âŒ Full request/response bodies if they contain sensitive data
- âŒ Database connection strings with credentials

**Logger Module Pattern (COMPLETE IMPLEMENTATION):**

```javascript
// logger.js - Production-ready centralized logging with rotation
const fs = require('fs');
const path = require('path');
const os = require('os');
const config = require('./config');

class Logger {
  constructor() {
    this.levels = { debug: 0, info: 1, warn: 2, error: 3, fatal: 4 };
    this.currentLevel = this.levels[config.logLevel] || 1;
    
    if (config.logToFile) {
      this.logPath = this.expandPath(config.logPath);
      this.logDir = path.dirname(this.logPath);
      this.logBasename = path.basename(this.logPath, '.log');
      this.ensureLogDir();
    }
  }

  /**
   * Expand environment variables in path (cross-platform)
   * Windows: %LOCALAPPDATA%, %APPDATA%, %TEMP%, %USERPROFILE%
   * Unix: $HOME, $TMPDIR, ${HOME}
   */
  expandPath(filepath) {
    if (!filepath) return './logs/app.log';
    
    // Windows environment variables
    if (process.platform === 'win32') {
      filepath = filepath.replace(/%([^%]+)%/g, (_, key) => {
        return process.env[key] || _;
      });
    }
    
    // Unix environment variables ($VAR or ${VAR})
    filepath = filepath.replace(/\$\{?([A-Z_][A-Z0-9_]*)\}?/gi, (_, key) => {
      return process.env[key] || _;
    });
    
    // Expand ~ to home directory
    if (filepath.startsWith('~')) {
      filepath = path.join(os.homedir(), filepath.slice(1));
    }
    
    return path.resolve(filepath);
  }

  ensureLogDir() {
    try {
      if (!fs.existsSync(this.logDir)) {
        fs.mkdirSync(this.logDir, { recursive: true, mode: 0o750 });
      }
    } catch (err) {
      console.error(`Failed to create log directory: ${err.message}`);
      process.exit(1);
    }
  }

  /**
   * Get log file path with current date: app-2025-01-29.log
   */
  getLogFilePath() {
    const date = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
    return path.join(this.logDir, `${this.logBasename}-${date}.log`);
  }

  writeToFile(message) {
    try {
      const logFile = this.getLogFilePath();
      
      // Append to file with proper permissions (Unix: 640 = owner rw, group r)
      fs.appendFileSync(logFile, message + '\n', { 
        mode: 0o640,
        encoding: 'utf8' 
      });
      
      // Check file size and rotate if needed
      const stats = fs.statSync(logFile);
      const maxSize = this.parseSize(config.logMaxSize || '10M');
      
      if (stats.size > maxSize) {
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const rotated = `${logFile}.${timestamp}`;
        fs.renameSync(logFile, rotated);
      }
      
      // Cleanup old log files (keep only LOG_MAX_FILES)
      this.cleanOldLogs();
      
    } catch (err) {
      // Fallback to console if file write fails
      console.error('Log write failed:', err.message);
      console.log(message);
    }
  }

  cleanOldLogs() {
    try {
      const maxFiles = parseInt(config.logMaxFiles) || 7;
      
      // Get all log files for this app
      const files = fs.readdirSync(this.logDir)
        .filter(f => f.startsWith(this.logBasename))
        .map(f => ({
          name: f,
          path: path.join(this.logDir, f),
          time: fs.statSync(path.join(this.logDir, f)).mtime.getTime()
        }))
        .sort((a, b) => b.time - a.time); // Newest first

      // Delete oldest files if exceeding limit
      if (files.length > maxFiles) {
        files.slice(maxFiles).forEach(f => {
          try {
            fs.unlinkSync(f.path);
          } catch (err) {
            // Ignore deletion errors
          }
        });
      }
    } catch (err) {
      // Ignore cleanup errors
    }
  }

  /**
   * Parse size string to bytes: "10M" → 10485760, "1G" → 1073741824
   */
  parseSize(sizeStr) {
    const units = { 
      K: 1024, 
      M: 1024 * 1024, 
      G: 1024 * 1024 * 1024 
    };
    const match = sizeStr.match(/^(\d+)([KMG]?)$/i);
    if (!match) return 10 * 1024 * 1024; // default 10MB
    const value = parseInt(match[1]);
    const unit = match[2].toUpperCase();
    return value * (units[unit] || 1);
  }

  log(level, message, meta = {}) {
    if (this.levels[level] < this.currentLevel) return;
    
    const entry = {
      timestamp: new Date().toISOString(),
      level: level.toUpperCase(),
      message,
      ...meta
    };

    const output = config.logFormat === 'json' 
      ? JSON.stringify(entry)
      : `[${entry.timestamp}] [${entry.level}] ${message}`;

    if (config.logToFile) {
      this.writeToFile(output);
    } else {
      console.log(output);
    }
  }

  debug(msg, meta) { this.log('debug', msg, meta); }
  info(msg, meta)  { this.log('info', msg, meta); }
  warn(msg, meta)  { this.log('warn', msg, meta); }
  error(msg, meta) { this.log('error', msg, meta); }
  fatal(msg, meta) { 
    this.log('fatal', msg, meta); 
    process.exit(1); 
  }
}

module.exports = new Logger();
```

**Configuration Examples (.env.example):**

```env
# Logging Configuration
LOG_LEVEL=info                              # debug|info|warn|error|fatal
LOG_TO_FILE=false                           # true=file, false=console
LOG_FORMAT=json                             # json|text (json for prod)
LOG_MAX_SIZE=10M                            # Max file size (K/M/G)
LOG_MAX_FILES=7                             # Keep last N files

# Log Path Examples (cross-platform):
# Development (local)
LOG_PATH=./logs/app.log                     # Project logs folder

# Production Windows
LOG_PATH=%LOCALAPPDATA%\\MyApp\\logs\\app.log   # C:\Users\Name\AppData\Local\MyApp\logs\
LOG_PATH=%APPDATA%\\MyApp\\logs\\app.log        # C:\Users\Name\AppData\Roaming\MyApp\logs\

# Production Linux/Mac
LOG_PATH=$HOME/.local/share/myapp/logs/app.log  # /home/user/.local/share/myapp/logs/
LOG_PATH=/var/log/myapp/app.log                 # System logs (needs permissions)
LOG_PATH=~/myapp/logs/app.log                   # Shorthand for home dir

# Temp directories
LOG_PATH=%TEMP%\\myapp\\app.log             # Windows temp
LOG_PATH=$TMPDIR/myapp/app.log              # Unix temp
```

**Usage Example:**
```javascript
const logger = require('./logger');

// API endpoint
app.get('/api/users', (req, res) => {
  const start = Date.now();
  logger.info('GET /api/users', { userId: req.user?.id });
  
  try {
    const users = getUsersFromDB();
    logger.info('GET /api/users success', { 
      count: users.length, 
      duration: Date.now() - start 
    });
    res.json(users);
  } catch (err) {
    logger.error('GET /api/users failed', { 
      error: err.message, 
      stack: err.stack 
    });
    res.status(500).json({ error: 'Internal error' });
  }
});
```

**Log File Structure:**
```
logs/
├── app-2025-01-29.log              ← Today (active)
├── app-2025-01-29.log.2025-01-29T14-30-15-123Z  ← Rotated (too big)
├── app-2025-01-28.log              ← Yesterday
├── app-2025-01-27.log
└── ...
(Keeps only LOG_MAX_FILES newest files)
```

**Production Best Practices:**
- Use `LOG_LEVEL=info` or `warn` (not debug)
- Enable `LOG_TO_FILE=true` with rotation
- Use `LOG_FORMAT=json` for parsing by monitoring tools
- Use system paths: `/var/log/myapp` (Linux) or `%LOCALAPPDATA%\MyApp` (Windows)
- Set up log shipping to centralized system (Sentry, ELK, CloudWatch, Datadog)
- Set up alerts for ERROR/FATAL levels
- Regular log review for security events
- Verify log directory permissions (not world-readable)

**Development Best Practices:**
- Use `LOG_LEVEL=debug` for troubleshooting
- Console output (`LOG_TO_FILE=false`) is fine
- Or use `LOG_PATH=./logs/app.log` for local files
- Review logs frequently to catch issues early

**Setup Checklist (Stage 1):**
- [ ] Create `logger.js` with full implementation above
- [ ] Add all LOG_* variables to `.env.example` with examples
- [ ] Add to `.gitignore`: `logs/`, `*.log`, `*.log.*`
- [ ] Test all log levels: `logger.debug/info/warn/error()`
- [ ] Test file creation: Set `LOG_TO_FILE=true`, verify file created
- [ ] Test rotation: Create large log, verify rotation happens
- [ ] Test cleanup: Verify old files deleted (keep LOG_MAX_FILES)
- [ ] Test cross-platform paths: Verify env var expansion works
- [ ] Verify logs NOT committed to git
- [ ] Verify log files have proper permissions (640 on Unix)


### Development Order (Enforce)

1. **Foundation first** — Config, DB, Auth
2. **Test foundation** — Don't continue if broken
3. **Core features** — One by one, tested
4. **Advanced features** — Only after core works

### File Size Guidelines

**Target sizes (lines of code):**
- **< 300** : ideal
- **300-500** : acceptable
- **500-800** : consider splitting
- **> 800** : must split

**When to split a file:**
- Multiple unrelated concerns in the same file
- Hard to find functions/methods
- File has too many responsibilities
- Scrolling endlessly to find something

**Naming convention for split files:**
```
app.go           → Core struct, New(), Run(), Shutdown()
app_jobs.go      → Job-related methods
app_sync.go      → Sync-related methods
app_settings.go  → Config/settings methods
```

**Benefits of smaller files:**
- Easier to navigate and understand
- Cleaner git diffs
- Less merge conflicts
- Faster incremental compilation
- More focused tests

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
# [Project] - Session State

> **Claude : Appliquer le protocole de session (CLAUDE.md)**
> - Créer/mettre à jour la session en temps réel
> - Valider après chaque module avec : ✅ [Module] complete. **Test it:** [...] Waiting for validation.
> - Ne pas continuer sans validation utilisateur
```

This ensures Claude applies the session protocol when the user asks to read SESSION_STATE.md.

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
| DB Connection | ✅ | YYYY-MM-DD |
| Auth | ✅ | YYYY-MM-DD |

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
📋 **Module:** [Name]
📝 **Purpose:** [One sentence]
📁 **Files:** [List]
🔗 **Depends on:** [Previous modules]
🧪 **Test procedure:** [How to verify]
🔒 **Security concerns:** [If any]
```

### 2. Implement

- Write minimal working code
- Include error handling
- Document as you go (headers, comments)

### 3. Validate

**Functional:**
- [ ] Runs without errors
- [ ] Expected output verified
- [ ] Errors handled gracefully

**Security (if applicable):**
- [ ] Input validated
- [ ] No hardcoded secrets, paths, or credentials
- [ ] Parameterized queries (SQL)
- [ ] Output encoded (XSS)

### 4. User Confirmation

**⚠️ DO NOT proceed until user says "OK", "validated", or "continue"**

---

## Build Order Templates

### Web Application

```
Stage 1: Foundation (validate before Stage 2)
├── [ ] Project structure + config module → starts without error
├── [ ] .env.example with all variables documented (including LOG_*)
├── [ ] Logging infrastructure → logger.js + test at all levels
├── [ ] Database connection (from env var) → can connect
├── [ ] Auth (register/login/logout) → full flow works
├── [ ] Session/JWT management → persists correctly
└── [ ] SECURITY REVIEW

Stage 2: Core (validate before Stage 3)
├── [ ] User profile CRUD
├── [ ] Basic API routes
└── [ ] Error handling middleware

Stage 3: Features
├── [ ] Feature A
├── [ ] Feature B
└── [ ] ...

Stage 4: Pre-Launch (MANDATORY)
├── [ ] Full security audit (see checklist)
├── [ ] Dependency audit (npm audit, etc.)
├── [ ] Penetration testing
├── [ ] Portability test (deploy on clean machine)
├── [ ] DEPLOYMENT.md written
├── [ ] All issues fixed or documented
└── [ ] Final validation
```

### API Service

```
Stage 1: Foundation
├── [ ] Config module + .env.example (including LOG_*)
├── [ ] Logging infrastructure → logger.js working
├── [ ] Database + migrations (connection from env)
├── [ ] Auth middleware
└── [ ] Health check endpoint

Stage 2: Core Endpoints
├── [ ] Resource A (CRUD)
├── [ ] Resource B (CRUD)
└── [ ] Relationships

Stage 3: Advanced
├── [ ] Search/filtering
├── [ ] Pagination
└── [ ] Rate limiting

Stage 4: Pre-Launch (MANDATORY)
├── [ ] Full security audit
├── [ ] Dependency vulnerabilities checked
├── [ ] API penetration testing
├── [ ] Portability test (fresh environment)
├── [ ] DEPLOYMENT.md written
├── [ ] Rate limiting verified
└── [ ] Final validation
```

### DEPLOYMENT.md Template

```markdown
# Deployment Guide

## Requirements
- [Runtime] v[version]
- [Database] v[version]
- [Other dependencies]

## Environment Variables
| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| PORT | No | 3000 | Server port |
| DATABASE_URL | Yes | - | Database connection string |
| ... | ... | ... | ... |

## Quick Start
1. Clone repository
2. Copy `.env.example` to `.env`
3. Edit `.env` with your values
4. Run `[install command]`
5. Run `[start command]`

## Production Deployment
[Platform-specific instructions]

## Troubleshooting
[Common issues and solutions]
```

---

## Documentation Standards

### File Header (Required)

```javascript
/**
 * @file filename.ext
 * @description Brief purpose
 * @created YYYY-MM-DD
 */
```

### Function Documentation (Required)

```javascript
/**
 * Brief description
 * @param {type} name - Description
 * @returns {type} Description
 */
```

### .EXPLAIN.md Files

Create for complex scripts/modules:

```markdown
# [Filename]

## Purpose
[What and why]

## Usage
[Code example]

## Key Functions
[List with brief descriptions]
```

---

## Pre-Launch Security Audit

### When to Run

**MANDATORY before any deployment or "project complete" status.**

Plan this phase from the start — it's not optional.

### Security Audit Checklist

#### 1. Code Review (Full Scan)
- [ ] No hardcoded secrets (API keys, passwords, tokens)
- [ ] No hardcoded paths (use relative or configurable)
- [ ] No hardcoded credentials or connection strings
- [ ] No sensitive data in logs
- [ ] All user inputs validated and sanitized
- [ ] No debug/dev code left in production
- [ ] `.env.example` present with all required variables
- [ ] `.gitignore` excludes `.env` and sensitive files

#### 2. OWASP Top 10 Check
- [ ] **Injection** — SQL, NoSQL, OS command injection protected
- [ ] **Broken Auth** — Strong passwords, session management, MFA if needed
- [ ] **Sensitive Data Exposure** — Encryption at rest and in transit (HTTPS)
- [ ] **XXE** — XML parsing secured (if applicable)
- [ ] **Broken Access Control** — Authorization verified on all routes
- [ ] **Security Misconfiguration** — Default credentials removed, error messages generic
- [ ] **XSS** — Output encoding, CSP headers
- [ ] **Insecure Deserialization** — Untrusted data not deserialized
- [ ] **Vulnerable Components** — Dependencies updated, no known CVEs
- [ ] **Insufficient Logging** — Security events logged, logs protected

#### 3. Dependency Audit
```bash
# Run appropriate command for your stack:
npm audit                    # Node.js
pip-audit                    # Python
cargo audit                  # Rust
dotnet list package --vulnerable  # .NET
```
- [ ] All critical/high vulnerabilities addressed
- [ ] Outdated packages updated or justified

#### 4. Online Vulnerability Research
- [ ] Search CVE databases for stack components
- [ ] Check GitHub security advisories for dependencies
- [ ] Review recent security news for frameworks used

**Resources:**
- https://cve.mitre.org
- https://nvd.nist.gov
- https://github.com/advisories
- https://snyk.io/vuln

#### 5. Basic Penetration Testing
- [ ] SQL injection attempts on all inputs
- [ ] XSS attempts on all outputs
- [ ] Auth bypass attempts (direct URL access, token manipulation)
- [ ] Rate limiting verified (brute force protection)
- [ ] File upload restrictions tested (if applicable)
- [ ] CORS policy verified

#### 6. Configuration Security
- [ ] HTTPS enforced
- [ ] Security headers present (HSTS, CSP, X-Frame-Options, etc.)
- [ ] Cookies secured (HttpOnly, Secure, SameSite)
- [ ] Error pages don't leak stack traces
- [ ] Admin interfaces protected/hidden

#### 7. Logging Security
- [ ] No passwords, tokens, API keys, secrets in logs
- [ ] No credit card numbers, SSNs, personal IDs in logs
- [ ] Session tokens not logged (only hash/ID if needed)
- [ ] Full request/response bodies not logged if sensitive
- [ ] Log files not publicly accessible (outside webroot)
- [ ] Log rotation configured (max size/count)
- [ ] Production uses INFO or WARN level (not DEBUG)
- [ ] File permissions restrict log access (chmod 640 or stricter)
- [ ] Security events are logged (auth failures, injection attempts)

### Audit Report Template

```markdown
# Security Audit Report

**Project:** [Name]
**Date:** YYYY-MM-DD
**Audited by:** [Claude / Human / Both]

## Summary
- Critical issues: X
- High issues: X
- Medium issues: X
- Low issues: X

## Findings

### [CRITICAL/HIGH/MEDIUM/LOW] Issue Title
- **Location:** [File:line or endpoint]
- **Description:** [What's wrong]
- **Risk:** [Impact if exploited]
- **Fix:** [How to resolve]
- **Status:** [ ] Fixed / [ ] Accepted risk

## Dependency Audit Results
[Paste output]

## Checklist Completion
[Copy checklist with status]

## Conclusion
[ ] Ready for launch
[ ] Requires fixes before launch
```

### Post-Audit Actions

1. **Critical/High issues** → Fix immediately, re-test
2. **Medium issues** → Fix before launch or document accepted risk
3. **Low issues** → Add to backlog
4. **Re-run audit** after fixes

---

## Git Integration

### Branch Naming
`feature/session-XXX-brief-name`

### Commit Message
```
Session XXX: [Summary]

- Change 1
- Change 2
```

---

## 🔒 Git Best Practices & .gitignore

### Critical Rules (MANDATORY)

**NEVER commit to repository:**
- ❌ Secrets, credentials, API keys, tokens
- ❌ `.env` files (commit `.env.example` only)
- ❌ Database files (SQLite .db, etc.)
- ❌ Log files, debug outputs
- ❌ IDE/editor configurations (user-specific)
- ❌ Build artifacts, compiled binaries
- ❌ Dependency directories (node_modules, venv, vendor)
- ❌ OS-specific files (.DS_Store, Thumbs.db)
- ❌ Temporary files, caches
- ❌ Large binary files (images/videos in development)
- ❌ Personal notes, TODO lists (unless project-related)

**ALWAYS commit:**
- ✅ `.env.example` (template with placeholders)
- ✅ `.gitignore` (comprehensive)
- ✅ README.md, DEPLOYMENT.md, CLAUDE.md
- ✅ Source code, configuration templates
- ✅ Database migrations/schemas (NOT the actual data)
- ✅ Documentation, LICENSE
- ✅ CI/CD configuration files

### Universal .gitignore Template

```gitignore
# === SECRETS & CREDENTIALS (CRITICAL) ===
.env
.env.local
.env.*.local
*.key
*.pem
*.p12
*.pfx
secrets/
credentials/
config.json
!config.example.json

# === LOGS ===
logs/
*.log
npm-debug.log*
yarn-debug.log*
yarn-error.log*
pnpm-debug.log*

# === DATABASES ===
*.db
*.sqlite
*.sqlite3
*.db-shm
*.db-wal
data/
*.mdb

# === DEPENDENCY DIRECTORIES ===
node_modules/
jspm_packages/
bower_components/
vendor/
packages/

# Python
__pycache__/
*.py[cod]
*$py.class
.Python
venv/
env/
ENV/
.venv

# Ruby
/.bundle
/vendor/bundle

# === BUILD OUTPUTS ===
dist/
build/
out/
target/
*.exe
*.dll
*.so
*.dylib
*.o
*.a
*.class
*.jar
*.war
*.ear

# === IDE & EDITORS ===
# VSCode
.vscode/
!.vscode/extensions.json
!.vscode/settings.json.example

# JetBrains
.idea/
*.iml
*.iws

# Sublime Text
*.sublime-project
*.sublime-workspace

# Vim
*.swp
*.swo
*~

# Emacs
*~
\#*\#
.\#*

# === OS FILES ===
# macOS
.DS_Store
.AppleDouble
.LSOverride
._*

# Windows
Thumbs.db
ehthumbs.db
Desktop.ini
$RECYCLE.BIN/

# Linux
*~
.directory
.Trash-*

# === TEMPORARY & CACHE ===
tmp/
temp/
*.tmp
*.bak
*.cache
.cache/
.sass-cache/
.npm/
.eslintcache
.parcel-cache/

# === TESTING ===
coverage/
.nyc_output/
*.lcov
.pytest_cache/
junit.xml

# === MISCELLANEOUS ===
*.orig
*.rej
.DS_Store?
```

### Project-Specific Additions

#### Node.js / JavaScript
```gitignore
# Additional for Node.js projects
.pnp
.pnp.js
.yarn/cache
.yarn/unplugged
.yarn/build-state.yml
.yarn/install-state.gz
.next/
.nuxt/
.vercel
```

#### Python
```gitignore
# Additional for Python projects
*.egg-info/
.eggs/
dist/
.tox/
.mypy_cache/
.dmypy.json
dmypy.json
.pyre/
.pytype/
.ruff_cache/
```

#### Java / Maven / Gradle
```gitignore
# Additional for Java projects
target/
.gradle/
build/
*.class
*.jar
*.war
hs_err_pid*
```

#### Rust
```gitignore
# Additional for Rust projects
target/
Cargo.lock  # Commit for binaries, ignore for libraries
**/*.rs.bk
```

#### Go
```gitignore
# Additional for Go projects
*.exe
*.test
*.out
vendor/
go.work
go.work.sum
```

### Pre-Commit Checklist

**Before every commit, verify:**
- [ ] No `.env` or secrets in staged files
- [ ] No absolute paths in code (`C:\Users\...`, `/home/user/...`)
- [ ] No hardcoded credentials or API keys
- [ ] No temporary debug code (`console.log`, `print()`, etc.)
- [ ] No large binary files (>1MB) unless necessary
- [ ] `.gitignore` is up to date
- [ ] Commit message follows convention (see Git Integration)

### Emergency: Committed Secrets by Mistake

**If you accidentally committed secrets:**

```bash
# 1. Remove from last commit (not pushed yet)
git rm --cached .env
git commit --amend --no-edit

# 2. If already pushed (CRITICAL - ACT IMMEDIATELY)
# a) Rotate/revoke ALL exposed credentials NOW
# b) Remove from history (force push required)
git filter-branch --force --index-filter \
  'git rm --cached --ignore-unmatch .env' \
  --prune-empty --tag-name-filter cat -- --all
git push --force --all

# 3. For complex cases, use BFG Repo-Cleaner
# https://rtyley.github.io/bfg-repo-cleaner/
```

**IMPORTANT:** Removing from Git history is not enough. Secrets must be:
1. **Immediately revoked/rotated** (API keys, passwords, tokens)
2. **Reported** if company/team credentials
3. **Monitored** for unauthorized use

### .gitignore Testing

```bash
# Test what would be ignored
git check-ignore -v path/to/file

# Test entire directory
git status --ignored

# Force add a file if needed (use with caution)
git add -f path/to/file
```

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

---

**Last Updated:** 2025-01-29  
**Version:** 3.2.0