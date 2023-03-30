package itkach.aard2.lookup;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.elevation.SurfaceColors;

import java.util.List;

import itkach.aard2.R;
import itkach.aard2.utils.ThreadUtils;
import itkach.slob.Slob;

public class LookupResultAdapter extends BaseAdapter {
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

        @Override
        public void onInvalidated() {
            ThreadUtils.postOnMainThread(() -> {
                list = lookupResult.getList();
                notifyDataSetInvalidated();
            });
        }
    };

    public LookupResultAdapter(@NonNull LookupResult lookupResult) {
        this.lookupResult = lookupResult;
        this.lookupResult.registerDataSetObserver(observer);
    }

    @Override
    public int getCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public Slob.Blob getItem(int position) {
        Slob.Blob result = list.get(position);
        lookupResult.loadMoreItems(position);
        return result;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Slob.Blob item = getItem(position);
        Slob slob = item.owner;

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

    @Override
    protected void finalize() {
        lookupResult.unregisterDataSetObserver(observer);
    }
}
