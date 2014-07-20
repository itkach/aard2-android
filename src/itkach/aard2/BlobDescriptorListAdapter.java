package itkach.aard2;

import itkach.slob.Slob;

import java.text.DateFormat;

import android.content.Context;
import android.database.DataSetObserver;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

public class BlobDescriptorListAdapter extends BaseAdapter {

    BlobDescriptorList      list;
    DateFormat              dateFormat;
    private DataSetObserver observer;
    private boolean         selectionMode;
//    private Set<Integer>    selectedPositions;

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
                notifyDataSetInvalidated();
            }
        };
        this.list.registerDataSetObserver(observer);
//        this.selectedPositions = new HashSet<Integer>();
    }

    @Override
    public int getCount() {
        synchronized (list) {
            return list == null ? 0 : list.size();
        }
    }

    @Override
    public Object getItem(int position) {
        synchronized (list) {
            return list.get(position);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
//        if (!this.selectionMode) {
//            this.selectedPositions.clear();
//        }
        notifyDataSetChanged();
    }

//    public void setSelected(int position, boolean selected) {
//        if (selected) {
//            this.selectedPositions.add(position);
//        }
//        else {
//            this.selectedPositions.remove(position);
//        }
//        notifyDataSetChanged();
//    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        BlobDescriptor item = list.get(position);
        Slob slob = list.resolveOwner(item);
        CharSequence timestamp = DateUtils.getRelativeDateTimeString(
                parent.getContext(), item.createdAt,
                DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL);
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            LayoutInflater inflater = (LayoutInflater) parent.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.blob_descriptor_list_item, parent,
                    false);
        }
        TextView titleView = (TextView) view
                .findViewById(R.id.blob_descriptor_key);
        titleView.setText(item.key);
        TextView sourceView = (TextView) view
                .findViewById(R.id.blob_descriptor_source);
        sourceView.setText(slob == null ? "???" : slob.getTags().get("label"));
        TextView timestampView = (TextView) view
                .findViewById(R.id.blob_descriptor_timestamp);
        timestampView.setText(timestamp);
        CheckBox cb = (CheckBox) view
                .findViewById(R.id.blob_descriptor_checkbox);
        cb.setVisibility(isSelectionMode() ? View.VISIBLE : View.GONE);
        return view;
    }

//    public boolean isSelected(int position) {
//        return selectedPositions.contains(position);
//    }
//
//    public void toggleSelected(int position) {
//        setSelected(position, !isSelected(position));
//    }

}
