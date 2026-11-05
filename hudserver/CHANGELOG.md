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

v1.23.0: **Planer und Auto-Profil.** (1) Neues Profil „Auto" (Planer, Touren, App): schnellste Route für Autos, Autobahn und Bundesstraßen bevorzugt; die Schalter Autobahn, Fähren, Maut und Unbefestigt gelten wie bei den Motorrad-Profilen. Abgeleitet vom Moped-Profil von BRouter, wie die übrigen, im Feldtest noch nicht geprüft. (2) Der Planer zoomt beim Öffnen auf den Home-Favoriten (etwa 30 km Umkreis), wenn einer angelegt ist und noch keine Tour geladen oder die Karte bewegt wurde. (3) Die berechnete Route zeigt Kilometer und ungefähre Fahrzeit groß oben auf der Karte. Neue Tests.

v1.23.1: Planer: Favoriten lassen sich auf der Karte einblenden (Haken „Auf der Karte zeigen“, standardmäßig an, wird im Browser gemerkt). 🏠 für Home, ★ für die übrigen, mit Namen (bei weitem Herauszoomen ausgeblendet). Ein Klick auf einen Marker bietet „Als Start“, „Als Zwischenpunkt“ und „Als Ziel“ an.

v1.24.0: **Planer aufgeräumt.** Oben links nur noch die Routenplanung: Suchfeld, Punkteliste, Km/Zeit, Name und Speichern. Profil, Vermeiden, Sperrungen/Baustellen und Kartenstil liegen eingeklappt hinter „⚙ Einstellungen“ (im Kopf steht das gewählte Profil), die Favoriten hinter „★ Favoriten“. Die Liste der gespeicherten Touren ist weg (die gibt es im Reiter Touren, ein geöffneter Link …/planer?tour=ID funktioniert weiter). Neu: **Adresssuche** (Nominatim/OpenStreetMap, direkt aus dem Browser) und **Orte-Suche** für Tanken, Essen, Hotel und Parken im Umkreis von 15 km um die Kartenmitte, aus den POI-Dateien der gebauten Regionen (GET /api/poi, ohne Internet). Treffer anklicken: Marker auf der Karte, dazu „Als Start/Zwischenpunkt/Ziel“. Der Knopf „GPX laden“ heißt jetzt richtig „GPX speichern“. Neue Tests.

v1.25.0: **Gefahrene Fahrten im Planer weiterbearbeiten.** Im Reiter Touren hat jede gefahrene Fahrt jetzt den Knopf „Im Planer öffnen“ (geplante Touren hatten ihn schon). Der Planer setzt die Punkte selbst: Er rechnet zuerst Start nach Ziel, sucht die Stellen, an denen die gefahrene Spur am weitesten von der berechneten Route abweicht (über 120 m), setzt dort Zwischenpunkte und wiederholt das, bis die Route nah genug an der Spur liegt (höchstens 27 Punkte, maximal 12 Durchgänge). Bei sehr verwinkelten Fahrten bleibt es eine Annäherung, das meldet der Planer. Es gilt das Profil und Vermeiden, das im Planer eingestellt ist.

v1.26.0: **Rundtour-Planer.** Neuer Knopf „Rundtour erstellen …“ im Planer: Länge mit Schieberegler (50 bis 500 km), Richtung auf einer Windrose (N bis NW) oder 🎲 für zufällig, Start ist der erste Punkt der Route, sonst Home, sonst die Kartenmitte. Der Planer legt einen Kreis in die gewünschte Richtung um den Start, setzt 5 bis 6 Punkte darauf (leicht zufällig versetzt), lässt die Route rechnen und passt den Kreis an, bis die Länge etwa stimmt (±10 %, bis zu 5 Anläufe je Vorschlag). Läuft ein großer Teil der Strecke auf derselben Straße zurück, gibt es bis zu 4 Vorschläge, der beste gewinnt. „🎲 Andere Variante“ würfelt neu. Profil und Vermeiden wie im Planer. Wenn Routendaten fehlen, kommt eine Meldung. Die BRouter-Version auf dem Server kann keine Rundtouren selbst, deshalb rechnet der Planer sie mit Zwischenpunkten.

v1.27.0: **Rundtour neu gedacht: anderer Rückweg statt Kreis.** Hinweg und Rückweg sind jetzt verschiedene Straßen: der Planer rechnet erst den Hinweg und dann den Rückweg mit gesperrter Hinfahrt (neu: Server reicht Sperrkreise `nogos` an BRouter weiter). Optional mit eigenem Ziel: erste Punkt = Start, letzter Punkt = Ziel, dann Hinweg zum Ziel und anderer Rückweg; die Länge gilt dann als „mindestens“. Länge darf bis ±15 % abweichen (lieber eine brauchbare Tour als keine). Punkte, die BRouter keiner Straße zuordnen kann, werden automatisch ein Stück verschoben und neu versucht. Fehlermeldungen sind jetzt genau: fehlende Routendaten nennen die Kachel (z. B. E10_N45), ein Start abseits von Straßen wird als solcher gemeldet. Von mehreren Versuchen wird der mit den wenigsten doppelten Stücken genommen.

v1.28.0: **Rundtour: breitere Schleifen, schneller, mit Länder-Haken.** Der Wendepunkt liegt zufällig mal näher, mal weiter, dazu kommt ein seitlicher Punkt auf der Hinfahrt: die Tour geht eher in die Breite statt als langer Strich. Neuer Haken „Länderübergreifend erlauben“: ohne Haken bleibt die Tour im Land (Erkennung über die Rahmen der gebauten Regionen, an Grenzen nicht auf den Meter genau). Schneller: drei Vorschläge werden gleichzeitig gerechnet, die Länge wird mit einem Korrekturlauf getroffen, die erste brauchbare Tour wird sofort angezeigt (Fahrzeit und Höhenmeter folgen im Hintergrund), die doppelte Endberechnung entfällt. BRouter bekommt dafür mehr Arbeitsspeicher (640 MB). Neu: GET /api/laender. Im Planer stehen die Favoriten jetzt über den Einstellungen und sind standardmäßig aufgeklappt.

v1.29.0: **Gefahrene Fahrt genau im Planer.** „Im Planer öffnen“ übernimmt eine gefahrene Fahrt jetzt mit bis zu 300 Punkten (etwa alle 3 km, dazu überall dort, wo die berechnete Route mehr als 70 m von deiner Spur abweicht), statt wie bisher mit höchstens 27. Die Route folgt damit deiner Spur praktisch Straße für Straße. Lange Punktlisten werden in Stücken (höchstens 3 gleichzeitig) berechnet und zusammengesetzt; Punkte, die BRouter keiner Straße zuordnen kann, werden weggelassen. Gespeicherte Touren dürfen bis zu 300 Punkte haben (vorher 27). Passt zur App v0.32.0.

v1.29.1: **Gefahrene Fahrt im Planer: Punkte abseits der Straße werden zur Straße gesetzt.** Findet BRouter zu einem Punkt der Spur keine Straße (Waldweg, Parkplatz, Zufahrt), probiert der Planer automatisch Punkte in der Nähe (120, 250, 500 m) und nimmt den ersten, der auf einer Straße liegt. Das gilt auch für Start und Ziel. Gibt es zwischen zwei Punkten keinen Weg, wird der Zwischenpunkt weggelassen und das im Hinweis genannt. Statt „Routendaten fehlen?“ steht bei einem Fehler jetzt der echte Grund.

v1.30.0: **Freunde und Live-Standort.** Profile können sich gegenseitig als Freunde hinzufügen (über den genauen Profilnamen, die andere Seite muss die Anfrage annehmen). Pro Freund gilt eine dauerhafte Erlaubnis „darf mich sehen“; die Position schickt die App nur, wenn der Schalter „Standort teilen“ an ist. Positionen liegen nur im Arbeitsspeicher und verfallen nach 2 Minuten; gespeichert werden nur die Freundschaften und tägliche Zähler. Neu: GET /api/friends, GET /api/friends/live, POST /api/friends/request|respond|remove|permit, POST /api/live und /api/live/stop (auch von außen, mit Profil). In der Verwaltung gibt es auf der Übersicht die Karte „Freunde und Live-Standort“ (Freundschaften, wer gerade teilt, wer zuschaut, geteilte Minuten pro Tag, Spitze, Verbindungen trennen); Positionen zeigt die Verwaltung nie. Neue Protokoll-Kategorie „Freunde und Live-Standort“. Passt zur App v0.33.0.

v1.31.0: **Verwaltung: Übersicht aufgeräumt.** Ganz oben eine Status-Leiste („Maps“, „Baustellen“, „Routing“, „Clients“, „Live“, „Storage“) mit Farbe (grün idle/synced, orange läuft, rot Fehler) und Fortschrittsbalken; ein Klick springt zur passenden Karte. Reihenfolge darunter: Aktive Clients, Kennzahlen (Regionen, Länder …), Road Data, Update, Roadworks, Storage. „Friends & Live“ ist in den Tab Profile umgezogen. Keine Änderung an App oder Schnittstellen.
v1.32.0: **Baustellen: keine falschen Sperr-Linien mehr über Bundesstraßen.** Zu einer Meldung mit Anfangs- und Endpunkt sucht der Server die Straße dazwischen jetzt mit dem neuen Profil „kuerzeste“ (nur die Entfernung zählt, keine Bevorzugung großer Straßen) statt mit dem Landstraßen-Profil. Ein gefundener Weg gilt nur, wenn er höchstens das 1,3-Fache der Luftlinie plus 100 m lang ist, sonst bleibt es bei der geraden Linie zwischen den beiden Punkten (vorher: bis 3-fach plus 300 m). Alte, im Zwischenspeicher liegende Linien werden nicht mehr benutzt (neue Schlüssel-Version), beim nächsten Abruf werden alle neu gesucht. Meldungen, die dieselben zwei Punkte in beiden Fahrtrichtungen enthalten, zählen einmal. Neue Tests: tests/test_baustellen.py. Keine Änderung an App oder Schnittstellen.

v1.33.0: **Admin-Meldungen.** Der Server schreibt wichtige Ereignisse in eine Liste: SCDB-Blitzerliste fällig, Update fertig, Update mit Fehlern oder abgebrochen, Server hängt (15 Minuten keine Bewegung), Baustellen-Abruf fehlgeschlagen, Mobilithek-Zertifikat läuft in 30 Tagen ab (gelesen mit keytool), Festplatte über 90 %, Server neu gestartet; aus: neues Profil, neue Server-Version. Im Tab Profile gibt es pro Profil den Haken „Admin-Meldungen“, unter Einstellungen die Karte „Admin-Meldungen“ (einzelne Arten ein/aus, Testmeldung, Liste leeren, letzte Meldungen). Neu: GET /api/notify?since=N (mit Profil; nur Empfänger bekommen Inhalt, alle anderen „erlaubt: false“), GET/POST /api/admin/notify. Zustände melden sich höchstens alle paar Stunden bis Tage. Gespeichert in data/notify.json. Passt zur App v0.35.0. Neue Tests: tests/test_notify.py.
v1.34.0: **Routing schneller und messbar, Status-Infos für die Admin-Meldungen.** BRouter bekommt jetzt standardmäßig 4 GB Arbeitsspeicher und 8 gleichzeitige Berechnungen (vorher 640 MB und 3) und rechnet nach dem Start einmal eine Probestrecke, damit die erste echte Anfrage nicht wartet. Beides lässt sich im Tab Protokoll unter „Routing-Leistung“ ändern (BRouter startet dann neu). Dort steht auch die Messung: Berechnungen heute, Durchschnitt, langsamste, laufende, langsame (ab 5 Sekunden), Fehler und die letzten 20 Berechnungen mit Profil, Punkten, Strecke und Dauer (Zähler im Arbeitsspeicher, nach einem Neustart wieder bei null). Der Webplaner rechnet Teilstücke mit bis zu 6 gleichzeitigen Anfragen (vorher 3). Admin-Meldungen: Unter Einstellungen wählst du zusätzlich Status-Infos (Server, Update, Karten, Baustellen, Blitzerliste, Speicher, Routing, Handys, Live-Standort, Zertifikat), die Profile mit Haken „Admin-Meldungen“ über der Meldungsliste in der App sehen (GET /api/notify liefert dazu „status“, nur für Empfänger; nie Passwörter, Adressen oder Namen). Übersicht Friends & Live: der Balken ist durch drei Zahlen ersetzt (heute, 7 Tage, Spitze). Neue Tests: tests/test_routing.py. Passt zur App v0.36.0.
