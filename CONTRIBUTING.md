# Mithelfen

Danke! Der wichtigste Grundsatz dieses Projekts ist: **lieber eine Nachricht in ALL lassen als sie falsch wegzufiltern**.

## Fehlende oder falsch erkannte Nachricht melden

1. Im Spiel `/opschat debug` eingeben.
2. Die problematische Situation kurz reproduzieren.
3. `/opschat debug` erneut eingeben.
4. `.minecraft/opsucht-chat/debug-chat.jsonl` öffnen.
5. Nur die relevanten Zeilen in ein GitHub Issue kopieren.

Bitte private Inhalte und Spielernamen prüfen oder anonymisieren, wenn sie für die Regel nicht relevant sind.

Der Debug-Log bleibt ausschließlich lokal und wird vom Mod niemals hochgeladen.

## Klassifizierungsregeln

Regeln liegen im loader-unabhängigen Core. Neue Regeln sollten:

- möglichst spezifisch sein,
- bestehende normale Chatnachrichten nicht fälschlich treffen,
- mit einem Test abgesichert werden,
- keine einzelnen Spielernamen hardcoden.

Für Auktionen bitte berücksichtigen, dass der Parser zustandsbehaftet ist. Folgezeilen dürfen nur als Auktion erkannt werden, wenn zuvor eine Auktion geöffnet wurde.

## Pull Requests

Vor einem PR bitte mindestens ausführen:

```bash
gradle :core:test
```

Für Änderungen an der Minecraft-Integration zusätzlich:

```bash
gradle :platforms:fabric:build :platforms:neoforge:build
```

## Datenschutz

Keine Chat-Logs, UUIDs, Tokens oder sonstige personenbezogene Daten als Testfixture committen. Testdaten sollten synthetisch oder anonymisiert sein.
