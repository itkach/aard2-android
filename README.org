* Aard 2 for Android
  /Aard 2 for Android/ is a successor to [[https://github.com/aarddict/android][Aard Dictionary for Android]]. It
  comes with redesigned user interface, bookmarks, history and a
  better [[https://github.com/itkach/slob][dictionary storage format]].

** Download

   - [[https://github.com/itkach/aard2-android/releases][Aard 2 for Android]]
   - [[https://github.com/itkach/slob/wiki/Dictionaries][Dictionaries]]


** Features

*** Lookup
    Lookup queries are punctuation, diacritics and case
    insensitive.

    [[images/sm-lookup.png]]


*** Bookmarks and History
    Visited articles are automatically added to history and appear in
    History tab. Articles can also be bookmarked (tap Bookmark icon
    when viewing article). Bookmarked articles
    appear in Bookmarks tab. Bookmarks and history can be
    filtered and sorted by time or article title. Both bookmarks and
    history are limited to a hundred of most recently used items. To
    remove bookmark or history record, long tap a list item to enter
    selection mode, tap items to be removed, tap Trash Can icon and
    confirm. A bookmark can also be removed by tapping Bookmark icon
    when viewing article.

    [[images/sm-bookmarks.png]]
    [[images/sm-history.png]]


*** Dictionary Management
    Dictionaries are added by selecting dictionary files using
    Android's document chooser.

    Note that application itself does not download dictionary files.

    Opened dictionaries can be ordered by
    marking and unmarking them as "favorite" (tap dictionary
    title). Lookup results of equal match strength from multiple
    dictionaries are presented in the order of dictionaries in the
    dictionary list. Dictionaries can also be deactivated (turned
    off). Turned off dictionaries do not participate in word lookup or
    random article lookup,
    but are still available when opening articles from bookmarks,
    history or when following links in other articles. Unwanted dictionaries
    can also be completely removed from the program (but dictionary files
    are not deleted).

    [[images/sm-dictionaries.png]]


*** Article Appearance
    Dictionaries may include alternate style sheets. User may
    also add custom style sheets via Settings tab. Dictionary built-in and
    user styles appear in the "Style..." menu in article view.

    [[images/sm-article_appearance_default.png]]
    [[images/sm-article_menu.png]]
    [[images/sm-article_appearance_select_style.png]]
    [[images/sm-article_appearance_dark.png]]

*** Math
    Mathematical [[https://meta.wikimedia.org/wiki/Help:Displaying_a_formula][formulas in Wikipedia]] articles are rendered as text
    using [[http://www.mathjax.org/][MathJax]] - scalable, styleable, beautiful on any screen.

    [[images/sm-article-integral.png]]
    [[images/sm-article-ru-ryad-taylora.png]]
    [[images/sm-article-ru-zam-predely.png]]

*** Random Article
    Tapping application logo in main activity finds a random title
    in an active dictionary and opens corresponding articles.
    User may optionally limit random lookup to only use favorite
    dictionaries.

*** Volume Buttons Navigation
    When viewing articles, volume up/down buttons scroll article
    content or, if at the bottom (top) of the page, move to the next
    (previous) article. Long press scrolls all the way to the bottom
    (top). In main view volume buttons cycle through tabs. This
    behavior can be disabled in settings.

*** Fullscreen Mode
    Articles can be viewed in fullscreen
    mode. Pull down the top edge to exit fullscreen mode.

*** Clipboard Auto-Paste
    Text from clipboard can be automatically pasted into lookup field
    (unless it contains a Web address, email or phone number). This
    behavior is off by default and can be enabled in settings.

*** External Link Sharing
    Some dictionaries (such as Mediawiki based ones - Wikipedia,
    Wiktionary etc.) contain external links. Long tap on the link to
    share it without opening in a browser first.


** Requested Permissions
*** android.permission.INTERNET
    Aard 2 uses local embedded web server to provide article content. This
    permission is necessary to run the server.

    Also, articles may reference remote content such as images. This
    permission is necessary to load it.

*** android.permission.ACCESS_NETWORK_STATE
    User chooses when to allow loading remote content: always,
    when on Wi-Fi or never. This permission is necessary to
    determine network connection state.

** Developing

  Aard 2 is built with [[http://www.gradle.org][Gradle]].

  Aard 2 depends on projects [[https://github.com/itkach/slobj][slobj]] and [[https://github.com/itkach/slobber][slobber]].

  Get the source code:

   #+BEGIN_SRC sh
   mkdir aard2
   cd aard2
   git clone https://github.com/itkach/slobj.git
   git clone https://github.com/itkach/slobber.git
   git clone https://github.com/itkach/aard2-android.git
   #+END_SRC

   Open [[https://developer.android.com/sdk/installing/studio.html][Android Studio]], go to /File/, /Open.../, select
   /aard2-android/ directory and click /Open/.

   To build the APK on the command line:

   #+BEGIN_SRC sh
   cd aard2-android
   ./gradlew build
   #+END_SRC

   To install the APK:

   #+BEGIN_SRC sh
   adb install -r build/outputs/apk/aard2-android-debug.apk
   #+END_SRC

** Launching from Other Applications

   Aard 2 lookup can be initiated from other applications, either
   through standard /Share/ action or directly, if application
   implemented an action to start Aard 2 with lookup intent.

   Applications can launch Aard 2 lookup by starting activity with intent
   ~aard2.lookup~ with text to look up passed as an extra string
    parameter [[http://developer.android.com/reference/android/app/SearchManager.html#QUERY][SearchManager.QUERY]]. For example:

   #+BEGIN_SRC java
   Intent intent = new Intent("aard2.lookup");
   intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
   intent.putExtra(SearchManager.QUERY, "Foo Bar");
   startActivity(intent);
   #+END_SRC

   Same thing from a command line using /adb/:

   #+BEGIN_SRC sh
   adb shell am start -a aard2.lookup -f 335544320 -e query "Foo Bar"
   #+END_SRC
