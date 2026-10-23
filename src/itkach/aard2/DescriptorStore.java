package itkach.aard2;

import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class DescriptorStore<T extends BaseDescriptor> {

    static final String TAG = DescriptorStore.class.getSimpleName();

    // Suffix for the temporary file save() writes before atomically renaming it
    // into place. load() skips these so a reader never parses a half-written one.
    private static final String TMP_SUFFIX = ".tmp";

    private File         dir;
    private ObjectMapper mapper;
    // Runs the writes made with saveLater and deleteLater, one at a time, in
    // the order they're made.
    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    DescriptorStore(ObjectMapper mapper, File dir) {
        this.dir = dir;
        this.mapper = mapper;
    }

    // Reads every item, after the writes made with saveLater and deleteLater
    // before this call are done.
    List<T> load(Class<T> type) {
        awaitWrites();
        List<T> result = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                // A save() in progress (or left behind by a crash mid-save); not a
                // descriptor. Skipping it keeps the delete-on-parse-failure below
                // from ever deleting a file that's simply being written.
                if (f.getName().endsWith(TMP_SUFFIX)) {
                    continue;
                }
                try {
                    T sd = mapper.readValue(f, type);
                    result.add(sd);
                } catch (Exception e) {
                    String path = f.getAbsolutePath();
                    Log.w(TAG, String.format("Loading data from file %s failed", path), e);
                    boolean deleted = f.delete();
                    Log.w(TAG, String.format("Attempt to delete corrupted file %s succeeded? %s",
                            path, deleted));
                }
            }
        }
        return result;
    }

    void save(List<T> lst) {
        for (T item : lst) {
            save(item);
        }
    }

    void save(T item) {
        if (item.id == null) {
            Log.d(getClass().getName(), "Can't save item without id");
            return;
        }
        try {
            write(item.id, mapper.writeValueAsBytes(item));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Saves item off the calling thread, as it is now: changes made to it
    // after this call aren't saved. A failure (e.g. the disk is full) is
    // logged.
    void saveLater(T item) {
        if (item.id == null) {
            Log.d(getClass().getName(), "Can't save item without id");
            return;
        }
        String id = item.id;
        byte[] json;
        try {
            json = mapper.writeValueAsBytes(item);
        } catch (IOException e) {
            Log.w(TAG, String.format("Failed to save %s", id), e);
            return;
        }
        writer.execute(() -> {
            try {
                write(id, json);
            } catch (Exception e) {
                Log.w(TAG, String.format("Failed to save %s", id), e);
            }
        });
    }

    // Deletes the item with this id off the calling thread.
    void deleteLater(String itemId) {
        writer.execute(() -> delete(itemId));
    }

    // Waits until the writes made with saveLater and deleteLater before this
    // call are done.
    void awaitWrites() {
        try {
            writer.submit(() -> {}).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void write(String id, byte[] json) throws IOException {
        // Write to a temp file and rename it into place, so a concurrent reader
        // (the background load at startup) never sees a partially-written file -
        // which load() would treat as corrupt and delete. renameTo is atomic
        // within a filesystem, and the temp lives in the same dir as the target.
        File target = new File(dir, id);
        File tmp = null;
        try {
            tmp = File.createTempFile(id + "-", TMP_SUFFIX, dir);
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json);
            }
            if (!tmp.renameTo(target)) {
                throw new IOException(
                        String.format("Failed to rename %s to %s", tmp, target));
            }
            tmp = null;
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    boolean delete(String itemId) {
        if (itemId == null) {
            return false;
        }
        return new File(dir, itemId).delete();
    }

}
