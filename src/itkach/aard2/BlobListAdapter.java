package itkach.aard2;

import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import itkach.slob.Slob;

// View-scoped RecyclerView adapter over an app-scoped BlobList. One is created
// per consumer (the Lookup list, each article pager), each with its own click
// listener, so the listener lives and dies with the view that owns it. Mirrors
// BlobDescriptorListAdapter's relationship to BlobDescriptorList.
public class BlobListAdapter extends RecyclerView.Adapter<BlobListAdapter.ViewHolder>
        implements BlobSource {

    private final BlobList            list;
    private final OnItemClickListener itemClickListener;
    private final DataSetObserver     observer;

    BlobListAdapter(BlobList list) {
        this(list, null);
    }

    BlobListAdapter(BlobList list, OnItemClickListener itemClickListener) {
        this.list = list;
        this.itemClickListener = itemClickListener;
        this.observer = new DataSetObserver() {
            @Override
            public void onChanged() {
                notifyDataSetChanged();
            }

            @Override
            public void onInvalidated() {
                notifyDataSetChanged();
            }
        };
        this.list.registerDataSetObserver(observer);
    }

    // Detaches from the underlying BlobList. Call when the owning view goes away
    // (e.g. LookupFragment.onDestroyView) so the app-scoped list stops holding
    // this adapter - and, through its click listener, the Activity.
    void close() {
        list.unregisterDataSetObserver(observer);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.blob_descriptor_list_item, parent, false);
        final ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (itemClickListener != null && position != RecyclerView.NO_POSITION) {
                itemClickListener.onItemClick(position);
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Slob.Blob item = list.get(position);
        Slob slob = item.owner;
        holder.title.setText(item.key);
        holder.source.setText(slob == null ? "???" : slob.getTags().get("label"));
        holder.timestamp.setText("");
        holder.timestamp.setVisibility(View.GONE);
        list.maybeLoadMore(position);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    @Override
    public int getBlobCount() {
        return list.size();
    }

    @Override
    public Object getBlobItem(int position) {
        return list.get(position);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView source;
        final TextView timestamp;

        ViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.blob_descriptor_key);
            source = (TextView) itemView.findViewById(R.id.blob_descriptor_source);
            timestamp = (TextView) itemView.findViewById(R.id.blob_descriptor_timestamp);
        }
    }
}
