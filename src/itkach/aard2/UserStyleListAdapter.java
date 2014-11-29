package itkach.aard2;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by itkach on 11/28/14.
 */
public class UserStyleListAdapter extends BaseAdapter implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final SharedPreferences prefs;
    private List<String> names;
    private Map<String, ?> data;

    UserStyleListAdapter(SharedPreferences prefs) {
        this.prefs = prefs;
        onSharedPreferenceChanged(prefs, null);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public int getCount() {
        Log.d("AAA", "Size:" + names.size());
        return names.size();
    }

    @Override
    public Object getItem(int i) {
        return names.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        }
        else {
            LayoutInflater inflater = (LayoutInflater) parent.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.user_styles_list_item, parent,
                    false);
            ImageView btnDelete = (ImageView)view.findViewById(R.id.user_styles_list_btn_delete);
            btnDelete.setImageDrawable(Icons.TRASH.forListSmall());
            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String name = (String)view.getTag();
                    delete(name);
                }
            });
        }
        String name = names.get(i);
        TextView nameView = (TextView)view.findViewById(R.id.user_styles_list_name);
        nameView.setText(name);

        ImageView btnDelete = (ImageView)view.findViewById(R.id.user_styles_list_btn_delete);
        btnDelete.setTag(name);

        Log.d("AAA", name);
        return view;
    }

    private void delete(String name) {
        SharedPreferences.Editor edit = this.prefs.edit();
        edit.remove(name);
        edit.commit();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        this.data = sharedPreferences.getAll();
        names = new ArrayList<String>(this.data.keySet());
        Collections.sort(names);
        notifyDataSetChanged();
    }
}
