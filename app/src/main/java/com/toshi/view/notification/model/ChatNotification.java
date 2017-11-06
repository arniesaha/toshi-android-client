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

package com.toshi.view.notification.model;


import android.app.PendingIntent;
import android.content.Intent;
import android.support.v4.app.TaskStackBuilder;

import com.bumptech.glide.Glide;
import com.toshi.R;
import com.toshi.model.local.Recipient;
import com.toshi.presenter.chat.DirectReplyService;
import com.toshi.service.NotificationDismissedReceiver;
import com.toshi.service.RejectPaymentRequestService;
import com.toshi.util.PaymentType;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.ChatActivity;
import com.toshi.view.activity.MainActivity;
import com.toshi.view.activity.SplashActivity;
import com.toshi.view.custom.CropCircleTransformation;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import rx.Completable;

public class ChatNotification extends ToshiNotification {

    private static final String NO_ACTION = "noAction";
    private static final String PAYMENT_REQUEST_ACTION = "paymentRequestAction";

    private final Recipient sender;

    public ChatNotification(final Recipient sender) {
        super(generateID(sender));
        this.sender = sender;
    }

    private static String generateID(final Recipient sender) {
        return sender == null
                ? UUID.randomUUID().toString()
                : sender.getThreadId();
    }

    @Override
    public String getTag() {
        return this.sender == null ? DEFAULT_TAG : sender.getThreadId();
    }

    @Override
    public String getTitle() {
        return this.sender == null
                ? BaseApplication.get().getString(R.string.unknown_sender)
                : this.sender.getDisplayName();
    }

    public PendingIntent getPendingIntent() {
        final Intent mainIntent = getMainIntent();

        if (this.sender == null || this.sender.getThreadId() == null) {
            return getFallbackPendingIntent(mainIntent);
        }

        final Intent chatIntent = getChatIntent();
        return getPendingIntent(mainIntent, chatIntent);
    }

    public PendingIntent getAcceptPaymentRequestIntent(final String messageId) {
        final Intent mainIntent = getMainIntent();

        if (this.sender == null || this.sender.getThreadId() == null) {
            return getFallbackPendingIntent(mainIntent);
        }

        final Intent chatIntent = getChatIntentWithPaymentRequestAction(messageId);
        return getPendingIntent(mainIntent, chatIntent);
    }

    private Intent getMainIntent() {
        return new Intent(BaseApplication.get(), MainActivity.class)
                .putExtra(MainActivity.EXTRA__ACTIVE_TAB, 1)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    public PendingIntent getFallbackPendingIntent() {
        return PendingIntent.getActivity(
                BaseApplication.get(),
                UUID.randomUUID().hashCode(),
                getMainIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getFallbackPendingIntent(final Intent mainIntent) {
        return TaskStackBuilder.create(BaseApplication.get())
                .addParentStack(MainActivity.class)
                .addNextIntent(mainIntent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Intent getChatIntent() {
        return new Intent(BaseApplication.get(), ChatActivity.class)
                .putExtra(ChatActivity.EXTRA__THREAD_ID, this.sender.getThreadId())
                .setAction(NO_ACTION)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private Intent getChatIntentWithPaymentRequestAction(final String messageId) {
        return new Intent(BaseApplication.get(), ChatActivity.class)
                .putExtra(ChatActivity.EXTRA__THREAD_ID, this.sender.getThreadId())
                .putExtra(ChatActivity.EXTRA__PAYMENT_ACTION, PaymentType.TYPE_SEND)
                .putExtra(ChatActivity.EXTRA__MESSAGE_ID, messageId)
                .setAction(PAYMENT_REQUEST_ACTION)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private PendingIntent getPendingIntent(final Intent mainIntent, final Intent chatIntent) {
        final PendingIntent nextIntent = TaskStackBuilder.create(BaseApplication.get())
                .addParentStack(MainActivity.class)
                .addNextIntent(mainIntent)
                .addNextIntent(chatIntent)
                .getPendingIntent(getTitle().hashCode(), PendingIntent.FLAG_UPDATE_CURRENT);

        final Intent splashIntent =  new Intent(BaseApplication.get(), SplashActivity.class)
                .putExtra(SplashActivity.EXTRA__NEXT_INTENT, nextIntent);

        return PendingIntent.getActivity(
                BaseApplication.get(),
                UUID.randomUUID().hashCode(),
                splashIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public PendingIntent getDirectReplyIntent() {
        if (isUnknownSender()) return null;

        final Intent directReplyIntent = new Intent(BaseApplication.get(), DirectReplyService.class)
                .putExtra(DirectReplyService.TOSHI_ID, this.sender.getUser().getToshiId());

        return PendingIntent.getService(
                BaseApplication.get(),
                DirectReplyService.REQUEST_CODE,
                directReplyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    public PendingIntent getDeleteIntent() {
        final Intent intent =
                new Intent(BaseApplication.get(), NotificationDismissedReceiver.class)
                        .putExtra(NotificationDismissedReceiver.TAG, getTag());

        return PendingIntent.getBroadcast(
                BaseApplication.get(),
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public PendingIntent getRejectPaymentRequestIntent(final String messageId) {
        if (isUnknownSender()) return null;
        final Intent rejectIntent = new Intent(BaseApplication.get(), RejectPaymentRequestService.class)
                .putExtra(RejectPaymentRequestService.MESSAGE_ID, messageId);

        return PendingIntent.getService(
                BaseApplication.get(),
                RejectPaymentRequestService.REJECT_REQUEST_CODE,
                rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    public Completable generateLargeIcon() {
        if (this.largeIcon != null) return Completable.complete();
        if (getAvatarUri() == null) return Completable.fromAction(this::setDefaultLargeIcon);

        return Completable.fromAction(() -> {
            try {
                fetchUserAvatar();
            } catch (InterruptedException | ExecutionException e) {
                setDefaultLargeIcon();
            }
        });
    }

    private void fetchUserAvatar() throws InterruptedException, ExecutionException {
        this.largeIcon = Glide
                        .with(BaseApplication.get())
                        .load(getAvatarUri())
                        .asBitmap()
                        .transform(new CropCircleTransformation(BaseApplication.get()))
                        .into(200, 200)
                        .get();
    }

    private String getAvatarUri() {
        return this.sender == null
                ? null
                : this.sender.getAvatar();
    }

    public boolean isUnknownSender() {
        return this.sender == null;
    }
}
