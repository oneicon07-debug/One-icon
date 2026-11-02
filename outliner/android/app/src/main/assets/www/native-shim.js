/* Android bridge for the original web app.
   - Screenshot / Auto Angles: <a download> blob links don't work in a WebView,
     so the PNG is sent to the app and saved to Gallery (Pictures/3D Outliner).
   - "Landscape" button: toggles landscape + fullscreen through the app. */
(function () {
  if (!window.Android) return;

  function toBase64(blob) {
    return new Promise(function (res, rej) {
      var r = new FileReader();
      r.onload = function () { res(String(r.result).split(',')[1]); };
      r.onerror = rej;
      r.readAsDataURL(blob);
    });
  }

  var origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (this.download && /^blob:/.test(this.href)) {
      var name = this.download;
      fetch(this.href)
        .then(function (r) { return r.blob(); })
        .then(toBase64)
        .then(function (b64) { window.Android.saveImage(name, b64); })
        .catch(function (e) { alert('Save failed: ' + e); });
      return;
    }
    return origClick.apply(this, arguments);
  };

  var landscape = false;
  document.documentElement.requestFullscreen = function () {
    landscape = !landscape;
    window.Android.setLandscape(landscape);
    return Promise.resolve();
  };
  try {
    screen.orientation.lock = function () { return Promise.resolve(); };
  } catch (e) { /* ignore */ }
})();
