package itkach.aard2;

import itkach.fdrawable.Icon;
import itkach.fdrawable.IconicFontDrawable;
import itkach.slob.Slob;
import itkach.slob.Slob.Blob;
import itkach.slobber.Slobber;

import itkach.fdrawable.TypefaceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Application extends android.app.Application {



    private Slobber                         slobber;

    BlobDescriptorList                      bookmarks;
    BlobDescriptorList                      history;

    private int                             port = 8013;

    BlobListAdapter                         lastResult;
    DictionaryListAdapter                   dictionaryList;

    private DescriptorStore<BlobDescriptor> bookmarkStore;
    private DescriptorStore<BlobDescriptor> historyStore;
    private DescriptorStore<SlobDescriptor> dictStore;

    private ObjectMapper                    mapper;

    private TypefaceManager                 typefaceManager;


    @Override
    public void onCreate() {
        super.onCreate();

        typefaceManager = new TypefaceManager(getAssets());

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
        dictionaryList = new DictionaryListAdapter(this);
        // List<File> dictFiles = loadDictFileList();
        List<SlobDescriptor> slobDescList = loadDictList();
        DictionaryFinder finder = new DictionaryFinder();
        List<File> dictFiles = new ArrayList<File>();
        for (SlobDescriptor sd : slobDescList) {
            dictFiles.add(new File(sd.path));
        }
        final List<Slob> result = finder.open(dictFiles);
        slobber.setSlobs(result);
        dictionaryList.setData(slobDescList);


        bookmarks = new BlobDescriptorList(this, bookmarkStore);
        bookmarks.load();
        history = new BlobDescriptorList(this, historyStore);
        history.load();
    }

    Iterator<Blob> find(String key) {
        return Slob.find(key, 1000, slobber.getSlobs());
    }

    Iterator<Blob> find(String key, String preferredSlobId) {
        return Slob.find(key, 10, slobber.getSlob(preferredSlobId),
                slobber.getSlobs());
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
        discoveryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                DictionaryFinder finder = new DictionaryFinder();
                final List<Slob> result = finder.findDictionaries();
                discoveryThread = null;
                Handler h = new Handler(Looper.getMainLooper());
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        saveDictList(result);
                        slobber.setSlobs(result);
                        callback.onDiscoveryFinished();
                    }
                });
            }
        });
        discoveryThread.start();
    }

    // private SharedPreferences prefs() {
    // SharedPreferences prefs = getSharedPreferences("aard2", MODE_PRIVATE);
    // return prefs;
    // }

    private void saveDictList(List<Slob> slobs) {
        List<SlobDescriptor> descriptors = new ArrayList<SlobDescriptor>(
                slobs.size());
        for (Slob s : slobs) {
            SlobDescriptor sd = new SlobDescriptor();
            sd.id = s.getId().toString();
            sd.path = s.file.getAbsolutePath();
            sd.tags = s.getTags();
            sd.blobCount = s.size();
            descriptors.add(sd);
        }
        dictStore.save(descriptors);
        dictionaryList.setData(descriptors);
    }

    private List<SlobDescriptor> loadDictList() {
        return dictStore.load(SlobDescriptor.class);
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

    Slob slobFromArticleURL(String articleURL) {
        Uri uri = Uri.parse(articleURL);
        String slobId = uri.getQueryParameter("slob");
        Slob slob = this.getSlob(slobId);
        return slob;
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
    }

    List<Slob.Blob> getBookmarkedBlobList() {
        List<Slob.Blob> result = new ArrayList<Slob.Blob>();
        for (BlobDescriptor bd : bookmarks) {
            result.add(bookmarks.resolve(bd));
        }
        return result;
    }

    IconicFontDrawable getIcon(int codePoint) {
        Typeface font = typefaceManager.get("fontawesome-4.1.0.ttf");
        return new IconicFontDrawable(new Icon(font, codePoint));
    }
}
