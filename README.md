# Opsucht Chat

Ein moderner, komplett clientseitiger Chat für Minecraft Java. **OPSUCHT ist der erste Server-Adapter** und aktuell der Entwicklungsfokus.

Das Ziel ist ein Chat, der sich weiterhin wie Minecraft anfühlt, aber die Dinge übernimmt, die große MMOs seit Jahren besser machen: getrennte Feeds, echte PN-Unterhaltungen, lokale Social-Historie, strukturierte Servermeldungen und server-spezifische Adapter statt einer unlesbaren Textwand.

## Ansichten

- **ALL** – alle Nachrichten als Sicherheitsnetz
- **MSG** – normaler Spielerchat
- **PN [x]** – Social-/Privatnachrichtenbereich mit eigenen Unterhaltungen
- **AUKTION** – Auktionsstart, Gebote, Countdown und Abschluss
- **SERVER** – strukturierte Server-/Systemmeldungen
- **WERBUNG** – erkannte Werbung

Unbekannte Nachrichten landen in **MSG** und bleiben zusätzlich in **ALL**. Eine unvollständige Regel kann deshalb keine Nachricht vollständig verschwinden lassen.

## ChatFrame

Wenn der Chat geöffnet ist, rendert Opsucht Chat einen eigenen Minecraft-artigen ChatFrame:

- kompakter normaler Chat mit Skin, Rang, Spielername, Uhrzeit und Nachricht
- echte Tabs statt überlagerter Vanilla-Buttons
- Servermeldungen als typisierte Karten, z. B. Geld, Teleport, Vote, Markt, Job oder Booster
- Auktionen als eigene Timeline mit START / GEBOT / COUNT / VERKAUFT
- Scrollen direkt im aktiven Feed
- Klick auf einen Spielernamen öffnet eine PN-Unterhaltung
- anklickbare Server-Befehle werden sicher in die Eingabe übernommen und nicht automatisch ausgeführt

Der geschlossene HUD-Chat bleibt bewusst möglichst nah an Minecraft. Der umfangreiche ChatFrame wird erst beim Öffnen des Chats aktiv.

## Private Nachrichten & Social

PN ist kein normaler Textfilter mehr, sondern ein kleiner Social-Bereich.

Links stehen die letzten Unterhaltungen, rechts der Verlauf des ausgewählten Spielers. Pro Unterhaltung gibt es:

- Skin-Kopf, Spielername, Vorschau der letzten Nachricht und Uhrzeit
- Ungelesen-Zähler
- **★** Favorit/Pin
- **×** zum Schließen; geschlossene Sessions bleiben bis zur nächsten Nachricht oder zum erneuten Öffnen verborgen
- **+Freund** als server-spezifische Social-Aktion
- **Pay** mit einem festen, nicht editierbaren `/pay <Spieler>`-Präfix; eingegeben wird nur noch der Betrag
- lokale PN-Historie über Neustarts hinweg

Wird eine Spieler-Unterhaltung geöffnet, wird normal eingegebener Text automatisch über den Server-Adapter als PN verschickt. Auf OPSUCHT entspricht das:

```text
/msg <Spieler> <Nachricht>
```

Explizite Slash-Commands bleiben unverändert.

Einzelne PNs können per **Rechtsklick** mit ★ als wichtig markiert werden. Über den ★-Schalter im PN-Bereich können alle wichtigen PNs gemeinsam angezeigt werden.

Auf OPSUCHT nutzt **+Freund** den offiziellen Befehl `/freund hinzufügen <Name>`. Freundschaftsanfragen selbst werden erst dann als Social-Ereignisse dargestellt, wenn dafür echte Servernachrichten im Testkorpus vorliegen; hier wird nichts geraten.

### Alias / echte Spieleridentität

OPSUCHT zeigt teilweise sichtbare Namen wie `~Name` oder Bedrock-Namen mit führendem Punkt. Der OPSUCHT-Adapter trennt deshalb sichtbaren Chatnamen und echte Spieleridentität.

Wenn eine eindeutige Zuordnung über Tablist oder echte PN-Formate möglich ist, wird sie lokal gelernt und für Klick → PN, Skins und spätere Unterhaltungen wiederverwendet.

## Lokale Daten

Es gibt keine Telemetrie und keinen Upload. Social-Daten liegen ausschließlich lokal und getrennt nach Server-Adapter:

```text
.minecraft/opsucht-chat/
├── debug-chat.jsonl
└── social/
    └── opsucht/
        ├── favorites.txt
        ├── private-messages.tsv
        ├── aliases.tsv
        ├── important-private.txt
        └── closed-conversations.txt
```

Der Debug-Logger ist standardmäßig aus und wird mit `/opschat debug` umgeschaltet.

## Auktionen

Auktionen werden zustandsbehaftet erkannt. Der Parser merkt sich eine laufende Auktion:

```text
Auktionsstart -> Gebote -> zum ersten/zweiten/dritten -> verkauft
```

Dadurch gehören auch reine Beträge wie `5.5k` zur Auktion, solange eine passende Session aktiv ist. Die Erkennung basiert auf echten OPSUCHT-Beispielen und wird mit einem Testkorpus abgesichert.

## Installation

### Fabric / Prism

1. Minecraft Java 26.2 mit Fabric verwenden.
2. Den Fabric-Build aus den GitHub Releases in den `mods`-Ordner legen.
3. Minecraft starten.

### NeoForge / Prism

1. Minecraft Java 26.2 mit NeoForge verwenden.
2. Den NeoForge-Build aus den GitHub Releases in den `mods`-Ordner legen.
3. Minecraft starten.

### Dawn

Dawn kann Fabric-/NeoForge-Profile starten. Dort den passenden Build verwenden.

### LabyMod 4

Der Fabric-Build wird als experimentelle Kombination behandelt. Da LabyMod selbst tief in den Chat eingreifen kann, können dort zusätzliche Kompatibilitätsarbeiten nötig werden.

Ein komplett unveränderter Vanilla-Client kann seine Chat-GUI nicht durch eine JAR aus diesem Projekt erweitern. Ein Client-Loader bzw. Addon ist technisch erforderlich.

## Lokale Befehle

```text
/opschat
/opschat tab all
/opschat tab msg
/opschat tab pn
/opschat tab auktion
/opschat tab server
/opschat tab werbung
/opschat debug
```

## Adapter-Architektur

Der Chat selbst ist nicht mehr an OPSUCHT gekoppelt.

```text
                    Chat UI + Social Core
                           │
             ┌─────────────┴─────────────┐
             │                           │
        Server Adapter              Client Adapter
             │                           │
      OPSUCHT / später X          Fabric / NeoForge
```

Ein Server-Adapter definiert unter anderem:

- Erkennung der Server-Adresse
- Klassifizierung von Chat / PN / Auktion / Server / Werbung
- Darstellung normaler Chatzeilen
- strukturierte Server- und Auktionsereignisse
- sichtbarer Spielername vs. echte Identität/Alias
- PN- und später weitere server-spezifische Befehle

Details für weitere Server stehen in [docs/ADAPTERS.md](docs/ADAPTERS.md).

## Community-Projekt

- kostenlos und Open Source
- MIT-Lizenz
- keine Accounts
- keine Werbung
- keine Telemetrie
- kein eigener Backend-Service
- keine Serverinstallation erforderlich
- keine Gameplay-Automatisierung

## Entwicklung

```text
core/                    Parser, Social Model, Server Adapter, Tests
minecraft/26.2/          gemeinsamer Minecraft-26.2-ChatFrame
platforms/fabric/        Fabric-Einstiegspunkt
platforms/neoforge/      NeoForge-Einstiegspunkt
```

Build lokal mit JDK 25:

```bash
# Core + NeoForge
gradle --configure-on-demand :core:test :platforms:neoforge:build

# Fabric
gradle --configure-on-demand :platforms:fabric:build
```

## Releases

GitHub Actions testet den Core und baut Fabric sowie NeoForge. Ein Release kann über **Actions → Release → Run workflow** gestartet werden oder über einen `release/v...`-Branch entstehen.

Der Workflow erzeugt:

- Fabric JAR
- NeoForge JAR
- `SHA256SUMS.txt`
- GitHub Release

## Mithelfen

Siehe [CONTRIBUTING.md](CONTRIBUTING.md). Besonders hilfreich sind anonymisierte echte Chatbeispiele, wenn ein Serverformat falsch oder gar nicht erkannt wird.

---

**Inoffizielles Community-Projekt.** Nicht mit opsucht.net, Mojang oder Microsoft verbunden oder von diesen unterstützt.
