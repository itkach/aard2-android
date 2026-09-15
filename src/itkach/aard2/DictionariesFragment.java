package itkach.aard2;

import static android.view.View.OnClickListener;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class DictionariesFragment extends BaseListFragment {

    private final static String TAG = DictionariesFragment.class.getSimpleName();

    private DictionaryListAdapter listAdapter;

    private final ActivityResultLauncher<Intent> filePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> onFilesSelected(result.getResultCode(), result.getData()));

    protected IconMaker.Glyph getEmptyIcon() {
        return IconMaker.IC_DICTIONARY;
    }

    protected CharSequence getEmptyText() {
        return Html.fromHtml(getString(R.string.main_empty_dictionaries));
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Application app = (Application)getActivity().getApplication();
        listAdapter = new DictionaryListAdapter(app.dictionaries, getActivity());
        setListAdapter(listAdapter);

        // Drag-to-reorder via the row's grip handle (long-press-drag disabled so
        // the handle is the only initiator). onMove rearranges live; the settled
        // order is persisted in clearView (drag end).
        ItemTouchHelper helper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                    @Override
                    public boolean onMove(@androidx.annotation.NonNull RecyclerView rv,
                                          @androidx.annotation.NonNull RecyclerView.ViewHolder vh,
                                          @androidx.annotation.NonNull RecyclerView.ViewHolder target) {
                        listAdapter.onItemMove(vh.getBindingAdapterPosition(),
                                target.getBindingAdapterPosition());
                        return true;
                    }

                    @Override
                    public void onSwiped(@androidx.annotation.NonNull RecyclerView.ViewHolder vh, int direction) {
                    }

                    @Override
                    public boolean isLongPressDragEnabled() {
                        return false;
                    }

                    @Override
                    public void clearView(@androidx.annotation.NonNull RecyclerView rv,
                                          @androidx.annotation.NonNull RecyclerView.ViewHolder vh) {
                        super.clearView(rv, vh);
                        listAdapter.onDragFinished();
                    }
                });
        helper.attachToRecyclerView(getRecyclerView());
        listAdapter.setItemTouchHelper(helper);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View result = super.onCreateView(inflater, container, savedInstanceState);
        View extraEmptyView = inflater.inflate(R.layout.dictionaries_empty_view_extra, container, false);
        MaterialButton btn = extraEmptyView.findViewById(R.id.dictionaries_empty_btn_scan);
        // FontDrawable ignores tint, so colour the "+" to the button's own text
        // colour (onPrimary for the filled button) and disable MaterialButton's
        // icon tint so it isn't overridden.
        btn.setIconTint(null);
        btn.setIcon(IconMaker.make(getActivity(), IconMaker.IC_ADD, 20, btn.getCurrentTextColor()));
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
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            filePicker.launch(intent);
        }
        catch (ActivityNotFoundException e){
            Log.d(TAG, "Not activity to get content", e);
            Toast.makeText(getContext(), R.string.msg_no_activity_to_get_content,
                    Toast.LENGTH_LONG).show();
        }
    }


    private void onFilesSelected(int resultCode, Intent intent) {
        Uri dataUri = intent == null ? null : intent.getData();
        Log.d(TAG, String.format("result code: %s, data: %s", resultCode, dataUri));

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
