(function(href){
  // Applies a user style in place, the client-side twin of the <link> Slobber
  // injects server-side for the initial load. Slobber serves user styles under
  // its /user-styles/ route, so the currently-applied one is identified by that
  // href prefix - whether Slobber injected it on load or an earlier switch
  // added it, and with no marker shared with Slobber. Remove any such <link>,
  // then, if a href is given, add a fresh one (the browser fetches the CSS from
  // Slobber). Pair with setcannedstyle.js (setStyle('')) to drop the document's
  // built-in alternate stylesheets while a user style is active. Injected via
  // WebView.evaluateJavascript, not loadUrl("javascript:...") - the latter
  // doesn't reliably materialize a dynamically added stylesheet. href is passed
  // in (JSON-quoted by the caller), so a name with special characters can't break
  // out of the string literal.
  var existing = document.querySelectorAll('link[href^="/user-styles/"]');
  // applyStylePref() re-runs on every resume and page change. If the wanted
  // style is already the linked one, leave it: removing and re-adding an
  // identical <link> would drop the sheet and reload it asynchronously (a
  // script-inserted stylesheet isn't render-blocking, and /user-styles is
  // no-cache), flashing the article unstyled.
  if (href && existing.length === 1 && existing[0].getAttribute('href') === href) {
    return;
  }
  for (var i = 0; i < existing.length; i++) {
    existing[i].parentNode.removeChild(existing[i]);
  }
  if (href && document.head) {
    var link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    document.head.appendChild(link);
  }
})
