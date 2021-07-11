package itkach.aard2;

import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class DescriptorStore<T extends BaseDescriptor> {

    static final String TAG = DescriptorStore.class.getSimpleName();

    private File         dir;
    private ObjectMapper mapper;

    DescriptorStore(ObjectMapper mapper, File dir) {
        this.dir = dir;
        this.mapper = mapper;
    }

    List<T> load(Class<T> type) {
        List<T> result = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
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
            mapper.writeValue(new File(dir, item.id), item);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    boolean delete(String itemId) {
        if (itemId == null) {
            return false;
        }
        return new File(dir, itemId).delete();
    }

}
