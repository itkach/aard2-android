package itkach.aard2;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import itkach.aard2.descriptor.BaseDescriptor;
import itkach.aard2.descriptor.DescriptorStore;

public abstract class BaseDescriptorList<T extends BaseDescriptor> extends AbstractList<T> {
    private final DataSetObservable dataSetObservable;
    private final DescriptorStore<T> store;
    private final List<T> list;
    private final Class<T> typeParameterClass;

    private int updating;

    BaseDescriptorList(Class<T> typeParameterClass, DescriptorStore<T> store) {
        this.typeParameterClass = typeParameterClass;
        this.store = store;
        this.dataSetObservable = new DataSetObservable();
        this.list = new ArrayList<>();
        this.updating = 0;
    }

    public void registerDataSetObserver(DataSetObserver observer) {
        this.dataSetObservable.registerObserver(observer);
    }

    public void unregisterDataSetObserver(DataSetObserver observer) {
        this.dataSetObservable.unregisterObserver(observer);
    }

    public void beginUpdate() {
        Log.d(getClass().getName(), "beginUpdate");
        this.updating++;
    }

    public void endUpdate(boolean changed) {
        Log.d(getClass().getName(), "endUpdate, changed? " + changed);
        this.updating--;
        if (changed) {
            notifyChanged();
        }
    }

    protected void notifyChanged() {
        if (this.updating == 0) {
            this.dataSetObservable.notifyChanged();
        }
    }

    public void load() {
        addAll(this.store.load(typeParameterClass));
    }

    @Override
    public T get(int i) {
        return list.get(i);
    }

    @Override
    public T set(int location, T object) {
        T result = list.set(location, object);
        this.store.save(object);
        notifyChanged();
        return result;
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public void add(int location, T object) {
        list.add(location, object);
        store.save(object);
        notifyChanged();
    }

    @Override
    public boolean addAll(int location, @NonNull Collection<? extends T> collection) {
        beginUpdate();
        boolean result = super.addAll(location, collection);
        endUpdate(result);
        return result;
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends T> collection) {
        beginUpdate();
        boolean result = super.addAll(collection);
        endUpdate(result);
        return result;
    }

    @Override
    public T remove(int location) {
        T result = list.remove(location);
        this.store.delete(result.id);
        notifyChanged();
        return result;
    }

    @Override
    public void clear() {
        boolean wasEmpty = size() == 0;
        beginUpdate();
        super.clear();
        endUpdate(!wasEmpty);
    }
}
