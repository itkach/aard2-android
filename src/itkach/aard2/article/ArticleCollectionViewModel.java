package itkach.aard2.article;

import android.app.Application;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Iterator;
import java.util.List;

import itkach.aard2.BlobDescriptor;
import itkach.aard2.R;
import itkach.aard2.SlobHelper;
import itkach.aard2.lookup.LookupResult;
import itkach.aard2.utils.ThreadUtils;
import itkach.aard2.utils.Utils;
import itkach.slob.Slob;

public class ArticleCollectionViewModel extends AndroidViewModel {
    public static final String TAG = ArticleCollectionViewModel.class.getSimpleName();
    private final itkach.aard2.Application application;
    private final MutableLiveData<BlobListWrapper> blobListLiveData = new MutableLiveData<>();
    private final MutableLiveData<CharSequence> failureMessageLiveData = new MutableLiveData<>();

    public ArticleCollectionViewModel(@NonNull Application application) {
        super(application);
        this.application = (itkach.aard2.Application) application;
    }

    public LiveData<BlobListWrapper> getBlobListLiveData() {
        return blobListLiveData;
    }

    public LiveData<CharSequence> getFailureMessageLiveData() {
        return failureMessageLiveData;
    }

    public void loadBlobList(@NonNull Intent intent) {
        ThreadUtils.postOnBackgroundThread(() -> {
            Uri articleUrl = intent.getData();
            int currentPosition = intent.getIntExtra("position", 0);
            try {
                BlobListWrapper result;
                if (articleUrl != null) {
                    result = createFromUri(articleUrl, intent);
                } else {
                    String action = intent.getAction();
                    if (action == null) {
                        result = createFromLastResult();
                    } else if (action.equals("showBookmarks")) {
                        result = createFromBookmarks();
                    } else if (action.equals("showHistory")) {
                        result = createFromHistory();
                    } else {
                        result = createFromIntent(intent);
                    }
                }
                // Deliver result
                if (result != null) {
                    int resultCount = result.size();
                    if (resultCount != 0) {
                        blobListLiveData.postValue(result);
                    } else if (currentPosition >= resultCount) {
                        failureMessageLiveData.postValue(application.getString(R.string.article_collection_selected_not_available));
                    } else {
                        failureMessageLiveData.postValue(application.getString(R.string.article_collection_nothing_found));
                    }
                } else {
                    failureMessageLiveData.postValue(application.getString(R.string.article_collection_invalid_link));
                }
            } catch (Exception e) {
                failureMessageLiveData.postValue(e.getLocalizedMessage());
            }
        });
    }

    @Nullable
    private BlobListWrapper createFromUri(@NonNull Uri articleUrl, @NonNull Intent intent) {
        String host = articleUrl.getHost();
        if (!(host.equals("localhost") || host.matches("127.\\d{1,3}.\\d{1,3}.\\d{1,3}"))) {
            return createFromIntent(intent);
        }
        BlobDescriptor bd = BlobDescriptor.fromUri(articleUrl);
        if (bd == null) {
            return null;
        }
        Iterator<Slob.Blob> result = SlobHelper.getInstance().find(bd.key, bd.slobId);
        LookupResult lookupResult = new LookupResult(20, 1);
        lookupResult.setResult(result);
        boolean hasFragment = !TextUtils.isEmpty(bd.fragment);
        return new LookupResultWrapper(lookupResult, hasFragment ? new ToBlobWithFragment(bd.fragment) : item -> item);
    }

    @NonNull
    private BlobListWrapper createFromLastResult() {
        return new LookupResultWrapper(SlobHelper.getInstance().lastLookupResult, item -> item);
    }

    @NonNull
    private BlobListWrapper createFromBookmarks() {
        return new BlobDescriptorListWrapper(SlobHelper.getInstance().bookmarks);
    }

    @NonNull
    private BlobDescriptorListWrapper createFromHistory() {
        return new BlobDescriptorListWrapper(SlobHelper.getInstance().history);
    }

    @NonNull
    private BlobListWrapper createFromIntent(@NonNull Intent intent) {
        String lookupKey = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (intent.getAction().equals(Intent.ACTION_PROCESS_TEXT)) {
            lookupKey = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString();
        }
        if (lookupKey == null) {
            lookupKey = intent.getStringExtra(SearchManager.QUERY);
        }
        if (lookupKey == null) {
            lookupKey = intent.getStringExtra("EXTRA_QUERY");
        }
        String preferredSlobId = null;
        if (lookupKey == null) {
            Uri uri = intent.getData();
            List<String> segments = uri.getPathSegments();
            int length = segments.size();
            if (length > 0) {
                lookupKey = segments.get(length - 1);
            }
            String slobUri = Utils.wikipediaToSlobUri(uri);
            Log.d(TAG, String.format("Converted URI %s to slob URI %s", uri, slobUri));
            if (slobUri != null) {
                Slob slob = SlobHelper.getInstance().findSlob(slobUri);
                if (slob != null) {
                    preferredSlobId = slob.getId().toString();
                    Log.d(TAG, String.format("Found slob %s for slob URI %s", preferredSlobId, slobUri));
                }
            }
        }
        LookupResult lookupResult = new LookupResult(20, 1);
        if (lookupKey == null || lookupKey.length() == 0) {
            String msg = application.getString(R.string.article_collection_nothing_to_lookup);
            throw new RuntimeException(msg);
        }
        Iterator<Slob.Blob> result = stemLookup(lookupKey, preferredSlobId);
        lookupResult.setResult(result);
        return new LookupResultWrapper(lookupResult, item -> item);
    }

    @NonNull
    private Iterator<Slob.Blob> stemLookup(@NonNull String lookupKey, @Nullable String preferredSlobId) {
        Slob.PeekableIterator<Slob.Blob> result;
        final int length = lookupKey.length();
        String currentLookupKey = lookupKey;
        int currentLength = currentLookupKey.length();
        do {
            result = SlobHelper.getInstance().find(currentLookupKey, preferredSlobId, true);
            if (result.hasNext()) {
                Slob.Blob b = result.peek();
                if (b.key.length() - length > 3) {
                    // We don't like this result
                } else {
                    break;
                }
            }
            currentLookupKey = currentLookupKey.substring(0, currentLength - 1);
            currentLength = currentLookupKey.length();
        } while (length - currentLength < 5 && currentLength > 0);
        return result;
    }

    private static class ToBlobWithFragment implements LookupResultWrapper.ToBlob<Slob.Blob> {
        @NonNull
        private final String fragment;

        ToBlobWithFragment(@NonNull String fragment) {
            this.fragment = fragment;
        }

        @Override
        @Nullable
        public Slob.Blob convert(@Nullable Slob.Blob item) {
            if (item == null) {
                return null;
            }
            return new Slob.Blob(item.owner, item.id, item.key, this.fragment);
        }
    }
}
