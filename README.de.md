# HUDPilot

[English version](README.md)

**HUDPilot** ist eine Motorrad-Navigations-App für Android mit eigener Offline-Navigation, Vektorkarte und direkter Ansteuerung eines Bluetooth-LE-Head-up-Displays (*Tilsberk / DVision* für Motorradhelme). Kein Herstellerkonto und keine Hersteller-App nötig. Die Karten, Straßendaten und Touren kommen auf Wunsch von einem **eigenen Server** (HUDPilot Updater, Ordner `hudserver` im Projekt oder als eigenes Projekt).

Inoffizielle Hobby-App für den privaten Gebrauch.

> **Keine Verbindung** zu Tilsberk, Digades, Moket, WhatsApp oder Spotify. Alle Produktnamen und Marken gehören ihren Inhabern und dienen nur der Beschreibung der Kompatibilität. **Keine Gewähr.** Die Anzeige kann ausfallen oder falsch sein, Navigationshinweise können falsch sein. Verlass dich beim Fahren nicht darauf. Verkehrsschilder und -regeln haben immer Vorrang.

## Was die App kann

**Navigation und Karte**
- Offline-Vektorkarte (MapLibre, Protomaps) mit 3D-Gebäuden, Tag- und Nachtstil, Suche nach Orten und Adressen, Orte-Suche (Tanken, Essen, Hotel, Parken) aus den heruntergeladenen Daten
- Routen mit BRouter (offline), mehrere Motorrad-Profile (Landstraße, kurvig, schnell, schnellste mit Autobahn), „Vermeiden“ für Autobahn, Fähren, Maut und unbefestigte Wege, Zwischenziele, Routenvorschau, Neuberechnung bei Abweichung
- Sprachansagen mit der installierten Handy-Stimme, Spurhinweise, Hinweiskarte mit Abbiegepfeil, Ankunft und Restweg, Ankunftsflagge
- Hoch- und Querformat, Norden oben / Fahrtrichtung oben / 3D, Zoom passt sich dem Tempo an, Simulation einer Fahrt zum Testen
- **Baustellen und Sperrungen** (Bayern, aus der Mobilithek, über den eigenen Server): auf der Karte sichtbar, mit Hinweis entlang der Route und auf Wunsch **Umfahrung gesperrter Strecken**

**HUD (Head-up-Display)**
- Tempo, Tempolimit, Abbiegepfeil und Entfernung, Restweg und -zeit, Ankunft, Kompass, Spurhinweise
- **Eingehende Anrufe, Nachrichten und der Musiktitel am HUD** (siehe „Meldungen am HUD“ unten)
- Sechs Anzeigen: Navigator, Minimalist, Explorer, City und die frei einstellbaren Guide (Pfeil plus zwei Zeilen nach Wahl) und Cruiser (vier Zeilen nach Wahl); Helligkeit des HUD automatisch oder in drei Stufen; ein Element-Test, mit dem du herausfindest, was welcher Code auf deinem HUD zeigt
- Der Dienst läuft im Vordergrund, verbindet sich selbst neu und zeigt lieber nichts als etwas Falsches

**Warnungen**
- Tempolimit aus OpenStreetMap (importiert oder online), Tempo-Warnton bei Überschreitung (mit HUD, Navigation oder laufender Aufnahme)
- Blitzer-Warnung (aus, mit Hinweis beim Einschalten: in Deutschland und einigen Ländern für Fahrer nicht erlaubt), Hinweistöne für Bahnübergänge, Zebrastreifen, Verkehrsberuhigung und scharfe Kurven

**Fahrten und Touren**
- Fahrten aufzeichnen (GPX und CSV), Auswertung mit Tempo, Höhe, Schräglage und Beschleunigung, Saison-Übersicht
- Touren im Browser planen (Server) und in der App fahren, Favoriten, Profile (mit eigenem Namen und Code auf dem Server), Einstellungen sichern und auf einem anderen Handy holen
- Status-Seite mit Verbindungen, Server, Baustellen und Speicherübersicht (Karten, Routen, Straßendaten, Fahrten)

**Sprache:** Deutsch und Englisch (folgt der Handy-Sprache oder lässt sich in der App umstellen).

## Voraussetzungen
- Android 8.0 oder neuer
- Für das HUD: Bluetooth LE und ein gekoppeltes Tilsberk/DVision-HUD (die App läuft auch ohne HUD als reine Navigation)
- Für Karten und Straßendaten: ein eigener Server (HUDPilot Updater, Anleitung siehe `hudserver/README.de.md`)
- Benachrichtigungszugriff für die App (nötig für Anrufe, Nachrichten und Musiktitel am HUD)

Vom Autor mit einem HUD auf einem Xiaomi Mi 10T (LineageOS) und einem Nubia Z80 Ultra (Original-Android) getestet. Andere Geräte, Firmwarestände und Android-Versionen sind ungetestet.

## Ohne eigenen Server: Karte von Hand einspielen
Der Server ist optional. Ohne ihn navigiert die App trotzdem, aber du bringst die Karte selbst mit und verzichtest auf Komfort: kein Regionen-Download, keine automatischen Straßendaten (Tempolimits, Blitzer, Bahnübergänge) und keine Baustellen.

1. Besorge eine **Protomaps-Kartendatei (`.pmtiles`)** für dein Gebiet. So baut auch der HUDPilot Updater seine Karten: mit dem kostenlosen Programm `pmtiles` und dem täglichen Planeten-Build von `build.protomaps.com` (siehe `maps.protomaps.com/builds`):
   ```
   pmtiles extract <Planeten-Datei-oder-URL> karte.pmtiles --bbox=WEST,SUED,OST,NORD --maxzoom=15
   ```
   (`--bbox` ist das gewünschte Rechteck in Grad, z. B. `11.0,47.5,13.0,49.0`.)
2. Kopiere `karte.pmtiles` aufs Handy (USB-Kabel, Cloud, Download).
3. In der App: **Menü → Karte → „Eigene Karte wählen (.pmtiles) …“** und die Datei antippen. Das Kopieren großer Dateien dauert eine Weile. Sobald „Karte geladen.“ erscheint, wird sie benutzt.
4. Routendaten (`.rd5`) lädt die App beim ersten Berechnen auf Tipp aus dem BRouter-Verzeichnis (brouter.de), ohne deinen Standort zu senden.

Gut zu wissen:
- Nur Protomaps-Karten passen zum eingebauten Kartenstil. Andere Kartenformate gehen nicht.
- Der Stil ist für die Kartendateien gebaut, die der Server heute lädt. Es ist nicht garantiert, dass jede Protomaps-Datei richtig aussieht.
- Eine so gewählte Karte ersetzt die vorherige. Vom Server geladene Regionen werden getrennt verwaltet.
- Dieser Weg ist **nicht auf einem frischen Handy ohne Server getestet**.
- Außer Karten kannst du eigene Routen öffnen: **Menü → „GPX-Datei öffnen …“**.

## Meldungen am HUD
Die App liest die Benachrichtigungen des Handys mit (nur mit deiner Freigabe, siehe *Einrichten*) und zeigt kurze Texte im Meldungsfeld des HUD:

| Quelle | Was erscheint | Hinweise |
|---|---|---|
| **Anrufe** | Name des Anrufers, solange es klingelt | Bis zu 2 Minuten oder bis der Anruf endet; hat Vorrang vor allem außer einer Moket-Hilfeanfrage |
| **WhatsApp** (auch WhatsApp Business) | Name des Absenders (nicht der Nachrichtentext) | 5 Sekunden lang; Sammelmeldungen und Dauermeldungen werden übergangen |
| **Moket** (Biker-App) | Hilfeanfragen mit Art und Entfernung (z. B. „HILFE Reifen 3km“), „Biker gekreuzt“, Antworten auf die eigene Hilfeanfrage, Namen von Chats und Gruppen | Rangfolge: Hilfeanfrage, Anruf, Hilfe-Nachricht, Chats/WhatsApp, Begegnung. Erkennt nur die **deutschen** Moket-Texte |
| **Musik** (Spotify, YouTube Music und andere Player) | „Interpret - Titel“, wenn ein neuer Titel beginnt | Geht mit jedem Player, der eine Medien-Benachrichtigung zeigt; Pause und Weiterspielen zählen nicht als neuer Titel |

Grenzen des HUD selbst:
- Meldungen erscheinen nur in den Anzeigen **Explorer** und **City**. Die anderen Anzeigen haben keinen Platz dafür.
- Die Texte werden auf 19 Zeichen gekürzt und in einfachem ASCII gezeigt (Umlaute und Emojis werden vereinfacht). Das HUD scrollt nicht.
- Spotify, WhatsApp und Moket werden nur genannt, um zu sagen, was funktioniert; das Projekt hat keine Verbindung zu ihnen.

## Installation

**Fertige APK:** Auf der Release-Seite des Projekts die neueste `.apk` herunterladen, auf dem Handy öffnen und installieren („Installation aus dieser Quelle“ erlauben).

**Selbst bauen:** Ordner in Android Studio öffnen (JDK 17, Gradle 8.13 / AGP 8.9.2) und *Build → Build APK(s)*, oder:

```
./gradlew assembleDebug
```

Die APK liegt danach in `app/build/outputs/apk/debug/`. Tests (ohne Handy): `./gradlew test`.

## Einrichten
1. HUD einschalten und in den Android-Bluetooth-Einstellungen koppeln (nur nötig, wenn du ein HUD nutzt).
2. App öffnen, Berechtigungen erlauben. Auf der Status-Seite den **Benachrichtigungszugriff** freigeben, wenn Anrufe und Nachrichten am HUD erscheinen sollen.
3. **Menü → Server:** Adresse deines Servers eintragen (zu Hause und unterwegs), Profil anlegen (Name und Einladungscode aus der Server-Verwaltung).
4. **Menü → Karte → Regionen laden:** Regionen wählen und laden (am besten im WLAN zu Hause).
5. Ziel suchen oder lang auf die Karte drücken, Route ansehen, **START**.

## Fehlerbehebung (APK installieren)
- **„App wurde zum Schutz deines Geräts blockiert“ (Google Play Protect):** *Weitere Details* antippen, dann *Trotzdem installieren*. Play Protect kennt den Signaturschlüssel dieser Open-Source-App nicht.
- **Benachrichtigungszugriff ausgegraut („Eingeschränkte Einstellung“):** Ab Android 13 ist das für Apps gesperrt, die per Datei oder Browser installiert wurden. *Einstellungen → Apps → HUDPilot* öffnen, oben rechts das **⋮-Menü** antippen, **Eingeschränkte Einstellungen zulassen** wählen, dann den Zugriff freigeben.
- **Keine Karte:** Ohne eigenen Server und geladene Region zeigt die Karte nur einen grauen Hintergrund. Server einrichten und Region laden (siehe oben).

## Datenschutz
- Benachrichtigungen (Anrufe, Nachrichten, Musiktitel) werden nur gelesen, um sie am HUD anzuzeigen. Nichts wird gespeichert oder gesendet.
- Wenn du einen eigenen Server betreibst (optional), schickt die App ihm eine zufällige Gerätekennung und den Gerätenamen (z. B. „Google Pixel 7“), damit die Verwaltungsseite zeigen kann, welches Handy gerade lädt. Nie an Dritte.
- Ohne importierte Straßendaten wird für Straßennamen und Tempolimits die ungefähre Position an einen Overpass-Server (OpenStreetMap) gesendet. Mit dem Schalter *Offline-Daten* wird nichts gesendet. Routendaten kommen vom Server oder auf Tipp aus dem BRouter-Verzeichnis, ohne Standort.
- Die App merkt sich deine letzte Position nur auf dem Handy (um die Karte nach dem Öffnen gleich zu zentrieren). Sie wird nirgends hingeschickt.
- Fahrten, Touren, Favoriten und Einstellungen liegen auf deinem Handy und, wenn du es einschaltest, auf deinem eigenen Server. Das Protokoll liegt nur auf dem Gerät. Es kann Namen von Anrufern oder Absendern enthalten, vor dem Teilen prüfen.

## Protokoll
Das Bluetooth-Protokoll des HUDs, soweit bekannt, steht in [PROTOCOL.md](PROTOCOL.md) (englisch).

## Lizenzen
MIT für den eigenen Code ([LICENSE](LICENSE)). Fremde Bestandteile und Hinweise: [NOTICE.md](NOTICE.md). Kartendaten © OpenStreetMap-Mitwirkende (ODbL).

## Mitmachen
Issues und Pull Requests sind willkommen: Tests mit anderer HUD-Firmware, Fehler, Übersetzungen, weitere Meldungsquellen (andere Messenger, andere Sprachen für Moket).

Hinweis: Der Paketname `io.github.th3s1nc.osmandhudbridge` stammt noch aus den Anfängen des Projekts (es begann als Brücke zu einer anderen Navigations-App, die nicht mehr verwendet wird). Er bleibt, weil eine Änderung auf jedem Handy die Benachrichtigungsfreigabe und die Einstellungen zurücksetzen würde.
