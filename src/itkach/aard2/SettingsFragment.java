package itkach.aard2;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.webkit.WebView;
import android.widget.ListView;

public class SettingsFragment extends ListFragment {


    private SettingsListAdapter listAdapter;
    private AlertDialog         clearCacheConfirmationDialog;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        listAdapter = new SettingsListAdapter(getActivity());
        setListAdapter(listAdapter);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (position == listAdapter.getCount() - 1) {
            Uri uri = Uri.parse(getString(R.string.application_home_url));
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(browserIntent);
            return;
        }
        if (position == 2) {
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
    public void onPause() {
        super.onPause();
        if (clearCacheConfirmationDialog != null) {
            clearCacheConfirmationDialog.dismiss();
        }
    }
}
