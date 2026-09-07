package itkach.aard2;

// Row-click callback for the RecyclerView list adapters (RecyclerView, unlike
// ListView, has no built-in item-click notion, so each adapter dispatches
// clicks from its ViewHolder through this).
public interface OnItemClickListener {
    void onItemClick(int position);
}
