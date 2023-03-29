package itkach.aard2;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import itkach.aard2.prefs.AppPrefs;
import itkach.slob.Slob;
import itkach.slobber.Slobber;

public final class SlobHelper {
    public static final String TAG = SlobHelper.class.getSimpleName();
    public static final String LOCALHOST = "127.0.0.1";
    public static final String CONTENT_URL_TEMPLATE = "http://" + LOCALHOST + ":%s%s";
    public static final int PREFERRED_PORT = 8013;

    private static SlobHelper instance;

    public static SlobHelper getInstance() {
        if (instance == null) {
            instance = new SlobHelper(Application.get());
        }
        return instance;
    }

    @NonNull
    private final Application application;
    @NonNull
    private final ObjectMapper mapper;
    @NonNull
    private final DescriptorStore<BlobDescriptor> bookmarkStore;
    @NonNull
    private final DescriptorStore<BlobDescriptor> historyStore;
    @NonNull
    private final DescriptorStore<SlobDescriptor> dictStore;

    @NonNull
    public final BlobDescriptorList bookmarks;
    @NonNull
    public final BlobDescriptorList history;
    @NonNull
    public final SlobDescriptorList dictionaries;

    private Slobber slobber;
    private int port = -1;
    private volatile boolean initialized;

    private SlobHelper(@NonNull Application application) {
        this.application = application;
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        dictStore = new DescriptorStore<>(mapper, application.getDir("dictionaries", Context.MODE_PRIVATE));
        bookmarkStore = new DescriptorStore<>(mapper, application.getDir("bookmarks", Context.MODE_PRIVATE));
        historyStore = new DescriptorStore<>(mapper, application.getDir("history", Context.MODE_PRIVATE));
        dictionaries = new SlobDescriptorList(dictStore);
        bookmarks = new BlobDescriptorList(bookmarkStore);
        history = new BlobDescriptorList(historyStore);
    }

    @WorkerThread
    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        long t0 = System.currentTimeMillis();
        slobber = new Slobber();
        int portCandidate = PREFERRED_PORT;
        try {
            slobber.start("127.0.0.1", portCandidate);
            port = portCandidate;
        } catch (IOException e) {
            Log.w(TAG, String.format("Failed to start on preferred port %d", portCandidate), e);
            Set<Integer> seen = new HashSet<>();
            seen.add(PREFERRED_PORT);
            Random rand = new Random();
            int attemptCount = 0;
            while (true) {
                int value = 1 + (int) Math.floor((65535 - 1025) * rand.nextDouble());
                portCandidate = 1024 + value;
                if (seen.contains(portCandidate)) {
                    continue;
                }
                attemptCount += 1;
                seen.add(portCandidate);
                Exception lastError;
                try {
                    slobber.start("127.0.0.1", portCandidate);
                    port = portCandidate;
                    break;
                } catch (IOException e1) {
                    lastError = e1;
                    Log.w(TAG, String.format("Failed to start on port %d", portCandidate), e1);
                }
                if (attemptCount >= 20) {
                    throw new RuntimeException("Failed to start web server", lastError);
                }
            }
        }
        Log.d(TAG, String.format("Started web server on port %d in %d ms", port, (System.currentTimeMillis() - t0)));
        // Load dictionaries, bookmarks and history
        dictionaries.load();
        bookmarks.load();
        history.load();
    }

    public void updateSlobs() {
        checkInitialized();
        slobber.setSlobs(null);
        List<Slob> slobs = new ArrayList<>();
        for (SlobDescriptor sd : dictionaries) {
            Slob s = sd.load(application);
            if (s != null) {
                slobs.add(s);
            }
        }
        slobber.setSlobs(slobs);
    }

    @NonNull
    public Slob[] getActiveSlobs() {
        checkInitialized();
        List<Slob> result = new ArrayList<>(dictionaries.size());
        for (SlobDescriptor sd : dictionaries) {
            if (sd.active) {
                Slob s = slobber.getSlob(sd.id);
                if (s != null) {
                    result.add(s);
                }
            }
        }
        return result.toArray(new Slob[0]);
    }

    @NonNull
    public Slob[] getFavoriteSlobs() {
        checkInitialized();
        List<Slob> result = new ArrayList<>(dictionaries.size());
        for (SlobDescriptor sd : dictionaries) {
            if (sd.active && sd.priority > 0) {
                Slob s = slobber.getSlob(sd.id);
                if (s != null) {
                    result.add(s);
                }
            }
        }
        return result.toArray(new Slob[0]);
    }


    @NonNull
    public String getUrl(@NonNull Slob.Blob blob) {
        return String.format(CONTENT_URL_TEMPLATE, port, Slobber.mkContentURL(blob));
    }

    public Slob getSlob(String slobId) {
        return slobber.getSlob(slobId);
    }

    public Slob findSlob(String slobOrUri) {
        return slobber.findSlob(slobOrUri);
    }

    public String getSlobUri(String slobId) {
        return slobber.getSlobURI(slobId);
    }

    public Slob.Blob findRandom() {
        Slob[] slobs = AppPrefs.useOnlyFavoritesForRandomLookups() ? getFavoriteSlobs() : getActiveSlobs();
        return slobber.findRandom(slobs);
    }

    @NonNull
    public Iterator<Slob.Blob> find(@NonNull String key) {
        return Slob.find(key, getActiveSlobs());
    }

    @NonNull
    public Iterator<Slob.Blob> find(@NonNull String key, String preferredSlobId) {
        // When following links we want to consider all dictionaries
        // including the ones user turned off
        return find(key, preferredSlobId, false);
    }

    @NonNull
    public Slob.PeekableIterator<Slob.Blob> find(@NonNull String key, String preferredSlobId, boolean activeOnly) {
        return find(key, preferredSlobId, activeOnly, null);
    }

    @NonNull
    private Slob.PeekableIterator<Slob.Blob> find(@NonNull String key, String preferredSlobId, boolean activeOnly,
                                                  @Nullable Slob.Strength upToStrength) {
        checkInitialized();
        long t0 = System.currentTimeMillis();
        Slob[] slobs = activeOnly ? getActiveSlobs() : slobber.getSlobs();
        Slob.PeekableIterator<Slob.Blob> result = Slob.find(key, slobs, slobber.findSlob(preferredSlobId), upToStrength);
        Log.d(TAG, String.format("find ran in %dms", System.currentTimeMillis() - t0));
        return result;
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("SlobHelper not initialized. Make sure to call init() first!");
        }
    }
}
