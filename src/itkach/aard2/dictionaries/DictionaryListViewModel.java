package itkach.aard2.dictionaries;

import android.app.Application;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import itkach.aard2.BlobDescriptor;
import itkach.aard2.BlobDescriptorList;
import itkach.aard2.SlobDescriptor;
import itkach.aard2.SlobDescriptorList;
import itkach.aard2.utils.ThreadUtils;

public class DictionaryListViewModel extends AndroidViewModel {
    private final itkach.aard2.Application application;
    @Nullable
    private SlobDescriptor dictionaryToBeReplaced;

    public DictionaryListViewModel(@NonNull Application application) {
        super(application);
        this.application = (itkach.aard2.Application) application;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }

    public void addDictionaries(@NonNull Intent intent) {
        ThreadUtils.postOnBackgroundThread(() -> {
            List<Uri> selection = new ArrayList<>();
            Uri dataUri = intent.getData();
            if (dataUri != null) {
                selection.add(dataUri);
            }
            ClipData clipData = intent.getClipData();
            if (clipData != null) {
                int itemCount = clipData.getItemCount();
                for (int i = 0; i < itemCount; i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    selection.add(uri);
                }
            }
            for (Uri uri : selection) {
                application.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                SlobDescriptor sd = SlobDescriptor.fromUri(application, uri);
                SlobDescriptorList dictionaries = application.dictionaries;
                if (!dictionaries.hasId(sd.id)) {
                    dictionaries.add(sd);
                }
            }
        });
    }

    public void setDictionaryToBeReplaced(@Nullable SlobDescriptor dictionaryToBeReplaced) {
        this.dictionaryToBeReplaced = dictionaryToBeReplaced;
    }

    public void updateDictionary(@NonNull Uri newUri) {
        ThreadUtils.postOnBackgroundThread(() -> {
            if (dictionaryToBeReplaced != null) {
                application.getContentResolver().takePersistableUriPermission(newUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                SlobDescriptorList dictionaries = application.dictionaries;
                String oldId = dictionaryToBeReplaced.id;
                dictionaries.beginUpdate();
                dictionaryToBeReplaced.path = newUri.toString();
                dictionaryToBeReplaced.load(application);
                dictionaries.endUpdate(true);
                String newId = dictionaryToBeReplaced.id;
                String newSlobUri = application.getSlobURI(newId);
                // Update history and bookmarks
                BlobDescriptorList history = application.history;
                for (BlobDescriptor d : history.getList()) {
                    if (Objects.equals(d.slobId, oldId)) {
                        d.slobId = newId;
                        d.slobUri = newSlobUri;
                    }
                }
                BlobDescriptorList bookmarks = application.bookmarks;
                for (BlobDescriptor d : bookmarks.getList()) {
                    if (Objects.equals(d.slobId, oldId)) {
                        d.slobId = newId;
                        d.slobUri = newSlobUri;
                    }
                }
                ThreadUtils.postOnMainThread(() -> {
                    history.notifyDataSetChanged();
                    bookmarks.notifyDataSetChanged();
                });
            }
        });
    }
}
