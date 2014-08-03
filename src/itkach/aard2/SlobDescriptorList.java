package itkach.aard2;

import java.util.AbstractList;

import itkach.slob.Slob;

public class SlobDescriptorList extends BaseDescriptorList<SlobDescriptor> {

    private final Application app;

    SlobDescriptorList(Application app, DescriptorStore<SlobDescriptor> store) {
        super(SlobDescriptor.class, store);
        this.app = app;
    }

    Slob resolve(SlobDescriptor sd) {
        return this.app.getSlob(sd.id);
    }
}
