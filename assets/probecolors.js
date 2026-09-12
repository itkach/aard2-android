// Measures the article's actual painted colors with the browser's own CSS
// engine, so the WebView placeholder background (and the loading spinner tint)
// can match instead of guessing from the style's name. Returns a flat numeric
// array evaluateJavascript hands back as JSON:
//   [ bodyR, bodyG, bodyB, bodyA,  htmlR, htmlG, htmlB, htmlA,  fgR, fgG, fgB ]
// Both body and html backgrounds are reported (body wins when opaque, html is
// the fallback) along with the alpha channel, so the caller can tell an opaque
// color from a transparent one - getComputedStyle normalizes an unset/transparent
// background to rgba(0,0,0,0), whose RGB is meaningless black, so alpha is the
// only signal that the color isn't real. fg is body's text color, for the spinner.
(function () {
  function nums(s) {
    return ((s || '').match(/[\d.]+/g) || []).map(Number);
  }
  function bg(el) {
    var n = nums(getComputedStyle(el).backgroundColor);
    return [n[0] || 0, n[1] || 0, n[2] || 0, n.length > 3 ? n[3] : 1];
  }
  var b = bg(document.body),
      h = bg(document.documentElement),
      f = nums(getComputedStyle(document.body).color);
  return [b[0], b[1], b[2], b[3], h[0], h[1], h[2], h[3], f[0] || 0, f[1] || 0, f[2] || 0];
})()
