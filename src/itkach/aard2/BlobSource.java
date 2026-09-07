package itkach.aard2;

// The slice of a list adapter the article ViewPager needs as a data source:
// a count and positional access to the underlying blob/descriptor items,
// independent of the adapter's RecyclerView view-binding role. Change
// notification reaches the pager through RecyclerView.Adapter's own
// AdapterDataObserver, which every implementer already provides.
public interface BlobSource {
    int getBlobCount();
    Object getBlobItem(int position);
}
