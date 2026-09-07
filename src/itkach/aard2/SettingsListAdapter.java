package itkach.aard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SettingsListAdapter extends RecyclerView.Adapter<SettingsListAdapter.ViewHolder>
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    final static int CSS_SELECT_REQUEST = 13;

    private final static String TAG = SettingsListAdapter.class.getSimpleName();
    private final Activity      context;
    private final Application   app;

    private List<String>            userStyleNames;
    private Map<String, ?>          userStyleData;
    private SharedPreferences       userStylePrefs;
    private View.OnClickListener    onDeleteUserStyle;
    private Fragment                fragment;
    private OnItemClickListener     itemClickListener;


    final static int POS_UI_THEME = 0;
    final static int POS_REMOTE_CONTENT = 1;
    final static int POS_FAV_RANDOM = 2;
    final static int POS_USE_VOLUME_FOR_NAV = 3;
    final static int POS_AUTO_PASTE = 4;
    final static int POS_USER_STYLES = 5;
    final static int POS_CLEAR_CACHE = 6;
    final static int POS_ABOUT = 7;

    SettingsListAdapter(Fragment fragment) {
        this.fragment = fragment;
        this.context = fragment.getActivity();
        this.app = (Application)this.context.getApplication();
        this.userStylePrefs = context.getSharedPreferences(
                "userStyles", Activity.MODE_PRIVATE);
        this.userStylePrefs.registerOnSharedPreferenceChangeListener(this);

        this.onDeleteUserStyle = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = (String)view.getTag();
                deleteUserStyle(name);
            }
        };
    }

    void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    @Override
    public int getItemCount() {
        return 8;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(createView(parent, viewType));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        bindView(holder.itemView, position);
    }

    private View createView(ViewGroup parent, int viewType) {
        switch (viewType) {
            case POS_UI_THEME: return createUIThemeSettingsView(parent);
            case POS_REMOTE_CONTENT: return createRemoteContentSettingsView(parent);
            case POS_FAV_RANDOM: return createFavRandomSwitchView(parent);
            case POS_USE_VOLUME_FOR_NAV: return createUseVolumeForNavView(parent);
            case POS_AUTO_PASTE: return createAutoPasteView(parent);
            case POS_USER_STYLES: return createUserStylesView(parent);
            case POS_CLEAR_CACHE: return createClearCacheView(parent);
            case POS_ABOUT: return createAboutView(parent);
        }
        throw new IllegalArgumentException("Unexpected view type " + viewType);
    }

    private void bindView(View view, int position) {
        switch (position) {
            case POS_UI_THEME: bindUIThemeSettingsView(view); break;
            case POS_REMOTE_CONTENT: bindRemoteContentSettingsView(view); break;
            case POS_FAV_RANDOM: bindFavRandomSwitchView(view); break;
            case POS_USE_VOLUME_FOR_NAV: bindUseVolumeForNavView(view); break;
            case POS_AUTO_PASTE: bindAutoPasteView(view); break;
            case POS_USER_STYLES: bindUserStylesView(view); break;
            default: break; // clear-cache and about rows are static
        }
    }

    private static LayoutInflater inflater(ViewGroup parent) {
        return (LayoutInflater) parent.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    private View createUIThemeSettingsView(ViewGroup parent) {
        View view = inflater(parent).inflate(R.layout.settings_ui_theme_item, parent, false);
        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences prefs = app.prefs();
                SharedPreferences.Editor editor = prefs.edit();
                String value = null;
                int viewId = view.getId();
                if (viewId == R.id.setting_ui_theme_light) {
                    value = Application.PREF_UI_THEME_LIGHT;
                } else if (viewId == R.id.setting_ui_theme_dark) {
                    value = Application.PREF_UI_THEME_DARK;
                }
                Log.d("Settings", Application.PREF_UI_THEME + ": " + value);
                if (value != null) {
                    editor.putString(Application.PREF_UI_THEME, value);
                    editor.commit();
                }
                context.recreate();
            }
        };
        view.findViewById(R.id.setting_ui_theme_light).setOnClickListener(clickListener);
        view.findViewById(R.id.setting_ui_theme_dark).setOnClickListener(clickListener);
        return view;
    }

    private void bindUIThemeSettingsView(View view) {
        String currentValue = app.prefs().getString(Application.PREF_UI_THEME,
                Application.PREF_UI_THEME_LIGHT);
        ((RadioButton) view.findViewById(R.id.setting_ui_theme_light))
                .setChecked(currentValue.equals(Application.PREF_UI_THEME_LIGHT));
        ((RadioButton) view.findViewById(R.id.setting_ui_theme_dark))
                .setChecked(currentValue.equals(Application.PREF_UI_THEME_DARK));
    }

    private View createFavRandomSwitchView(ViewGroup parent) {
        View view = inflater(parent).inflate(R.layout.settings_fav_random_search, parent, false);
        final CheckedTextView toggle = (CheckedTextView)view.findViewById(R.id.setting_fav_random_search);
        toggle.setOnClickListener(v -> {
            boolean newValue = !app.isOnlyFavDictsForRandomLookup();
            app.setOnlyFavDictsForRandomLookup(newValue);
            toggle.setChecked(newValue);
        });
        return view;
    }

    private void bindFavRandomSwitchView(View view) {
        ((CheckedTextView)view.findViewById(R.id.setting_fav_random_search))
                .setChecked(app.isOnlyFavDictsForRandomLookup());
    }

    private View createUseVolumeForNavView(ViewGroup parent) {
        View view = inflater(parent).inflate(R.layout.settings_use_volume_for_nav, parent, false);
        final CheckedTextView toggle = (CheckedTextView)view.findViewById(R.id.setting_use_volume_for_nav);
        toggle.setOnClickListener(v -> {
            boolean newValue = !app.useVolumeForNav();
            app.setUseVolumeForNav(newValue);
            toggle.setChecked(newValue);
        });
        return view;
    }

    private void bindUseVolumeForNavView(View view) {
        ((CheckedTextView)view.findViewById(R.id.setting_use_volume_for_nav))
                .setChecked(app.useVolumeForNav());
    }

    private View createAutoPasteView(ViewGroup parent) {
        View view = inflater(parent).inflate(R.layout.settings_auto_paste, parent, false);
        final CheckedTextView toggle = (CheckedTextView)view.findViewById(R.id.setting_auto_paste);
        toggle.setOnClickListener(v -> {
            boolean newValue = !app.autoPaste();
            app.setAutoPaste(newValue);
            toggle.setChecked(newValue);
        });
        return view;
    }

    private void bindAutoPasteView(View view) {
        ((CheckedTextView)view.findViewById(R.id.setting_auto_paste))
                .setChecked(app.autoPaste());
    }

    private View createUserStylesView(final ViewGroup parent) {
        View view = inflater(parent).inflate(R.layout.settings_user_styles_item, parent, false);
        ImageView btnAdd = view.findViewById(R.id.setting_btn_add_user_style);
        btnAdd.setImageDrawable(IconMaker.list(context, IconMaker.IC_ADD));
        btnAdd.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("text/*");
                Intent chooser = Intent.createChooser(intent, "Select CSS file");
                try {
                    fragment.startActivityForResult(chooser, CSS_SELECT_REQUEST);
                }
                catch (ActivityNotFoundException e){
                    Log.d(TAG, "Not activity to get content", e);
                    Toast.makeText(context, R.string.msg_no_activity_to_get_content,
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        return view;
    }

    private void bindUserStylesView(View view) {
        this.userStyleData = userStylePrefs.getAll();
        this.userStyleNames = new ArrayList<String>(this.userStyleData.keySet());
        Util.sort(this.userStyleNames);

        View emptyView = view.findViewById(R.id.setting_user_styles_empty);
        emptyView.setVisibility(userStyleNames.size() == 0 ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = (LayoutInflater) view.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout userStyleListLayout = (LinearLayout)view.findViewById(R.id.setting_user_styles_list);
        userStyleListLayout.removeAllViews();
        for (int i = 0; i < userStyleNames.size(); i++) {
            View styleItemView = inflater.inflate(R.layout.user_styles_list_item,
                    userStyleListLayout, false);
            ImageView btnDelete = (ImageView)styleItemView.findViewById(R.id.user_styles_list_btn_delete);
            btnDelete.setImageDrawable(IconMaker.list(context, IconMaker.IC_TRASH));
            btnDelete.setOnClickListener(onDeleteUserStyle);

            String name = userStyleNames.get(i);
            btnDelete.setTag(name);

            TextView nameView = (TextView)styleItemView.findViewById(R.id.user_styles_list_name);
            nameView.setText(name);

            userStyleListLayout.addView(styleItemView);
        }
    }

    private void deleteUserStyle(final String name) {
        String message = context.getString(R.string.setting_user_style_confirm_forget, name);
        new AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d(TAG, "Deleting user style " + name);
                        SharedPreferences.Editor edit = userStylePrefs.edit();
                        edit.remove(name);
                        edit.commit();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        this.userStyleData = sharedPreferences.getAll();
        this.userStyleNames = new ArrayList<String>(this.userStyleData.keySet());
        Util.sort(userStyleNames);
        notifyItemChanged(POS_USER_STYLES);
    }

    private View createRemoteContentSettingsView(ViewGroup parent) {
        View view = inflater(parent).inflate(R.layout.settings_remote_content_item, parent, false);
        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences prefs = view.getContext().getSharedPreferences(
                        Application.ARTICLE_VIEW_PREF, Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                String value = null;
                int viewId = view.getId();
                if (viewId == R.id.setting_remote_content_always) {
                    value = ArticleWebView.PREF_REMOTE_CONTENT_ALWAYS;
                } else if (viewId == R.id.setting_remote_content_wifi) {
                    value = ArticleWebView.PREF_REMOTE_CONTENT_WIFI;
                } else if (viewId == R.id.setting_remote_content_never) {
                    value = ArticleWebView.PREF_REMOTE_CONTENT_NEVER;
                }
                Log.d("Settings", "Remote content: " + value);
                if (value != null) {
                    editor.putString(ArticleWebView.PREF_REMOTE_CONTENT, value);
                    editor.commit();
                }
            }
        };
        view.findViewById(R.id.setting_remote_content_always).setOnClickListener(clickListener);
        view.findViewById(R.id.setting_remote_content_wifi).setOnClickListener(clickListener);
        view.findViewById(R.id.setting_remote_content_never).setOnClickListener(clickListener);
        return view;
    }

    private void bindRemoteContentSettingsView(View view) {
        SharedPreferences prefs = view.getContext().getSharedPreferences(
                Application.ARTICLE_VIEW_PREF, Activity.MODE_PRIVATE);
        String currentValue = prefs.getString(ArticleWebView.PREF_REMOTE_CONTENT,
                ArticleWebView.PREF_REMOTE_CONTENT_WIFI);
        ((RadioButton) view.findViewById(R.id.setting_remote_content_always))
                .setChecked(currentValue.equals(ArticleWebView.PREF_REMOTE_CONTENT_ALWAYS));
        ((RadioButton) view.findViewById(R.id.setting_remote_content_wifi))
                .setChecked(currentValue.equals(ArticleWebView.PREF_REMOTE_CONTENT_WIFI));
        ((RadioButton) view.findViewById(R.id.setting_remote_content_never))
                .setChecked(currentValue.equals(ArticleWebView.PREF_REMOTE_CONTENT_NEVER));
    }

    private View createClearCacheView(ViewGroup parent) {
        View view = inflater(parent).inflate(R.layout.settings_clear_cache_item, parent, false);
        view.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(POS_CLEAR_CACHE);
            }
        });
        return view;
    }

    private View createAboutView(ViewGroup parent) {
        final Context context = parent.getContext();
        View view = inflater(parent).inflate(R.layout.settings_about_item, parent, false);

        ImageView copyrightIcon = (ImageView) view.findViewById(R.id.setting_about_copyright_icon);
        copyrightIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_COPYRIGHT));

        ImageView licenseIcon = (ImageView) view.findViewById(R.id.setting_about_license_icon);
        licenseIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_LICENSE));

        ImageView sourceIcon = (ImageView) view.findViewById(R.id.setting_about_source_icon);
        sourceIcon.setImageDrawable(IconMaker.text(context, IconMaker.IC_EXTERNAL_LINK));

        String appName = context.getString(R.string.app_name);
        String title = context.getString(R.string.setting_about, appName);
        TextView titleView = (TextView)view.findViewById(R.id.setting_about);
        titleView.setText(title);

        String licenseName = context.getString(R.string.application_license_name);
        final String licenseUrl = context.getString(R.string.application_license_url);
        String license = context.getString(R.string.application_license, licenseUrl, licenseName);
        TextView licenseView = (TextView)view.findViewById(R.id.application_license);
        licenseView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Uri uri = Uri.parse(licenseUrl);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                context.startActivity(browserIntent);
            }
        });
        licenseView.setText(Html.fromHtml(license.trim()));

        PackageManager manager = context.getPackageManager();
        String versionName;
        try {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "?";
        }
        String version = context.getString(R.string.application_version, versionName);
        TextView versionView = (TextView)view.findViewById(R.id.application_version);
        versionView.setText(Html.fromHtml(version));
        return view;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View itemView) {
            super(itemView);
        }
    }

}
