# CompressedBlocks

Ein neu entwickeltes Paper-Plugin fuer Minecraft Java Edition 26.2. Es komprimiert
neun exakt gleiche Items zu einem sicheren 1x-Token. Neun gleiche 1x-Tokens
werden zu einem 2x-Token, neun 2x-Tokens zu einem 3x-Token usw. bis 9x.

## Eigenschaften

- funktioniert mit jedem registrierten Item (`Material#isItem`), auch nicht
  stapelbaren Items
- bewahrt Namen, Lore, Verzauberungen, Haltbarkeit, Trims, Buchinhalt,
  Trankdaten, PDC und Container-Inhalte bytegenau auf
- signierte Tokens verhindern das Faelschen per Creative/NBT
- `/compress` und `/decompress` funktionieren universell
- feste Kompressionsfolge von 1x bis 9x
- optionale 3x3-/1-Slot-Crafting-Bedienung
- Vanilla-Rezepte haben standardmaessig Vorrang
- keine NMS-/CraftBukkit-Interna und kein Resourcepack erforderlich

## Benutzung

1. Lege das JAR in `plugins/` eines Paper-26.2-Servers.
2. Starte den Server mit Java 25.
3. Halte ein Item in der Haupthand und nutze `/compress`. Der Befehl nimmt neun
   exakt gleiche Exemplare aus deinem Inventar. `/compress all` wiederholt das,
   solange genug Items und Platz vorhanden sind.
4. Halte einen Token in der Haupthand und nutze `/decompress` oder
   `/decompress all`.

## Kompressionsstufen

| Stufe | Benötigt | Enthaltene Original-Items |
| --- | --- | ---: |
| 1x | 9 Original-Items | 9 |
| 2x | 9 gleiche 1x-Items | 81 |
| 3x | 9 gleiche 2x-Items | 729 |
| 4x | 9 gleiche 3x-Items | 6.561 |
| 5x | 9 gleiche 4x-Items | 59.049 |
| 6x | 9 gleiche 5x-Items | 531.441 |
| 7x | 9 gleiche 6x-Items | 4.782.969 |
| 8x | 9 gleiche 7x-Items | 43.046.721 |
| 9x | 9 gleiche 8x-Items | 387.420.489 |

Die Obergrenze ist absichtlich fest auf 9x gesetzt. Ein alter
`max-compression-level`-Eintrag in `config.yml` wird ignoriert, damit ein
vorheriger Wert wie `3` die Kompression nicht mehr begrenzen kann.

Im 3x3-Craftingfeld ergeben neun gleiche Items ebenfalls einen Token, sofern
kein Vanilla-Rezept Vorrang hat. Ein einzelner Token wird eine x-Stufe entpackt,
wenn die neun Ausgaben einen legalen Stack bilden. Fuer Schwerter, Werkzeuge und
andere nicht stapelbare Items ist `/decompress` der sichere Weg.

Beim ersten Start entsteht `plugins/CompressedBlocks/secret.key`. Diese Datei
gehoert unbedingt ins Server-Backup: Ohne denselben Schluessel werden vorhandene
Tokens absichtlich als ungueltig abgewiesen.

## Bauen

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
.\gradlew.bat clean test build
```

Das fertige JAR liegt danach unter `build/libs/`.
