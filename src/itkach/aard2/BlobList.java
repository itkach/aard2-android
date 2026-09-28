package itkach.aard2;

import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import itkach.slob.Slob;

// App-scoped holder for the current lookup result: the list of matching blobs,
// pulled from the lookup iterator in chunks as the list is scrolled. Mirrors
// BlobDescriptorList's role for bookmarks and history - the data lives here and
// is rendered by disposable, view-scoped BlobListAdapter instances (one per
// RecyclerView, one per article pager), each observing it for changes.
class BlobList implements BlobSource {

    private static final String TAG = BlobList.class.getSimpleName();

    private final Handler           mainHandler;
    private final ExecutorService   executor;
    private final List<Slob.Blob>   list;
    private final DataSetObservable dataSetObservable = new DataSetObservable();

    private Iterator<Slob.Blob> iter;

    private final int chunkSize;
    private final int loadMoreThreshold;
    private final int maxSize = 10000;

    BlobList(Context context) {
        this(context, 20, 10);
    }

    BlobList(Context context, int chunkSize, int loadMoreThreshold) {
        this.mainHandler = new Handler(context.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        this.list = new ArrayList<>(chunkSize);
        this.chunkSize = chunkSize;
        this.loadMoreThreshold = loadMoreThreshold;
    }

    void registerDataSetObserver(DataSetObserver observer) {
        dataSetObservable.registerObserver(observer);
    }

    void unregisterDataSetObserver(DataSetObserver observer) {
        dataSetObservable.unregisterObserver(observer);
    }

    void setData(Iterator<Slob.Blob> lookupResultsIter) {
        this.iter = lookupResultsIter;
        // Replace the previous results with the first chunk of the new ones in a
        // single update: clearing under its own notification would expose an empty
        // list to observers, and an open article's pager treats an empty result as
        // "closed" and finishes itself. Run synchronously when already on the main
        // thread (a lookup is), so lastResult reflects the results before setData
        // returns rather than only once the message queue drains - otherwise a
        // reader that samples it in between (e.g. an article restored on relaunch)
        // sees it empty.
        final List<Slob.Blob> chunkList = readChunk();
        runOnMain(() -> {
            list.clear();
            list.addAll(chunkList);
            dataSetObservable.notifyChanged();
        });
    }

    // Pulls the next chunk once an accessed position nears the end of what's
    // loaded. Called from the item accessors so every reader - the Lookup
    // RecyclerView and the article pager alike - keeps the list growing, not just
    // whichever view binds rows.
    private void maybeLoadMore(int position) {
        if (position >= list.size() - loadMoreThreshold) {
            loadChunk();
        }
    }

    private void loadChunk() {
        if (iter == null || !iter.hasNext()) {
            return;
        }
        executor.execute(() -> {
            final List<Slob.Blob> chunkList = readChunk();
            runOnMain(() -> {
                list.addAll(chunkList);
                dataSetObservable.notifyChanged();
            });
        });
    }

    // Drains up to chunkSize more items from the current result iterator.
    private List<Slob.Blob> readChunk() {
        long t0 = System.currentTimeMillis();
        final List<Slob.Blob> chunkList = new LinkedList<>();
        while (iter != null && iter.hasNext() && chunkList.size() < chunkSize
                && list.size() <= maxSize) {
            chunkList.add(iter.next());
        }
        Log.d(TAG, String.format("Read chunk of %d in %d ms",
                chunkList.size(), (System.currentTimeMillis() - t0)));
        return chunkList;
    }

    // Runs r on the main thread: inline when already there (so a lookup on the
    // main thread updates the list before it returns), otherwise posted.
    private void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
    }

    Slob.Blob get(int position) {
        maybeLoadMore(position);
        return list.get(position);
    }

    int size() {
        return list.size();
    }

    @Override
    public int getBlobCount() {
        return list.size();
    }

    @Override
    public Object getBlobItem(int position) {
        return get(position);
    }
}
