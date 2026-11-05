/*
 * Krishnas Spiele - startet die lokale index.html in einer Vollbild-WebView.
 * JS-Bruecke "AndroidHuelle": setVollbild(boolean), istVollbild().
 * Datei-Auswahl: <input type="file" [multiple]> oeffnet den System-Dialog
 * (Storage Access Framework, keine Speicher-Berechtigung noetig).
 * Beim Pausieren der App wird das laufende Spiel per musikKachelnPause() angehalten.
 * Stirbt der WebView-Renderer (z. B. Speichermangel), wird die WebView neu
 * aufgebaut statt die App abstuerzen zu lassen.
 * @ Joschy
 */
package de.joschy.krishnaspiele;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private static final String ASSET_URL = "file:///android_asset/";
    private static final String KEY_VOLLBILD = "vollbild";
    private static final int REQ_DATEI_AUSWAHL = 4711;
    /** Mehr Renderer-Abbrueche als das innerhalb des Zeitfensters: App sauber beenden statt Endlosschleife. */
    private static final int MAX_NEUSTARTS = 3;
    private static final long NEUSTART_FENSTER_MS = 60_000L;

    private WebView webView;
    /** Zuletzt gewaehlter Zustand; Standard beim Start: Leisten aus. */
    private volatile boolean vollbild = true;
    /** Offener Callback der Datei-Auswahl (genau einer gleichzeitig). */
    private ValueCallback<Uri[]> dateiCallback;
    private int neustarts = 0;
    private long ersterNeustart = 0L;

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        webView = erstelleWebView();
        setContentView(webView);

        if (savedInstanceState != null) {
            vollbild = savedInstanceState.getBoolean(KEY_VOLLBILD, true);
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(ASSET_URL + "index.html");
        }
        wendeVollbildAn();
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @SuppressWarnings("deprecation")
    private WebView erstelleWebView() {
        WebView wv = new WebView(this);
        wv.setBackgroundColor(0xFF060B0E);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setAllowFileAccess(true);
        // Auswahl-Dateien kommen als content://-Uris; die WebView liest sie selbst.
        s.setAllowContentAccess(true);
        // Keine Ausweitung: file://-Seiten duerfen nicht frei auf andere Dateien/Urls zugreifen.
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setTextZoom(100);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Nur lokale Asset-Seiten zulassen, alles andere blockieren.
                return !istLokal(request.getUrl().toString());
            }

            @Override
            public boolean onRenderProcessGone(final WebView view, RenderProcessGoneDetail detail) {
                // true = App laeuft weiter; die tote WebView wird ersetzt.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bauWebViewNeu(view);
                    }
                });
                return true;
            }
        });
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                return oeffneDateiAuswahl(view, callback, params);
            }
        });
        wv.addJavascriptInterface(new Huelle(), "AndroidHuelle");
        return wv;
    }

    /** Renderer weg: alte WebView verwerfen (nicht wiederverwendbar), neue aufbauen und index.html laden. */
    private void bauWebViewNeu(WebView tot) {
        if (tot != webView || isFinishing()) {
            return;
        }
        long jetzt = SystemClock.elapsedRealtime();
        if (neustarts == 0 || jetzt - ersterNeustart > NEUSTART_FENSTER_MS) {
            neustarts = 0;
            ersterNeustart = jetzt;
        }
        neustarts++;
        beendeDateiAuswahl(null);
        webView = null;
        if (neustarts <= MAX_NEUSTARTS) {
            try {
                WebView neu = erstelleWebView();
                setContentView(neu);          // loest die tote WebView aus dem Fenster
                webView = neu;
                neu.loadUrl(ASSET_URL + "index.html");
                wendeVollbildAn();
            } catch (RuntimeException e) {
                webView = null;
            }
        }
        try {
            tot.destroy();
        } catch (RuntimeException ignoriert) {
            // Bereits zerstoert - egal.
        }
        if (webView == null) {
            // Zu viele Abbrueche kurz hintereinander oder Neuaufbau fehlgeschlagen: sauber beenden.
            finish();
        }
    }

    private static boolean istLokal(String url) {
        return url != null && url.startsWith(ASSET_URL);
    }

    private boolean oeffneDateiAuswahl(WebView view, ValueCallback<Uri[]> callback,
                                       WebChromeClient.FileChooserParams params) {
        // Laufenden Dialog sauber beenden, damit kein Callback haengen bleibt.
        beendeDateiAuswahl(null);
        if (view == null || !istLokal(view.getUrl()) || params == null) {
            callback.onReceiveValue(null);
            return true;
        }
        Intent intent = params.createIntent();
        if (params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        dateiCallback = callback;
        try {
            startActivityForResult(intent, REQ_DATEI_AUSWAHL);
        } catch (ActivityNotFoundException e) {
            beendeDateiAuswahl(null);
        }
        return true;
    }

    private void beendeDateiAuswahl(Uri[] ergebnis) {
        ValueCallback<Uri[]> cb = dateiCallback;
        dateiCallback = null;
        if (cb != null) {
            cb.onReceiveValue(ergebnis);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_DATEI_AUSWAHL) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        Uri[] ergebnis = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                java.util.ArrayList<Uri> liste = new java.util.ArrayList<>();
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (u != null) {
                        liste.add(u);
                    }
                }
                if (!liste.isEmpty()) {
                    ergebnis = liste.toArray(new Uri[0]);
                }
            }
            if (ergebnis == null) {
                ergebnis = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
        }
        beendeDateiAuswahl(ergebnis);
        wendeVollbildAn();
    }

    /** Bruecke fuer index.html (nur auf der lokalen Asset-Seite wirksam). */
    private class Huelle {
        @JavascriptInterface
        public void setVollbild(final boolean an) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (webView == null || !istLokal(webView.getUrl())) {
                        return;
                    }
                    vollbild = an;
                    wendeVollbildAn();
                }
            });
        }

        @JavascriptInterface
        public boolean istVollbild() {
            return vollbild;
        }
    }

    @SuppressWarnings("deprecation")
    private void wendeVollbildAn() {
        if (vollbild) {
            hideSystemUi();
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            wendeVollbildAn();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_VOLLBILD, vollbild);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            // Laufendes Spiel anhalten, bevor die WebView pausiert wird.
            webView.evaluateJavascript("window.musikKachelnPause&&musikKachelnPause()", null);
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        wendeVollbildAn();
    }

    @Override
    protected void onDestroy() {
        beendeDateiAuswahl(null);
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
