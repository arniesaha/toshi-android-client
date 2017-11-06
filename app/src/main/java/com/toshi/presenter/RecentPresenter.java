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

package com.toshi.presenter;

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.View;

import com.toshi.R;
import com.toshi.model.local.Conversation;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.ChatActivity;
import com.toshi.view.activity.NewConversationActivity;
import com.toshi.view.adapter.RecentAdapter;
import com.toshi.view.custom.HorizontalLineDivider;
import com.toshi.view.fragment.toplevel.RecentFragment;

import java.util.List;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;

public final class RecentPresenter implements Presenter<RecentFragment>{

    private RecentFragment fragment;
    private boolean firstTimeAttaching = true;
    private RecentAdapter adapter;
    private CompositeSubscription subscriptions;

    @Override
    public void onViewAttached(final RecentFragment fragment) {
        this.fragment = fragment;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }
        initShortLivingObjects();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initShortLivingObjects() {
        initClickListeners();
        initRecentsAdapter();
        populateRecentsAdapter();
        attachSubscriber();
    }

    private void initClickListeners() {
        this.fragment.getBinding().startChat.setOnClickListener(__ -> goToUserSearchActivity());
        this.fragment.getBinding().add.setOnClickListener(__ -> goToUserSearchActivity());
    }

    private void initRecentsAdapter() {
        final RecyclerView recyclerView = this.fragment.getBinding().recents;
        final RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this.fragment.getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        addSwipeToDeleteListener(recyclerView);

        this.adapter = new RecentAdapter()
                .setOnItemClickListener(this::handleConversationClicked)
                .setOnConversationAcceptedListener(this::handleAcceptedConversation)
                .setOnConversationRejectListed(this::handleRejectedConversation);

        recyclerView.setAdapter(this.adapter);

        final int dividerLeftPadding = fragment.getResources().getDimensionPixelSize(R.dimen.avatar_size_small)
                + fragment.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin)
                + fragment.getResources().getDimensionPixelSize(R.dimen.list_item_avatar_margin);
        final int dividerRightPadding = fragment.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        final HorizontalLineDivider lineDivider =
                new HorizontalLineDivider(ContextCompat.getColor(this.fragment.getContext(), R.color.divider))
                        .setRightPadding(dividerRightPadding)
                        .setLeftPadding(dividerLeftPadding);
        recyclerView.addItemDecoration(lineDivider);
    }

    private void handleConversationClicked(final Conversation conversation) {
        if (this.fragment == null) return;
        final Intent intent = new Intent(this.fragment.getActivity(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA__THREAD_ID, conversation.getThreadId());
        this.fragment.startActivity(intent);
    }

    private void handleAcceptedConversation(final Conversation conversation) {
        if (this.fragment == null) return;

        final Subscription sub =
                BaseApplication
                .get()
                .getSofaMessageManager()
                .acceptConversation(conversation)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> this.adapter.acceptConversation(conversation),
                        throwable -> LogUtil.e(getClass(), "Error while saving contact " + throwable)
                );

        this.subscriptions.add(sub);
    }

    private void handleRejectedConversation(final Conversation conversation) {
        if (this.fragment == null) return;

        final Subscription sub =
                BaseApplication
                .get()
                .getSofaMessageManager()
                .rejectConversation(conversation)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> handleRejection(conversation),
                        throwable -> LogUtil.e(getClass(), "Error while saving blocked user " + throwable)
                );

        this.subscriptions.add(sub);
    }

    private void handleRejection(final Conversation conversation) {
        this.adapter.rejectConversation(conversation);
        updateEmptyState();
    }

    private void addSwipeToDeleteListener(final RecyclerView recyclerView) {
        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                @Override
                public boolean onMove(final RecyclerView recyclerView1, final RecyclerView.ViewHolder viewHolder, final RecyclerView.ViewHolder target) {
                    return false;
                }

                @Override
                public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int swipeDir) {
                    adapter.removeItemAtWithUndo(viewHolder.getAdapterPosition(), recyclerView);
                }
            });
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    private void populateRecentsAdapter() {
        final Subscription sub =
                BaseApplication
                .get()
                .getSofaMessageManager()
                .loadAllConversations()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleConversations,
                        throwable -> LogUtil.exception(getClass(), "Error fetching conversations", throwable)
                );

        this.subscriptions.add(sub);
    }

    private void handleConversations(final List<Conversation> conversations) {
        this.adapter.setConversations(conversations);
        updateEmptyState();
    }

    private void attachSubscriber() {
        final Subscription sub =
                BaseApplication
                .get()
                .getSofaMessageManager()
                .registerForAllConversationChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleConversation,
                        throwable -> LogUtil.exception(getClass(), "Error during fetching conversation", throwable)
                );

        this.subscriptions.add(sub);
    }

    private void handleConversation(final Conversation updatedConversation) {
        this.adapter.updateConversation(updatedConversation);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (this.fragment == null) return;
        // Hide empty state if we have some content
        final boolean shouldShowEmptyState = this.adapter.getItemCount() == 0;

        if (shouldShowEmptyState) {
            this.fragment.getBinding().emptyState.setVisibility(View.VISIBLE);
            this.fragment.getBinding().recents.setVisibility(View.GONE);
        } else {
            this.fragment.getBinding().recents.setVisibility(View.VISIBLE);
            this.fragment.getBinding().emptyState.setVisibility(View.GONE);
        }
    }

    private void goToUserSearchActivity() {
        if (this.fragment == null || this.fragment.getContext() == null) return;
        final Intent intent = new Intent(this.fragment.getContext(), NewConversationActivity.class);
        this.fragment.startActivity(intent);
    }

    @Override
    public void onViewDetached() {
        this.adapter.doDelete();
        this.subscriptions.clear();
        this.fragment = null;
    }

    @Override
    public void onDestroyed() {
        this.subscriptions = null;
        this.adapter = null;
    }
}
