package itkach.aard2;

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
}
