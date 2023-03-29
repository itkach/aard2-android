package itkach.aard2;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.elevation.SurfaceColors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import itkach.aard2.utils.ThreadUtils;
import itkach.slob.Slob;

public class BlobListAdapter extends BaseAdapter {

    private static final String TAG = BlobListAdapter.class.getSimpleName();

    private final List<Slob.Blob> list;
    private Iterator<Slob.Blob> iter;

    private final int chunkSize;
    private final int loadMoreThreashold;
    int MAX_SIZE = 10000;


    public BlobListAdapter() {
        this(20, 10);
    }

    public BlobListAdapter(int chunkSize, int loadMoreThreashold) {
        this.list = new ArrayList<>(chunkSize);
        this.chunkSize = chunkSize;
        this.loadMoreThreashold = loadMoreThreashold;
    }

    public void setData(Iterator<Slob.Blob> lookupResultsIter) {
        ThreadUtils.postOnMainThread(() -> {
            list.clear();
            notifyDataSetChanged();
        });
        this.iter = lookupResultsIter;
        loadChunkSync();
    }

    private void loadChunkSync() {
        long t0 = System.currentTimeMillis();
        int count = 0;
        final List<Slob.Blob> chunkList = new LinkedList<>();

        while (iter.hasNext() && count < chunkSize && list.size() <= MAX_SIZE) {
            count++;
            Slob.Blob b = iter.next();
            chunkList.add(b);
        }

        ThreadUtils.postOnMainThread(() -> {
            list.addAll(chunkList);
            notifyDataSetChanged();
        });

        Log.d(TAG, String.format("Loaded chunk of %d (adapter size %d) in %d ms",
                count, list.size(), (System.currentTimeMillis() - t0)));
    }

    private void loadChunk() {
        if (!iter.hasNext()) {
            return;
        }
        ThreadUtils.postOnBackgroundThread(this::loadChunkSync);
    }

    @Override
    public int getCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public Object getItem(int position) {
        Object result = list.get(position);
        maybeLoadMore(position);
        return result;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private void maybeLoadMore(int position) {
        if (position >= list.size() - loadMoreThreashold) {
            loadChunk();
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Slob.Blob item = list.get(position);
        Slob slob = item.owner;
        maybeLoadMore(position);

        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            LayoutInflater inflater = (LayoutInflater) parent.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.blob_descriptor_list_item, parent, false);
            MaterialCardView cardView = view.findViewById(R.id.card_view);
            cardView.setCardBackgroundColor(SurfaceColors.SURFACE_1.getColor(view.getContext()));
        }

        TextView titleView = view.findViewById(R.id.blob_descriptor_key);
        titleView.setText(item.key);
        TextView sourceView = view.findViewById(R.id.blob_descriptor_source);
        sourceView.setText(slob == null ? "???" : slob.getTags().get("label"));
        TextView timestampView = view.findViewById(R.id.blob_descriptor_timestamp);
        timestampView.setText("");
        timestampView.setVisibility(View.GONE);
        return view;

    }

}
