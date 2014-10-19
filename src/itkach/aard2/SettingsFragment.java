package itkach.aard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.ListView;

public class SettingsFragment extends ListFragment {


    private SettingsListAdapter listAdapter;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Application app = (Application)getActivity().getApplication();
        listAdapter = new SettingsListAdapter();
        setListAdapter(listAdapter);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        switch (position) {
            case 0:
                //openRemoteContentSettings();
                break;
            case 1:
                openAbout();
                break;
        }
    }

    private void openRemoteContentSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final String[] labels = new String[]{"Always", "When on Wi-Fi", "Never"};
        final SharedPreferences prefs = getActivity().getSharedPreferences(
                ArticleWebView.PREF, Activity.MODE_PRIVATE);
        String currentValue = prefs.getString(ArticleWebView.PREF_REMOTE_CONTENT,
                ArticleWebView.PREF_REMOTE_CONTENT_WIFI);
        final String[] values = new String[]{
                ArticleWebView.PREF_REMOTE_CONTENT_ALWAYS,
                ArticleWebView.PREF_REMOTE_CONTENT_WIFI,
                ArticleWebView.PREF_REMOTE_CONTENT_NEVER,
        };
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentValue)) {
                selected = i;
                break;
            }
        }

        final int[] userChoice = new int[1];
        builder.setTitle(R.string.select_remote_content_mode)
                .setSingleChoiceItems(labels, selected, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        userChoice[0] = i;
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        int selectedIndex = userChoice[0];
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(ArticleWebView.PREF_REMOTE_CONTENT, values[selectedIndex]);
                        editor.commit();
                        listAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void openAbout() {
    }

}
