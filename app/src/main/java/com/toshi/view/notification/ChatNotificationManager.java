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

package com.toshi.view.notification;


import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.RemoteInput;

import com.toshi.R;
import com.toshi.model.local.Recipient;
import com.toshi.model.sofa.Message;
import com.toshi.model.sofa.PaymentRequest;
import com.toshi.model.sofa.SofaAdapters;
import com.toshi.model.sofa.SofaMessage;
import com.toshi.model.sofa.SofaType;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.notification.model.ChatNotification;

import java.util.HashMap;
import java.util.Map;

import rx.Single;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class ChatNotificationManager extends ToshiNotificationBuilder {

    public static final String KEY_TEXT_REPLY = "key_text_reply";

    private static String currentlyOpenConversation;
    private static final Map<String, ChatNotification> activeNotifications = new HashMap<>();

    public static void suppressNotificationsForConversation(final String conversationId) {
        currentlyOpenConversation = conversationId;
        final NotificationManager manager = (NotificationManager) BaseApplication.get().getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(conversationId, 1);
        handleNotificationDismissed(conversationId);
    }

    public static void handleNotificationDismissed(final String notificationTag) {
        activeNotifications.remove(notificationTag);
    }

    public static void stopNotificationSuppression(final String conversationId) {
        // By passing conversationId we ensure that a second activity
        // doesn't accidentally get unsubscribed by the first activity
        // being destroyed
        if (conversationId.equals(currentlyOpenConversation)) {
            currentlyOpenConversation = null;
        }
    }

    public static void showNotification(final SofaMessage sofaMessage) {
        if (sofaMessage == null) return;
        final Recipient recipient = new Recipient(sofaMessage.getSender());
        showChatNotification(recipient, sofaMessage);
    }

    public static void showChatNotification(final Recipient sender, final String content) {
        final Message message = new Message().setBody(content);
        final String messageBody = SofaAdapters.get().toJson(message);
        final SofaMessage newSofaMessage = new SofaMessage().makeNew(sender.getUser(), messageBody);
        showChatNotification(sender, newSofaMessage);
    }

    public static void showChatNotification(final Recipient sender, final SofaMessage sofaMessage) {
        checkIfConversationIsAccepted(sofaMessage.getSender().getToshiId())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        isAccepted -> checkIfConversationIsAcceptedAndShowNotification(sender, sofaMessage, isAccepted),
                        throwable -> LogUtil.e("ChatNotificationManager", "Error while parsing sofa message " + throwable)
                );
    }

    private static Single<Boolean> checkIfConversationIsAccepted(final String toshiId) {
        return BaseApplication
                .get()
                .getSofaMessageManager()
                .loadConversation(toshiId)
                .map(conversation -> conversation !=  null && conversation.isAccepted());
    }

    private static void checkIfConversationIsAcceptedAndShowNotification(final Recipient sender,
                                                                         final SofaMessage sofaMessage,
                                                                         final boolean isConversationAccepted) {
        final ChatNotification activeChatNotification = getAndCacheChatNotification(sender);
        if (activeChatNotification == null) return;

        final SofaMessage message = isConversationAccepted
                ? sofaMessage
                : getConversationRequestMessage();

        if (message.getType() == SofaType.PLAIN_TEXT) {
            activeChatNotification.addUnreadMessage(message);
            generateIconAndShowNotification(activeChatNotification, null, isConversationAccepted);
        } else if (message.getType() == SofaType.PAYMENT_REQUEST) {
            final PaymentRequest paymentRequest = getPaymentRequestFromMessage(sofaMessage);
            if (paymentRequest == null) return;
            getLocalPriceAndShowPaymentRequestNotification(sender, paymentRequest, message, isConversationAccepted);
        }
    }

    private static SofaMessage getConversationRequestMessage() {
        final String content = BaseApplication.get().getString(R.string.new_message);
        final Message message = new Message().setBody(content);
        final String messageBody = SofaAdapters.get().toJson(message);
        return new SofaMessage().makeNew(messageBody);
    }

    private static PaymentRequest getPaymentRequestFromMessage(final SofaMessage sofaMessage) {
        try {
            return SofaAdapters.get().txRequestFrom(sofaMessage.getPayload());
        } catch (Exception e) {
            LogUtil.e("ChatNotificationManager", "Error while parsing sofa message " + e);
        }
        return null;
    }

    private static void getLocalPriceAndShowPaymentRequestNotification(final Recipient sender,
                                                                       final PaymentRequest paymentRequest,
                                                                       final SofaMessage sofaMessage,
                                                                       final boolean isConversationAccepted) {
        paymentRequest
                .generateLocalPrice()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map(pr -> addLocalPriceToSofaMessage(pr, sofaMessage))
                .subscribe(
                        sofaMessageWithLocalPrice -> showPaymentRequestNotification(sender, sofaMessageWithLocalPrice, isConversationAccepted),
                        throwable -> LogUtil.e("ChatNotificationManager", "Error " + throwable)
                );
    }

    private static SofaMessage addLocalPriceToSofaMessage(final PaymentRequest paymentRequest,
                                                          final SofaMessage sofaMessage) {
        final String payload = SofaAdapters.get().toJson(paymentRequest);
        return sofaMessage.setPayload(payload);
    }

    private static void showPaymentRequestNotification(final Recipient sender,
                                                       final SofaMessage sofaMessage,
                                                       final boolean isConversationAccepted) {
        final ChatNotification activeChatNotification = getAndCacheChatNotification(sender);
        if (activeChatNotification == null) return;
        activeChatNotification.addUnreadMessage(sofaMessage);
        generateIconAndShowNotification(activeChatNotification, sofaMessage.getPrivateKey(), isConversationAccepted);
    }

    private static void generateIconAndShowNotification(final ChatNotification activeChatNotification,
                                                        final String messageId,
                                                        final boolean isConversationAccepted) {
        activeChatNotification
                .generateLargeIcon()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> showNotification(activeChatNotification, getChatNotificationBuilder(messageId, activeChatNotification, isConversationAccepted)));
    }

    private static ChatNotification getAndCacheChatNotification(final Recipient sender) {
        // Sender will be null if the transaction came from outside of the Toshi platform.
        final String notificationKey = sender == null ? ChatNotification.DEFAULT_TAG : sender.getThreadId();

        if (notificationKey.equals(currentlyOpenConversation) && !BaseApplication.get().isInBackground()) {
            return null;
        }

        final ChatNotification activeChatNotification
                = activeNotifications.get(notificationKey) != null
                ? activeNotifications.get(notificationKey)
                : new ChatNotification(sender);

        activeNotifications.put(notificationKey, activeChatNotification);

        return activeChatNotification;
    }

    private static NotificationCompat.Builder getChatNotificationBuilder(final String messageId,
                                                                         final ChatNotification activeChatNotification,
                                                                         final boolean isConversationAccepted) {
        final PendingIntent contentIntent = isConversationAccepted
                ? activeChatNotification.getPendingIntent()
                : activeChatNotification.getFallbackPendingIntent();

        final NotificationCompat.Builder builder = buildNotification(activeChatNotification)
                .setDeleteIntent(activeChatNotification.getDeleteIntent())
                .setContentIntent(contentIntent);

        if (!isConversationAccepted) return builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !activeChatNotification.isUnknownSender()) {
            builder.addAction(buildDirectReplyAction(activeChatNotification));

            if (activeChatNotification.getTypeOfLastMessage() == SofaType.PAYMENT_REQUEST) {
                builder.addAction(buildAcceptPaymentRequestAction(messageId, activeChatNotification))
                        .addAction(buildRejectPaymentRequestAction(messageId, activeChatNotification));
            }
        }

        return builder;
    }

    private static NotificationCompat.Action buildAcceptPaymentRequestAction(final String messageId,
                                                                             final ChatNotification activeChatNotification) {
        final PendingIntent acceptIntent = activeChatNotification.getAcceptPaymentRequestIntent(messageId);
        return new NotificationCompat.Action.Builder(
                0,
                BaseApplication.get().getString(R.string.button_accept),
                acceptIntent
        ).build();
    }

    private static NotificationCompat.Action buildRejectPaymentRequestAction(final String messageId,
                                                                             final ChatNotification activeChatNotification) {
        final PendingIntent rejectIntent = activeChatNotification.getRejectPaymentRequestIntent(messageId);
        return new NotificationCompat.Action.Builder(
                0,
                BaseApplication.get().getString(R.string.button_decline),
                rejectIntent
        ).build();
    }

    @RequiresApi(24)
    private static NotificationCompat.Action buildDirectReplyAction(final ChatNotification activeChatNotification) {
        final String messageLabel = BaseApplication.get().getString(R.string.message);
        final RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(messageLabel)
                .build();

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_send,
                messageLabel,
                activeChatNotification.getDirectReplyIntent()
        ).addRemoteInput(remoteInput).build();
    }
}
