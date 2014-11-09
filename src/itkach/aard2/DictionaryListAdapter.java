package itkach.aard2;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.net.Uri;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.Locale;

import static java.lang.String.format;

public class DictionaryListAdapter extends BaseAdapter {

    private final SlobDescriptorList data;
    private final DataSetObserver observer;
    private boolean              selectionMode;

    DictionaryListAdapter(SlobDescriptorList data) {
        this.data = data;
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
        this.data.registerDataSetObserver(observer);

    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().equals("");
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        SlobDescriptor desc = (SlobDescriptor) getItem(position);
        String label = desc.tags.get("label");
        String path = desc.path;
        long blobCount = desc.blobCount;
        boolean available = this.data.resolve(desc) != null;
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            LayoutInflater inflater = (LayoutInflater) parent.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.dictionary_list_item, parent,
                    false);
            TextView licenseView = (TextView) view.findViewById(R.id.dictionary_license);
            licenseView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String url = (String)v.getTag();
                    if (!isBlank(url)) {
                        Uri uri = Uri.parse(url);
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                        v.getContext().startActivity(browserIntent);
                    }
                }
            });
        }

        Resources r = parent.getResources();

        TextView titleView = (TextView) view
                .findViewById(R.id.dictionary_label);
        titleView.setEnabled(available);
        titleView.setText(label);
        TextView pathView = (TextView) view.findViewById(R.id.dictionary_path);
        pathView.setText(path);
        pathView.setEnabled(available);

        TextView licenseView = (TextView) view.findViewById(R.id.dictionary_license);
        String licenseName = desc.tags.get("license.name");
        String licenseUrl = desc.tags.get("license.url");
        licenseView.setVisibility(isBlank(licenseName) && isBlank(licenseUrl) ? View.GONE : View.VISIBLE);
        CharSequence license;
        if (isBlank(licenseUrl)) {
            license = licenseName;
        }
        else {
            if (isBlank(licenseName)) {
                licenseName = licenseUrl;
            }
            license = Html.fromHtml(r.getString(R.string.dict_license, licenseUrl, licenseName));
        }
        licenseView.setText(license);
        licenseView.setEnabled(available);
        licenseView.setTag(licenseUrl);

        TextView copyrightView = (TextView) view.findViewById(R.id.dictionary_copyright);
        String copyright = desc.tags.get("copyright");
        copyrightView.setVisibility(isBlank(copyright) ? View.GONE : View.VISIBLE);
        copyrightView.setText(String.format("\u00a9 %s", copyright));
        copyrightView.setEnabled(available);

        TextView blobCountView = (TextView) view
                .findViewById(R.id.dictionary_blob_count);
        blobCountView.setEnabled(available);
        blobCountView.setVisibility(desc.error == null ? View.VISIBLE : View.GONE);

        blobCountView.setText(format(Locale.getDefault(),
                r.getString(R.string.dict_item_count), blobCount));
        CheckBox cb = (CheckBox) view.findViewById(R.id.dictionary_checkbox);
        cb.setVisibility(isSelectionMode() ? View.VISIBLE : View.GONE);
        TextView errorView = (TextView) view
                .findViewById(R.id.dictionary_error);
        errorView.setVisibility(desc.error == null ? View.GONE : View.VISIBLE);
        errorView.setText(desc.error);
        return view;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public int getCount() {
        return data.size();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
    }
}
