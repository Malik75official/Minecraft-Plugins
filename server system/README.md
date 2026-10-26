# ServerSystem

Paper-Plugin für Minecraft 26.2, Version 1.9.4. Enthält ServerSystem, Case-Opening und Mob-System. Benötigt ein JDK 25. Maven wird über den Windows-Wrapper beim ersten Build heruntergeladen.

## Build

```powershell
.\mvnw.cmd package
```

Die Plugin-Datei liegt anschließend unter `target/server-system-1.9.4.jar`.
Kopiere sie in den `plugins`-Ordner deines Paper-Servers und starte den Server neu.

## Befehle

- Economy: `/balance`, `/pay`, `/payall`, `/eco`
- Bank: `/bank`, `/bank admin`, `/banknpc`
- Homes und Warps: `/sethome [Name]`, `/home [Name]`, `/homes`, `/delhome <Name>`, `/setwarp`, `/warp`, `/delwarp`
- Farmwelt: `/farmwelt` (Zielserver in `config.yml`; benötigt einen BungeeCord-kompatiblen Proxy)
- Links: `/discord`, `/webseite` (`/website`, `/web`), `/webshop` (Texte in `config.yml`)
- Spawn: `/setspawn`, `/spawn`
- Utilities: `/ec`, `/craft`, `/amboss`, `/ofen` (öffnet einen echten Ofen im Umkreis von 5 Blöcken)
- Spieler-Tools: `/fly [Spieler]`, `/i <Item> [Menge]`, `/vanish`
- Spielmodus: `/gm 0|1|2|3` oder `/gamemode <survival|creative|adventure|spectator> [Spieler]`
- Teleport: `/tp`, `/tpo`, `/tphere`, `/tpa`, `/tpahere`, `/tpaccept`, `/tpadeny`, `/tpacancel`
- Administration: `/invsee <Spieler>` (Bearbeiten benötigt `serversystem.command.invsee.edit`), `/lockchest`

Spieler-Tools, Vanish, direkte Teleports, Inventarzugriff und Kistensperren sind standardmäßig OP-only. `/tpa`-Anfragen sind für Spieler offen und werden per klickbaren Chat-Buttons angenommen oder abgelehnt. `/invsee` benötigt `serversystem.command.invsee`; Bearbeiten zusätzlich `serversystem.command.invsee.edit`.
- Sidebar: konfigurierbar in `plugins/ServerSystem/scoreboard.yml`
- Inventar-Sync: MySQL/MariaDB-Verbindung in `plugins/ServerSystem/config.yml`
- Moderation: `/kick`, `/ban`, `/unban`, `/mute`, `/unmute`
- Case-Opening: `/case`, `/case-set`, `/case-clear`, `/kristall` (`/crystals` als Alias)
- Case-Editor: `/case admin` (Admin-Permission `caseopening.admin`)
- Mob-System: `/mobspawn <mob> <level> [spieler]` (`/ms` als Alias); Mob-Level, Lebensanzeigen und Belohnungen konfigurierst du in `plugins/ServerSystem/mobs.yml`.

Den Spawn-Schutzradius kannst du in `plugins/ServerSystem/config.yml` einstellen.
Spieler-, Warp-, Moderations-, Spawn-, Bank-, Case-, Kristall-, Prefix- und Sidebar-Daten werden standardmäßig weiter lokal gespeichert. Für den optionalen gemeinsamen MySQL-Speicher setze `storage.mysql.enabled: true` in `config.yml` und trage JDBC-URL, Benutzer und Passwort ein. Vorhandene YAML-Daten werden beim ersten Start importiert; danach ist MySQL die Datenquelle. `config.yml` selbst bleibt lokal, damit die Verbindungseinstellungen verfügbar sind. Der Inventar-Sync bleibt separat über `inventory-sync.enabled` schaltbar; wenn beide Funktionen aktiviert sind, verwenden sie dieselbe MySQL-Datenbank und dieselben Zugangsdaten.
Mit `/banknpc` (Permission `serversystem.command.bank.npc`) platzierst du einen Bank-NPC. Spieler öffnen das Bank-GUI mit `/bank` oder durch Rechtsklick auf den NPC. Das Admin-GUI öffnest du mit `/bank admin` (`serversystem.command.bank.admin`); Zinsen und Kontoführungsgebühr werden täglich abgerechnet. Die Bank-Bestände und Einstellungen liegen in `bank.yml` beziehungsweise im optionalen gemeinsamen MySQL-Speicher.
Case-Definitionen, Preise, Animation und Belohnungen liegen in `plugins/ServerSystem/caseopening.yml`. Item-Rewards und Geld-Rewards lassen sich dort oder über `/case admin` verwalten. Geld wird direkt dem Economy-Guthaben gutgeschrieben. Kristall-Guthaben wird in `plugins/ServerSystem/crystals.yml` gespeichert.
Ein Geld-Reward in der YAML verwendet `type: money`, `amount` und `chance`; bei Item-Rewards ist `type: item` optional. Enchantments gehaltener Reward-Items werden beim Hinzufügen übernommen.
Vorhandene Case-Definitionen und Kristall-Guthaben aus `plugins/CaseOpening-v26.2` werden beim ersten Start übernommen.
Mit `/case-set` kannst du auch eine Enderchest als Case-Auswahl markieren; die Position wird in `caseopening.yml` gespeichert.

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