package itkach.aard2;

import java.util.Collections;
import java.util.Comparator;

import itkach.slob.Slob;

public class SlobDescriptorList extends BaseDescriptorList<SlobDescriptor> {

    private final Application            app;
    private Comparator<SlobDescriptor>   comparator;

    SlobDescriptorList(Application app, DescriptorStore<SlobDescriptor> store) {
        super(SlobDescriptor.class, store);
        this.app = app;
        comparator = new Comparator<SlobDescriptor>() {
            @Override
            public int compare(SlobDescriptor d1, SlobDescriptor d2) {
                //Dictionaries that are unfavorited
                //go immediately after favorites
                if (d1.priority == 0 && d2.priority == 0) {
                    return cmp(d2.lastAccess, d1.lastAccess);
                }
                //Favorites are always above other
                if (d1.priority == 0 && d2.priority > 0) {
                    return 1;
                }
                if (d1.priority > 0 && d2.priority == 0) {
                    return -1;
                }
                //Old favorites are above more recent ones
                return cmp(d1.priority, d2.priority);
            }
        };
    }

    private static int cmp(long lhs, long rhs) {
        return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
    }

    Slob resolve(SlobDescriptor sd) {
        return this.app.getSlob(sd.id);
    }

    void sort() {
        Collections.sort(this, comparator);
    }

    @Override
    void load() {
        //FIXME change notification fired twice, once for load, once for sort
        super.load();
        sort();
    }
}
