package itkach.aard2;

import android.app.Activity;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import itkach.fdrawable.TypefaceManager;
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

    private String                          lookupQuery;

    private List<Activity>                  articleActivities;

    @Override
    public void onCreate() {
        super.onCreate();
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

                //FIXME run in background, set LookupFragment's busy indicator
                if (lookupQuery != null && !lookupQuery.equals("")) {
                    Iterator<Slob.Blob> result = find(lookupQuery);
                    lastResult.setData(result);
                }
                bookmarks.notifyDataSetChanged();
                history.notifyDataSetChanged();
            }
        });

        dictionaries.load();
        bookmarks.load();
        history.load();
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
                slobber.getSlobs());
        Log.d(getClass().getName(), String.format("find ran in %dms", System.currentTimeMillis() - t0));
        return result;
    }

    Iterator<Blob> findExact(String key, String slobId) {
        Slob slob = slobber.getSlob(slobId);
        return slob.find(key, Slob.Strength.IDENTICAL);
    }

    String getUrl(Blob blob) {
        return String.format(
                "http://localhost:%s/content/%s?slob=%s&blob=%s#%s", port,
                Uri.encode(blob.key), blob.owner.getId(), blob.id,
                blob.fragment);
    }

    String getUrl(String key) {
        return String.format("http://localhost:%s/content/%s", port,
                Uri.encode(key));
    }

    Slob getSlob(String slobId) {
        return slobber.getSlob(slobId);
    }

    List<Slob> getSlobs() {
        return slobber.getSlobs();
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
                        //slobber.setSlobs(result);
                        callback.onDiscoveryFinished();
                    }
                });
            }
        });
        discoveryThread.start();
    }


    String getURI(Slob slob) {
        Map<String, String> tags = slob.getTags();
        String uri = tags.get("uri");
        if (uri == null) {
            uri = "slob:" + slob.getId();
        }
        return uri;
    }

    Slob findSlob(String slobURI) {
        List<Slob> slobs = getSlobs();
        for (Slob s : slobs) {
            if (getURI(s).equals(slobURI)) {
                return s;
            }
        }
        return null;
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

    void setLookupResult(String query, Iterator<Slob.Blob> data) {
        this.lastResult.setData(data);
        lookupQuery = query;
    }

    String getLookupQuery() {
        return lookupQuery;
    }
}
