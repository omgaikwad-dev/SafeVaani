---
trigger: always_on
description: Enforce completely empty git commit messages
---

# Git Commit Rules

- **Zero Commit Message**: Whenever staging, committing, and pushing files or directories, **NEVER** write a message. Do not include features, fixes, descriptions, or file names.
- Always execute:
  ```bash
  git commit --allow-empty-message -m ""
  ```
- All commits must remain completely empty across all projects, files, and folders.
