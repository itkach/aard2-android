package itkach.aard2.lookup;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import itkach.aard2.utils.ThreadUtils;
import itkach.slob.Slob;

public class LookupResult {
    public static final String TAG = LookupResult.class.getSimpleName();
    private static final int MAX_SIZE = 10000;

    private final int chunkSize;
    private final int loadingThreshold;
    private final DataSetObservable dataSetObservable;

    private final List<Slob.Blob> chunkList;
    private Iterator<Slob.Blob> resultIterator;

    public LookupResult() {
        this(20, 10);
    }

    public LookupResult(int chunkSize, int loadingThreshold) {
        this.chunkList = Collections.synchronizedList(new ArrayList<>(chunkSize));
        this.chunkSize = chunkSize;
        this.loadingThreshold = loadingThreshold;
        this.dataSetObservable = new DataSetObservable();
    }

    @AnyThread
    public void registerDataSetObserver(@NonNull DataSetObserver observer) {
        dataSetObservable.registerObserver(observer);
    }

    @AnyThread
    public void unregisterDataSetObserver(@NonNull DataSetObserver observer) {
        dataSetObservable.unregisterObserver(observer);
    }

    @WorkerThread
    public void setResult(@NonNull Iterator<Slob.Blob> resultIterator) {
        chunkList.clear();
        dataSetObservable.notifyChanged();
        // Update data
        this.resultIterator = resultIterator;
        loadChunksSync();
    }

    @AnyThread
    public List<Slob.Blob> getList() {
        return chunkList;
    }

    @AnyThread
    public void loadMoreItems(int index) {
        if (resultIterator.hasNext() && index >= (chunkList.size() - loadingThreshold)) {
            ThreadUtils.postOnBackgroundThread(this::loadChunksSync);
        }
    }

    @WorkerThread
    private void loadChunksSync() {
        long t0 = System.currentTimeMillis();
        int count = 0;
        final List<Slob.Blob> chunkList = new LinkedList<>();

        while (resultIterator.hasNext() && count < chunkSize && this.chunkList.size() <= MAX_SIZE) {
            count++;
            Slob.Blob b = resultIterator.next();
            chunkList.add(b);
        }

        this.chunkList.addAll(chunkList);
        dataSetObservable.notifyChanged();

        Log.d(TAG, String.format("Loaded chunk of %d (adapter size %d) in %d ms",
                count, this.chunkList.size(), (System.currentTimeMillis() - t0)));
    }
}
