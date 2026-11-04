# HUDPilot

[Deutsche Version](README.de.md)

**HUDPilot** is a motorcycle navigation app for Android with its own offline navigation, a vector map and direct control of a Bluetooth LE head-up display (the *Tilsberk / DVision* HUD for motorcycle helmets). No vendor account and no vendor app needed. Maps, road data and tours come, if you like, from your **own server** (HUDPilot Updater, folder `hudserver` in the project or as a separate project).

An unofficial hobby app for private use.

> **Not affiliated** with Tilsberk, Digades, Moket, WhatsApp or Spotify. All product names and trademarks belong to their owners and are used only to describe compatibility. **No warranty.** The display can fail or be wrong, and navigation hints can be wrong. Do not rely on it while riding. Road signs and traffic rules always take priority.

## What it does

**Navigation and map**
- Offline vector map (MapLibre, Protomaps) with 3D buildings, day and night style, search for places and addresses, place search (fuel, food, hotels, parking) from the downloaded data
- Routing with BRouter (offline), several motorcycle profiles (country roads, twisty, fast, fastest with motorway), "avoid" for motorways, ferries, tolls and unpaved roads, waypoints, route preview, re-routing when you leave the route
- Voice guidance with the phone's installed voice, lane hints, info card with turn arrow, arrival and remaining distance, arrival flag
- Portrait and landscape, north up / heading up / 3D, zoom follows speed, simulated rides for testing
- **Road works and closures** (Bavaria, from the Mobilithek, via your own server): shown on the map, with a note along the route and, if you like, **avoidance of closed roads**

**HUD (head-up display)**
- Speed, speed limit, turn arrow and distance, remaining distance and time, arrival, compass, lane hints
- **Incoming calls, messages and the music track on the HUD** (see "Messages on the HUD" below)
- Six displays: Navigator, Minimalist, Explorer, City, and the free ones Guide (arrow plus two lines of your choice) and Cruiser (four lines of your choice); HUD brightness automatic or in three steps; an element test to find out what each code shows on your HUD
- Runs as a foreground service, reconnects by itself and shows nothing rather than something wrong

**Warnings**
- Speed limit from OpenStreetMap (imported or online), overspeed tone (with HUD, navigation or a running recording)
- Speed camera warning (off; a note appears when switching on: not allowed for drivers in Germany and some other countries), hint tones for level crossings, pedestrian crossings, traffic calming and sharp bends

**Rides and tours**
- Record rides (GPX and CSV), analysis with speed, elevation, lean angle and acceleration, season overview
- Plan tours in the browser (server) and ride them in the app, favourites, profiles (own name and code on the server), back up settings and fetch them on another phone
- Status page with connections, server, road works and a storage overview (maps, routes, road data, rides)

**Language:** German and English (follows the phone language or can be switched in the app).

## Requirements
- Android 8.0 or newer
- For the HUD: Bluetooth LE and a paired Tilsberk/DVision HUD (the app also works without a HUD as plain navigation)
- For maps and road data: your own server (HUDPilot Updater, see `hudserver/README.md`)
- Notification access for the app (needed for calls, messages and the music track on the HUD)

Tested by the author with a HUD on a Xiaomi Mi 10T (LineageOS) and a Nubia Z80 Ultra (stock Android). Other devices, firmware versions and Android versions are untested.

## Without your own server: adding a map by hand
The server is optional. Without it the app still navigates, but you bring the map yourself, and you lose the convenience: no region download, no automatic road data (speed limits, speed cameras, level crossings), no road works.

1. Get a **Protomaps map file (`.pmtiles`)** for your area. The HUDPilot Updater builds its maps like this, with the free `pmtiles` command-line tool and the daily planet build from `build.protomaps.com` (see `maps.protomaps.com/builds`):
   ```
   pmtiles extract <planet-file-or-URL> map.pmtiles --bbox=WEST,SOUTH,EAST,NORTH --maxzoom=15
   ```
   (`--bbox` is the rectangle you want, in degrees, e.g. `11.0,47.5,13.0,49.0`.)
2. Copy `map.pmtiles` to the phone (USB cable, cloud, download).
3. In the app: **Menu → Map → "Choose own map (.pmtiles) …"** and pick the file. Copying a big file takes a while. The map is used as soon as the message "Map loaded." appears.
4. Routing data (`.rd5`) is fetched on tap from the BRouter directory (brouter.de) when you first calculate a route there, without sending your position.

Good to know:
- Only Protomaps maps fit the built-in map style. Other map formats do not work.
- The style is built for the map files the server downloads today. It is not guaranteed that every Protomaps file looks right.
- A map picked this way replaces the previous one. Regions loaded from a server are managed separately.
- This path has **not been tested on a fresh phone without any server**.
- Besides maps, you can open your own routes: **Menu → "Open GPX file …"**.

## Messages on the HUD
The app reads the phone's notifications (only with your permission, see *First setup*) and shows short texts in the HUD's message field:

| Source | What appears | Notes |
|---|---|---|
| **Phone calls** | Name of the caller while it rings | Up to 2 minutes or until the call ends; takes priority over everything except a Moket help request |
| **WhatsApp** (also WhatsApp Business) | Name of the sender (not the message text) | Shown for 5 seconds; group summaries and ongoing notifications are ignored |
| **Moket** (biker app) | Help requests with kind and distance (e.g. "HILFE Reifen 3km"), "Biker gekreuzt" (met a biker), answers to your own help request, chat and group names | Order of priority: help request, call, help message, chats/WhatsApp, encounter. Recognises the **German** Moket texts only |
| **Music** (Spotify, YouTube Music and other players) | "Artist - Title" when a new track starts | Works with any player that shows a media notification; pause/resume does not count as a new track |

Limits of the HUD itself:
- Messages appear only in the **Explorer** and **City** displays. The other displays have no room for them.
- Texts are cut to 19 characters and shown in plain ASCII (umlauts and emoji are simplified). The HUD does not scroll.
- Spotify, WhatsApp and Moket are named only to describe what works; this project has no connection to them.

## Installation

**Ready-made APK:** download the latest `.apk` from the project's Releases page, open it on the phone and install it (allow "install from this source").

**Build it yourself:** open the folder in Android Studio (JDK 17, Gradle 8.13 / AGP 8.9.2) and choose *Build → Build APK(s)*, or:

```
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. Tests (no phone needed): `./gradlew test`.

## First setup
1. Switch the HUD on and pair it in the Android Bluetooth settings (only if you use a HUD).
2. Open the app and allow the permissions. On the status page grant **notification access** if calls and messages should appear on the HUD.
3. **Menu → Server:** enter your server address (at home and on the go), create a profile (name and invitation code from the server admin page).
4. **Menu → Map → Load regions:** choose and load regions (best on home Wi-Fi).
5. Search for a destination or long-press the map, look at the route, press **START**.

## Troubleshooting (installing the APK)
- **"App blocked to protect your device" (Google Play Protect):** tap *More details*, then *Install anyway*. Play Protect does not know the signing key of this open-source app.
- **Notification access greyed out ("Restricted setting"):** since Android 13 this is blocked for apps installed from a file or browser. Open *Settings → Apps → HUDPilot*, tap the **⋮ menu** at the top right, choose **Allow restricted settings**, then grant the access.
- **No map:** without your own server and a loaded region the map shows only a grey background. Set up the server and load a region (see above).

## Privacy
- Notifications (calls, messages, music track) are only read to show them on the HUD. Nothing is stored or sent.
- If you run your own server (optional), the app sends it a random device ID and the device name (e.g. "Google Pixel 7") so the server's admin page can show which phone is downloading. Never sent to third parties.
- Without imported road data the approximate position is sent to an Overpass server (OpenStreetMap) for street names and speed limits. With the *Offline data* switch nothing is sent. Routing data comes from your server or, on request, from the BRouter directory, without your location.
- The app remembers your last position on the phone only (to centre the map right after opening). It is never sent anywhere.
- Rides, tours, favourites and settings stay on your phone and, if you switch it on, on your own server. The log stays on the device. It may contain caller or sender names; check it before sharing.

## Protocol
The Bluetooth protocol of the HUD, as far as known, is in [PROTOCOL.md](PROTOCOL.md).

## Licences
MIT for the own code ([LICENSE](LICENSE)). Third-party parts and notices: [NOTICE.md](NOTICE.md). Map data © OpenStreetMap contributors (ODbL).

## Contributing
Issues and pull requests are welcome: tests with other HUD firmware, bugs, translations, more notification sources (other messengers, other languages for Moket).

Note: the package name `io.github.th3s1nc.osmandhudbridge` is a leftover from the project's beginnings (it started as a bridge for another navigation app, which is no longer used). It stays as it is, because changing it would reset the notification permission and settings on every phone.
