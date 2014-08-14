/*
 * Copyright 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package itkach.aard2;

import android.app.ActionBar;
import android.app.SearchManager;
import android.content.Intent;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.PagerTitleStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Toast;

import java.util.Iterator;
import java.util.List;

import itkach.slob.Slob;
import itkach.slob.Slob.Blob;

public class ArticleCollectionActivity extends FragmentActivity {

    ArticleCollectionPagerAdapter articleCollectionPagerAdapter;
    ViewPager viewPager;

    ToBlob blobToBlob = new ToBlob(){

        @Override
        public Blob convert(Object item) {
            return (Blob)item;
        }

    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.activity_article_collection);
        final Application app = (Application)getApplication();
        app.push(this);
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        Intent intent = getIntent();

        Uri articleUrl = intent.getData();
        if (articleUrl != null) {
            List<String> pathSegments = articleUrl.getPathSegments();
            String lookupKey = pathSegments.get(1);
            String refererSlobId = intent.getStringExtra("refererSlobId");
            if (refererSlobId == null) {
                String referer = intent.getStringExtra("referer");
                Uri refererUrl = Uri.parse(referer);
                refererSlobId = refererUrl.getQueryParameter("slob");
            }
            Iterator<Slob.Blob> result;
            if (intent.getBooleanExtra("findExact", false)) {
                result = app.findExact(lookupKey, refererSlobId);
            }
            else {
                result = app.find(lookupKey, refererSlobId);
            }
            if (!result.hasNext()) {
                Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show();
                this.finish();
                return;
            }
            BlobListAdapter data = new BlobListAdapter(this);
            data.setData(result);
            articleCollectionPagerAdapter = new ArticleCollectionPagerAdapter(
                    app, data, blobToBlob, getSupportFragmentManager());
        }
        else {
            String action = intent.getAction();
            if (action != null && action.equals("showBookmarks")) {
                articleCollectionPagerAdapter = new ArticleCollectionPagerAdapter(
                        app, new BlobDescriptorListAdapter(app.bookmarks), new ToBlob() {
                            @Override
                            public Blob convert(Object item) {
                                return app.bookmarks.resolve((BlobDescriptor)item);
                            }
                        }, getSupportFragmentManager());

            }
            else if (action != null && action.equals("showHistory")) {
                articleCollectionPagerAdapter = new ArticleCollectionPagerAdapter(
                        app, new BlobDescriptorListAdapter(app.history), new ToBlob() {
                            @Override
                            public Blob convert(Object item) {
                                return app.history.resolve((BlobDescriptor)item);
                            }
                        }, getSupportFragmentManager());

            }
            else if (action != null && (action.equals(Intent.ACTION_SEND) || action.equals(Intent.ACTION_SEARCH)) ) {
                String lookupKey = "";
                if (action.equals(Intent.ACTION_SEND)) {
                    lookupKey = intent.getStringExtra(Intent.EXTRA_TEXT);
                }
                else {
                    lookupKey = intent.getStringExtra(SearchManager.QUERY);
                }
                Iterator<Slob.Blob> result = null;
                if (!lookupKey.equals("")) {
                    result = app.find(lookupKey, null);
                }
                if (result == null || !result.hasNext()) {
                    Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show();
                    Intent lookupIntent = new Intent(this, MainActivity.class);
                    lookupIntent.putExtra(SearchManager.QUERY, lookupKey);
                    startActivity(lookupIntent);
                    this.finish();
                    return;
                }
                BlobListAdapter data = new BlobListAdapter(this);
                data.setData(result);
                articleCollectionPagerAdapter = new ArticleCollectionPagerAdapter(
                        app, data, blobToBlob, getSupportFragmentManager());
            }
            else {
                articleCollectionPagerAdapter = new ArticleCollectionPagerAdapter(
                        app, app.lastResult, blobToBlob, getSupportFragmentManager());
            }
        }
        findViewById(R.id.pager_title_strip).setVisibility(
                articleCollectionPagerAdapter.getCount() == 1 ? ViewGroup.GONE : ViewGroup.VISIBLE);

        int position = intent.getIntExtra("position", 0);
        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(articleCollectionPagerAdapter);
        viewPager.setOnPageChangeListener(new OnPageChangeListener(){

            @Override
            public void onPageScrollStateChanged(int arg0) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onPageSelected(int position) {
                updateTitle(position);
            }});
        viewPager.setCurrentItem(position);

        PagerTitleStrip titleStrip = (PagerTitleStrip)findViewById(R.id.pager_title_strip);
        titleStrip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        updateTitle(position);
        articleCollectionPagerAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                if (articleCollectionPagerAdapter.getCount() == 0) {
                    finish();
                }
            }
        });
    }

    private void updateTitle(int position) {
        Log.d("updateTitle", ""+position + " count: " + articleCollectionPagerAdapter.getCount());
        Slob.Blob blob = articleCollectionPagerAdapter.get(position);
        CharSequence pageTitle = articleCollectionPagerAdapter.getPageTitle(position);
        Log.d("updateTitle", ""+blob);
        ActionBar actionBar = getActionBar();
        if (blob != null) {
            String dictLabel = blob.owner.getTags().get("label");
            actionBar.setTitle(dictLabel);
            Application app = (Application)getApplication();
            app.history.add(app.getUrl(blob));
        }
        else {
            actionBar.setTitle("???");
        }
        actionBar.setSubtitle(pageTitle);
    }

    @Override
    protected void onDestroy() {
        if (viewPager != null) {
            viewPager.setAdapter(null);
        }
        if (articleCollectionPagerAdapter != null) {
            articleCollectionPagerAdapter.destroy();
        }
        Application app = (Application)getApplication();
        app.pop(this);
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            // This is called when the Home (Up) button is pressed in the action
            // bar.
            // Create a simple intent that starts the hierarchical parent
            // activity and
            // use NavUtils in the Support Package to ensure proper handling of
            // Up.
            Intent upIntent = new Intent(this, MainActivity.class);
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                // This activity is not part of the application's task, so
                // create a new task
                // with a synthesized back stack.
                TaskStackBuilder.from(this)
                // If there are ancestor activities, they should be added here.
                        .addNextIntent(upIntent).startActivities();
                finish();
            } else {
                // This activity is part of the application's task, so simply
                // navigate up to the hierarchical parent activity.
                NavUtils.navigateUpTo(this, upIntent);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    static interface ToBlob {
        Slob.Blob convert(Object item);
    }

    public static class ArticleCollectionPagerAdapter extends FragmentStatePagerAdapter {

        private Application app;
        private DataSetObserver observer;
        private BaseAdapter data;
        private ToBlob toBlob;

        public ArticleCollectionPagerAdapter(Application app, BaseAdapter data, ToBlob toBlob, FragmentManager fm) {
            super(fm);
            this.app = app;
            this.data = data;
            this.observer = new DataSetObserver(){
                @Override
                public void onChanged() {
                    notifyDataSetChanged();
                }
            };
            data.registerDataSetObserver(observer);
            this.toBlob = toBlob;
        }

        void destroy() {
            data.unregisterDataSetObserver(observer);
            data = null;
            app = null;
        }

        @Override
        public Fragment getItem(int i) {
            Fragment fragment = new ArticleFragment();
            Slob.Blob blob = get(i);
            if (blob != null) {
                String articleUrl = app.getUrl(blob);
                Bundle args = new Bundle();
                Log.i("Setting article fragment url",
                        String.format("%s (key %s slob %s)",
                                articleUrl, blob.key, blob.owner.getTags().get("label")));
                args.putString(ArticleFragment.ARG_URL, articleUrl);
                fragment.setArguments(args);
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return data.getCount();
        }

        Slob.Blob get(int position) {
            return toBlob.convert(data.getItem(position));
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Object item = data.getItem(position);
            if (item instanceof BlobDescriptor) {
                return ((BlobDescriptor) item).key;
            }
            if (item instanceof Slob.Blob) {
                return ((Blob)item).key;
            }
            return "???";
        }

        //this is needed so that fragment is properly updated
        //if underlying data changes (such as on unbookmark)
        //https://code.google.com/p/android/issues/detail?id=19001
        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }
    }
}
