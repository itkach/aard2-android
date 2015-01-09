package itkach.aard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.support.v4.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.shamanland.fonticon.FontIconDrawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SettingsListAdapter extends BaseAdapter implements SharedPreferences.OnSharedPreferenceChangeListener {

    final static int CSS_SELECT_REQUEST = 13;

    private final static String TAG = SettingsListAdapter.class.getSimpleName();
    private final Activity      context;

    private List<String>            userStyleNames;
    private Map<String, ?>          userStyleData;
    private SharedPreferences       userStylePrefs;
    private View.OnClickListener    onDeleteUserStyle;
    private Fragment                fragment;


    final static int POS_REMOTE_CONTENT = 0;
    final static int POS_FAV_RANDOM = 1;
    final static int POS_USER_STYLES = 2;
    final static int POS_CLEAR_CACHE = 3;
    final static int POS_ABOUT = 4;

    SettingsListAdapter(Fragment fragment) {
        this.fragment = fragment;
        this.context = fragment.getActivity();

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

    @Override
    public int getCount() {
        return 5;
    }

    @Override
    public Object getItem(int i) {
        return i;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public int getViewTypeCount() {
        return getCount();
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup parent) {
        switch (i) {
            case POS_REMOTE_CONTENT: return getRemoteContentSettingsView(convertView, parent);
            case POS_FAV_RANDOM: return getFavRandomSwitchView(convertView, parent);
            case POS_USER_STYLES: return getUserStylesView(convertView, parent);
            case POS_CLEAR_CACHE: return getClearCacheView(convertView, parent);
            case POS_ABOUT: return getAboutView(convertView, parent);
        }
        return null;
    }

    private View getFavRandomSwitchView(View convertView, ViewGroup parent) {
        View view;
        LayoutInflater inflater = (LayoutInflater) parent.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final Application app = (Application)context.getApplication();
        if (convertView != null) {
            view = convertView;
        }
        else {
            view = inflater.inflate(R.layout.settings_fav_random_search, parent,
                    false);
            final CheckedTextView toggle = (CheckedTextView)view.findViewById(R.id.setting_fav_random_search);
            toggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean currentValue = app.isOnlyFavDictsForRandomLookup();
                    boolean newValue = !currentValue;
                    app.setOnlyFavDictsForRandomLookup(newValue);
                    toggle.setChecked(newValue);
                }
            });
        }
        boolean currentValue = app.isOnlyFavDictsForRandomLookup();
        CheckedTextView toggle = (CheckedTextView)view.findViewById(R.id.setting_fav_random_search);
        toggle.setChecked(currentValue);
        return view;
    }

    private View getUserStylesView(View convertView, final ViewGroup parent) {
        View view;
        LayoutInflater inflater = (LayoutInflater) parent.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (convertView != null) {
            view = convertView;
        }
        else {
            this.userStyleData = userStylePrefs.getAll();
            this.userStyleNames = new ArrayList<String>(this.userStyleData.keySet());
            Collections.sort(this.userStyleNames);

            view = inflater.inflate(R.layout.settings_user_styles_item, parent,
                    false);
            ImageView btnAdd = (ImageView)view.findViewById(R.id.setting_btn_add_user_style);
            btnAdd.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_list_add));
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
        };

        View emptyView = view.findViewById(R.id.setting_user_styles_empty);
        emptyView.setVisibility(userStyleNames.size() == 0 ? View.VISIBLE : View.GONE);

        LinearLayout userStyleListLayout = (LinearLayout)view.findViewById(R.id.setting_user_styles_list);
        userStyleListLayout.removeAllViews();
        for (int i = 0; i < userStyleNames.size(); i++) {
            View styleItemView = inflater.inflate(R.layout.user_styles_list_item, parent,
                    false);
            ImageView btnDelete = (ImageView)styleItemView.findViewById(R.id.user_styles_list_btn_delete);
            btnDelete.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_list_trash));
            btnDelete.setOnClickListener(onDeleteUserStyle);

            String name = userStyleNames.get(i);

            btnDelete.setTag(name);

            TextView nameView = (TextView)styleItemView.findViewById(R.id.user_styles_list_name);
            nameView.setText(name);

            userStyleListLayout.addView(styleItemView);
        }

        return view;
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
        Collections.sort(userStyleNames);
        notifyDataSetChanged();
    }

    private View getRemoteContentSettingsView(View convertView, ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        }
        else {
            LayoutInflater inflater = (LayoutInflater) parent.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.settings_remote_content_item, parent,
                    false);

            final SharedPreferences prefs = view.getContext().getSharedPreferences(
                    ArticleWebView.PREF, Activity.MODE_PRIVATE);

            String currentValue = prefs.getString(ArticleWebView.PREF_REMOTE_CONTENT,
                    ArticleWebView.PREF_REMOTE_CONTENT_WIFI);
            Log.d("Settings", "Remote content, current value: " + currentValue);

            View.OnClickListener clickListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    SharedPreferences.Editor editor = prefs.edit();
                    String value = null;
                    switch(view.getId()) {
                        case R.id.setting_remote_content_always:
                            value = ArticleWebView.PREF_REMOTE_CONTENT_ALWAYS;
                            break;
                        case R.id.setting_remote_content_wifi:
                            value = ArticleWebView.PREF_REMOTE_CONTENT_WIFI;
                            break;
                        case R.id.setting_remote_content_never:
                            value = ArticleWebView.PREF_REMOTE_CONTENT_NEVER;
                            break;
                    }
                    Log.d("Settings", "Remote content: " + value);
                    if (value != null) {
                        editor.putString(ArticleWebView.PREF_REMOTE_CONTENT, value);
                        editor.commit();
                    }
                }
            };
            RadioButton btnAlways = (RadioButton) view
                    .findViewById(R.id.setting_remote_content_always);
            RadioButton btnWiFi = (RadioButton) view
                    .findViewById(R.id.setting_remote_content_wifi);
            RadioButton btnNever = (RadioButton) view
                    .findViewById(R.id.setting_remote_content_never);
            btnAlways.setOnClickListener(clickListener);
            btnWiFi.setOnClickListener(clickListener);
            btnNever.setOnClickListener(clickListener);
            btnAlways.setChecked(currentValue.equals(ArticleWebView.PREF_REMOTE_CONTENT_ALWAYS));
            btnWiFi.setChecked(currentValue.equals(ArticleWebView.PREF_REMOTE_CONTENT_WIFI));
            btnNever.setChecked(currentValue.equals(ArticleWebView.PREF_REMOTE_CONTENT_NEVER));
        };
        return view;
    }

    private View getClearCacheView(View convertView, ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        }
        else {
            final Context context = parent.getContext();
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.settings_clear_cache_item, parent,
                    false);
        }
        return view;
    }

    private View getAboutView(View convertView, ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        }
        else {
            final Context context = parent.getContext();
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.settings_about_item, parent,
                    false);

            ImageView copyrightIcon = (ImageView) view.findViewById(R.id.setting_about_copyright_icon);
            copyrightIcon.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_text_copyright));

            ImageView licenseIcon = (ImageView) view.findViewById(R.id.setting_about_license_icon);
            licenseIcon.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_text_license));

            ImageView sourceIcon = (ImageView) view.findViewById(R.id.setting_about_source_icon);
            sourceIcon.setImageDrawable(FontIconDrawable.inflate(context, R.xml.ic_text_external_link));

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

        }
        return view;
    }

}
