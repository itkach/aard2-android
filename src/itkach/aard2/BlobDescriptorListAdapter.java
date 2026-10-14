package itkach.aard2;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ImageSpan;
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
        implements BlobSource, ReleasableAdapter {

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

    // Detaches from the app-scoped list. Call when the owning view goes away
    // (BlobDescriptorListFragment.onDestroyView, or the article pager's destroy())
    // so the list stops holding this adapter - and, via its click listener, the
    // Activity.
    @Override
    public void release() {
        list.unregisterDataSetObserver(observer);
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
        Context context = holder.itemView.getContext();
        String label = slob == null ? item.slobLabel : BlobDescriptorList.getLabel(slob);
        if (label == null) {
            label = context.getString(R.string.blob_descriptor_unknown_dictionary);
        }
        if (slob == null) {
            // The entry's dictionary file isn't loaded: show the label stored in
            // the entry, marked as not available, and dim the row.
            holder.source.setText(withUnavailableMarker(holder.source, label));
            holder.source.setContentDescription(
                    context.getString(R.string.blob_descriptor_dictionary_unavailable, label));
            holder.text.setAlpha(ORPHANED_ALPHA);
        } else {
            holder.source.setText(label);
            holder.source.setContentDescription(null);
            holder.text.setAlpha(1f);
        }
        holder.timestamp.setText(DateUtils.getRelativeTimeSpanString(item.createdAt));

        boolean selected = tracker != null && tracker.isSelected((long) position);
        holder.itemView.setActivated(selected);
        holder.checkbox.setVisibility(selectionModeActive ? View.VISIBLE : View.GONE);
        holder.checkbox.setChecked(selected);
    }

    // Opacity of the text of an entry whose dictionary isn't loaded.
    private static final float ORPHANED_ALPHA = 0.6f;

    // The label after the prohibit icon (the one the page for an entry that
    // can't be opened shows), drawn at the label's size and in its color.
    private static CharSequence withUnavailableMarker(TextView view, String label) {
        int sizeDp = Math.round(view.getTextSize()
                / view.getResources().getDisplayMetrics().density);
        Drawable icon = IconMaker.make(view.getContext(), IconMaker.IC_BAN, sizeDp,
                view.getCurrentTextColor());
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        SpannableStringBuilder sb = new SpannableStringBuilder();
        // An object-replacement placeholder (U+FFFC) the span renders over, then
        // a gap.
        sb.append("\uFFFC");
        sb.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BASELINE), 0, 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(" ").append(label);
        return sb;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View text;
        final TextView title;
        final TextView source;
        final TextView timestamp;
        final CheckBox checkbox;

        ViewHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.blob_descriptor_item);
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
