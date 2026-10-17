# ServerSystem

Paper-Plugin für Minecraft 26.2, Version 1.1.1. Benötigt ein JDK 25. Maven wird über den Windows-Wrapper beim ersten Build heruntergeladen.

## Build

```powershell
.\mvnw.cmd package
```

Die Plugin-Datei liegt anschließend unter `target/server-system-1.1.1.jar`.
Kopiere sie in den `plugins`-Ordner deines Paper-Servers und starte den Server neu.

## Befehle

- Economy: `/balance`, `/pay`, `/payall`, `/eco`
- Homes und Warps: `/sethome [Name]`, `/home [Name]`, `/homes`, `/delhome <Name>`, `/setwarp`, `/warp`, `/delwarp`
- Farmwelt: `/farmwelt` (Zielserver in `config.yml`; benötigt einen BungeeCord-kompatiblen Proxy)
- Links: `/discord`, `/webseite` (`/website`, `/web`), `/webshop` (Texte in `config.yml`)
- Spawn: `/setspawn`, `/spawn`
- Utilities: `/ec`, `/craft`, `/amboss`, `/ofen` (öffnet einen echten Ofen im Umkreis von 5 Blöcken)
- Sidebar: konfigurierbar in `plugins/ServerSystem/scoreboard.yml`
- Inventar-Sync: MySQL/MariaDB-Verbindung in `plugins/ServerSystem/config.yml`
- Moderation: `/kick`, `/ban`, `/unban`, `/mute`, `/unmute`

Den Spawn-Schutzradius kannst du in `plugins/ServerSystem/config.yml` einstellen.

## Prefixe

Prefixe für Chat, TAB-Spielerliste und Nametag stellst du in `plugins/ServerSystem/prefixes.yml` ein. Die höchste passende Permission aus `priority` gewinnt, zum Beispiel `prefix.owner`, `prefix.developer`, `prefix.premium` oder `prefix.spieler`. Farben und Formate verwenden MiniMessage-Tags. Ohne passende Permission wird kein Prefix angezeigt.

## Permissions

Jeder Befehl verwendet `serversystem.command.<befehl>`, zum Beispiel `serversystem.command.home` oder `serversystem.command.ban`. Die Nodes für normale Spielerbefehle sind standardmäßig offen; Admin- und Moderationsbefehle standardmäßig OP-only. Ein externes Permission-Plugin kann diese Nodes individuell vergeben.

`/payall <Betrag>` zahlt an alle Online-Spieler aus und benötigt `serversystem.command.payall`.

`/ban <Spieler> <Dauer> <Grund>` akzeptiert `s` (Sekunden), `m` (Minuten), `h` (Stunden), `d` (Tage), `w` (Wochen), `y` (365 Tage) und zusammengesetzte Dauern wie `2h30m`. `perma` erstellt einen permanenten Bann.

Die Utility-Befehle haben einzelne Nodes: `serversystem.command.ec`, `serversystem.command.craft`, `serversystem.command.amboss` und `serversystem.command.ofen`.

Zusätzlich benötigt `/balance <Spieler>` die Permission `serversystem.economy.admin`. `serversystem.protection.bypass` umgeht den Spawn-Schutz.

Spieler ohne `spawn.override` werden bei jedem Join zum gesetzten Server-Spawn teleportiert; ist keiner gesetzt, wird der Spawn der ersten Welt verwendet.

Das Home-Limit richtet sich nach der höchsten vergebenen Permission: `serversystem.maxhome.5`, `.10`, `.15`, `.20` oder `.25`. Ohne diese Permissions bleibt das bisherige Limit von einem Home bestehen. Alte einzelne Homes werden beim ersten Zugriff in ein Home namens `home` migriert.

Für serverübergreifende Inventare müssen alle Paper-Server dieselbe MySQL/MariaDB-Datenbank verwenden. Trage auf jedem Server dieselbe JDBC-URL, denselben Benutzernamen und dasselbe Passwort in `config.yml` ein und setze `inventory-sync.enabled: true`. Dann werden Inventar, Rüstung, Zweithand und Enderchest bei Login geladen, beim Verlassen gespeichert und zusätzlich alle fünf Minuten gesichert. Nach erfolgreichem Speichern kann eine formatierte Chatnachricht mit der Dauer in Millisekunden ausgegeben werden. Wenn die Datenbank beim Login nicht erreichbar ist, wird der Login abgelehnt, damit kein leeres Inventar den gespeicherten Stand überschreibt.