package itkach.aard2;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import itkach.aard2.prefs.AppPrefs;
import itkach.aard2.utils.ThreadUtils;
import itkach.aard2.utils.Utils;
import itkach.slob.Slob;
import itkach.slob.Slob.Blob;
import itkach.slobber.Slobber;

public class Application extends android.app.Application {

    public static final String LOCALHOST = "127.0.0.1";
    public static final String CONTENT_URL_TEMPLATE = "http://" + LOCALHOST + ":%s%s";

    private Slobber slobber;

    public BlobDescriptorList bookmarks;
    public BlobDescriptorList history;
    public SlobDescriptorList dictionaries;

    private static int PREFERRED_PORT = 8013;
    private int port = -1;

    public BlobListAdapter lastResult;

    private DescriptorStore<BlobDescriptor> bookmarkStore;
    private DescriptorStore<BlobDescriptor> historyStore;
    private DescriptorStore<SlobDescriptor> dictStore;

    private ObjectMapper mapper;

    private String lookupQuery = "";

    private List<Activity> articleActivities;

    public static String jsStyleSwitcher;
    public static String jsUserStyle;
    public static String jsClearUserStyle;
    public static String jsSetCannedStyle;

    private static final String TAG = Application.class.getSimpleName();

    private static Application instance;

    public static Application get() {
        return instance;
    }

    @Override
    public void onCreate() {
        instance = this;
        super.onCreate();
        try {
            Method setWebContentsDebuggingEnabledMethod = WebView.class.getMethod(
                    "setWebContentsDebuggingEnabled", boolean.class);
            setWebContentsDebuggingEnabledMethod.invoke(null, true);
        } catch (NoSuchMethodException e1) {
            Log.d(TAG,
                    "setWebContentsDebuggingEnabledMethod method not found");
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
        articleActivities = Collections.synchronizedList(new ArrayList<>());

        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        dictStore = new DescriptorStore<>(mapper, getDir("dictionaries", MODE_PRIVATE));
        bookmarkStore = new DescriptorStore<>(mapper, getDir(
                "bookmarks", MODE_PRIVATE));
        historyStore = new DescriptorStore<>(mapper, getDir(
                "history", MODE_PRIVATE));
        slobber = new Slobber();

        long t0 = System.currentTimeMillis();

        startWebServer();

        Log.d(TAG, String.format("Started web server on port %d in %d ms",
                port, (System.currentTimeMillis() - t0)));
        try {
            InputStream is;
            is = getClass().getClassLoader().getResourceAsStream("styleswitcher.js");
            jsStyleSwitcher = Utils.readStream(is, 0);
            is = getAssets().open("userstyle.js");
            jsUserStyle = Utils.readStream(is, 0);
            is = getAssets().open("clearuserstyle.js");
            jsClearUserStyle = Utils.readStream(is, 0);
            is = getAssets().open("setcannedstyle.js");
            jsSetCannedStyle = Utils.readStream(is, 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        lastResult = new BlobListAdapter(this);

        dictionaries = new SlobDescriptorList(this, dictStore);
        bookmarks = new BlobDescriptorList(this, bookmarkStore);
        history = new BlobDescriptorList(this, historyStore);

        dictionaries.registerDataSetObserver(new DataSetObserver() {
            @Override
            synchronized public void onChanged() {
                lastResult.setData(new ArrayList<Slob.Blob>().iterator());
                slobber.setSlobs(null);
                List<Slob> slobs = new ArrayList<>();
                for (SlobDescriptor sd : dictionaries) {
                    Slob s = sd.load(getApplicationContext());
                    if (s != null) {
                        slobs.add(s);
                    }
                }
                slobber.setSlobs(slobs);

                ThreadUtils.postOnMainThread(() -> {
                    new EnableLinkHandling(Application.this)
                            .execute(getActiveSlobs());
                    lookup(lookupQuery);
                    bookmarks.notifyDataSetChanged();
                    history.notifyDataSetChanged();
                });
            }
        });

        dictionaries.load();
        lookup(AppPrefs.getLastQuery(), false);
        bookmarks.load();
        history.load();
    }


    private void startWebServer() {
        int portCandidate = PREFERRED_PORT;
        try {
            slobber.start("127.0.0.1", portCandidate);
            port = portCandidate;
        } catch (IOException e) {
            Log.w(TAG,
                    String.format("Failed to start on preferred port %d",
                            portCandidate), e);
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
                    Log.w(TAG,
                            String.format("Failed to start on port %d",
                                    portCandidate), e1);
                }
                if (attemptCount >= 20) {
                    throw new RuntimeException("Failed to start web server", lastError);
                }
            }
        }
    }

    public void push(Activity activity) {
        articleActivities.add(activity);
        Log.d(TAG, "Activity added, stack size " + articleActivities.size());
        if (articleActivities.size() > 3) {
            Log.d(TAG, "Max stack size exceeded, finishing oldest activity");
            articleActivities.get(0).finish();
        }
    }

    public void pop(Activity activity) {
        articleActivities.remove(activity);
    }

    @NonNull
    private Slob[] getActiveSlobs() {
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
    private Slob[] getFavoriteSlobs() {
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
    private Iterator<Blob> find(String key) {
        return Slob.find(key, getActiveSlobs());
    }

    @NonNull
    public Iterator<Blob> find(String key, String preferredSlobId) {
        //When following links we want to consider all dictionaries
        //including the ones user turned off
        return find(key, preferredSlobId, false);
    }

    public Slob.PeekableIterator<Blob> find(String key, String preferredSlobId, boolean activeOnly) {
        return this.find(key, preferredSlobId, activeOnly, null);
    }

    private Slob.PeekableIterator<Blob> find(String key, String preferredSlobId, boolean activeOnly, Slob.Strength upToStrength) {
        long t0 = System.currentTimeMillis();
        Slob[] slobs = activeOnly ? getActiveSlobs() : slobber.getSlobs();
        Slob.PeekableIterator<Blob> result = Slob.find(key, slobs, slobber.findSlob(preferredSlobId), upToStrength);
        Log.d(TAG, String.format("find ran in %dms", System.currentTimeMillis() - t0));
        return result;
    }

    public Blob random() {
        Slob[] slobs = AppPrefs.useOnlyFavoritesForRandomLookups() ? getFavoriteSlobs() : getActiveSlobs();
        return slobber.findRandom(slobs);
    }

    public String getUrl(Blob blob) {
        return String.format(CONTENT_URL_TEMPLATE,
                port, Slobber.mkContentURL(blob));
    }

    public Slob getSlob(String slobId) {
        return slobber.getSlob(slobId);
    }

    public Slob findSlob(String slobOrUri) {
        return slobber.findSlob(slobOrUri);
    }

    public String getSlobURI(String slobId) {
        return slobber.getSlobURI(slobId);
    }


    public void addBookmark(String contentURL) {
        bookmarks.add(contentURL);
    }

    public void removeBookmark(String contentURL) {
        bookmarks.remove(contentURL);
    }

    public boolean isBookmarked(String contentURL) {
        return bookmarks.contains(contentURL);
    }

    private void setLookupResult(@NonNull String query, Iterator<Slob.Blob> data) {
        lastResult.setData(data);
        lookupQuery = query;
        AppPrefs.setLastQuery(query);
    }

    public String getLookupQuery() {
        return lookupQuery;
    }

    private AsyncTask<Void, Void, Iterator<Blob>> currentLookupTask;

    public void lookup(String query) {
        lookup(query, true);
    }

    public void lookup(final String query, boolean async) {
        if (currentLookupTask != null) {
            currentLookupTask.cancel(false);
            notifyLookupCanceled(query);
            currentLookupTask = null;
        }
        notifyLookupStarted(query);
        if (query == null || query.equals("")) {
            setLookupResult("", new ArrayList<Slob.Blob>().iterator());
            notifyLookupFinished(query);
            return;
        }

        if (async) {
            currentLookupTask = new AsyncTask<Void, Void, Iterator<Blob>>() {

                @Override
                protected Iterator<Blob> doInBackground(Void... params) {
                    return find(query);
                }

                @Override
                protected void onPostExecute(Iterator<Blob> result) {
                    if (!isCancelled()) {
                        setLookupResult(query, result);
                        notifyLookupFinished(query);
                        currentLookupTask = null;
                    }
                }

            };
            currentLookupTask.execute();
        } else {
            setLookupResult(query, find(query));
            notifyLookupFinished(query);
        }
    }

    private void notifyLookupStarted(String query) {
        for (LookupListener l : lookupListeners) {
            l.onLookupStarted(query);
        }
    }

    private void notifyLookupFinished(String query) {
        for (LookupListener l : lookupListeners) {
            l.onLookupFinished(query);
        }
    }

    private void notifyLookupCanceled(String query) {
        for (LookupListener l : lookupListeners) {
            l.onLookupCanceled(query);
        }
    }

    private final List<LookupListener> lookupListeners = new ArrayList<>();

    void addLookupListener(LookupListener listener) {
        lookupListeners.add(listener);
    }

    void removeLookupListener(LookupListener listener) {
        lookupListeners.remove(listener);
    }


    private static class EnableLinkHandling extends AsyncTask<Slob, Void, Void> {
        private final Application application;

        public EnableLinkHandling(@NonNull Application application) {
            this.application = application;
        }

        @Override
        protected Void doInBackground(Slob[] slobs) {
            Set<String> hosts = new HashSet<>();
            for (Slob slob : slobs) {
                try {
                    String uriValue = slob.getTags().get("uri");
                    Uri uri = Uri.parse(uriValue);
                    String host = uri.getHost();
                    if (host != null) {
                        hosts.add(host.toLowerCase());
                    }
                } catch (Exception ex) {
                    Log.w(TAG, String.format("Dictionary %s (%s) has no uri tag", slob.getId(), slob.getTags()), ex);
                }
            }

            long t0 = System.currentTimeMillis();
            String packageName = application.getPackageName();
            try {
                PackageManager pm = application.getPackageManager();
                PackageInfo p = pm.getPackageInfo(packageName,
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_DISABLED_COMPONENTS);
                Log.d(TAG, "Done getting available activities in " + (System.currentTimeMillis() - t0));
                t0 = System.currentTimeMillis();
                for (ActivityInfo activityInfo : p.activities) {
                    if (isCancelled()) break;
                    if (activityInfo.targetActivity != null) {
                        boolean enabled = hosts.contains(activityInfo.name);
                        if (enabled) {
                            Log.d(TAG, "Enabling links handling for " + activityInfo.name);
                        }
                        int setting = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                        pm.setComponentEnabledSetting(new ComponentName(application, activityInfo.name), setting,
                                PackageManager.DONT_KILL_APP);
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, e);
            }
            Log.d(TAG, "Done enabling activities in " + (System.currentTimeMillis() - t0));
            return null;
        }
    }
}
