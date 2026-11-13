package com.minipaint.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;

/**
 * Mini Paint: a thin native shell around the original single-file HTML app.
 * The app file lives at assets/www/index.html and is served from a virtual
 * https origin so ES modules, IndexedDB, canvas, MediaRecorder and blob URLs
 * all behave exactly as they do in a desktop browser. Any other host (the
 * app's CDN scripts for fonts/zip/QR/AI tools) is left to load from the
 * real network, which is why INTERNET permission is required.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_FILES = 1;

    // Monkey-patches <a download> clicks so that blob:/data: exports (PNG,
    // JPEG, MP4/WebM video, etc.) get handed to the native save bridge
    // instead of silently doing nothing, which is what plain WebView does
    // with blob/data download links.
    private static final String DOWNLOAD_HOOK_JS =
        "(function(){" +
        "if(window.__mpHooked)return;window.__mpHooked=1;" +
        "function toB64(blob,cb){var r=new FileReader();r.onloadend=function(){try{cb(String(r.result).split(',')[1]||'')}catch(e){}};r.readAsDataURL(blob)}" +
        "var origClick=HTMLAnchorElement.prototype.click;" +
        "HTMLAnchorElement.prototype.click=function(){" +
        "  try{" +
        "    var href=this.href,dl=this.download;" +
        "    if(dl&&href){" +
        "      if(href.indexOf('blob:')===0){" +
        "        fetch(href).then(function(r){return r.blob()}).then(function(b){toB64(b,function(b64){window.Android&&window.Android.saveFile(dl,b64)})}).catch(function(){});" +
        "        return;" +
        "      }" +
        "      if(href.indexOf('data:')===0){" +
        "        var i=href.indexOf(',');var b64=i>=0?href.slice(i+1):'';" +
        "        window.Android&&window.Android.saveFile(dl,b64);" +
        "        return;" +
        "      }" +
        "    }" +
        "  }catch(e){}" +
        "  return origClick.call(this);" +
        "};" +
        "})();";

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private Toast toast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(0xFF1F5AF6);
        getWindow().setNavigationBarColor(0xFFFFFFFF);

        web = new WebView(this);
        web.setBackgroundColor(0xFFFFFFFF);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serve(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // keep top-level navigation inside the app; everything else
                // (CDN scripts, fonts, AI model downloads) is a sub-resource
                // and never reaches this callback.
                return !HOST.equals(request.getUrl().getHost());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(DOWNLOAD_HOOK_JS, null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                        params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(Intent.createChooser(pick, "Select a file"), REQ_FILES);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            // Links the web app opens with window.open(url, '_blank') (share
            // to WhatsApp/Twitter/Pinterest, "open link", font search, etc.)
            // are launched in the system browser instead of being swallowed.
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView.HitTestResult hit = view.getHitTestResult();
                String targetUrl = hit != null ? hit.getExtra() : null;

                WebView hidden = new WebView(MainActivity.this);
                hidden.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        openExternally(request.getUrl());
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(hidden);
                resultMsg.sendToTarget();

                if (targetUrl != null) {
                    openExternally(Uri.parse(targetUrl));
                }
                return true;
            }
        });

        web.loadUrl("https://" + HOST + "/index.html");
    }

    private void openExternally(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_FILES) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (filePathCallback == null) {
            return;
        }
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                result = new Uri[n];
                for (int i = 0; i < n; i++) {
                    result[i] = data.getClipData().getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        filePathCallback.onReceiveValue(result);
        filePathCallback = null;
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }

    /* ---------- serving assets/www over the virtual https origin ---------- */

    private WebResourceResponse serve(Uri uri) {
        if (!HOST.equals(uri.getHost())) {
            return null;
        }
        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            path = "/index.html";
        }
        try {
            InputStream in = getAssets().open("www" + path);
            String mime = mimeOf(path);
            String enc = (mime.startsWith("text/") || mime.equals("application/json")) ? "UTF-8" : null;
            return new WebResourceResponse(mime, enc, in);
        } catch (IOException e) {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                    new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
        }
    }

    private static String mimeOf(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "text/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    /* ---------- JS bridge: save exported drawings/videos to the gallery ---------- */

    private class Bridge {
        @JavascriptInterface
        public void saveFile(String name, String base64) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String where = saveToGallery(name, bytes);
                showToast("Saved: " + where);
            } catch (Exception e) {
                showToast("Save failed: " + e.getMessage());
            }
        }
    }

    private String saveToGallery(String name, byte[] data) throws IOException {
        String file = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (file.trim().isEmpty()) {
            file = "drawing.png";
        }
        file = System.currentTimeMillis() + "-" + file;

        String lower = file.toLowerCase(Locale.ROOT);
        boolean isVideo = lower.endsWith(".mp4") || lower.endsWith(".webm");
        String mime = isVideo
                ? (lower.endsWith(".mp4") ? "video/mp4" : "video/webm")
                : (lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg" : "image/png");
        Uri collection = isVideo
                ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String relDir = (isVideo ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES) + "/Mini Paint";

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(isVideo ? MediaStore.Video.Media.DISPLAY_NAME : MediaStore.Images.Media.DISPLAY_NAME, file);
            v.put(isVideo ? MediaStore.Video.Media.MIME_TYPE : MediaStore.Images.Media.MIME_TYPE, mime);
            v.put(isVideo ? MediaStore.Video.Media.RELATIVE_PATH : MediaStore.Images.Media.RELATIVE_PATH, relDir);
            Uri uri = getContentResolver().insert(collection, v);
            if (uri == null) {
                throw new IOException("could not create file");
            }
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) {
                throw new IOException("could not open file");
            }
            try {
                os.write(data);
            } finally {
                os.close();
            }
            return relDir;
        }

        // Android 8-9: app-specific folder (no storage permission needed)
        File dir = getExternalFilesDir(isVideo ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES);
        if (dir == null) {
            throw new IOException("no storage available");
        }
        dir.mkdirs();
        File f = new File(dir, file);
        FileOutputStream fo = new FileOutputStream(f);
        try {
            fo.write(data);
        } finally {
            fo.close();
        }
        return f.getAbsolutePath();
    }

    private void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (toast != null) {
                    toast.cancel();
                }
                toast = Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT);
                toast.show();
            }
        });
    }
}
