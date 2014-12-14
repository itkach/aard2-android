package itkach.aard2;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.util.Log;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class BaseDescriptorList<T extends BaseDescriptor> extends AbstractList<T> {

    private final DataSetObservable dataSetObservable;
    private final DescriptorStore<T> store;
    private final List<T> list;
    private final Class<T> typeParameterClass;

    private int updating;

    BaseDescriptorList(Class<T> typeParameterClass, DescriptorStore<T> store) {
        this.typeParameterClass = typeParameterClass;
        this.store = store;
        this.dataSetObservable = new DataSetObservable();
        this.list = new ArrayList<T>();
        this.updating = 0;
    }

    void registerDataSetObserver(DataSetObserver observer) {
        this.dataSetObservable.registerObserver(observer);
    }

    void unregisterDataSetObserver(DataSetObserver observer) {
        this.dataSetObservable.unregisterObserver(observer);
    }

    void beginUpdate() {
        Log.d(getClass().getName(), "beginUpdate");
        this.updating++;
    }

    void endUpdate(boolean changed) {
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

    void load() {
        this.addAll(this.store.load(typeParameterClass));
    }

    @Override
    public T get(int i) {
        return this.list.get(i);
    }

    @Override
    public T set(int location, T object) {
        T result = this.list.set(location, object);
        this.store.save(object);
        notifyChanged();
        return result;
    }

    @Override
    public int size() {
        return this.list.size();
    }

    @Override
    public void add(int location, T object) {
        this.list.add(location, object);
        this.store.save(object);
        notifyChanged();
    }

    @Override
    public boolean addAll(int location, Collection<? extends T> collection) {
        beginUpdate();
        boolean result = super.addAll(location, collection);
        endUpdate(result);
        return result;
    }

    @Override
    public boolean addAll(Collection<? extends T> collection) {
        beginUpdate();
        boolean result = super.addAll(collection);
        endUpdate(result);
        return result;
    }

    @Override
    public T remove(int location) {
        T result = this.list.remove(location);
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
