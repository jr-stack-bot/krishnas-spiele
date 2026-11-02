/*
 * Krishnas Spiele - startet die lokale index.html in einer Vollbild-WebView.
 * JS-Bruecke "AndroidHuelle": setVollbild(boolean), istVollbild().
 * @ Joschy
 */
package de.joschy.krishnaspiele;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private static final String ASSET_URL = "file:///android_asset/";
    private static final String KEY_VOLLBILD = "vollbild";

    private WebView webView;
    /** Zuletzt gewaehlter Zustand; Standard beim Start: Leisten aus. */
    private volatile boolean vollbild = true;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        webView = new WebView(this);
        webView.setBackgroundColor(0xFFF4F1EA);
        setContentView(webView);

        WebSettings s = webView.getSettings();
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
        s.setTextZoom(100);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Nur lokale Asset-Seiten zulassen, alles andere blockieren.
                return !istLokal(request.getUrl().toString());
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Huelle(), "AndroidHuelle");

        if (savedInstanceState != null) {
            vollbild = savedInstanceState.getBoolean(KEY_VOLLBILD, true);
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(ASSET_URL + "index.html");
        }
        wendeVollbildAn();
    }

    private static boolean istLokal(String url) {
        return url != null && url.startsWith(ASSET_URL);
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
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
