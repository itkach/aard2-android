package itkach.aard2;

import android.database.DataSetObserver;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;

import itkach.slob.Slob;

public class BlobDescriptorListAdapter
        extends RecyclerView.Adapter<BlobDescriptorListAdapter.ViewHolder>
        implements BlobSource {

    BlobDescriptorList      list;
    DateFormat              dateFormat;
    private DataSetObserver observer;
    private OnItemClickListener itemClickListener;
    private SelectionTracker<Long> tracker;
    // Whether the contextual selection bar is open. Kept separate from the
    // tracker's hasSelection() so the bar (and the row checkboxes) persist
    // even when the selection is emptied by deselecting the last item.
    private boolean selectionModeActive;

    public BlobDescriptorListAdapter(BlobDescriptorList list) {
        this.list = list;
        this.dateFormat = DateFormat.getDateTimeInstance();
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

    void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    void setSelectionTracker(SelectionTracker<Long> tracker) {
        this.tracker = tracker;
    }

    void setSelectionModeActive(boolean active) {
        this.selectionModeActive = active;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        synchronized (list) {
            return list == null ? 0 : list.size();
        }
    }

    @Override
    public int getBlobCount() {
        return getItemCount();
    }

    @Override
    public Object getBlobItem(int position) {
        synchronized (list) {
            return list.get(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.blob_descriptor_list_item, parent, false);
        final ViewHolder holder = new ViewHolder(view);
        view.setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            // In selection mode a tap toggles the row. When the tracker
            // already has a selection it consumes the tap itself and this
            // listener never fires; this branch handles the remaining case -
            // tapping while the selection is empty but the bar is still open.
            if (selectionModeActive) {
                if (tracker != null) {
                    long key = (long) position;
                    if (tracker.isSelected(key)) {
                        tracker.deselect(key);
                    } else {
                        tracker.select(key);
                    }
                }
                return;
            }
            if (itemClickListener != null) {
                itemClickListener.onItemClick(position);
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BlobDescriptor item = list.get(position);
        holder.title.setText(item.key);
        Slob slob = list.resolveOwner(item);
        holder.source.setText(slob == null ? "???" : slob.getTags().get("label"));
        holder.timestamp.setText(DateUtils.getRelativeTimeSpanString(item.createdAt));

        boolean selected = tracker != null && tracker.isSelected((long) position);
        holder.itemView.setActivated(selected);
        holder.checkbox.setVisibility(selectionModeActive ? View.VISIBLE : View.GONE);
        holder.checkbox.setChecked(selected);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView source;
        final TextView timestamp;
        final CheckBox checkbox;

        ViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.blob_descriptor_key);
            source = (TextView) itemView.findViewById(R.id.blob_descriptor_source);
            timestamp = (TextView) itemView.findViewById(R.id.blob_descriptor_timestamp);
            checkbox = (CheckBox) itemView.findViewById(R.id.blob_descriptor_checkbox);
        }

        // Lets the SelectionTracker map a touch on this row to its position/key.
        // The position is snapshotted here (when the touch lands) rather than
        // read live: the tracker calls getPosition() again after select(),
        // and a notifyDataSetChanged() in between would otherwise make the
        // live binding position NO_POSITION and crash anchorRange().
        ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
            final int position = getBindingAdapterPosition();
            return new ItemDetailsLookup.ItemDetails<Long>() {
                @Override
                public int getPosition() {
                    return position;
                }

                @Nullable
                @Override
                public Long getSelectionKey() {
                    return position == RecyclerView.NO_POSITION ? null : (long) position;
                }
            };
        }
    }

}
