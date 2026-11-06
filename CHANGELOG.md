# Changelog HUDPilot

(Deutsch. HUDPilot ist eine Kopie der OSMAnd HUD Bridge v0.11.101 mit eigener Navigation und zählt ab 0.1.0 neu. Der Verlauf der Bridge steht darunter.)

## v0.37.1 (HUDPilot, Pfeil springt nicht mehr auf Radwege)
- **Freies Fahren:** Der Pfeil rastet nur noch auf Straßen ein, nicht mehr auf Rad-, Fuß- und sonstige Wege (auch nicht auf Brücken und in Tunneln). Vorher konnte er neben der Straße auf einen parallelen Radweg springen.
- Nicht auf einem echten Gerät getestet; die App wurde hier nicht gebaut.

## v0.37.0 (HUDPilot, ruhigerer Pfeil, bessere Wiedereinfädelung, GPS-Log)
- **Pfeil bleibt ruhig:** Im Stand und bei sehr langsamer Fahrt dreht sich der Pfeil nicht mehr um 180 Grad; die Richtung wird erst bei echter Bewegung übernommen. Ab 3 km/h rastet der Pfeil auf der Route ein (z. B. an Ampeln nicht mehr neben der Straße).
- **Neuberechnung:** Kommt man bald wieder auf die alte Route, wird das neue Stück nahtlos an den Rest der Route angehängt. Bei kurzem GPS-Ausfall (Tunnel) läuft die Position entlang der Route weiter.
- **GPS-Log (neu, Tab Werkzeuge, über dem Protokoll):** Schalter zum Mitschreiben der GPS-Daten samt Abweichungen und Neuberechnungen, mit Teilen-Knopf, Löschen und Info. Der rote Punkt auf der Karte zeigt die Aufzeichnung.
- Braucht Server ab v1.37.0 nur für das Einrasten im Planer; sonst auch mit dem alten Server nutzbar.
- Nicht auf einem echten Gerät getestet; die App wurde hier nicht gebaut (nur die Rechenlogik per Tests geprüft).

## v0.36.1 (HUDPilot, einheitliche Symbole und schwarzer Start)
- **Menü:** „Freunde“ hat jetzt ein orangenes Linien-Symbol wie die übrigen Punkte (vorher ein farbiges Emoji). Im Untermenü Freunde ebenso „Standort teilen“ und „Darf mich sehen“.
- **Start der App (Android 12 und neuer):** Der Startbildschirm hat jetzt schwarzen statt weißen Grund. Das Fenster ist auch sonst schwarz hinterlegt.
- Nicht auf einem echten Gerät getestet; die App wurde hier nicht gebaut.

## v0.36.0 (HUDPilot, Seite für Admin-Meldungen)
- **Admin-Meldungen sind jetzt eine eigene Seite** (statt eines Fensters): oben der **Serverstatus** als Kacheln mit farbigem Punkt (grün in Ordnung, orange Achtung, rot Fehler; ein laufendes Update mit Fortschrittsbalken), darunter die **Meldungen** als Karten mit Symbol, Titel, Text und Zeit („heute 07:00“, „gestern 01:03“). Neue Meldungen haben einen orangen Rand. Die Seite aktualisiert sich alle 10 Sekunden.
- Welche Status-Kacheln erscheinen, wählt der Admin am Webserver (Einstellungen, Admin-Meldungen). Braucht Server ab v1.34.0; mit einem älteren Server bleibt die Kachel-Gruppe leer, die Meldungen gehen weiter.
- Nicht auf einem echten Gerät getestet; die App wurde hier nicht gebaut.

## v0.35.0 (HUDPilot, Admin-Meldungen)
- **Admin-Meldungen** (Server ab v1.33.0): Der Admin bestimmt am Webserver im Tab Profile, welche Profile sie bekommen. Nur diese Profile sehen im **Status-Tab** den Knopf „Admin-Meldungen“ (mit Zahl der ungelesenen); bei allen anderen ist er gar nicht vorhanden. Die Berechtigung prüft der Server.
- Meldungen sind zum Beispiel: SCDB-Liste fällig, Update fertig oder mit Fehlern, Server hängt, Baustellen-Abruf fehlgeschlagen, Zertifikat läuft ab, Festplatte voll, Server neu gestartet. Welche Arten kommen, stellt der Admin am Server ein.
- Neue Meldungen kommen als Benachrichtigung (App im Hintergrund etwa alle 15 Minuten) beziehungsweise als kurzer Hinweis bei offener App (etwa 30 Sekunden). Ein Tipp auf die Benachrichtigung öffnet die Liste. Beim ersten Mal wird der alte Verlauf nicht angekündigt.
- Nicht auf einem echten Gerät getestet; die App wurde hier nicht gebaut.

## v0.34.1 (HUDPilot, Teilen-Knopf bei Navigation)
- **„Standort teilen“-Knopf** liegt während der Navigation **unten links über dem Menü** und nicht mehr unter der Navigationsanzeige (dort war er verdeckt). Ohne Navigation bleibt er links unter dem Haus-Knopf.
- Nicht auf einem echten Gerät getestet; die App wurde hier nicht gebaut.

## v0.34.0 (HUDPilot, Baustellen ehrlicher darstellen)
- **Baustellen ohne gefundene Straße** (Server ab v1.32.0 liefert dann nur zwei Punkte) werden als **gestrichelte Luftlinie** gezeichnet, nicht mehr wie eine echte Sperr-Straße. Tippen auf die Linie zeigt wie bisher den Meldungstext.
- Nach einem App-Update wird die Baustellen-Liste einmal sofort neu vom Server geholt (statt erst nach bis zu 6 Stunden).
- Nicht auf einem echten Gerät getestet; die App wurde hier nicht gebaut.

## v0.33.0 (HUDPilot, Freunde und Live-Standort)
- **Freunde:** Neuer Menüpunkt „Freunde“. Freunde findest du über den genauen Profilnamen auf deinem Server, die andere Person muss die Anfrage annehmen. Offene Anfragen zeigt ein Punkt am Menü.
- **Live-Standort:** Pro Freund schaltest du „darf mich sehen“ (bleibt dauerhaft). Gesendet wird nur, wenn der Schalter „Standort teilen“ an ist und die App Positionen bekommt: unterwegs alle 5 Sekunden, im Stand alle 30. Auf der Karte erscheint der Freund als Marker mit Schild „Anna · 87 km/h · vor 3 s“. Simulationen werden nie geteilt.
- **Knopf „Standort teilen“** links unter dem Haus-Knopf (nur sichtbar, wenn es Freunde gibt). Orange = Freunde sehen dich; ein Tipp zeigt kurz „Sichtbar für 2 Freunde“.
- **Benachrichtigung** bei neuer Freundschaftsanfrage: bei geöffneter App nach spätestens etwa 30 Sekunden, im Hintergrund etwa alle 15 Minuten (mehr erlaubt Android normalen Apps nicht).
- Braucht den Server v1.30.0. Positionen liegen dort nur im Arbeitsspeicher und verfallen nach 2 Minuten.

## v0.32.0 (HUDPilot, gefahrene Fahrten genau navigieren)
- **Planer-Routen mit vielen Punkten:** Eine im Webplaner übernommene gefahrene Fahrt (bis 300 Punkte) wird in der App genau entlang der Spur gerechnet. Die vielen Punkte sind reine Formpunkte: kein Zwischenziel-Ansagen, nicht in der Liste, nicht mitgezählt (die Grenze von 25 echten Zwischenzielen bleibt). Die Liste zeigt stattdessen „Route folgt der gefahrenen Spur“. Passt zum Server v1.29.0.

## v0.31.0 (HUDPilot, Kurvenwarner mit Route, Früherkennung, Auto)
- **Kurvenwarner mit Route:** Bei aktiver Navigation rechnet er mit der geplanten Route statt mit der Straße unter dir (kein Raten mehr an Abzweigen). Wo du ohnehin abbiegst (Abbiegung, Kreisverkehr, Gabelung, bis 40 m neben der Kurve), kommt kein Kurventon. Ohne Navigation ändert sich nichts.
- **Früherkennung (neuer Schalter, Tempolimit-Seite, unter „Ton bei Überschreitung“, standardmäßig aus):** Zeigt bei aktiver Navigation das nächste Tempolimit kurz vor dem Wechsel als „Gleich 50“ neben dem Schild, etwa 4 Sekunden vorher (mindestens 40 m, höchstens 150 m). Nur ein ausdrücklich eingetragenes Limit, nur wenn das jetzige Limit bekannt ist, ohne Ton.
- **Neues Profil „Auto“** (schnellste Route, Autobahn und Bundesstraßen bevorzugt), mit denselben Vermeiden-Schaltern. Passt zum Server v1.23.0.
- **Routen aus dem Webplaner** werden mit dem Profil **und den Vermeiden-Schaltern** gerechnet, mit denen sie dort geplant wurden, egal was in der App eingestellt ist. Ein kurzer Hinweis zeigt das beim Laden und beim Start. Danach gilt wieder dein eigenes Profil (auch wenn du selbst umstellst oder die Planung leerst). Vorher wurde das Profil übernommen und blieb dann dauerhaft geändert, die Vermeiden-Schalter kamen gar nicht an.
- **Hinweis „Diese App zeigt keine Karte“** auf der Tempolimit-Seite entfernt.

## v0.30.1 (HUDPilot, neuer Schreibname)
- **Umbenannt:** Aus „HUD Pilot“ wird überall „HUDPilot“ (App-Name, Texte, Dokumentation). Die interne App-Kennung bleibt gleich, die App wird als normales Update installiert, Einstellungen und Karten bleiben erhalten.

## v0.30.0 (HUDPilot, Regionen nach Ländern)
- **Regionen laden:** Die Liste ist nach Ländern gruppiert (Deutschland zuerst, dann alphabetisch), mit ausgeschriebenen Ländernamen statt Kürzeln. Die Länder lassen sich auf- und zuklappen; Länder mit angehakten Regionen sind aufgeklappt, bei einem Land mit nur einer Region steht die Region direkt darunter. Die Zeile zeigt, wie viele Regionen gewählt sind.
- **Suchfeld** oben: findet Länder und Regionen (Groß/Klein und Akzente egal, mehrere Wörter möglich).
- Passt zur Europa-Liste des Servers (v1.22.0). Ältere Server ohne Ländernamen zeigen weiter das Kürzel.

## v0.29.7 (HUDPilot, dezenter Standort-Knopf)
- Der Standort-Knopf ist dunkel wie die anderen Knöpfe; nur das Zielkreuz ist orange (Karte folgt) oder grau (Karte verschoben). Vorher war der ganze Knopf orange und zu auffällig.

## v0.29.6 (HUDPilot, GPS nach Simulation, Standort-Knopf, SIM-Punkt)
- **Fehler behoben:** Nach einer Simulation blieb der Schalter „Simulation“ hängen, auch wenn nichts mehr lief. Solange er an war, ignorierte die App das **echte GPS**: Der Pfeil folgte der Straße nicht mehr, die Karte zentrierte nicht auf den Standort. Der Schalter wird jetzt am Ende jeder Navigation oder Simulation zurückgesetzt, und das GPS wird nur noch während einer laufenden Simulation ignoriert. Neuer Test dazu (SimGuardTest).
- **Standort-Knopf** (Zielkreuz) ist jetzt immer sichtbar: orange, solange die Karte dir folgt, grau, wenn du sie verschoben hast. Ein Tipp zentriert auf deinen Standort und schaltet das Folgen wieder an.
- **Beim Öffnen** springt die Karte sofort auf die letzte bekannte Position (von Android, sonst die zuletzt gemerkte) und wartet nicht auf den ersten GPS-Treffer. Ohne jede Position erscheint nach 4 Sekunden „GPS wird gesucht …“. Die letzte Position wird nur auf dem Handy gespeichert.
- **Simulations-Knopf** in der Routenvorschau hat einen grünen Leuchtpunkt, solange der Simulationsmodus an ist.

## v0.29.5 (HUDPilot, Pfeil im Querformat)
- Beim Navigieren im Querformat sitzt der Pfeil jetzt auf der Höhe des Trennstrichs zwischen ANKUNFT und VERBLEIBEND (ca. 70 % von oben), mittig zwischen linkem Rand und Beginn der Hinweiskarte. Gilt für alle Ansichten, auch 3D. (v0.29.4 hatte ihn zu hoch gesetzt.)
- Dokumentation nachgebessert: README mit eigenem Abschnitt „Meldungen am HUD“ (Anrufe, WhatsApp, Moket, Musik, Rangfolge, Grenzen), alle Verweise auf OSMAnd entfernt (die App nutzt es nicht mehr), NOTICE bereinigt, `.kotlin/` in `.gitignore`. Neuer Abschnitt „Ohne eigenen Server: Karte von Hand einspielen“ (eigene .pmtiles-Datei). Die App selbst ist unverändert.
- Aufgeräumt für die Veröffentlichung: keine echten Adressen und Namen mehr in Kommentaren, Beispielen und Testdaten; neue README-Dateien (deutsch und englisch).

## v0.29.4 (HUDPilot, Pfeil-Position, Zielflagge, Diskette)
- **Pfeil beim Navigieren:** Ursache gefunden: Nach der Routenvorschau blieb der Kartenrand der Vorschau hängen, die Kamera brachte ihn bei jedem Schritt zurück, und der Pfeil saß zu hoch (Hochformat) oder zu tief (Querformat). Jede Kamerabewegung bringt den Rand jetzt selbst mit. Hochformat: Pfeil auf Höhe der Mitte des Stopp-Knopfs. Querformat: mittig zwischen linkem Rand und Beginn der Hinweiskarte, 5 mm höher als bisher gedacht (statt 68 % jetzt 68 % minus 5 mm).
- **Zielflagge:** Beim Ankommen erscheint oben in der Mitte 8 Sekunden lang ein Balken mit gezeichneter Zielflagge und „Ziel erreicht“. Der alte Hinweis unten links, den der Tempo-Kreis verdeckte, entfällt.
- **Tour speichern:** Statt der Wolke steht eine gezeichnete Diskette im Routen-Fenster.

## v0.29.3 (HUDPilot, Knöpfe in Regionen laden)
- Die Knöpfe auf „Regionen laden“ sind jetzt eigene, einfache Felder statt System-Knöpfe: alle gleich hoch, Text mittig, nichts mehr abgeschnitten. „Gewählte laden / aktualisieren“ heißt kürzer „Gewählte laden“ (es aktualisiert weiterhin auch).

## v0.29.2 (HUDPilot, Tempo beim Karten-Download, schönere Knöpfe)
- **Regionen laden:** Beim Download steht hinter den GB jetzt das Tempo, z. B. „Bayern: Karte – 361 MB von 1,8 GB · 12.4 MB/s“.
- **Knöpfe** auf der Seite gleich hoch, gleich breit nebeneinander, abgerundet und in einer Linie.

## v0.29.1 (HUDPilot, Handy-Kennung für den eigenen Server)
- Die App schickt an **deinen eigenen Server** eine zufällige Gerätekennung und den Gerätenamen (z. B. „Google Pixel 7“). Nichts Persönliches, nie an Dritte (nicht an brouter.de, nicht an Karten- oder Kartenkachel-Anbieter).
- Damit zeigt die Verwaltung (Server v1.21.4) an, welches Handy gerade etwas lädt, mit Tempo und Prozent, und bei den Profilen, wie viele Handys dazugehören.
- Die Versionsnummer in der App steht jetzt richtig auf 0.29.1 (sie stand noch auf 0.27.6).

## v0.27.6 (HUDPilot, Knopfsymbole mittig)
- Die neuen Symbole im Ansichts- und Menü-Knopf sitzen jetzt exakt in der Mitte des Kreises.

## v0.29.0 (HUDPilot, Aufräumen, Speicher, Tempoton bei Aufnahme)
- **Tempo-Warnton auch bei laufender Aufnahme** (ohne HUD und ohne Navigation). Die Beschreibungen der Töne sagen jetzt „Mit HUD, Navigation oder Aufnahme“ und sind kürzer („Hinweise mit Ton“ nur noch ein Satz).
- **Status-Seite:** Neue Karte „Speicher“: Karten, Routendaten, Straßendaten, Orte, Blitzer, Fahrten, App und Sonstiges, Gesamt.
- **Kartenmenü:** Neue Symbole für Touren (Routenlinie) und Karte (Faltkarte). „Sperrungen umfahren“ und „Danach-Hinweis“ stehen jetzt unter Navigation, „Baustellen und Sperrungen“ bleibt unter Anzeige.
- **Stay awake** ist entfernt (die Karte hält den Bildschirm selbst an).
- **Blitzer-Liste (SCDB):** Beschreibung jetzt „Kommt vom Server, wenn dort vorhanden.“

## v0.28.1 (HUDPilot, Regionen oben, Spurhinweise)
- **Regionen laden:** Oben steht „Auf dem Handy“ mit allen geladenen Regionen (Update zuerst, dann nach Name). Darunter folgen nach Ländern nur die, die noch fehlen. Ist ein Download fertig, rutscht die Region automatisch nach oben.
- **Spurhinweise:** Der Schalter ist weg. Die Spurinfo geht automatisch ans HUD, wenn die Navigator-Ansicht aktiv ist (nur die kann Spuren), in den anderen Ansichten wird sie nicht gesendet.

## v0.28.0 (HUDPilot, Sperrungen umfahren)
- **Sperrungen umfahren:** Liegt auf einer neu berechneten Route eine gültige Sperrung, rechnet die App mit Sperrzonen neu. In der Vorschau steht „✓ 1 Sperrung umfahren“, oder „Keine Umfahrung gefunden“ (dann bleibt die erste Route samt Warnung). Gilt auch bei der Neuberechnung unterwegs. Nur Sperrungen (rot), Einschränkungen warnen nur. Menü → Anzeige: Schalter „Sperrungen umfahren“ (Standard an).
- **Kartenknöpfe:** Die Symbole für Ansicht und Menü sitzen jetzt genau mittig und sind etwas kleiner.
- Der Webplaner auf dem Server (v1.21) umfährt Sperrungen ebenfalls (Häkchen im Planer).

## v0.27.5 (HUDPilot, neue Kartenknöpfe)
- **Ansichts-Knopf:** Statt „N“, „↑“ und „3D“ zeigt er Symbole: Kompass mit roter Nadel (Norden oben), Navigationspfeil (Fahrtrichtung 2D), Pfeil auf geneigter Fläche (3D).
- **Menü-Knopf:** Drei Striche statt des Zahnrads.
- **Prüfseite (MapLibre) und der Menüpunkt „Mehr“ sind entfernt.**

## v0.27.4 (HUDPilot, Baustellen-Fix und kurze Benachrichtigung)
- **Baustellen:** Eine leere Liste vom Server wird nicht mehr gemerkt und löst keine 6-Stunden-Pause aus (das war der Grund, warum nach dem Einspielen der Daten nichts kam). Ein Tipp auf die Zeile „Baustellen“ auf der Status-Seite lädt die Liste sofort neu.
- **Benachrichtigung:** Kurze Texte: „Navigation“, „Aufnahme“, „HUD verbunden“, „Bereit“ (bei mehreren: „Navigation · Aufnahme · HUD“). Der Auto-Modus ist ganz entfernt und aus.
- **Pufferzeit nach dem Schließen** der App: 5 statt 10 Sekunden.
- **Regionen laden:** Der Knopf „Server-Einstellungen“ ist weg (die Server-Einstellungen gibt es im Kartenmenü und auf der Status-Seite).

## v0.27.3 (HUDPilot, Status-Symbol)
- Der Tab „Status“ hat ein Puls-Symbol statt des Hauses.

## v0.27.2 (HUDPilot, Aufräumen und Baustellen-Fehleranzeige)
- **Karte:** Tempo-Kreis und REC-Taste sitzen etwas höher, die Quellenzeile „© OpenStreetMap …“ bleibt frei.
- **Werkzeuge:** Auto-Modus (Testfahrt), Saisonfahrer und der Speicherplatz-Schieber sind weg. Die Datengrenze steht auf dem Maximum, gespeicherte Daten werden nicht mehr automatisch gelöscht.
- **Baustellen:** Die Status-Zeile „Baustellen“ zeigt den Grund, wenn das Laden scheitert (z. B. „Kein Profil gewählt“ oder „Server antwortet mit 403“). Die Liste wird auch beim Öffnen der App geholt, nicht nur beim Öffnen der Karte.

## v0.27.1 (HUDPilot, Status-Seite)
- **Status statt Übersicht:** Die Seite heißt jetzt „Status“. Neue Karte mit Punkt-Zeilen: Benachrichtigungszugriff (rot mit Tipp zum Einschalten, wenn er fehlt oder nicht verbunden ist), Server, Baustellen-Stand. „Letzte Meldungen“ heißt jetzt „Ticker“.
- **Karte:** Tempolimit-Schild 25 % kleiner, km/h-Zahl 25 % größer.
- **Server-Einstellungen:** Die Beispieladressen sind jetzt allgemein (192.168.1.10, navi.beispiel.duckdns.org).

## v0.27.0 (HUDPilot, Baustellen und Sperrungen)
- **Baustellen auf der Karte:** Vom eigenen Server (ab Server v1.20, Quelle Mobilithek Bayern) kommen Baustellen und Sperrungen. Rot = gesperrt, orange = eingeschränkt, gelb = Arbeiten. Tipp auf eine Linie zeigt Text und Zeitraum. Sichtbar ab Zoom 8.
- **Hinweis bei der Route:** Liegt eine Sperrung oder Einschränkung auf der Route, steht ein Hinweis in der Routenvorschau.
- **Menü → Anzeige:** neuer Schalter „Baustellen und Sperrungen“.
- Die Liste wird beim Öffnen geholt (höchstens alle 6 Stunden) und für unterwegs gemerkt. Ohne Server oder Profil passiert nichts.

## v0.26.1 (HUDPilot, HUD-Schalter im Menü)
- **HUD-Schalter im Menü der Karte:** In der Titelzeile rechts neben „Menü“ steht jetzt „HUD“ mit einem Schalter und einem Punkt (grün verbunden, orange verbindet, rot Fehler, grau aus). Ein Tipp verbindet oder trennt das HUD, ohne dass die Übersicht öffnet. Ist noch kein HUD gewählt oder fehlt die Bluetooth-Berechtigung, führt die Übersicht wie bisher durch die Auswahl.
- **Aus der Übersicht entfernt:** Die Zeile „HUD verbinden“ ist dort weg. Status, Hinweise und Protokoll bleiben.

## v0.26.0 (HUDPilot, nur aktiv, wenn offen oder auf Fahrt)
- **Der Schalter „App aktiv“ ist weg.** Die App verhält sich wie Calimoto und Motobit: Sobald du sie öffnest, startet der Dienst von selbst (HUD verbinden, GPS, Straßendaten). Es gibt nichts mehr, was man vergessen kann.
- **Schließen beendet alles:** Ist die App zu (10 Sekunden Wartezeit für Bildschirmwechsel) und läuft keine Fahrt, endet der Dienst. Bluetooth, GPS und die Benachrichtigung sind dann weg, das HUD ist frei für andere Apps.
- **Fahrt läuft weiter:** Bei laufender Navigation oder Aufzeichnung läuft der Dienst auch mit Display aus oder weggewischter App weiter (mit der Benachrichtigung, das verlangt Android). Eine Aufzeichnung beendest nur du. Ist die Fahrt vorbei und die App zu, endet der Dienst von selbst.
- „HUD verbinden“ und die Übersichtsseite bleiben unverändert. Anrufe, WhatsApp und Moket am HUD gibt es nur, solange der Dienst läuft (App offen oder Fahrt).

## v0.25.0 (HUDPilot, Server-Bildschirm und Auto-Sync)
- **Neuer Bildschirm Menü → Server:** Adresse zuhause und unterwegs, „Speichern und verbinden“, Profil (anmelden, neu anlegen, abmelden), „Jetzt abgleichen“, Einstellungen sichern/holen und der Schalter „Automatisch abgleichen“. Unter „Regionen laden“ gibt es keine Adressfelder mehr, nur einen Knopf dorthin (und einen Hinweis, wenn noch keine Adresse da ist).
- **Auto-Sync:** Neue Fahrten gehen nach dem Speichern sofort zum Server (auch über mobile Daten; bei Misserfolg drei spätere Versuche). Einstellungen werden beim Öffnen der Karte abgeglichen und beim Verlassen still gesichert, wenn sich etwas geändert hat. Liegt auf dem Server etwas Neueres, wird es übernommen. Haben beide Seiten Änderungen (oder ist es der erste Abgleich dieses Handys), fragt die App, welche gelten sollen.
- **Touren nur mit „Speichern“:** In der Routenvorschau gibt es den Knopf ☁. Erst damit geht eine geplante Tour zum Server. Der Menüpunkt „Plan auf Server speichern“ und die Nachfrage „Neue Route vom Planer“ sind entfernt. Die Tourenliste wird nach dem Speichern und beim Öffnen aktualisiert.
- **Kosmetik:** Tempo-Anzeige und REC-Knopf sitzen ganz unten am Rand. Beim Tippen auf die Lupe verschwinden beide, damit die Tastatur Platz hat.
- Braucht für „An Handy senden“-freie Planer-Nutzung Server v1.19.1 (neue Startseite).

## v0.24.0 (HUDPilot, Adresse unterwegs)
- **Zweite Serveradresse „Adresse unterwegs“** (Karte → ⚙ → Regionen, unter der Heim-Adresse), zum Beispiel `navi.beispiel.duckdns.org`. Ohne Angabe von http oder https gilt https. Die App nimmt automatisch die Heim-Adresse, wenn sie erreichbar ist (kurze Probe, 1,5 Sekunden, gilt eine Minute), sonst die Adresse unterwegs. Das gilt für Touren, Fahrten, Einstellungen und Favoriten (braucht Server v1.19 und den Eintrag im Nginx Proxy Manager).
- **Karten und Straßendaten unterwegs:** Sie gehen nur, wenn am Server der Schalter „Karten extern ladbar“ an ist. Sonst zeigt die App „Karten und Straßendaten gibt es nur im Heimnetz“, Touren, Fahrten und Einstellungen gehen trotzdem. Unterwegs werden keine Karten still nachgeladen.
- Die Statuszeile im Menü zeigt „(unterwegs)“, wenn die Adresse unterwegs benutzt wird.

## v0.23.0 (HUDPilot, Straßendaten und Blitzer vom Server)
- **Kein Import mehr am Handy:** Tempolimits, Ortsschilder, Blitzer (OpenStreetMap), Bahnübergänge, Zebrastreifen und Verkehrsberuhigung baut jetzt der Server (braucht Server v1.17) mit demselben Import-Code wie bisher die App. Beim **Region laden** kommt die fertige Straßendatei der Region mit. Nachbarregionen werden an den Kanten zusammengeführt.
- **Automatisch aktuell:** Beim Öffnen der Karte (nur im WLAN) holt die App neuere Straßendaten der geladenen Regionen und die Blitzerliste still nach. Im Reiter Einstellungen → Straßendaten gibt es dazu den Knopf **Jetzt vom Server holen** mit Fortschritt.
- **Blitzerliste (SCDB) vom Server:** Du lädst die SCDB-Dateien (GPX) einmal im Admin-Bereich des Servers hoch, alle Handys bekommen dieselbe zusammengeführte Liste. Wird sie am Server gelöscht, verschwindet sie auch am Handy.
- Die Knöpfe zum Einlesen einer Kartendatei, zum Laden vom Internet und zum Auswählen von GPX-Dateien sind ausgeblendet. Das Nachladen einzelner Kacheln aus dem Internet bleibt als stille Reserve.
- Die Serverdaten werden beim Aufräumen des Kartenspeichers **nicht** mehr gelöscht (nur der Zwischenspeicher).

## v0.22.1 (HUDPilot, Reiter „Touren")
- **Reiter „Tracking" heißt jetzt „Touren":** oben die Zusammenfassung (Saison, Kilometer, Fahrten, Höchsttempo, Strecke je Monat), darunter die Reiter **Geplant · Gefahren · Aufnahmen**. Die Einstellungen der Aufzeichnung (Start/Stopp, Höhe, Tempolimit, Schräglage) liegen hinter dem **⚙** oben rechts.
- **Karte → Menü → Touren** öffnet direkt diesen Reiter in der App. Geplante Tour antippen legt sie in die Planung der Karte, Fahrt antippen öffnet die Details, langer Druck auf eine Fahrt: nachfahren, auf den Server sichern oder löschen.
- Die eigene Touren-Seite ist weg (es gibt nur noch den Reiter). **Beispielfahrten** werden nicht mehr angelegt und der Knopf ist entfernt; schon vorhandene löschst du von Hand.
- Menü **Vermeiden** zeigt keine Textliste mehr, nur noch den Namen mit Pfeil. In den Sprachansagen heißt „Probe hören" jetzt **Test**.

## v0.22.0 (HUDPilot, Zuhause und Favoriten)
- **Zuhause-Knopf 🏠** unter der Lupe: ein Tipp plant die Route nach Hause. Beim ersten Mal die Adresse festlegen (Meine Position, Kartenmitte oder Adresse suchen). Langer Druck: Adresse ändern oder löschen.
- **Favoriten ★:** Im Zielblatt zeigt ☆/★ neben dem Profil, ob das Ziel ein Favorit ist (Tipp speichert mit Namen oder entfernt). In den Suchtreffern speichert ein langer Druck den Treffer. Unter dem leeren Suchfeld stehen die Favoriten (mit Zuhause) über dem Verlauf; Tipp navigiert hin, langer Druck: umbenennen, als Zuhause setzen, löschen.
- **Abgleich mit dem Profil (braucht Server v1.16):** Zuhause und Favoriten werden bei jeder Änderung, beim Anmelden und beim Öffnen der App automatisch mit dem Server abgeglichen. Mehrere Handys mit demselben Profil haben so dieselben Favoriten. Je Eintrag gewinnt die jüngere Änderung, gelöschte Favoriten verschwinden auch auf dem anderen Handy.
- Im Planer am PC gibt es eine Favoriten-Liste: als Start, Zwischenpunkt oder Ziel einfügen.

## v0.21.4 (HUDPilot, Hinweiskarte im Querformat)
- **Querformat:** Die Karte rechts nimmt jetzt die **ganze Höhe** ein und teilt sich in drei Bereiche: Abbiegepfeil mit Entfernung (größter Teil, extrafett), Ankunft und Restweg (kleiner, fett). Die Karte ist breiter (höchstens 215 dp, nie mehr als etwa ein Viertel des Bildschirms), „123,4 km" steht in einer Zeile.
- Zahlen verkleinern sich selbst, wenn sie nicht passen (lange Werte, kleine Displays). Hochformat unverändert.

## v0.21.3 (HUDPilot)
- Unten in der Info-Reihe getauscht: **Geschwindigkeit links**, Tempolimit-Schild rechts daneben.

## v0.21.2 (HUDPilot, Bau-Fehler behoben)
- Zwei doppelte Namen in der Kartenansicht beseitigt (Zoom-Knopf-Feld und Info-Spalte), die den Bau in Android Studio gestoppt haben. Sonst unverändert zu 0.21.1.

## v0.21.1 (HUDPilot, Einstellungen auf dem Server sichern)
- **Einstellungen sichern und holen (braucht Server v1.12):** Menü → **Server** → „Einstellungen sichern" legt Anzeige, Standard-Profil, Vermeiden, Sprachansagen, HUD-Einstellungen und Zielverlauf unter deinem Profil auf dem Server ab. „Einstellungen holen" spielt sie auf einem anderen Handy wieder ein.
- **Nachfrage beim Anmelden:** Meldest du dich auf einem Handy mit vorhandenem Profil an und es gibt eine Sicherung, fragt die App „Einstellungen vom Server übernehmen?" (mit Datum und Handy der Sicherung).
- **Nicht gesichert:** Serveradresse, Profil-Code, Bluetooth-Adresse des HUD, Karten/Regionen, Import-Stand. Es wird nie von selbst hochgeladen, damit ein frisches Handy die Sicherung nicht mit Standardwerten überschreibt. Der Server hebt die vorige Sicherung als Rückfall auf.

## v0.21.0 (HUDPilot, Profile auf dem Server)
- **Profil auf dem Server (braucht Server v1.11):** Menü → **Server**: Profil mit Name und Einladungscode **neu anlegen** (der Server schickt dir deinen persönlichen Code) oder mit Name und Code **anmelden**. Touren, Fahrten und die „Zum Handy senden"-Ablage gehören dann zu diesem Profil. Jede Person sieht nur das Eigene, auch im Planer am PC.
- **Statuszeile im Menü** bei „Server": ✓ Synchron, ⟳ N offen (Fahrten noch nicht hochgeladen), ✕ Nicht erreichbar, ✕ Kein Profil, ✕ Kein Server. Ein Tipp öffnet das Fenster mit „Jetzt abgleichen" und „Anderes Profil …".
- Ohne Profil melden Touren, Planung-Speichern und Planer-Ablage, dass erst ein Profil nötig ist.
- Nicht kompiliert und nicht auf dem Handy getestet. Der Server wurde lokal mit Beispieldaten geprüft.

## v0.20.8 (HUDPilot, neue Navigationsanzeige)
- **Neue Anzeige bei Navigation (Hochformat):** Oben eine dunkle, leicht durchsichtige Karte: links Abbiegepfeil mit Entfernung (und „danach"/Spurhinweis), rechts **Ankunft** und **Verbleibend**. Ein Tipp auf Ankunft wechselt zwischen Ankunftszeit und Restzeit, ein Tipp auf Verbleibend zwischen Kilometern und Restzeit (merkt sich die Wahl, „›" zeigt, dass man tippen kann).
- **Tempo und Tempolimit als Kreise:** Unter der Karte rechts der runde Tempo-Kreis (rot bei Überschreitung), links daneben gleich groß das Tempolimit im Schilddesign. Ohne Limit fehlt nur das Schild. Ohne Navigation bleiben die Kreise unten links.
- **Querformat:** Die Karte steht als schmale Spalte rechts (Pfeil, Entfernung, Ankunft, Verbleibend), Tempo und Limit links daneben.
- **Knöpfe bei Navigation:** ⚙ unten links, der Ansichts-Knopf (N/↑/3D) bleibt unten rechts immer sichtbar, ● HUD, Stopp, ⌖ und +/− erscheinen mit einem Tipp auf die Karte darüber.
- **Weg:** Fortschrittsbalken, die breite Leiste unten und die Zeile Uhrzeit · Höhe · Kompass (samt den vier Schaltern im Anzeige-Menü).
- Nicht kompiliert und nicht auf dem Handy getestet. Das Layout (Abstände, Querformat) kann noch Feinschliff brauchen.

## v0.20.7 (HUDPilot, Neuberechnung, Magenta nachts, Fortschrittsbalken)
- **Neuberechnung mit Fahrtrichtung:** Die neue Route beginnt jetzt in die Richtung, in die du fährst (ab 10 km/h), und schickt dich nicht mehr zu einer Kreuzung zurück, die du schon hinter dir hast. Im Log steht die Fahrtrichtung. Die Wartezeit zwischen Neuberechnungen wächst nur noch auf höchstens 15 s (vorher 120 s): 5, 10, 15 s. Bist du schon über 100 m weg, wird nach 3 s erneut berechnet.
- **Route nachts in Magenta:** Tags bleibt sie orange, nachts Magenta (die Straßen der Nachtkarte sind selbst orange). Die Linie ist dicker (10 statt 8, Rand 14 statt 11).
- **Pfeil größer:** Kern schwarz, Rand in der Routenfarbe (tags orange, nachts Magenta).
- **„Danach sofort":** Folgt die nächste Abbiegung weniger als 60 m danach, sagt die Ansage zum Beispiel „In 250 Metern links abbiegen, danach sofort rechts abbiegen", auch ohne „viele Ansagen".
- **Fortschrittsbalken:** Er zählt die schon gefahrene Strecke vor einer Neuberechnung mit, statt nach jeder Neuberechnung wieder bei null zu beginnen.
- **Keine Kopie mehr in Downloads:** Fahrten bleiben im App-Ordner (Teilen in der Fahrtansicht). Schon vorhandene Dateien in Download/GPX-Tracking bleiben liegen und müssen von Hand gelöscht werden.
- **Warnton bei Tempoüberschreitung** kommt jetzt auch bei echter Navigation ohne verbundenes HUD (Schalter muss an sein; bei geschätzten Limits bleibt er stumm).
- Nicht kompiliert und nicht auf dem Handy getestet.

## v0.20.6 (HUDPilot, Zoom, Ansagen lauter, Einrasten, Parkplatz)
- **Zoom bei Fahrt näher:** Stufen jetzt 17,5 / 16,5 / 15,5 / 14,5 nach Tempo (vorher eine Stufe weiter weg). Im Menü ⚙ → Anzeige → „Zoom bei Fahrt" lässt sich zwischen weit (wie früher), normal und nah umschalten.
- **Ansagen lauter:** In den Sprachansagen gibt es „Verstärkung: aus / laut / sehr laut" (+6 bzw. +12 dB über die Handy-Lautstärke hinaus). Die Ansage wird dazu in eine Datei gesprochen und mit Lautstärke-Anhebung abgespielt. Geht dabei etwas schief, spricht die App wie bisher normal. Kein Ton davor, nichts doppelt.
- **Pfeil rastet ein:** Bei Navigation sitzt der Pfeil auf der Route, wenn sie höchstens 40 m entfernt ist (vorher 30 m, jetzt wie die Schwelle für „von der Route ab"). Beim freien Fahren (ab 8 km/h) rastet der Pfeil auf die nächste Straße der Karte ein, wenn eine in Fahrtrichtung höchstens 28 m entfernt ist, sonst bleibt er auf der GPS-Position.
- **Parkplatz:** Abseits der Route und stehend oder im Schritttempo (unter 8 km/h) wird nicht neu berechnet. Oben steht „Pause – abseits der Route". Es geht erst wieder los, wenn du losfährst.
- Nicht kompiliert und nicht auf dem Handy getestet.

## v0.20.5 (HUDPilot, Pfeil-Position, REC, GPS-Hinweis)
- **Pfeil wieder im unteren Drittel:** Die Randabstände der Routenvorschau konnten in die Navigation „mitrutschen", der Pfeil saß dann etwa in der Mitte. Die Vorschau kann nach dem Start nicht mehr dazwischenfunken, und die Karte setzt den Rand bei jeder Kamerabewegung notfalls neu.
- **REC:** Bei laufender Aufnahme ist der Knopf nur noch rot mit „■ REC" (keine Zeit und Strecke mehr). Im Ruhezustand bleibt er dunkel mit rotem Punkt.
- **Echte Navigation ohne GPS-Position:** oben steht „Warte auf GPS …", das Banner zeigt schon die erste Anweisung (Abstand ab Routenstart). Kommt länger als 20 Sekunden kein Signal, steht eine Zeile im Log.
- Nicht kompiliert und nicht auf dem Handy getestet.

## v0.20.4 (HUDPilot, aufgeräumte Karte)
- **Suche hinter einer Lupe:** oben links ist nur noch der runde Lupenknopf. Ein Tipp klappt Suchfeld, Kategorien (Tanken, Essen & Trinken, Hotel …) und die Tastatur auf, das ✕ klappt alles wieder zu. Nach der Wahl eines Treffers oder Verlaufseintrags klappt die Suche von selbst zu. Treffer einer Kategorie bleiben auf der Karte. Die Zurück-Taste schließt erst die Liste, dann die Suche.
- **REC unten rechts:** fester Platz in der rechten unteren Ecke, springt nicht mehr, wenn das Tempolimit-Schild kommt oder geht.
- **Weniger Knöpfe rechts:** ⚙ **ist jetzt das Menü** (vorher ▦, auch während der Navigation; langer Druck beendet die Navigation), darunter ● HUD und die Ansicht N/↑/3D. Das ⌖ (Zentrieren) erscheint nur noch, wenn die Karte nicht mehr folgt. Der Tag/Nacht-Knopf ist weg (Einstellung weiter unter ⚙ → Anzeige → Nachtmodus).
- **+ und −** stehen im Hochformat abgesetzt weiter unten rechts (im Querformat wie bisher als zweite Spalte).
- **Während der Navigation** sind die Knöpfe zugeklappt. Ein Tipp auf die Karte zeigt sie für 8 Sekunden.
- Hinweistexte nennen jetzt ⚙ statt ▦.
- Nicht kompiliert getestet.

## v0.20.3 (HUDPilot, weniger Hinweise vor Kreisverkehren, Logname)
- **Leichte Kurven vor einer Abbiegung entfallen:** Liegt höchstens 250 m danach ein Kreisverkehr oder eine andere Abbiegung (Links/Rechts abbiegen, scharf, Wenden, Ausfahrt), wird die leichte Kurve nicht mehr angezeigt und nicht mehr angesagt. Der Kreisverkehr wird dafür früh angesagt („In 700 Metern im Kreisverkehr die dritte Ausfahrt nehmen") und steht gleich im Pfeilfeld und am HUD.
- **Gleiche leichte Kurven dicht hintereinander** (bis 120 m) werden eine. Einzelne leichte Kurven bleiben.
- **Logdatei heißt jetzt `hud-pilot-log.txt`** (vorher noch `osmand-hud-bridge-log.txt`). Die alte Datei wird beim ersten Start übernommen.
- Neuer Test HintMergeTest.
- Nicht kompiliert getestet.

## v0.20.2 (HUDPilot, Abbiegepfeil richtig herum)
- **Fix:** Der gezeichnete Abbiegepfeil in der Kartenansicht war links/rechts vertauscht (zum Beispiel zeigte „Leicht rechts" einen Linkspfeil). Betroffen waren alle Abbiegepfeile und die Wenden. Text, Sprachansage und HUD waren nicht betroffen.
- Das Fahrtprotokoll nennt jetzt zu jedem Hinweis den Befehl, der ans HUD geht (zum Beispiel „HUD-Pfeil KEEP_RIGHT (6)").
- Nicht kompiliert getestet.

## v0.20.1 (HUDPilot, Vermeiden beim Plan auf dem Server)
- „Plan auf Server speichern" schickt jetzt auch die Vermeiden-Schalter mit. Passend zu Server v1.10 (Planer mit „Schnellste" und Vermeiden). Beim Öffnen eines Plans aus dem Web gelten weiter die Schalter der App.
- Nicht kompiliert getestet.

## v0.20.0 (HUDPilot, Schnellste mit Autobahn, Vermeiden)
- **Neues Profil „Schnellste (mit Autobahn)":** bevorzugt Autobahn und Schnellstraßen, dann Bundes- und Landstraßen.
- **Autobahn in allen Profilen erlaubt:** bei Landstraße, Kurvig und Schnell nur sehr teuer, also nur zur Not. Kraftfahrstraßen (z. B. Bundesstraßen mit Autobahncharakter) sind damit nicht mehr komplett gesperrt.
- **Vermeiden** (▦ → Navigation, direkt unter Standard-Profil): Autobahn, Fähren, Mautstraßen, unbefestigte Wege. Gilt für alle Profile, auch bei der Neuberechnung unterwegs. Autobahn vermeiden schließt Autobahn und Kraftfahrstraßen ganz aus; Fähren, Maut und unbefestigte Wege werden sehr teuer, aber nicht verboten (sonst gäbe es manchmal keine Route). Motorrad-Maut-Ausnahmen (toll:motorcycle=no) werden beachtet.
- Die Profildateien wurden mit dem BRouter-Code geprüft (Kostenfaktoren je Straßenart und Schalter). Auf dem Handy noch nicht getestet.
- Server-Planer kennt das neue Profil noch nicht (er nimmt dafür das erste Profil).
- Nicht kompiliert getestet.

## v0.19.3 (HUDPilot, Profil im Infoband, Routenvorschau)
- **Profil-Chip im Infoband** der Routenvorschau (z. B. „Kurvig ▾“): Tippen öffnet eine Liste aller Profile über dem Band, das aktuelle ist markiert, Auswahl rechnet die Route sofort neu. Die Profil-Zeile im ausgeklappten Bereich entfällt. Das Standard-Profil bleibt unter ▦ → Navigation.
- **Routenvorschau:** die ganze Strecke wird in voller Breite zwischen Suchleiste und Infoband gezeigt (Norden oben, nicht geneigt, nicht unter den runden Knöpfen) und passt sich an, wenn das Band größer oder kleiner gezogen wird. Beim Start kehrt die gemerkte Ansicht zurück.
- Nicht kompiliert getestet.

## v0.19.2 (HUDPilot, Kartenstile, 3D merken, niedrigere Häuser)
- **Kartenstil** (▦ → Anzeige): **Standard**, **Kräftig** (satte Farben, Straßen in Gelb/Orange, warmer Untergrund) und **Kontrast** (weißer bzw. schwarzer Untergrund, breite Straßen mit dunklem Rand, schwarze bzw. weiße Beschriftung zum Fahren in der Sonne), jeweils für Tag und Nacht. **Ruhig** (weniger Beschriftung) ist ein eigener Schalter und lässt sich mit jedem Stil kombinieren.
- **Ansicht wird gemerkt:** N / Fahrtrichtung oben / 3D bleiben nach dem Schließen der App erhalten. Mit eingeschalteten 3D-Gebäuden startet die Karte ohne gemerkte Ansicht gleich geneigt.
- **Häuser niedriger:** unbekannte Höhe 6 m, echte Höhen mal 0,6, höchstens 40 m.
- Nicht kompiliert getestet.

## v0.19.1 (HUDPilot, 3D-Gebäude Höhe)
- Fix: 3D-Gebäude blieben flach, weil die Kartendaten bei unbekannter Höhe den Wert 0 liefern. Höhe unter 3 m oder fehlend zählt jetzt als unbekannt und wird als 9 m gezeichnet; echte Höhen aus den Daten bleiben. Helleres Dach für mehr Kontrast zu den Wänden.
- Nicht kompiliert getestet.

## v0.19.0 (HUDPilot, alte Pilot-Seite entfernt)
- **Werkzeuge → „Navigation HUDPilot“ (Gebiete & Routen öffnen) und die alte Pilot-Seite sind weg.** Was dort steckte, hat jetzt seinen Platz in der Karte:
  - **GPX-Import:** „Teilen“ / „Öffnen mit“ aus Kurviger usw. öffnet die Karte und legt die Route in die Planung. Dazu ▦ → Karte → **GPX-Datei öffnen …**. Nicht während einer Navigation.
  - **Profil:** ▦ → Navigation → **Standard-Profil** (Landstraße, kurvig, schnell) für neue Routen; die Auswahl in der Routenvorschau bleibt.
  - **Simulation:** wie bisher in der Karte (Routenvorschau → Simulation, Tempo, Pause, Abweichung).
  - **Regionen laden:** ▦ → Karte → Regionen laden.
- Nicht mehr da: die Anweisungsliste der alten Seite.
- Nicht kompiliert getestet.

## v0.18.3 (HUDPilot, Spurhinweise im HUD)
- **Spurinfo am HUD** (Bildschirm Navigator): ab 400 m vor einer Abbiegung, Ausfahrt oder einem Halten auf einer Straße mit mindestens 2 Spuren zeigt das HUD die Spuren von links nach rechts. Die äußere Spur zur Abbiegeseite ist ein heller Pfeil (rechts/links), die übrigen sind dunklere Geradeauspfeile. Danach verschwindet die Anzeige wieder. Näherung aus der Spuranzahl; echte Spurzuordnung kennen die Routendaten nicht.
- Schalter unter ▦ → Anzeige → **Spurhinweise im HUD** (an).
- Nicht kompiliert getestet.

## v0.18.2 (HUDPilot, Spur-Test fürs HUD)
- **Element-Test, Code 13 „Spurinfo“:** jedes Antippen schickt ein anderes Spurmuster (1/4 bis 4/4: 3 Spuren rechts empfohlen, 3 Spuren Mitte empfohlen, 4 Spuren, 2 Spuren). Eine Einblendung nennt das Muster. So lässt sich sehen, wie das HUD Spuren zeichnet; danach folgen Spurhinweise im HUD.
- Nicht kompiliert getestet.

## v0.18.1 (HUDPilot, Aufnahme-Kapsel, Spurhinweise, Zoom vor Abbiegungen)
- **Aufnahme-Kapsel** statt Rundknopf: sitzt rechts neben Tempo und Tempolimit-Schild. Ruhe: dunkel mit rotem Punkt und „REC“. Aufnahme: rot mit „■ Laufzeit · Strecke“. Bleibt auch bei ausgeblendeter Anzeige sichtbar, nur nicht bei Planung/Navigation. Das Zeitfeld oben entfällt.
- **Spurhinweis** in der Abbiegebox (ab 500 m vor der Anweisung): „3 Spuren · rechts einordnen“ bei Abbiegen, Halten und Ausfahren auf mehrspurigen Straßen. Die Routendaten kennen nur die Spuranzahl, keine Spurpfeile; deshalb ist es eine Näherung (links/rechts einordnen).
- **Zoom vor Abbiegungen** (Anzeige → „Zoom vor Abbiegungen“, an): 220 m vor Abbiegen, Halten, Ausfahrt oder Kehre zoomt die Karte näher heran (17,5; bei mehr als 2 Spuren, Halten und Ausfahrt 18), danach wieder nach Tempo.
- **3D-Gebäude:** kräftigere Farbe, Standardhöhe 10 m, beim Einschalten neigt sich die Karte gleich. Im Protokoll steht, ob die Kastenebene im Stil ist (zur Fehlersuche, falls die Häuser flach bleiben).
- Nicht kompiliert getestet.

## v0.18.0 (HUDPilot, Kartenstil, 3D-Gebäude, Menü)
- **Menü → Anzeige:** neuer Eintrag **Kartenstil** (Standard oder **Ruhig**: ohne Hausnummern, Orte-Symbole, Straßen-Nebennamen, Schilder und kleine Ortsteile) und Schalter **3D-Gebäude** (Kästen mit Höhe aus den Kartendaten, sonst 8 m; ab Zoom 15 und bei geneigter Karte sichtbar). Gilt für Tag und Nacht.
- **Menü-Reihenfolge:** Navigation, Anzeige, Karte, Touren, App-Einstellungen, Mehr.
- **Aufnahme-Knopf** sitzt jetzt unten rechts, größer und getrennt von den anderen Knöpfen; Punkt und Quadrat sind gezeichnet und genau mittig. Beim Aufnehmen: roter Kreis mit weißem Quadrat.
- Server: Karte auf der Touren-Seite größer (mindestens 460 px, 62 % der Höhe).
- Nicht kompiliert getestet.

## v0.17.2 (HUDPilot, Bau-Fix und Beispielfahrten)
- **Beispielfahrten sind gleich da:** Beim ersten Öffnen der Touren-Seite legt die App 3 Beispielfahrten an (nur wenn noch keine Fahrt existiert). Der Server legt seine 4 beim ersten Start von v1.9 an. Der Knopf unten entfernt sie wieder.
- Fix: Bauen scheiterte in TourSync.kt (`return` in einer Funktion mit `=`, seit v0.16.0). Die Funktion hat jetzt normale Klammern. Sonst unverändert zu v0.17.0.

## v0.17.0 (HUDPilot, Aufnahme-Knopf in der Karte)
- **Aufnahme-Knopf in der Karte** (roter Punkt, wie bei Calimoto): Antippen startet die Aufnahme, oben erscheint „● Zeit · Strecke“, nochmal antippen (■) beendet und **speichert sofort** als „Fahrt am <Datum Uhrzeit>“, ohne Namensfenster. Der Knopf ist nur da, solange keine Route geplant wird oder läuft; eine laufende Aufnahme geht bei Planung und Navigation weiter.
- **Fahrten haben jetzt eine Art:** „frei“ (Aufnahme-Knopf, Tracking-Seite) oder „Navigation“. Ältere Fahrten gelten als frei. Die Art geht auch an den Server.
- **Touren-Seite mit drei Reitern:** Geplant, Gefahren (Navigation), Aufnahmen (frei). Die Tracking-Seite in den Einstellungen zeigt die freien Aufnahmen; die Saison-Übersicht zählt alle.
- **Zusatzaufzeichnung** (Höhe, Schräglage) gilt für alle Aufnahmen, auch die bei der Navigation (der Schalter wirkte schon für jede laufende Aufnahme).
- **Beispielfahrten:** auf der Touren-Seite unten „Beispielfahrten anlegen“ (3 erfundene Fahrten, Name beginnt mit „Beispiel:“) und wieder entfernen.
- Server v1.9: Touren-Seite mit drittem Reiter „Aufzeichnungen“, Fahrt-Art, Beispielfahrten per Knopf.
- Nicht kompiliert getestet.

## v0.16.0 (HUDPilot, Touren-Seite)
- **Neue Seite „Touren“** (▦ → Touren), Vollbild mit zwei Reitern:
  - **Geplant:** Touren vom Server (Webplaner), die Liste bleibt offline sichtbar. Antippen legt die Tour in die Planung der Karte.
  - **Gefahren:** deine aufgezeichneten Fahrten mit Datum, Strecke, Zeit, Höchsttempo und Hinweis „auf Server / noch nicht“. Antippen öffnet die Fahrt mit Diagrammen; langer Druck: als Route nachfahren oder jetzt auf den Server sichern.
- **Fahrten gehen automatisch auf den Server,** sobald die Karte im Heim-WLAN geöffnet wird (alle Rohdaten: Tempo, Limit, Höhe, Schräglage). Auf der Serverseite „Touren“ gibt es dazu Karte, Diagramme und Kennzahlen.
- **▦ → Karte → „Plan auf Server speichern“** sichert die aktuelle Planung als geplante Tour.
- Server v1.8: Touren-Seite mit Karte (Linie nach Tempo/Schräglage/Höhe gefärbt, auch 3D), Diagramme (Tempo und Limit, Höhe, Schräglage, Beschleunigen/Bremsen), GPX/CSV, Umbenennen, Löschen.
- Nicht kompiliert getestet.

## v0.15.0 (HUDPilot, Web-Planer)
- **Neu: Tourenplaner im Browser** (Server v1.4, Adresse http://<Server>/planer). Start, Ziele und Zwischenpunkte per Klick auf die Karte, Marker verschiebbar, Profil wählen (Landstraße, kurvig, schnell). Die Route rechnet BRouter auf dem Server, mit denselben Profilen und Routendaten wie in der App.
- Touren auf dem Server speichern, wieder öffnen, löschen; GPX herunterladen.
- **„An Handy senden“:** Beim Öffnen der Karte (oder beim Zurückkommen in die App) fragt die App „Neue Route vom Planer … Jetzt laden?“ und legt Start, Zwischenziele und Ziel in die Planung. Danach rechnet die App die Route selbst und du kannst starten. Nicht während der Navigation.
- Höchstens 27 Punkte (Start, 25 Zwischenpunkte, Ziel). Für Gebiete ohne geladene Routendaten auf dem Server meldet der Planer das.
- Nicht kompiliert getestet.

## v0.14.1 (HUDPilot, Verbindung zum eigenen Server)
- **Fix: "Server nicht erreichbar" bei Regionen laden.** Android blockierte die unverschlüsselte Verbindung (http) zum Heimserver. Die App erlaubt sie jetzt. Android kann das nicht auf das Heimnetz beschränken; alle Internet-Adressen im Code (brouter.de, OSM) bleiben https.
- Nicht kompiliert getestet.

## v0.14.0 (HUDPilot, Regionen vom eigenen Kartenserver, alte Karte entfernt)
- **Regionen laden:** Neue Seite (▦ → Karte → Regionen laden, auch auf der HUDPilot-Seite). Serveradresse eintragen (z. B. 192.168.1.10), Liste nach Land/Bundesland, ankreuzen, laden. Pro Region kommen Karte (.pmtiles), Routendaten (.rd5) und Orte (Tanken, Essen & Trinken, Hotel, Parken) vom Server. Downloads sind fortsetzbar. Ist eine Region auf dem Server noch nicht gebaut, baut er sie auf Anforderung; die App wartet und lädt dann weiter.
- **Mehrere Regionen gleichzeitig auf der Karte:** Jede geladene Region ist eine Kartenquelle, die Ebenen werden abwechselnd gezeichnet, damit sich Regionen nicht überdecken. Eine selbst gewählte .pmtiles-Datei bleibt möglich.
- **Orte-Suche (Tanken, Essen …) aus der heruntergeladenen Datei:** funktioniert offline und bei weitem Zoom (Umkreis bis etwa 120 km), ohne Overpass. Ohne Orte-Datei bleibt die Suche aus der Karte wie bisher.
- **Server meldet sich:** Die App meldet ihre Regionen beim Server (/api/need); der Server hält nur gemeldete Regionen aktuell. Beim Start im WLAN zeigt die App einen Hinweis, wenn es Neueres gibt.
- **Alte Mapsforge-Karte entfernt** (Activity, Download, Menüeintrag, Bibliotheken Mapsforge und AndroidSVG). Die Karte ist jetzt immer die MapLibre-Karte.
- Server v1.3 (hudserver): Bedarfsgesteuert, Statusseite mit "Gebraucht"-Spalte, manifest.json mit Liste aller Regionen.
- Nicht kompiliert getestet.

## v0.13.4 (HUDPilot, Knöpfe und Zielverlauf)
- **+ und − liegen nicht mehr über dem Menü und der Routenvorschau.** Im Hochformat sitzen sie in der rechten Knopfspalte unter dem ●-Knopf (wie im Querformat). Die Knöpfe werden nicht mehr nach vorn geholt.
- **Tempo-Feld und Tempolimit** werden ausgeblendet, solange die Routenvorschau offen ist.
- **Verlauf zeigt nur gefahrene Ziele:** Ein Eintrag entsteht erst bei START (nicht bei Suche, Antippen oder Simulation). Bis zu 10 Einträge, scrollbar, mit Ort und Alter ("heute", "gestern", "vor 5 Tagen"). Langer Druck löscht einen Eintrag, "Verlauf löschen" alle. Alte Suchbegriffe aus dem Verlauf werden entfernt.
- Nicht kompiliert getestet.

## v0.13.3 (HUDPilot, neues Menü, POI-Suche aus der Karte)
- **Neues Menü** als dunkles Blatt von unten: große Zeilen mit Symbol, Wert und Pfeil, Schalter für Ein/Aus-Einstellungen, oben links die runde "‹"-Taste zurück. Ein Tipp neben das Blatt oder die Zurück-Taste schließt es. Seiten: Karte, Navigation (Sprachansagen, Ansage-Häufigkeit, Aufzeichnung), Anzeige (Nachtmodus und Schalter für Warnsymbole, Danach-Hinweis, Fortschrittsbalken, Uhrzeit, Höhe, Kompass), App-Einstellungen, Mehr. Änderungen bei der Anzeige wirken sofort.
- **Suche nach Tanken, Essen & Trinken, Hotel, Parken** liest jetzt die POIs der Karte (offline, sofort) im sichtbaren Ausschnitt statt der überlasteten Overpass-Server. Dazu etwa auf Ortsansicht heranzoomen. Nicht alles ist in den Kartenkacheln enthalten; die POI-Dateien vom Server folgen.
- **Querformat:** Die untere Leiste (Restzeit, km) endet links von den Knopfspalten und überdeckt sie nicht mehr.
- Nicht kompiliert getestet.

## v0.13.2 (HUDPilot, Menü, Ansagen, Pfeile, 3D)
- **Menü neu:** ▦ öffnet ein Hauptmenü mit Unterseiten (Karte, Navigation, Anzeige, App-Einstellungen, Mehr). In jeder Unterseite steht oben "‹ Zurück", und Auswahlfenster führen nach OK oder Abbrechen wieder in die Unterseite zurück. Die **App-Einstellungen** sind auch während der Navigation erreichbar.
- **Karte-Knopf schaltet N → ↑ → 3D:** Norden oben (N), Fahrtrichtung oben (↑), Fahrtrichtung oben und geneigt (3D). Die Neigung wird bei jeder Kamerabewegung mitgegeben und geht nicht mehr verloren (vorher setzte die Positionsnachführung sie zurück). Der Menüpunkt "Neigung 3D" entfällt.
- **Mehr Ansagen:** Leichte Abbiegungen ("Leicht rechts") werden jetzt angesagt ("leicht rechts abbiegen"). Neue Einstellung **Ansage-Häufigkeit** (Menü Navigation): "normal" wie bisher, "viele" mit zusätzlicher früher Vorankündigung (ab etwa 2 bis 3 km bei hohem Tempo), Ansage von "links/rechts halten" und "danach …" bei zwei dicht aufeinander folgenden Abbiegungen.
- **Gezeichnete Abbiegepfeile** (links, rechts, leicht, scharf, Wenden, Kreisverkehr mit Ausfahrtsnummer, Zwischenziel, Ziel) statt Schriftzeichen.
- **Tippen auf die Karte** blendet bei laufender Navigation Tempo und Abbiegefeld nicht mehr aus.
- **Simulation:** "SIM" startet nicht mehr auf einer Route von 0 m.
- Neue Tests für die Ansagen (leichte Abbiegungen, "viele", "danach"). Nicht kompiliert getestet.

## v0.13.1 (HUDPilot, Bedienung der neuen Karte)
- **Orange Pfeile auf der Karte behoben:** Der Kartenstil zeichnet Einbahnstraßen mit einem Symbol namens "arrow". Unser orangefarbener Positionspfeil hatte denselben Namen und wurde dadurch auf Einbahnstraßen eingestreut. Alle eigenen Symbole heißen jetzt anders ("hud-…"), die Einbahn-Pfeile sehen wieder normal aus.
- **Zahnrad während der Navigation:** Bei laufender Navigation bleibt die Karte aufgeräumt, nur das ⚙ oben rechts ist sichtbar. Ein Tipp darauf klappt alle Knöpfe auf (Stopp, Position, Norden, ▦-Menü, Nacht, HUD, Zoom); nach 8 Sekunden ohne Tippen klappen sie wieder zu. Langer Druck auf ⚙ beendet die Navigation wie bisher. Ohne Navigation sieht alles aus wie vorher.
- **▦-Menü und Einstellungen sind damit auch während der Fahrt erreichbar.**
- **Stopp-Knopf:** Das Quadrat wird gezeichnet und sitzt genau in der Mitte.
- **Querformat:** Simulationsleiste oben in der Mitte, untere Leiste rückt nach rechts und liegt nicht mehr unter dem Tempo-Feld oder den Knöpfen.
- **Optik:** Runde dunkle Knöpfe, stärker abgerundete Felder.
- Nicht kompiliert getestet.

## v0.13.0 (HUDPilot, neue Hauptkarte mit MapLibre)
- **Neue Karte auf MapLibre** (Vektorkarte aus deiner `.pmtiles`-Datei), flüssiger als die alte. Sie öffnet sich über ▦ → "Neue Karte (MapLibre) …" in der alten Karte. In ihrem eigenen ▦-Menü gibt es "Karte wählen (.pmtiles) …", "Beim Start diese Karte öffnen" (Häkchen), "Alte Karte (Mapsforge) öffnen" und die Prüfseite. Die alte Karte bleibt unverändert.
- **Gleiche Bedienung wie bisher:** Suche mit Verlauf und Kategorien (Treffer als blaue Nummern im sichtbaren Ausschnitt), langer Druck setzt das Ziel, Zwischenziele, Start wählen, Routenvorschau mit Profilen, START ▶, Rückfrage beim Beenden, Aufzeichnung mit Fragen, Warnsymbole, Nachtmodus (automatisch, auch im Tunnel), Anzeige (Pfeil, Abstand, danach, Ankunft/Restzeit, Tempo, Tempolimit, Uhrzeit, Höhe, Kompass).
- **Pfeil rastet auf der Route ein**, die Karte dreht sich nach Fahrtrichtung (Zwei-Finger-Drehen schaltet auf freies Drehen, "N" stellt zurück), Zoom passt sich dem Tempo an, Position sitzt unten im Bild. Neigung 3D im Menü. Namen und Symbole bleiben beim Drehen von selbst aufrecht.
- **Simulationsmodus:** In der Routenvorschau neben START der Knopf "SIM". Das virtuelle Fahrzeug fährt die Route ab. Leiste oben links: 50 und 100 km/h, Zeitraffer 10× und 50×, "↔" (200 m neben die Route setzen, zum Testen der Neuberechnung) und Pause. Ansagen, Pfeil und Anzeige am HUD laufen wie bei einer echten Fahrt. Nach der Simulation bleibt die Route stehen.
- Noch nicht in der neuen Karte (kommt in v0.14.0 und v0.15.0): "Region laden", Freies-Fahren-Pfeil, Hausnummern, Kartenstil-Auswahl. Der Kartenstil ist fest (Protomaps hell/dunkel), POI-Symbole sind noch wenige.
- Nicht kompiliert getestet.

## v0.12.3 (HUDPilot, MapLibre-Bibliothek aktualisiert)
- Die Kartenbibliothek MapLibre ist von 11.7.0 auf **11.8.8** aktualisiert. Grund: Die Prüfung in v0.12.2 zeigte gültige, vollständige Kartendateien, aber es wurden keine Kacheln gezeichnet. In 11.7.0 gab es einen bekannten Fehler beim Lesen lokaler PMTiles-Dateien (die ganze Datei statt des angeforderten Stücks), der ab 11.8.8 behoben sein soll.
- Sonst unverändert (die Prüfung und die Fehlermeldungen auf der Testseite bleiben). Bleibt die Karte leer, ist als Nächstes Version 13.5.3 dran.
- Nicht kompiliert getestet.

## v0.12.2 (HUDPilot, MapLibre-Fehlersuche)
- **Nur die MapLibre-Testseite:** Neuer Knopf "Prüfen" liest den Kopf der Kartendatei und zeigt unten: ist es gültiges PMTiles (Version, Vektor/Raster), Zoomstufen, Gebiet, Anzahl Kacheln, Kompression, und ob die Datei vollständig kopiert wurde. Dazu die aktuelle Ansicht und wie viele Objekte gerade gezeichnet werden.
- Ladefehler der Karte, der verwendete Dateipfad und "Stil geladen" erscheinen unten auf der Seite und im Log (Zeilen mit "MapLibre").
- Hintergrund: In v0.12.0 blieb die Karte hellgrau (nur der Stilhintergrund, keine Kacheln aus der Datei).
- Nicht kompiliert getestet.

## v0.12.1 (HUDPilot, Navigation repariert)
- **Weniger und nur wichtige Ansagen:** Angesagt werden nur noch echte Abbiegungen (links, rechts, scharf, Kreisverkehr, Wenden, Auffahrt/Ausfahrt). Leichte Kurven ("links halten", "rechts halten") bleiben stumm. Mindestabstand zwischen zwei Ansagen (6 s für "In 300 Metern …", 2,5 s für "Jetzt …"), jede Abbiegung höchstens je einmal, auch wenn die "nächste Anweisung" hin- und herspringt. "Route wird neu berechnet" höchstens einmal in 45 s. Derselbe Satz wird nie zweimal innerhalb von 10 s gesprochen.
- **Feste Stimme:** Beim Start wird immer dieselbe Stimme gewählt (deine gewählte, sonst die beste installierte deutsche), damit sie nicht zwischen Stimmen wechselt. Der Name der Stimme steht im Protokoll.
- **Bremse gegen Neuberechnungs-Schleifen:** Ungenaues GPS lässt die Schwelle für "neben der Route" wachsen (ab 100 m Ungenauigkeit zählt der Fix gar nicht). Folgen Neuberechnungen schnell aufeinander, wächst die Wartezeit (8 → 16 → 32 → 64 → 120 s).
- **Navigation endet, wenn die App weggewischt wird** (samt Ansagen).
- **Fahrtprotokoll:** Im Log (Hauptseite → Log) steht jetzt die Navigation: Start und Ende mit Grund, jede Ansage mit Text, Abstand und Tempo, Wechsel neben/auf der Route mit Abstand und GPS-Genauigkeit, jede Neuberechnung mit Ergebnis oder Fehler, und alle 15 s eine Zeile mit Fortschritt, nächstem Hinweis und Tempo.
- **Benachrichtigungen erlauben:** Die Karte fragt (ab Android 13) nach der Erlaubnis für Benachrichtigungen, höchstens zweimal. Ohne sie ist die Dauer-Benachrichtigung mit dem "Beenden"-Knopf unsichtbar.
- Geprüft mit einer 46-km-Probestrecke aus den Oberpfalz-Daten: BRouter liefert dort nur etwa alle 2 km einen Hinweis und alle mit der richtigen Seite. Die Ursache der "falschen" Ansagen ist damit noch nicht bewiesen; das Fahrtprotokoll soll sie beim nächsten Test zeigen.
- Nicht kompiliert getestet.

## v0.12.0 (HUDPilot, MapLibre-Test)
- **Neue Karte als Testversion (MapLibre):** ▦ → "Neue Karte (MapLibre-Test) …" öffnet eine eigene Seite, die die bisherige Karte nicht verändert. Sie zeigt eine Vektorkarte aus einer lokalen `.pmtiles`-Datei (mit "Datei wählen" aussuchen, wird in den App-Speicher kopiert), deine Position und, falls berechnet, die Route. Drehen mit zwei Fingern, Schrägansicht (Neigung 3D), Hell/Dunkel, Knopf "N" für Norden oben und "⌖" für die Position. Die Namen richten sich beim Drehen von selbst auf.
- Stil (Protomaps, deutsch), Schriften und Symbole sind in der App (kein Internet nötig).
- Nur ein Test: Noch ohne Suche, Ziele, Ansagen, Warnsymbole und "Region laden". Die bisherige Karte bleibt die Hauptkarte.
- Die App wird dadurch größer (die Kartenbibliothek bringt eigene Programmteile mit).
- Nicht kompiliert getestet. Die Bibliotheksversion `org.maplibre.gl:android-sdk:11.7.0` konnte ich hier nicht prüfen.

## v0.11.0 (HUDPilot)
- **Pfeil rastet beim Navigieren auf der Route ein:** Solange du höchstens 30 m von der Route entfernt bist, sitzt der Pfeil auf der Route statt auf der rohen GPS-Position. Bei einer Abweichung bleibt er auf der GPS-Position. (Beim freien Fahren noch nicht.)
- **Beenden nur nach Rückfrage:** Zurück-Geste, ■ und langer Druck auf ■ fragen "Navigation beenden? Ja/Nein". Die Zurück-Geste schiebt die App nicht mehr in den Hintergrund (dafür die Wischgeste von unten oder die Home-Taste).
- **Aufzeichnung bei Navigation, unabhängig vom HUD:** Beim Start fragt ein Fenster "Fahrt aufzeichnen? Ja/Nein" (mit "Nicht mehr fragen", änderbar unter ▦ → Aufzeichnung bei Navigation: jedes Mal fragen / immer / nie). Läuft schon eine Aufzeichnung (freie Fahrt), geht sie weiter, ohne zu fragen. Am Ende (Ziel erreicht oder beendet) kommt das Fenster "Fahrt beendet" mit Kennzahlen, Name, Speichern und Verwerfen (mit Rückfrage). Lief vorher eine freie Fahrt von mindestens 300 m, gibt es dazu das Häkchen "Getrennt speichern" (zwei Fahrten: frei und Navigation), sonst eine Fahrt.
- Der Bildschirm bleibt auf der Karte schon bisher an, solange die Karte offen ist (nur mit der Ein-/Aus-Taste auszuschalten).
- Nicht kompiliert getestet.

## v0.10.4 (HUDPilot)
- **Verlauf der Suche:** Tippst du ins leere Suchfeld, stehen darunter die letzten 3 Suchen und Ziele (🔍 Suchtext, 📍 Ort). Ein Tipp wählt den Ort sofort bzw. sucht neu. "Zuletzt · Verlauf löschen" leert die Liste.
- **Drehen flüssiger:** Die Totzone der Drehgeste ist kleiner (15° → 4°), sie reagiert schneller (25 ms → 10 ms), und während des Drehens sind die Namen kurz ausgeblendet. Nach dem Loslassen kommen sie aufrecht zurück.
- **Fehlende Namen beim Herauszoomen:** Bei "Namen und Symbole aufrecht" liegen die Kacheln nur noch im Arbeitsspeicher (Kacheln aus dem Datei-Zwischenspeicher hatten keine Namen). Die Karte baut sich nach dem Start dadurch etwas langsamer auf.
- Nicht kompiliert getestet.

## v0.10.3 (HUDPilot)
- **Karte mit zwei Fingern drehen** (Kreisbewegung). Dabei hört die automatische Drehung nach Fahrtrichtung auf. Der Knopf ↑/N stellt zuerst auf Norden oben zurück, ein weiterer Tipp wechselt wieder zu Fahrtrichtung oben.
- **Weniger fehlende Namen beim Zoomen** (Namen aufrecht): größerer Kachel- und Namen-Zwischenspeicher, und die Nachbar-Zoomstufe wird bei aufrechten Namen nicht mehr vorgehalten. Hilft das nicht genug, kommt als Nächstes ein Rückfall für die Übersicht.
- Nicht kompiliert getestet.

## v0.10.2 (HUDPilot)
- **Namen und Symbole bleiben aufrecht, wenn sich die Karte dreht:** Ortsnamen, Orte und Symbole wie Tankstellen werden nicht mehr ins Kartenbild gebrannt, sondern in einer eigenen Ebene darüber gezeichnet und gegen die Kartendrehung zurückgedreht. Schalter unter ▦ → Anzeige → "Namen und Symbole aufrecht" (Standard an; aus = wie bisher).
- Offen: Ob Straßennamen und Nummern (z. B. "St 2143") dabei nie kopfüber stehen, zeigt erst der Test auf dem Handy. Die Namen werden bei Drehung neu platziert, einzelne können dabei kurz wechseln.
- Nicht kompiliert getestet.

## v0.10.1 (HUDPilot)
- **Kategorie-Suche (Tanken, Essen & Trinken, Hotel, Parken) sucht im sichtbaren Kartenausschnitt** statt im festen Umkreis um dich. Schieb oder zoome die Karte an die gewünschte Stelle und tippe dann auf den Knopf. Sortiert wird nach Abstand zur Bildschirmmitte. Ist der Ausschnitt zu groß (über etwa 60 km), kommt "Bitte näher heranzoomen".
- **Kein langes Warten mehr:** zwei Server werden gleichzeitig gefragt, nach höchstens etwa 20 Sekunden kommt eine Meldung ("Server antwortet nicht, bitte nochmal tippen") mit dem Grund in Klammern.
- Die Treffer stehen als blaue Nummern auf der Karte und in der Liste (gleiche Nummer). Beim Antippen eines Treffers verschwinden die Nummern wieder.
- Nicht kompiliert getestet.

## v0.10.0 (HUDPilot)
- **"Karte + Routendaten laden" wie bei den Straßendaten:** kurzer Text, zwei Knöpfe "Datei wählen" und "Region laden". "Region laden" öffnet ein Fenster zum Durchklicken (Europa › Deutschland › Bayern …); bei jeder Ebene gibt es "Alle laden". Vor dem Laden kommt eine Rückfrage mit Größe (Karte + Routendaten zusammen) und Warnung bei mobilen Daten.
- Die lange Liste mit Häkchen ist weg. Darunter stehen nur die **geladenen Regionen** (auch eigene Karten) mit Größe, Datum und Stand der Routendaten. Tippen auf eine Zeile: Aktualisieren oder Löschen. "Updates prüfen" und "Gewählte löschen" entfallen, der Serverstand wird automatisch beim Öffnen geholt.
- Fehlen für die geöffnete Route Routendaten, steht ein Hinweis mit der passenden Region.
- Die Auswahl enthält nur die bekannten 21 Regionen (16 deutsche Länder, Österreich, Schweiz, Liechtenstein, Slowenien, Tschechien). Weitere Länder brauchen geprüfte Dateinamen vom Kartenserver.
- Nicht kompiliert getestet.

## v0.9.1 (HUDPilot)
- **Zwischenziele bis zum Start und Ziel verschieben:** ▼ beim letzten Zwischenziel macht es zum Ziel (das alte Ziel rückt als letztes Zwischenziel nach), ▲ beim ersten macht es zum Start ("Aktuelle Position" fällt dann weg; über die Startzeile holst du sie zurück). Die Ziel-Zeile hat ein ▲ (tauscht mit dem letzten Zwischenziel), eine gewählte Start-Zeile ein ▼ (tauscht mit dem ersten).
- **⇅ Start und Ziel tauschen:** neuer Eintrag unter ＋ Zwischenziel; dreht die Route um (Zwischenziele in umgekehrter Reihenfolge). Steht der Start auf "Aktuelle Position", wird sie zum Ziel (braucht GPS-Position).
- Nicht kompiliert getestet.

## v0.9.0 (HUDPilot)
- **Sprachansagen der Navigation** (Android-Sprachausgabe, funktioniert offline mit einer installierten Stimme): eine frühe Ansage ("In 350 Metern links abbiegen"; der Abstand richtet sich nach deinem Tempo, bei 50 km/h etwa 350 m, bei 100 km/h etwa 700 m, höchstens 1 km) und kurz davor "Jetzt links abbiegen". Dazu "Im Kreisverkehr die zweite Ausfahrt nehmen", "Zwischenziel erreicht", "Sie haben Ihr Ziel erreicht" und "Route wird neu berechnet". Nur Navigation: keine Ansagen zu Blitzern oder Tempolimit.
- **Einstellungen** unter ▦ → Sprachansagen: an/aus (Standard an), Stimme wählen (alle installierten Offline-Stimmen der Sprache, jeweils mit Probehören), Lautstärke (im Verhältnis zur Navigations-Lautstärke des Handys), Sprechtempo, Probe hören und "Weitere Stimmen laden" (öffnet die Android-Einstellungen der Sprachausgabe).
- Der Ton läuft wie eine Navigationsansage: Musik wird kurz leiser, die Ausgabe geht dorthin, wo das Handy Audio ausgibt (zum Beispiel Intercom per Bluetooth). Fehlt eine Stimme, erscheint beim Start ein Hinweis; die Navigation läuft dann stumm weiter.
- Nicht kompiliert getestet.

## v0.8.2 (HUDPilot)
- **Vorschau zum Schieben:** Die Vorschau unten hat einen Griff und drei Stufen: voll (Punkteliste und Profile), kompakt (Strecke, Zeit, ✕, START, das ist der Standard) und weg (nur der Griff). Ziehen am Griff nach unten oder oben oder ein Tipp darauf wechselt die Stufe. Sie schiebt sich von selbst nach unten, wenn du ＋ Zwischenziel oder Start ändern antippst, in die Suchleiste tippst oder eine Kategorie suchst. Sobald ein Punkt gesetzt ist, kommt sie auf kompakt zurück. Der Kartenausschnitt der Vorschau ist an die schmalere Leiste angepasst.
- **Leerer Block entfernt:** In der Vorschau klaffte zwischen Profilen und START eine große Lücke; die Punkteliste und die Profile erscheinen jetzt nur noch in der vollen Stufe.
- **Kurze Profilnamen** (Landstraße, Kurvig, Schnell ohne "(Motorrad)"), damit alle drei in eine Zeile passen.
- **Bessere Namen für Adress-Treffer:** statt "27" steht jetzt "Hauptstraße 27, Ort". Treffer mit eigenem Namen (Lokale) zeigen den Namen mit der Straße.
- Nicht kompiliert getestet.

## v0.8.1 (HUDPilot)
- **Querformat:** Suchleiste und Kategorie-Knöpfe nutzen jetzt die ganze Breite bis zu den Knöpfen am rechten Rand (vorher nur 320 dp, die Reihe wurde abgeschnitten). Das Wischen bleibt als Reserve.
- **Uhrzeit, Höhe und Himmelsrichtung** werden nur noch während der Navigation angezeigt; ohne Navigation schienen sie durch die Suchleiste durch.
- Nicht kompiliert getestet.

## v0.8.0 (HUDPilot)
- **Online-Suche** oben auf der Karte (nur ohne Navigation): Adresse, Ort oder Lokal eintippen und auf 🔍 tippen (Nominatim/OpenStreetMap, Treffer in deiner Nähe zuerst, mit Entfernung). Darunter Knöpfe für Tanken, Essen & Trinken, Hotel und Parken: zeigen die nächsten dieser Art im Umkreis von 15 km (Overpass). Ohne Internet erscheint ein Hinweis. Ein Tipp auf einen Treffer setzt ihn als Ziel; ist schon ein Ziel da, fragt die App: neues Ziel, Zwischenziel oder Start.
- **Zwischenziele (bis 25):** In der Vorschau steht die Liste der Punkte (Start, 1 bis 25, Ziel) mit ▲ ▼ ✕ zum Verschieben und Löschen und dem Knopf ＋ Zwischenziel. Zwischenziele hinzufügen per Suche oder langem Druck auf die Karte. Auf der Karte sind sie nummeriert (orange).
- **Start wählbar:** Tipp auf die Startzeile: aktuelle Position oder ein anderer Start (Suche oder langer Druck, grünes S). So lassen sich Routen vorab planen. Bist du beim START mehr als 500 m vom gewählten Start weg, fragt die App: von hier aus rechnen, trotzdem starten oder abbrechen. Mit "aktuelle Position" als Start wird beim START bei Bedarf (mehr als 100 m Abweichung) frisch von hier gerechnet.
- **Automatisch rechnen:** Nach jeder Änderung rechnet die Route nach einer halben Sekunde neu. START ▶ ist grau, solange gerechnet wird.
- Nicht kompiliert getestet.

## v0.7.0 (HUDPilot)
- **Die Karte ist jetzt die Startseite.** Beim Start der App öffnet sich die Navigation (sobald eine Karte geladen ist). Die bisherigen Seiten liegen darunter und sind über das Zahnrad ⚙ (oben rechts auf der Karte) erreichbar. In den Einstellungen bringt der Pfeil oben rechts neben dem i zurück zur Karte; die Zurück-Taste geht ebenfalls zurück.
- **Ziel setzen:** Langer Druck auf die Karte setzt eine Fahne. Die Route vom aktuellen Standort wird berechnet. Unten erscheint die Vorschau mit Strecke, Fahrzeit, Ankunft, den drei Profilen (Landstraße, Kurvig, Schnell) und dem Knopf START. ✕ löscht das Ziel. Ohne GPS-Fix wartet die App auf die erste Position. Eine über GPX geöffnete Route erscheint ebenfalls mit START.
- **Während der Navigation** ist das Zahnrad ein ■: langes Drücken beendet die Navigation (kurzes Tippen zeigt nur einen Hinweis). Die Zurück-Taste schiebt die App in den Hintergrund, die Navigation läuft weiter.
- **HUD-Knopf ● auf der Karte:** Farbe zeigt den Zustand (grün verbunden, orange verbindet, grau aus). Tippen öffnet ein Fenster mit Status von HUD und GPS und dem Knopf zum Verbinden oder Trennen. Der Schalter selbst bleibt in der Übersicht und wird dafür kurz geöffnet (dort erscheint auch die Geräte-Auswahl, falls noch kein HUD gekoppelt ist).
- **Karte + Routendaten laden** wie bei den Straßendaten: immer sichtbare Karte mit "Datei wählen" und "Region laden" (kein Aufklappen mehr), darunter "Updates prüfen" und "Gewählte löschen".
- Nicht kompiliert getestet.

## v0.6.0 (HUDPilot)
- **OSMAnd ist komplett entfernt.** HUDPilot navigiert nur noch mit der eigenen Navigation. Raus sind: die Verbindung zu OSMAnd, das Lesen der OSMAnd-Benachrichtigung, die Auswahl der Tempo-Quelle, der Schalter "Freies Fahren", der OSMAnd-Knopf und die OSMAnd-Statuszeile. Texte und Anleitung sind angepasst.
- Der Benachrichtigungszugriff bleibt erteilt: Die Klasse heißt bewusst weiter `OsmAndNotificationListener`, weil Android den Zugriff pro Klassenname vergibt. Sie liest nur noch Moket, WhatsApp und Anrufe.
- Nicht kompiliert getestet. Falls der Build einen Fehler meldet, bitte die Meldung schicken (am ehesten ein übersehener Verweis auf Entferntes).

## v0.5.3 (HUDPilot)
- **Alles mittig:** Texte in allen Feldern sind zentriert (vorher waren sie linksbündig, zum Beispiel "km/h" unter der Geschwindigkeit), und der Abstand oben und unten ist gleich (kein Extra-Rand der Schrift mehr). Gilt für Hoch- und Querformat.
- **Dickere, größere Zahlen:** Entfernung, Geschwindigkeit, Limit, Pfeil und die untere Leiste in der kräftigsten Schriftart des Handys (Roboto Black).
- **Farben wie in 0.5.1:** dunkelgraue Felder, weißer Text, weißer Pfeil. Der Tag-/Nacht-Wechsel der Anzeigefarben (0.5.2) entfällt. Die Knöpfe haben wieder ihren alten Look.
- Nicht kompiliert getestet.

## v0.5.2 (HUDPilot)
- **Fahrzeit korrigiert:** Die drei Motorrad-Profile waren als "für Fahrräder gültig" markiert, der Router rechnete die Zeit deshalb wie bei einem Radfahrer mit 100 Watt (etwa 16 km/h im Schnitt: 208 km in 12 h 41 min). Jetzt richtet sich die Zeit nach dem Tempolimit der Straße mit einer Obergrenze je Profil (Landstraße 80, Kurvig 70, Schnell 100 km/h). Am Testgebiet sank die Zeit einer kurzen Strecke von 196 s auf 53 s (Landstraße), 60 s (Kurvig) und 42 s (Schnell). Der gleiche Schalter ließ den Router Abbiegeverbote ignorieren, die nur für Fahrräder nicht gelten; auch das ist behoben. Die Streckenwahl selbst ändert sich dadurch nicht, außer an solchen Abbiegeverboten. Schon berechnete Routen behalten die alte Zeit, bis sie neu berechnet werden. Die Zeit bleibt eine Schätzung (in Orten ohne Tempoangabe etwas zu optimistisch).
- **Neigung entfernt** (sah nicht gut aus).
- **Neue Farben der Anzeige:** Tagsüber weiße, undurchsichtige Felder mit schwarzem Text und schwarzem Rand (bei Sonne am besten lesbar), nachts und im Tunnel dunkle Felder mit weißem Text und gelbem Abbiegepfeil. Gilt auch für die Knöpfe. Die Anzeige wechselt mit dem Kartenstil (hell/dunkel). Größere Zahlen, weniger Polster (kompakter). Rot über dem Limit bleibt (tagsüber dunkleres Rot).
- **Kartenschrift:** Unter ▦ → Anzeige → "Große Kartenschrift" (Text 1,4-fach). 
- **Route** etwas dicker (8 statt 6 dp), Farbe bleibt Orange.
- Nicht kompiliert getestet. Aussehen bei Sonne und die große Kartenschrift sind ungeprüft.

## v0.5.1 (HUDPilot)
- **Fahrradkarte (Biker) entfernt.** Ist sie als Stil gespeichert, nimmt die Karte wieder "Motorrad".
- **Hausnummern:** Standardmäßig aus. Unter ▦ → Anzeige gibt es "Hausnummern (nicht beim Navigieren)": an heißt, sie erscheinen nur, solange nicht navigiert wird; beim Navigieren sind sie immer weg. Gilt für Motorrad, Dunkel und Indigo. Bei "Standard" und "Osmarender" lassen sie sich nicht ausblenden (Standard zeigt sie ohnehin erst in sehr naher Ansicht).
- **Neigung (Test):** Unter ▦ → Neigung: Aus, Leicht (35°) oder Stark (50°). Die Karte wird nach hinten gekippt, oben wirkt sie weiter weg. Die Karte wird dafür größer als der Bildschirm gezeichnet (mehr Kacheln, etwas mehr Rechenarbeit). Es ist ein Perspektiv-Effekt auf die flache Karte, kein echtes 3D (keine Gebäude).
- Nicht kompiliert getestet. Wie die Neigung aussieht (Richtung, Ränder, Schriftgröße oben), wie sie mit Drehen und Querformat zusammenspielt und ob die Hausnummern wirklich wegfallen, ist ungeprüft.

## v0.5.0 (HUDPilot)
- **Neue Anzeige in der Kartenansicht** (wie bei Calimoto und Co), für Hoch- und Querformat; beim Drehen des Handys ordnet sie sich neu an.
  - **Abbiegepfeil mit Entfernung** (links oben), bei Kreisverkehr mit Ausfahrtsnummer.
  - **Geschwindigkeit** (unten links, wird rot über dem Limit) und **Tempolimit-Schild** daneben (nur wenn das Limit bekannt ist; braucht das Tempolimit-Modul und importierte Straßendaten).
  - **Untere Leiste:** Ankunftszeit oder Restzeit (ein Tipp auf die Leiste wechselt, die Wahl wird gemerkt) und Restweg.
  - **Hinweis "Neuberechnung …"** bei Abweichung von der Route.
  - **Ein Tipp auf die Karte** blendet die Anzeige aus und wieder ein (die Knöpfe bleiben).
  - **Optionen** (Knopf ▦ → Anzeige): Danach-Hinweis (übernächster Pfeil), Fortschrittsbalken, Uhrzeit, Höhe, Kompass, Warnsymbole.
  - Im Querformat steht der Abbiegepfeil in einer schmalen Spalte links, die Zoomtasten sind eine zweite Spalte neben den anderen Tasten, und der eigene Punkt sitzt etwas weiter rechts und unten.
  - Der Maßstabsbalken der Karte entfällt (er läge unter der Anzeige).
- **Warnsymbole auf der Karte:** Blitzer (Tempo, Abschnitt, Ampel) und Bahnübergänge sind auf der Route bis etwa 2,5 km voraus zu sehen, Zebrastreifen und Verkehrsberuhigung erst auf den letzten 300 m. Ohne Route: Punkte vor dir in Fahrtrichtung. Nur das nächste Symbol zeigt die Entfernung. Alles lässt sich unter ▦ → Anzeige → Warnsymbole ausschalten. Die Daten kommen aus dem Straßenimport und der eigenen Blitzerliste, es erscheinen also nur Symbole in importierten Gebieten, und das Tempolimit-Modul muss laufen. Es gibt keinen Banner und keine Live-Daten (mobile Blitzer, Baustellen, Staus).
- Tests: Pfeile, Entfernungen, Zeiten, Kompass, Zu-schnell, Auswahl der Warnsymbole (mit und ohne Route).
- Nicht kompiliert getestet. Aussehen, Querformat, Symbole und die Pfeilzeichen (↰ ↱ ⟳) in der Schrift des Handys sind ungeprüft.

## v0.4.1 (HUDPilot)
- **Build-Fehler behoben:** Die Kartenbibliothek Mapsforge 0.30.0 liegt nicht auf dem üblichen Server (Maven Central), sondern nur bei JitPack. JitPack ist jetzt als zusätzliche Quelle eingetragen (nur für diese Bibliothek), alle Teile der Bibliothek sind einzeln gelistet. Beim ersten Build kann JitPack ein paar Minuten brauchen.
- Sonst keine Änderung gegenüber 0.4.0. Nicht kompiliert getestet.

## v0.4.0 (HUDPilot)
- **Position weiter unten:** Beim Folgen sitzt der eigene Punkt auf etwa drei Viertel der Höhe, die Karte dreht sich um diesen Punkt. So sieht man mehr von der Strecke voraus.
- **Lücken in der Karte:** mehr vorgezeichnete Fläche (Faktor 1,5), größerer Kachel-Zwischenspeicher, ein Kachelrand und die nächstgröbere Zoomstufe werden mitgeladen. Die Karte wird weiter bei jeder Position gedreht (keine Drehsperre).
- **Kartenstile:** Knopf ▦ in der Karte: Motorrad (Standard), Fahrrad (Biker), Standard, Osmarender, Indigo, Dunkel. Die Wahl wird gemerkt.
- **Nachtmodus:** Knopf A / ☀ / ☾ schaltet zwischen automatisch, immer hell und immer dunkel. Automatisch heißt: dunkel nach Sonnenuntergang (aus Ort und Datum gerechnet, ohne Internet) und im Tunnel. Ein Tunnel wird erkannt, wenn der Straßenimport die Straße als Tunnel kennt, oder wenn das GPS bei Fahrt (ab 25 km/h) länger als 5 s ausfällt. Nach dem Tunnel bleibt es 3 s dunkel, damit es nicht flackert. Nachts und im Tunnel nutzt die Karte den Stil "Dunkel".
- **Kartenbibliothek** (Mapsforge) auf 0.30.0 angehoben, weil erst sie die Stile "Dunkel" und "Indigo" hat.
- Tests: Sonnenstand, Tunnel-Erkennung, Auswahl dunkel/hell.
- Nicht kompiliert getestet. Die neue Bibliotheksversion und das Umschalten der Stile am Gerät sind ungeprüft. Der Tunnel-Hinweis aus dem Import gilt nur in importierten Gebieten und bei eingeschaltetem Tempolimit-Modul.

## v0.3.1 (HUDPilot)
- **Karten und Routendaten zusammen:** Die Liste "Regionen laden" ersetzt die beiden getrennten Listen. Ein Haken lädt für die Region die Karte und die dazu nötigen Routendaten gleichzeitig (jede Region kennt ihre Routendaten-Kacheln, gemeinsame Kacheln werden nur einmal geladen). Unter jeder Region steht, was geladen ist ("Karte 650 MB · Routendaten 4 von 4") und ob ein Update da ist. Zwei Fortschrittsbalken (Karte, Routendaten).
- **Aufklappbar** wie die Karte "Blitzer": Die Region-Liste und die Anweisungen sind zugeklappt, ein Pfeil klappt sie auf; der Zustand wird gemerkt.
- **Löschen** entfernt die Karte und nur die Routendaten, die keine andere geladene Region braucht. "Eigene Karte hinzufügen" ist jetzt in derselben Liste.
- **GPX öffnen:** Fehlen Routendaten, wird die passende Region (die kleinste, in der ein Punkt liegt) vorgewählt und die Liste aufgeklappt.
- Die frühere Seite "Karten" entfällt, der Knopf "Karten verwalten" ebenso.
- Tests für Kacheln je Region (Rechtecke, Grenzen, alle Kacheln gibt es).
- Nicht kompiliert getestet.

## v0.3.0 (HUDPilot)
- **Neu: Kartenansicht (offline).** Auf der Seite "Gebiete & Routen" gibt es "Karte öffnen": die Karte wird auf dem Handy gezeichnet (Mapsforge, Kartenstil "Motorider"), mit der berechneten Route in Orange und der eigenen Position als Pfeil.
  - **Folgen:** die Karte läuft mit der Position mit und dreht sich so, dass die Fahrtrichtung oben ist. Taste "N/↑" wechselt zu Norden oben. Wer die Karte verschiebt, löst das Folgen; die Taste ⌖ schaltet es wieder ein.
  - **Zoom** passt sich dem Tempo an (nah in der Stadt, weiter auf der Landstraße). Mit den Tasten + und – oder zwei Fingern lässt er sich selbst einstellen.
  - **Info-Feld** mit nächster Anweisung, Abstand, Restweg und Restzeit, solange navigiert oder simuliert wird. Die Simulation läuft auch auf der Karte mit.
- **Neu: Seite "Karten".** Kartendateien laden (Bundesländer, Österreich, Schweiz u. a. von download.mapsforge.org, groß und einmalig), Stand auf dem Server prüfen, löschen, und **eigene .map-Dateien hinzufügen** (Kopf wird geprüft).
- Karten-Hinweis "© OpenStreetMap · Mapsforge" in der Ansicht, Lizenzen in NOTICE.md (Mapsforge: LGPL-3.0).
- Ohne geladene Karte führt "Karte öffnen" zur Seite "Karten".
- Intern: Die Führung kann mehrere Zuhörer bedienen (Seite und Karte gleichzeitig).
- Nicht kompiliert getestet. Die Kartenbibliothek ist neu dazu gekommen; Adressen der Kartendateien konnte ich von hier aus nicht prüfen (die App zeigt "gibt es auf dem Server nicht", falls eine nicht stimmt). Kartenansicht und Drehen am Gerät noch nicht getestet.

## v0.2.1 (HUDPilot)
- **Neues App-Symbol:** orange Kurve mit Pfeil auf Schwarz (mit Bluetooth-Zeichen und HUD-Ecken). Als anpassbares Symbol für runde und eckige Startbildschirme, dazu eine einfarbige Fassung für Themen-Symbole (Android 13+). Die Ecken am Rand werden bei runder Form teilweise abgeschnitten, das ist so gewollt.
- Über-Text: Das Symbol wurde mit KI-Hilfe gestaltet (nicht mehr "eigene Zeichnung").
- Sonst keine Änderung gegenüber 0.2.0. Nicht kompiliert getestet.

## v0.2.0 (HUDPilot)
- **Umbenannt:** Die App heißt jetzt überall "HUDPilot" (Anzeige, Benachrichtigung, Über-Text, README). Intern bleiben Paketname und Datei-Namespace gleich. Neues Symbol folgt später.
- **Neu: echte Navigation mit GPS.** Auf der Seite "Gebiete & Routen" gibt es "Navigation starten". Der HUD-Dienst verfolgt die Position auf der Route und schickt Pfeil, Abstand, Rest und Ankunft ans HUD. Läuft auch bei ausgeschaltetem Bildschirm (GPS jede Sekunde, solange navigiert wird). Beim Ziel erscheint das Ziel-Symbol, bei Zwischenzielen das Via-Symbol.
- **Neu: automatische Neuberechnung.** Ist die Position dreimal hintereinander mehr als 40 m neben der Route, wird von der aktuellen Position zu den noch ausstehenden Wegpunkten neu gerechnet (höchstens alle 8 s; schlägt es fehl, bleibt die alte Route und es wird später erneut versucht).
- **Neu: Route ansehen.** Die berechnete Route wird als GPX an eine Karten-App übergeben, zum Prüfen und Vergleichen mit der Route aus Kurviger.
- **Neu: Teilen aus Kurviger/Calimoto** rechnet die Route gleich, wenn alle Routendaten geladen sind.
- **Simulation** nutzt jetzt dieselbe Führungslogik wie die echte Fahrt. "Abweichen" schiebt die virtuelle Position 200 m neben die Route; die Führung muss die Abweichung selbst erkennen und neu rechnen.
- Geprüft: Tests (Führung, Route als GPX, Simulation gegen Führung) und ein Durchlauf mit echten BRouter-Testdaten (Abweichung erkannt, neu gerechnet, Ziel erreicht). Nicht kompiliert; Oberfläche, Dienst und GPS am Gerät noch nicht getestet.

## v0.1.0 (HUDPilot)
- **Neu: Seite "Gebiete & Routen"** (Reiter Werkzeuge, Karte "NAVIGATION"): eigene Navigation mit BRouter (MIT-Lizenz), alles offline auf dem Handy.
  - **Routendaten laden:** zehn Gebiete (Kacheln 5 x 5 Grad, z. B. Bayern Süd / Österreich West), Auswahl per Haken, Fortschritt, Stand und Größe; "Updates prüfen" fragt den Server. Daten kommen von brouter.de.
  - **GPX öffnen** (auch über Teilen/Öffnen mit aus Kurviger): Start, Ziel, Zwischenziele und Formpunkte aus den Routenpunkten; bei reinen Tracks alle 4 km ein Formpunkt.
  - **Berechnen** mit drei Profilen (Landstraße, Kurvig, Schnell, jeweils Motorrad, ohne Autobahn). Erster Entwurf: ein Profil kennt die Kurvigkeit einer Straße nicht.
  - **Anweisungen** aus der Karte (Abbiegen, halten, Kreisverkehr mit Ausfahrt, Wenden, Zwischenziel, Ziel), ohne Straßennamen (BRouter kennt keine).
  - **Simulation:** Fahrt auf der berechneten Route abspielen (50, 100 km/h, x10, x50), mit Ausgabe aufs HUD (Pfeil, Abstand, Rest, Ankunft, Ziel-/Via-Symbol). "Abweichen" setzt die Position 200 m neben die Route und rechnet neu.
  - Solange HUDPilot sendet, ignoriert der HUD-Dienst die OSMAnd-Navigation.
- Alle bisherigen Funktionen der Bridge bleiben unverändert.
- Nicht kompiliert getestet. Rechenteil (BRouter, Anweisungen, Simulation, Neuplanung) mit echten Testdaten (BRouter-Beispielkarte Dreieich) und Tests geprüft; Oberfläche und Download am Gerät noch nicht.

---

# Changelog der OSMAnd HUD Bridge (bis v0.11.101)

(Deutsch. Die Versionsnummern bis 0.9.x gehören zur Entwicklung vor dem Paketwechsel.)

## v0.11.101
- **Meldungen:** Der Block "MOKET" steht jetzt am Ende der Karte, nach der Handy-Akku-Warnung (die gehörte durch die Unterüberschrift optisch fälschlich zu Moket). Sonst keine Änderung.
- Nicht kompiliert getestet.

## v0.11.100
- **Meldungen:** Die fünf Moket-Schalter stehen unter einer eigenen Unterüberschrift "MOKET" und haben kürzere Namen (Hilfe-Anfrage, Hilfe-Antwort/-Nachricht, Begegnung, Privat-Chat, Gruppen-/Ausfahrtchat), damit keine Zeile mehr über den Rand läuft. Im i-Fenster der Hilfe-Anfrage entfällt der Hinweis auf die deutschen Texte.
- Nicht kompiliert getestet.

## v0.11.99
- **Moket-Hilfe-Anfrage:** Bei Arten wie "Reifenschaden" oder "Motorschaden" lässt die App die Endung weg und zeigt "HILFE Reifen 12km" bzw. "HILFE Motor 7km" (statt eine lange Art hinten abzuschneiden). Endungen: -schaden, -panne, -defekt, -problem, -mangel; "Panne" bleibt "Panne".
- Nicht kompiliert getestet.

## v0.11.98
- **Moket-Meldungen am HUD** (nur deutsche Texte, nur Explorer und City): fünf neue Schalter in "Meldungen", jeder mit i-Fenster:
  - **Hilfe-Anfrage** (an): "HILFE Panne 5km" (Art und Entfernung), etwa 10 s, mit Warnton, Vorrang vor allem außer einem Anruf.
  - **Hilfe-Antwort und -Nachricht** (an): "Name hilft" bzw. "Hilfe: Name", 5 s.
  - **Begegnung** (aus): "Biker gekreuzt", 5 s.
  - **Privat-Chat** (aus): Name des Absenders, 5 s.
  - **Gruppen- und Ausfahrtchat** (aus): Name des Chats ohne Emojis und Klammern, 5 s.
  - Mehrere Chat-Nachrichten desselben Chats innerhalb von 30 s werden zusammengefasst ("Chat (3)"). Alles ohne Ton außer der Hilfe-Anfrage. Vorrang: Hilfe-Anfrage > Anruf > Hilfe-Nachricht > Chats/WhatsApp > Begegnung.
  - Nicht angezeigt: neue Ausfahrt, "Neue Begegnungen" nach der Fahrt, "Anfrage angenommen", Standort/Abschluss einer Hilfe, die Fahrtanzeige von Moket.
- **Log:** Die Fahrtanzeige und die Sammelzeilen von Moket werden nicht mehr protokolliert; erkannte Moket-Meldungen stehen als eine kurze Zeile im Log, unbekannte weiter ausführlich.
- Neue Datei `nav/MoketParser.kt` mit 7 Tests.
- Nicht kompiliert getestet, und nicht mit echten Moket-Meldungen am HUD geprüft.

## v0.11.97
- **Genauere Zuordnung der Straße (Tempolimit):** Die App vergleicht nicht mehr nur die letzte Position mit der nächsten Straße, sondern den Weg der letzten Sekunden (bis 15 s, 12 Punkte, neuere zählen mehr). Dazu kommen: die aktuelle Fahrtrichtung, ein Bonus für die Straße, auf der man schon fährt (gleiche Straße, gleicher Name oder direkter Anschluss), und eine Prüfung auf gleichwertige Straßen mit anderem Limit. Ein einzelner GPS-Ausreißer oder eine Parallelstraße 10 m daneben bringt die Zuordnung nicht mehr durcheinander, nach dem Abbiegen wechselt sie nach etwa zwei Sekunden. Nach einer Lücke (Tunnel, GPS weg) fängt sie neu an.
- Neue Datei `limit/PathMatcher.kt` mit 7 Tests. Die frühere Einzelpunkt-Zuordnung bleibt im Code (Tests), wird aber nicht mehr genutzt.
- Nicht kompiliert getestet, und nicht im Fahrbetrieb geprüft.

## v0.11.96
- **Moket-Protokoll (nur zum Erkunden):** Für Benachrichtigungen der App Moket (`app.moket`) schreibt die Bridge Titel, Text, Kanal, Kategorie, Flags und alle Zusatzdaten ins Protokoll (Werkzeuge → Log). Es wird noch nichts am HUD angezeigt. Damit lässt sich nach einer Testfahrt festlegen, woran Hilfe-Anfrage, Begegnung, Privat- und Gruppenchat zu erkennen sind. Hinweis: Namen und Nachrichtentexte aus Moket stehen dann im Log; vor dem Teilen des Logs prüfen.
- Nicht kompiliert getestet.

## v0.11.95
- **Fehler behoben:** In der englischen Anzeige standen noch deutsche Reste ("Sparmodus (ohne HUD)" bei GPS, "aus Import" und andere Herkunftsangaben der Kartenkacheln im Log). Die Statuszeilen werden jetzt vor dem Kürzen übersetzt, und die Log-Zeilen zu Kacheln, Blitzern, Hinweisen, Kurven und Abfragefehlern sind zweisprachig.
- Nicht kompiliert getestet.

## v0.11.94
- **Englische Übersetzung, Etappe 3 (fertig):** Die Tabs Übersicht, Anzeige, Tracking und Info, die Fahrt-Seiten (Details, Karte), der Element-Test, "Felder wählen", Bluetooth-Dialoge, Texte zur Aufzeichnung und die Info-/Rechtstexte gibt es jetzt auch auf Englisch (Deutsch bleibt der Originaltext). Auch das Log und die Statuszeilen werden angezeigt in der gewählten Sprache; intern bleiben die deutschen Wörter, damit die Statusauswertung weiter funktioniert.
- **Fehler behoben:** Nach dem Umschalten der Sprache bleibt die App auf der gleichen Seite, und die untere Leiste zeigt den passenden Reiter.
- **Fehler behoben:** "Fertig: … Kacheln" und "Eingelesen am … aus …" (Straßendaten) werden in der gewählten Sprache angezeigt, auch für schon gespeicherte Importe.
- Nicht kompiliert getestet.

## v0.11.93
- **Fehler behoben:** 0.11.92 ließ sich nicht bauen ("Unresolved reference 'github'" in BridgeBus.kt). Die Hilfsklasse für die Sprache wird dort jetzt normal importiert. Sonst keine Änderung.

## v0.11.92
- **Alle i-Knöpfe** öffnen die Erklärung jetzt in einem Fenster (wie "Stay awake"), mit dem Namen der Zeile als Titel. Vorher klappte der Text unter der Zeile auf.
- **Englische Übersetzung, Etappe 2:** Texte, die das Programm zur Laufzeit setzt, sind jetzt zweisprachig: Live-Kachel (Tempo, Straße, Quelle des Limits), "Überschreitung", "Empfindlichkeit", "Warnabstand", Zahlen und Texte in "Gespeicherte Daten", Blitzer-Import, Region laden (Auswahl, Rückfragen, Fortschritt, Benachrichtigung), Import der Kartendatei (Schritte, Fehlermeldungen, Benachrichtigung), Saison (Monate, Datum), Dialoge, Hinweise und die Benachrichtigung des Dienstes. Neue kleine Hilfsklasse `Tx` dafür.
- **Noch deutsch:** die Zeilen im Log (die App wertet einzelne Wörter daraus für Statusanzeigen aus, das wird getrennt umgebaut) sowie die Tabs Übersicht, Anzeige, Tracking und Info.
- Nicht kompiliert getestet.

## v0.11.91
- **Englische Übersetzung, Etappe 1:** Die festen Texte der Tabs **Tempolimit** und **Werkzeuge** (Überschriften, Schalter, Erklärungen hinter dem i, Knöpfe) gibt es jetzt auf Englisch. Umschalten unter Werkzeuge → "Sprache / Language". Deutsch bleibt die Hauptsprache und die Rückfall-Sprache; die deutschen Texte sind unverändert, sie stehen jetzt in der Textdatei statt in der Bildschirm-Datei.
- Noch deutsch: die übrigen Tabs, Meldungen und Rückfragen aus dem Programm (zum Beispiel Statuszeilen, Dialoge, Benachrichtigung). Das folgt in den nächsten Etappen.
- Oberfläche nicht kompiliert getestet.

## v0.11.90
- **Neu: Sprachauswahl** (Tab Werkzeuge, Karte "Sprache / Language"): Automatisch (Handy-Sprache), Deutsch oder English. Die Wahl gilt sofort für die ganze App, auch für die Benachrichtigung des Dienstes (nach dem nächsten Start des Dienstes). Das ist die Grundlage für die englische Übersetzung; bisher ist nur diese Karte übersetzt, die übrigen Texte folgen in Etappen.
- Technisch: neue Klasse für die Sprache, die App-Klasse setzt sie beim Start; Dienste bekommen einen Kontext mit der gewählten Sprache.
- Oberfläche nicht kompiliert getestet.

## v0.11.89
- **Fehler behoben:** Bei "Speicherplatz" stand dauerhaft "Belegt: –". Das Ergebnis der Zählung wird jetzt sofort angezeigt, bis dahin steht dort "wird berechnet …".
- Hinweis: nicht kompiliert getestet.

## v0.11.88
- **Tab Tempolimit:** Die Karte "Gespeicherte Daten" hat jetzt eine eigene Überschrift **SPEICHER** und gehört damit nicht mehr optisch zu Blitzer.
- Hinweis: Oberfläche nicht kompiliert getestet.

## v0.11.87
- **Tab Tempolimit:** Reihenfolge angepasst: Straßendaten importieren, dann **Blitzer** (zugeklappt, zum Aufklappen), ganz am Ende **Gespeicherte Straßendaten**.
- Hinweis: Oberfläche nicht kompiliert getestet.

## v0.11.86
- **Tab Tempolimit:** Die Karte **Straßendaten** steht jetzt über **Blitzer**. Die Blitzer-Karte ist beim Start **zugeklappt** und zeigt nur die Überschrift mit einem Pfeil. Ein Tipp auf die Überschrift klappt sie auf oder zu, die App merkt sich den Zustand. Die Funktion ist unverändert: Ist die Blitzer-Warnung an, warnt die App auch bei zugeklappter Karte.
- Hinweis: Oberfläche nicht kompiliert getestet.

## v0.11.85
- **Neu: Schalter "Mobile Daten"** in der Karte "Straßendaten" (immer sichtbar, standardmäßig aus, bleibt gespeichert). Er lässt sich jederzeit umschalten, auch mitten im Download.
- **An:** Der Download läuft ohne Rückfrage auch über Mobilfunk weiter, wenn das WLAN weg ist, und verbindet sich nach Unterbrechungen selbst neu (pausiert erst nach etwa 5 Minuten ohne Fortschritt).
- **Aus:** Der Download pausiert, sobald du das WLAN verlässt, und fragt vor dem Fortsetzen im Mobilfunk. Schaltest du ihn an, während er deswegen pausiert, geht es automatisch weiter.
- **WLAN hat immer Vorrang:** Kommt das WLAN zurück, wechselt der Download dorthin und macht an derselben Stelle weiter.
- Der Schalter wird nie automatisch zurückgesetzt.
- Hinweis: Oberfläche und Dienst sind nicht kompiliert getestet.

## v0.11.84
- **Neu: "Region laden"** (Tab Tempolimit, Karte "Straßendaten importieren"). Statt die Kartendatei am PC zu holen, wählst du in der App eine Region (Europa → Land → Bundesland → Regierungsbezirk) aus dem Verzeichnis von Geofabrik. Die App fragt die Größe ab und lädt nur nach deiner Bestätigung. Danach liest sie die Datei mit dem bekannten Import ein und **löscht die Datei anschließend**.
- **Pausieren / Fortsetzen / Verwerfen:** Der Download kann pausiert und später (auch nach einem Neustart der App) an derselben Stelle fortgesetzt werden. "Verwerfen" löscht die halbe Datei. Auch in der Benachrichtigung gibt es "Pausieren".
- **Mobilfunk:** Kein neuer Schalter. Im WLAN startet der Download direkt, im Mobilfunk fragt die App vorher mit der Größe nach ("Trotzdem laden"). Wechselt die Verbindung mitten im Download vom WLAN auf Mobilfunk, pausiert er von selbst. Ist **Offline-Daten** an, ist "Region laden" gesperrt.
- **Aktualisieren:** Die zuletzt geladene Region wird mit Datum angezeigt und lässt sich mit "Aktualisieren" neu laden.
- Vor dem Laden prüft die App den freien Speicher (etwa 2,5-fache Dateigröße plus 50 MB). Bei großen Regionen (mit Unterregionen, zum Beispiel ganz Bayern) kommt eine Warnung wegen des Arbeitsspeichers beim Einlesen; besser einzelne Regierungsbezirke laden.
- Lädt nie von selbst, nur auf deinen Tipp. Das Verzeichnis der Regionen wird bis zu 7 Tage zwischengespeichert.
- Die Größen stehen in der Rückfrage, nicht in der Liste (das Verzeichnis enthält keine Größen).
- Test der Verzeichnis-Auswertung mit selbst geschriebenem Beispiel im Format von Geofabrik; gegen das echte Verzeichnis und den Download selbst noch nicht geprüft. Oberfläche und Dienst sind nicht kompiliert getestet.

## v0.11.83
- **Fehler behoben:** Nach dem Ausschalten der App ("App aktiv" aus oder "Beenden") blieb manchmal eine Meldung in der Statusleiste zurück ("Ohne HUD: wartet auf die Verbindung …"). Beim Beenden stoppte die HUD-Verbindung noch einmal und zeigte dabei die Meldung neu an, obwohl der Dienst schon aus war. Jetzt wird beim Beenden keine Meldung mehr gezeigt und eine eventuell vorhandene entfernt.
- Der Meldungstext ohne HUD ist verständlicher: "Läuft ohne HUD: Tempolimit und Straße, GPS im Sparmodus" (im Auto-Modus: "Auto-Modus: Tempolimit, Straße und Töne ohne HUD"). Er sagt nicht mehr "wartet auf die Verbindung".
- Dienst und Oberfläche sind nicht kompiliert getestet.

## v0.11.82
- **Fehler behoben:** Die Live-Werte in der App (Tempo, Tempolimit, Straßenname, Schild in "Aktuell", Karte "Live" auf der Übersicht, Protokoll) wurden nur aufgefrischt, wenn zufällig eine andere Meldung kam, und hinkten deshalb stark nach. Jetzt werden sie jede Sekunde aktualisiert, solange die App offen ist. Das HUD war nicht betroffen, es bekam das Tempo schon immer direkt.
- Ohne HUD und ohne Auto-Modus fragt das GPS weiterhin nur alle 30 Sekunden (Sparmodus), die Anzeige zeigt dann natürlich entsprechend seltener neue Werte.
- Oberfläche nicht kompiliert getestet.

## v0.11.81
- Der Schalter in der Karte "Aktuell" heißt jetzt **"Stay awake"** (vorher "Display anlassen"). Sonst keine Änderung.

## v0.11.80
- **Neu: Schalter "Stay awake" (zuerst "Display anlassen")** in der Karte "Aktuell" (Tab Tempolimit), Standard aus, mit i-Knopf. Der Bildschirm bleibt an, solange die App vorne offen ist (zum Beispiel bei Testfahrten im Auto). Öffnest du eine andere App, gilt wieder die normale Zeit; die Warnungen laufen im Hintergrund weiter. Kostet viel Akku, am besten mit Ladekabel.
- Oberfläche nicht kompiliert getestet.

## v0.11.79
- **Neu: Auto-Modus** (Tab Werkzeuge, Karte "Auto-Modus (Testfahrt)"), Standard aus. Zum Ausprobieren im Auto ohne HUD: Das GPS wird jede Sekunde abgefragt (ohne HUD sonst nur alle 30 s, das war für die Töne zu selten) und auch der Ton bei Tempo-Überschreitung geht ohne verbundenes HUD. Blitzer, Hinweistöne und Kurven laufen damit wie am Motorrad. Verbraucht mehr Akku. Der Dienst beendet sich im Auto-Modus nicht, wenn kein HUD gefunden wird.
- **Neu:** Im Tab Tempolimit unter "Aktuell" steht jetzt der erkannte Straßenname ("Straße: …"), damit man Schild und Straße zusammen prüfen kann.
- Hinweis zu 0.11.77: Ohne HUD und ohne Auto-Modus läuft das GPS weiter im Sparmodus (alle 30 s), die Töne kommen dann oft zu spät oder gar nicht.
- Oberfläche und Dienst sind nicht kompiliert getestet.

## v0.11.78
- Unter dem Schieber bei "Blitzer warnen" steht jetzt statt des allgemeinen Beispiels der echte ungefähre Abstand in Metern bei 50, 100 und 130 km/h. Der Text ändert sich mit dem Schieber (Kurz / Normal / Lang). Die Zahlen kommen aus derselben Rechnung wie die Warnung selbst, auf 10 m gerundet.
- Oberfläche nicht kompiliert getestet.

## v0.11.77
- **Fehler behoben:** Mit ausgeschaltetem "HUD verbinden" beendete sich der Dienst sofort, deshalb gab es kein Tempolimit, keinen Straßennamen und keine Töne ("Live" zeigte überall "–"). Das war eine Nebenwirkung aus 0.11.72: Damals ließ das Vorladen den Dienst ungewollt weiterlaufen, und mit dem Entfernen des Vorladens fiel das weg.
- **Jetzt:** Mit "App aktiv" an und "HUD verbinden" aus läuft der Dienst weiter, nur ohne Bluetooth: GPS, Tempolimit, Straßenname, Live-Anzeige und die Töne (Blitzer, Bahnübergang, Zebrastreifen, Verkehrsberuhigung, Kurven). Der Ton bei Überschreitung bleibt an ein verbundenes HUD gebunden. "App aktiv" aus lässt die App weiter komplett ruhen. Ist ein HUD gewünscht, wird aber 10 Minuten lang nicht gefunden, beendet sich der Dienst wie bisher.
- Fehlt die Bluetooth-Berechtigung, startet der Dienst trotzdem (nur mit Standort), statt abzubrechen.
- Die Beschreibungen bei "HUD verbinden" und "App aktiv" sind angepasst.
- Oberfläche und Dienst sind nicht kompiliert getestet.

## v0.11.76
- **Neu: Warnung vor sehr scharfen Kurven** (Karte "Hinweise mit Ton", Schalter "Scharfe Kurven", Standard aus). Nur Ton (derselbe einzelne Ton wie bei Bahnübergang usw.), nichts am HUD.
- **So funktioniert es:** Aus der Straßenform in den Kartendaten wird die engste Kurve vor dir gesucht (aktuelle Straße, bei gleichem Namen auch die anschließenden Stücke, bis etwa 450 m). Aus dem Radius folgt ein Kurventempo (Wurzel aus Querbeschleunigung mal Radius). Bist du mindestens 10 km/h schneller und kannst gerade noch bequem bremsen (2 s Reaktion, 2,5 m/s²), kommt einmal der Ton.
- **Regler "Empfindlichkeit":** Wenig (Radius bis 30 m, 5,0 m/s²), Normal (bis 45 m, 4,0 m/s², Standard), Viel (bis 60 m, 3,0 m/s²). Gemessen an zwei aufgezeichneten Fahrten (452 km, Oberpfalz) gab das etwa 3, 5 und 7 Töne je 100 km. Die engsten Kehren sind in allen Stufen dabei.
- **Nicht gewarnt wird:** innerorts (Ortsschild-Zone, Limit bis 50, beleuchtete Straße), in Wohn-, Zufahrts- und Fußwegen, unter etwa 40 km/h.
- Geht auch mit online geladenen Kacheln (die Straßenform ist dort gleich). Wo OSM grob gezeichnet ist, kann eine Kurve schärfer wirken, als sie ist.
- Oberfläche und Dienst sind nicht kompiliert getestet.

## v0.11.75
- **Neu: Karte "Hinweise mit Ton"** (Tab Tempolimit, unter der Tempowarnung): drei Schalter, alle standardmäßig aus: **Bahnübergänge**, **Zebrastreifen**, **Verkehrsberuhigung**. Es kommt ein einzelner Ton (höher als das dreifache "Dü", 780 Hz, 0,34 s), nichts am HUD. Das dreifache "Dü" bleibt für Tempoüberschreitung und Blitzer.
- **Wann der Ton kommt:** einmal pro Punkt, nur für Punkte vor dir auf deiner Linie, nur ab etwa 20 km/h. Der Abstand hängt vom Tempo ab: Zeit zum Reagieren (2 s) plus bequemes Bremsen (2,5 m/s²), also etwa 70 m bei 50 km/h und 210 m bei 100 km/h (mindestens 40 m, höchstens 250 m). Bei Bahnübergang und Verkehrsberuhigung kommt er etwas später (60 %), weil man dort nicht anhalten muss. Liegen mehrere Punkte dicht beieinander (Zebrastreifen mit Insel), gibt es nur einen Ton.
- **Nur mit importierten Straßendaten:** Online geladene Kacheln kennen diese Punkte nicht. Beim Einschalten ohne Import gibt es einen Hinweis. Kacheln aus dem Import von 0.11.71 oder neuer enthalten die Punkte schon, ein neuer Import ist nicht nötig.
- Kommt später: Warnung vor scharfen Kurven (mit Regler wenig / normal / viel).
- Oberfläche und Dienst sind nicht kompiliert getestet.

## v0.11.74
- **Tab Tempolimit neu geordnet** (nach Themen): Aktuell (Schild oben, Beschreibung darunter), Warnung (Tempowarnung), Tempolimit (Limit-Erkennung), Blitzer (alles in einer Karte: Warnung, Warnabstand, Ton, Ampelblitzer/Abschnitt/Tunnel, eigene Blitzer-Liste mit Import), Straßendaten (Import, Offline-Daten, Gespeicherte Daten).
- **Neu: eigener Schalter "Ton bei Blitzern"**, unabhängig von der Tempowarnung. Der bisherige Schalter heißt "Ton bei Überschreitung" und gilt nur noch dafür. Beide starten mit deiner bisherigen Einstellung.
- **Karte "Gespeicherte Daten"** enthält jetzt auch den Datenspeicher (Regler für das Speicherlimit, belegter Platz).
- **Entfernt:** der Schalter "Tempolimit aus Kartendaten" (Hauptschalter). Die Karten "Datenquelle" und "Fehlende Limits" sind jetzt die Karte "Limit-Erkennung". Wer nichts aus dem Internet laden will, nutzt "Offline-Daten".
- Oberfläche und Dienst sind nicht kompiliert getestet.

## v0.11.73
- **Tab Tempolimit neu geordnet:** Die Karte "Tempowarnung" enthält jetzt nur die Überschreitung (Schalter, Schwelle in km/h direkt darunter, Akustische Warnung). Die Blitzer-Warnung hat eine eigene Karte mit Warnabstand.
- **Reihenfolge der Karten:** Tempowarnung, Blitzer-Warnung, Straßendaten importieren, Datenspeicher, Blitzer-Liste, Gespeicherte Daten, danach Datenquelle (mit "Fehlende Limits" in einer Karte zusammengelegt).
- **"Gespeicherte Daten"** zeigt die Statistik jetzt immer an (ohne i-Knopf): Zahl der Kacheln (importiert und online geladen), belegter Platz und die Summe aus allen Importen (Straßen, Tempolimits, Zonen, Ortsschilder, Bahnübergänge, Zebrastreifen, Verkehrsberuhigung …) sowie die Blitzer-Zählung. Bei mehreren Dateien können Überschneidungen an den Rändern doppelt zählen. Der i-Knopf bei "Straßendaten importieren" zeigt weiter die Zahlen je Datei.
- **Hinweis zum Aktualisieren:** Sind die importierten Daten älter als etwa 6 Monate, steht bei "Straßendaten importieren" eine orange Zeile "Daten vom … : ein neuer Import wäre gut." Nur ein Hinweis, keine Benachrichtigung.
- Der Schalter heißt jetzt "Tempolimit aus Kartendaten" (statt "aus OSM-Daten"), der Erklärungstext passt zu Import und Online-Laden.
- Oberfläche und Dienst sind nicht kompiliert getestet.

## v0.11.72
- **Neu: i-Knopf bei "Straßendaten importieren".** Er zeigt eine Zusammenfassung je eingelesener Datei: Zahl der Straßen (davon mit Tempolimit, mit Tempo-Zone, beleuchtet, Tunnel, Brücken), Ortsschilder, Temposchilder, Blitzer, Bahnübergänge, Fußgängerüberwege (davon Zebrastreifen) und Verkehrsberuhigung. Gemerkt werden die letzten 8 Dateien; "Importierte Daten entfernen" löscht auch die Zusammenfassung.
- **Neu: Fortschrittsbalken beim Blitzer-Import** (GPX-Dateien): "Datei n von N", danach "Zusammenführen …". Der Knopf ist währenddessen gesperrt.
- **Neue Texte:** Karte "Straßendaten importieren", Knopf "Datei wählen", kürzere Beschreibung (.osm.pbf, z. B. von Geofabrik, mehrere Regionen nacheinander möglich). Neue Karte "Gespeicherte Straßendaten" mit der Blitzer-Zählung.
- **Entfernt (der Import ersetzt es):** "Straßendaten laden ab x km" (Vorladen), "Tour laden" (GPX-Route), Laden im Hintergrund, Schalter für mobile Daten und der Saison-Vorlauf (das Vorladen vor Saisonbeginn entfällt; die Saison selbst bleibt). Die Abhängigkeit androidx.work wurde entfernt.
- **Bleibt:** Laden unterwegs (aktuelle Kachel und die nächste in Fahrtrichtung, ca. 3 km voraus) und die Erneuerung online geladener Kacheln nach 30 Tagen. **Importierte Kacheln werden nie online erneuert**, sie gelten immer als gültig.
- Am Gerät noch nicht erprobt (Oberfläche und Dienst sind nicht kompiliert getestet).

## v0.11.71
- **Neu: Straßenkarten importieren** (Tab Tempolimit, neue Karte "Straßenkarten importieren"). Du lädst am Handy eine Karte als Datei (Endung .osm.pbf, zum Beispiel von Geofabrik, auch einzelne Regierungsbezirke) und wählst sie mit "Kartendatei wählen". Die App rechnet sie selbst in Kacheln um, ohne PC und ohne Internet. Fortschritt als Balken mit Prozent, Schritt (1 Straßen suchen, 2 Punkte lesen, 3 Kacheln schreiben), Zahl der fertigen Kacheln und grober Restzeit, auch in der Benachrichtigung (mit "Abbrechen"). Die Umrechnung läuft als Dienst und arbeitet in Streifen, damit wenig Arbeitsspeicher reicht (mit der Oberpfalz-Datei, 85 MB, in 15 Sekunden am Rechner, 2.186 Kacheln, 14 MB; das Handy ist am Gerät noch nicht gemessen, auch ganz Bayern ist noch nicht erprobt).
- Die Kacheln haben das gleiche Format wie die online geladenen. Gespeichert werden befahrbare Straßen mit Tempo-Angaben (maxspeed, Richtungen, Zonen), Ortsschilder, Blitzer sowie für spätere Warnungen Bahnübergänge, Zebrastreifen, Verkehrsberuhigung und Temposchilder (diese werden noch nicht benutzt). Mehrere Regionen nacheinander werden an der Kante zusammengeführt (gleiche Straßen-Nummer: das Neue gewinnt). "Importierte Daten entfernen" löscht nur importierte Kacheln, online geladene bleiben.
- **Neuer Schalter "Offline-Daten"** (Standard aus). An: die App lädt nichts mehr aus dem Internet (weder unterwegs noch im Voraus noch im Hintergrund) und nutzt nur gespeicherte und importierte Kacheln, auch alte. Fehlende Gebiete gelten als leer. Aus: wie bisher.
- Die App darf jetzt mehr Arbeitsspeicher nutzen (largeHeap), neuer Dienst-Typ "Datenübertragung" für den Import. Kartendaten © OpenStreetMap-Mitwirkende (ODbL).

## v0.11.70
- **Neu: eigene Blitzer-Liste** (Tab Tempolimit, Karte "Blitzer-Liste"). Mit "GPX-Dateien wählen" importierst du eine eigene Liste, zum Beispiel von SCDB, mehrere Dateien auf einmal. Bei SCDB steht Art und Limit im Namen der Datei beziehungsweise der Kategorie (Tempo_50, Ampel_30, Abschnitt_80, Tunnel, Tempo_variabel, Kamera). Gleiche Nummern werden zusammengelegt (die Datei mit Art und Limit gewinnt gegen die allgemeine "Kamera"-Datei), Punkte unter 30 m Abstand ebenfalls. Mit den echten SCDB-Dateien ergibt das rund 6.700 Blitzer. Die Liste liegt nur im App-Speicher auf dem Handy (nicht im Projekt) und wird bei einer neuen Auswahl ersetzt; "Liste entfernen" löscht sie.
- **Warnung mit der Liste:** Die Blitzer-Warnung nutzt die Liste zusätzlich zu den OSM-Blitzern (OSM-Blitzer näher als 30 m an einem eigenen werden weggelassen). Schalter für Ampelblitzer, Abschnittskontrolle und Tunnel (Standard an); Tempoblitzer warnen immer. Am HUD zeigt der Gefahren-Bildschirm das Tempolimit des Blitzers, wenn die Liste eines kennt.
- Ohne Richtungsangabe in den Daten gilt wie bisher: nur Blitzer vor dir auf deiner Linie. Auf Autobahnen kann deshalb ab und zu auch ein Blitzer der Gegenseite warnen.

## v0.11.69
- **Blitzer-Zählung über alle gespeicherten Kacheln:** Unter "Straßendaten laden" steht jetzt zusätzlich eine zweite Zeile "Alle gespeicherten Kacheln: x Blitzer in y von z Kacheln mit Blitzerdaten". Sie zählt im Hintergrund (höchstens einmal pro Minute) alle gespeicherten Kacheln, auch wenn der Dienst nicht läuft, und rechnet einen Blitzer an der Kachelgrenze nur einmal. Ältere Kacheln aus der Zeit vor 0.11.66 haben noch keine Blitzerdaten und werden getrennt genannt. So siehst du, wie dicht die festen Blitzer in OpenStreetMap bei dir eingetragen sind.

## v0.11.68
- **Blitzer-Warnung: HUD schaltet kurz um.** Kommt die Warnung vor einem Blitzer, wechselt das HUD für höchstens 4 Sekunden auf den Gefahren-Bildschirm (Tempolimit, Blitzer-Symbol, Entfernung) und geht dann von selbst zurück auf die gewählte Anzeige. Ist man vorher am Blitzer vorbei, geht es früher zurück. Nicht umgeschaltet wird, wenn die nächste Abbiegung näher als 300 m ist (Kreuzung: der Pfeil bleibt sichtbar, es gibt nur den Ton), im Element-Test und in der Justage. Am Gerät noch nicht erprobt: wie schnell das HUD umschaltet.

## v0.11.67
- **Neu: Blitzer-Warnung** (Tab Tempolimit, Karte "Tempowarnung"). Schalter, standardmäßig aus; beim Einschalten kommt erst ein Hinweis (in Deutschland und einigen anderen Ländern für Fahrer nicht erlaubt), dann "Trotzdem aktivieren" oder "Abbrechen". Der Regler "Warnabstand" hat Kurz, Normal und Lang (ca. 6, 10 und 15 Sekunden vor dem Blitzer, mindestens 100 m, höchstens 600 m). Je schneller du fährst, desto früher kommt die Warnung (Normal: bei 100 km/h ca. 280 m, bei 50 km/h ca. 140 m). Gewarnt wird nur vor festen Blitzern vor dir auf deiner Straße (kein Blitzer auf der Gegenseite oder an Parallelstraßen), pro Blitzer einmal. Die Daten kommen aus OpenStreetMap, mobile Blitzer und Abschnittskontrollen fehlen. Braucht "Tempolimit aus OSM-Daten".
- **HUD:** Das HUD bekommt während der Warnung den Kameratyp im Tempolimit-Feld und die Entfernung (Feld 25, am Gerät nur auf dem Gefahr-Bildschirm 22 bestätigt). Welche Anzeige das im Alltag zeigt, ist noch zu testen.
- **Ton:** Neuer Warnton "dü-dü-dü" (drei weiche Töne, selbst erzeugt). Er gilt für die Tempo-Überschreitung und für Blitzer. Der Schalter "Akustische Warnung" ist jetzt auch bei ausgeschalteter Tempowarnung bedienbar, wenn die Blitzer-Warnung an ist.

## v0.11.66
- **Blitzer-Test (erster Schritt):** Die Kartendaten von OpenStreetMap enthalten jetzt zusätzlich die festen Blitzer ("highway=speed_camera"). Im Tab Werkzeuge, Karte "Straßendaten laden", steht eine Zeile "Blitzer (nur feste, aus OpenStreetMap)" mit der Zahl in den gerade geladenen Kacheln. So lässt sich prüfen, wie vollständig die Daten für deine Gegend sind. Es gibt noch keine Warnung am HUD. Bereits geladene Kacheln der Vorversion haben keine Blitzerdaten und werden beim nächsten Laden (im Hintergrund oder unterwegs) neu geholt, auch beim Vorladen. Solange kein Netz da ist, werden sie weiter für Straßen und Limits benutzt, die Blitzer sind dann unbekannt. Der alte Speicher wird beim Aufräumen nach Alter mit entfernt.

## v0.11.65
- **Element-Test:** Neu mit Testwert: Entfernung Blitzer (101), Entfernung Zwischenziel (81) sowie Höhenmeter aufwärts (83) und abwärts (84). Die Feldnummern stammen aus dem SDK und sind noch nicht am Gerät bestätigt. Bisher hatte 101 keinen Wert und blieb deshalb leer.

## v0.11.64
- **Vollbild-Karte** in "Meine Fahrten": In einer Fahrt öffnet der Knopf oben rechts an der Karte die Karte im Vollbild. Verschieben mit einem Finger, zoomen mit zwei Fingern, Doppeltipp oder Plus und Minus, "⌖" zeigt wieder die ganze Route. Ein Tipp auf die Route zeigt Tempo und Höhe an der Stelle. Die Markierung aus den Diagrammen wird mitgenommen.
- **Element-Test:** Nicht gesehene Codes bekommen ein rotes ✗ (gesehene ein grünes ✓). Neu in der Bildschirm-Auswahl: **Gefahr (22)**, der Gefahren-Bildschirm des HUD, nur zum Ausprobieren (was er zeichnet, ist unbekannt).

## v0.11.63
- **Neu: Saison-Übersicht** oben in "Meine Fahrten": Zahl der Fahrten, Strecke, Fahrzeit, Höchsttempo, "Zu schnell: x Mal in y Fahrten" und die Strecke pro Monat als kleine Balken (der stärkste Monat in Orange). Mit den Pfeilen neben der Überschrift blätterst du durch frühere Saisons. Die Zeitspanne folgt deiner Einstellung unter Werkzeuge → Saison (auch über den Jahreswechsel, dann "2025/26"); ist keine Saison eingestellt, zählt das Kalenderjahr. Alles wird aus den gespeicherten Fahrten gerechnet. Überschreitungen zählen nur bei Fahrten, bei denen Tempolimits aufgezeichnet wurden.

## v0.11.62
- **Neu: Handy-Akku-Warnung** (Tab Anzeige, Karte "Meldungen"). Mit Schalter (Standard aus) und Regler "Warnen ab" von 5 bis 50 % (Standard 20 %). Fällt der Handy-Akku auf oder unter die Grenze, zeigt das HUD sein Akku-Warnsymbol, dauerhaft. Es verschwindet wieder, wenn der Akku 5 % über der Grenze liegt oder das Handy lädt. Das HUD zeichnet das Symbol nur in **Guide, Cruiser und Explorer** (am Gerät mit dem Element-Test festgestellt). Das Senden an das HUD ist noch nicht am Gerät bestätigt.

## v0.11.61
- **Element-Test neu aufgebaut:** Beim Öffnen schaltet das HUD in den Bildschirm, den du im Test wählst (Navigator, Minimalist, Explorer, City, Guide, Cruiser), mit den echten Live-Werten. Ein angetippter Code **blinkt 5 Sekunden**, danach ist der Bildschirm wieder normal. Erst beim Schließen des Tests geht das HUD zurück in den Modus, der unter "Anzeige" gewählt ist (die Einstellung selbst wird nie verändert). Vergisst du das Schließen, stellt die App nach 3 Minuten Ruhe alles selbst zurück.
- **Protokoll** im Test: jede Antwort ("Gesehen" / "Nicht gesehen") mit Zeit, Modus, Code und Name, nur in diesem Menü (Teilen oder Leeren).
- **GPS-Warnsymbol entfernt:** Kein Bildschirm zeichnet es (am Gerät geprüft). Die Funktion und der Code 37 im Test sind weg.

## v0.11.60
- **Neu: Element-Test** (Werkzeuge, Karte "HUD"): einzelne Anzeige-Codes am HUD testen, mit Protokoll. Der Ablauf wurde in 0.11.61 überarbeitet.

## v0.11.59
- **Vorschauen:** Die Zahl im Tacho (Navigator, Explorer, City) saß zu weit links und verschwand teilweise im weißen Bogen. Sie ist jetzt mittig unter dem Bogen.

## v0.11.58
- **Vorschauen der Anzeigemodi neu:** Schwarz-Weiß mit feinem Innenrahmen, dickere Pfeile, Tempo als Tacho-Bogen wie beim Original (Navigator, Explorer, City), Straßenname fett, Telefonhörer beim Anruf.
- **Beschreibungen dauerhaft sichtbar:** Bei Navigationsanweisung, Meldungen, Straßendaten laden, Tour laden, Datenspeicher, Aufzeichnung, Zusatzaufzeichnung, Saison und den beiden HUD-Karten steht der Text direkt unter dem Strich, das i dahinter ist weg. Weitere Details gibt es per i bei der jeweiligen Zeile (Anrufe, WhatsApp, Hinweise zu den Werten).
- **Umbenannt:** "Straßendaten laden", "Tour laden", "Datenspeicher".
- **Datenspeicher:** Regler jetzt 0,25 / 0,5 / 1 / 2 GB (Standard 1 GB). Größere gespeicherte Werte werden auf 2 GB gesetzt. Die Größenangaben im Hilfetext sind realistischer.

## v0.11.57
- **Vorschauen der Anzeigemodi angepasst:** Die Fahrzeit sitzt rechts, die Spurpfeile stehen mittig unter dem Abbiege-Pfeil, und beim Navigator ist die Linie über dem Tempo weg.

## v0.11.56
- **Tempolimit, Datenquelle:** "Zusätzliche Hinweise der Karte nutzen" heißt jetzt "Zusatzhinweise nutzen", damit der Schalter sichtbar bleibt.

## v0.11.55
- **Werkzeuge, Einrichtung:** Der Hinweistext steht jetzt immer da, das i ist weg.
- **Tempolimit:** Die Karte heißt jetzt "Fehlende Limits".
- **i-Knöpfe sitzen wieder mittig** neben den Überschriften "Straßendaten vorladen" und "Navigationsanweisung".

## v0.11.54
- **Vorladen:** Die Zeilen heißen jetzt "Mobile Daten nutzen" und "Hintergrundladen", damit der Schalter nicht mehr abgeschnitten wird.

## v0.11.53
- **Vorschauen der Anzeigemodi näher am Original:** Oben sind jetzt Uhrzeit, Bluetooth und HUD-Akku an den Original-Positionen zu sehen, der Inhalt ist wie auf dem HUD angeordnet (Navigator mit Spurpfeilen, Explorer mit Kompass, City mit Straßenname, Guide und Cruiser mit Statuszeile).
- **Alles weiß:** Symbole, Uhrzeiten, Linien und das Tempolimit-Schild (weiße 50 mit weißem Ring) sind weiß, passend zum HUD.
- **Anruf:** Ein Telefonhörer-Symbol vor dem Namen statt "Anruf:".
- Nur die Vorschau-Bilder in der App ändern sich, am HUD selbst nichts.

## v0.11.52
- **Übersichtlicher gegliedert:** Kleine orange Abschnittsüberschriften mit dünner Linie trennen die Karten: Anzeige (ANZEIGEMODI, NAVIGATION, HUD), Tempolimit (AKTUELL, WARNUNG, DATENQUELLE, STRASSENDATEN) und Werkzeuge (EINRICHTUNG, HUD, DIAGNOSE).
- **Neue Kartenüberschriften auf allen Seiten:** Orangebalken, größere Schrift und eine dünne Linie darunter.
- **Tracking:** Der Kopf heißt jetzt "Aufzeichnung" mit einem Status-Etikett (BEREIT, LÄUFT, PAUSE). Während der Aufnahme hat die Karte einen orangen Rand. Im Ruhezustand zeigt sie "0:00 h · 0,0 km".
- **Status oben auf der Übersicht** kürzer und englisch: Paused, Off-season, No HUD, Connecting …, Connected, Error, mit einer kurzen deutschen Zeile darunter. Die lange Erklärung zu "Paused" steht hinter dem i bei "App aktiv".
- Kleine Textänderungen: "Akku-Optimierung aus", "Geradeaus-Pfeil an", "Zusatzaufzeichnung" statt "Was aufgezeichnet wird", bei Guide und Cruiser kein Hinweis mehr auf das Zahnrad.

## v0.11.51
- **i-Knöpfe** auch auf den Seiten Tempolimit, Werkzeuge und Tracking: die Erklärungstexte unter Schaltern und Überschriften sind zugeklappt und öffnen sich per Tipp. Kurze Hinweise unter Schiebereglern bleiben stehen.
- Die Texte der Karten Guide und Cruiser sind stark gekürzt. Der Hinweis, dass Fahrzeit und Strecke bei null starten, steht jetzt im Fenster "Felder wählen". Cruiser ist nicht mehr auf "ohne Navigation" festgelegt.
- Der Knopf "3 Testfahrten anlegen" im Tracking-Tab ist wieder entfernt. Schon angelegte Testfahrten bleiben und lassen sich löschen.

## v0.11.50
- Auf der Seite Anzeige stehen die Erklärungstexte unter den Karten jetzt hinter kleinen grauen **i-Knöpfen** (wie auf der Übersicht): Navigationsanweisung, Geradeaus-Pfeil dauerhaft, Meldungen, Spotify, Automatische Helligkeit und Justage-Modus. Ein Tipp klappt den Text auf und wieder zu. Die Karten der Anzeigen mit ihren Vorschauen bleiben unverändert.

## v0.11.49
- Die Zeilenwahl bei Guide und Cruiser hat kein **Durchschnittstempo** mehr (und damit auch den Hinweis, dass es nicht zusammen mit dem Tempolimit geht). Eine früher gewählte Zeile "Durchschnitt" wird zurück auf den Standardwert gesetzt.

## v0.11.48
- **Zwei neue Namen und eine neue Anzeige:** Die Anzeige "Freies Fahren" heißt jetzt **Cruiser**. Neu ist **Guide**: Abbiegepfeil mit Entfernung und darunter zwei Zeilen (Standard: Reststrecke und Restzeit). Beide sind noch Tests am HUD.
- **Zeilen frei wählen:** An den Karten von Guide (2 Zeilen) und Cruiser (4 Zeilen) öffnet ein **Zahnrad** das Fenster "Felder wählen". Je Zeile gibt es eine Liste: Leer, Geschwindigkeit, Tempolimit, Durchschnittstempo, Reststrecke, Restzeit, Ankunft, Fahrzeit, Strecke, Höhe, nächste Straße. Die Vorschau auf der Karte zeigt die Wahl. Tempolimit und Durchschnitt teilen sich ein Feld am HUD, es geht nur eines von beiden. Werte ohne Daten (zum Beispiel Restzeit ohne Navigation) bleiben leer.
- Die Uhrzeit wird bei Guide und Cruiser jetzt regelmäßig mitgeschickt.

## v0.11.47
- Der Tab **Tracking** ist jetzt immer anwählbar, auch beim Navigieren. Läuft eine Aufnahme, zeigt die Übersicht eine Zeile "Aufzeichnung läuft – zum Tracking" (Antippen öffnet den Tab).
- **Freies Fahren** lässt sich nur einschalten, solange "App aktiv" an ist.
- Die Anzeige "Tracking" heißt jetzt **Freies Fahren**.

## v0.11.46
- **Neue Fahrt-Seite:** Antippen einer Fahrt in "Meine Fahrten" öffnet eine eigene Seite nach dem Vorbild der Calimoto-Tourübersicht, ohne Veröffentlichen, Fotos, Bewertung und Eigenschaften: Name (mit Stift zum Umbenennen), Datum, Strecke und Zeit, **kleine Karte mit der Route** (Start grün, Ziel rot), Knöpfe **In Karten-App ansehen** (öffnet die GPX in einer Karten-App wie OSMAnd) und **Teilen** (GPX oder CSV), neun **Kacheln** (Durchschnitt, Maximum, Auf- und Abstieg, stärkstes Beschleunigen und Bremsen, max. Schräglage, Überschreitungen, Gesamtzeit) und **Diagramme** für Höhe, Schräglage (L oben, R unten), Beschleunigung und Geschwindigkeit (mit Tempolimit als orange Linie). Antippen oder Wischen über ein Diagramm setzt eine blaue Markierung mit Wert, die in allen Diagrammen und als orange Punkt auf der Karte mitläuft. Löschen steht oben rechts.
- Die Karte zeigt **OpenStreetMap-Kacheln**. Sie werden beim ersten Öffnen aus dem Internet geladen und auf dem Handy gemerkt (höchstens 40 MB). Dabei erfährt der Kartenserver, welches Gebiet du ansiehst. Ohne Netz bleibt die Karte hell und leer, die Route wird trotzdem gezeichnet.
- Die Rohpunkte jeder neuen Fahrt werden zusätzlich in einer eigenen kleinen Datei gespeichert (für die Fahrt-Seite). Fahrten aus früheren Versionen werden dafür nicht mehr unterstützt.
- Neuer Knopf **3 Testfahrten anlegen** in "Meine Fahrten": erfundene Fahrten (Allgäu kurz, Eifel mit Pause, Schwäbische Alb lang) zum Ausprobieren der neuen Seite, ohne echte Ausfahrt. Sie lassen sich wie echte Fahrten löschen.

## v0.11.45
- Die drei Aufzeichnungs-Schalter (Höhe, Tempolimit, Schräglage) sind jetzt **standardmäßig aus**. Sind sie an und läuft eine Aufnahme, erscheinen die **aktuellen Werte live** klein unter dem jeweiligen Schalter: Höhe mit Auf- und Abstieg, Limit mit Anzahl der Überschreitungen, Schräglage jetzt und maximal.

## v0.11.44
- **Tracking-Tab, Teil 2:** Drei Schalter "Was aufgezeichnet wird" (Standard an, während einer Aufnahme nicht änderbar): **Höhe** (Barometer, das GPS gibt das Niveau vor; ohne Barometer GPS-Höhe), **Tempolimit und Überschreitungen** und **Schräglage** (Schätzung aus Drehsensor und GPS-Tempo ab ca. 15 km/h, rechts positiv, egal wie das Handy liegt).
- Zusammenfassung zeigt zusätzlich Auf- und Abstieg (Höhenänderungen unter 3 m zählen nicht), wie oft das Tempolimit überschritten wurde (ab Limit + 3 km/h, eine Überschreitung zählt, bis man wieder auf Limit-Tempo ist; geschätzte Limits zählen nicht) und die maximale Schräglage.
- Zu jeder Fahrt gibt es jetzt auch eine **CSV-Datei** (Semikolon, Dezimalkomma, Umlaute korrekt, öffnet in Excel per Doppelklick; eine Zeile je Punkt, keine Zusammenfassung) in Download/GPX-Tracking. Teilen fragt, ob GPX oder CSV.
- In der GPX stehen Limit (`hb:limit`) und Schräglage (`hb:lean`) als eigene Felder, die andere Apps überspringen.

## v0.11.43
- **Tracking-Tab, Teil 1:** Aufzeichnung starten und beenden, mit Anzeige von Zeit in Bewegung, Strecke und Tempo. **Autopause**: nach 3 Minuten Stand pausiert die Aufnahme und geht ab 5 km/h von selbst weiter. Beenden nur durch dich, dann Zusammenfassung mit Name, **Speichern**, **Verwerfen** (mit Rückfrage) oder Weiter aufzeichnen.
- Gespeichert wird als **GPX** (mit Tempo je Punkt, Garmin-Erweiterung) in **Download/GPX-Tracking** und zusätzlich in der App. Die Liste **Meine Fahrten** zeigt alle Fahrten, Antippen öffnet die Zusammenfassung mit Teilen und Löschen (löscht auch die Datei in Downloads).
- Die Punkte werden während der Fahrt laufend gesichert. Wird die App oder der Dienst beendet, bietet die App die Fahrt beim nächsten Öffnen zum Speichern an.
- Während der Aufnahme fragt das GPS jede Sekunde, der Dienst bleibt an, und die Benachrichtigung zeigt "Aufzeichnung läuft". Der Tab bleibt anwählbar, solange aufgenommen wird.
- Noch nicht dabei (Teil 2): Höhe mit Barometer, Tempolimit und Überschreitungen, Schräglage, CSV.

## v0.11.42
- Anzeigemodus **Tracking** überarbeitet: zweites Feld ist jetzt das **Tempolimit** (statt Höhenmeter), Beschriftungen "FAHRZEIT" und "STRECKE". Eigene schematische Vorschau auf der Karte.

## v0.11.41
- Neuer Testmodus "Tracking (Test)" in der Anzeige: der Tracking-Bildschirm des HUD mit vier Feldern wie bei Calimoto (Tempo, Höhenmeter, gefahrene Zeit, gefahrene Strecke), Werte aus dem GPS des Handys.

## v0.11.40
- Text bei "Freies Fahren" klarer: Nur OSMAnd wird getrennt, GPS, Tempolimit und Straßenname laufen weiter.

## v0.11.39
- Übersicht: Der Schalter "Navigation aus OSMAnd" (Werkzeuge, Karte Datenquellen) ist jetzt der Schalter **Freies Fahren** auf der Übersicht, unter "App aktiv". An = OSMAnd komplett getrennt (die Einstellung bleibt dieselbe, nur umgekehrt benannt). Anrufe, WhatsApp und Spotify laufen weiter.
- Kleine graue **i-Knöpfe** hinter den Schaltern auf der Übersicht klappen die Beschreibung auf und zu. Die Übersicht ist dadurch kürzer.
- Der Info-Tab ist aus der unteren Leiste verschwunden (sie fasst höchstens fünf Tabs). Die Info-Seite öffnet das **i oben rechts**; Zurück-Taste oder Antippen eines Tabs führt zurück.
- Neuer Tab **Tracking** (Platzhalter, die Aufzeichnung folgt): grau und nicht anwählbar, solange Freies Fahren aus ist.

## v0.11.38
- Kreisverkehr und Abbiegungen bleiben am HUD stehen, auch wenn OSMAnd länger nichts meldet (im Hintergrund kamen Pausen bis über 30 s vor). Vorher wurde das HUD nach 10 s Stille geleert, und damit war auch die Ausfahrt-Nummer weg. Jetzt zählt die Entfernung mit dem GPS-Tempo weiter herunter (vorher nur 4 s lang) und wird erst nach 60 s ohne Meldung geleert.
- Log: "OSMAnd war X s still", wenn nach mehr als 10 s wieder eine Meldung kommt.

## v0.11.37
- Übersicht: Kommen die OSMAnd-Benachrichtigungen nicht an (Zugriff fehlt oder ist nach einem Update nicht verbunden), zeigt ein orange umrandeter Hinweis das an. Ein Tipp darauf öffnet die Android-Einstellung, dort den Zugriff für die App aus- und wieder einschalten. Der Hinweis verschwindet von selbst, sobald die Verbindung steht.
- Abbiegedistanz am HUD: Die Hochrechnung zwischen zwei OSMAnd-Meldungen nimmt jetzt das aktuelle Tempo (statt der aus den letzten Meldungen errechneten Geschwindigkeit) und zieht 0,8 s Meldeverzögerung ab. Die Anzeige hing sonst 10 bis 30 m hinterher. Bei frischen Meldungen springt die Anzeige nicht mehr zurück.

## v0.11.36
- Neu: Schalter "Akustische Warnung" bei der Tempowarnung (standardmäßig aus). Doppelpiepen, einmal pro Überschreitung (Limit + eingestellte Überschreitung, mindestens 1,5 s lang) und erst wieder, nachdem das Tempo auf oder unter das Limit gefallen ist. Der Ton läuft wie eine Navigationsansage (Musik wird kurz leiser) über das Handy, z. B. zur Intercom. Nur mit verbundenem HUD und nur bei einem Limit aus den Kartendaten, nicht bei einem geschätzten.
- Die App liest beim Start und beim Verbinden des Benachrichtigungszugriffs die schon vorhandene OSMAnd-Benachrichtigung nach. Damit klappt es auch, wenn OSMAnd vor der Bridge gestartet wurde.

## v0.11.35
- Neu: Quadratischer Knopf mit Navigationspfeil rechts neben dem Status ("Verbunden und bereit") auf der Übersicht. Ein Tipp öffnet OSMAnd. Ist OSMAnd nicht installiert, erscheint eine kurze Meldung.

## v0.11.34
- Build-Fehler behoben ("Only one companion object is allowed per class" in der Benachrichtigungs-Klasse). Keine neue Funktion, v0.11.33 ist damit gebaut lauffähig.

## v0.11.33
- Neu: Schalter "Geradeaus-Pfeil dauerhaft" (Reiter Anzeige, Karte Navigationsanweisung, standardmäßig aus). Solange die Navigation läuft, zeigt das HUD zwischen den Abbiegungen einen Geradeaus-Pfeil ohne Distanz, bis die nächste Abbiegung nah genug ist. Hört OSMAnd auf zu melden (nach 10 s) oder ist das Ziel erreicht, verschwindet er.
- Späte OSMAnd-Meldungen überschreiben die Zielflagge nicht mehr.

## v0.11.32
- Vorschau Explorer: Der Platzhalter "Hauptstraße" ist weg, denn im Modus Explorer sendet die App keinen Straßennamen. README nennt jetzt, in welchen Modi was erscheint.

## v0.11.31
- Nach einem App-Update las die App die OSMAnd-Benachrichtigung nicht mehr (km/h, Gesamtstrecke, Restzeit, Ankunft fehlten), obwohl der Benachrichtigungszugriff "an" zeigte. Die App fordert die Verbindung jetzt beim Start selbst neu an und schreibt nach 10 s eine Warnzeile ins Protokoll, falls sie trotzdem nicht verbunden ist. Neu im Protokoll: "Benachrichtigungszugriff getrennt".

## v0.11.30
- Musik am HUD: Selbstausblendung des HUD jetzt 9 s (genau die Dauer der Anzeige), damit sich beim Weiterschalten Titel nicht überschneiden.

## v0.11.29
- Musik am HUD: Der Titel wurde nur 1 s gezeigt, weil sich das HUD nach 6 s selbst ausblendete. Selbstausblendung jetzt 15 s, das Wegnehmen macht die App.

## v0.11.28
- Spotify/Musik am HUD: Das HUD fror bei Texten über 19 Zeichen ein. Jetzt höchstens 19 Zeichen, nacheinander: Interpret 4 s, 1 s leer, Titel 4 s. Ein Anruf oder eine Nachricht bricht die Anzeige ab.

## v0.11.27
- Fehler behoben: In der Übersicht blieb "Karte wird geladen" stehen, obwohl die Karte längst da war und nur kein Limit gefunden wurde. Jetzt steht dort "Limit: unbekannt".
- Protokoll: Bei "Tempolimit: unbekannt" steht jetzt der Grund dabei (Karte fehlt noch / keine Straße in der Nähe / Straße ohne Tempo-Angabe).
- Vorausschau beim Fahren: Die nächste Kachel wird jetzt schon 3 km vor der Kante geholt (vorher 1 km).
- Live-Laden fragt bis zu 4 Server gleichzeitig (vorher 3).

## v0.11.26
- Protokoll (Werkzeuge) und "Letzte Meldungen" (Übersicht): die neueste Zeile steht jetzt oben. Die Datei zum Teilen bleibt wie bisher zeitlich geordnet (älteste zuerst).

## v0.11.25
- Spotify-Anzeige: der Wechsel Interpret/Titel alle 2 s funktioniert am HUD nicht und ist wieder raus. Stattdessen steht "Interpret - Titel" 16 s lang im Meldungsfeld. Testweise bis 40 Zeichen (Umlaute werden zu ae/oe/ue, andere Sonderzeichen entfallen). Im Protokoll steht die Zeichenzahl des gesendeten Textes.

## v0.11.24
- Spotify-Anzeige: Das HUD scrollt nicht, deshalb wechselt die App 16 s lang alle 2 s zwischen Interpret und Titel (je viermal). Anrufe und Nachrichten verdrängen die Anzeige.

## v0.11.23
- Spotify-Anzeige korrigiert: Der Titel steht jetzt im Meldungsfeld des HUD, dort wo auch Anrufe und Nachrichten erscheinen (nicht mehr an der Stelle des Straßennamens). Länge testweise bis 30 Zeichen (das Feld nahm bisher höchstens 19), 15 s.

## v0.11.22
- Neu bei Meldungen: Schalter "Spotify" (standardmäßig aus). Beginnt in Spotify (oder einer anderen Musik-App) ein neuer Titel, zeigt das HUD 15 s lang "Interpret - Titel" an der Stelle des Straßennamens, ohne Symbol. Nur in Explorer und City, Anrufe haben Vorrang, Pausieren und Weiterspielen zählen nicht, Werbung wird übergangen. Sonderzeichen werden wie bei Straßennamen vereinfacht, höchstens 40 Zeichen. Braucht den Benachrichtigungszugriff, den die App ohnehin hat.
- Der Schalter "Name des Absenders anzeigen" ist entfernt, der Name wird immer angezeigt.
- Der Saison-Schalter heißt jetzt "Saisonfahrer".

## v0.11.21
- Neue Karte "Saison" (Werkzeuge, unter Einrichtung): "Nur in meiner Saison aktiv", Beginn- und Ende-Monat (zum Beispiel März bis Oktober, auch über den Jahreswechsel) und Vorlauf von 0 bis 6 Wochen. Außerhalb der Saison ruht die App komplett (kein Bluetooth, kein GPS, kein Dienst). Im Vorlauf vor Saisonbeginn lädt sie im Hintergrund (nur WLAN) die Straßendaten rund um den letzten Standort, auch wenn "Auch im Hintergrund vorladen" aus ist. Importierte Touren laden immer sofort. Endet die Saison, während die App läuft, beendet sie sich von selbst (Prüfung einmal pro Minute).
- Übersicht und Karte zeigen Klartext: "Außerhalb der Saison", "Die App ruht bis zum Saisonbeginn am 1. März. Straßendaten laden ab 8. Februar."

## v0.11.20
- Schalter neu aufgeteilt. "HUD verbinden" steuert nur noch die Bluetooth-Verbindung zum HUD und bleibt gespeichert. Ohne HUD läuft die App weiter und lädt Straßendaten (Umkreis und Touren). "Bridge aktiv" heißt jetzt "App aktiv": Aus = die App ruht komplett (kein Bluetooth, kein GPS, kein Laden), das HUD ist frei für die Tilsberk-App; nur das Laden im Hintergrund per WLAN läuft weiter, falls eingeschaltet.
- GPS in drei Stufen: mit HUD jede Sekunde; ohne HUD unterwegs alle 30 s, im Stand alle 5 min. Im Sparmodus hört die App zusätzlich auf Ortungen anderer Apps (zum Beispiel OSMAnd), das kostet nichts. Grobe Ortungen (über 200 m ungenau) werden ignoriert.
- Der Dienst läuft ohne HUD weiter, solange das Vorladen über 0 km steht oder eine Tour offen ist. Ist HUD verbinden aus und es gibt nichts zu laden, beendet er sich selbst. Ist das HUD an, aber nicht in Reichweite, und es gibt nichts zu laden, endet er wie bisher nach 10 Minuten.
- Statuszeile oben: "App ruht, HUD frei", "Kein HUD verbunden", "Kein HUD verbunden, Straßendaten laden läuft". "Dienst gestoppt" gibt es nicht mehr. "Dienst beendet" steht nur noch im Protokoll, wenn der Dienst wirklich lief.
- Die Benachrichtigung zeigt ohne HUD "Ohne HUD: lädt Straßendaten, Standort im Sparmodus".

## v0.11.19
- Übersicht, Live: "Straßendaten laden" ist jetzt ein echter Ticker. Es gibt eine Zeile pro offenem Punkt: "Straßendaten Tour „Name“" (darunter "27 von 123 Kacheln") für jede offene Tour, dann "Straßendaten Umkreis". Was gerade geladen wird, steht oben. Fertiges verschwindet, ist alles fertig, ist keine Zeile mehr da. Der Umkreis trägt den Zusatz "(wartet auf Tour)", solange eine Tour offen ist.
- Die Tour-Zahl zieht jetzt nach jeder geladenen Kachel nach.

## v0.11.18
- Protokoll: Wiederholungen werden zusammengefasst, statt sie wegzulassen. Beispiele: "OSMAnd: 48 Verbindungsversuche in 17 min", "Tour vorladen: 7 Fehlversuche (6x timeout, 1x HTTP 403), danach ging es weiter". Die erste Zeile jeder Störung steht weiter einzeln drin.
- Neuer Schalter "Ausführliches Protokoll" (Werkzeuge, Karte Protokoll): schreibt jede Zeile einzeln, für die Fehlersuche.
- Die Protokolldatei darf jetzt etwa 5 MB groß werden (vorher 1 MB). Danach wird wie bisher die ältere Hälfte verworfen.

## v0.11.17
- Protokoll zeigt den Fortschritt: "Tour „Name“: 37 von 123 Kacheln da" (alle 10 Kacheln), "Tour vorladen: alle Kacheln da", "Vorladen: 150 von 1711 Kacheln" (alle 50). Das Laden im Hintergrund schreibt Anfang und Ende ("Hintergrund-Laden beendet: 12 Kacheln neu geladen").
- Fehlermeldungen beim Laden nennen jetzt den Server (zum Beispiel "overpass-api.de: HTTP 403").
- Weist ein Server uns ab (HTTP 403 oder 429), wird er 15 Minuten pausiert statt 3.
- HUD: ein früher gemerktes Gerät, das kein HUD ist (zum Beispiel der Lautsprecher aus v0.11.15), wird vergessen. Der Dienst startet dann nicht mehr von selbst damit. Ein von Hand gewähltes Gerät bleibt gemerkt.

## v0.11.16
- Straßendaten laden: ein Server, der ausfällt oder zu langsam ist, wird ein paar Minuten übersprungen, statt ihn immer wieder zu probieren. Vorladen und Touren warten bei einem Server höchstens 15 s.
- Mehr Server in der Liste (lz4.overpass-api.de, z.overpass-api.de, overpass.openstreetmap.fr). Bei der Fahrt fragen die ersten drei brauchbaren gleichzeitig, fällt einer aus, rückt der nächste nach.
- Protokoll: "OSMAnd nicht erreichbar" steht nur noch einmal drin statt alle 10 Sekunden.
- HUD verbinden: Die App bietet nur noch Geräte mit HUD-Namen an (TILS, Head-Up, DVISION). Findet sie keins, kommt der Hinweis "Kein HUD gefunden"; alle gekoppelten Geräte (z. B. Lautsprecher) erscheinen nur noch auf Knopfdruck.

## v0.11.15
- Reiter Tempolimit: kurze Erklärkarte oben ("Diese App zeigt keine Karte. Sie lädt Straßendaten …"). "Karten" heißt jetzt überall "Straßendaten" (Straßendaten vorladen, Speicher für Straßendaten, Straßendaten laden).
- Übersicht, Live: die Zeile "OSMAnd" zeigt nur noch den Zustand (Verbunden, Verbinde, Nicht verbunden, Aus). Die Ansagen stehen weiter im Protokoll.
- Werkzeuge: "OSMAnd-Navigation übernehmen" heißt jetzt "Navigation aus OSMAnd" und steht direkt unter "Einrichtung".

## v0.11.14
- Neue Karte "Tour vorladen" (Tempolimit): GPX-Datei wählen (Calimoto, Kurviger, Motobit und andere), die App lädt die Karte entlang der Strecke (Rand 1,5 km, bei Dateien mit nur wenigen Punkten 3 km). Gibt es in der Datei eine Aufzeichnung (Track), gilt sie, sonst die Route. Schon gespeicherte Kacheln (unter 90 Tage) werden übersprungen. Die Tour hat Vorrang vor dem Kreis, lädt auch im Hintergrund (nur WLAN) und zeigt den Stand ("123 von 249 Kacheln"). Eine Tour verschwindet, sobald alle Kacheln da sind. "Touren in Arbeit abbrechen" entfernt die offenen Touren, gespeicherte Kacheln bleiben.

## v0.11.13
- Neue Karte "Speicher für Karten" (Tempolimit): Regler 0,5 / 1 / 2 / 5 / 10 GB (Standard 2 GB), darunter "Belegt: … MB, … Kacheln". Ist der Platz voll, werden die ältesten Kacheln gelöscht, bei kleinerer Einstellung sofort. Vorher gab es nur eine feste Grenze von 3000 Kacheln.
- Die Kacheln liegen jetzt im App-Speicher statt im Cache-Ordner. Android leert ihn nicht von selbst, auch "Cache leeren" löscht sie nicht mehr. Vorhandene Kacheln werden beim ersten Start verschoben.
- Vorladen überspringt Kacheln, die jünger als 90 Tage sind (vorher 30 Tage). Die Kacheln bleiben weiterhin 180 Tage gültig.

## v0.11.12
- Übersicht, Karte "Live": neue Zeile "Karte vorladen" mit dem Stand (zum Beispiel "123 von 452 Kacheln", "Fertig …", "Wartet auf WLAN").
- Vorladen: Der Kreis wird jetzt schon nach 3 km Fahrt neu um den aktuellen Standort gelegt (vorher 10 km).
- Vorladen: Unterwegs werden zusätzlich die Kacheln in einem Streifen von 20 km in Fahrtrichtung vorgezogen (nur über WLAN bzw. mit erlaubtem Mobilfunk, nicht im Roaming). Das Gebiet in Fahrtrichtung kommt damit vor dem Rest des Kreises.

## v0.11.11
- Das Vorladen im Hintergrund läuft jetzt auch, wenn "Bridge aktiv" aus ist (das HUD wird dabei nicht berührt, nur WLAN). Hinweistexte, README angepasst.

## v0.11.10
- Neuer Schalter "Auch im Hintergrund vorladen" (Tempolimit, Gebiet vorladen): Das Vorladen läuft dann auch ohne laufenden Dienst und ohne HUD, solange Android es erlaubt (nur WLAN, Akku nicht schwach, alle 6 Stunden und kurz nach dem Einschalten). Nutzt den zuletzt gemerkten Standort. Läuft der Dienst, übernimmt dieser.
- Neue Bibliothek: androidx.work (WorkManager) für den Hintergrund-Auftrag.

## v0.11.9
- Vorladen: Schlägt ein Server fehl, wird sofort der nächste der drei Server probiert (nacheinander).
- Das Protokoll nennt bei fehlgeschlagenem Vorladen jetzt den genauen Grund (zum Beispiel "HTTP 429").

## v0.11.8
- Behebt einen Build-Fehler (Funktionsname im Vorlade-Status).
- Anzeige: "Pfeil anzeigen ab" heißt jetzt **Navigationsanweisung**. Reihenfolge: Modus, Navigationsanweisung, Meldungen, Helligkeit, Justage.
- Die Tempo-Quelle wanderte von Tempolimit nach Werkzeuge und heißt dort "Tempo für das HUD".

## v0.11.7
- Neuer Reiter **Tempolimit** (ersetzt "Einstellungen"), mit Live-Kachel oben: Verkehrsschild mit dem aktuellen Limit, darunter die Herkunft (aus den Kartendaten / geschätzt / unbekannt) und das aktuelle Tempo.
- Im Reiter Tempolimit: Tempowarnung (von Anzeige hierher), Tempo-Quelle, Datenquelle, Fehlende Limits schätzen, Gebiet vorladen.
- Meldungen sind jetzt im Reiter Anzeige, "OSMAnd-Navigation übernehmen" im Reiter Werkzeuge.

## v0.11.6
- Einstellungen: neuer Abschnitt **Tempolimit** ganz oben, alle Punkte einzeln schaltbar:
  - "Tempolimit aus OSM-Daten" (Hauptschalter) und "Zusätzliche Hinweise der Karte nutzen" (Tempo-Zonen, Quellenangaben wie innerorts/außerorts, echte Kartendaten, an).
  - "Fehlende Limits schätzen" mit den Unterpunkten "Ortsschilder nutzen" und "Nachbarabschnitte nutzen" (beide an, wirken nur beim Schätzen). Ortsschilder: Eingang = innerorts 50, Ausgang = außerorts 100, gilt bis zum nächsten Schild, höchstens 2,5 km. Nachbarabschnitte: Limit der anschließenden Abschnitte derselben Straße. Beides sind Schätzwerte und lösen keine Tempowarnung aus. Jedes erkannte Ortsschild steht im Protokoll.
  - "Gebiet vorladen" mit neuem Schalter "Auch über mobile Daten vorladen" (aus, nie im Roaming).
- Die übrigen Einstellungen (Meldungen, OSMAnd, Tempo-Quelle) stehen darunter unter "Weitere Einstellungen".
- Kacheln enthalten jetzt auch Ortsschilder. Der Zwischenspeicher wird neu aufgebaut (alte Dateien werden gelöscht).

## v0.11.5
- Tempolimit, Server: Kacheln, die gerade gebraucht werden, werden bei allen drei Servern gleichzeitig angefragt (der schnellste gewinnt, die übrigen werden abgebrochen). Zeitlimit pro Server 20 s statt 9 s.
- Zwischenspeicher: gepackt (gzip), gültig 180 Tage statt 7. Ist eine Kachel älter als 30 Tage, wird sie sofort benutzt und im Hintergrund erneuert. Platz für bis zu 3000 Kacheln.
- Neu in Einstellungen, Datenquellen: Regler "Gebiet vorladen" (Aus, 10, 25, 50 km, Standard 25 km). Im WLAN lädt die App rund um den letzten Standort von selbst langsam im Voraus (ein Server, 2,5 s Pause zwischen den Kacheln, setzt später fort, nutzt keine mobilen Daten). Der Fortschritt steht unter dem Regler. Solange vorgeladen wird, beendet sich der Dienst nicht wegen fehlendem HUD.
- Neue Berechtigung "Netzwerkstatus" (nur um zu erkennen, ob WLAN aktiv ist).

## v0.11.4
- Schreibweise überall "OSMAnd" (App-Name, Benachrichtigung, Texte in der App, README, Hinweise). Interne Namen und der Quellcode von OsmAnd selbst bleiben unverändert.

## v0.11.4
- Einstellungen: Abschnitt "Verbindung" entfällt. Automatisch verbinden hängt jetzt fest an "Bridge aktiv", das Beenden nach 10 Minuten ohne HUD ist ein fester Standard.
- Werkzeuge, Einrichtung: Benachrichtigungszugriff und Akku-Optimierung sind jetzt Schalter, die den echten Zustand der Berechtigung zeigen (beim Öffnen der App neu geprüft). Antippen öffnet die passende Android-Seite, denn setzen oder entziehen darf nur der Nutzer.
- Protokoll (Werkzeuge) und "Letzte Meldungen" (Start) in Orange, Größe unverändert.
- Schreibweise "OSMAnd" auch in internen Bezeichnungen (Log-Kennung, User-Agent).

## v0.11.3
- Start-Reiter neu gestaltet: eine große Statuskarte (großer Statuspunkt, Meldung, darunter die Schalter "HUD verbinden" und "Bridge aktiv"), "Live" mit beschrifteten Werten (GPS und Tempo, Tempolimit, OsmAnd) und die letzten Meldungen. Die Knöpfe Start/Beenden sind durch den Schalter "HUD verbinden" ersetzt.

## v0.11.2
- Helligkeit korrigiert: dunkel und hell waren vertauscht.
- "Pfeil anzeigen ab" ist jetzt ein Regler (Kurz, Normal, Lang, Immer) im Anzeigen-Reiter, über der Tempowarnung. Darunter stehen die Entfernungen für Stadt, Landstraße und schnelle Straße. "Ab 1 km" entfällt (gespeichertes "Ab 1 km" wird zu "Normal").

## v0.11.1
- Neuer Schalter "Fehlendes Limit schätzen" (Einstellungen, Datenquellen, Standard aus): ohne Limit in den Kartendaten wird in Deutschland 50 (Wohnstraße oder beleuchtet) bzw. 100 (Landstraße) angenommen, nie auf Autobahnen. Geschätzte Limits lösen keine Tempowarnung aus.

## v0.11.0
- Anzeigen-Reiter: neue Karte **Tempowarnung** (Schalter und Regler 0 bis 30 km/h Überschreitung, Standard 10). Zu schnell = das HUD zeigt Tempo und Limit dicker. Aus = nie.
- Anzeigen-Reiter: neue Karte **Helligkeit** (Schalter "Automatische Helligkeit", sonst Regler dunkel/mittel/hell). Gilt sofort und wird beim Verbinden gesetzt. Die Zuordnung der Stufen stammt aus einem Mitschnitt und ist am HUD zu prüfen.
- Anzeigen-Reiter: neue Karte **Justage** (Schalter "Justage-Modus"): zeigt das Einstellbild am HUD, beim Ausschalten kehrt das HUD zum gewählten Modus zurück. Nur bei verbundenem HUD, wird nie über einen Neustart hinweg gespeichert.
- Tempolimit deutlich zuverlässiger: Karte wird in Kacheln (ca. 2 x 2 km) geladen, die Kachel in Fahrtrichtung schon vorab, alles auf dem Gerät zwischengespeichert (7 Tage, funktioniert danach auch ohne Netz). Schnellere Wiederholung bei Fehlern (höchstens 15 s statt bis zu 80 s), kürzere Zeitlimits, ein weiterer Server. Auf der gleichen Straße (gleicher Name/Ref) bleibt das Limit bis zu 90 s erhalten, an Kreuzungen und Parallelstraßen gewinnt die Straße, auf der man schon fährt.

## v0.10.8
- Überschrift in der App in der Schreibweise "OSMAnd HUD Bridge".

## v0.10.7
- Überschrift in der App heißt jetzt "OsmAnd HUD Bridge" (stand noch mit dem Herstellernamen darin).

## v0.10.6
- Start-Reiter: Karte "Bridge aktiv" und Statuskarte haben jetzt dieselbe Schriftgröße und denselben Aufbau, der Schalter sitzt rechts.

## v0.10.5
- Neuer Schalter "Bridge aktiv" (Start): aus = die App nutzt weder Bluetooth noch GPS oder Internet und liest keine Benachrichtigungen, das HUD bleibt für die Original-App frei. Bleibt gespeichert.
- Neue Einstellung "Dienst automatisch beenden": Der Dienst beendet sich, wenn das HUD 10 Minuten lang nicht verbunden war (Standard: an).

## v0.10.4
- App-Symbol neu (Entwurf D): Brücke von vorn mit drei Bögen im HUD-Rahmen (Eckklammern), orange Pfeil darüber. Liegt komplett im sicheren Bereich des adaptiven Icons.

## v0.10.3
- App-Symbol: Brücke läuft von vorne links nach hinten rechts und ist undurchsichtig im Vordergrund, das HUD-Symbol (orange Umrandung, größer) sitzt rechts unten und tritt hinter der Brücke hervor.

## v0.10.2
- App-Symbol: stilisierte Steinbogenbrücke in Perspektive (Bögen werden nach hinten kleiner), HUD-Symbol rechts daneben.

## v0.10.1
- App-Symbol: filigrane, halbtransparente Steinbogenbrücke mit dünnen Linien, HUD dahinter.
- NOTICE.md: Hinweis zur Nennung der Produktnamen.

## v0.10.0
- Neuer Paketname `io.github.th3s1nc.osmandhudbridge` (frische Installation, alte App vorher deinstallieren; in OsmAnd die neue App unter "Verbundene Apps" freigeben).
- Klassen/Styles neutral benannt (`HudProtocol` statt Produktname), Kommentare ohne Verweise auf interne Namen des Herstellers.
- Lizenz MIT, README (EN/DE), PROTOCOL.md, `.gitignore`, Gradle-Wrapper-Skripte.
- Neues App-Symbol: Steinbogenbrücke im Vordergrund, schematisches HUD im Hintergrund.

## v0.9.2
- Vorschaubilder neu: echte Zahlen und Texte, eigene Symbole, gleiche Anordnung der Elemente wie im jeweiligen Modus.
- Eigener Reiter "Info" (Hinweise, Haftung, Datenschutz, Quellen/Lizenzen, Version).
- Neues App-Symbol (adaptiv, mit Monochrom-Variante).

## v0.9.1
- Lizenz der OsmAnd-Schnittstelle geklärt (laut OsmAnd-Doku frei verfügbar), Info-Seite und NOTICE.md angepasst.

## v0.9.0
- Neuer App-Name "OsmAnd HUD Bridge", eigene Vorschaubilder, Info-Seite, NOTICE.md. Protokolldatei heißt osmand-hud-bridge-log.txt.

## v0.8.3
- Zwischenziel erreicht: VIA-Symbol für 8 s (OsmAnd-Ansage "reached_intermediate", Name vermutet).

## v0.8.2
- "halb/leicht links/rechts" (OsmAnd Typ 3/6) wird als KEEP_LEFT/KEEP_RIGHT gesendet statt als Abbiegepfeil.

## v0.8.1
- Ersatz für fehlende OsmAnd-Schnittstelle (Callback-ID -1, z. B. OsmAnd~ aus F-Droid ohne Freigabe): Pfeil und Entfernung aus der Benachrichtigung (deutsch/englisch). Dann fehlen Zielflagge und Hochrechnung zwischen den Meldungen.

## v0.8
- Reiter Anzeige mit Vorschaubildern der vier Modi.
- City: nächste und aktuelle Straße liegen im HUD übereinander, deshalb wird immer nur eine gesendet (nächste Straße hat Vorrang).

## v0.7.3
- Schalter "OsmAnd-Navigation übernehmen" aus: auch die Daten aus der OsmAnd-Benachrichtigung werden ignoriert. Anrufe/WhatsApp unberührt.

## v0.7.2
- Log wird in eine Textdatei geschrieben (höchstens ca. 1 MB, danach wird die ältere Hälfte verworfen). Teilen und Löschen in der App.

## v0.7
- Zielflagge über die OsmAnd-Ansage `reached_destination`; die Abstands-Heuristik entfiel.
- WhatsApp-Nachrichten laufen über die Anruf-Anzeige (5 s); ein laufender Anruf hat Vorrang.

## v0.6 – Anrufe und WhatsApp am HUD
- Einstellungen > Meldungen: Schalter für Anrufe, WhatsApp-Nachrichten und Absendername. Gelesen wird über den Benachrichtigungszugriff.
- Anzeige nur in Explorer und City. Nachrichten 5 s, Anruf bis er endet (höchstens 120 s).

## v0.5
- Zielflagge (`GOAL`) per Heuristik (später ersetzt).

## v0.4
- Anzeigemodi Navigator, Minimalist, Explorer, City; zuletzt gewählter Modus wird gespeichert; automatisches Verbinden.
