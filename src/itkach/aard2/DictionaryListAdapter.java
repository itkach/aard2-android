package itkach.aard2;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.shamanland.fonticon.FontIconDrawable;

import java.util.Comparator;
import java.util.Locale;

import static java.lang.String.format;

public class DictionaryListAdapter extends BaseAdapter {

    private final static String TAG = DictionaryListAdapter.class.getName();

    private final SlobDescriptorList    data;
    private final Activity              context;
    private View.OnClickListener        openUrlOnClick;
    private AlertDialog                 deleteConfirmationDialog;

    private final static String hrefTemplate = "<a href=\'%1$s\'>%2$s</a>";

    DictionaryListAdapter(SlobDescriptorList data, Activity context) {
        this.data = data;
        this.context = context;
        DataSetObserver observer = new DataSetObserver() {
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

        openUrlOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = (String)v.getTag();
                if (!isBlank(url)) {
                    try {
                        Uri uri = Uri.parse(url);
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                        v.getContext().startActivity(browserIntent);
                    }
                    catch (Exception e) {
                        Log.d(TAG, "Failed to launch browser with url " + url, e);
                    }
                }
            }
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().equals("");
    }

    @Override
    public View getView(int position, final View convertView, ViewGroup parent) {
        SlobDescriptor desc = (SlobDescriptor) getItem(position);
        String label = desc.getLabel();
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

            View licenseView= view.findViewById(R.id.dictionary_license);
            licenseView.setOnClickListener(openUrlOnClick);

            View sourceView= view.findViewById(R.id.dictionary_source);
            sourceView.setOnClickListener(openUrlOnClick);

            Switch activeSwitch = (Switch)view.findViewById(R.id.dictionary_active);
            activeSwitch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Switch activeSwitch = (Switch)view;
                    Integer position = (Integer)view.getTag();
                    SlobDescriptor desc = data.get(position);
                    desc.active = activeSwitch.isChecked();
                    data.set(position, desc);
                }
            });

            View btnForget = view
                    .findViewById(R.id.dictionary_btn_forget);
            btnForget.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Integer position = (Integer)view.getTag();
                    forget(position);
                }
            });

            View btnToggleDetail = view
                    .findViewById(R.id.dictionary_btn_toggle_detail);

            btnToggleDetail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Integer position = (Integer)view.getTag();
                    SlobDescriptor desc = data.get(position);
                    desc.expandDetail = !desc.expandDetail;
                    data.set(position, desc);
                }
            });


            View.OnClickListener toggleFavListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Integer position = (Integer) view.getTag();
                    SlobDescriptor desc = data.get(position);
                    long currentTime = System.currentTimeMillis();
                    if (desc.priority == 0) {
                        desc.priority = currentTime;
                    } else {
                        desc.priority = 0;
                    }
                    desc.lastAccess = currentTime;
                    data.beginUpdate();
                    data.set(position, desc);
                    data.sort();
                    data.endUpdate(true);
                }
            };
            View btnToggleFav = view
                    .findViewById(R.id.dictionary_btn_toggle_fav);
            btnToggleFav.setOnClickListener(toggleFavListener);
            View dictLabel = view
                    .findViewById(R.id.dictionary_label);
            dictLabel.setOnClickListener(toggleFavListener);
        }

        Resources r = parent.getResources();

        Switch switchView = (Switch) view
                .findViewById(R.id.dictionary_active);

        switchView.setChecked(desc.active);
        switchView.setTag(position);

        TextView titleView = (TextView) view
                .findViewById(R.id.dictionary_label);
        titleView.setEnabled(available);
        titleView.setText(label);
        titleView.setTag(position);

        View detailView = view.findViewById(R.id.dictionary_details);
        detailView.setVisibility(desc.expandDetail ? View.VISIBLE : View.GONE);

        setupBlobCountView(desc, blobCount, available, view, r);
        setupCopyrightView(desc, available, view);
        setupLicenseView(desc, available, view);
        setupSourceView(desc, available, view);
        setupPathView(path, available, view);
        setupErrorView(desc, view);

        ImageView btnToggleDetail = (ImageView) view
                .findViewById(R.id.dictionary_btn_toggle_detail);
        int toggleIcon = desc.expandDetail ? R.xml.ic_list_angle_up : R.xml.ic_list_angle_down;
        btnToggleDetail.setImageDrawable(FontIconDrawable.inflate(context, toggleIcon));
        btnToggleDetail.setTag(position);

        ImageView btnForget = (ImageView) view
                .findViewById(R.id.dictionary_btn_forget);
        btnForget.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_list_trash));
        btnForget.setTag(position);

        ImageView btnToggleFav = (ImageView) view
                .findViewById(R.id.dictionary_btn_toggle_fav);
        int favIcon = desc.priority > 0 ? R.xml.ic_list_star: R.xml.ic_list_star_o;
        btnToggleFav.setImageDrawable(FontIconDrawable.inflate(context, favIcon));
        btnToggleFav.setTag(position);
        return view;
    }

    private void setupPathView(String path, boolean available, View view) {
        View pathRow = view.findViewById(R.id.dictionary_path_row);

        ImageView pathIcon = (ImageView) view.findViewById(R.id.dictionary_path_icon);
        pathIcon.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_text_file_archive));

        TextView pathView = (TextView) view.findViewById(R.id.dictionary_path);
        pathView.setText(path);

        pathRow.setEnabled(available);
    }

    private void setupErrorView(SlobDescriptor desc, View view) {
        View errorRow= view.findViewById(R.id.dictionary_error_row);

        ImageView errorIcon = (ImageView) view.findViewById(R.id.dictionary_error_icon);
        errorIcon.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_text_error));

        TextView errorView = (TextView) view
                .findViewById(R.id.dictionary_error);
        errorView.setText(desc.error);

        errorRow.setVisibility(desc.error == null ? View.GONE : View.VISIBLE);
    }

    private void setupBlobCountView(SlobDescriptor desc, long blobCount, boolean available, View view, Resources r) {
        TextView blobCountView = (TextView) view
                .findViewById(R.id.dictionary_blob_count);
        blobCountView.setEnabled(available);
        blobCountView.setVisibility(desc.error == null ? View.VISIBLE : View.GONE);

        blobCountView.setText(format(Locale.getDefault(),
                r.getQuantityString(R.plurals.dict_item_count, (int)blobCount), blobCount));
    }

    private void setupCopyrightView(SlobDescriptor desc, boolean available, View view) {
        View copyrightRow= view.findViewById(R.id.dictionary_copyright_row);

        ImageView copyrightIcon = (ImageView) view.findViewById(R.id.dictionary_copyright_icon);
        copyrightIcon.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_text_copyright));

        TextView copyrightView = (TextView) view.findViewById(R.id.dictionary_copyright);
        String copyright = desc.tags.get("copyright");
        copyrightView.setText(copyright);

        copyrightRow.setVisibility(isBlank(copyright) ? View.GONE : View.VISIBLE);
        copyrightRow.setEnabled(available);
    }

    private void setupSourceView(SlobDescriptor desc, boolean available, View view) {
        View sourceRow = view.findViewById(R.id.dictionary_license_row);

        ImageView sourceIcon = (ImageView) view.findViewById(R.id.dictionary_source_icon);
        sourceIcon.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_text_external_link));

        TextView sourceView = (TextView) view.findViewById(R.id.dictionary_source);
        String source = desc.tags.get("source");
        CharSequence sourceHtml = Html.fromHtml(String.format(hrefTemplate, source, source));
        sourceView.setText(sourceHtml);
        sourceView.setTag(source);

        int visibility = isBlank(source) ? View.GONE : View.VISIBLE;
        //Setting visibility on layout seems to have no effect
        //if one of the children is a link
        sourceIcon.setVisibility(visibility);
        sourceView.setVisibility(visibility);
        sourceRow.setVisibility(visibility);
        sourceRow.setEnabled(available);
    }

    private void setupLicenseView(SlobDescriptor desc, boolean available, View view) {
        View licenseRow= view.findViewById(R.id.dictionary_license_row);

        ImageView licenseIcon = (ImageView) view.findViewById(R.id.dictionary_license_icon);
        licenseIcon.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_text_license));

        TextView licenseView = (TextView) view.findViewById(R.id.dictionary_license);
        String licenseName = desc.tags.get("license.name");
        String licenseUrl = desc.tags.get("license.url");
        CharSequence license;
        if (isBlank(licenseUrl)) {
            license = licenseName;
        }
        else {
            if (isBlank(licenseName)) {
                licenseName = licenseUrl;
            }
            license = Html.fromHtml(String.format(hrefTemplate, licenseUrl, licenseName));
        }
        licenseView.setText(license);
        licenseView.setTag(licenseUrl);

        int visibility = (isBlank(licenseName) && isBlank(licenseUrl)) ? View.GONE : View.VISIBLE;
        licenseIcon.setVisibility(visibility);
        licenseView.setVisibility(visibility);
        licenseRow.setVisibility(visibility);
        licenseRow.setEnabled(available);
    }

    private void forget(final int position) {
        SlobDescriptor desc = data.get(position);
        final String label = desc.getLabel();
        String message = context.getString(R.string.dictionaries_confirm_forget, label);
        deleteConfirmationDialog = new AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        data.remove(position);
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .create();
        deleteConfirmationDialog.setOnDismissListener(new DialogInterface.OnDismissListener(){
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                deleteConfirmationDialog = null;
            }
        });
        deleteConfirmationDialog.show();
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

}
