package itkach.aard2;

import static java.lang.String.format;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.net.Uri;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.documentfile.provider.DocumentFile;

import java.util.Locale;

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

        openUrlOnClick = v -> {
            String url = (String)v.getTag();
            if (!Util.isBlank(url)) {
                try {
                    Uri uri = Uri.parse(url);
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                    v.getContext().startActivity(browserIntent);
                }
                catch (Exception e) {
                    Log.d(TAG, "Failed to launch browser with url " + url, e);
                }
            }
        };
    }

    @Override
    public View getView(int position, final View convertView, ViewGroup parent) {
        SlobDescriptor desc = (SlobDescriptor) getItem(position);
        String label = desc.getLabel();
        String fileName;
        try {
            DocumentFile documentFile = DocumentFile.fromSingleUri(parent.getContext(), Uri.parse(desc.path));
            fileName = documentFile.getName();
        }
        catch (Exception ex) {
            fileName = desc.path;
            Log.w(TAG, "Couldn't parse get document file name from uri" + desc.path, ex);
        }
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

            Switch activeSwitch = view.findViewById(R.id.dictionary_active);
            activeSwitch.setOnClickListener(view14 -> {
                Switch activeSwitch1 = (Switch) view14;
                Integer position14 = (Integer) view14.getTag();
                SlobDescriptor desc13 = data.get(position14);
                desc13.active = activeSwitch1.isChecked();
                data.set(position14, desc13);
            });

            View btnForget = view
                    .findViewById(R.id.dictionary_btn_forget);
            btnForget.setOnClickListener(view1 -> {
                Integer position1 = (Integer) view1.getTag();
                forget(position1);
            });

            View.OnClickListener detailToggle = view12 -> {
                Integer position12 = (Integer) view12.getTag();
                SlobDescriptor desc1 = data.get(position12);
                desc1.expandDetail = !desc1.expandDetail;
                data.set(position12, desc1);
            };

            View viewDetailToggle = view
                    .findViewById(R.id.dictionary_detail_toggle);
            viewDetailToggle.setOnClickListener(detailToggle);

            View.OnClickListener toggleFavListener = view13 -> {
                Integer position13 = (Integer) view13.getTag();
                SlobDescriptor desc12 = data.get(position13);
                long currentTime = System.currentTimeMillis();
                if (desc12.priority == 0) {
                    desc12.priority = currentTime;
                } else {
                    desc12.priority = 0;
                }
                desc12.lastAccess = currentTime;
                data.beginUpdate();
                data.set(position13, desc12);
                data.sort();
                data.endUpdate(true);
            };
            View btnToggleFav = view
                    .findViewById(R.id.dictionary_btn_toggle_fav);
            btnToggleFav.setOnClickListener(toggleFavListener);
            View dictLabel = view
                    .findViewById(R.id.dictionary_label);
            dictLabel.setOnClickListener(toggleFavListener);
        }

        Resources r = parent.getResources();

        Switch switchView = view
                .findViewById(R.id.dictionary_active);

        switchView.setChecked(desc.active);
        switchView.setTag(position);

        TextView titleView = view
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
        setupPathView(fileName, available, view);
        setupErrorView(desc, view);

        ImageView btnToggleDetail = view
                .findViewById(R.id.dictionary_btn_toggle_detail);
        char toggleIcon = desc.expandDetail ? IconMaker.IC_ANGLE_UP : IconMaker.IC_ANGLE_DOWN;
        btnToggleDetail.setImageDrawable(IconMaker.list(context, toggleIcon));

        View viewDetailToggle = view
                .findViewById(R.id.dictionary_detail_toggle);
        viewDetailToggle.setTag(position);

        ImageView btnForget = view
                .findViewById(R.id.dictionary_btn_forget);
        btnForget.setImageDrawable(IconMaker.list(context, IconMaker.IC_TRASH));
        btnForget.setTag(position);

        ImageView btnToggleFav = view
                .findViewById(R.id.dictionary_btn_toggle_fav);
        char favIcon = desc.priority > 0 ? IconMaker.IC_STAR: IconMaker.IC_STAR_O;
        btnToggleFav.setImageDrawable(IconMaker.list(context, favIcon));
        btnToggleFav.setTag(position);
        return view;
    }

    private void setupPathView(String path, boolean available, View view) {
        View pathRow = view.findViewById(R.id.dictionary_path_row);

        ImageView pathIcon = view.findViewById(R.id.dictionary_path_icon);
        pathIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_FILE_ARCHIVE));

        TextView pathView = view.findViewById(R.id.dictionary_path);
        pathView.setText(path);

        pathRow.setEnabled(available);
    }

    private void setupErrorView(SlobDescriptor desc, View view) {
        View errorRow= view.findViewById(R.id.dictionary_error_row);

        ImageView errorIcon = view.findViewById(R.id.dictionary_error_icon);
        errorIcon.setImageDrawable(IconMaker.errorText(context, IconMaker.IC_ERROR));

        TextView errorView = view
                .findViewById(R.id.dictionary_error);
        errorView.setText(desc.error);

        errorRow.setVisibility(desc.error == null ? View.GONE : View.VISIBLE);
    }

    private void setupBlobCountView(SlobDescriptor desc, long blobCount, boolean available, View view, Resources r) {
        TextView blobCountView = view
                .findViewById(R.id.dictionary_blob_count);
        blobCountView.setEnabled(available);
        blobCountView.setVisibility(desc.error == null ? View.VISIBLE : View.GONE);

        blobCountView.setText(format(Locale.getDefault(),
                r.getQuantityString(R.plurals.dict_item_count, (int)blobCount), blobCount));
    }

    private void setupCopyrightView(SlobDescriptor desc, boolean available, View view) {
        View copyrightRow= view.findViewById(R.id.dictionary_copyright_row);

        ImageView copyrightIcon = view.findViewById(R.id.dictionary_copyright_icon);
        copyrightIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_COPYRIGHT));

        TextView copyrightView = view.findViewById(R.id.dictionary_copyright);
        String copyright = desc.tags.get("copyright");
        copyrightView.setText(copyright);

        copyrightRow.setVisibility(Util.isBlank(copyright) ? View.GONE : View.VISIBLE);
        copyrightRow.setEnabled(available);
    }

    private void setupSourceView(SlobDescriptor desc, boolean available, View view) {
        View sourceRow = view.findViewById(R.id.dictionary_license_row);

        ImageView sourceIcon = view.findViewById(R.id.dictionary_source_icon);
        sourceIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_EXTERNAL_LINK));

        TextView sourceView = view.findViewById(R.id.dictionary_source);
        String source = desc.tags.get("source");
        CharSequence sourceHtml = Html.fromHtml(String.format(hrefTemplate, source, source));
        sourceView.setText(sourceHtml);
        sourceView.setTag(source);

        int visibility = Util.isBlank(source) ? View.GONE : View.VISIBLE;
        //Setting visibility on layout seems to have no effect
        //if one of the children is a link
        sourceIcon.setVisibility(visibility);
        sourceView.setVisibility(visibility);
        sourceRow.setVisibility(visibility);
        sourceRow.setEnabled(available);
    }

    private void setupLicenseView(SlobDescriptor desc, boolean available, View view) {
        View licenseRow= view.findViewById(R.id.dictionary_license_row);

        ImageView licenseIcon = view.findViewById(R.id.dictionary_license_icon);
        licenseIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_LICENSE));

        TextView licenseView = view.findViewById(R.id.dictionary_license);
        String licenseName = desc.tags.get("license.name");
        String licenseUrl = desc.tags.get("license.url");
        CharSequence license;
        if (Util.isBlank(licenseUrl)) {
            license = licenseName;
        }
        else {
            if (Util.isBlank(licenseName)) {
                licenseName = licenseUrl;
            }
            license = Html.fromHtml(String.format(hrefTemplate, licenseUrl, licenseName));
        }
        licenseView.setText(license);
        licenseView.setTag(licenseUrl);

        int visibility = (Util.isBlank(licenseName) && Util.isBlank(licenseUrl)) ? View.GONE : View.VISIBLE;
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
                .setPositiveButton(android.R.string.yes, (dialog, which) -> data.remove(position))
                .setNegativeButton(android.R.string.no, null)
                .create();
        deleteConfirmationDialog.setOnDismissListener(dialogInterface -> deleteConfirmationDialog = null);
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
