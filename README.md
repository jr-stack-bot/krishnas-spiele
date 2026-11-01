# Krishnas Spiele

Ruhige, reizarme Musik- und Tipp-Spiele für Kinder – eine Tablet-Web-App, verpackt als Android-APK.

Die App startet eine lokale `index.html` im Vollbild (Querformat, Display bleibt an, kein Zoom). Läuft ab Android 8 (API 26).

## Spiele

Fünf Modi, alle bewusst reizarm gestaltet (ruhige Farben, sanfte Klänge, kein Zeitdruck):

- **Langsame Kacheln** – Kacheln fallen langsam, das Tempo ist frei wählbar
- **Warte-Modus** – eine Kachel leuchtet und wartet, bis sie angetippt wird
- **Muster merken** – zuhören, anschauen, nachspielen
- **Beruhigende Spiele** – Blasen und Farben, einfach tippen
- **Kuthu-Trommeln** – weiche Trommeln, Rhythmus nachspielen

## APK herunterladen und installieren

1. Auf dem Tablet die Seite **Releases** dieses Repos öffnen (rechts auf der Repo-Startseite oder `…/releases/latest`).
2. Die Datei **`krishnas-spiele.apk`** herunterladen.
3. Die heruntergeladene Datei antippen. Android fragt beim ersten Mal, ob der Browser/Dateimanager **unbekannte Apps installieren** darf → in den Einstellungen erlauben (*Einstellungen → Apps → [Browser] → Unbekannte Apps installieren*).
4. Installieren – fertig. Neue Versionen lassen sich einfach drüberinstallieren.

## Aufbau

- `app/src/main/assets/index.html` – die Web-App mit allen Spielen
- `app/src/main/java/.../MainActivity.java` – Vollbild-WebView
- `.github/workflows/build-apk.yml` – baut bei jedem Push auf `main` die Debug-APK und veröffentlicht sie als Release `latest`

Lokal bauen: `gradle assembleDebug` (Gradle 8.x, JDK 17, Android SDK).

Hinweis: `app/debug.keystore` ist ein gewöhnlicher Android-Debug-Schlüssel mit Standardpasswörtern, damit Updates dieselbe Signatur haben. Er ist kein Geheimnis.

---

@ Joschy · Lizenz: MIT (siehe `LICENSE`)
