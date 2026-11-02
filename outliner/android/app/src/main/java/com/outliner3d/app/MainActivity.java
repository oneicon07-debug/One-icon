package com.outliner3d.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
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
 * 3D Outliner: a thin native shell around the original HTML app.
 * The web files live in assets/www and are served from a virtual https origin so that
 * ES modules, import maps, IndexedDB and blob URLs all work exactly like in a browser.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_FILES = 1;

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private Toast toast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(0xFFB794FF);
        getWindow().setNavigationBarColor(0xFFFFC4E4);

        web = new WebView(this);
        web.setBackgroundColor(0xFFB794FF);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);

        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serve(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // keep everything inside the app
                return !HOST.equals(request.getUrl().getHost());
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
                    startActivityForResult(Intent.createChooser(pick, "Select 3D model (.glb .gltf .obj)"), REQ_FILES);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.loadUrl("https://" + HOST + "/index.html");
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
        if (p.endsWith(".glb")) return "model/gltf-binary";
        if (p.endsWith(".gltf")) return "model/gltf+json";
        return "application/octet-stream";
    }

    /* ---------- JS bridge: save screenshots, landscape/fullscreen ---------- */

    private class Bridge {
        @JavascriptInterface
        public void saveImage(String name, String base64) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String where = saveToGallery(name, bytes);
                showToast("Saved: " + where);
            } catch (Exception e) {
                showToast("Save failed: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void setLandscape(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    applyLandscape(on);
                }
            });
        }
    }

    private String saveToGallery(String name, byte[] data) throws IOException {
        String file = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!file.toLowerCase(Locale.ROOT).endsWith(".png")) {
            file = file + ".png";
        }
        file = System.currentTimeMillis() + "-" + file;

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, file);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/3D Outliner");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
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
            return "Pictures/3D Outliner";
        }

        // Android 8-9: app-specific folder (no storage permission needed)
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
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

    private void applyLandscape(boolean on) {
        setRequestedOrientation(on
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(on
                ? (View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
                : View.SYSTEM_UI_FLAG_VISIBLE);
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
