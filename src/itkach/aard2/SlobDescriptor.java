package itkach.aard2;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

import itkach.slob.Slob;


public class SlobDescriptor extends BaseDescriptor {

    private final static transient String TAG = SlobDescriptor.class.getSimpleName();

    public String path;
    public Map<String, String> tags = new HashMap<String, String>();
    public boolean active = true;
    public long priority;
    public long blobCount;
    public String error;
    public boolean expandDetail = false;
    private transient ParcelFileDescriptor fileDescriptor;

    void update(Slob s) {
        this.id = s.getId().toString();
        this.path = s.fileURI;
        this.tags = s.getTags();
        this.blobCount = s.getBlobCount();
        this.error = null;
    }

    Slob load(final Context context) {
        Slob slob = null;
        //File f = new File(path);

        try {
            //slob = new Slob(f);
            final Uri uri = Uri.parse(path);
            //must hold on to ParcelFileDescriptor,
            //otherwise it gets garbage collected and trashes underlying file descriptor
            fileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            FileInputStream fileInputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            slob = new Slob(fileInputStream.getChannel(), path);
            this.update(slob);
        }
        catch (Exception e) {
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

    String getLabel() {
        String label = tags.get("label");
        if (label == null || label.trim().length() == 0) {
            label = "???";
        }
        return label;
    }

//    static SlobDescriptor fromFile(File file) {
//        SlobDescriptor s = new SlobDescriptor();
//        s.path = file.getAbsolutePath();
//        s.load();
//        return s;
//    }

    static SlobDescriptor fromUri(Context context, String uri) {
        SlobDescriptor s = new SlobDescriptor();
        s.path = uri;
        s.load(context);
        return s;
    }


}
