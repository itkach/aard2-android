Aard 2 for Android does not collect any data.

Note that the application uses embedded [[https://developer.android.com/reference/android/webkit/WebView][WebView]] component to display
dictionary content which may include references to remote resources
such as images. If loading remote content is enabled in application's
settings such resources will be loaded and parties hosting these
resources will, unavoidably, have access to user's IP address
and standard http request headers such as ~User-Agent~ which may
reveal non-personal information such as Android version, phone model
and language preference.
