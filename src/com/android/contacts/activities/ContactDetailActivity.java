/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.contacts.activities;

import com.android.contacts.ContactLoader;
import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.ContactsSearchManager;
import com.android.contacts.R;
import com.android.contacts.detail.ContactDetailAboutFragment;
import com.android.contacts.detail.ContactDetailDisplayUtils;
import com.android.contacts.detail.ContactDetailFragment;
import com.android.contacts.detail.ContactDetailFragmentCarousel;
import com.android.contacts.detail.ContactDetailTabCarousel;
import com.android.contacts.detail.ContactDetailUpdatesFragment;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.util.PhoneCapabilityTester;

import android.accounts.Account;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.CheckBox;
import android.widget.Toast;

import java.util.ArrayList;

public class ContactDetailActivity extends ContactsActivity {
    private static final String TAG = "ContactDetailActivity";

    public static final int FRAGMENT_COUNT = 2;

    private ContactLoader.Result mContactData;
    private Uri mLookupUri;

    private ContactDetailAboutFragment mAboutFragment;
    private ContactDetailUpdatesFragment mUpdatesFragment;

    private ContactDetailTabCarousel mTabCarousel;
    private ViewPager mViewPager;

    private ContactDetailFragmentCarousel mFragmentCarousel;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (PhoneCapabilityTester.isUsingTwoPanes(this)) {
            // This activity must not be shown. We have to select the contact in the
            // PeopleActivity instead ==> Create a forward intent and finish
            final Intent originalIntent = getIntent();
            Intent intent = new Intent();
            intent.setAction(originalIntent.getAction());
            intent.setDataAndType(originalIntent.getData(), originalIntent.getType());
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_FORWARD_RESULT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            intent.setClass(this, PeopleActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.contact_detail_activity);

        // Narrow width screens have a {@link ViewPager} and {@link ContactDetailTabCarousel}
        mViewPager = (ViewPager) findViewById(R.id.pager);
        if (mViewPager != null) {
            mViewPager.setAdapter(new ViewPagerAdapter(getFragmentManager()));
            mViewPager.setOnPageChangeListener(mOnPageChangeListener);
        }

        mTabCarousel = (ContactDetailTabCarousel) findViewById(R.id.tab_carousel);
        if (mTabCarousel != null) {
            mTabCarousel.setListener(mTabCarouselListener);
        }

        // Otherwise, wide width screens have a {@link ContactDetailFragmentCarousel}
        mFragmentCarousel = (ContactDetailFragmentCarousel) findViewById(R.id.fragment_carousel);
        if (mFragmentCarousel != null) {
            if (mAboutFragment != null) mFragmentCarousel.setAboutFragment(mAboutFragment);
            if (mUpdatesFragment != null) mFragmentCarousel.setUpdatesFragment(mUpdatesFragment);
        }

        Log.i(TAG, getIntent().getData().toString());
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof ContactDetailAboutFragment) {
            mAboutFragment = (ContactDetailAboutFragment) fragment;
            mAboutFragment.setListener(mFragmentListener);
            mAboutFragment.setVerticalScrollListener(mVerticalScrollListener);
            mAboutFragment.loadUri(getIntent().getData());
        } else if (fragment instanceof ContactDetailUpdatesFragment) {
            mUpdatesFragment = (ContactDetailUpdatesFragment) fragment;
        }
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.star, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem starredMenuItem = menu.findItem(R.id.menu_star);
        ViewGroup starredContainer = (ViewGroup) getLayoutInflater().inflate(
                R.layout.favorites_star, null, false);
        final CheckBox starredView = (CheckBox) starredContainer.findViewById(R.id.star);
        starredView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Toggle "starred" state
                // Make sure there is a contact
                if (mLookupUri != null) {
                    Intent intent = ContactSaveService.createSetStarredIntent(
                            ContactDetailActivity.this, mLookupUri, starredView.isChecked());
                    ContactDetailActivity.this.startService(intent);
                }
            }
        });
        // If there is contact data, update the starred state
        if (mContactData != null) {
            ContactDetailDisplayUtils.setStarred(mContactData, starredView);
        }
        starredMenuItem.setActionView(starredContainer);
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        FragmentKeyListener mCurrentFragment;
        switch (getCurrentPage()) {
            case 0:
                mCurrentFragment = (FragmentKeyListener) mAboutFragment;
                break;
            case 1:
                mCurrentFragment = (FragmentKeyListener) mUpdatesFragment;
                break;
            default:
                throw new IllegalStateException("Invalid current item for ViewPager");
        }
        if (mCurrentFragment.handleKeyDown(keyCode)) return true;

        return super.onKeyDown(keyCode, event);
    }

    private int getCurrentPage() {
        if (mViewPager != null) {
            return mViewPager.getCurrentItem();
        } else if (mFragmentCarousel != null) {
            return mFragmentCarousel.getCurrentPage();
        }
        throw new IllegalStateException("Can't figure out the currently selected page. The activity"
                + "must either have the ViewPager or fragment carousel");
    }

    private final ContactDetailFragment.Listener mFragmentListener =
            new ContactDetailFragment.Listener() {
        @Override
        public void onContactNotFound() {
            finish();
        }

        @Override
        public void onDetailsLoaded(ContactLoader.Result result) {
            if (result == null) {
                return;
            }
            mContactData = result;
            mLookupUri = result.getLookupUri();
            invalidateOptionsMenu();
            if (mTabCarousel != null) {
                mTabCarousel.loadData(result);
            }
        }

        @Override
        public void onEditRequested(Uri contactLookupUri) {
            startActivity(new Intent(Intent.ACTION_EDIT, contactLookupUri));
        }

        @Override
        public void onItemClicked(Intent intent) {
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found for intent: " + intent);
            }
        }

        @Override
        public void onDeleteRequested(Uri contactUri) {
            ContactDeletionInteraction.start(ContactDetailActivity.this, contactUri, true);
        }

        @Override
        public void onCreateRawContactRequested(
                ArrayList<ContentValues> values, Account account) {
            Toast.makeText(ContactDetailActivity.this, R.string.toast_making_personal_copy,
                    Toast.LENGTH_LONG).show();
            Intent serviceIntent = ContactSaveService.createNewRawContactIntent(
                    ContactDetailActivity.this, values, account,
                    ContactDetailActivity.class, Intent.ACTION_VIEW);
            startService(serviceIntent);

        }
    };

    public class ViewPagerAdapter extends FragmentPagerAdapter{

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new ContactDetailAboutFragment();
                case 1:
                    return new ContactDetailUpdatesFragment();
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public int getCount() {
            return FRAGMENT_COUNT;
        }
    }

    private OnPageChangeListener mOnPageChangeListener = new OnPageChangeListener() {

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            // The user is horizontally dragging the {@link ViewPager}, so send
            // these scroll changes to the tab carousel. Ignore these events though if the carousel
            // is actually controlling the {@link ViewPager} scrolls because it will already be
            // in the correct position.
            if (mViewPager.isFakeDragging()) {
                return;
            }
            int x = (int) ((position + positionOffset) *
                    mTabCarousel.getAllowedHorizontalScrollLength());
            mTabCarousel.scrollTo(x, 0);
        }

        @Override
        public void onPageSelected(int position) {
            // Since a new page has been selected by the {@link ViewPager},
            // update the tab selection in the carousel.
            mTabCarousel.setCurrentTab(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {}

    };

    private ContactDetailTabCarousel.Listener mTabCarouselListener =
            new ContactDetailTabCarousel.Listener() {

        @Override
        public void onTouchDown() {
            // The user just started scrolling the carousel, so begin "fake dragging" the
            // {@link ViewPager} if it's not already doing so.
            if (mViewPager.isFakeDragging()) {
                return;
            }
            mViewPager.beginFakeDrag();
        }

        @Override
        public void onTouchUp() {
            // The user just stopped scrolling the carousel, so stop "fake dragging" the
            // {@link ViewPager} if was doing so before.
            if (mViewPager.isFakeDragging()) {
                mViewPager.endFakeDrag();
            }
        }

        @Override
        public void onScrollChanged(int l, int t, int oldl, int oldt) {
            // The user is scrolling the carousel, so send the scroll deltas to the
            // {@link ViewPager} so it can move in sync.
            if (mViewPager.isFakeDragging()) {
                mViewPager.fakeDragBy(oldl-l);
            }
        }

        @Override
        public void onTabSelected(int position) {
            // The user selected a tab, so update the {@link ViewPager}
            mViewPager.setCurrentItem(position);
        }
    };

    private OnScrollListener mVerticalScrollListener = new OnScrollListener() {

        @Override
        public void onScroll(
                AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (mTabCarousel == null) {
                return;
            }
            // Only re-position the tab carousel vertically if the FIRST item is still visible on
            // the screen, otherwise the carousel should be in the correct place (pinned at the
            // top).
            if (firstVisibleItem != 0) {
                return;
            }
            View topView = view.getChildAt(firstVisibleItem);
            if (topView == null) {
                return;
            }
            int amtToScroll = Math.max((int) view.getChildAt(firstVisibleItem).getY(),
                    -mTabCarousel.getAllowedVerticalScrollLength());
            mTabCarousel.setY(amtToScroll);
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {}

    };

    /**
     * This interface should be implemented by {@link Fragment}s within this
     * activity so that the activity can determine whether the currently
     * displayed view is handling the key event or not.
     */
    public interface FragmentKeyListener {
        /**
         * Returns true if the key down event will be handled by the implementing class, or false
         * otherwise.
         */
        public boolean handleKeyDown(int keyCode);
    }
}