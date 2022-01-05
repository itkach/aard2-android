package itkach.aard2;

import static android.view.View.OnClickListener;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class DictionariesFragment extends BaseListFragment {

    private final static String TAG = DictionariesFragment.class.getSimpleName();

    final static int FILE_SELECT_REQUEST = 17;

    private DictionaryListAdapter listAdapter;

    protected char getEmptyIcon() {
        return IconMaker.IC_DICTIONARY;
    }

    protected CharSequence getEmptyText() {
        return Html.fromHtml(getString(R.string.main_empty_dictionaries));
    }

    @Override
    protected boolean supportsSelection() {
        return false;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Application app = (Application)getActivity().getApplication();
        listAdapter = new DictionaryListAdapter(app.dictionaries, getActivity());
        setListAdapter(listAdapter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View result = super.onCreateView(inflater, container, savedInstanceState);
        View extraEmptyView = inflater.inflate(R.layout.dictionaries_empty_view_extra, container, false);
        Button btn = extraEmptyView.findViewById(R.id.dictionaries_empty_btn_scan);
        btn.setCompoundDrawablesWithIntrinsicBounds(
                IconMaker.list(getActivity(), IconMaker.IC_ADD),
                null, null, null);
        btn.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View v) {
                selectDictionaryFiles();
            }
        });
        LinearLayout emptyViewLayout = (LinearLayout)emptyView;
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        emptyViewLayout.addView(extraEmptyView, layoutParams);
        return result;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.dictionaries, menu);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        FragmentActivity activity = getActivity();
        MenuItem miAddDictionaries = menu.findItem(R.id.action_add_dictionaries);
        miAddDictionaries.setIcon(IconMaker.actionBar(activity, IconMaker.IC_ADD));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_add_dictionaries) {
            selectDictionaryFiles();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void selectDictionaryFiles() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("*/*");
        Intent chooser = Intent.createChooser(intent, getResources().getString(R.string.title_activity_file_select));
        try {
            startActivityForResult(chooser, FILE_SELECT_REQUEST);
        }
        catch (ActivityNotFoundException e){
            Log.d(TAG, "Not activity to get content", e);
            Toast.makeText(getContext(), R.string.msg_no_activity_to_get_content,
                    Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode != FILE_SELECT_REQUEST) {
            Log.d(TAG, "Unknown request code: " + requestCode);
            return;
        }

        Uri dataUri = intent == null ? null : intent.getData();
        Log.d(TAG, String.format("req code %s, result code: %s, data: %s", requestCode, resultCode, dataUri));

        if (resultCode == Activity.RESULT_OK && intent != null) {
            final Application app = ((Application)getActivity().getApplication());
            List<Uri> selection = new ArrayList<>();
            if (dataUri != null) {
                selection.add(dataUri);
            }
            ClipData clipData = intent.getClipData();
            if (clipData != null) {
                int itemCount = clipData.getItemCount();
                for (int i = 0; i < itemCount; i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    selection.add(uri);
                }
            }
            for (Uri uri : selection) {
                getActivity().getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                app.addDictionary(uri);
            }
        }
    }
}
