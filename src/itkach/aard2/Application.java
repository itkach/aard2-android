package itkach.aard2;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import itkach.slob.Slob;
import itkach.slob.Slob.Blob;
import itkach.slobber.Slobber;

public class Application extends android.app.Application {

    private Slobber                         slobber;

    BlobDescriptorList                      bookmarks;
    BlobDescriptorList                      history;
    SlobDescriptorList dictionaries;

    private int                             port = 8013;

    BlobListAdapter                         lastResult;

    private DescriptorStore<BlobDescriptor> bookmarkStore;
    private DescriptorStore<BlobDescriptor> historyStore;
    private DescriptorStore<SlobDescriptor> dictStore;

    private ObjectMapper                    mapper;

    private String                          lookupQuery = "";

    private List<Activity>                  articleActivities;

    static String styleSwitcherJs;

    @Override
    public void onCreate() {
        super.onCreate();
        if(Build.VERSION.SDK_INT >= 19) {
            try {
                Method setWebContentsDebuggingEnabledMethod = WebView.class.getMethod(
                        "setWebContentsDebuggingEnabled", boolean.class);
                setWebContentsDebuggingEnabledMethod.invoke(null, true);
            } catch (NoSuchMethodException e1) {
                Log.d(getClass().getName(),
                        "setWebContentsDebuggingEnabledMethod method not found");
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        articleActivities = Collections.synchronizedList(new ArrayList<Activity>());
        Icons.init(getAssets(), getResources());
        try {
            mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false);
            dictStore = new DescriptorStore<SlobDescriptor>(mapper, getDir("dictionaries", MODE_PRIVATE));
            bookmarkStore = new DescriptorStore<BlobDescriptor>(mapper, getDir(
                    "bookmarks", MODE_PRIVATE));
            historyStore = new DescriptorStore<BlobDescriptor>(mapper, getDir(
                    "history", MODE_PRIVATE));
            slobber = new Slobber();
            slobber.start("127.0.0.1", port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("styleswitcher.js");
            InputStreamReader reader = new InputStreamReader(is, "UTF-8");
            StringWriter sw = new StringWriter();
            int c;
            while ((c = reader.read()) != -1) {
                sw.write(c);
            }
            reader.close();
            styleSwitcherJs = sw.toString();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String initialQuery = prefs().getString("query", "");

        lastResult = new BlobListAdapter(this);

        dictionaries = new SlobDescriptorList(this, dictStore);
        bookmarks = new BlobDescriptorList(this, bookmarkStore);
        history = new BlobDescriptorList(this, historyStore);

        dictionaries.registerDataSetObserver(new DataSetObserver() {
            @Override
            synchronized public void onChanged() {
                lastResult.setData(new ArrayList<Slob.Blob>().iterator());
                slobber.setSlobs(null);
                for (SlobDescriptor sd : dictionaries) {
                    sd.close();
                }
                List<Slob> slobs = new ArrayList<Slob>();
                for (SlobDescriptor sd : dictionaries) {
                    Slob s = sd.open();
                    if (s != null) {
                        slobs.add(s);
                    }
                }
                slobber.setSlobs(slobs);
                lookup(lookupQuery);
                bookmarks.notifyDataSetChanged();
                history.notifyDataSetChanged();
            }
        });

        dictionaries.load();
        lookup(initialQuery, false);
        bookmarks.load();
        history.load();
    }

    private SharedPreferences prefs() {
        return this.getSharedPreferences("app", Activity.MODE_PRIVATE);
    }

    void push(Activity activity) {
        this.articleActivities.add(activity);
        Log.d(getClass().getName(), "Activity added, stack size " + this.articleActivities.size());
        if (this.articleActivities.size() > 3) {
            Log.d(getClass().getName(), "Max stack size exceeded, finishing oldest activity");
            this.articleActivities.get(0).finish();
        }
    }

    void pop(Activity activity) {
        this.articleActivities.remove(activity);
    }


    Iterator<Blob> find(String key) {
        return Slob.find(key, 1000, slobber.getSlobs());
    }

    Iterator<Blob> find(String key, String preferredSlobId) {
        long t0 = System.currentTimeMillis();
        Iterator<Blob> result = Slob.find(key, 10, slobber.getSlob(preferredSlobId),
                slobber.getSlobs(), Slob.Strength.QUATERNARY.level);
        Log.d(getClass().getName(), String.format("find ran in %dms", System.currentTimeMillis() - t0));
        return result;
    }

    Blob random() {
        return slobber.findRandom();
    }

    String getUrl(Blob blob) {
        return String.format("http://localhost:%s%s",
                port, Slobber.mkContentURL(blob));
    }

    Slob getSlob(String slobId) {
        return slobber.getSlob(slobId);
    }

    private Thread discoveryThread;

    synchronized void findDictionaries(
            final DictionaryDiscoveryCallback callback) {
        if (discoveryThread != null) {
            throw new RuntimeException(
                    "Dictionary discovery is already running");
        }
        dictionaries.clear();
        discoveryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                DictionaryFinder finder = new DictionaryFinder();
                final List<SlobDescriptor> result = finder.findDictionaries();
                discoveryThread = null;
                Handler h = new Handler(Looper.getMainLooper());
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        dictionaries.addAll(result);
                        callback.onDiscoveryFinished();
                    }
                });
            }
        });
        discoveryThread.start();
    }

    Slob findSlob(String slobOrUri) {
        return slobber.findSlob(slobOrUri);
    }

    String getSlobURI(String slobId) {
        return  slobber.getSlobURI(slobId);
    }


    void addBookmark(String contentURL) {
        bookmarks.add(contentURL);
    }

    void removeBookmark(String contentURL) {
        bookmarks.remove(contentURL);
    }

    boolean isBookmarked(String contentURL) {
        return bookmarks.contains(contentURL);
    }

    private void setLookupResult(String query, Iterator<Slob.Blob> data) {
        this.lastResult.setData(data);
        lookupQuery = query;
        SharedPreferences.Editor edit = prefs().edit();
        edit.putString("query", query);
        edit.apply();
    }

    String getLookupQuery() {
        return lookupQuery;
    }

    private AsyncTask<Void, Void, Iterator<Blob>> currentLookupTask;

    public void lookup(String query) {
        this.lookup(query, true);
    }

    private void lookup(final String query, boolean async) {
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
        }
        else {
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

    private void notifyLookupCanceled (String query) {
        for (LookupListener l : lookupListeners) {
            l.onLookupCanceled(query);
        }
    }

    private List<LookupListener> lookupListeners = new ArrayList<LookupListener>();

    void addLookupListener(LookupListener listener){
        lookupListeners.add(listener);
    }

    void removeLookupListener(LookupListener listener){
        lookupListeners.remove(listener);
    }

}
