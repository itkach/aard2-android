package itkach.aard2;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.ListFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.InputStream;
import java.util.List;

public class SettingsFragment extends ListFragment {


    private final static String TAG = SettingsFragment.class.getSimpleName();

    private AlertDialog clearCacheConfirmationDialog;

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setListAdapter(new SettingsListAdapter(this));
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        if (position == SettingsListAdapter.POS_CLEAR_CACHE) {
            clearCacheConfirmationDialog = new MaterialAlertDialogBuilder(requireActivity())
                    .setMessage(R.string.confirm_clear_cached_content)
                    .setPositiveButton(android.R.string.yes, (dialog, id1) -> {
                        WebView webView = new WebView(getActivity());
                        webView.clearCache(true);
                    })
                    .setNegativeButton(android.R.string.no, (dialog, id12) -> {
                        // User cancelled the dialog
                    })
                    .create();
            clearCacheConfirmationDialog.setOnDismissListener(dialogInterface -> clearCacheConfirmationDialog = null);
            clearCacheConfirmationDialog.show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != SettingsListAdapter.CSS_SELECT_REQUEST) {
            Log.d(TAG, String.format("Unknown request code: %d", requestCode));
            return;
        }
        Uri dataUri = data == null ? null : data.getData();
        Log.d(TAG, String.format("req code %s, result code: %s, data: %s", requestCode, resultCode, dataUri));
        if (resultCode == Activity.RESULT_OK && dataUri != null) {
            try {
                InputStream is = requireActivity().getContentResolver().openInputStream(dataUri);
                DocumentFile documentFile = DocumentFile.fromSingleUri(requireActivity(), dataUri);
                String fileName = documentFile.getName();
                Application app = (Application) getActivity().getApplication();
                String userCss = app.readTextFile(is, 256 * 1024);
                List<String> pathSegments = dataUri.getPathSegments();
                Log.d(TAG, fileName);
                Log.d(TAG, userCss);
                int lastIndexOfDot = fileName.lastIndexOf(".");
                if (lastIndexOfDot > -1) {
                    fileName = fileName.substring(0, lastIndexOfDot);
                }
                if (fileName.length() == 0) {
                    fileName = "???";
                }
                final SharedPreferences prefs = getActivity().getSharedPreferences(
                        "userStyles", Activity.MODE_PRIVATE);

                userCss = userCss.replace("\r", "").replace("\n", "\\n");

                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(fileName, userCss);
                boolean saved = editor.commit();
                if (!saved) {
                    Toast.makeText(getActivity(), R.string.msg_failed_to_store_user_style,
                            Toast.LENGTH_LONG).show();
                }
            } catch (Application.FileTooBigException e) {
                Log.d(TAG, "File is too big: " + dataUri);
                Toast.makeText(getActivity(), R.string.msg_file_too_big,
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.d(TAG, "Failed to load: " + dataUri, e);
                Toast.makeText(getActivity(), R.string.msg_failed_to_read_file,
                        Toast.LENGTH_LONG).show();
            }
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        if (clearCacheConfirmationDialog != null) {
            clearCacheConfirmationDialog.dismiss();
        }
    }
}
