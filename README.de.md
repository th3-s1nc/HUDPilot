# HUDPilot

[English version](README.md)

**HUDPilot** ist eine Motorrad-Navigations-App für Android mit eigener Offline-Navigation, Vektorkarte und direkter Ansteuerung eines Bluetooth-LE-Head-up-Displays (*Tilsberk / DVision* für Motorradhelme). Kein Herstellerkonto und keine Hersteller-App nötig. Die Karten, Straßendaten und Touren kommen auf Wunsch von einem **eigenen Server** (HUDPilot Updater, Ordner `hudserver` im Projekt oder als eigenes Projekt).

Inoffizielle Hobby-App für den privaten Gebrauch.

> **Keine Verbindung** zu Tilsberk, Digades, Moket, WhatsApp oder Spotify. Alle Produktnamen und Marken gehören ihren Inhabern und dienen nur der Beschreibung der Kompatibilität. **Keine Gewähr.** Die Anzeige kann ausfallen oder falsch sein, Navigationshinweise können falsch sein. Verlass dich beim Fahren nicht darauf. Verkehrsschilder und -regeln haben immer Vorrang.

## Was die App kann

**Navigation und Karte**
- Offline-Vektorkarte (MapLibre, Protomaps) mit 3D-Gebäuden, Tag- und Nachtstil, Suche nach Orten und Adressen, Orte-Suche (Tanken, Essen, Hotel, Parken) aus den heruntergeladenen Daten
- Routen mit BRouter (offline), mehrere Motorrad-Profile (Landstraße, kurvig, schnell, schnellste mit Autobahn) und ein Auto-Profil, „Vermeiden“ für Autobahn, Fähren, Maut und unbefestigte Wege, Zwischenziele, Routenvorschau, Neuberechnung bei Abweichung
- Sprachansagen mit der installierten Handy-Stimme, Spurhinweise, Hinweiskarte mit Abbiegepfeil, Ankunft und Restweg, Ankunftsflagge
- Hoch- und Querformat, Norden oben / Fahrtrichtung oben / 3D, Zoom passt sich dem Tempo an, Simulation einer Fahrt zum Testen
- **Baustellen und Sperrungen** (Bayern, aus der Mobilithek, über den eigenen Server): auf der Karte sichtbar, mit Hinweis entlang der Route und auf Wunsch **Umfahrung gesperrter Strecken**

**HUD (Head-up-Display)**
- Tempo, Tempolimit, Abbiegepfeil und Entfernung, Restweg und -zeit, Ankunft, Kompass, Spurhinweise
- **Eingehende Anrufe, Nachrichten und der Musiktitel am HUD** (siehe „Meldungen am HUD“ unten)
- Sechs Anzeigen: Navigator, Minimalist, Explorer, City und die frei einstellbaren Guide (Pfeil plus zwei Zeilen nach Wahl) und Cruiser (vier Zeilen nach Wahl); Helligkeit des HUD automatisch oder in drei Stufen; ein Element-Test, mit dem du herausfindest, was welcher Code auf deinem HUD zeigt
- Der Dienst läuft im Vordergrund, verbindet sich selbst neu und zeigt lieber nichts als etwas Falsches

**Warnungen**
- Tempolimit aus OpenStreetMap (importiert oder online), Tempo-Warnton bei Überschreitung (mit HUD, Navigation oder laufender Aufnahme), optional Früherkennung des nächsten Limits auf der Route („Gleich 50“), Kurventon, der der geplanten Route folgt
- Blitzer-Warnung (aus, mit Hinweis beim Einschalten: in Deutschland und einigen Ländern für Fahrer nicht erlaubt), Hinweistöne für Bahnübergänge, Zebrastreifen, Verkehrsberuhigung und scharfe Kurven

**Fahrten und Touren**
- Fahrten aufzeichnen (GPX und CSV), Auswertung mit Tempo, Höhe, Schräglage und Beschleunigung, Saison-Übersicht
- Touren im Browser planen (Server) und in der App fahren, Favoriten, Profile (mit eigenem Namen und Code auf dem Server), Einstellungen sichern und auf einem anderen Handy holen
- Status-Seite mit Verbindungen, Server, Baustellen und Speicherübersicht (Karten, Routen, Straßendaten, Fahrten)

**Automation** (standardmäßig aus, siehe „Automation“ unten)
- HUDPilot öffnen, wenn dein Bluetooth-Gerät verbindet, die Aufnahme in Moket mit Navigation oder Aufnahme starten und stoppen, Spotify öffnen (auf Wunsch mit Play) und danach wieder zu HUDPilot springen

**Sprache:** Deutsch und Englisch (folgt der Handy-Sprache oder lässt sich in der App umstellen).

## Voraussetzungen
- Android 8.0 oder neuer
- Für das HUD: Bluetooth LE und ein gekoppeltes Tilsberk/DVision-HUD (die App läuft auch ohne HUD als reine Navigation)
- Für Karten und Straßendaten: ein eigener Server (HUDPilot Updater, Anleitung siehe `hudserver/README.de.md`)
- Benachrichtigungszugriff für die App (nötig für Anrufe, Nachrichten und Musiktitel am HUD)

Vom Autor mit einem HUD auf einem Xiaomi Mi 10T (LineageOS) und einem Nubia Z80 Ultra (Original-Android) getestet. Andere Geräte, Firmwarestände und Android-Versionen sind ungetestet.

## Ohne eigenen Server
Der Server ist optional. Ohne ihn navigiert und warnt die App trotzdem, aber du bringst einiges selbst mit und verzichtest auf Komfort. Das bekommst du ohne Server:

| Was | Ohne Server |
|---|---|
| Karte | Deine eigene Protomaps-Datei (`.pmtiles`), siehe **A** |
| Routendaten (`.rd5`) | Holt die App auf Fingertipp aus dem BRouter-Verzeichnis (brouter.de), wenn du dort zum ersten Mal eine Route berechnest. Deine Position wird nicht gesendet. |
| Tempolimits | Online aus OpenStreetMap (Overpass) automatisch, oder offline aus einer `.osm.pbf`-Datei, die du einliest, siehe **B** |
| Blitzer | Deine eigene Liste (SCDB, GPX), siehe **C** |
| Regionen-Download, Baustellen, Tourenplanung, Profile, Einstellungen sichern | Brauchen den Server |

**A. Karte (`.pmtiles`)**
1. Besorge eine **Protomaps-Kartendatei (`.pmtiles`)** für dein Gebiet. So baut auch der HUDPilot Updater seine Karten: mit dem kostenlosen Programm `pmtiles` und dem täglichen Planeten-Build von `build.protomaps.com` (siehe `maps.protomaps.com/builds`):
   ```
   pmtiles extract <Planeten-Datei-oder-URL> karte.pmtiles --bbox=WEST,SUED,OST,NORD --maxzoom=15
   ```
   (`--bbox` ist das gewünschte Rechteck in Grad, z. B. `11.0,47.5,13.0,49.0`.)
2. Kopiere `karte.pmtiles` aufs Handy (USB-Kabel, Cloud, Download).
3. In der App: **Menü → Karte → „Eigene Karte wählen (.pmtiles) …“** und die Datei antippen. Das Kopieren großer Dateien dauert eine Weile. Sobald „Karte geladen.“ erscheint, wird die Karte benutzt.

**B. Tempolimits offline (optional)**
Ohne diesen Schritt fragt die App einen Overpass-Server (OpenStreetMap) nach Tempolimits und Straßennamen und sendet dafür deine ungefähre Position. Für den Offline-Betrieb:
1. Lade einen regionalen Auszug im Format `.osm.pbf` für dein Gebiet herunter, zum Beispiel bei Geofabrik (`download.geofabrik.de`), und kopiere ihn aufs Handy.
2. In der App: **Werkzeuge → STRASSENDATEN → „Datei wählen“** und die `.osm.pbf`-Datei antippen. Die App macht daraus Kacheln. Bei einer großen Region dauert das eine Weile; es läuft als Vordergrunddienst mit Fortschrittsbalken.
3. Schalte **Offline-Daten** an, wenn die App gar nichts mehr aus dem Internet laden soll. Der i-Knopf zeigt, was geladen ist.

**C. Blitzer (optional)**
In **Werkzeuge → BLITZER** kannst du eine eigene Blitzer-Liste einlesen, zum Beispiel eine GPX-Datei von SCDB. Die Blitzer-Warnung ist standardmäßig aus; lies den Hinweis, der beim Einschalten erscheint (in Deutschland und einigen Ländern für Fahrer nicht erlaubt).

**D. Routendaten**
Nichts vorzubereiten: Die `.rd5`-Dateien holt die App auf Fingertipp von brouter.de, wenn du in einem Gebiet zum ersten Mal eine Route berechnest (ohne deine Position). Berechne die erste Route in jedem Gebiet online.

Gut zu wissen:
- Nur Protomaps-Karten passen zum eingebauten Kartenstil. Andere Kartenformate gehen nicht.
- Der Stil ist für die Kartendateien gebaut, die der Server heute lädt. Es ist nicht garantiert, dass jede Protomaps-Datei richtig aussieht.
- Eine so gewählte Karte ersetzt die vorherige. Vom Server geladene Regionen werden getrennt verwaltet.
- Diese Wege sind **nicht auf einem frischen Handy ohne Server getestet**. Die Schritte stammen aus dem Quellcode. Wenn es bei dir anders aussieht, mach bitte ein Issue auf.
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

## Automation
In **Werkzeuge → AUTOMATION** (nach „Einrichtung“) kannst du das Handy Dinge für dich starten lassen. Alles ist **standardmäßig aus**, und jeder Schritt steht im Protokoll („Automation: …“).

| Karte | Was sie tut |
|---|---|
| **Bluetooth**, „Autostart bei Verbindung“ | Wähle ein gekoppeltes Gerät (zum Beispiel ein Headset). Verbindet es, öffnet sich HUDPilot von selbst. Das HUD hat damit nichts zu tun: Das verbindest du wie bisher selbst. Braucht Android 12 oder neuer; Android fragt einmal, ob HUDPilot das Gerät beobachten darf. |
| **Moket**, „Auto-Aufnahme“ | Startet die Aufnahme in Moket, wenn deine Navigation und/oder Aufnahme beginnt, und stoppt sie am Ende (mit zwei Schaltern wählst du, welche). Laufen beide, stoppt Moket erst, wenn beides zu Ende ist. Benutzt die Links, die Moket für Automationen anbietet (`moket://ride/start` und `moket://ride/stop`). |
| **Spotify**, „Autostart“ | Öffnet Spotify, wenn HUDPilot startet (höchstens alle 2 Minuten), nach einer Startverzögerung von 0 bis 30 s. „Auto-Play“ schickt zusätzlich ein Play nach einer Verzögerung von 0 bis 30 s (braucht den Benachrichtigungszugriff). |
| **Auto-Return** | Nachdem Moket oder Spotify geöffnet wurden, kommt HUDPilot nach 1 bis 15 s wieder nach vorn. Braucht auf den meisten Handys die Berechtigung **„Über anderen Apps anzeigen“** (Knopf in der Karte). Verweigert Android es trotzdem, kommt eine Benachrichtigung zum Antippen. |

Gut zu wissen:
- Für Moket brauchst du eine Version mit den Automations-Links (1.1.0 oder neuer). Stelle in Moket **Akku → „Nicht eingeschränkt“** ein, sonst startet die Fahrt bei geschlossener App nicht.
- Moket und Spotify werden nur genannt, um zu beschreiben, was funktioniert; das Projekt hat keine Verbindung zu ihnen.
- Das ist **nur auf wenigen Handys getestet**. Ob Android das Öffnen und Nach-vorn-Holen aus dem Hintergrund erlaubt, hängt vom Handy und Hersteller ab. Klappt etwas nicht, zeigt das Protokoll, welcher Schritt gescheitert ist.

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
- Die Automation speichert deine Schalter sowie Name und Adresse des gewählten Bluetooth-Geräts nur auf dem Handy. Nichts wird irgendwohin gesendet.
- Fahrten, Touren, Favoriten und Einstellungen liegen auf deinem Handy und, wenn du es einschaltest, auf deinem eigenen Server. Das Protokoll liegt nur auf dem Gerät. Es kann Namen von Anrufern oder Absendern enthalten, vor dem Teilen prüfen.

## Protokoll
Das Bluetooth-Protokoll des HUDs, soweit bekannt, steht in [PROTOCOL.md](PROTOCOL.md) (englisch).

## Lizenzen
MIT für den eigenen Code ([LICENSE](LICENSE)). Fremde Bestandteile und Hinweise: [NOTICE.md](NOTICE.md). Kartendaten © OpenStreetMap-Mitwirkende (ODbL).

## Mitmachen
Issues und Pull Requests sind willkommen: Tests mit anderer HUD-Firmware, Fehler, Übersetzungen, weitere Meldungsquellen (andere Messenger, andere Sprachen für Moket).

Hinweis: Der Paketname `io.github.th3s1nc.osmandhudbridge` stammt noch aus den Anfängen des Projekts (es begann als Brücke zu einer anderen Navigations-App, die nicht mehr verwendet wird). Er bleibt, weil eine Änderung auf jedem Handy die Benachrichtigungsfreigabe und die Einstellungen zurücksetzen würde.
