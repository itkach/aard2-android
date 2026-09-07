package itkach.aard2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import itkach.slob.Slob;

public class SlobDescriptorList extends BaseDescriptorList<SlobDescriptor> {

    private final Application            app;

    // The list is ordered solely by the user-arranged `order` field now
    // (drag to reorder); it is an explicit, persisted index.
    private final Comparator<SlobDescriptor> byOrder = new Comparator<SlobDescriptor>() {
        @Override
        public int compare(SlobDescriptor d1, SlobDescriptor d2) {
            return Integer.compare(d1.order, d2.order);
        }
    };

    // The pre-drag ordering rule, kept only to seed the initial `order` values
    // for descriptors saved before the `order` field existed: favourites first
    // (oldest first), then the rest by most-recently-accessed.
    private final Comparator<SlobDescriptor> legacyOrder = new Comparator<SlobDescriptor>() {
        @Override
        public int compare(SlobDescriptor d1, SlobDescriptor d2) {
            if (d1.priority == 0 && d2.priority == 0) {
                return Util.compare(d2.lastAccess, d1.lastAccess);
            }
            if (d1.priority == 0 && d2.priority > 0) {
                return 1;
            }
            if (d1.priority > 0 && d2.priority == 0) {
                return -1;
            }
            return Util.compare(d1.priority, d2.priority);
        }
    };

    SlobDescriptorList(Application app, DescriptorStore<SlobDescriptor> store) {
        super(SlobDescriptor.class, store);
        this.app = app;
    }

    Slob resolve(SlobDescriptor sd) {
        return this.app.getSlob(sd.id);
    }

    void sort() {
        Util.sort(this, byOrder);
    }

    // The order to give a freshly added dictionary so it lands at the end.
    int nextOrder() {
        int max = -1;
        for (SlobDescriptor d : this) {
            max = Math.max(max, d.order);
        }
        return max + 1;
    }

    // Persist the current list positions as each descriptor's order. Called once
    // when a drag settles (BaseDescriptorList.move already rearranged the list).
    void commitOrder() {
        for (int i = 0; i < size(); i++) {
            SlobDescriptor d = get(i);
            if (d.order != i) {
                d.order = i;
                save(d);
            }
        }
    }

    // One-time migration for descriptors saved before `order` existed: give them
    // order values following the old favourites-first arrangement, and carry the
    // old favourite flag over to useForRandomLookup. No-op once every descriptor
    // has an order.
    private void migrate() {
        List<SlobDescriptor> unmigrated = new ArrayList<>();
        int max = -1;
        for (SlobDescriptor d : this) {
            if (d.order < 0) {
                unmigrated.add(d);
            } else {
                max = Math.max(max, d.order);
            }
        }
        if (unmigrated.isEmpty()) {
            return;
        }
        Util.sort(unmigrated, legacyOrder);
        int next = max + 1;
        for (SlobDescriptor d : unmigrated) {
            d.order = next++;
            d.useForRandomLookup = d.priority > 0;
            save(d);
        }
    }

    @Override
    void load() {
        beginUpdate();
        super.load();
        migrate();
        sort();
        endUpdate(true);
    }
}
