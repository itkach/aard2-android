package itkach.aard2;

import android.app.ListActivity;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import java.io.File;


public class FileSelectActivity extends ListActivity {

    final static String KEY_SELECTED_FILE_PATH = "selectedFilePath";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final FileSelectListAdapter adapter = new FileSelectListAdapter();
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                File root = adapter.getRoot();
                String path;
                if (root != null) {
                    path = root.getAbsolutePath();
                } else {
                    path = "";
                }
                getActionBar().setSubtitle(path);
            }
        });
        File f = Environment.getExternalStorageDirectory();
        adapter.setRoot(f);
        setListAdapter(adapter);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_file_select, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        MenuItem miParentDir = menu.findItem(R.id.action_goto_parent_dir);
        miParentDir.setIcon(Icons.LEVEL_UP.forActionBar());
        MenuItem miReloadDir = menu.findItem(R.id.action_reload_directory);
        miReloadDir.setIcon(Icons.REFRESH.forActionBar());
        FileSelectListAdapter adapter = (FileSelectListAdapter)getListAdapter();
        File root = adapter.getRoot();
        File parent = root.getParentFile();
        miParentDir.setEnabled(parent != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        FileSelectListAdapter adapter = (FileSelectListAdapter)getListAdapter();
        if (id == R.id.action_goto_parent_dir) {
            File root = adapter.getRoot();
            File parent = root.getParentFile();
            if (parent != null) {
                adapter.setRoot(parent);
            }
            return true;
        }
        if (id == R.id.action_reload_directory) {
            adapter.reload();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        FileSelectListAdapter adapter = (FileSelectListAdapter)l.getAdapter();
        File f = (File)adapter.getItem(position);
        if (f.isDirectory()) {
            adapter.setRoot(f);
        }
        else {
            Intent data = new Intent();
            data.putExtra(KEY_SELECTED_FILE_PATH, f.getAbsolutePath());
            setResult(RESULT_OK, data);
            finish();
        }
    }
}
