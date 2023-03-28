package itkach.aard2;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class DictionariesFragment extends BaseListFragment {
    private final static String TAG = DictionariesFragment.class.getSimpleName();

    private final ActivityResultLauncher<Intent> dictionarySelector = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result == null || result.getResultCode() != Activity.RESULT_OK) {
                    return;
                }
                Intent intent = result.getData();
                Uri dataUri = intent == null ? null : intent.getData();
                if (dataUri == null) {
                    return;
                }
                final Application app = ((Application) requireActivity().getApplication());
                List<Uri> selection = new ArrayList<>();
                selection.add(dataUri);
                ClipData clipData = intent.getClipData();
                if (clipData != null) {
                    int itemCount = clipData.getItemCount();
                    for (int i = 0; i < itemCount; i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        selection.add(uri);
                    }
                }
                for (Uri uri : selection) {
                    requireActivity().getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    app.addDictionary(uri);
                }
            });

    @DrawableRes
    @Override
    protected int getEmptyIcon() {
        return R.drawable.ic_library_books;
    }

    @NonNull
    @Override
    protected CharSequence getEmptyText() {
        return getString(R.string.main_empty_dictionaries);
    }

    @Override
    protected boolean supportsSelection() {
        return false;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Application app = (Application) requireActivity().getApplication();
        DictionaryListAdapter listAdapter = new DictionaryListAdapter(app.dictionaries, getActivity());
        setListAdapter(listAdapter);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View result = super.onCreateView(inflater, container, savedInstanceState);
        View extraEmptyView = inflater.inflate(R.layout.dictionaries_empty_view_extra, container, false);
        MaterialButton btn = extraEmptyView.findViewById(R.id.action_add_dictionaries);
        btn.setOnClickListener(v -> selectDictionaryFiles());
        LinearLayoutCompat emptyViewLayout = emptyView.findViewById(R.id.container);
        emptyViewLayout.addView(extraEmptyView);
        return result;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.dictionaries, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
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
            dictionarySelector.launch(intent);
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "Not activity to get content", e);
            Toast.makeText(getContext(), R.string.msg_no_activity_to_get_content, Toast.LENGTH_LONG).show();
        }
    }
}
