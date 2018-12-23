package itkach.aard2;

import android.database.DataSetObservable;
import android.database.DataSetObserver;

import java.util.AbstractList;
import java.util.LinkedList;
import java.util.List;

public class DownloadDictionaryList extends AbstractList<DownloadDictionary> {
    private final DataSetObservable dataSetObservable;
    private final List<DownloadDictionary> dictionariesList;

    public DownloadDictionaryList() {
        this.dataSetObservable = new DataSetObservable();
        dictionariesList = new LinkedList<DownloadDictionary>();
    }

    @Override
    public int size() {
        return dictionariesList.size();
    }

    @Override
    public DownloadDictionary get(int i) {
        return dictionariesList.get(i);
    }

    void registerDataSetObserver(DataSetObserver observer) {
        this.dataSetObservable.registerObserver(observer);
    }

    void unregisterDataSetObserver(DataSetObserver observer) {
        this.dataSetObservable.unregisterObserver(observer);
    }

    protected void notifyChanged() {
        this.dataSetObservable.notifyChanged();
    }

    @Override
    public DownloadDictionary remove(int location) {
        DownloadDictionary result = dictionariesList.remove(location);
        notifyChanged();
        return result;
    }

    @Override
    public boolean add(DownloadDictionary object) {
        boolean isAddSuccessful = dictionariesList.add(object);
        notifyChanged();
        return isAddSuccessful;
    }

    @Override
    public void clear() {
        dictionariesList.clear();
        notifyChanged();
    }

    @Override
    public int indexOf(Object object) {
        return dictionariesList.indexOf(object);
    }
}
