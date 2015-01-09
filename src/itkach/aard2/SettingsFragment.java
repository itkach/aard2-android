package itkach.aard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.ListView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.List;

public class SettingsFragment extends ListFragment {


    private final static String TAG = SettingsFragment.class.getSimpleName();

    private SettingsListAdapter listAdapter;
    private AlertDialog         clearCacheConfirmationDialog;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        listAdapter = new SettingsListAdapter(this);
        setListAdapter(listAdapter);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (position == SettingsListAdapter.POS_CLEAR_CACHE) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.confirm_clear_cached_content)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            WebView webView = new WebView(getActivity());
                            webView.clearCache(true);
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog
                        }
                    });
            clearCacheConfirmationDialog = builder.create();
            clearCacheConfirmationDialog.setOnDismissListener(new DialogInterface.OnDismissListener(){
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    clearCacheConfirmationDialog = null;
                }
            });
            clearCacheConfirmationDialog.show();
            return;
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
            String path = dataUri.getPath().toLowerCase();
            if (!(path.endsWith(".css") || path.endsWith(".txt"))) {
                Log.d(TAG, "Doesn't appear to be a css: " + dataUri);
                Toast.makeText(getActivity(), R.string.msg_file_not_css,
                        Toast.LENGTH_LONG).show();
                return;
            }
            try {
                InputStream is = getActivity().getContentResolver().openInputStream(dataUri);
                Application app = (Application)getActivity().getApplication();
                String userCss = app.readTextFile(is, 256 * 1024);
                List<String> pathSegments = dataUri.getPathSegments();
                String fileName = pathSegments.get(pathSegments.size() - 1);
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
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(fileName, userCss);
                boolean saved = editor.commit();
                if (!saved) {
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


    @Override
    public void onPause() {
        super.onPause();
        if (clearCacheConfirmationDialog != null) {
            clearCacheConfirmationDialog.dismiss();
        }
    }
}
