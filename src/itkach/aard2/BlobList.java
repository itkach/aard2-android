package itkach.aard2;

import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.os.Handler;
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
        mainHandler.post(() -> {
            list.clear();
            dataSetObservable.notifyChanged();
        });
        this.iter = lookupResultsIter;
        loadChunkSync();
    }

    // Pulls the next chunk once a bound row nears the end of what's loaded.
    void maybeLoadMore(int position) {
        if (position >= list.size() - loadMoreThreshold) {
            loadChunk();
        }
    }

    private void loadChunk() {
        if (iter == null || !iter.hasNext()) {
            return;
        }
        executor.execute(this::loadChunkSync);
    }

    private void loadChunkSync() {
        long t0 = System.currentTimeMillis();
        int count = 0;
        final List<Slob.Blob> chunkList = new LinkedList<>();

        while (iter.hasNext() && count < chunkSize && list.size() <= maxSize) {
            count++;
            chunkList.add(iter.next());
        }

        mainHandler.post(() -> {
            list.addAll(chunkList);
            dataSetObservable.notifyChanged();
        });

        Log.d(TAG, String.format("Loaded chunk of %d (list size %d) in %d ms",
                count, list.size(), (System.currentTimeMillis() - t0)));
    }

    Slob.Blob get(int position) {
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
        return list.get(position);
    }
}
