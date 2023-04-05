package itkach.aard2;

import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.text.format.DateUtils;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.elevation.SurfaceColors;

import itkach.aard2.article.ArticleCollectionActivity;
import itkach.aard2.descriptor.BlobDescriptor;
import itkach.aard2.slob.SlobTags;
import itkach.aard2.utils.ThreadUtils;
import itkach.aard2.widget.RecyclerView;
import itkach.slob.Slob;

public class BlobDescriptorListAdapter extends RecyclerView.Adapter<BlobDescriptorListAdapter.ViewHolder> {
    public interface OnSelectionChangeListener {
        void selectionStarted();

        void selectionChanged(int selectionCount);

        void selectionCanceled();
    }

    private final BlobDescriptorList list;
    private final String action;
    private final SparseBooleanArray checkStates;
    private int checkedItemCount = 0;
    private boolean selectionMode;
    private OnSelectionChangeListener onSelectionChangeListener;

    public BlobDescriptorListAdapter(@NonNull BlobDescriptorList list, @NonNull String action) {
        this.list = list;
        this.checkStates = new SparseBooleanArray(list.size());
        this.action = action;
        this.list.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                ThreadUtils.postOnMainThread(() -> {
                    checkedItemCount = 0;
                    checkStates.clear();
                    notifyDataSetChanged();
                    onSelectionChangeListener.selectionChanged(0);
                });
            }
        });
    }

    public void setOnSelectionStartedListener(OnSelectionChangeListener onSelectionChangeListener) {
        this.onSelectionChangeListener = onSelectionChangeListener;
    }

    @Override
    public int getItemCount() {
        synchronized (list) {
            return list.size();
        }
    }

    public BlobDescriptor getItem(int position) {
        synchronized (list) {
            return list.get(position);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void selectAll() {
        synchronized (list) {
            for (int i = 0; i < list.size(); ++i) {
                checkStates.put(i, true);
            }
            checkedItemCount = list.size();
        }
        notifyItemRangeChanged(0, getItemCount());
        onSelectionChangeListener.selectionChanged(checkedItemCount);
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
        checkedItemCount = 0;
        checkStates.clear();
        notifyItemRangeChanged(0, getItemCount());
    }

    public SparseBooleanArray getCheckedItemPositions() {
        return checkStates;
    }

    public int getCheckedItemCount() {
        return checkedItemCount;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.blob_descriptor_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BlobDescriptor item = getItem(position);
        Slob slob = list.resolveOwner(item);
        CharSequence timestamp = DateUtils.getRelativeTimeSpanString(item.createdAt);
        Context context = holder.itemView.getContext();
        holder.titleView.setText(item.key);
        holder.sourceView.setText(slob == null ? "???" : slob.getTags().get(SlobTags.TAG_LABEL));
        holder.dateView.setText(timestamp);
        holder.cardView.setChecked(checkStates.get(position, false));
        holder.cardView.setOnLongClickListener(v -> {
            // Long click only checks an item, no uncheck performed
            if (holder.cardView.isChecked()) {
                return false;
            }
            if (!selectionMode) {
                // Selection mode not started
                onSelectionChangeListener.selectionStarted();
            }
            checkStates.put(position, true);
            ++checkedItemCount;
            notifyItemChanged(position);
            onSelectionChangeListener.selectionChanged(checkedItemCount);
            return true;
        });
        holder.cardView.setOnClickListener(v -> {
            if (selectionMode) {
                select(position, holder.cardView);
                return;
            }
            Intent intent = new Intent(context, ArticleCollectionActivity.class);
            intent.setAction(action);
            intent.putExtra("position", position);
            context.startActivity(intent);
        });
    }

    private void select(int position, @NonNull MaterialCardView cardView) {
        boolean checked = !cardView.isChecked();
        if (checked == checkStates.get(position, false)) {
            return;
        }
        checkStates.put(position, checked);
        if (checked) {
            ++checkedItemCount;
        } else {
            --checkedItemCount;
        }
        notifyItemChanged(position);
        onSelectionChangeListener.selectionChanged(checkedItemCount);
        if (checkedItemCount == 0) {
            onSelectionChangeListener.selectionCanceled();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final MaterialCardView cardView;
        public final TextView titleView;
        public final TextView sourceView;
        public final TextView dateView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            cardView.setCardBackgroundColor(SurfaceColors.SURFACE_1.getColor(itemView.getContext()));
            cardView.setCheckable(true);
            titleView = itemView.findViewById(R.id.blob_descriptor_key);
            sourceView = itemView.findViewById(R.id.blob_descriptor_source);
            dateView = itemView.findViewById(R.id.blob_descriptor_timestamp);
        }
    }
}
