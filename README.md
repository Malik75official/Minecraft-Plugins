# Minecraft-Plugins
Minecraft Plugins

This repository contains the `Anti Cheat` and `server system` projects. Mob-System features are integrated into `server system`.

## Automatic GitHub sync

On Windows, the scheduled task `Minecraft Plugins GitHub Auto Sync` runs `sync-to-github.ps1` when you sign in. It checks for changes, waits five seconds after the latest edit, then commits and pushes them to the `main` branch of [LarsvanMozart/Minecraft-Plugins](https://github.com/LarsvanMozart/Minecraft-Plugins).

Generated Maven output in `target` directories is excluded. Sync errors and retries are logged to `.git-sync/sync.log`. Git Credential Manager must be signed in to an account with write access to the repository.
