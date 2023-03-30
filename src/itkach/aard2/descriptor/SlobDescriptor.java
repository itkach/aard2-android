package itkach.aard2.descriptor;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

import itkach.aard2.slob.SlobTags;
import itkach.slob.Slob;


public class SlobDescriptor extends BaseDescriptor {
    private final static String TAG = SlobDescriptor.class.getSimpleName();

    public String path;
    public Map<String, String> tags = new HashMap<>();
    public boolean active = true;
    public long priority;
    public long blobCount;
    public String error;
    public boolean expandDetail = false;
    @SuppressWarnings("FieldCanBeLocal")
    private transient ParcelFileDescriptor fileDescriptor;

    private SlobDescriptor() {
    }

    private void update(@NonNull Slob s) {
        this.id = s.getId().toString();
        this.path = s.fileURI;
        this.tags = s.getTags();
        this.blobCount = s.getBlobCount();
        this.error = null;
    }

    @Nullable
    public Slob load(@NonNull Context context) {
        Slob slob = null;
        try {
            final Uri uri = Uri.parse(path);
            fileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            FileInputStream fileInputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            slob = new Slob(fileInputStream.getChannel(), path);
            update(slob);
        } catch (Exception e) {
            Log.e(TAG, "Error while opening " + this.path, e);
            error = e.getMessage();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Error while opening " + this.path, e);
            }
            expandDetail = true;
            active = false;
        }
        return slob;
    }

    @NonNull
    public String getLabel() {
        String label = tags.get(SlobTags.TAG_LABEL);
        if (TextUtils.isEmpty(label)) {
            return "???";
        }
        return label;
    }

    @NonNull
    public static SlobDescriptor fromUri(@NonNull Context context, @NonNull Uri uri) {
        SlobDescriptor s = new SlobDescriptor();
        s.path = uri.toString();
        s.load(context);
        return s;
    }
}
