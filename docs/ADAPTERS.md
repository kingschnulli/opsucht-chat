# Server-Adapter

Opsucht Chat ist intern in einen generischen Chat-/Social-Core und server-spezifische Adapter getrennt.

Ein neuer Server soll **keine eigene UI** und **keinen eigenen Social-Speicher** benötigen. Er liefert nur die Semantik seiner Nachrichten.

## Einstiegspunkt

Ein Adapter implementiert:

```java
de.kingschnulli.opsuchtchat.core.server.ChatServerAdapter
```

und wird anschließend in `ServerAdapterRegistry` registriert.

## Aufgaben eines Adapters

Ein Adapter ist für folgende server-spezifische Dinge verantwortlich:

1. **Server erkennen**
   - `matchesAddress(...)`

2. **Nachrichten klassifizieren**
   - `classify(...)`
   - `classifyStateless(...)`
   - Kategorien: MESSAGE, PRIVATE, AUCTION, SERVER, ADVERTISING

3. **Normale Chatzeilen strukturieren**
   - `parsePublicChat(...)`
   - liefert Rang, sichtbaren Spielernamen und Nachrichteninhalt

4. **Klickbaren Spielernamen lokalisieren**
   - `publicPlayerRange(...)`
   - notwendig, wenn der sichtbare Name Farbcodes oder servereigene Präfixe enthält

5. **Servermeldungen strukturieren**
   - `parseServerEvent(...)`
   - z. B. Geld, Teleport, Vote, Job, Markt, Booster

6. **Auktionen strukturieren**
   - `parseAuctionEvent(...)`
   - START, BID, COUNTDOWN, SOLD

7. **Identität/Aliase auflösen**
   - `identityBase(...)`
   - Beispiel OPSUCHT: `~PG_Mystical` und `.PG_Mystical` können auf dieselbe Identitätsbasis zeigen

8. **SERVER-Arbeitsbereich definieren**
   - `serverHubPages()`
   - liefert nur Daten: Seiten, Befehle, Beschreibungen, Aliase und RUN/PREFILL-Modus
   - `defaultServerCommandFavorites()`
   - die Minecraft-UI bleibt für alle Adapter identisch

9. **Serverbefehle erzeugen**
   - `privateMessageCommand(...)`
   - `paymentCommand(...)`
   - optional Freund-/Social-Befehle

## Sicherheitsprinzip

Eine unbekannte Nachricht darf nicht verschwinden.

Wenn ein Adapter sich nicht sicher ist, soll die Nachricht als normale MESSAGE behandelt werden. ALL bleibt zusätzlich das vollständige Sicherheitsnetz.

## Kein Regex-Monster in der UI

Regex, Präfixe und Serverbegriffe gehören ausschließlich in den Adapter bzw. dessen Parser.

Die Minecraft-UI darf nicht wissen, ob ein Server PNs als

```text
FREUNDE » [Spieler -> Mir] hallo
```

oder völlig anders darstellt.

## Social-Daten

Lokale Daten sind automatisch nach Adapter-ID getrennt:

```text
.minecraft/opsucht-chat/social/<adapter-id>/
```

Damit vermischen sich Favoriten, PN-Historie, Aliase und wichtige Nachrichten verschiedener Netzwerke nicht.

## Tests

Jedes neue Format sollte mit einem kleinen synthetischen oder anonymisierten Testfall abgesichert werden.

Besonders wichtig:

- eingehende PN
- ausgehende PN
- normaler Chat
- Werbung
- Start/Gebot/Ende einer Auktion
- relevante Servermeldungen
- Alias-/Identitätsfälle

Keine echten privaten Logs vollständig committen.
