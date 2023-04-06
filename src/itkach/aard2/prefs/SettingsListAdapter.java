package itkach.aard2.prefs;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.webkit.WebViewFeature;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.elevation.SurfaceColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;

import java.util.Collections;
import java.util.List;

import itkach.aard2.R;
import itkach.aard2.utils.Utils;

public class SettingsListAdapter extends RecyclerView.Adapter<SettingsListAdapter.ViewHolder> implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final static String TAG = SettingsListAdapter.class.getSimpleName();
    private final Activity context;
    @SuppressWarnings("FieldCanBeLocal")
    private final SharedPreferences userStylePrefs;
    private final View.OnClickListener onDeleteUserStyle;
    private final Fragment fragment;

    final static int POS_UI_THEME = 0;
    final static int POS_FORCE_DARK = 1;
    final static int POS_REMOTE_CONTENT = 2;
    final static int POS_FAV_RANDOM = 3;
    final static int POS_USE_VOLUME_FOR_NAV = 4;
    final static int POS_AUTO_PASTE = 5;
    final static int POS_DISABLE_JS = 6;
    final static int POS_USER_STYLES = 7;
    final static int POS_CLEAR_CACHE = 8;
    final static int POS_ABOUT = 9;

    SettingsListAdapter(Fragment fragment) {
        this.fragment = fragment;
        this.context = fragment.requireActivity();
        this.userStylePrefs = context.getSharedPreferences("userStyles", Activity.MODE_PRIVATE);
        this.userStylePrefs.registerOnSharedPreferenceChangeListener(this);

        this.onDeleteUserStyle = view -> {
            String name = (String) view.getTag();
            deleteUserStyle(name);
        };
    }

    @Override
    public int getItemCount() {
        return 10;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case POS_UI_THEME:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.settings_ui_theme_item, parent, false);
                break;
            case POS_REMOTE_CONTENT:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.settings_remote_content_item, parent, false);
                break;
            default:
            case POS_FORCE_DARK:
            case POS_FAV_RANDOM:
            case POS_USE_VOLUME_FOR_NAV:
            case POS_AUTO_PASTE:
            case POS_DISABLE_JS:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.settings_switch, parent, false);
                break;
            case POS_USER_STYLES:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.settings_user_styles_item, parent, false);
                break;
            case POS_CLEAR_CACHE:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.settings_clear_cache_item, parent, false);
                break;
            case POS_ABOUT:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.settings_about_item, parent, false);
                break;
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        switch (position) {
            case POS_UI_THEME:
                getUIThemeSettingsView(holder);
                break;
            case POS_FORCE_DARK:
                getForceDarkView(holder);
                break;
            case POS_REMOTE_CONTENT:
                getRemoteContentSettingsView(holder);
                break;
            case POS_FAV_RANDOM:
                getFavRandomSwitchView(holder);
                break;
            case POS_USE_VOLUME_FOR_NAV:
                getUseVolumeForNavView(holder);
                break;
            case POS_AUTO_PASTE:
                getAutoPasteView(holder);
                break;
            case POS_DISABLE_JS:
                getDisableJavaScriptView(holder);
                break;
            case POS_USER_STYLES:
                getUserStylesView(holder);
                break;
            case POS_CLEAR_CACHE:
                getClearCacheView(holder);
                break;
            case POS_ABOUT:
                getAboutView(holder);
                break;
        }
    }

    private void getUIThemeSettingsView(@NonNull ViewHolder holder) {
        View view = holder.itemView;
        String currentValue = AppPrefs.getPreferredTheme();

        View.OnClickListener clickListener = view1 -> {
            String value = null;
            int id = view1.getId();
            if (id == R.id.setting_ui_theme_auto) {
                value = AppPrefs.PREF_UI_THEME_AUTO;
            } else if (id == R.id.setting_ui_theme_light) {
                value = AppPrefs.PREF_UI_THEME_LIGHT;
            } else if (id == R.id.setting_ui_theme_dark) {
                value = AppPrefs.PREF_UI_THEME_DARK;
            }
            if (value != null) {
                AppPrefs.setPreferredTheme(value);
            }
            Utils.updateNightMode();
        };
        RadioButton btnAuto = view.findViewById(R.id.setting_ui_theme_auto);
        RadioButton btnLight = view.findViewById(R.id.setting_ui_theme_light);
        RadioButton btnDark = view.findViewById(R.id.setting_ui_theme_dark);
        btnAuto.setOnClickListener(clickListener);
        btnLight.setOnClickListener(clickListener);
        btnDark.setOnClickListener(clickListener);
        btnAuto.setChecked(currentValue.equals(AppPrefs.PREF_UI_THEME_AUTO));
        btnLight.setChecked(currentValue.equals(AppPrefs.PREF_UI_THEME_LIGHT));
        btnDark.setChecked(currentValue.equals(AppPrefs.PREF_UI_THEME_DARK));
    }

    private void getForceDarkView(@NonNull ViewHolder holder) {
        View view = holder.itemView;
        MaterialSwitch toggle = view.findViewById(R.id.setting_switch);
        toggle.setText(R.string.setting_enable_force_dark_web_view);
        toggle.setEnabled(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING));
        toggle.setOnClickListener(v -> {
            boolean currentValue = ArticleViewPrefs.enableForceDark();
            boolean newValue = !currentValue;
            ArticleViewPrefs.setEnableForceDark(newValue);
            toggle.setChecked(newValue);
        });
        view.findViewById(R.id.setting_subtitle).setVisibility(View.GONE);
        toggle.setChecked(ArticleViewPrefs.enableForceDark());
    }

    private void getFavRandomSwitchView(@NonNull ViewHolder holder) {
        View view = holder.itemView;
        MaterialSwitch toggle = view.findViewById(R.id.setting_switch);
        toggle.setText(R.string.setting_fav_random_search);
        toggle.setOnClickListener(v -> {
            boolean currentValue = AppPrefs.useOnlyFavoritesForRandomLookups();
            boolean newValue = !currentValue;
            AppPrefs.setUseOnlyFavoritesForRandomLookups(newValue);
            toggle.setChecked(newValue);
        });
        view.findViewById(R.id.setting_subtitle).setVisibility(View.GONE);
        toggle.setChecked(AppPrefs.useOnlyFavoritesForRandomLookups());
    }

    private void getUseVolumeForNavView(@NonNull ViewHolder holder) {
        View view = holder.itemView;
        MaterialSwitch toggle = view.findViewById(R.id.setting_switch);
        toggle.setText(R.string.setting_use_volume_for_nav);
        toggle.setOnClickListener(v -> {
            boolean currentValue = AppPrefs.useVolumeKeysForNavigation();
            boolean newValue = !currentValue;
            AppPrefs.setUseVolumeKeysForNavigation(newValue);
            toggle.setChecked(newValue);
        });
        view.findViewById(R.id.setting_subtitle).setVisibility(View.GONE);
        toggle.setChecked(AppPrefs.useVolumeKeysForNavigation());
    }

    private void getAutoPasteView(@NonNull ViewHolder holder) {
        View view = holder.itemView;
        MaterialSwitch toggle;
        toggle = view.findViewById(R.id.setting_switch);
        toggle.setText(R.string.setting_auto_paste);
        toggle.setOnClickListener(v -> {
            boolean currentValue = AppPrefs.autoPasteInLookup();
            boolean newValue = !currentValue;
            AppPrefs.setAutoPasteInLookup(newValue);
            toggle.setChecked(newValue);
        });
        view.findViewById(R.id.setting_subtitle).setVisibility(View.GONE);
        toggle.setChecked(AppPrefs.autoPasteInLookup());
    }

    private void getDisableJavaScriptView(@NonNull ViewHolder holder) {
        View view = holder.itemView;
        MaterialSwitch toggle;
        toggle = view.findViewById(R.id.setting_switch);
        toggle.setText(R.string.setting_disable_javascript_title);
        toggle.setOnClickListener(v -> {
            boolean currentValue = ArticleViewPrefs.disableJavaScript();
            boolean newValue = !currentValue;
            ArticleViewPrefs.setDisableJavaScript(newValue);
            toggle.setChecked(newValue);
        });
        MaterialTextView subtitleView = view.findViewById(R.id.setting_subtitle);
        subtitleView.setVisibility(View.VISIBLE);
        subtitleView.setText(R.string.setting_disable_javascript_subtitle);
        toggle.setChecked(ArticleViewPrefs.disableJavaScript());
    }


    private void getUserStylesView(@NonNull ViewHolder holder) {
        View view = holder.itemView;
        MaterialButton btnAdd = view.findViewById(R.id.setting_btn_add_user_style);
        btnAdd.setOnClickListener(view1 -> {
            try {
                ((SettingsFragment) fragment).userStylesChooser.launch("text/*");
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "Not activity to get content", e);
                Toast.makeText(context, R.string.msg_no_activity_to_get_content,
                        Toast.LENGTH_LONG).show();
            }
        });

        List<String> userStyleNames = UserStylesPrefs.listStyleNames();
        Collections.sort(userStyleNames);

        View emptyView = view.findViewById(R.id.setting_user_styles_empty);
        emptyView.setVisibility(userStyleNames.size() == 0 ? View.VISIBLE : View.GONE);

        LinearLayoutCompat userStyleListLayout = view.findViewById(R.id.setting_user_styles_list);
        userStyleListLayout.removeAllViews();
        for (String userStyleName : userStyleNames) {
            View styleItemView = View.inflate(view.getContext(), R.layout.user_styles_list_item, null);
            ImageView btnDelete = styleItemView.findViewById(R.id.user_styles_list_btn_delete);
            btnDelete.setOnClickListener(onDeleteUserStyle);
            btnDelete.setTag(userStyleName);

            TextView nameView = styleItemView.findViewById(R.id.user_styles_list_name);
            nameView.setText(userStyleName);

            userStyleListLayout.addView(styleItemView);
        }

    }

    private void deleteUserStyle(final String name) {
        String message = context.getString(R.string.setting_user_style_confirm_forget, name);
        new MaterialAlertDialogBuilder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.setting_user_styles)
                .setMessage(message)
                .setPositiveButton(R.string.action_yes, (dialog, which) -> {
                    Log.d(TAG, "Deleting user style " + name);
                    UserStylesPrefs.removeStyle(name);
                })
                .setNegativeButton(R.string.action_no, null)
                .show();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        notifyDataSetChanged();
    }

    private void getRemoteContentSettingsView(@NonNull ViewHolder holder) {
        View view = holder.itemView;
        String currentValue = ArticleViewPrefs.getRemoteContentPreference();
        View.OnClickListener clickListener = view1 -> {
            String value = null;
            int id = view1.getId();
            if (id == R.id.setting_remote_content_always) {
                value = ArticleViewPrefs.PREF_REMOTE_CONTENT_ALWAYS;
            } else if (id == R.id.setting_remote_content_wifi) {
                value = ArticleViewPrefs.PREF_REMOTE_CONTENT_WIFI;
            } else if (id == R.id.setting_remote_content_never) {
                value = ArticleViewPrefs.PREF_REMOTE_CONTENT_NEVER;
            }
            if (value != null) {
                ArticleViewPrefs.setRemoteContentPreference(value);
            }
        };
        RadioButton btnAlways = view.findViewById(R.id.setting_remote_content_always);
        RadioButton btnWiFi = view.findViewById(R.id.setting_remote_content_wifi);
        RadioButton btnNever = view.findViewById(R.id.setting_remote_content_never);
        btnAlways.setOnClickListener(clickListener);
        btnWiFi.setOnClickListener(clickListener);
        btnNever.setOnClickListener(clickListener);
        btnAlways.setChecked(currentValue.equals(ArticleViewPrefs.PREF_REMOTE_CONTENT_ALWAYS));
        btnWiFi.setChecked(currentValue.equals(ArticleViewPrefs.PREF_REMOTE_CONTENT_WIFI));
        btnNever.setChecked(currentValue.equals(ArticleViewPrefs.PREF_REMOTE_CONTENT_NEVER));
    }

    private void getClearCacheView(@NonNull ViewHolder holder) {
        holder.cardView.setOnClickListener(v -> new MaterialAlertDialogBuilder(fragment.requireActivity())
                .setMessage(R.string.confirm_clear_cached_content)
                .setPositiveButton(R.string.action_yes, (dialog, id1) -> {
                    WebView webView = new WebView(fragment.requireActivity());
                    webView.clearCache(true);
                })
                .setNegativeButton(R.string.action_no, null)
                .show());
    }

    private void getAboutView(@NonNull ViewHolder holder) {
        View view = holder.itemView;

        String appName = context.getString(R.string.app_name);
        String title = context.getString(R.string.setting_about, appName);

        TextView titleView = view.findViewById(R.id.setting_about);
        titleView.setText(title);

        String licenseName = context.getString(R.string.application_license_name);
        final String licenseUrl = context.getString(R.string.application_license_url);
        String license = context.getString(R.string.application_license, licenseUrl, licenseName);
        TextView licenseView = view.findViewById(R.id.application_license);
        licenseView.setOnClickListener(view1 -> {
            Uri uri = Uri.parse(licenseUrl);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
            context.startActivity(browserIntent);
        });
        licenseView.setText(HtmlCompat.fromHtml(license.trim(), HtmlCompat.FROM_HTML_MODE_LEGACY));

        PackageManager manager = context.getPackageManager();
        String versionName;
        try {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "?";
        }

        TextView versionView = view.findViewById(R.id.application_version);
        versionView.setText(context.getString(R.string.application_version, versionName));

    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final MaterialCardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            cardView.setCardBackgroundColor(SurfaceColors.SURFACE_1.getColor(itemView.getContext()));
        }
    }
}
