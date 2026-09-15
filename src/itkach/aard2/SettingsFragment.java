package itkach.aard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The Settings screen. A fixed set of sections laid out in a plain ScrollView
 * (see fragment_settings.xml) - there is nothing dynamic to recycle, so no
 * RecyclerView/adapter. Each section's controls are wired up here.
 */
public class SettingsFragment extends Fragment {

    private final static String TAG = SettingsFragment.class.getSimpleName();

    private Application app;
    private View rootView;

    private final ActivityResultLauncher<Intent> cssPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> onCssSelected(result.getResultCode(), result.getData()));

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.rootView = view;
        this.app = (Application) requireActivity().getApplication();

        setupUiTheme(view);
        setupRemoteContent(view);
        setupSwitch(view, R.id.setting_fav_random_search,
                app.isOnlyFavDictsForRandomLookup(), app::setOnlyFavDictsForRandomLookup);
        setupSwitch(view, R.id.setting_use_volume_for_nav,
                app.useVolumeForNav(), app::setUseVolumeForNav);
        setupSwitch(view, R.id.setting_auto_paste,
                app.autoPaste(), app::setAutoPaste);
        setupRecordHistory(view);
        setupUserStyles(view);
        setupAbout(view);
    }

    // The user-styles list mirrors the .css files in the user style directory;
    // refresh it whenever this screen becomes visible (and after add/delete).
    @Override
    public void onResume() {
        super.onResume();
        populateUserStyles(rootView);
    }

    private void setupUiTheme(View view) {
        RadioButton light = view.findViewById(R.id.setting_ui_theme_light);
        RadioButton dark = view.findViewById(R.id.setting_ui_theme_dark);
        View.OnClickListener clickListener = v -> {
            String value = v.getId() == R.id.setting_ui_theme_dark
                    ? Application.PREF_UI_THEME_DARK : Application.PREF_UI_THEME_LIGHT;
            app.prefs().edit().putString(Application.PREF_UI_THEME, value).commit();
            requireActivity().recreate();
        };
        light.setOnClickListener(clickListener);
        dark.setOnClickListener(clickListener);
        String current = app.prefs().getString(Application.PREF_UI_THEME,
                Application.PREF_UI_THEME_LIGHT);
        light.setChecked(current.equals(Application.PREF_UI_THEME_LIGHT));
        dark.setChecked(current.equals(Application.PREF_UI_THEME_DARK));
    }

    private void setupRemoteContent(View view) {
        final SharedPreferences prefs = requireActivity().getSharedPreferences(
                Application.ARTICLE_VIEW_PREF, Activity.MODE_PRIVATE);
        RadioButton always = view.findViewById(R.id.setting_remote_content_always);
        RadioButton wifi = view.findViewById(R.id.setting_remote_content_wifi);
        RadioButton never = view.findViewById(R.id.setting_remote_content_never);
        View.OnClickListener clickListener = v -> {
            String value = null;
            int id = v.getId();
            if (id == R.id.setting_remote_content_always) {
                value = ArticleWebView.PREF_REMOTE_CONTENT_ALWAYS;
            } else if (id == R.id.setting_remote_content_wifi) {
                value = ArticleWebView.PREF_REMOTE_CONTENT_WIFI;
            } else if (id == R.id.setting_remote_content_never) {
                value = ArticleWebView.PREF_REMOTE_CONTENT_NEVER;
            }
            if (value != null) {
                prefs.edit().putString(ArticleWebView.PREF_REMOTE_CONTENT, value).commit();
            }
        };
        always.setOnClickListener(clickListener);
        wifi.setOnClickListener(clickListener);
        never.setOnClickListener(clickListener);
        String current = prefs.getString(ArticleWebView.PREF_REMOTE_CONTENT,
                ArticleWebView.PREF_REMOTE_CONTENT_WIFI);
        always.setChecked(current.equals(ArticleWebView.PREF_REMOTE_CONTENT_ALWAYS));
        wifi.setChecked(current.equals(ArticleWebView.PREF_REMOTE_CONTENT_WIFI));
        never.setChecked(current.equals(ArticleWebView.PREF_REMOTE_CONTENT_NEVER));
    }

    private interface BooleanSetter { void set(boolean value); }

    private void setupSwitch(View view, int id, boolean initial, BooleanSetter setter) {
        CompoundButton toggle = view.findViewById(id);
        toggle.setChecked(initial);
        // The switch auto-toggles its own state on click; persist that new state.
        toggle.setOnClickListener(v -> setter.set(toggle.isChecked()));
    }

    // Record history is not a plain toggle: turning it off deletes the history
    // recorded so far and hides the History section, so it confirms first (unless
    // there is nothing to delete). Turning it back on just resumes recording and
    // restores the section.
    private void setupRecordHistory(View view) {
        CompoundButton toggle = view.findViewById(R.id.setting_record_history);
        toggle.setChecked(app.recordHistory());
        toggle.setOnClickListener(v -> {
            if (toggle.isChecked()) {
                app.setRecordHistory(true);
                ((MainActivity) requireActivity()).setHistoryVisible(true);
            } else if (app.history.isEmpty()) {
                app.setRecordHistory(false);
                ((MainActivity) requireActivity()).setHistoryVisible(false);
            } else {
                new AlertDialog.Builder(requireActivity())
                        .setTitle(R.string.setting_record_history_off_title)
                        .setMessage(R.string.setting_record_history_off_message)
                        .setPositiveButton(R.string.setting_record_history_off_confirm,
                                (dialog, which) -> {
                                    app.setRecordHistory(false);
                                    app.history.clear();
                                    ((MainActivity) requireActivity()).setHistoryVisible(false);
                                })
                        .setNegativeButton(android.R.string.cancel,
                                (dialog, which) -> toggle.setChecked(true))
                        .setOnCancelListener(dialog -> toggle.setChecked(true))
                        .show();
            }
        });
    }

    private void setupUserStyles(View view) {
        MaterialButton btnAdd = view.findViewById(R.id.setting_btn_add_user_style);
        // Keep the glyph's own colour (colorPrimary) rather than letting the
        // button re-tint it, so it matches the outlined button's text.
        btnAdd.setIcon(IconMaker.make(requireActivity(), IconMaker.IC_ADD, 18,
                IconMaker.resolveThemeColor(requireActivity(),
                        androidx.appcompat.R.attr.colorPrimary, 0xff0099cc)));
        btnAdd.setIconTint(null);
        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/*");
            Intent chooser = Intent.createChooser(intent, "Select CSS file");
            try {
                cssPicker.launch(chooser);
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "No activity to get content", e);
                Toast.makeText(getContext(), R.string.msg_no_activity_to_get_content,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void populateUserStyles(View view) {
        if (view == null) {
            return;
        }
        List<String> names = new ArrayList<>(app.userStyleNames());
        Util.sort(names);
        LayoutInflater inflater = LayoutInflater.from(requireActivity());
        LinearLayout list = view.findViewById(R.id.setting_user_styles_list);
        list.removeAllViews();
        for (final String name : names) {
            View row = inflater.inflate(R.layout.user_styles_list_item, list, false);
            ImageView btnDelete = row.findViewById(R.id.user_styles_list_btn_delete);
            btnDelete.setImageDrawable(IconMaker.list(requireActivity(), IconMaker.IC_TRASH));
            btnDelete.setOnClickListener(v -> confirmDeleteUserStyle(name));
            TextView nameView = row.findViewById(R.id.user_styles_list_name);
            // The identifier carries the .css extension; show it stripped.
            nameView.setText(displayStyleName(name));
            list.addView(row);
        }
    }

    // Style identifiers are file names (with .css); labels drop the extension.
    private static String displayStyleName(String name) {
        return name.endsWith(".css") ? name.substring(0, name.length() - 4) : name;
    }

    private void confirmDeleteUserStyle(final String name) {
        String message = getString(R.string.setting_user_style_confirm_forget,
                displayStyleName(name));
        new AlertDialog.Builder(requireActivity())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    app.deleteUserStyle(name);
                    populateUserStyles(rootView);
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void setupAbout(View view) {
        Context ctx = requireActivity();
        ((ImageView) view.findViewById(R.id.setting_about_copyright_icon))
                .setImageDrawable(IconMaker.text(ctx, IconMaker.IC_COPYRIGHT));
        ((ImageView) view.findViewById(R.id.setting_about_license_icon))
                .setImageDrawable(IconMaker.text(ctx, IconMaker.IC_LICENSE));
        ((ImageView) view.findViewById(R.id.setting_about_source_icon))
                .setImageDrawable(IconMaker.text(ctx, IconMaker.IC_EXTERNAL_LINK));

        String licenseName = getString(R.string.application_license_name);
        final String licenseUrl = getString(R.string.application_license_url);
        String license = getString(R.string.application_license, licenseUrl, licenseName);
        TextView licenseView = view.findViewById(R.id.application_license);
        licenseView.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(licenseUrl))));
        licenseView.setText(Html.fromHtml(license.trim()));

        String versionName;
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "?";
        }
        ((TextView) view.findViewById(R.id.application_version))
                .setText(Html.fromHtml(getString(R.string.application_version, versionName)));
    }

    private void onCssSelected(int resultCode, Intent data) {
        Uri dataUri = data == null ? null : data.getData();
        Log.d(TAG, String.format("result code: %s, data: %s", resultCode, dataUri));
        if (resultCode == Activity.RESULT_OK && dataUri != null) {
            try {
                InputStream is = getActivity().getContentResolver().openInputStream(dataUri);
                DocumentFile documentFile = DocumentFile.fromSingleUri(getContext(), dataUri);
                String fileName = documentFile.getName();
                Application app = (Application)getActivity().getApplication();
                String userCss = app.readTextFile(is, 256 * 1024);
                // The file name (with a .css extension) is the style identifier
                // and, sanitized of path separators, the file Slobber serves.
                if (fileName == null || fileName.isEmpty()) {
                    fileName = "user";
                }
                fileName = fileName.replaceAll("[/\\\\]", "_");
                if (!fileName.toLowerCase().endsWith(".css")) {
                    fileName = fileName + ".css";
                }
                try {
                    app.saveUserStyle(fileName, userCss);
                    populateUserStyles(rootView);
                } catch (IOException e) {
                    Log.d(TAG, "Failed to store user style " + fileName, e);
                    Toast.makeText(getActivity(), R.string.msg_failed_to_store_user_style,
                            Toast.LENGTH_LONG).show();
                }
            }
            catch (Application.FileTooBigException e) {
                Log.d(TAG, "File is too big: " + dataUri);
                Toast.makeText(getActivity(), R.string.msg_file_too_big,
                        Toast.LENGTH_LONG).show();
            }
            catch (Exception e) {
                Log.d(TAG, "Failed to load: " + dataUri, e);
                Toast.makeText(getActivity(), R.string.msg_failed_to_read_file,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
