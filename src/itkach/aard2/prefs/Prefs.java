package itkach.aard2.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import itkach.aard2.Application;

public abstract class Prefs {
    protected final SharedPreferences prefs;
    protected Prefs(@NonNull String prefName) {
        prefs = Application.get().getSharedPreferences(prefName, Context.MODE_PRIVATE);
    }
}
