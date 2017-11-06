/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.view.activity;


import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;

import com.toshi.R;
import com.toshi.databinding.ActivityViewUserBinding;
import com.toshi.model.local.ActivityResultHolder;
import com.toshi.presenter.LoaderIds;
import com.toshi.presenter.ViewUserPresenter;
import com.toshi.presenter.factory.PresenterFactory;
import com.toshi.presenter.factory.ViewUserPresenterFactory;

public class ViewUserActivity extends BasePresenterActivity<ViewUserPresenter, ViewUserActivity> {
    public static final String EXTRA__USER_ADDRESS = "extra_user_address";
    public static final String EXTRA__USER_NAME = "extra_user_name";
    public static final String EXTRA__PLAY_SCAN_SOUNDS = "play_scan_sounds";

    private ActivityViewUserBinding binding;
    private ViewUserPresenter presenter;
    private ActivityResultHolder resultHolder;
    private Menu menu;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    private void init() {
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_view_user);
    }

    @Override
    public void onResume() {
        super.onResume();
        tryProcessResultHolder();
    }

    public ActivityViewUserBinding getBinding() {
        return this.binding;
    }

    @NonNull
    @Override
    protected PresenterFactory<ViewUserPresenter> getPresenterFactory() {
        return new ViewUserPresenterFactory();
    }

    @Override
    protected void onPresenterPrepared(@NonNull final ViewUserPresenter presenter) {
        this.presenter = presenter;
        tryProcessResultHolder();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        this.resultHolder = new ActivityResultHolder(requestCode, resultCode, data);
        tryProcessResultHolder();
    }

    private void tryProcessResultHolder() {
        if (this.presenter == null || this.resultHolder == null) return;
        if (this.presenter.handleActivityResult(this.resultHolder)) {
            this.resultHolder = null;
        }
    }

    @Override
    protected int loaderId() {
        return LoaderIds.get(this.getClass().getCanonicalName());
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.profile, menu);
        this.menu = menu;
        if (this.presenter != null) this.presenter.onCreateOptionsMenu();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        this.presenter.handleActionMenuClicked(item);
        return super.onOptionsItemSelected(item);
    }

    public Menu getMenu() {
        return this.menu;
    }
}
