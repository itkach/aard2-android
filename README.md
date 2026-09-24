# Aard 2 for Android

_Aard 2 for Android_ is a successor to
[Aard Dictionary for Android](https://github.com/aarddict/android). It comes
with redesigned user interface, bookmarks, history and a better
[dictionary storage format](https://github.com/itkach/slob).

## Download

- [Aard 2 for Android](https://github.com/itkach/aard2-android/releases)
- [Dictionaries](https://github.com/itkach/slob/wiki/Dictionaries)

## Features

### Lookup

Lookup queries are punctuation, diacritics and case insensitive.

<a href="images/lookup1.webp"><img src="images/lookup1.webp" width="220" height="468" alt="Lookup"></a>
<a href="images/lookup2.webp"><img src="images/lookup2.webp" width="220" height="468" alt="Lookup"></a>
<a href="images/lookup3.webp"><img src="images/lookup3.webp" width="220" height="468" alt="Lookup"></a>

### Bookmarks and History

Visited articles are automatically added to history and appear in the History
tab. Articles can also be bookmarked (tap the Bookmark icon when viewing an
article). Bookmarked articles appear in the Bookmarks tab. Bookmarks and history
can be filtered and sorted by time or article title. Both bookmarks and history
are limited to a thousand most recently used items. To remove a single bookmark
or history record, swipe the list item left or right. Several records can be
removed at once: long tap a list item to enter selection mode, tap the items to
be removed, tap the Trash Can icon and confirm. A bookmark can also be removed
by tapping the Bookmark icon when viewing an article.

<a href="images/bookmarks2a.webp"><img src="images/bookmarks2a.webp" width="220" height="468" alt="Bookmarks"></a>
<a href="images/bookmarks2b.webp"><img src="images/bookmarks2b.webp" width="220" height="468" alt="Swipe to delete"></a>
<a href="images/bookmarks3.webp"><img src="images/bookmarks3.webp" width="220" height="468" alt="Select and delete"></a>
<a href="images/history1.webp"><img src="images/history1.webp" width="220" height="468" alt="History"></a>

### Dictionary Management

Dictionaries are added by selecting dictionary files using Android's document
chooser.

Note that the application itself does not download dictionary files.

Opened dictionaries can be reordered by dragging them by the handle at the right
of each row. Lookup results of equal match strength from multiple dictionaries
are presented in the order of dictionaries in the dictionary list. Each
dictionary can be marked to also be used for random article lookup. Dictionaries
can also be deactivated (turned off). Turned off dictionaries do not participate
in word lookup or random article lookup, but are still available when opening
articles from bookmarks, history or when following links in other articles.
Unwanted dictionaries can also be completely removed from the program (but
dictionary files are not deleted).

<a href="images/dictionaries1a.webp"><img src="images/dictionaries1a.webp" width="220" height="468" alt="Dictionaries"></a>
<a href="images/dictionaries1b.webp"><img src="images/dictionaries1b.webp" width="220" height="468" alt="Dictionary options"></a>

### Article Appearance

Dictionaries may include alternate style sheets. Custom style sheets can also be
added via the Settings tab. The article menu's "Style..." entry lists the
dictionary's built-in styles alongside any user styles.

<a href="images/article_menu.webp"><img src="images/article_menu.webp" width="220" height="468" alt="Article menu"></a>
<a href="images/select_style_dialog.webp"><img src="images/select_style_dialog.webp" width="220" height="468" alt="Select style"></a>

### Text Size

Article text size can be adjusted with a slider, available as "Text size" in the
article menu.

<a href="images/resize_text.webp"><img src="images/resize_text.webp" width="220" height="468" alt="Text size"></a>

### Following the Device Color Scheme

Aard 2 follows the device's system colors (Material You): its accent and surface
colors are derived from the current system theme.

<a href="images/theme1.webp"><img src="images/theme1.webp" width="220" height="468" alt="Color scheme"></a>
<a href="images/theme2.webp"><img src="images/theme2.webp" width="220" height="468" alt="Color scheme"></a>
<a href="images/theme3.webp"><img src="images/theme3.webp" width="220" height="468" alt="Color scheme"></a>
<a href="images/theme4.webp"><img src="images/theme4.webp" width="220" height="468" alt="Color scheme"></a>
<a href="images/theme5.webp"><img src="images/theme5.webp" width="220" height="468" alt="Color scheme"></a>
<a href="images/theme6.webp"><img src="images/theme6.webp" width="220" height="468" alt="Color scheme"></a>

### Math

Mathematical
[formulas in Wikipedia](https://meta.wikimedia.org/wiki/Help:Displaying_a_formula)
articles are rendered as text using [MathJax](http://www.mathjax.org/) —
scalable, styleable, beautiful on any screen.

<a href="images/math1.webp"><img src="images/math1.webp" width="220" height="468" alt="Math"></a>
<a href="images/math4.webp"><img src="images/math4.webp" width="220" height="468" alt="Math"></a>

### Random Article

Tapping the dice icon in the main view finds a random title in an active
dictionary and opens the corresponding article. Random lookup can optionally be
limited to only the dictionaries marked for it (see Dictionary Management).

### Volume Buttons Navigation

When viewing articles, volume up/down buttons scroll article content or, if at
the bottom (top) of the page, move to the next (previous) article. Long press
scrolls all the way to the bottom (top). In the main view volume buttons cycle
through tabs. This behavior can be disabled in settings.

### Full-screen Mode

Articles can be viewed in full screen for distraction-free reading. Toggle it
from the article menu; a floating button in the corner brings the toolbar back.
In landscape orientation articles switch to full screen automatically — this can
be turned off in settings.

<a href="images/fullscreen_with_fabs.webp"><img src="images/fullscreen_with_fabs.webp" width="220" alt="Full screen"></a>
<a href="images/fullscreen_with_fabs_landscape.webp"><img src="images/fullscreen_with_fabs_landscape.webp" width="440" alt="Full screen, landscape"></a>

### Navigate to Top

While scrolling a long article, a floating button appears in the corner to jump
back to the top.

<a href="images/scroll_to_top.webp"><img src="images/scroll_to_top.webp" width="220" height="468" alt="Navigate to top"></a>

### Clipboard Auto-Paste

Text from clipboard can be automatically pasted into the lookup field (unless it
contains a Web address, email or phone number). This behavior is off by default
and can be enabled in settings.

### External Link Sharing

Some dictionaries (such as Mediawiki based ones — Wikipedia, Wiktionary etc.)
contain external links. Long tap on the link to share it without opening in a
browser first.

### Settings

The Settings tab collects the application's options: interface style (light or
dark), when to load remote content, whether to use only dictionaries marked for
random lookup, volume-button navigation, automatic full screen in landscape,
clipboard auto-paste, history recording, and user style sheets.

<a href="images/settings.webp"><img src="images/settings.webp" width="220" height="468" alt="Settings"></a>

## Requested Permissions

### android.permission.INTERNET

Aard 2 uses local embedded web server to provide article content. This
permission is necessary to run the server.

Also, articles may reference remote content such as images. This permission is
necessary to load it.

### android.permission.ACCESS_NETWORK_STATE

User chooses when to allow loading remote content: always, when on Wi-Fi or
never. This permission is necessary to determine network connection state.

## Developing

Aard 2 is built with [Gradle](http://www.gradle.org).

Aard 2 depends on projects [slobj](https://github.com/itkach/slobj) and
[slobber](https://github.com/itkach/slobber).

Get the source code:

```sh
mkdir aard2
cd aard2
git clone https://github.com/itkach/slobj.git
git clone https://github.com/itkach/slobber.git
git clone https://github.com/itkach/aard2-android.git
```

Open [Android Studio](https://developer.android.com/sdk/installing/studio.html),
go to _File_, _Open..._, select _aard2-android_ directory and click _Open_.

To build the APK on the command line:

```sh
cd aard2-android
./gradlew build
```

To install the APK:

```sh
adb install -r build/outputs/apk/aard2-android-debug.apk
```

## Launching from Other Applications

Aard 2 lookup can be initiated from other applications, either through standard
_Share_ action or directly, if application implemented an action to start Aard 2
with lookup intent.

Applications can launch Aard 2 lookup by starting activity with intent
`aard2.lookup` with text to look up passed as an extra string parameter
[SearchManager.QUERY](http://developer.android.com/reference/android/app/SearchManager.html#QUERY).
For example:

```java
Intent intent = new Intent("aard2.lookup");
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
intent.putExtra(SearchManager.QUERY, "Foo Bar");
startActivity(intent);
```

Same thing from a command line using _adb_:

```sh
adb shell am start -a aard2.lookup -f 335544320 -e query "Foo Bar"
```
