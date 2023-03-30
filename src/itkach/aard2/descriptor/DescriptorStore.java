package itkach.aard2.descriptor;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DescriptorStore<T extends BaseDescriptor> {
    static final String TAG = DescriptorStore.class.getSimpleName();

    private final File dir;
    private final ObjectMapper mapper;

    public DescriptorStore(@NonNull ObjectMapper mapper, @NonNull File dir) {
        this.dir = dir;
        this.mapper = mapper;
    }

    public List<T> load(@NonNull Class<T> type) {
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

    public void save(@NonNull List<T> lst) {
        for (T item : lst) {
            save(item);
        }
    }

    public void save(@NonNull T item) {
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

    public boolean delete(@Nullable String itemId) {
        if (itemId == null) {
            return false;
        }
        return new File(dir, itemId).delete();
    }

}
