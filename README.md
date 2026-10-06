# Opsucht Chat

Ein kleiner, komplett clientseitiger Chat-Organizer für **opsucht.net**. Er aktiviert sich nur auf `opsucht.net` bzw. Subdomains wie `java.opsucht.net` und lässt andere Server unangetastet.

Das Ziel ist nicht, den Minecraft-Chat neu zu erfinden. Opsucht Chat lässt den normalen Chat inklusive Formatierungen, Hover-Texten und Klick-Aktionen bestehen und ergänzt nur sinnvolle Tabs und eine server-spezifische Sortierung.

## Tabs

- **ALL** – wirklich alle Nachrichten, unverändert und als Sicherheitsnetz
- **PN [x]** – Privatnachrichten, inklusive Ungelesen-Zähler
- **AUKTION** – Start, Gebote und Abschluss einer laufenden Versteigerung
- **SERVER** – Server-/Systemmeldungen
- **WERBUNG** – erkannte Werbung

Unbekannte Nachrichten bleiben immer in **ALL**. Eine unvollständige Regel kann deshalb niemals eine Nachricht vollständig verschwinden lassen.

## Auktionen

Auktionen werden nicht nur über einen einzelnen Regex gefiltert. Der Core merkt sich eine laufende Auktion:

```text
Auktionsstart -> Auktion aktiv -> Gebot -> Gebot -> verkauft/abgebrochen -> beendet
```

Dadurch können zusammengehörige Meldungen im AUKTION-Tab bleiben, auch wenn nicht jede Zeile denselben Präfix besitzt.

## Installation

### Prism / Fabric

1. Minecraft-Instanz mit Fabric 26.2 verwenden.
2. Den Fabric-Build aus den GitHub Releases in den `mods`-Ordner legen.
3. Minecraft starten.

### Prism / NeoForge

1. Minecraft-Instanz mit NeoForge 26.2 verwenden.
2. Den NeoForge-Build aus den GitHub Releases in den `mods`-Ordner legen.
3. Minecraft starten.

### Dawn

Dawn kann Fabric-/NeoForge-Profile starten. Dort einfach den passenden Build verwenden.

### LabyMod 4

LabyMod 4 kann auf Minecraft 26.2 Fabric-Mods mitladen. Deshalb ist **kein dritter Sonder-Build nötig**: in einem LabyMod-Profil mit aktiviertem Fabric Loader wird zunächst derselbe Fabric-Build verwendet. Da LabyMod selbst tief in den Chat eingreift, behandeln wir diese Kombination bis zum Praxistest als **experimentell**; insbesondere Labys „Advanced Chat“ kann ein Konfliktkandidat sein.

Falls sich dabei echte Inkompatibilitäten zeigen, bleibt der Parser-Core loader-unabhängig und wir können einen dünnen nativen LabyMod-Adapter ergänzen, ohne die Opsucht-Regeln zu duplizieren.

Ein komplett unveränderter Vanilla-Client kann seine Chat-GUI nicht durch eine JAR aus diesem Projekt erweitern. Dafür ist immer ein Client-Loader bzw. Client-Addon nötig.

## Bedienung

Die Tabs werden direkt oberhalb der Chat-Eingabe angezeigt. Ein Klick wechselt die Ansicht.

Lokale Befehle:

```text
/opschat
/opschat tab all
/opschat tab pn
/opschat tab auktion
/opschat tab server
/opschat tab werbung
/opschat debug
```

`/opschat debug` aktiviert bzw. deaktiviert einen **lokalen** Diagnose-Log unter:

```text
.minecraft/opsucht-chat/debug-chat.jsonl
```

Der Debug-Modus ist standardmäßig aus. Es gibt **keine Telemetrie und keinen Upload**. Wer einen Log in einem Issue veröffentlicht, sollte Spielernamen oder private Nachrichten vorher prüfen/anonymisieren.

## Community-Projekt

- kostenlos und Open Source
- MIT-Lizenz
- keine Accounts
- keine Werbung
- keine Telemetrie
- kein eigener Backend-Service
- keine Gameplay-Automatisierung
- keine Serverinstallation erforderlich

Das Projekt sortiert nur Chat-Nachrichten, die der eigene Client ohnehin erhält.

## Entwicklung

Der intelligente Teil liegt im loader-unabhängigen `core/`. Die Minecraft-Integration ist von den Loader-Adaptern getrennt:

```text
core/                    Parser, Auktionen, Kategorien, Tests
minecraft/26.2/          gemeinsame Minecraft-26.2-UI/Chat-Integration
platforms/fabric/        Fabric-Einstiegspunkt
platforms/neoforge/      NeoForge-Einstiegspunkt
```

Build lokal mit JDK 25. Wegen der aktuellen Toolchains werden die Loader getrennt gebaut:

```bash
# Core + NeoForge (Gradle 9.2.1)
gradle --configure-on-demand :core:test :platforms:neoforge:build

# Fabric (Gradle 9.7.1)
gradle --configure-on-demand :platforms:fabric:build
```

Die GitHub Actions verwenden für jeden Loader automatisch die passende Gradle-Version.

## Releases

GitHub Actions testet den gemeinsamen Core und baut Fabric sowie NeoForge in getrennten, reproduzierbaren Jobs automatisch. Ein Release kann in GitHub über **Actions → Release → Run workflow** mit einer Versionsnummer wie `0.1.0` gestartet werden. Der Workflow:

1. führt die Core-Tests aus,
2. baut Fabric und NeoForge,
3. erzeugt SHA-256-Prüfsummen,
4. erstellt den Git-Tag `v0.1.0`,
5. veröffentlicht beide JARs als GitHub Release.

Alternativ löst das Pushen eines Tags wie `v0.1.0` denselben Release-Build aus.

## Mithelfen

Siehe [CONTRIBUTING.md](CONTRIBUTING.md). Vor allem echte, anonymisierte Opsucht-Chatbeispiele helfen dabei, die Klassifizierung sauberer zu machen.

---

**Inoffizielles Community-Projekt.** Nicht mit opsucht.net, Mojang oder Microsoft verbunden oder von diesen unterstützt.
