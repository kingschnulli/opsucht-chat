# Architektur

## Grundidee

Der Parser darf nicht an einen Modloader gekoppelt sein. `core` verarbeitet ausschließlich einfache Java-Daten (`String`, Zeitstempel, Quelle) und kennt keine Minecraft-Klassen.

Die Minecraft-Integration übernimmt nur:

1. eine eingehende `GuiMessage` beobachten,
2. sie an den Core übergeben,
3. die Klassifikation zur Nachricht merken,
4. den sichtbaren Vanilla-Chat anhand des ausgewählten Tabs filtern.

## Warum ALL immer alles enthält

`ALL` ist das Sicherheitsnetz. Neue oder geänderte Opsucht-Formate können dadurch nie vollständig versteckt werden. Spezifische Tabs sind zusätzliche Ansichten, kein destruktives Routing.

## Warum der Vanilla-Chat erhalten bleibt

Minecraft 26.2 stellt am `ChatComponent` einen sichtbaren Nachrichtenfilter bereit. Dadurch müssen wir Chatzeilen nicht kopieren oder selbst neu rendern. Formatierung, Hover-/Klick-Aktionen und die eigentliche Chat-History bleiben bei Minecraft.

## Zustandsbehaftete Regeln

Einige Formate können nicht aus einer einzelnen Zeile verstanden werden. Auktionen besitzen deshalb einen kleinen Sitzungszustand mit Timeout. Der Core kann später auf dieselbe Weise weitere server-spezifische Kontexte erhalten.

## Plattformadapter

Fabric und NeoForge verwenden für Minecraft 26.2 denselben gemeinsamen Minecraft-Code. Nur Bootstrap/Metadata unterscheiden sich.

Ein LabyMod-Adapter soll denselben Core verwenden, aber Labys natives Chat-/Addon-System ansprechen, statt ungeprüft die normale Chat-GUI zu mixen.


## Nachrichtenquelle ist keine Kategorie

Auf großen Minecraft-Netzwerken wird normaler Spielerchat häufig als Server-Systemnachricht an den Client geschickt. Deshalb gilt `SYSTEM_SERVER` **nicht** automatisch als `SERVER`. Der Core sortiert nur über explizite, getestete Formate. Das verhindert, dass normaler Chat versehentlich im SERVER-Tab verschwindet.

## Build-Isolation

Fabric Loom und NeoForge ModDevGradle benötigen derzeit unterschiedliche Gradle-Stände. CI und Releases bauen die Loader deshalb in getrennten Jobs. Der gemeinsame Core bleibt davon unabhängig.


## Aktivierung nur auf Opsucht

Der gemeinsame Core enthält auch die Host-Erkennung. Die Minecraft-Adapter greifen nur auf `opsucht.net` und Subdomains wie `java.opsucht.net` in den Chat ein. Auf anderen Servern läuft der originale Vanilla-Chatpfad unverändert weiter.
