package itkach.aard2;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.ListFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.io.InputStream;

import itkach.aard2.prefs.UserStylesPrefs;
import itkach.aard2.utils.Utils;

public class SettingsFragment extends ListFragment {


    private final static String TAG = SettingsFragment.class.getSimpleName();

    private AlertDialog clearCacheConfirmationDialog;
    public final ActivityResultLauncher<String> userStylesChooser = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) {
                    return;
                }
                try {
                    InputStream is = requireActivity().getContentResolver().openInputStream(uri);
                    DocumentFile documentFile = DocumentFile.fromSingleUri(requireActivity(), uri);
                    if (documentFile == null) {
                        throw new IOException("Could not access file");
                    }
                    String fileName = documentFile.getName();
                    if (fileName == null) {
                        fileName = uri.getLastPathSegment();
                    }
                    String userCss = Utils.readStream(is, 256 * 1024);
                    Log.d(TAG, fileName);
                    Log.d(TAG, userCss);
                    int lastIndexOfDot = fileName.lastIndexOf(".");
                    if (lastIndexOfDot > -1) {
                        fileName = fileName.substring(0, lastIndexOfDot);
                    }
                    if (fileName.length() == 0) {
                        fileName = "???";
                    }

                    userCss = userCss.replace("\r", "").replace("\n", "\\n");

                    if (!UserStylesPrefs.addStyle(fileName, userCss)) {
                        Toast.makeText(requireActivity(), R.string.msg_failed_to_store_user_style,
                                Toast.LENGTH_LONG).show();
                    }
                } catch (IOException e) {
                    if ("Too big file".equals(e.getMessage())) {
                        Toast.makeText(requireActivity(), R.string.msg_file_too_big, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireActivity(), R.string.msg_failed_to_read_file, Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
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
    public void onPause() {
        super.onPause();
        if (clearCacheConfirmationDialog != null) {
            clearCacheConfirmationDialog.dismiss();
        }
    }
}
