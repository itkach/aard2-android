package itkach.aard2.lookup;

import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.elevation.SurfaceColors;

import java.util.List;

import itkach.aard2.R;
import itkach.aard2.article.ArticleCollectionActivity;
import itkach.aard2.slob.SlobTags;
import itkach.aard2.utils.ThreadUtils;
import itkach.slob.Slob;

public class LookupResultAdapter extends RecyclerView.Adapter<LookupResultAdapter.ViewHolder> {
    private List<Slob.Blob> list;

    private final LookupResult lookupResult;
    private final DataSetObserver observer = new DataSetObserver() {
        @Override
        public void onChanged() {
            ThreadUtils.postOnMainThread(() -> {
                list = lookupResult.getList();
                notifyDataSetChanged();
            });
        }
    };

    public LookupResultAdapter(@NonNull LookupResult lookupResult) {
        this.lookupResult = lookupResult;
        this.lookupResult.registerDataSetObserver(observer);
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    @Nullable
    public Slob.Blob getItem(int position) {
        Slob.Blob result = list.get(position);
        lookupResult.loadMoreItems(position);
        return result;
    }

    @Override
    public long getItemId(int position) {
        Slob.Blob blob = getItem(position);
        return blob != null ? blob.hashCode() : RecyclerView.NO_ID;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.blob_descriptor_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Slob.Blob item = getItem(position);
        if (item == null) {
            holder.itemView.setVisibility(View.GONE);
            return;
        }
        Slob slob = item.owner;
        Context context = holder.itemView.getContext();

        holder.itemView.setVisibility(View.VISIBLE);
        holder.titleView.setText(item.key);
        holder.sourceView.setText(slob == null ? "???" : slob.getTags().get(SlobTags.TAG_LABEL));
        holder.cardView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ArticleCollectionActivity.class);
            intent.putExtra("position", position);
            context.startActivity(intent);
        });
    }

    @Override
    protected void finalize() {
        lookupResult.unregisterDataSetObserver(observer);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final MaterialCardView cardView;
        public final TextView titleView;
        public final TextView sourceView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            cardView.setCardBackgroundColor(SurfaceColors.SURFACE_1.getColor(itemView.getContext()));
            titleView = itemView.findViewById(R.id.blob_descriptor_key);
            sourceView = itemView.findViewById(R.id.blob_descriptor_source);
            itemView.findViewById(R.id.blob_descriptor_timestamp).setVisibility(View.GONE);
        }
    }
}
