package itkach.aard2;

import android.app.Activity;
import android.content.Context;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.Map;

public class SettingsListAdapter extends BaseAdapter {

    SettingsListAdapter() {
    }

    @Override
    public int getCount() {
        return 4;
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
            case 0: return getRemoteContentSettingsView(convertView, parent);
            case 1: return getUserStylesView(convertView, parent);
            case 2: return getClearCacheView(convertView, parent);
            case 3: return getAboutView(convertView, parent);
        }
        return null;
    }

    private View getUserStylesView(View convertView, final ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        }
        else {
            LayoutInflater inflater = (LayoutInflater) parent.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.settings_user_styles_item, parent,
                    false);

            ListView userStyleList = (ListView)view.findViewById(R.id.setting_user_styles_list);
            final SharedPreferences prefs = view.getContext().getSharedPreferences(
                    "userStyles", Activity.MODE_PRIVATE);
            userStyleList.setAdapter(new UserStyleListAdapter(prefs));

            ImageView btnAdd = (ImageView)view.findViewById(R.id.setting_btn_add_user_style);
            btnAdd.setImageDrawable(Icons.ADD.forList());
            btnAdd.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    intent.setType("text/*");
                    Intent chooser = Intent.createChooser(intent, "Select CSS file");
                    ((Activity)parent.getContext()).startActivityForResult(chooser, 0);
                    return;
                }
            });
        };
        ListView userStyleList = (ListView)view.findViewById(R.id.setting_user_styles_list);
        View emptyView = view.findViewById(R.id.setting_user_styles_empty);
        emptyView.setVisibility(userStyleList.getAdapter().getCount() == 0 ? View.VISIBLE : View.GONE);
        return view;
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
            licenseView.setText(Html.fromHtml(license));

            PackageManager manager = context.getPackageManager();
            String versionName = "";
            try {
                PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
                versionName = info.versionName;
            } catch (PackageManager.NameNotFoundException e) {
               throw new RuntimeException(e);
            }

            String version = context.getString(R.string.application_version, versionName);
            TextView versionView = (TextView)view.findViewById(R.id.application_version);
            versionView.setText(Html.fromHtml(version));

        }
        return view;
    }

}
