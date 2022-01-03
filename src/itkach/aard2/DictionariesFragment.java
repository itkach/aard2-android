package itkach.aard2;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
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

import java.io.File;

import static android.view.View.OnClickListener;

public class DictionariesFragment extends BaseListFragment {

    private final static String TAG = DictionariesFragment.class.getSimpleName();

    final static int FILE_SELECT_REQUEST = 17;

    private DictionaryListAdapter listAdapter;
    //private boolean findDictionariesOnAttach = false;

//    private class DiscoveryProgressDialog extends ProgressDialog {
//
//        public DiscoveryProgressDialog(Context context) {
//            super(context);
//            setIndeterminate(true);
//            setCancelable(false);
//            setTitle(getString(R.string.dictionaries_please_wait));
//            setMessage(getString(R.string.dictionaries_scanning_device));
//        }
//
//        @Override
//        public void onBackPressed() {
//            final Application app = (Application)getActivity().getApplication();
//            app.cancelFindDictionaries();
//        }
//    }


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
        Button btn = (Button)extraEmptyView.findViewById(R.id.dictionaries_empty_btn_scan);
        btn.setCompoundDrawablesWithIntrinsicBounds(
                IconMaker.list(getActivity(), IconMaker.IC_RELOAD),
                null, null, null);
        btn.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View v) {
//                findDictionaries();
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
        MenuItem miFindDictionaries = menu.findItem(R.id.action_find_dictionaries);
        FragmentActivity activity = getActivity();
        miFindDictionaries.setIcon(IconMaker.actionBar(activity, IconMaker.IC_RELOAD));
        MenuItem miAddDictionaries = menu.findItem(R.id.action_add_dictionaries);
        miAddDictionaries.setIcon(IconMaker.actionBar(activity, IconMaker.IC_ADD));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//        if (item.getItemId() == R.id.action_find_dictionaries) {
//            findDictionaries();
//            return true;
//        }
        if (item.getItemId() == R.id.action_add_dictionaries) {
//            Intent intent = new Intent(getActivity(), FileSelectActivity.class);
//            startActivityForResult(intent, FILE_SELECT_REQUEST);
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            Intent chooser = Intent.createChooser(intent, "Select Dictionary File");
            try {
                startActivityForResult(chooser, FILE_SELECT_REQUEST);
            }
            catch (ActivityNotFoundException e){
                Log.d(TAG, "Not activity to get content", e);
                Toast.makeText(getContext(), R.string.msg_no_activity_to_get_content,
                        Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

//    public void findDictionaries() {
//        Activity activity = getActivity();
//        if (activity == null) {
//            this.findDictionariesOnAttach = true;
//            return;
//        }
//        this.findDictionariesOnAttach = false;
//        final Application app = ((Application)activity.getApplication());
//        final ProgressDialog p = new DiscoveryProgressDialog(getActivity());
//        app.findDictionaries(new DictionaryDiscoveryCallback() {
//            @Override
//            public void onDiscoveryStarting() {
//                p.show();
//            }
//
//            @Override
//            public void onDiscoveryFinished() {
//                p.dismiss();
//            }
//        });
//    }



//    @Override
//    public void onAttach(Activity activity) {
//        super.onAttach(activity);
//        if (findDictionariesOnAttach) {
//            findDictionaries();
//        }
//    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_SELECT_REQUEST) {
            Log.d(TAG, "Unknown request code: " + requestCode);
            return;
        }

        Uri dataUri = data == null ? null : data.getData();
        Log.d(TAG, String.format("req code %s, result code: %s, data: %s", requestCode, resultCode, dataUri));

        if (resultCode == Activity.RESULT_OK && data != null) {
            final Application app = ((Application)getActivity().getApplication());
            boolean alreadyExists = app.addDictionary(dataUri);
            String toastMessage;
            if (alreadyExists) {
                toastMessage = getString(R.string.msg_dictionary_already_open);
            }
            else {
                toastMessage = getString(R.string.msg_dictionary_added, dataUri);
            }

            Toast.makeText(getActivity(), toastMessage, Toast.LENGTH_LONG).show();
        }
    }
}
