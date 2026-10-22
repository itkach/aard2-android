package itkach.aard2;

import static itkach.aard2.Util.fromHtml;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
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
import android.widget.CompoundButton;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

import static java.lang.String.format;

public class DictionaryListAdapter extends RecyclerView.Adapter<DictionaryListAdapter.ViewHolder>
        implements ReleasableAdapter {

    private final static String TAG = DictionaryListAdapter.class.getName();

    private final SlobDescriptorList    data;
    private final Activity              context;
    private final DataSetObserver       observer;
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
        this.observer = new DataSetObserver() {
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

    // Detaches from the app-scoped dictionary list. Call when the owning view goes
    // away (DictionariesFragment.onDestroyView) so the list stops holding this
    // adapter - and, through its captured Activity, the whole view tree.
    @Override
    public void release() {
        data.unregisterDataSetObserver(observer);
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

        // The uri row turns the error color when there's no uri, so it keeps
        // its regular colors to go back to when the row is reused.
        TextView uriView = (TextView) view.findViewById(R.id.dictionary_uri);
        uriView.setTag(uriView.getTextColors());
        view.findViewById(R.id.dictionary_uri_row).setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                showUriInfo(data.get(position));
            }
        });

        // The switch itself isn't clickable (its scaled-down touch area would be
        // small); its 48dp frame owns the tap and toggles it.
        CompoundButton activeSwitch = (CompoundButton) view.findViewById(R.id.dictionary_active);
        view.findViewById(R.id.dictionary_active_touch).setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            activeSwitch.toggle();
            SlobDescriptor desc = data.get(position);
            desc.active = activeSwitch.isChecked();
            // Toggling active changes only which already-open dictionaries a
            // lookup considers, not which files are open, and nothing else in
            // this row reflects it (the switch is already toggled). So persist
            // the flag and refresh the active set directly, bypassing
            // data.set(), whose list-wide observer reopens every dictionary
            // file - increasingly slow as more are installed.
            data.save(desc);
            ((Application) context.getApplication()).onActiveDictionariesChanged();
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
            // Only affects random-article selection and this row's dice marker -
            // no files to reopen, no lookup to refresh. Persist and rebind just
            // this row, not data.set()'s list-wide reload of every dictionary.
            data.save(desc);
            notifyItemChanged(position);
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

        CompoundButton switchView = (CompoundButton) view.findViewById(R.id.dictionary_active);
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
        setupUriView(desc, view);
        setupPathView(fileName, available, view);
        setupErrorView(desc, view);

        ImageView btnToggleDetail = (ImageView) view.findViewById(R.id.dictionary_btn_toggle_detail);
        IconMaker.Glyph toggleIcon = desc.expandDetail ? IconMaker.IC_ANGLE_UP : IconMaker.IC_ANGLE_DOWN;
        btnToggleDetail.setImageDrawable(IconMaker.chevron(context, toggleIcon));


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
        pathIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_FILE));

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

    // The dictionary's uri tag, which its bookmarks, history and style go by,
    // or, in the error color, that it has none (a blank one counts as none):
    // it then goes by one made from its file's id (see SlobDescriptor.getURI),
    // which every new version changes. Hidden for a file that couldn't be
    // read when it was added: it has no tags at all, and its error is shown
    // instead.
    private void setupUriView(SlobDescriptor desc, View view) {
        View uriRow = view.findViewById(R.id.dictionary_uri_row);
        ImageView uriIcon = (ImageView) view.findViewById(R.id.dictionary_uri_icon);
        TextView uriView = (TextView) view.findViewById(R.id.dictionary_uri);

        uriRow.setVisibility(desc.id == null ? View.GONE : View.VISIBLE);
        String uri = desc.tags.get("uri");
        boolean missing = Util.isBlank(uri);
        String shown = missing ? context.getString(R.string.dictionary_uri_missing) : uri;
        uriView.setText(shown);
        // Only the fingerprint says what the row is, so screen readers get
        // that from here.
        uriRow.setContentDescription(
                context.getString(R.string.dictionary_uri_title) + ": " + shown);
        // The fingerprint stays either way, badged when there's no uri: it's
        // what says the row is about the uri. The badge sticks out to the
        // right, into the gap before the text, so the text stays in line with
        // the other rows'.
        ViewGroup.MarginLayoutParams iconParams =
                (ViewGroup.MarginLayoutParams) uriIcon.getLayoutParams();
        int gap = IconMaker.px(context, 4);
        if (missing) {
            uriIcon.setImageDrawable(IconMaker.textWithWarning(context, IconMaker.IC_URI));
            iconParams.setMarginEnd(gap - IconMaker.px(context, IconMaker.BADGE_OVERHANG_DP));
            uriView.setTextColor(IconMaker.resolveThemeColor(context,
                    androidx.appcompat.R.attr.colorError, 0xffcc0000));
        }
        else {
            uriIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_URI));
            iconParams.setMarginEnd(gap);
            uriView.setTextColor((ColorStateList) uriView.getTag());
        }
        uriIcon.setLayoutParams(iconParams);
        // Italic sets "missing" apart from a uri.
        uriView.setTypeface(Typeface.create(uriView.getTypeface(),
                missing ? Typeface.ITALIC : Typeface.NORMAL));
    }

    // The dictionary's uri, or that it has none, naming the dictionary, since
    // the dialog can cover the row it was opened from; then what the uri is
    // for - the same explanation either way - and, without one, what that
    // means and how to move the entries over anyway. The text can be
    // selected, so the uri can be copied, e.g. into a bug report.
    private void showUriInfo(SlobDescriptor desc) {
        String uri = desc.tags.get("uri");
        String about = context.getString(R.string.dictionary_uri_about);
        String message = Util.isBlank(uri)
                ? context.getString(R.string.dictionary_uri_none, desc.getLabel()) + "\n\n" + about
                        + "\n\n" + context.getString(R.string.dictionary_uri_none_tips)
                : uri + "\n\n" + about;
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dictionary_uri_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextIsSelectable(true);
        }
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
        View sourceRow = view.findViewById(R.id.dictionary_source_row);

        ImageView sourceIcon = (ImageView) view.findViewById(R.id.dictionary_source_icon);
        sourceIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_EXTERNAL_LINK));

        TextView sourceView = (TextView) view.findViewById(R.id.dictionary_source);
        String source = desc.tags.get("source");
        CharSequence sourceHtml = fromHtml(String.format(hrefTemplate, source, source));
        sourceView.setText(sourceHtml);
        sourceView.setTag(source);

        sourceRow.setVisibility(Util.isBlank(source) ? View.GONE : View.VISIBLE);
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
            license = fromHtml(String.format(hrefTemplate, licenseUrl, licenseName));
        }
        licenseView.setText(license);
        licenseView.setTag(licenseUrl);

        licenseRow.setVisibility(
                (Util.isBlank(licenseName) && Util.isBlank(licenseUrl)) ? View.GONE : View.VISIBLE);
        licenseRow.setEnabled(available);
    }

    // Close (remove from the app) the dictionary at position, returning the
    // removed descriptor so the caller can offer Undo. Not a delete: the file on
    // disk is untouched, and reopen() puts it back.
    SlobDescriptor close(int position) {
        return data.remove(position);
    }

    void reopen(int position, SlobDescriptor desc) {
        data.add(position, desc);
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
