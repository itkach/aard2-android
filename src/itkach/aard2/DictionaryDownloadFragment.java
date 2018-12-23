package itkach.aard2;

import android.app.Activity;
import android.app.Fragment;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.ListFragment;
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
import android.widget.ListView;
import android.widget.Toast;

import com.shamanland.fonticon.FontIconDrawable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class UpdateDatabaseTask extends AsyncTask<Void, Void, Void> {
    private DictionaryDownloadFragment mFragment;

    public UpdateDatabaseTask(DictionaryDownloadFragment fragment) {
        this.mFragment = fragment;
    }
    @Override
    protected Void doInBackground(Void... voids) {
        mFragment.parseCSVIntoDB();
        return null;
    }
}

public class DictionaryDownloadFragment extends ListFragment {

    private final static String TAG = DictionaryDownloadFragment.class.getSimpleName();
    private DownloadDictionaryDBHelper mDBHelper;
    private ListView listView;

    private DictionaryDownloadListAdapter listAdapter;
    private DownloadDictionaryList dictionaryList = new DownloadDictionaryList();

    protected int getEmptyIcon() {
        return R.xml.ic_empty_view_dictionary;
    }

    protected CharSequence getEmptyText() {
        return Html.fromHtml(getString(R.string.main_empty_dictionaries));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View result = inflater.inflate(R.layout.download_dictionary_list, container, false);
        listView = (ListView) result.findViewById(android.R.id.list);
        mDBHelper = new DownloadDictionaryDBHelper(getActivity());
        new UpdateDatabaseTask(this).execute();
        // sleep for 1000ms so that the task updateDatabase can finish.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        HashMap<Integer, ArrayList<String>> information = mDBHelper.getAllInformation();
        // get all the information from the hashmap and populate it into the listview
        for (Map.Entry<Integer, ArrayList<String>> entry : information.entrySet()) {
            Integer dictionaryID = entry.getKey();
            ArrayList<String> info = entry.getValue();
            DownloadDictionary downloadDictionary = new DownloadDictionary(dictionaryID, info.get(0), info.get(1), false);
            if (info.get(2).equals("1")) {
                downloadDictionary.setDownloaded(true);
            }
            dictionaryList.add(downloadDictionary);
        }
        listAdapter = new DictionaryDownloadListAdapter(getActivity(), dictionaryList, mDBHelper);
        listView.setAdapter(listAdapter);
        return result;
    }

    // a method for parsing the data from csv file into the new Database
    public void parseCSVIntoDB() {
        SQLiteDatabase mDB = mDBHelper.getWritableDatabase();
        String csvFile = "dictionaryDownload.csv";
        InputStream mInput = null;
        try {
            AssetManager am = getActivity().getAssets();
            mInput = am.open(csvFile);

        } catch (IOException e) {
            e.printStackTrace();
            Log.v("IO exception","Cannot open " + csvFile + "");
        }
        BufferedReader buffer = new BufferedReader(new InputStreamReader(mInput));
        String line;
        mDB.beginTransaction();
        try {
            while ((line = buffer.readLine()) != null) {
                String[] columns = line.split(",");
                // columns[1] is the dictionary name, columns[2] is the dictionary download url
                mDBHelper.insertNewDictionary(columns[1], columns[2]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        mDB.setTransactionSuccessful();
        mDB.endTransaction();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }
}
