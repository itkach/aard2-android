package itkach.aard2;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * An article link on a MediaWiki site (Wikipedia, Wiktionary, ...) in the
 * standard {@code http(s)://<host>/wiki/<Title>[#<section>]} form, as shared from
 * a browser or the Wikipedia app. Parsed with java.net.URI rather than
 * android.net.Uri so it has no Android dependency.
 */
final class WikiArticleUrl {

    private static final String ARTICLE_PATH = "/wiki/";

    // Site host, lowercase and without the mobile "m" label
    // (en.m.wikipedia.org -> en.wikipedia.org), comparable with siteHost() of a
    // dictionary's uri tag.
    final String host;
    // Article title, with MediaWiki's underscores turned back into spaces.
    final String title;
    // Section anchor, or null.
    final String fragment;

    private WikiArticleUrl(String host, String title, String fragment) {
        this.host = host;
        this.title = title;
        this.fragment = fragment;
    }

    /** The article link {@code text} consists of, or null if it isn't one. */
    static WikiArticleUrl parse(String text) {
        if (text == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(text.trim());
        } catch (URISyntaxException e) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return null;
        }
        String host = siteHost(uri);
        String path = uri.getPath();
        if (host == null || path == null || !path.startsWith(ARTICLE_PATH)) {
            return null;
        }
        String title = path.substring(ARTICLE_PATH.length()).replace('_', ' ').trim();
        if (title.isEmpty()) {
            return null;
        }
        String fragment = uri.getFragment();
        if (fragment != null && fragment.isEmpty()) {
            fragment = null;
        }
        return new WikiArticleUrl(host, title, fragment);
    }

    /**
     * The site host named by a dictionary's uri tag (MediaWiki's server value:
     * "http://en.wikipedia.org", "https://..." or protocol-relative "//..."),
     * normalized like {@link #host}; null if it names none.
     */
    static String siteHost(String uri) {
        try {
            return siteHost(new URI(uri));
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String siteHost(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        String[] labels = host.split("\\.");
        if (labels.length < 3 || !labels[1].equals("m")) {
            return host;
        }
        StringBuilder desktop = new StringBuilder(labels[0]);
        for (int i = 2; i < labels.length; i++) {
            desktop.append('.').append(labels[i]);
        }
        return desktop.toString();
    }
}
