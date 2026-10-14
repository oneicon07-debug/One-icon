package com.minipaint.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mini Paint: the web app (assets/index.html) running inside a full-screen WebView.
 *
 * - Everything is served from the app itself over https://appassets.androidplatform.net/ so it works
 *   offline and the browser storage (your drawings, settings) keeps a stable, private home.
 * - "Save / Export" buttons write real files into Downloads/MiniPaint.
 * - Import buttons open the normal Android file picker.
 * - Links to outside websites open in your browser.
 * - The Android Back button walks back through the app's own screens first.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME_URL = "https://" + HOST + "/index.html";
    private static final int REQ_FILE = 4101;

    /** Asks the page to close whatever is open (pop-up, panel, editor). Returns 1 if it handled Back. */
    private static final String BACK_JS =
            "(function(){try{"
                    + "if(window.__mpLegalClose&&window.__mpLegalClose())return 1;"
                    + "if(window.mpBack&&window.mpBack())return 1;"
                    + "}catch(e){}return 0;})()";

    /**
     * Runs inside the page. Android's WebView cannot save "blob:" downloads by itself, so any
     * <a download> click is intercepted here and the file is streamed to the app in small pieces.
     */
    private static final String DOWNLOAD_HOOK_JS =
            "(function(){"
                    + "if(window.__mpDl)return;window.__mpDl=1;"
                    + "function send(a){"
                    + "var href=a.href,fn=a.getAttribute('download')||('MiniPaint_'+Date.now());"
                    + "fetch(href).then(function(r){return r.blob();}).then(function(b){"
                    + "var id=String(Date.now())+Math.random().toString(36).slice(2);"
                    + "MPNative.begin(id,fn,b.type||'application/octet-stream');"
                    + "var CH=196608,pos=0;"
                    + "function next(){"
                    + "if(pos>=b.size){MPNative.end(id);return;}"
                    + "var fr=new FileReader();"
                    + "fr.onload=function(){var s=String(fr.result);MPNative.chunk(id,s.substring(s.indexOf(',')+1));pos+=CH;next();};"
                    + "fr.onerror=function(){MPNative.fail(fn);};"
                    + "fr.readAsDataURL(b.slice(pos,pos+CH));"
                    + "}"
                    + "next();"
                    + "}).catch(function(){MPNative.fail(fn);});"
                    + "}"
                    + "var oc=HTMLAnchorElement.prototype.click;"
                    + "HTMLAnchorElement.prototype.click=function(){"
                    + "if(this.hasAttribute('download')&&/^(blob:|data:)/.test(this.href)){send(this);return;}"
                    + "return oc.apply(this,arguments);};"
                    + "document.addEventListener('click',function(e){"
                    + "var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;"
                    + "if(a&&/^(blob:|data:)/.test(a.href)){e.preventDefault();send(a);}"
                    + "},true);"
                    + "})();";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, Sink> sinks = new HashMap<String, Sink>();

    private FrameLayout root;
    private WebView web;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> filePathCallback;
    private long lastBackPress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(0xFFDDE1EA);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        web.addJavascriptInterface(new Bridge(), "MPNative");
        web.setWebViewClient(new AppClient());
        web.setWebChromeClient(new AppChrome());

        // The app saves your work on its own, so a fresh start is always safe.
        web.loadUrl(HOME_URL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            root.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ Back button

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
            return;
        }
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript(BACK_JS, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if ("1".equals(value)) return; // the page closed something
                long now = SystemClock.uptimeMillis();
                if (now - lastBackPress < 2000) {
                    finish();
                } else {
                    lastBackPress = now;
                    Toast.makeText(MainActivity.this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ------------------------------------------------------------------ File picker result

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (filePathCallback != null) {
                Uri[] result = null;
                if (resultCode == RESULT_OK) {
                    result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                }
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ------------------------------------------------------------------ Helpers

    private void openExternal(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (scheme == null) return;
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https") || scheme.equals("mailto") || scheme.equals("tel"))) {
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("No app found to open this link");
        }
    }

    private void toast(final String msg) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showCustomView(View view, WebChromeClient.CustomViewCallback cb) {
        if (customView != null) {
            cb.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = cb;
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setVisibility(View.GONE);
        setImmersive(true);
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        setImmersive(false);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    @SuppressWarnings("deprecation")
    private void setImmersive(boolean on) {
        View decor = getWindow().getDecorView();
        if (on) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private static String mimeFor(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        int dot = p.lastIndexOf('.');
        String ext = dot >= 0 ? p.substring(dot + 1) : "";
        if (ext.equals("html") || ext.equals("htm")) return "text/html";
        if (ext.equals("js") || ext.equals("mjs")) return "text/javascript";
        if (ext.equals("css")) return "text/css";
        if (ext.equals("json") || ext.equals("webmanifest")) return "application/json";
        if (ext.equals("svg")) return "image/svg+xml";
        if (ext.equals("wasm")) return "application/wasm";
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m != null ? m : "application/octet-stream";
    }

    private static boolean isText(String mime) {
        return mime.startsWith("text/") || mime.equals("application/json") || mime.equals("image/svg+xml");
    }

    // ------------------------------------------------------------------ WebViewClient

    private class AppClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (u == null || !"https".equals(u.getScheme()) || !HOST.equals(u.getHost())) {
                return null; // normal network request (online fonts, pasted image links ...)
            }
            String path = u.getPath();
            if (path == null || path.length() <= 1) path = "/index.html";
            path = path.substring(1);
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Access-Control-Allow-Origin", "https://" + HOST);
            if (path.contains("..")) {
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", headers,
                        new ByteArrayInputStream(new byte[0]));
            }
            try {
                InputStream in = getAssets().open(path);
                String mime = mimeFor(path);
                return new WebResourceResponse(mime, isText(mime) ? "utf-8" : null, 200, "OK", headers, in);
            } catch (IOException e) {
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", headers,
                        new ByteArrayInputStream(new byte[0]));
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (u != null && "https".equals(u.getScheme()) && HOST.equals(u.getHost())) {
                return false; // our own pages
            }
            openExternal(u);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.evaluateJavascript(DOWNLOAD_HOOK_JS, null);
        }
    }

    // ------------------------------------------------------------------ WebChromeClient

    private class AppChrome extends WebChromeClient {

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;
            try {
                startActivityForResult(params.createIntent(), REQ_FILE);
            } catch (Exception e) {
                filePathCallback = null;
                toast("Could not open the file picker");
                return false;
            }
            return true;
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            // window.open(...) and <a target="_blank"> : send the link to the phone's browser
            final WebView tmp = new WebView(MainActivity.this);
            tmp.setWebViewClient(new WebViewClient() {
                private boolean done = false;

                private void go(Uri uri) {
                    if (done) return;
                    done = true;
                    openExternal(uri);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            tmp.stopLoading();
                            tmp.destroy();
                        }
                    });
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    go(r.getUrl());
                    return true;
                }

                @Override
                public void onPageStarted(WebView v, String url, Bitmap favicon) {
                    if (url != null && !url.equals("about:blank")) go(Uri.parse(url));
                }
            });
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(tmp);
            resultMsg.sendToTarget();
            return true;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            showCustomView(view, callback);
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }
    }

    // ------------------------------------------------------------------ Saving files (called from the page)

    private static class Sink {
        OutputStream out;
        Uri uri;          // Android 10+: entry in the Downloads collection
        String shownName; // name the file really got
        String where;     // text shown to the user
    }

    public class Bridge {

        @JavascriptInterface
        public void begin(String id, String name, String mime) {
            try {
                Sink sink = openSink(cleanName(name), mime);
                synchronized (sinks) {
                    sinks.put(id, sink);
                }
            } catch (Exception e) {
                toast("Could not save " + name);
            }
        }

        @JavascriptInterface
        public void chunk(String id, String base64) {
            Sink sink;
            synchronized (sinks) {
                sink = sinks.get(id);
            }
            if (sink == null) return;
            try {
                sink.out.write(Base64.decode(base64, Base64.DEFAULT));
            } catch (Exception e) {
                synchronized (sinks) {
                    sinks.remove(id);
                }
                closeQuietly(sink);
                toast("Saving failed (not enough storage?)");
            }
        }

        @JavascriptInterface
        public void end(String id) {
            Sink sink;
            synchronized (sinks) {
                sink = sinks.remove(id);
            }
            if (sink == null) return;
            try {
                sink.out.flush();
                sink.out.close();
                if (sink.uri != null && Build.VERSION.SDK_INT >= 29) {
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(sink.uri, done, null, null);
                }
                toast("Saved: " + sink.where);
            } catch (Exception e) {
                toast("Saving failed");
            }
        }

        @JavascriptInterface
        public void fail(String name) {
            toast("Could not save " + name);
        }
    }

    private static void closeQuietly(Sink s) {
        try {
            if (s.out != null) s.out.close();
        } catch (IOException ignored) {
        }
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (n.isEmpty()) n = "MiniPaint_" + System.currentTimeMillis();
        return n;
    }

    private Sink openSink(String name, String mime) throws IOException {
        if (mime == null || mime.isEmpty() || mime.equals("application/octet-stream")) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                String g = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
                if (g != null) mime = g;
            }
        }
        if (mime == null || mime.isEmpty()) mime = "application/octet-stream";

        Sink sink = new Sink();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver resolver = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MiniPaint");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("MediaStore insert failed");
            sink.uri = uri;
            sink.out = resolver.openOutputStream(uri);
            if (sink.out == null) throw new IOException("openOutputStream failed");
            sink.shownName = name;
            sink.where = "Downloads/MiniPaint/" + name;
        } else {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MiniPaint");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs failed");
            File f = new File(dir, name);
            int n = 1;
            while (f.exists()) {
                int dot = name.lastIndexOf('.');
                String base = dot > 0 ? name.substring(0, dot) : name;
                String ext = dot > 0 ? name.substring(dot) : "";
                f = new File(dir, base + " (" + (n++) + ")" + ext);
            }
            sink.out = new FileOutputStream(f);
            sink.shownName = f.getName();
            sink.where = f.getAbsolutePath();
        }
        return sink;
    }
}
