package itkach.aard2;

import android.app.Activity;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import itkach.slob.Slob;
import itkach.slob.Slob.Blob;
import itkach.slobber.Slobber;

public class Application extends android.app.Application {

    public static final String LOCALHOST = "127.0.0.1";
    public static final String CONTENT_URL_TEMPLATE = "http://" + LOCALHOST + ":%s%s";

    private Slobber                         slobber;

    BlobDescriptorList                      bookmarks;
    BlobDescriptorList                      history;
    SlobDescriptorList                      dictionaries;

    private static int                      PREFERRED_PORT = 8013;
    private int                             port = -1;

    BlobListAdapter                         lastResult;

    private DescriptorStore<BlobDescriptor> bookmarkStore;
    private DescriptorStore<BlobDescriptor> historyStore;
    private DescriptorStore<SlobDescriptor> dictStore;

    private ObjectMapper                    mapper;

    private String                          lookupQuery = "";

    private List<Activity>                  articleActivities;

    static String jsStyleSwitcher;
    static String jsUserStyle;
    static String jsClearUserStyle;
    static String jsSetCannedStyle;

    private static final String PREF                    = "app";
    static final String PREF_RANDOM_FAV_LOOKUP          = "onlyFavDictsForRandomLookup";
    static final String PREF_UI_THEME                   = "UITheme";
    static final String PREF_UI_THEME_LIGHT             = "light";
    static final String PREF_UI_THEME_DARK              = "dark";
    static final String PREF_USE_VOLUME_FOR_NAV         = "useVolumeForNav";
    static final String PREF_AUTO_PASTE                 = "autoPaste";

    private static final String TAG = Application.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        if(Build.VERSION.SDK_INT >= 19) {
            try {
                Method setWebContentsDebuggingEnabledMethod = WebView.class.getMethod(
                        "setWebContentsDebuggingEnabled", boolean.class);
                setWebContentsDebuggingEnabledMethod.invoke(null, true);
            } catch (NoSuchMethodException e1) {
                Log.d(TAG,
                        "setWebContentsDebuggingEnabledMethod method not found");
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        articleActivities = Collections.synchronizedList(new ArrayList<Activity>());

        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        dictStore = new DescriptorStore<SlobDescriptor>(mapper, getDir("dictionaries", MODE_PRIVATE));
        bookmarkStore = new DescriptorStore<BlobDescriptor>(mapper, getDir(
                "bookmarks", MODE_PRIVATE));
        historyStore = new DescriptorStore<BlobDescriptor>(mapper, getDir(
                "history", MODE_PRIVATE));
        slobber = new Slobber();

        long t0 = System.currentTimeMillis();

        startWebServer();

        Log.d(TAG, String.format("Started web server on port %d in %d ms",
                port, (System.currentTimeMillis() - t0)));
        try {
            InputStream is;
            is = getClass().getClassLoader().getResourceAsStream("styleswitcher.js");
            jsStyleSwitcher = readTextFile(is, 0);
            is = getAssets().open("userstyle.js");
            jsUserStyle = readTextFile(is, 0);
            is = getAssets().open("clearuserstyle.js");
            jsClearUserStyle = readTextFile(is, 0);
            is = getAssets().open("setcannedstyle.js");
            jsSetCannedStyle = readTextFile(is, 0);
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
                List<Slob> slobs = new ArrayList<Slob>();
                for (SlobDescriptor sd : dictionaries) {
                    String origSlobId = sd.id;
                    Slob s = sd.load(getApplicationContext());
                    if (s != null) {
                        if (!origSlobId.equals(sd.id)) {
                            Log.d(TAG, String.format("%s has been replaced, updating dict store %s -> %s", sd.path, origSlobId, sd.id));
                            //dictionary file has been replaced
                            //(same file name, different slob uuid)
                            //need to update store accordingly
                            dictStore.delete(origSlobId);
                            dictStore.save(sd);
                        }
                        slobs.add(s);
                    }
                }
                slobber.setSlobs(slobs);

                new EnableLinkHandling().execute(getActiveSlobs());

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

    static String readTextFile(InputStream is, int maxSize) throws IOException, FileTooBigException {
        InputStreamReader reader = new InputStreamReader(is, "UTF-8");
        StringWriter sw = new StringWriter();
        char[] buf = new char[16384];
        int count = 0;
        while (true) {
            int read = reader.read(buf);
            if (read == -1) {
                break;
            }
            count += read;
            if (maxSize > 0 && count > maxSize) {
                throw new FileTooBigException();
            }
            sw.write(buf, 0, read);
        }
        reader.close();
        return sw.toString();
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
            Set<Integer> seen = new HashSet<Integer>();
            seen.add(PREFERRED_PORT);
            Random rand = new Random();
            int attemptCount = 0;
            while (true) {
                int value = 1 + (int)Math.floor((65535-1025)*rand.nextDouble());
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

    SharedPreferences prefs() {
        return this.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
    }

    String getPreferredTheme() {
        return prefs().getString(Application.PREF_UI_THEME,
                Application.PREF_UI_THEME_LIGHT);
    }

    void installTheme(Activity activity) {
        String theme = getPreferredTheme();
        if (theme.equals(PREF_UI_THEME_DARK)) {
            activity.setTheme(android.R.style.Theme_Holo);
        }
        else {
            activity.setTheme(android.R.style.Theme_Holo_Light_DarkActionBar);
        }
    }

    void push(Activity activity) {
        this.articleActivities.add(activity);
        Log.d(TAG, "Activity added, stack size " + this.articleActivities.size());
        if (this.articleActivities.size() > 3) {
            Log.d(TAG, "Max stack size exceeded, finishing oldest activity");
            this.articleActivities.get(0).finish();
        }
    }

    void pop(Activity activity) {
        this.articleActivities.remove(activity);
    }


    Slob[] getActiveSlobs() {
        List<Slob> result = new ArrayList(dictionaries.size());
        for (SlobDescriptor sd : dictionaries) {
            if (sd.active) {
                Slob s = slobber.getSlob(sd.id);
                if (s != null) {
                    result.add(s);
                }
            }
        }
        return result.toArray(new Slob[result.size()]);
    };

    Slob[] getFavoriteSlobs() {
        List<Slob> result = new ArrayList(dictionaries.size());
        for (SlobDescriptor sd : dictionaries) {
            if (sd.active && sd.priority > 0) {
                Slob s = slobber.getSlob(sd.id);
                if (s != null) {
                    result.add(s);
                }
            }
        }
        return result.toArray(new Slob[result.size()]);
    };


    Iterator<Blob> find(String key) {
        return Slob.find(key, getActiveSlobs());
    }

    Iterator<Blob> find(String key, String preferredSlobId) {
        //When following links we want to consider all dictionaries
        //including the ones user turned off
        return find(key, preferredSlobId, false);
    }

    Slob.PeekableIterator<Blob> find(String key, String preferredSlobId, boolean activeOnly) {
        return this.find(key, preferredSlobId, activeOnly, null);
    }

    Slob.PeekableIterator<Blob> find(String key, String preferredSlobId, boolean activeOnly, Slob.Strength upToStrength) {
        long t0 = System.currentTimeMillis();
        Slob[] slobs = activeOnly ? getActiveSlobs() : slobber.getSlobs();
        Slob.PeekableIterator<Blob> result = Slob.find(key, slobs, slobber.findSlob(preferredSlobId), upToStrength);
        Log.d(TAG, String.format("find ran in %dms", System.currentTimeMillis() - t0));
        return result;
    }

    boolean isOnlyFavDictsForRandomLookup() {
        final SharedPreferences prefs = prefs();
        return prefs.getBoolean(Application.PREF_RANDOM_FAV_LOOKUP, false);
    }

    void setOnlyFavDictsForRandomLookup(boolean value) {
        final SharedPreferences prefs = prefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Application.PREF_RANDOM_FAV_LOOKUP, value);
        editor.commit();
    }

    Blob random() {
        Slob[] slobs = isOnlyFavDictsForRandomLookup() ? getFavoriteSlobs() : getActiveSlobs();
        return slobber.findRandom(slobs);
    }

    boolean useVolumeForNav() {
        final SharedPreferences prefs = prefs();
        return prefs.getBoolean(Application.PREF_USE_VOLUME_FOR_NAV, true);
    }

    void setUseVolumeForNav(boolean value) {
        final SharedPreferences prefs = prefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Application.PREF_USE_VOLUME_FOR_NAV, value);
        editor.commit();
    }

    boolean autoPaste() {
        final SharedPreferences prefs = prefs();
        return prefs.getBoolean(Application.PREF_AUTO_PASTE, false);
    }

    void setAutoPaste(boolean value) {
        final SharedPreferences prefs = prefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Application.PREF_AUTO_PASTE, value);
        editor.commit();
    }


    String getUrl(Blob blob) {
        return String.format(CONTENT_URL_TEMPLATE,
                port, Slobber.mkContentURL(blob));
    }

    Slob getSlob(String slobId) {
        return slobber.getSlob(slobId);
    }

    synchronized boolean addDictionary(Uri uri) {
        SlobDescriptor newDesc = SlobDescriptor.fromUri(getApplicationContext(), uri.toString());
        if (newDesc.id != null) {
            for (SlobDescriptor d: dictionaries) {
                if (d.id != null && d.id.equals(newDesc.id)) {
                    return true;
                }
            }
        }
        dictionaries.add(newDesc);
        return false;
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

    void lookup(String query) {
        this.lookup(query, true);
    }

    void lookup(final String query, boolean async) {
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


    static class FileTooBigException extends IOException {
    }


    private class EnableLinkHandling extends AsyncTask<Slob, Void, Void> {

        @Override
        protected Void doInBackground(Slob[] slobs) {
            Set<String> hosts = new HashSet<String>();
            for (Slob slob : slobs) {
                try {
                    String uriValue = slob.getTags().get("uri");
                    Uri uri = Uri.parse(uriValue);
                    String host = uri.getHost();
                    if (host != null) {
                        hosts.add(host.toLowerCase());
                    }
                }
                catch (Exception ex) {
                    Log.w(TAG, String.format("Dictionary %s (%s) has no uri tag", slob.getId(), slob.getTags()), ex);
                }
            }

            long t0 = System.currentTimeMillis();
            String packageName = getPackageName();
            try {
                PackageManager pm = getPackageManager();
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
                        pm.setComponentEnabledSetting(new ComponentName(getApplicationContext(), activityInfo.name),
                                setting, PackageManager.DONT_KILL_APP);
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
