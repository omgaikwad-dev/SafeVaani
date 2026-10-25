# Project & Agent Guidelines

## Git Commit & Push Rules
- **Empty Commit Messages**: When staging, committing, and pushing files or directories, **NEVER** write descriptive commit messages (e.g., do not write `feat(...)`, `fix(...)`, `refactor(...)`, or any summary text).
- The commit message must always be completely empty across all files, folders, and commits:
  ```bash
  git commit --allow-empty-message -m ""
  ```
- When pushing to remote repositories, ensure no commit summaries or descriptions are added.
