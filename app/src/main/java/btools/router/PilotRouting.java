/**
 * Schmale Schnittstelle von HUDPilot zu BRouter (MIT-Lizenz, siehe NOTICE.md).
 * Liegt im Paket btools.router, weil BRouter die Abbiege-Anweisungen nur paketintern herausgibt.
 * Liefert reine Zahlenfelder, damit der Kotlin-Teil nichts von BRouter-Typen wissen muss.
 */
package btools.router;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import btools.mapaccess.MatchedWaypoint;

public final class PilotRouting {

  private PilotRouting() {
  }

  /** Ergebnis einer Berechnung. error != null: es gibt keine Route. */
  public static final class Result {
    public String error;
    /** Routenpunkte in Grad. */
    public double[] lats = new double[0];
    public double[] lons = new double[0];
    /** Höhe je Punkt in Metern. */
    public float[] ele = new float[0];
    public int distanceM;
    public int ascendM;
    public int seconds;
    /** Anweisungen: Punktindex, BRouter-Befehl (siehe PilotHint-Konstanten in Kotlin), Ausfahrt, Abstand zur nächsten. */
    public int[] hintIndex = new int[0];
    public int[] hintCmd = new int[0];
    public int[] hintExit = new int[0];
    public int[] hintDistToNext = new int[0];
    /** Spuranzahl der Straße an der Anweisung (aus dem Tag "lanes" der Routendaten), 0 = unbekannt. */
    public int[] hintLanes = new int[0];
    /** Punktindex der übergebenen Wegpunkte (gleiche Reihenfolge). */
    public int[] waypointIndex = new int[0];
  }

  /**
   * Route berechnen.
   *
   * @param segmentDir Ordner mit den .rd5-Dateien
   * @param profilePath Pfad der .brf-Profildatei (lookups.dat muss im selben Ordner liegen)
   * @param lats Breitengrade der Wegpunkte (mindestens 2)
   * @param lons Längengrade
   * @param visit true = Punkt wird sicher angefahren (Zwischenziel), false = Formpunkt (Route verläuft in der Nähe)
   * @param maxMs Zeitgrenze in Millisekunden
   */
  /** Spuranzahl aus den Wegtags am Punkt (oder dem davor); 0 = unbekannt. */
  private static int lanesAt(OsmTrack track, int idx) {
    for (int k = idx; k >= Math.max(0, idx - 1); k--) {
      if (k < 0 || k >= track.nodes.size()) continue;
      OsmPathElement e = track.nodes.get(k);
      String w = e.message == null ? null : e.message.wayKeyValues;
      if (w == null) continue;
      int a = w.indexOf("lanes=");
      while (a >= 0) {
        boolean start = a == 0 || w.charAt(a - 1) == ' ';
        if (start) {
          int b = a + 6, c = b;
          while (c < w.length() && Character.isDigit(w.charAt(c))) c++;
          if (c > b) return Integer.parseInt(w.substring(b, c));
        }
        a = w.indexOf("lanes=", a + 1);
      }
    }
    return 0;
  }

  public static Result route(File segmentDir, String profilePath, double[] lats, double[] lons,
                             boolean[] visit, long maxMs) {
    return route(segmentDir, profilePath, lats, lons, visit, maxMs, -1);
  }

  /** Wie oben; startDirection in Grad (0 = Nord), 0 bis 359 = die Route soll in diese Fahrtrichtung beginnen, -1 = egal. */
  public static Result route(File segmentDir, String profilePath, double[] lats, double[] lons,
                             boolean[] visit, long maxMs, int startDirection) {
    return route(segmentDir, profilePath, lats, lons, visit, maxMs, startDirection, null);
  }

  /**
   * Wie oben; nogoLines = Sperrlinien, jede als {lat, lon, lat, lon, ...}. Strecken darauf meidet BRouter vollständig
   * (nur wenn nicht Start oder Ziel selbst darin liegen).
   */
  public static Result route(File segmentDir, String profilePath, double[] lats, double[] lons,
                             boolean[] visit, long maxMs, int startDirection, List<double[]> nogoLines) {
    Result res = new Result();
    try {
      if (lats == null || lons == null || lats.length < 2 || lats.length != lons.length) {
        res.error = "need at least two points";
        return res;
      }
      List<OsmNodeNamed> wps = new ArrayList<>();
      for (int i = 0; i < lats.length; i++) {
        OsmNodeNamed n = new OsmNodeNamed();
        n.name = "via" + i;
        n.ilon = (int) ((lons[i] + 180.) * 1000000. + 0.5);
        n.ilat = (int) ((lats[i] + 90.) * 1000000. + 0.5);
        boolean v = visit != null && i < visit.length && visit[i];
        n.wpttype = v ? MatchedWaypoint.WAYPOINT_TYPE_MEETING : MatchedWaypoint.WAYPOINT_TYPE_SHAPING;
        wps.add(n);
      }
      wps.get(0).name = "from";
      wps.get(0).wpttype = MatchedWaypoint.WAYPOINT_TYPE_SHAPING;
      wps.get(wps.size() - 1).name = "to";
      wps.get(wps.size() - 1).wpttype = MatchedWaypoint.WAYPOINT_TYPE_SHAPING;

      RoutingContext rc = new RoutingContext();
      rc.localFunction = profilePath;
      // Ohne diesen Wert erzeugt BRouter keine Abbiege-Anweisungen (das Profil darf ihn auf "auto" lassen)
      rc.turnInstructionMode = 3;
      if (startDirection >= 0 && startDirection < 360) {
        rc.startDirection = Integer.valueOf(startDirection);
        rc.forceUseStartDirection = true;
      }

      if (nogoLines != null && !nogoLines.isEmpty()) {
        List<OsmNodeNamed> nogos = new ArrayList<>();
        for (double[] line : nogoLines) {
          if (line == null || line.length < 4) continue;
          OsmNogoPolygon poly = new OsmNogoPolygon(false);
          for (int k = 0; k + 1 < line.length; k += 2) {
            poly.addVertex((int) ((line[k + 1] + 180.) * 1000000. + 0.5), (int) ((line[k] + 90.) * 1000000. + 0.5));
          }
          poly.nogoWeight = Double.NaN;   // NaN = Strecke ganz meiden
          poly.calcBoundingCircle();
          nogos.add(poly);
        }
        if (!nogos.isEmpty()) rc.nogopoints = nogos;
      }

      RoutingEngine engine = new RoutingEngine(null, null, segmentDir, wps, rc, RoutingEngine.BROUTER_ENGINEMODE_ROUTING);
      engine.quite = true;
      engine.doRun(maxMs);

      if (engine.getErrorMessage() != null) {
        res.error = engine.getErrorMessage();
        return res;
      }
      OsmTrack track = engine.getFoundTrack();
      if (track == null || track.nodes == null || track.nodes.isEmpty()) {
        res.error = "no track found";
        return res;
      }
      int n = track.nodes.size();
      res.lats = new double[n];
      res.lons = new double[n];
      res.ele = new float[n];
      for (int i = 0; i < n; i++) {
        OsmPathElement e = track.nodes.get(i);
        res.lons[i] = e.getILon() / 1000000. - 180.;
        res.lats[i] = e.getILat() / 1000000. - 90.;
        res.ele[i] = e.getSElev() / 4.f;
      }
      res.distanceM = track.distance;
      res.ascendM = track.ascend;
      res.seconds = track.getTotalSeconds();

      if (track.voiceHints != null) {
        List<VoiceHint> list = track.voiceHints.list;
        int m = list.size();
        res.hintIndex = new int[m];
        res.hintCmd = new int[m];
        res.hintExit = new int[m];
        res.hintDistToNext = new int[m];
        res.hintLanes = new int[m];
        for (int i = 0; i < m; i++) {
          VoiceHint h = list.get(i);
          res.hintIndex[i] = h.indexInTrack;
          res.hintCmd[i] = h.cmd;
          res.hintExit[i] = h.getExitNumber();
          res.hintDistToNext[i] = (int) h.distanceToNext;
          res.hintLanes[i] = lanesAt(track, h.indexInTrack);
        }
      }
      if (track.matchedWaypoints != null) {
        int k = track.matchedWaypoints.size();
        res.waypointIndex = new int[k];
        for (int i = 0; i < k; i++) {
          res.waypointIndex[i] = track.matchedWaypoints.get(i).indexInTrack;
        }
      }
      return res;
    } catch (Throwable t) {
      res.error = t.getMessage() != null ? t.getMessage() : t.toString();
      return res;
    }
  }
}
