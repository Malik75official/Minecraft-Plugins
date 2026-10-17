# Mob System

Eigenständiges Paper-Plugin für Minecraft 26.2 (Java 25), Version 1.2.0.

- Über jedem geladenen Tier und Mob wird eine Lebensleiste mit aktuellem und maximalem Leben angezeigt.
- Treffer durch Spieler zeigen kurz den verursachten Schaden über dem Mob an.
- Normale Treffer werden grau und kritische Nahkampftreffer gelb dargestellt. Spielerprojektile zeigen ebenfalls ihren Schaden an.
- Tiere und andere Mobs erhalten beim ersten Erscheinen zufällig ein höheres Level. Level und erhöhte Lebenspunkte bleiben beim Entladen und Neustart erhalten.
- Höhere Level haben mehr maximale Lebenspunkte und geben beim Tod mehr Erfahrung sowie zusätzliche Gegenstände aus ihrer normalen Beute.
- Mit `/mobspawn <mob> <level> [spieler]` lassen sich Tiere und Mobs gezielt auf einem Level spawnen. Ohne Spielerangabe erscheint der Mob zwei Blöcke vor dem ausführenden Spieler; die Konsole muss einen Online-Spieler angeben. Tab-Vervollständigung hilft bei Mob-Namen, Leveln und Spielern.
- Die Anzeigen werden bei Tod, Chunk-Entladung und Plugin-Stopp aufgeräumt.

## Level und Belohnungen

Die Werte können in `plugins/MobSystem/config.yml` angepasst werden:

- `levels.spawn-chance`: Wahrscheinlichkeit für ein Level über 1 (Standard: 20 %).
- `levels.max-level`: höchstes zufälliges Level (Standard: 5); höhere Level werden gleichmäßig von 2 bis zum Maximum ausgewählt.
- `levels.health-per-level`: zusätzliche maximale Lebenspunkte pro Level über 1 (Standard: 20 %).
- `levels.command-max-level`: höchstes mit `/mobspawn` erlaubtes Level (Standard: 100).
- `rewards.xp-per-level`: zusätzlicher Erfahrungsmultiplikator pro Level über 1 (Standard: 50 %).
- `rewards.loot-per-level`: zusätzliche Beute pro Level über 1 (Standard: 25 %). Bruchteile werden zufällig gerundet.

## Befehl und Berechtigung

- `/mobspawn <mob> <level> [spieler]` oder `/ms`: Spawnt einen Mob zwei Blöcke vor dir oder vor dem angegebenen Online-Spieler. Mob-Namen wie `cow`, `polar_bear` und `minecraft:zombie` werden unterstützt.
- `mobsystem.spawn`: Berechtigung für den Befehl (standardmäßig OP).

## Build

Aus dem Workspace-Ordner:

```powershell
& '.\server system\mvnw.cmd' -f '.\Mob System\pom.xml' package
```

Die Plugin-Datei liegt danach unter `Mob System\target\mob-system-1.2.0.jar`. Kopiere sie in den `plugins`-Ordner deines Paper-Servers und starte den Server.
