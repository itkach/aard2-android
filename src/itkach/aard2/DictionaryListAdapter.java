package itkach.aard2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

import static java.lang.String.format;

public class DictionaryListAdapter extends RecyclerView.Adapter<DictionaryListAdapter.ViewHolder> {

    private final static String TAG = DictionaryListAdapter.class.getName();

    private final SlobDescriptorList    data;
    private final Activity              context;
    private AlertDialog                 deleteConfirmationDialog;
    private ItemTouchHelper             itemTouchHelper;

    void setItemTouchHelper(ItemTouchHelper helper) {
        this.itemTouchHelper = helper;
    }

    // Called repeatedly as a drag moves a row; rearranges the list visually. The
    // settled order is persisted once, on drag end (onDragFinished).
    void onItemMove(int from, int to) {
        data.move(from, to);
        notifyItemMoved(from, to);
    }

    void onDragFinished() {
        data.commitOrder();
    }

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
                notifyDataSetChanged();
            }
        };
        this.data.registerDataSetObserver(observer);
    }

    private final View.OnClickListener openUrlOnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String url = (String) v.getTag();
            if (!Util.isBlank(url)) {
                try {
                    Uri uri = Uri.parse(url);
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                    v.getContext().startActivity(browserIntent);
                } catch (Exception e) {
                    Log.d(TAG, "Failed to launch browser with url " + url, e);
                }
            }
        }
    };

    @NonNull
    @Override
    @SuppressLint("ClickableViewAccessibility")
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.dictionary_list_item, parent, false);
        final ViewHolder holder = new ViewHolder(view);

        view.findViewById(R.id.dictionary_license).setOnClickListener(openUrlOnClick);
        view.findViewById(R.id.dictionary_source).setOnClickListener(openUrlOnClick);

        Switch activeSwitch = (Switch) view.findViewById(R.id.dictionary_active);
        activeSwitch.setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            SlobDescriptor desc = data.get(position);
            desc.active = ((Switch) v).isChecked();
            data.set(position, desc);
        });

        view.findViewById(R.id.dictionary_btn_forget).setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                forget(position);
            }
        });

        View.OnClickListener detailToggle = v -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            SlobDescriptor desc = data.get(position);
            desc.expandDetail = !desc.expandDetail;
            // Expanding/collapsing changes only this one row. Rebind just it
            // rather than routing through data.set(), whose list-wide
            // notifyDataSetChanged rebinds every visible row - and each bind
            // does a ContentResolver getName() query, so a full rebind gets
            // noticeably slow as more dictionaries are installed. Persist the
            // new flag without a list-wide notification.
            notifyItemChanged(position);
            data.save(desc);
        };
        view.findViewById(R.id.dictionary_toggle_detail_area).setOnClickListener(detailToggle);

        CheckBox useForRandom = (CheckBox) view.findViewById(R.id.dictionary_use_for_random);
        useForRandom.setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            SlobDescriptor desc = data.get(position);
            desc.useForRandomLookup = ((CheckBox) v).isChecked();
            data.set(position, desc);
        });

        view.findViewById(R.id.dictionary_btn_drag_handle).setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && itemTouchHelper != null) {
                itemTouchHelper.startDrag(holder);
            }
            return false;
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        View view = holder.itemView;
        SlobDescriptor desc = data.get(position);
        String label = desc.getLabel();
        String fileName;
        try {
            DocumentFile documentFile = DocumentFile.fromSingleUri(view.getContext(), Uri.parse(desc.path));
            fileName = documentFile.getName();
        } catch (Exception ex) {
            fileName = desc.path;
            Log.w(TAG, "Couldn't parse get document file name from uri" + desc.path, ex);
        }
        long blobCount = desc.blobCount;
        boolean available = this.data.resolve(desc) != null;

        Resources r = view.getResources();

        Switch switchView = (Switch) view.findViewById(R.id.dictionary_active);
        switchView.setChecked(desc.active);

        TextView titleView = (TextView) view.findViewById(R.id.dictionary_label);
        titleView.setEnabled(available);
        titleView.setText(labelWithRandomMarker(label, desc.useForRandomLookup));
        titleView.setContentDescription(desc.useForRandomLookup
                ? context.getString(R.string.dictionary_marked_for_random_content_desc, label)
                : label);

        View detailView = view.findViewById(R.id.dictionary_details);
        detailView.setVisibility(desc.expandDetail ? View.VISIBLE : View.GONE);

        setupBlobCountView(desc, blobCount, available, view, r);
        setupCopyrightView(desc, available, view);
        setupLicenseView(desc, available, view);
        setupSourceView(desc, available, view);
        setupPathView(fileName, available, view);
        setupErrorView(desc, view);

        ImageView btnToggleDetail = (ImageView) view.findViewById(R.id.dictionary_btn_toggle_detail);
        IconMaker.Glyph toggleIcon = desc.expandDetail ? IconMaker.IC_ANGLE_UP : IconMaker.IC_ANGLE_DOWN;
        btnToggleDetail.setImageDrawable(IconMaker.chevron(context, toggleIcon));

        ImageView btnForget = (ImageView) view.findViewById(R.id.dictionary_btn_forget);
        btnForget.setImageDrawable(IconMaker.rowAction(context, IconMaker.IC_TRASH));

        ImageView dragHandle = (ImageView) view.findViewById(R.id.dictionary_btn_drag_handle);
        dragHandle.setImageDrawable(IconMaker.rowAction(context, IconMaker.IC_DRAG_HANDLE));

        CheckBox useForRandom = (CheckBox) view.findViewById(R.id.dictionary_use_for_random);
        useForRandom.setChecked(desc.useForRandomLookup);
    }

    // A small dice glyph (the same IC_RANDOM used by the toolbar's random-lookup
    // button) tacked onto the end of the name of any dictionary marked for
    // random lookup, so the marked ones are visible at a glance without
    // expanding each row. Deliberately not tied to the "use only marked
    // dictionaries" setting: the mark is a persistent choice worth showing
    // regardless, and that setting lives on a different tab that wouldn't
    // refresh these rows anyway.
    private CharSequence labelWithRandomMarker(String label, boolean marked) {
        if (!marked) {
            return label;
        }
        Drawable dice = IconMaker.make(context, IconMaker.IC_RANDOM, 13,
                IconMaker.resolveThemeColor(context,
                        androidx.appcompat.R.attr.colorPrimary, 0xff0099cc));
        dice.setBounds(0, 0, dice.getIntrinsicWidth(), dice.getIntrinsicHeight());
        SpannableStringBuilder sb = new SpannableStringBuilder(label);
        // A gap, then an object-replacement placeholder (U+FFFC) the span
        // renders over.
        sb.append(" ￼");
        sb.setSpan(new SuperscriptImageSpan(dice), sb.length() - 1, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    // ImageSpan draws its drawable on (or below) the baseline; this lifts the
    // small dice up to the text's cap height so it reads as a superscript, and
    // reserves only its width so the line height is unaffected.
    private static final class SuperscriptImageSpan extends ImageSpan {
        SuperscriptImageSpan(Drawable drawable) {
            super(drawable);
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                           Paint.FontMetricsInt fm) {
            return getDrawable().getBounds().width();
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, @NonNull Paint paint) {
            Drawable drawable = getDrawable();
            canvas.save();
            // y is the text baseline; ascent (negative) is the top of the text.
            canvas.translate(x, y + paint.getFontMetricsInt().ascent);
            drawable.draw(canvas);
            canvas.restore();
        }
    }

    private void setupPathView(String path, boolean available, View view) {
        View pathRow = view.findViewById(R.id.dictionary_path_row);

        ImageView pathIcon = (ImageView) view.findViewById(R.id.dictionary_path_icon);
        pathIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_FILE_ARCHIVE));

        TextView pathView = (TextView) view.findViewById(R.id.dictionary_path);
        pathView.setText(path);

        pathRow.setEnabled(available);
    }

    private void setupErrorView(SlobDescriptor desc, View view) {
        View errorRow= view.findViewById(R.id.dictionary_error_row);

        ImageView errorIcon = (ImageView) view.findViewById(R.id.dictionary_error_icon);
        errorIcon.setImageDrawable(IconMaker.errorText(context, IconMaker.IC_ERROR));

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
        copyrightIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_COPYRIGHT));

        TextView copyrightView = (TextView) view.findViewById(R.id.dictionary_copyright);
        String copyright = desc.tags.get("copyright");
        copyrightView.setText(copyright);

        copyrightRow.setVisibility(Util.isBlank(copyright) ? View.GONE : View.VISIBLE);
        copyrightRow.setEnabled(available);
    }

    private void setupSourceView(SlobDescriptor desc, boolean available, View view) {
        View sourceRow = view.findViewById(R.id.dictionary_license_row);

        ImageView sourceIcon = (ImageView) view.findViewById(R.id.dictionary_source_icon);
        sourceIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_EXTERNAL_LINK));

        TextView sourceView = (TextView) view.findViewById(R.id.dictionary_source);
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

        ImageView licenseIcon = (ImageView) view.findViewById(R.id.dictionary_license_icon);
        licenseIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_LICENSE));

        TextView licenseView = (TextView) view.findViewById(R.id.dictionary_license);
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
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View itemView) {
            super(itemView);
        }
    }

}
