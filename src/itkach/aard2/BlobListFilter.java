package itkach.aard2;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import itkach.slob.Slob.Blob;

/**
 * Iterator that filters out blobs that are not supposed to be user-visible (internal dictionary resources).
 */
class BlobListFilter implements Iterator<Blob> {
    private static final Set<String> USER_VISIBLE_TYPES = new HashSet<String>(Arrays.asList(
            "text/html",
            "text/plain"
    ));

    private final Iterator<Blob> delegate;
    private Blob next;

    BlobListFilter(Iterator<Blob> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
        return peek() != null;
    }

    @Override
    public Blob next() {
        Blob next = peek();
        if (next != null) {
            this.next = null;
            return next;
        } else {
            throw new NoSuchElementException();
        }
    }

    private Blob peek() {
        if (next == null) {
            next = findNext();
        }
        return next;
    }

    private Blob findNext() {
        while (delegate.hasNext()) {
            Blob next = delegate.next();
            if (shouldBlobBeVisible(next)) {
                return next;
            }
        }
        return null;
    }

    private boolean shouldBlobBeVisible(Blob blob) {
        // Getting the content type requires I/O, so only do it if the key starts with "~/"
        // which is the prefix used by the standard dictionary generators for storing resources.
        return !blob.key.startsWith("~/") || isUserVisibleType(blob.getContentType());
    }

    private static boolean isUserVisibleType(String type) {
        return type != null && USER_VISIBLE_TYPES.contains(normalizeType(type));
    }

    private static String normalizeType(String type) {
        int i = type.indexOf(';');
        if (i > 0) {
            type = type.substring(0, i);
        }
        return type;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
