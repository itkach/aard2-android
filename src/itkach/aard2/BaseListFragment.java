package itkach.aard2;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public abstract class BaseListFragment extends ListFragment {

    protected View emptyView;

    abstract Icons getEmptyIcon();

    abstract CharSequence getEmptyText();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        emptyView = inflater.inflate(R.layout.empty_view, container, false);
        TextView emptyText = ((TextView)emptyView.findViewById(R.id.empty_text));
        emptyText.setMovementMethod(LinkMovementMethod.getInstance());
        emptyText.setText(getEmptyText());
        ImageView emptyIcon = (ImageView)(emptyView.findViewById(R.id.empty_icon));
        emptyIcon.setImageDrawable(getEmptyIcon().forEmptyView());
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final ListView listView = getListView();
        listView.setEmptyView(emptyView);
        ((ViewGroup) listView.getParent()).addView(emptyView, 0);
    }

}
