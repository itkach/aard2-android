package itkach.aard2;

import android.support.v4.app.Fragment;

public abstract class FragmentRunnable implements Runnable {
    protected Fragment fragment;

    FragmentRunnable(Fragment fragment) {
        this.fragment = fragment;
    }
}
