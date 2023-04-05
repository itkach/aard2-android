package itkach.aard2.dictionaries;

import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.text.HtmlCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.elevation.SurfaceColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;

import itkach.aard2.R;
import itkach.aard2.SlobDescriptorList;
import itkach.aard2.descriptor.SlobDescriptor;
import itkach.aard2.slob.SlobTags;
import itkach.aard2.utils.ThreadUtils;

public class DictionaryListAdapter extends RecyclerView.Adapter<DictionaryListAdapter.ViewHolder> {
    private final static String TAG = DictionaryListAdapter.class.getName();

    private final SlobDescriptorList data;
    private final DictionaryListFragment fragment;
    private final View.OnClickListener openUrlOnClick;
    private AlertDialog deleteConfirmationDialog;

    private final static String hrefTemplate = "<a href='%1$s'>%2$s</a>";

    DictionaryListAdapter(SlobDescriptorList data, DictionaryListFragment fragment) {
        this.data = data;
        this.fragment = fragment;
        DataSetObserver observer = new DataSetObserver() {
            @Override
            public void onChanged() {
                ThreadUtils.postOnMainThread(() -> notifyDataSetChanged());
            }
        };
        this.data.registerDataSetObserver(observer);

        openUrlOnClick = v -> {
            String url = (String) v.getTag();
            if (!TextUtils.isEmpty(url)) {
                try {
                    Uri uri = Uri.parse(url);
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                    v.getContext().startActivity(browserIntent);
                } catch (Exception e) {
                    Log.d(TAG, "Failed to launch browser with url " + url, e);
                }
            }
        };
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.dictionary_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SlobDescriptor desc = getItem(position);
        if (desc == null) {
            holder.itemView.setVisibility(View.GONE);
            return;
        }
        Context context = holder.itemView.getContext();
        String label = desc.getLabel();
        String fileName;
        Uri uri = Uri.parse(desc.path);
        DocumentFile documentFile = DocumentFile.fromSingleUri(context, uri);
        fileName = documentFile != null ? documentFile.getName() : uri.getLastPathSegment();
        if (fileName == null) {
            fileName = desc.path;
        }
        long blobCount = desc.blobCount;
        boolean available = this.data.resolve(desc) != null;

        holder.itemView.setVisibility(View.VISIBLE);
        holder.licenseView.setOnClickListener(openUrlOnClick);
        holder.sourceView.setOnClickListener(openUrlOnClick);
        holder.forgetBtn.setOnClickListener(v -> forget(position));
        holder.updateBtn.setOnClickListener(v -> fragment.updateDictionary(desc));

        // Enable/disable dictionary
        holder.activeSwitch.setChecked(desc.active);
        holder.activeSwitch.setOnClickListener(v -> {
            desc.active = holder.activeSwitch.isChecked();
            data.set(position, desc);
        });

        // Set title
        holder.titleView.setEnabled(available);
        holder.titleView.setText(label);

        // Details
        holder.detailsView.setVisibility(desc.expandDetail ? View.VISIBLE : View.GONE);
        // 1. Error
        holder.errorView.setText(desc.error);
        holder.errorContainer.setVisibility(desc.error == null ? View.GONE : View.VISIBLE);
        // 2. Blob count
        holder.blobCountView.setEnabled(available);
        holder.blobCountView.setVisibility(desc.error == null ? View.VISIBLE : View.GONE);
        holder.blobCountView.setText(context.getResources().getQuantityString(R.plurals.dict_item_count,
                (int) blobCount, blobCount));
        // 3. Copyright
        String copyright = desc.tags.get(SlobTags.TAG_COPYRIGHT);
        holder.copyrightView.setText(copyright);
        holder.copyrightContainer.setVisibility(TextUtils.isEmpty(copyright) ? View.GONE : View.VISIBLE);
        holder.copyrightContainer.setEnabled(available);
        // 4. License
        setupLicenseView(desc, available, holder);
        // 5. Source
        setupSourceView(desc, available, holder);
        // 6. Path
        holder.pathView.setText(fileName);
        holder.pathContainer.setEnabled(available);

        // Toggle details
        int toggleIcon = desc.expandDetail ? R.drawable.ic_keyboard_arrow_up : R.drawable.ic_keyboard_arrow_down;
        holder.toggleDetailsBtn.setIconResource(toggleIcon);
        holder.toggleDetailsBtn.setOnClickListener(v -> {
            desc.expandDetail = !desc.expandDetail;
            data.set(position, desc);
        });

        // Favourite button
        int favIcon = desc.priority > 0 ? R.drawable.ic_favorite : R.drawable.ic_favorite_border;
        holder.toggleFavoriteBtn.setIconResource(favIcon);
        holder.toggleFavoriteBtn.setOnClickListener(v -> {
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
        });
    }

    private void setupSourceView(@NonNull SlobDescriptor desc, boolean available, @NonNull ViewHolder holder) {
        View sourceRow = holder.sourceContainer;
        String source = desc.tags.get(SlobTags.TAG_SOURCE);
        CharSequence sourceHtml = HtmlCompat.fromHtml(String.format(hrefTemplate, source, source),
                HtmlCompat.FROM_HTML_MODE_LEGACY);
        holder.sourceView.setText(sourceHtml);
        holder.sourceView.setTag(source);

        sourceRow.setVisibility(TextUtils.isEmpty(source) ? View.GONE : View.VISIBLE);
        sourceRow.setEnabled(available);
    }

    private void setupLicenseView(@NonNull SlobDescriptor desc, boolean available, @NonNull ViewHolder holder) {
        View licenseRow = holder.licenseContainer;
        String licenseName = desc.tags.get(SlobTags.TAG_LICENSE_NAME);
        String licenseUrl = desc.tags.get(SlobTags.TAG_LICENSE_URL);
        CharSequence license;
        if (TextUtils.isEmpty(licenseUrl)) {
            license = licenseName;
        } else {
            if (TextUtils.isEmpty(licenseName)) {
                licenseName = licenseUrl;
            }
            license = HtmlCompat.fromHtml(String.format(hrefTemplate, licenseUrl, licenseName),
                    HtmlCompat.FROM_HTML_MODE_LEGACY);
        }
        holder.licenseView.setText(license);
        holder.licenseView.setTag(licenseUrl);

        int visibility = (TextUtils.isEmpty(licenseName) && TextUtils.isEmpty(licenseUrl)) ? View.GONE : View.VISIBLE;
        licenseRow.setVisibility(visibility);
        licenseRow.setEnabled(available);
    }

    private void forget(final int position) {
        SlobDescriptor desc = data.get(position);
        final String label = desc.getLabel();
        String message = fragment.getString(R.string.dictionaries_confirm_forget, label);
        deleteConfirmationDialog = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(R.string.action_yes, (dialog, which) -> data.remove(position))
                .setNegativeButton(R.string.action_no, null)
                .create();
        deleteConfirmationDialog.setOnDismissListener(dialogInterface -> deleteConfirmationDialog = null);
        deleteConfirmationDialog.show();
    }

    @Override
    public long getItemId(int position) {
        SlobDescriptor desc = getItem(position);
        return desc != null ? desc.hashCode() : RecyclerView.NO_ID;
    }

    @Nullable
    public SlobDescriptor getItem(int position) {
        return data.get(position);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final MaterialCardView cardView;
        public final View detailsView;
        public final MaterialSwitch activeSwitch;
        public final MaterialButton toggleDetailsBtn;
        public final MaterialButton toggleFavoriteBtn;
        public final MaterialButton updateBtn;
        public final MaterialButton forgetBtn;
        public final MaterialTextView titleView;
        public final View errorContainer;
        public final MaterialTextView errorView;
        public final MaterialTextView blobCountView;
        public final View licenseContainer;
        public final MaterialTextView licenseView;
        public final View sourceContainer;
        public final MaterialTextView sourceView;
        public final View copyrightContainer;
        public final MaterialTextView copyrightView;
        public final View pathContainer;
        public final MaterialTextView pathView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            cardView.setCardBackgroundColor(SurfaceColors.SURFACE_1.getColor(itemView.getContext()));
            detailsView = itemView.findViewById(R.id.dictionary_details);
            activeSwitch = itemView.findViewById(R.id.dictionary_active);
            toggleDetailsBtn = itemView.findViewById(R.id.dictionary_btn_toggle_detail);
            toggleFavoriteBtn = itemView.findViewById(R.id.dictionary_btn_toggle_fav);
            updateBtn = itemView.findViewById(R.id.dictionary_btn_update);
            forgetBtn = itemView.findViewById(R.id.dictionary_btn_forget);
            titleView = itemView.findViewById(R.id.dictionary_label);
            errorContainer = itemView.findViewById(R.id.dictionary_error_row);
            errorView = itemView.findViewById(R.id.dictionary_error);
            blobCountView = itemView.findViewById(R.id.dictionary_blob_count);
            licenseContainer = itemView.findViewById(R.id.dictionary_license_row);
            licenseView = itemView.findViewById(R.id.dictionary_license);
            sourceContainer = itemView.findViewById(R.id.dictionary_source_row);
            sourceView = itemView.findViewById(R.id.dictionary_source);
            copyrightContainer = itemView.findViewById(R.id.dictionary_copyright_row);
            copyrightView = itemView.findViewById(R.id.dictionary_copyright);
            pathContainer = itemView.findViewById(R.id.dictionary_path_row);
            pathView = itemView.findViewById(R.id.dictionary_path);
        }
    }
}
