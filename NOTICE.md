# Hinweise (HUDPilot)

Inoffizielles Hobbyprojekt. Keine Verbindung zu Tilsberk, Digades, Moket, WhatsApp oder Spotify.
Genannte Namen und Marken gehören ihren Inhabern. "Tilsberk" und "DVision" dienen nur der Beschreibung der Geräte-Kompatibilität.

## Haftung
Keine Gewähr. Anzeigefehler und Ausfälle sind möglich. Nutzung beim Fahren auf eigene Gefahr. Siehe auch Haftungsausschluss in der MIT-Lizenz (`LICENSE`).

## Eigene Teile (MIT, `LICENSE`)
Quellcode der App, Zuordnungstabellen, Parser, Oberfläche, Vorschaubilder (eigene Zeichnungen), Dokumentation. Das App-Symbol wurde mit KI-Hilfe gestaltet. Urheber: th3_s1nc.
Es sind keine Programmteile, Grafiken, Schriften oder Texte der Hersteller-App enthalten.

## Fremde Bestandteile

| Teil | Quelle / Lizenz | Hinweis |
|---|---|---|
| Kartendaten (Straßennamen, Tempolimits) | © OpenStreetMap-Mitwirkende, ODbL (openstreetmap.org/copyright) | Hinweis in der App (Reiter Info) |
| Overpass API | öffentliche Server, moderate Nutzung | für wenige Nutzer gedacht |
| Importierte Kartendateien (.osm.pbf, z. B. Geofabrik) | © OpenStreetMap-Mitwirkende, ODbL | werden vom Nutzer geladen (auch über "Region laden" in der App, nur auf Tipp), nicht mitgeliefert. Blitzer-Listen (z. B. SCDB) sind nicht enthalten und gehören nicht zum Projekt |
| BRouter (`app/src/main/java/btools`, Profile in `app/src/main/assets/brouter`) | © BRouter contributors, MIT-Lizenz (`LICENSE-BRouter.txt`), github.com/abrensch/brouter | Routing-Programm, unverändert eingebunden; die Profile `motorrad_*.brf` sind vom Moped-Profil abgeleitet. Die Datei `PilotRouting.java` ist eigener Code im BRouter-Paket |
| Routendaten (.rd5) von brouter.de | © OpenStreetMap-Mitwirkende, ODbL (openstreetmap.org/copyright) | werden in der App auf Tipp geladen, nicht mitgeliefert |
| AndroidX, Material Components | Apache License 2.0 | Abhängigkeiten, werden beim Bauen geladen |
| MapLibre Native (`org.maplibre.gl:android-sdk`) | © MapLibre contributors, BSD-2-Clause (github.com/maplibre/maplibre-native) | Kartenanzeige, Abhängigkeit beim Bauen |
| Kartenstil Protomaps Basemaps (`app/src/main/assets/maplibre/style-*.json`) | © Protomaps LLC, Kelso Cartography, BSD-3-Clause (`LICENSE-Protomaps-Basemaps.md`) | mit dem Stilgenerator von github.com/protomaps/basemaps erzeugt, Schriftnamen in der Datei umbenannt |
| Schriften Noto Sans (`app/src/main/assets/maplibre/glyphs`) | © Google / Noto-Autoren, SIL Open Font License 1.1 (`LICENSE-NotoSans-OFL.txt`) | Schriftzeichen als Kartenschrift (github.com/protomaps/basemaps-assets) |
| Kartensymbole (`app/src/main/assets/maplibre/sprites`) | abgeleitet von tangrams/icons, MIT-Lizenz (github.com/tangrams/icons) | aus github.com/protomaps/basemaps-assets |
| Vektorkarten (.pmtiles) von Protomaps | © OpenStreetMap-Mitwirkende, ODbL (openstreetmap.org/copyright) | vom Nutzer erzeugt und gewählt, nicht mitgeliefert. Der Hinweis steht in der Kartenansicht |

## Nennung der Produktnamen
"Tilsberk", "DVision", "Moket", "WhatsApp" und "Spotify" werden nur beschreibend genannt, um zu sagen, mit welchem Gerät die App zusammenarbeitet (vgl. § 23 Abs. 1 Nr. 3 MarkenG, "Bestimmungshinweis"). Sie stehen nicht im App-Namen, Paketnamen oder Repository-Namen, es werden keine Logos oder Bildmarken verwendet, und es soll nicht der Eindruck einer Verbindung zu den Herstellern entstehen. Der Hersteller (Digades GmbH) hat sich Ende 2024 aus dem Endkundengeschäft zurückgezogen; wer heute Rechte an den Marken hält, wurde nicht geprüft.

## HUD-Protokoll
Das Funkprotokoll wurde allein zur Interoperabilität ermittelt: durch Beobachten des Funkverkehrs (Mitschnitte) und durch Untersuchung der Hersteller-App, um Nachrichtenformate zu verstehen. Es ist in `PROTOCOL.md` beschrieben. Es ist kein Code, keine Grafik und kein sonstiges Material der Hersteller-App übernommen. Die Rechte an Namen, Marken und der Hardware liegen beim jeweiligen Inhaber. Ob und wie weit an einer Funkschnittstelle Schutzrechte bestehen, wurde nicht abschließend geklärt; die rechtliche Einordnung liegt bei dem, der das Projekt weitergibt oder nutzt.
