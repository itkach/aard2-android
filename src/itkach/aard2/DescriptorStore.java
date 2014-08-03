package itkach.aard2;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

class DescriptorStore<T extends BaseDescriptor> {

    private File         dir;
    private ObjectMapper mapper;

    DescriptorStore(ObjectMapper mapper, File dir) {
        this.dir = dir;
        this.mapper = mapper;
    }

    List<T> load(Class<T> type) {
        List<T> result = new ArrayList<T>();
        File[] files = dir.listFiles();
        if (files != null) {
            try {
                for (File f : files) {
                    T sd = mapper.readValue(f, type);
                    result.add(sd);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
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
        return new File(dir, itemId).delete();
    }

}
