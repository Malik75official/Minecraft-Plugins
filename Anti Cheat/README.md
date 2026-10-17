# AntiCheat

Eigenständiges Paper-Plugin für Minecraft 26.2 (Java 25). Es prüft auffällige Bewegung, Combat-Reichweite/Clickrate und ungewöhnlich viele nicht freiliegende Diamanterze bzw. Ancient Debris. Die Erkennung erzeugt ausschließlich Staff-Alerts und Logs; es gibt keine automatischen Kicks, Bans oder sonstigen Strafen.

## Projektstruktur

```text
src/main/java/de/malik/anticheat/
├── AntiCheatPlugin.java          # Plugin-Start, Registrierung und gemeinsame Verdachtsmeldungen
├── CheckType.java                # Typen der erkannten Checks
├── alerts/AlertManager.java      # Staff-Alerts, VL-Zähler, Cooldowns und Logdatei
├── checks/
│   ├── combat/CombatCheck.java   # Reach- und Klickrate-Prüfung
│   ├── movement/MovementCheck.java # Speed- und Fly-Prüfung
│   └── xray/XrayCheck.java       # Erzstatistik und clientseitige X-Ray-Köder
└── commands/AntiCheatCommand.java # /anticheat und /ac

src/main/resources/
├── config.yml                    # Grenzwerte und Check-Einstellungen
└── plugin.yml                    # Paper-Metadaten, Befehl und Permissions
```

## Check-Bypass-Permissions

Ein Perk- oder Permissions-Plugin kann einem Spieler gezielt einzelne Checks freischalten:

- `anticheat.bypass.fly` – umgeht nur den Fly-Check.
- `anticheat.bypass.speed` – umgeht nur den Speed-Check.
- `anticheat.bypass.reach` – umgeht nur den Reach-Check.
- `anticheat.bypass.autoclicker` – umgeht nur den AutoClicker-Check.
- `anticheat.bypass.xray` – umgeht nur den X-Ray-Check und die X-Ray-Köder.

Alle Bypass-Permissions sind standardmäßig deaktiviert. Ein Bypass schaltet keine Fähigkeit frei: Ein Flug-Perk muss zusätzlich separat die Paper-/Server-Berechtigung zum Fliegen vergeben und den Flug für den Spieler aktivieren. Je nachdem, wie das Perk funktioniert, kann es zusätzlich `anticheat.bypass.speed` benötigen.

Bei einem X-Ray-Verdacht zeigt das Plugin dem betroffenen Spieler clientseitige Erz-Köder in geladenen Chunks. Sie ändern die echte Welt nicht. Wird ein Köder abgebaut, wird der Abbau abgebrochen und als weiterer Verdacht protokolliert. Anzahl und Radius sind unter `checks.xray.decoys` einstellbar. Paper kann nicht direkt erkennen, ob ein Spieler einen bestimmten Client-Mod installiert hat; die Köder werden daher erst nach einem X-Ray-Verdacht aktiviert.

## Build und Installation

Aus dem Workspace-Ordner ausführen:

```powershell
& '.\server system\mvnw.cmd' -f "$PWD\Anti Cheat\pom.xml" package
```

Beim Serverstart gibt das Plugin einen großen, farbigen `ANTI CHEAT BY MALIK75`-Schriftzug in der Konsole aus.

Die fertige Plugin-Datei liegt unter `Anti Cheat\target\anti-cheat-1.0.4.jar`. Diese Datei in den `plugins`-Ordner des Paper-Servers kopieren und den Server starten.

## Befehle und Berechtigungen

- `/anticheat alerts` oder `/ac alerts` schaltet die Alerts für den eigenen Spieler um (`anticheat.alerts`, standardmäßig OP).
- `/anticheat reload` lädt die Konfiguration neu (`anticheat.admin`, standardmäßig OP).

Verdachtsmeldungen erscheinen in der Konsole und werden in `plugins/AntiCheat/logs/checks.log` gespeichert. Die Grenzwerte und Checks können in `plugins/AntiCheat/config.yml` angepasst werden.

Die Checks sind heuristisch und können durch Latenz, Effekte oder ungewöhnliche Spielweisen beeinflusst werden. Verdachtsmeldungen sollten vor Maßnahmen manuell geprüft werden.
