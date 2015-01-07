package itkach.aard2;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.shamanland.fonticon.FontIconDrawable;

import java.io.File;
import java.io.FileFilter;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Created by itkach on 12/19/14.
 */
public class FileSelectListAdapter extends BaseAdapter {

    private final static File[] EMPTY = new File[0];

    private File             root;
    private FileFilter       fileFilter;
    private File[]           files;
    private Comparator<File> comparator;

    public FileSelectListAdapter() {
        fileFilter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory() || (
                        pathname.isFile() &&
                        pathname.getName().toLowerCase().endsWith(".slob"));
            }
        };

        comparator = new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                if (f1.isDirectory() && !f2.isDirectory()) {
                    return 1;
                }
                if (!f1.isDirectory() && f2.isDirectory()) {
                    return -1;
                }
                return f1.getName().compareTo(f2.getName());
            }
        };

        files = EMPTY;
    }

    public File getRoot() {
        return root;
    }

    void setRoot(File root) {
        this.root = root;
        if (root != null) {
            files = EMPTY;
        }
        files = root.listFiles(fileFilter);
        if (files == null) {
            files = EMPTY;
        }
        Arrays.sort(files, comparator);
        notifyDataSetChanged();
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        File f = (File)getItem(position);
        return f.isDirectory() ? 0 : 1;
    }

    @Override
    public int getCount() {
        return files.length;
    }

    @Override
    public Object getItem(int position) {
        return files[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        File f = (File)getItem(position);
        boolean isDir = f.isDirectory();
        View view;
        if (convertView != null) {
            view = convertView;
        }
        else {
            LayoutInflater inflater = (LayoutInflater) parent.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            int viewId = isDir ? R.layout.file_select_directory_item : R.layout.file_select_file_item;
            view = inflater.inflate(viewId, parent, false);
            if (isDir) {
                ImageView dirIconView = (ImageView)view.findViewById(R.id.file_select_directory_icon);
                dirIconView.setImageDrawable(FontIconDrawable.inflate(parent.getContext(), R.xml.ic_list_folder));
            }
        }

        TextView fileNameView = (TextView)view.findViewById(R.id.file_select_file_name);
        fileNameView.setText(f.getName());

        if (!isDir) {
            TextView timeStampView = (TextView)view.findViewById(R.id.file_select_file_timestamp);
            timeStampView.setText(DateUtils.formatDateTime(parent.getContext(), f.lastModified(), 0));

            TextView fileSizeView = (TextView)view.findViewById(R.id.file_select_file_size);
            fileSizeView.setText(NumberFormat.getNumberInstance().format(f.length()));
        }

        return view;
    }

    public void reload() {
        setRoot(this.root);
    }
}
