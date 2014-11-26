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
