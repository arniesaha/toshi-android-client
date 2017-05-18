/*
 * 	Copyright (c) 2017. Token Browser, Inc
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

package com.tokenbrowser.view.activity;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.tokenbrowser.R;
import com.tokenbrowser.databinding.ActivityLandingBinding;
import com.tokenbrowser.presenter.LandingPresenter;
import com.tokenbrowser.presenter.LoaderIds;
import com.tokenbrowser.presenter.factory.LandingPresenterFactory;
import com.tokenbrowser.presenter.factory.PresenterFactory;

public class LandingActivity extends BasePresenterActivity<LandingPresenter, LandingActivity> {

    private ActivityLandingBinding binding;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    private void init() {
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_landing);
    }

    public ActivityLandingBinding getBinding() {
        return this.binding;
    }

    @NonNull
    @Override
    protected PresenterFactory<LandingPresenter> getPresenterFactory() {
        return new LandingPresenterFactory();
    }

    @Override
    protected void onPresenterPrepared(@NonNull LandingPresenter presenter) {}

    @Override
    protected int loaderId() {
        return LoaderIds.get(this.getClass().getCanonicalName());
    }
}