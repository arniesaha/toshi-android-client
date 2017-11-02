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

package com.toshi.view.adapter;


import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.toshi.R;
import com.toshi.model.local.Conversation;
import com.toshi.model.local.User;
import com.toshi.model.sofa.Message;
import com.toshi.model.sofa.Payment;
import com.toshi.model.sofa.PaymentRequest;
import com.toshi.model.sofa.SofaAdapters;
import com.toshi.model.sofa.SofaMessage;
import com.toshi.model.sofa.SofaType;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.adapter.listeners.OnItemClickListener;
import com.toshi.view.adapter.viewholder.ThreadViewHolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RecentAdapter extends RecyclerView.Adapter<ThreadViewHolder> {

    private final ArrayList<Conversation> conversationsToDelete;
    private List<Conversation> conversations;

    private OnItemClickListener<Conversation> onItemClickListener;
    private OnItemClickListener<Conversation> onConversationAccepted;
    private OnItemClickListener<Conversation> onConversationRejected;

    public RecentAdapter() {
        this.conversations = new ArrayList<>(0);
        this.conversationsToDelete = new ArrayList<>();
    }

    public void setConversations(final List<Conversation> conversations) {
        this.conversations = conversations;
        notifyDataSetChanged();
    }

    public RecentAdapter setOnItemClickListener(final OnItemClickListener<Conversation> onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
        return this;
    }

    public RecentAdapter setOnConversationAcceptedListener(final OnItemClickListener<Conversation> onItemClickListener) {
        this.onConversationAccepted = onItemClickListener;
        return this;
    }

    public RecentAdapter setOnConversationRejectListed(final OnItemClickListener<Conversation> onItemClickListener) {
        this.onConversationRejected = onItemClickListener;
        return this;
    }

    @Override
    public ThreadViewHolder onCreateViewHolder(final ViewGroup parent, int viewType) {
        final View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__recent, parent, false);
        return new ThreadViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final ThreadViewHolder holder, final int position) {
        final Conversation conversation = this.conversations.get(position);
        holder.setThread(conversation);

        final String formattedLatestMessage = formatLastMessage(conversation.getLatestMessage());
        holder
                .isApproved(conversation.isAccepted())
                .setLatestMessage(formattedLatestMessage)
                .setOnItemClickListener(conversation, this.onItemClickListener)
                .setOnConversationAcceptedListener(conversation, this.onConversationAccepted)
                .setOnConversationRejectedListener(conversation, this.onConversationRejected);
    }

    @Override
    public int getItemCount() {
        return this.conversations.size();
    }

    public void updateConversation(final Conversation conversation) {
        final int position = this.conversations.indexOf(conversation);
        if (position == -1) {
            this.conversations.add(0, conversation);
            notifyItemInserted(0);
            return;
        }

        this.conversations.set(position, conversation);
        notifyItemChanged(position);
    }

    public void acceptConversation(final Conversation conversation) {
        final int position = this.conversations.indexOf(conversation);
        if (position == -1) return;
        notifyItemChanged(position);
    }

    public void rejectConversation(final Conversation conversation) {
        final int position = this.conversations.indexOf(conversation);
        this.conversations.remove(conversation);
        notifyItemRemoved(position);
    }

    public void removeItemAtWithUndo(final int position, final RecyclerView parentView) {
        final Conversation removedConversation = this.conversations.get(position);
        final Snackbar snackbar = generateSnackbar(parentView);
        snackbar.setAction(
                R.string.undo,
                handleUndoRemove(position, parentView, removedConversation)
        ).show();
        this.conversations.remove(position);
        notifyItemRemoved(position);
        conversationsToDelete.add(removedConversation);
    }

    @NonNull
    private Snackbar generateSnackbar(final RecyclerView parentView) {
        final Snackbar snackbar = Snackbar
                .make(parentView, R.string.conversation_deleted, Snackbar.LENGTH_LONG)
                .setActionTextColor(ContextCompat.getColor(BaseApplication.get(), R.color.colorAccent));

        final View snackbarView = snackbar.getView();
        final TextView snackbarTextView = (TextView)snackbarView.findViewById(android.support.design.R.id.snackbar_text);
        snackbarTextView.setTextColor(ContextCompat.getColor(BaseApplication.get(), R.color.textColorContrast));
        return snackbar;
    }

    @NonNull
    private View.OnClickListener handleUndoRemove(final int position, final RecyclerView parentView, final Conversation removedConversation) {
        return view -> {
            this.conversations.add(position, removedConversation);
            notifyItemInserted(position);
            parentView.scrollToPosition(position);
            conversationsToDelete.remove(removedConversation);
        };
    }

    private String formatLastMessage(final SofaMessage sofaMessage) {
        if (sofaMessage == null) {
            // Todo - this is only null because of group creation not containing a message
            // should it?
            return "";
        }

        final User localUser = getCurrentLocalUser();
        final boolean sentByLocal = sofaMessage.isSentBy(localUser);

        try {
            switch (sofaMessage.getType()) {
                case SofaType.PLAIN_TEXT: {
                    final Message message = SofaAdapters.get().messageFrom(sofaMessage.getPayload());
                    return message.toUserVisibleString(sentByLocal, sofaMessage.hasAttachment());
                }
                case SofaType.PAYMENT: {
                    final Payment payment = SofaAdapters.get().paymentFrom(sofaMessage.getPayload());
                    return payment.toUserVisibleString(sentByLocal, sofaMessage.getSendState());
                }
                case SofaType.PAYMENT_REQUEST: {
                    final PaymentRequest request = SofaAdapters.get().txRequestFrom(sofaMessage.getPayload());
                    return request.toUserVisibleString(sentByLocal, sofaMessage.getSendState());
                }
                case SofaType.COMMAND_REQUEST:
                case SofaType.INIT_REQUEST:
                case SofaType.INIT:
                case SofaType.UNKNOWN:
                    return "";
            }
        } catch (final IOException ex) {
            LogUtil.error(getClass(), "Error parsing SofaMessage. " + ex);
        }

        return "";
    }

    private User getCurrentLocalUser() {
        // Yes, this blocks. But realistically, a value should be always ready for returning.
        return BaseApplication
                .get()
                .getUserManager()
                .getCurrentUser()
                .toBlocking()
                .value();
    }

    public void doDelete() {
        for (final Conversation conversationToDelete : conversationsToDelete) {
            BaseApplication
                    .get()
                    .getSofaMessageManager()
                    .deleteConversation(conversationToDelete)
                    .subscribe(
                            () -> {},
                            t -> LogUtil.e(getClass(), "Unable to delete conversation")
                    );
        }
    }
}
