package itkach.aard2.article;

import android.database.DataSetObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import itkach.aard2.descriptor.BlobDescriptor;
import itkach.aard2.BlobDescriptorList;
import itkach.slob.Slob;

class BlobDescriptorListWrapper implements BlobListWrapper {
    private final BlobDescriptorList blobDescriptorList;

    BlobDescriptorListWrapper(@NonNull BlobDescriptorList blobDescriptorList) {
        this.blobDescriptorList = blobDescriptorList;
    }

    @Override
    public void registerDataSetObserver(@NonNull DataSetObserver observer) {
        blobDescriptorList.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(@NonNull DataSetObserver observer) {
        blobDescriptorList.unregisterDataSetObserver(observer);
    }

    @Override
    @Nullable
    public Slob.Blob get(int index) {
        BlobDescriptor item = blobDescriptorList.get(index);
        return item != null ? blobDescriptorList.resolve(item) : null;
    }

    @Override
    @Nullable
    public String getLabel(int index) {
        BlobDescriptor item = blobDescriptorList.get(index);
        return item != null ? item.key : null;
    }

    @Override
    public int size() {
        return blobDescriptorList.size();
    }
}
