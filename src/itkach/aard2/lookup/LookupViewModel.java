package itkach.aard2.lookup;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import itkach.aard2.prefs.AppPrefs;

public class LookupViewModel extends AndroidViewModel {
    private final itkach.aard2.Application application;

    public LookupViewModel(@NonNull Application application) {
        super(application);
        this.application = (itkach.aard2.Application) application;
    }

    public void lookupLastQuery() {
        String query = AppPrefs.getLastQuery();
        if (!query.isEmpty()) {
            application.lookupAsync(AppPrefs.getLastQuery());
        }
    }

    public void lookup(@NonNull String query) {
        if (!AppPrefs.getLastQuery().equals(query)) {
            application.lookupAsync(query);
        }
    }
}
