package itkach.aard2;

import android.app.DownloadManager;
import android.content.Context;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.LinkedList;

// reference: https://stackoverflow.com/questions/3028306/download-a-file-with-android-and-showing-the-progress-in-a-progressdialog
class DownloadDictionaryTask extends AsyncTask<Void, Void, Void> {

    private Context context;
    private DownloadDictionary dictionary;

    public DownloadDictionaryTask(Context context, DownloadDictionary dictionary) {
        this.context = context;
        this.dictionary = dictionary;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        String url = dictionary.getDictionaryDownloadAddress();
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        //request.setDescription("Downloading the " + dictionary.getDictionaryName() + " now");
        request.setTitle(dictionary.getDictionaryName()+".slob");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        }
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, dictionary.getDictionaryName()+".slob");

        // get download service and enqueue the file
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        manager.enqueue(request);
        return null;
    }
}

public class DictionaryDownloadListAdapter extends BaseAdapter implements ListAdapter {
    private Context mContext;
    private DownloadDictionaryList dictionariesList;
    private DownloadDictionaryDBHelper mDBHelper;

    public DictionaryDownloadListAdapter(Context context, DownloadDictionaryList dList, DownloadDictionaryDBHelper databaseHelper) {
        this.mContext = context;
        this.dictionariesList = dList;
        DataSetObserver observer = new DataSetObserver() {
            @Override
            public void onChanged() {
                notifyDataSetChanged();
            }

            @Override
            public void onInvalidated() {
                notifyDataSetInvalidated();
            }
        };
        dictionariesList.registerDataSetObserver(observer);
        this.mDBHelper = databaseHelper;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public int getCount() {
        if (!dictionariesList.isEmpty()) {
            return dictionariesList.size();
        }
        return 0;
    }

    @Override
    public Object getItem(int i) {
        if (!dictionariesList.isEmpty()) {
            return dictionariesList.get(i);
        }
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        final DownloadDictionary downloadDictionary = dictionariesList.get(i);
        if (view == null) {
            view = LayoutInflater.from(mContext).inflate(R.layout.download_dictionary_list_item, viewGroup, false);
        }
        TextView dictionaryName = (TextView) view.findViewById(R.id.dictionary_name);
        TextView downloadAddress = (TextView) view.findViewById(R.id.dictionary_download_address);
        final boolean isDictionaryDownloaded = downloadDictionary.getIsDownloaded();
        final Integer dictionaryId= downloadDictionary.getDictionaryId();
        dictionaryName.setText(downloadDictionary.getDictionaryName());
        downloadAddress.setText(downloadDictionary.getDictionaryDownloadAddress());

        // I may just download the dictionary here every time the user clicks on the checkbox.
        Button downloadCheck = (Button) view.findViewById(R.id.download);
        downloadCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new DownloadDictionaryTask(mContext, downloadDictionary).execute();
            }
        });
        return view;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        dictionariesList.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        dictionariesList.unregisterDataSetObserver(observer);
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetInvalidated() {
        super.notifyDataSetInvalidated();
    }
}
