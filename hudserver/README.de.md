# HUDPilot Updater

Selbst gehosteter Server für die Android-App **HUDPilot** (Motorrad-Navigation mit HUD). Er baut und liefert alles, was die App offline braucht, und hat einen Tourenplaner im Browser.

- **Karten** (Vektorkarten als `.pmtiles`, aus Protomaps), **Orte** (Tanken, Essen, Hotel, Parken), **Straßendaten** (Tempolimits, Blitzer, Bahnübergänge), **Routendaten** für BRouter (`.rd5`)
- **Verwaltung** im Browser: Regionen wählen (nach Ländern sortiert, mit Suche), bauen, Fortschritt, wer lädt gerade was, Profile, Protokoll
- **Europa-Liste** (optional, ein Knopf): bietet alle EU-Länder, die Schweiz, Norwegen, Großbritannien, Griechenland und die Türkei als wählbare Regionen an. Es wird nichts geladen oder gebaut, bevor du es ankreuzt.
- **Profile** (Name + Code): eigene Touren, Fahrten, Favoriten und App-Einstellungen pro Person
- **Aufräumen**: Gebaute Regionen lassen sich wieder vom Server löschen; Handys, die 90 Tage (`DEV_KEEP_DAYS`) nicht da waren, werden automatisch vergessen, einzelne Handys kann man von Hand entfernen
- **Tourenplaner** im Browser (Routen rechnet BRouter im Container, mit Umfahrung von Sperrungen; Motorrad-Profile und ein Auto-Profil; Strecke und Zeit groß angezeigt; Rundtour-Planer (50 bis 500 km, Richtung, Zufall oder eigenes Ziel, wahlweise im Land bleibend; der Rückweg ist immer eine andere Strecke), Adresssuche über Nominatim, Tanken/Essen/Hotel/Parken aus den eigenen gebauten Regionen)
- **Freunde und Live-Standort**: Profile können sich über den genauen Profilnamen als Freunde hinzufügen (die andere Seite muss annehmen); pro Freund erlaubst du „darf mich sehen“, und die App sendet deine Position nur bei eingeschaltetem „Standort teilen“. Positionen liegen nur im Arbeitsspeicher und verfallen nach 2 Minuten; die Verwaltung zeigt Zähler und Statistik, nie Positionen
- **Baustellen und Sperrungen** (optional, Bayern, aus der Mobilithek)

> Privates Hobbyprojekt, nicht gegen alle Umgebungen getestet. Fehler stehen im Protokoll der Verwaltung.

## Was du brauchst

- Einen Rechner oder Server mit **Docker** (x86_64, zum Beispiel Unraid, ein NAS oder ein Mini-PC). Das Image lädt Programme für x86_64.
- **Platz:** Deutschland und die Nachbarländer brauchen zusammen etwa 17 GB, jede Region einzeln zwischen 80 MB und 2 GB. Nach dem ersten Bauen reichen kleine Aktualisierungen.
- **Zeit:** Das erste Bauen dauert Stunden. Der Server lädt die Rohdaten selbst aus dem Internet (Tempo einstellbar).

## Installation (Docker Compose)

1. Den Ordner `hudserver` auf den Server kopieren.
2. In `docker-compose.yml` das Passwort ändern (`ADMIN_PASSWORD`).
3. Im Ordner starten:

```
docker compose up -d --build
```

4. Im Browser `http://<IP-des-Servers>:8085/admin` öffnen, mit dem Passwort anmelden.
5. Unter **Regionen** die gewünschten Regionen abhaken, dann **Markierte neu erstellen** drücken. Danach hält der Server die markierten Regionen selbst aktuell.
6. Mehr als Deutschland, Österreich, Schweiz und Tschechien? Im Reiter Regionen **Europa-Liste holen** drücken. Der Server holt die Regionsliste von Geofabrik und ergänzt sie, nach Ländern sortiert. Neue Regionen sind nicht angehakt. Hake nur an, was du wirklich fährst; ein Land kann mehrere GB groß sein.

### Ohne Compose (zum Beispiel Unraid)

```
docker build -t hud-updater /pfad/zu/hudserver/updater
docker run -d --name hud-updater --restart unless-stopped \
  -p 8085:8085 -e TZ=Europe/Berlin -e ADMIN_PASSWORD=BITTE-AENDERN \
  -v /pfad/zu/hudserver/data:/data hud-updater
```

Auf Unraid geht auch ein eigenes Docker-Netz mit fester IP (`--network br0 --ip <freie-IP> -e PORT=80`). Dann ist der Server ohne Portangabe erreichbar.

## In der App einrichten

1. In der App **Menü → Server** öffnen und die Adresse des Servers eintragen (zu Hause zum Beispiel `http://<IP-des-Servers>:8085`, unterwegs der https-Name, siehe unten).
2. Ein Profil anlegen: Name und **Einladungscode** (steht in der Verwaltung unter **Profile**).
3. Regionen laden: **Menü → Karte → Regionen laden**.

## Einstellungen (Umgebungsvariablen)

| Name | Bedeutung | Standard |
|---|---|---|
| `ADMIN_PASSWORD` | Passwort der Verwaltung. Ohne Passwort kann man nur ansehen. | leer |
| `PORT` | Port des Servers im Container | 8085 |
| `TZ` | Zeitzone | UTC |
| `DATA_DIR` | Ordner für alle Daten im Container | `/data` |
| `NPM_IPS` | IP-Adresse(n) deines Proxys, kommagetrennt. Zugriffe davon gelten als „von außen“. | leer |
| `NEED_DAYS` | Regionen, die ein Handy in dieser Zeit gemeldet hat, hält der Server aktuell | 90 |
| `UPDATE_EVERY_DAYS` | Wie oft der automatische Lauf startet | 30 |
| `REGIONS` | Welche Regionen anfangs markiert sind (`ALL` = die vier mitgelieferten Länder; Regionen aus der Europa-Liste sind nie von selbst markiert) | ALL |
| `INVITE_CODE` | Einladungscode für neue Profile beim ersten Start | zufällig |

Weitere (selten nötig): `MAXZOOM`, `LIMITS_XMX`, `PROXY_PORT`, `BROUTER_PORT`, `PLANET_BASE`, `GEOFABRIK_BASE`, `BROUTER_BASE`.

Gespeichert wird alles im Ordner `data` (Karten, Routen, Profile, Einstellungen). Diesen Ordner sicherst du, wenn du den Server umziehst.

## Zugriff von außen (optional)

Wenn die App auch unterwegs Dinge holen soll (Baustellen, Blitzerliste, Updates, Touren), brauchst du einen **Reverse Proxy mit https** (zum Beispiel Nginx Proxy Manager) und einen Namen, zum Beispiel von einem DynDNS-Dienst.

- Zugriffe über den Proxy erkennt der Server an den Weiterleitungs-Zeilen (`X-Forwarded-For`, `X-Real-IP`). Setze zusätzlich `NPM_IPS` auf die IP deines Proxys, dann gilt auch ohne diese Zeilen alles von dort als extern.
- **Von außen gibt es nie:** die Verwaltung, das Anlegen neuer Profile. Karten, Straßendaten und Orte gibt es von außen nur, wenn du in der Verwaltung **Karten von außen laden** einschaltest. Die Anmeldung mit Profil-Code ist gesperrt, nach 5 falschen Versuchen 15 Minuten.
- Der Server schickt bei Datei-Downloads `X-Accel-Buffering: no`, damit der Proxy nicht zwischenlagert und die Verwaltung das echte Tempo sieht.

Vorschlag für die „Custom Nginx Configuration“ des Proxys (Namen anpassen):

```
client_max_body_size 50M;
proxy_buffer_size 128k;
proxy_buffers 4 256k;

add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;

# Nur der eigene Name wird bedient, alles andere (auch Zugriff per IP) bekommt keine Antwort
if ($host !~* ^navi\.example\.duckdns\.org$) {
    return 444;
}

# Typische Suchen von Scannern gleich abweisen
location ~* (wp-includes|wp-admin|wp-content|xmlrpc\.php|\.git|\.env|phpmyadmin) { return 444; }
```

HSTS schaltest du im Proxy beim Zertifikat ein („HSTS Enabled“), nicht zusätzlich in der Konfiguration.

## Baustellen und Sperrungen (optional, Bayern)

Die Daten kommen aus der **Mobilithek** (DATEX II). Du brauchst dort ein Konto, ein Abo für „Baustellen Bayern“ und ein Zertifikat. Lege die Dateien **nur auf deinem Server** ab, nie ins Projekt und nie auf GitHub:

- `data/mobilithek/client.p12` (Zertifikat)
- `data/mobilithek/config.json` mit `{"abo": "<Abo-Nummer>", "pass": "<Passwort des Zertifikats>"}`

Ohne diese Dateien nutzt der Server eine Testdatei. Die App zeigt die Baustellen auf der Karte und kann sie bei der Routenberechnung umfahren.

## Sicherheit in Kürze

- `ADMIN_PASSWORD` ändern, die Verwaltung nicht ins Internet stellen.
- Nur den Proxy-Namen (https) nach außen freigeben, nicht den Server selbst.
- Den Ordner `data` regelmäßig sichern. Er enthält die Profile und alle Touren.
- Zertifikate und Passwörter gehören nie ins Projekt.

## Datenquellen

Karten und Straßendaten: © OpenStreetMap-Mitwirkende (ODbL), Kartenbau von Protomaps, Regionsauszüge von Geofabrik. Routendaten: BRouter-Segmente von brouter.de. Baustellen: Mobilithek. Der Tourenplaner im Browser lädt Programmteile und Kacheln von Drittanbietern (unter anderem unpkg.com, OpenFreeMap, AWS-Terrain). Dafür braucht der Browser Internet.

## Ordner

- `updater/`: der Server (Python), `static/` die Webseiten, `limits/` und `../limits-tool/` das Werkzeug für die Straßendaten, `brouter-profiles/` die Routenprofile
- `docker-compose.yml`: Start mit Docker Compose
- `CHANGELOG.md`: Verlauf aller Versionen
