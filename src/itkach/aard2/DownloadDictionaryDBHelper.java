package itkach.aard2;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.File;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;
import android.util.Log;

import itkach.slob.Slob;


public class DownloadDictionaryDBHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelperTag";
    private static final String DATABASE_NAME = "DownloadDictionaryDB.db";
    private static String DATABASE_PATH = "";
    private static final String DICTIONARY_TABLE_NAME = "dictionaries";
    private static final String DICTIONARY_COLUMN_ID = "id";
    private static final String DICTIONARY_COLUMN_DOWNLOAD_ADDRESS = "download_address";
    private static final String DICTIONARY_COLUMN_DICTIONARY_NAME = "dictionary_name";
    private static final String DICTIONARY_COLUMN_DOWNLOADED = "is_downloaded";
    private final Context mContext;
    private SQLiteDatabase mDB;

    public DownloadDictionaryDBHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, 1);
        DATABASE_PATH = context.getApplicationInfo().dataDir + "/databases/";
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        // if there is no Database, create one and parse the data from csv file into the new Database
        sqLiteDatabase.execSQL(
                "CREATE TABLE dictionaries " +
                        "(id INTEGER PRIMARY KEY AUTOINCREMENT, dictionary_name TEXT UNIQUE, download_address TEXT, is_downloaded BOOLEAN)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS dictionaries");
        onCreate(sqLiteDatabase);
    }

    // Check if the database exists here
    private boolean checkDatabase() {
        File dbFile = new File(DATABASE_PATH + DATABASE_NAME);
        Log.v("dbFile", dbFile + " " + dbFile.exists());
        return dbFile.exists();
    }

    // reference: https://stackoverflow.com/questions/15707796/sqlitedatabase-insert-only-if-the-value-does-not-exist-not-via-raw-sql-command
    // insert if and only if the record does not existed
    public int getCount(String dictionaryName) {
        Cursor c = null;
        try {
            mDB = this.getReadableDatabase();
            String query = "SELECT count(*) from " + DICTIONARY_TABLE_NAME + " WHERE " + DICTIONARY_COLUMN_DICTIONARY_NAME + " = ?";
            c= mDB.rawQuery(query, new String[] { dictionaryName });
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            return 0;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    public boolean insertNewDictionary(String dictionaryName, String downloadAddress) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(DICTIONARY_COLUMN_DICTIONARY_NAME, dictionaryName);
        contentValues.put(DICTIONARY_COLUMN_DOWNLOAD_ADDRESS, downloadAddress);
        contentValues.put(DICTIONARY_COLUMN_DOWNLOADED, false);
        int count = getCount(dictionaryName);
        // if it doesn't exist, insert it into the table, else do nothing
        if (count == 0) {
            db.insertWithOnConflict(DICTIONARY_TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
        }
        return true;
    }

    public boolean updateRow(String id, String dictionaryName, String downloadAddress, boolean isDownloaded) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(DICTIONARY_COLUMN_ID, id);
        contentValues.put(DICTIONARY_COLUMN_DICTIONARY_NAME, dictionaryName);
        contentValues.put(DICTIONARY_COLUMN_DOWNLOAD_ADDRESS, downloadAddress);
        contentValues.put(DICTIONARY_COLUMN_DOWNLOADED, isDownloaded);
        db.update(DICTIONARY_TABLE_NAME, contentValues, "ID = ?", new String[] {id});
        return true;
    }

    public Cursor getData(int id) {
        mDB = this.getReadableDatabase();
        return mDB.rawQuery("SELECT * FROM "+DICTIONARY_TABLE_NAME+" WHERE id ="+id+"", null);
    }

    public HashMap<Integer, ArrayList<String>> getAllInformation() {
        // map: id-> [dictioanry_name, download_address, is_downloaded]
        HashMap<Integer, ArrayList<String>> map = new HashMap<Integer, ArrayList<String>>();

        mDB = this.getReadableDatabase();
        Cursor res = mDB.rawQuery("SELECT * FROM "+ DICTIONARY_TABLE_NAME + "", null);
        res.moveToFirst();

        while(!res.isAfterLast()) {
            ArrayList<String> address_downloaded_pair = new ArrayList<String>();
            address_downloaded_pair.add(res.getString(res.getColumnIndex(DICTIONARY_COLUMN_DICTIONARY_NAME)));
            address_downloaded_pair.add(res.getString(res.getColumnIndex(DICTIONARY_COLUMN_DOWNLOAD_ADDRESS)));
            address_downloaded_pair.add(res.getString(res.getColumnIndex(DICTIONARY_COLUMN_DOWNLOADED)));
            map.put(Integer.parseInt(res.getString(res.getColumnIndex(DICTIONARY_COLUMN_ID))), address_downloaded_pair);
            res.moveToNext();
        }
        res.close();
        return map;
    }

}
