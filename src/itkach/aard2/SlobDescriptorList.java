package itkach.aard2;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import itkach.aard2.utils.Utils;
import itkach.slob.Slob;

public class SlobDescriptorList extends BaseDescriptorList<SlobDescriptor> {

    SlobDescriptorList(@NonNull DescriptorStore<SlobDescriptor> store) {
        super(SlobDescriptor.class, store);
    }

    public boolean hasId(@Nullable String id) {
        if (id == null) {
            return false;
        }
        for (SlobDescriptor d : this) {
            if (id.equals(d.id)) {
                return true;
            }
        }
        return false;
    }

    public Slob resolve(SlobDescriptor sd) {
        return SlobHelper.getInstance().getSlob(sd.id);
    }

    public void sort() {
        Utils.sort(this, (d1, d2) -> {
            //Dictionaries that are unfavorited
            //go immediately after favorites
            if (d1.priority == 0 && d2.priority == 0) {
                return Long.compare(d2.lastAccess, d1.lastAccess);
            }
            //Favorites are always above other
            if (d1.priority == 0 && d2.priority > 0) {
                return 1;
            }
            if (d1.priority > 0 && d2.priority == 0) {
                return -1;
            }
            //Old favorites are above more recent ones
            return Long.compare(d1.priority, d2.priority);
        });
    }

    @Override
    void load() {
        beginUpdate();
        super.load();
        sort();
        endUpdate(true);
    }
}
