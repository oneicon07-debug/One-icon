package com.minipaint.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
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
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Mini Paint - runs the original Mini Paint web page (assets/index.html) inside a full-screen
 * WebView, completely offline. The page is served from a virtual https origin so that storage
 * (autosave / gallery / challenges) and the clipboard work like in a normal browser.
 *
 * Native helpers added around the page:
 *  - file picker for the page's image / video / audio / brush imports,
 *  - "Save image": the page asks window.claude.use('downloads').save(...); this app provides that
 *    API and stores the PNG in Pictures/Mini Paint,
 *  - external links (Pinterest) open in the phone's browser,
 *  - camera permission for the page's QR scanner,
 *  - bundled JSZip / jsQR served in place of the cdnjs copies, so those features work offline.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String ORIGIN = "https://" + HOST;
    private static final String START_URL = ORIGIN + "/assets/index.html";
    private static final int REQ_FILE = 1001;
    private static final int REQ_STORAGE = 1002;
    private static final int REQ_CAMERA = 1003;
    private static final String CDN_HOST = "cdnjs.cloudflare.com";

    /** Gives the page the same "downloads" API it already knows how to use. */
    private static final String SHIM =
            "(function(){"
          + "if(window.claude&&window.claude.__mp)return;"
          + "var B=window.MiniPaintNative;if(!B)return;"
          + "async function save(o){"
          + "var blob=o.data;if(!(blob instanceof Blob))blob=new Blob([blob]);"
          + "var name=o.filename||'file';var mime=blob.type||'application/octet-stream';"
          + "if(!B.begin(name,mime))throw new Error('cannot create file');"
          + "try{"
          + "var CH=786432;"
          + "for(var off=0;off<blob.size;off+=CH){"
          + "var u=new Uint8Array(await blob.slice(off,off+CH).arrayBuffer());"
          + "var s='';for(var i=0;i<u.length;i+=32768)s+=String.fromCharCode.apply(null,u.subarray(i,i+32768));"
          + "if(!B.chunk(btoa(s)))throw new Error('write failed');"
          + "}"
          + "var r=String(B.end());if(r.indexOf('ok')!==0)throw new Error(r);"
          + "return{ok:true,path:r.substring(3)};"
          + "}catch(e){B.abort();throw e;}"
          + "}"
          + "window.claude={__mp:1,use:function(n){return Promise.resolve(n==='downloads'?{save:save}:null);}};"
          // <a download href="blob:..."> clicks (used by the page as a fallback) do nothing inside a
          // WebView, so send those files through the same native saver.
          + "try{var AP=HTMLAnchorElement.prototype,oc=AP.click;"
          + "AP.click=function(){"
          + "var h=this.href||'';"
          + "if(this.hasAttribute('download')&&h.indexOf('blob:')===0){"
          + "var nm=this.getAttribute('download')||'file';"
          + "fetch(h).then(function(r){return r.blob();})"
          + ".then(function(b){return save({data:b,filename:nm});})"
          + ".catch(function(){});"
          + "return;}"
          + "return oc.apply(this,arguments);};}catch(e){}"
          + "})();";

    private WebView web;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingCameraRequest;
    private long lastBack;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // ------------------------------------------------------------------ lifecycle

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (!night) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }

        assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(getColor(R.color.page_bg));
        setContentView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebView.setWebContentsDebuggingEnabled(false);
        configureSettings(web.getSettings());

        web.addJavascriptInterface(new Bridge(), "MiniPaintNative");
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, SHIM, Collections.singleton(ORIGIN));
        }
        web.setWebViewClient(new PageClient());
        web.setWebChromeClient(new PageChrome());

        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }

        web.loadUrl(START_URL);
    }

    @SuppressWarnings("deprecation")
    private void configureSettings(WebSettings s) {
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        s.setTextZoom(100);
        // Never let Android auto-darken the page; it has its own light/dark theme.
        if (Build.VERSION.SDK_INT >= 29 && Build.VERSION.SDK_INT < 33) {
            s.setForceDark(WebSettings.FORCE_DARK_OFF);
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, false);
        }
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
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (web != null) {
            ((ViewGroup) web.getParent()).removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    /** First let the page close its Privacy Policy / Terms of Use pop-up, if one is open. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript(
                "(function(){try{return !!(window.__mpLegalClose&&window.__mpLegalClose())}catch(e){return false}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String closed) {
                        if (!"true".equals(closed)) {
                            handleBack();
                        }
                    }
                });
    }

    @SuppressWarnings("deprecation")
    private void handleBack() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastBack < 2000) {
            super.onBackPressed();
        } else {
            lastBack = now;
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (filePathCallback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_CAMERA) {
            PermissionRequest r = pendingCameraRequest;
            pendingCameraRequest = null;
            if (r != null) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    r.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    r.deny();
                }
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /** Camera access for the page's QR scanner (video only, only for the app's own page). */
    private void handlePagePermission(PermissionRequest request) {
        boolean ownPage = request.getOrigin() != null && HOST.equals(request.getOrigin().getHost());
        boolean wantsCamera = false;
        boolean wantsOther = false;
        for (String res : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) {
                wantsCamera = true;
            } else {
                wantsOther = true;
            }
        }
        if (!ownPage || !wantsCamera || wantsOther) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            return;
        }
        if (pendingCameraRequest != null) {
            pendingCameraRequest.deny();
        }
        pendingCameraRequest = request;
        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    /**
     * The page loads JSZip and jsQR from cdnjs. If copies are bundled in assets/ (the build adds
     * them), serve those so zip export/import and the QR scanner also work with no internet.
     * Anything missing simply falls through to the normal network request.
     */
    private WebResourceResponse bundledLibrary(Uri u) {
        if (!CDN_HOST.equals(u.getHost())) return null;
        String p = u.getPath();
        if (p == null) return null;
        String asset = null;
        if (p.endsWith("/jszip.min.js")) {
            asset = "jszip.min.js";
        } else if (p.endsWith("/jsQR.min.js")) {
            asset = "jsQR.min.js";
        }
        if (asset == null) return null;
        try {
            InputStream in = getAssets().open(asset);
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Access-Control-Allow-Origin", "*");
            return new WebResourceResponse("application/javascript", "UTF-8", 200, "OK", headers, in);
        } catch (IOException e) {
            return null;
        }
    }

    private void openExternal(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme) && !"mailto".equals(scheme)) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException ignored) {
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

    // ------------------------------------------------------------------ web clients

    private class PageClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            WebResourceResponse lib = bundledLibrary(u);
            if (lib != null) return lib;
            return assetLoader.shouldInterceptRequest(u);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (HOST.equals(u.getHost())) {
                return false;
            }
            openExternal(u);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            // Fallback for WebViews without document-start scripts.
            view.evaluateJavascript(SHIM, null);
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // The page crashed (usually out of memory). Reload it instead of closing the app.
            ui.post(new Runnable() {
                @Override
                public void run() {
                    recreate();
                }
            });
            return true;
        }
    }

    private class PageChrome extends WebChromeClient {
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    handlePagePermission(request);
                }
            });
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            if (pendingCameraRequest == request) {
                pendingCameraRequest = null;
            }
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;
            try {
                startActivityForResult(params.createIntent(), REQ_FILE);
            } catch (ActivityNotFoundException e) {
                filePathCallback = null;
                toast("No file picker found");
                return false;
            }
            return true;
        }

        /** window.open / target=_blank: hand the link to the phone's browser. */
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                      Message resultMsg) {
            final WebView popup = new WebView(MainActivity.this);
            popup.setWebViewClient(new WebViewClient() {
                private boolean done;

                private void handle(Uri u) {
                    String sc = u == null ? null : u.getScheme();
                    if (done || sc == null || !(sc.equals("http") || sc.equals("https") || sc.equals("mailto"))) {
                        return;
                    }
                    done = true;
                    openExternal(u);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            popup.destroy();
                        }
                    });
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    handle(r.getUrl());
                    return true;
                }

                @Override
                public void onPageStarted(WebView v, String url, Bitmap favicon) {
                    v.stopLoading();
                    handle(Uri.parse(url));
                }
            });
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
            return true;
        }
    }

    // ------------------------------------------------------------------ saving files

    /** Methods below are called from JavaScript (on a background thread). */
    public class Bridge {
        private OutputStream out;
        private Uri uri;
        private File file;
        private String shown;

        @JavascriptInterface
        public synchronized boolean begin(String name, String mime) {
            abort();
            try {
                String safe = sanitize(name);
                if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
                String folder;
                if (mime.startsWith("image/")) {
                    folder = Environment.DIRECTORY_PICTURES;
                } else if (mime.startsWith("video/")) {
                    folder = Environment.DIRECTORY_MOVIES;
                } else if (mime.startsWith("audio/")) {
                    folder = Environment.DIRECTORY_MUSIC;
                } else {
                    folder = Environment.DIRECTORY_DOWNLOADS;
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, folder + "/Mini Paint");
                    v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    ContentResolver cr = getContentResolver();
                    uri = cr.insert(collectionFor(mime), v);
                    if (uri == null) return false;
                    out = cr.openOutputStream(uri);
                    shown = folder + "/Mini Paint/" + safe;
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(folder), "Mini Paint");
                    if (!dir.exists() && !dir.mkdirs()) return false;
                    file = uniqueFile(dir, safe);
                    out = new FileOutputStream(file);
                    shown = folder + "/Mini Paint/" + file.getName();
                }
                return out != null;
            } catch (Exception e) {
                abort();
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean chunk(String base64) {
            if (out == null) return false;
            try {
                out.write(Base64.decode(base64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public synchronized String end() {
            if (out == null) return "err:nothing to save";
            try {
                out.flush();
                out.close();
                out = null;
                if (uri != null) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(uri, v, null, null);
                } else if (file != null) {
                    MediaScannerConnection.scanFile(MainActivity.this,
                            new String[]{file.getAbsolutePath()}, null, null);
                }
                toast("Saved: " + shown);
                String done = shown;
                uri = null;
                file = null;
                return "ok:" + done;
            } catch (IOException e) {
                abort();
                return "err:" + e.getMessage();
            }
        }

        @JavascriptInterface
        public synchronized void abort() {
            try {
                if (out != null) out.close();
            } catch (IOException ignored) {
            }
            out = null;
            if (uri != null) {
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
                uri = null;
            }
            if (file != null) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                file = null;
            }
        }

        private Uri collectionFor(String mime) {
            if (mime.startsWith("image/")) return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            if (mime.startsWith("video/")) return MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            if (mime.startsWith("audio/")) return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            return MediaStore.Downloads.EXTERNAL_CONTENT_URI; // only called on Android 10+
        }

        private String sanitize(String name) {
            String n = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
            return n.isEmpty() ? "drawing.png" : n;
        }

        private File uniqueFile(File dir, String name) {
            File f = new File(dir, name);
            if (!f.exists()) return f;
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            for (int i = 1; i < 10000; i++) {
                f = new File(dir, base + " (" + i + ")" + ext);
                if (!f.exists()) return f;
            }
            return new File(dir, base + "-" + System.currentTimeMillis() + ext);
        }
    }
}
