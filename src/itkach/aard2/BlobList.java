package itkach.aard2;

import android.content.Context;
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

    // Distinguishes a full reset (a new query's results) from an append (a chunk
    // loaded onto the current results), so the adapter can do an incremental
    // RecyclerView update for appends instead of rebinding everything - the latter
    // stutters the list when chunks load mid-scroll.
    interface Listener {
        void onReset();
        void onInserted(int positionStart, int itemCount);
    }

    private final Handler           mainHandler;
    private final ExecutorService   executor;
    private final List<Slob.Blob>   list;
    // Main thread only, so a plain list; snapshot on notify to tolerate a listener
    // unregistering itself while being notified.
    private final List<Listener>    listeners = new ArrayList<>();

    private Iterator<Slob.Blob> iter;
    // Whether a chunk load is in flight. Main thread only (set true when a load is
    // scheduled, false when its results are merged in), so no synchronization.
    private boolean loadingChunk;

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

    void registerListener(Listener l) {
        listeners.add(l);
    }

    void unregisterListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyReset() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.onReset();
        }
    }

    private void notifyInserted(int positionStart, int itemCount) {
        if (itemCount <= 0) {
            return;
        }
        for (Listener l : new ArrayList<>(listeners)) {
            l.onInserted(positionStart, itemCount);
        }
    }

    void setData(Iterator<Slob.Blob> lookupResultsIter) {
        this.iter = lookupResultsIter;
        // Fresh result set: release the in-flight guard from the previous one. A
        // chunk still loading for it reads its own captured iterator (not this new
        // one) and, seeing iter has since been swapped, discards its result - so it
        // can neither advance this iterator concurrently nor merge into this list.
        loadingChunk = false;
        // Replace the previous results with the first chunk of the new ones in a
        // single update: clearing under its own notification would expose an empty
        // list to observers, and an open article's pager treats an empty result as
        // "closed" and finishes itself. Run synchronously when already on the main
        // thread (a lookup is), so lastResult reflects the results before setData
        // returns rather than only once the message queue drains - otherwise a
        // reader that samples it in between (e.g. an article restored on relaunch)
        // sees it empty.
        final List<Slob.Blob> chunkList = readChunk(iter);
        runOnMain(() -> {
            list.clear();
            list.addAll(chunkList);
            notifyReset();
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
        // One chunk in flight at a time: the several get() calls around a single
        // swipe near the end would otherwise each queue a chunk, and every chunk's
        // notification rebuilds the observing views. Cleared before notifying, so a
        // reader still short of the end can pull the next chunk right after.
        if (loadingChunk || iter == null || !iter.hasNext()) {
            return;
        }
        loadingChunk = true;
        // Capture the iterator this load is for: setData can swap iter to a new
        // query's while this runs, and reading the shared field on the executor
        // would then advance the new iterator from two threads at once. If it was
        // swapped, discard this result rather than merge the old query's items into
        // the new list (and leave loadingChunk to setData, which already cleared it).
        final Iterator<Slob.Blob> taskIter = iter;
        executor.execute(() -> {
            final List<Slob.Blob> chunkList = readChunk(taskIter);
            runOnMain(() -> {
                if (taskIter != iter) {
                    return;
                }
                int start = list.size();
                list.addAll(chunkList);
                loadingChunk = false;
                notifyInserted(start, chunkList.size());
            });
        });
    }

    // Drains up to chunkSize more items from the given result iterator. Takes the
    // iterator as an argument rather than reading the field, so a background load
    // keeps advancing the iterator it was started for even if setData swaps in a
    // new query's iterator meanwhile.
    private List<Slob.Blob> readChunk(Iterator<Slob.Blob> it) {
        long t0 = System.currentTimeMillis();
        final List<Slob.Blob> chunkList = new LinkedList<>();
        while (it != null && it.hasNext() && chunkList.size() < chunkSize
                && list.size() <= maxSize) {
            chunkList.add(it.next());
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
