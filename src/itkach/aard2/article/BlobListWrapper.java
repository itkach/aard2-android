package itkach.aard2.article;

import android.database.DataSetObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import itkach.slob.Slob;

interface BlobListWrapper {
    void registerDataSetObserver(@NonNull DataSetObserver observer);

    void unregisterDataSetObserver(@NonNull DataSetObserver observer);

    @Nullable
    Slob.Blob get(int index);

    @Nullable
    CharSequence getLabel(int index);

    int size();
}
