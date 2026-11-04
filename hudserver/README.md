# HUDPilot Updater

Self-hosted server for the Android app **HUDPilot** (motorcycle navigation with HUD). It builds and serves everything the app needs offline, and has a trip planner in the browser. (A German version of this page is in `README.de.md`.)

- **Maps** (vector maps as `.pmtiles`, from Protomaps), **places** (fuel, food, hotels, parking), **road data** (speed limits, speed cameras, level crossings), **routing data** for BRouter (`.rd5`)
- **Admin page** in the browser: choose regions (sorted by country, searchable), build them, progress, who is downloading what, profiles, log
- **Europe list** (optional, one button): offers all EU countries, Switzerland, Norway, Great Britain, Greece and Turkey as selectable regions. Nothing is downloaded or built until you tick it.
- **Profiles** (name + code): own tours, rides, favourites and app settings per person
- **Housekeeping**: built regions can be deleted from the server again; phones not seen for 90 days (`DEV_KEEP_DAYS`) are forgotten automatically, single phones can be removed by hand
- **Trip planner** in the browser (routes calculated by BRouter in the container, with avoidance of road closures)
- **Road works and closures** (optional, Bavaria, from the Mobilithek)

> A private hobby project, not tested in every environment. Errors show up in the admin log.

## What you need

- A computer or server with **Docker** (x86_64, e.g. Unraid, a NAS or a mini PC).
- **Space:** Germany plus its neighbours need about 17 GB in total, a single region between 80 MB and 2 GB.
- **Time:** the first build takes hours. The server downloads the raw data itself (speed adjustable).

## Installation (Docker Compose)

1. Copy the `hudserver` folder to your server.
2. Change the password in `docker-compose.yml` (`ADMIN_PASSWORD`).
3. Start it in that folder:

```
docker compose up -d --build
```

4. Open `http://<server-ip>:8085/admin` in a browser and sign in.
5. Tick the regions you want under **Regions** and press the rebuild button for the ticked regions. After that the server keeps them up to date by itself.
6. Want more than Germany, Austria, Switzerland and the Czech Republic? Press **Get Europe list** (Regions tab). The server fetches the list of regions from Geofabrik and adds them, sorted by country. New regions are not ticked. Tick only what you really ride; one country can be several GB.

Without Compose:

```
docker build -t hud-updater /path/to/hudserver/updater
docker run -d --name hud-updater --restart unless-stopped \
  -p 8085:8085 -e TZ=Europe/Berlin -e ADMIN_PASSWORD=CHANGE-ME \
  -v /path/to/hudserver/data:/data hud-updater
```

## Set up the app

1. In the app open **menu → Server** and enter the server address (at home e.g. `http://<server-ip>:8085`, on the go the https name, see below).
2. Create a profile: a name and the **invitation code** (shown in the admin page under **Profiles**).
3. Load regions: **Menu → Map → Load regions**.

## Settings (environment variables)

| Name | Meaning | Default |
|---|---|---|
| `ADMIN_PASSWORD` | Admin password. Without it the admin page is read-only. | empty |
| `PORT` | Server port inside the container | 8085 |
| `TZ` | Time zone | UTC |
| `DATA_DIR` | Data folder inside the container | `/data` |
| `NPM_IPS` | IP address(es) of your reverse proxy, comma separated. Requests from there count as "from outside". | empty |
| `NEED_DAYS` | Regions a phone reported within this time are kept up to date | 90 |
| `UPDATE_EVERY_DAYS` | How often the automatic run starts | 30 |
| `REGIONS` | Which regions are ticked at first (`ALL` = the four built-in countries; regions from the Europe list are never ticked by themselves) | ALL |
| `INVITE_CODE` | Invitation code for new profiles at first start | random |

Everything is stored in the `data` folder (maps, routes, profiles, settings). Back it up when you move the server.

## Access from outside (optional)

To let the app fetch things on the go (road works, camera list, updates, tours) you need a **reverse proxy with https** (e.g. Nginx Proxy Manager) and a host name (e.g. from a dynamic DNS service).

- The server recognises requests through the proxy by the forwarding headers (`X-Forwarded-For`, `X-Real-IP`). Also set `NPM_IPS` to your proxy's IP.
- **From outside there is never:** the admin page, creating new profiles. Maps, road data and places are only served from outside if you switch on the **load maps from outside** option in the admin page. Profile sign-in locks after 5 wrong tries for 15 minutes.
- For file downloads the server sends `X-Accel-Buffering: no`, so the proxy does not buffer and the admin page shows the real speed.

A sample "custom nginx configuration" for the proxy host is in `README.de.md` (adjust the host name).

## Road works and closures (optional, Bavaria)

The data comes from the **Mobilithek** (DATEX II). You need an account there, a subscription for "Baustellen Bayern" and a certificate. Put the files **only on your server**, never into the project and never on GitHub: `data/mobilithek/client.p12` and `data/mobilithek/config.json` with `{"abo": "<subscription id>", "pass": "<certificate password>"}`. Without them the server uses a test file.

## Security in short

Change `ADMIN_PASSWORD`; keep the admin page off the internet; expose only the proxy host name (https); back up `data` regularly; never put certificates or passwords into the project.

## Data sources

Maps and road data: © OpenStreetMap contributors (ODbL), map building by Protomaps, regional extracts from Geofabrik. Routing data: BRouter segments from brouter.de. Road works: Mobilithek. The browser trip planner loads parts and tiles from third parties (among others unpkg.com, OpenFreeMap, AWS terrain).

See `CHANGELOG.md` for the version history (German).
