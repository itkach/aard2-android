package itkach.aard2;

/**
 * External entry point for opening an article - shared text, colordict/aard2
 * lookup intents, and the per-wiki link-handling aliases. Behaves exactly like
 * {@link ArticleCollectionActivity}, but is declared in the manifest with its own
 * empty taskAffinity and excludeFromRecents so an article opened from another app
 * lands in an isolated, recents-hidden task rather than the app's main task.
 * In-app navigation uses ArticleCollectionActivity directly, keeping the article
 * in the main task where the launcher restores it.
 */
public class ExternalArticleActivity extends ArticleCollectionActivity {
}
