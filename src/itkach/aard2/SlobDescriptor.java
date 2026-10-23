package itkach.aard2;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.documentfile.provider.DocumentFile;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import itkach.slob.Slob;


public class SlobDescriptor extends BaseDescriptor {

    private final static transient String TAG = SlobDescriptor.class.getSimpleName();

    public String path;
    public Map<String, String> tags = new HashMap<String, String>();
    public boolean active = true;
    // Legacy: pre-drag-reorder this doubled as the favourite flag (>0) and the
    // sort key. It is now only read once, during the one-time migration in
    // SlobDescriptorList, to seed useForRandomLookup and the initial order.
    public long priority;
    // Explicit, user-arranged position in the list (drag to reorder). -1 marks
    // a descriptor that predates this field and still needs migrating.
    public int order = -1;
    // Whether this dictionary participates in random-article lookup.
    public boolean useForRandomLookup = false;
    public long blobCount;
    public String error;
    public boolean expandDetail = false;
    // Files that the file at path replaced, overwritten in place, whose
    // bookmarks and history are still to be moved to it (see
    // Application.continueDictionary). Kept until they are, so a move cut
    // short by the app stopping is done on the next start.
    public List<Replaced> replacedFiles;
    private transient ParcelFileDescriptor fileDescriptor;

    // A file that the dictionary's file replaced (see replacedFiles).
    public static class Replaced {
        public String slobId;
        // The uri it went by.
        public String uri;
    }

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

    // The uri the file went by when last loaded, by the same rule as
    // Slob.getURI(): its uri tag, else, if it has none or a blank one, one
    // made from its id. Null if it was never loaded.
    String getURI() {
        if (id == null) {
            return null;
        }
        String uri = tags.get("uri");
        return Util.isBlank(uri) ? "slob:" + id : uri;
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
