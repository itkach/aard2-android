package itkach.aard2;

import android.net.Uri;

import java.util.List;
import java.util.UUID;

public class BlobDescriptor extends BaseDescriptor {
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((blobId == null) ? 0 : blobId.hashCode());
        result = prime * result
                + ((fragment == null) ? 0 : fragment.hashCode());
        result = prime * result + ((slobId == null) ? 0 : slobId.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BlobDescriptor other = (BlobDescriptor) obj;
        if (blobId == null) {
            if (other.blobId != null)
                return false;
        } else if (!blobId.equals(other.blobId))
            return false;
        if (fragment == null) {
            if (other.fragment != null)
                return false;
        } else if (!fragment.equals(other.fragment))
            return false;
        if (slobId == null) {
            if (other.slobId != null)
                return false;
        } else if (!slobId.equals(other.slobId))
            return false;
        return true;
    }
    public String slobId;
    public String slobUri;
    public String blobId;
    public String key;
    public String fragment;
    // Label of the entry's dictionary, kept so the entry can still be shown
    // with it once that dictionary is gone. Display only.
    public String slobLabel;
    // Ids of the dictionary files with the entry's uri that no article for it
    // was picked in when moving the entry: its key isn't there, or several
    // articles are and its position doesn't tell which (see
    // BlobDescriptorList.moveToLoadedDictionaries), so the lookup isn't
    // repeated until another file with the uri is loaded.
    public List<String> notFoundInIds;
    // One such id, as older app versions store it: they would fail to read a
    // list here. Read along with notFoundInIds, and cleared when it's written.
    public String notFoundIn;
    // Which of the articles under the entry's key in its dictionary it points
    // to, counting from 0, and how many there are (see
    // BlobDescriptorList.articlesUnder), so a move can tell homographs apart.
    // Both null until worked out; keyIndex alone null if the entry's article
    // wasn't among them.
    public Integer keyIndex;
    public Integer keyCount;

    static BlobDescriptor fromUri(Uri uri) {
        BlobDescriptor bd = new BlobDescriptor();
        bd.id = UUID.randomUUID().toString();
        bd.createdAt = System.currentTimeMillis();
        bd.lastAccess = bd.createdAt;
        List<String> pathSegments = uri.getPathSegments();
        int segmentCount = pathSegments.size();
        if (segmentCount < 3) {
            return null;
        }
        bd.slobId = pathSegments.get(1);
        StringBuilder key = new StringBuilder();
        for (int i = 2; i < segmentCount; i++) {
            if (key.length() > 0) {
                key.append("/");
            }
            key.append(pathSegments.get(i));
        }
        bd.key = key.toString();
        bd.blobId = uri.getQueryParameter("blob");
        bd.fragment = uri.getFragment();
        return bd;
    }
}
