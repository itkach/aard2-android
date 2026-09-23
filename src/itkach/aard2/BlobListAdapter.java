package itkach.aard2;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import itkach.slob.Slob;

public class BlobListAdapter extends RecyclerView.Adapter<BlobListAdapter.ViewHolder>
        implements BlobSource {

    private static final String TAG = BlobListAdapter.class.getSimpleName();

    Handler             mainHandler;
    List<Slob.Blob>     list;
    Iterator<Slob.Blob> iter;
    ExecutorService     executor;

    private final int   chunkSize;
    private final int   loadMoreThreashold;
    int                 MAX_SIZE   = 10000;

    private OnItemClickListener itemClickListener;

    public BlobListAdapter(Context context) {
        this(context, 20, 10);
    }

    public BlobListAdapter(Context context, int chunkSize, int loadMoreThreashold) {
        this.mainHandler = new Handler(context.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        this.list = new ArrayList<Slob.Blob>(chunkSize);
        this.chunkSize = chunkSize;
        this.loadMoreThreashold = loadMoreThreashold;
    }

    void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    // Clear the listener only if it's still the given one. This adapter is
    // app-scoped and shared, while the listener is owned by a LookupFragment's
    // view: when a stale fragment (e.g. a stopped MainActivity being reclaimed)
    // tears its view down, it must not clobber a newer fragment's listener.
    void removeOnItemClickListener(OnItemClickListener listener) {
        if (this.itemClickListener == listener) {
            this.itemClickListener = null;
        }
    }

    void setData(Iterator<Slob.Blob> lookupResultsIter) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                list.clear();
                notifyDataSetChanged();
            }
        });
        this.iter = lookupResultsIter;
        loadChunkSync();
    }

    private void loadChunkSync() {
        long t0 = System.currentTimeMillis();
        int count = 0;
        final List<Slob.Blob> chunkList = new LinkedList<>();

        while (iter.hasNext() && count < chunkSize
                && list.size() <= MAX_SIZE) {
            count++;
            Slob.Blob b = iter.next();
            chunkList.add(b);
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                int start = list.size();
                list.addAll(chunkList);
                notifyItemRangeInserted(start, chunkList.size());
            }
        });

        Log.d(TAG,
                String.format("Loaded chunk of %d (adapter size %d) in %d ms",
                        count, list.size(), (System.currentTimeMillis() - t0)));
    }

    private void loadChunk() {
        if (iter == null || !iter.hasNext()) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                loadChunkSync();
            }
        });
    }

    private void maybeLoadMore(int position) {
        if (position >= list.size() - loadMoreThreashold) {
            loadChunk();
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
        maybeLoadMore(position);
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public int getBlobCount() {
        return getItemCount();
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
