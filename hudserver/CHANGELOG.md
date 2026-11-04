# Verlauf des HUDPilot Updater

v1.0 bis v1.1: Karten (Protomaps) und Routendaten (BRouter) bauen, Statusseite, Download-Bremse und Zeitfenster.

v1.2: Zusätzlich je Region eine POI-Datei (pois/<id>.tsv.gz) aus dem Geofabrik-Auszug (Tanken, Essen, Hotel, Parken, ...).
Statusseite: Knopf "Nur POIs bauen" holt nur die POI-Dateien, ohne Karten und Routen neu zu bauen.


v1.3: Bedarfsgesteuert. Die App meldet ihre Regionen (GET /api/need?ids=bw,by). Fehlende Regionen baut der Server auf Anforderung
(Warteschlange). Der automatische Lauf hält nur Regionen aktuell, die in den letzten 90 Tagen gemeldet wurden (NEED_DAYS).
Wurde noch nie etwas gemeldet, gelten alle Häkchen-Regionen. manifest.json enthält zusätzlich "alle" (auch ungebaute Regionen).


v1.4: Tourenplaner unter /planer (Leaflet + OSM-Karte im Browser, BRouter im Container). Touren in data/tours, "An Handy senden" legt data/outbox.json ab,
die App holt sie über /api/outbox. Routen rechnet BRouter mit den rd5-Dateien in public/rd5; für Bereiche ohne gebaute Region kommt "datafile ... not found".


v1.5: Startseite (/) mit Logo und Kennzahlen (/api/stats, öffentlich). Tourenplaner (/planer) ohne Passwort. Verwaltung (/admin) mit Passwort-Anmeldung (Cookie, 30 Tage).


v1.6: Logo ist das App-Icon (static/logo.png). Planer mit Kartenstilen (Standard, Hell, Dunkel, Topografie, Satellit), Auswahl wird im Browser gemerkt.


v1.7: Planer auf MapLibre GL umgestellt (Kartenstil-Auswahl, 3D Gelände mit AWS-Terrain-Höhendaten, 3D Gebäude über OpenFreeMap). Braucht im Browser Internet (unpkg.com, Kachelserver).


v1.8: Touren-Seite (/touren): geplante Touren (aus dem Planer) und gefahrene Fahrten (vom Handy hochgeladen, /api/ride/upload mit den Rohpunkten).

v1.9: Touren-Seite mit drei Reitern (Geplant / Gefahren = Navigationsfahrten / Aufzeichnungen = freie Aufnahmen). Fahrten tragen "kind" (nav/free), Beispielfahrten per Knopf anlegen/entfernen (/api/ride/demo).

v1.10: Profil "Schnellste (mit Autobahn)" und "Vermeiden"-Schalter (Autobahn, Fähren, Maut, unbefestigt) im Planer; /api/route?avoid=motorway,ferry,toll,unpaved; Touren speichern die Schalter. Profile wie in App v0.20.0.
Der Server rechnet Kennzahlen und Diagramme selbst (Tempo, Limit, Höhe, Schräglage, Beschleunigen/Bremsen). Fahrten in data/rides.

v1.11: Profile: jede Person hat Name und Code (Datei data/profiles.json), eigene Touren, Fahrten und Ablage unter data/profiles/<name>/. Neue Profile entstehen mit dem Einladungscode (Admin-Oberfläche, oder Umgebungsvariable INVITE_CODE beim ersten Start) in der App (Menü → Server) oder im Planer. Planer, Touren und Handy melden sich mit Name und Code an (Kopfzeilen X-Profile/X-Code oder Cookie hudprof). Die Admin-Oberfläche listet alle Profile (Code neu, sperren, löschen). Beim ersten Start wandern bisherige Touren, Fahrten und Ablage ins Profil th3_s1nc. Nach 10 falschen Codes sperrt der Server die Adresse für 10 Minuten. Die Beispielfahrten werden nicht mehr automatisch angelegt.

v1.12: Einstellungen der App pro Profil sichern und auf einem anderen Handy holen: GET /api/settings, POST /api/settings/save (Datei data/profiles/<name>/settings.json, eine ältere Sicherung als settings.prev.json).

v1.13: neue Seiten: Startseite mit Mein Bereich (/mein: Anmeldung, Übersicht, Planer, Touren, gemeinsames Menü) und Verwaltung (/admin mit Menü und Kennzahlen).

v1.14: Route in der Web-Ansicht bleibt beim Herauszoomen sichtbar; Klick auf eine Fahrt springt direkt hinein (/touren?ride=ID); neue Übersicht mit Kennzahlen, Fahrtenkarten mit Routenvorschau und Kilometer pro Monat/Jahr; GET /api/ride/preview.

v1.15: Fahrtenkarten zeigen die echte Karte (OpenStreetMap, im Dunkelmodus Esri) hinter der Route; Beispielfahrten werden in der Übersicht ausgeblendet.

v1.16: Favoriten pro Profil (Zuhause + Orte): GET /api/favs, POST /api/favs/save (Zusammenführen je Eintrag, jüngere Änderung gewinnt; Datei data/profiles/<name>/favs.json).

v1.17: Straßendaten vom Server: aus derselben Geofabrik-Datei wie die Orte baut der Server pro Region mit dem Import-Code der App (Ordner updater/limits, gebaut mit limits-tool/build.sh) die Tempolimit-Kacheln (Straßen, Ortsschilder, Blitzer, Bahnübergänge, Zebrastreifen, Verkehrsberuhigung) als public/limits/<id>.zip; im Manifest als "strassen". SCDB-Blitzerliste (GPX oder ZIP) im Admin hochladen: Server führt sie zusammen (public/cameras.csv, Manifest "blitzer"), Erinnerung nach 30 Tagen, Link zur SCDB. Arbeitsspeicher für das Berechnen: Umgebungsvariable LIMITS_XMX (Standard 2g).

v1.18: Verwaltung (/admin) neu: Reiter Übersicht, Regionen, Blitzer, Profile, Einstellungen, Protokoll. Klare Knöpfe „Alles neu bauen“ und „Nur Straßendaten und Orte“ (auch je Region), nach jedem Lauf eine Zeile „Fertig um …“ oder die Fehler; Haken bei den Regionen werden sofort gespeichert. Zähler für Straßendaten (Straßen, Tempolimit, Blitzer OSM und SCDB, Schilder, Bahnübergänge, Zebrastreifen, Verkehrsberuhigung) und Profile; Touren und Fahrten sehen nur die User selbst (nicht mehr in /api/stats und der Profilliste). /api/stats nur noch mit Admin-Anmeldung.

v1.18.1: Fortschritt überall: Download mit Größe, Tempo und Restzeit, Filtern und Straßendaten mit Prozent, dazu ein Gesamtbalken, „Läuft seit …“ und „Letzte Bewegung vor …“ (nach 5 Minuten ohne Bewegung warnt die Seite „hängt vermutlich“); der Balken steht auch oben auf jedem Reiter. „Abbrechen“ beendet jeden Schritt sofort (Download, Filtern, Karte, Straßendaten) und zeigt „Abbruch läuft …“. Protokoll: Stufen Kurz / Normal / Ausführlich und Haken je Kategorie (Downloads, Karte, Orte, Straßendaten, Blitzer, Handy-Anfragen, Anmeldungen und Sync, Aufräumen), Filter „Nur Fehler“, „Protokoll leeren“, bleibt nach Neustart erhalten (log.txt, etwa 1 MB). Regionen auf dem Server stehen in der Liste oben. Reste eines abgebrochenen Laufs werden beim nächsten Start aufgeräumt.

v1.18.2: Regionenliste in drei Gruppen: „Fertig“ (Karte, Orte und Straßendaten), „Karte da, Straßendaten noch nicht gebaut“, „Nicht gebaut"; die Region, die gerade gebaut wird, steht jeweils oben.

v1.18.3: Die Serverversion steht oben neben „Verwaltung“ und unten bei den Einstellungen (mit Startzeit).

v1.19: Zugriff von außen (Nginx Proxy Manager): Anfragen über den NPM (NPM_IPS, ohne Standardwert) oder mit Weiterleitungs-Kopf gelten als extern. Von außen gibt es die Verwaltung nicht („nicht gefunden“, kein Link), kein Anlegen neuer Profile und, solange „Karten extern ladbar“ aus ist, keine Karten, Straßendaten, Orte, Routen-Kacheln, Manifest und Bau-Anfragen (Meldung „nur im Heimnetz“). Erlaubt: Anmeldung mit Profil, Planer, Touren, Fahrten, Einstellungen-Sync, Favoriten. Sperre nach 5 falschen Anmeldungen für 15 Minuten (im Heimnetz 10), mit Eintrag im Protokoll; auch die Admin-Anmeldung hat eine Sperre. Schalter „Karten extern ladbar“ im Admin unter Einstellungen.

v1.19.1: Neue, schlichte Startseite (Logo, Name, „Anmelden“); der Text „Verwaltung nur im Heimnetz“ ist weg. Im Planer gibt es „An Handy senden“ nicht mehr (die App holt gespeicherte Touren selbst).

v1.19.2: Zuhause (direkt über die Server-Adresse) zeigt die Startseite zwei Knöpfe: „Touren & Fahrten“ und „Verwaltung“. Von außen gibt es nur „Anmelden“.

v1.20: Baustellen und Sperrungen aus der Mobilithek (Bayern, DATEX II): Der Server holt die Meldungen einmal pro Tag (oder per „Jetzt abrufen“ im Admin unter Übersicht), rechnet die große Datei in eine kleine Liste um, sucht mit BRouter die echte Straße zwischen Anfang und Ende und stellt sie der App unter /api/baustellen bereit (mit Profil, auch von außen). Für den Abruf: /data/mobilithek/client.p12 (Maschinenzertifikat) und /data/mobilithek/config.json mit {"abo": "ABO-ID", "pass": "ZERTIFIKAT-PASSWORT"}. Ohne beides liest der Server die Testdatei /data/baustellen/test.xml (mit Stand der Datei).

v1.20.1: Das Protokoll zeigt, wenn ein Handy die Baustellenliste holt („Handy holt Baustellen“), damit man Verbindungsprobleme sieht.

v1.21: Der Webplaner umfährt Sperrungen: /api/route?…&sperr=1 prüft die Route gegen die gültigen Sperrungen und rechnet bei einem Treffer mit Sperrzonen (BRouter polylines) neu. Antwort enthält "sperrung" (umfahren, bleibt). Im Planer gibt es dafür das Häkchen „Sperrungen umfahren“.

v1.21.1: Der Webplaner zeigt Baustellen und Sperrungen auf der Karte (rot gesperrt, orange eingeschränkt, gelb Arbeiten, ab Zoom 8). Klick auf eine Linie zeigt Text und Zeitraum; Häkchen „Baustellen zeigen“.

v1.21.2: Beschriftungen auf den Seiten kürzer und klarer („Alles neu erstellen“, „Download-Tempo“, „Karten von außen laden“ u. a.).

v1.21.3: Knopf heißt „Markierte neu erstellen“ (baut nur abgehakte Regionen); abgehakte Regionen stehen in der Tabelle oben.

v1.21.4: Übersicht „Gerade aktiv“: laufende Downloads mit Tempo und Prozent, Handys der letzten 10 Minuten; Profile zeigen die Zahl der Handys (App ab 0.29.1 schickt eine zufällige Kennung und den Gerätenamen).

v1.21.5: „Gerade aktiv“ zeigt jetzt das echte Handy-Tempo (der Server bittet den Proxy, nicht zwischenzulagern); die Inhaltsliste (manifest.json) steht dort nicht mehr.

v1.21.6: Der Server heißt in der Verwaltung jetzt „HUDPilot Updater“ (Seitentitel, Kopfzeile, Beschreibung). Containername hud-updater bleibt.

v1.21.7: Aufgeräumt für die Veröffentlichung: keine feste Proxy-Adresse mehr im Code (NPM_IPS ist leer, die Seite und /api/stats nennen keine Adresse); neue Anleitung (README.md und README.de.md), der Verlauf steht jetzt hier.

v1.22.0: **Europa-Liste.** Neuer Knopf „Europa-Liste holen“ im Reiter Regionen: Der Server holt die Übersicht von Geofabrik (index-v1.json) und erzeugt daraus Regionen für alle EU-Länder, die Schweiz, Norwegen, Großbritannien, Griechenland und die Türkei (pro Land die Unterregionen, die Geofabrik anbietet, sonst das ganze Land; Rechtecke aus den Umrissen, ferne Inseln zählen nicht mit). Die Liste liegt in /data/regions_extra.json und bleibt beim Neustart erhalten. Die mitgelieferten Regionen (Deutschland, Österreich, Schweiz, Tschechien) bleiben unverändert, neue Regionen sind nie von selbst angehakt, es wird nichts geladen oder gebaut, bevor man es ankreuzt. Die Verwaltung zeigt die Regionen jetzt nach Ländern (ausklappbar, mit Haken pro Land und Suchfeld). Das Manifest enthält zusätzlich „landname“ (ausgeschriebener Ländername). Neue Tests (hudserver/tests).

v1.22.1: **Aufräumen.** (1) Handys, die länger als 90 Tage nicht gemeldet waren, werden beim Start und danach täglich aus der Liste vergessen (einstellbar mit DEV_KEEP_DAYS). (2) In der Profil-Tabelle lässt sich die Handy-Zahl aufklappen: Name, zuletzt gesehen, Kennung, mit Knopf „Entfernen“ pro Handy (meldet es sich wieder, taucht es neu auf). (3) Gebaute Regionen können vom Server gelöscht werden („Neu bauen“ → „Vom Server löschen …“): Karte, Orte und Straßendaten werden entfernt, Routen-Kacheln nur, wenn keine andere gebaute Region sie braucht; der Haken wird entfernt. Nicht möglich, solange gebaut wird oder die Region in der Warteschlange steht. Handys behalten bereits geladene Regionen. Neue Tests.

v1.22.2: Umbenannt: aus „HUD Pilot“ wird „HUDPilot“ (Verwaltungsseite, Planer, Texte, Dokumentation). Keine Funktionsänderung.
