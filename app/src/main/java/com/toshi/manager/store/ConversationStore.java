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

package com.toshi.manager.store;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;

import com.toshi.model.local.Conversation;
import com.toshi.model.local.Group;
import com.toshi.model.local.Recipient;
import com.toshi.model.local.User;
import com.toshi.model.sofa.SofaMessage;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import io.realm.Sort;
import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class ConversationStore {

    private static final int FIFTEEN_MINUTES = 1000 * 60 * 15;
    private static final String THREAD_ID_FIELD = "threadId";
    private static final String MESSAGE_ID_FIELD = "privateKey";

    private static String watchedThreadId;
    private final static PublishSubject<SofaMessage> NEW_MESSAGE_SUBJECT = PublishSubject.create();
    private final static PublishSubject<SofaMessage> UPDATED_MESSAGE_SUBJECT = PublishSubject.create();
    private final static PublishSubject<SofaMessage> DELETED_MESSAGE_SUBJECT = PublishSubject.create();
    private final static PublishSubject<Conversation> CONVERSATION_CHANGED_SUBJECT = PublishSubject.create();
    private final static ExecutorService dbThread = Executors.newSingleThreadExecutor();

    // Returns a pair of RxSubjects, the first being the observable for new messages
    // the second being the observable for updated messages.
    public Pair<PublishSubject<SofaMessage>, PublishSubject<SofaMessage>> registerForChanges(final String threadId) {
        watchedThreadId = threadId;
        return new Pair<>(NEW_MESSAGE_SUBJECT, UPDATED_MESSAGE_SUBJECT);
    }

    public Observable<SofaMessage> registerForDeletedMessages(final String threadId) {
        watchedThreadId = threadId;
        return DELETED_MESSAGE_SUBJECT.asObservable();
    }

    public void stopListeningForChanges(final String threadId) {
        // Avoids the race condition where a second activity has already registered
        // before the first activity is destroyed. Thus the first activity can't deregister
        // changes for the second activity.
        if (watchedThreadId != null && watchedThreadId.equals(threadId)) {
            watchedThreadId = null;
        }
    }

    public Observable<Conversation> getConversationChangedObservable() {
        return CONVERSATION_CHANGED_SUBJECT
                .filter(thread -> thread != null);
    }

    public void saveNewGroup(@NonNull final Group group) {
        saveGroup(group)
                .observeOn(Schedulers.immediate())
                .subscribeOn(Schedulers.from(dbThread))
                .subscribe(
                        this::broadcastConversationChanged,
                        this::handleError
                );
    }

    public void saveNewMessage(
            @NonNull final Recipient receiver,
            @NonNull final SofaMessage message) {
        saveMessage(receiver, message)
        .observeOn(Schedulers.immediate())
        .subscribeOn(Schedulers.from(dbThread))
        .subscribe(
                conversationForBroadcast -> {
                    broadcastNewChatMessage(receiver.getThreadId(), message);
                    broadcastConversationChanged(conversationForBroadcast);
                },
                this::handleError
        );
    }

    private Single<Conversation> saveGroup(@NonNull final Group group) {
        return Single.fromCallable(() -> {
            final Conversation conversationToStore = getOrCreateConversation(group);
            final Realm realm = BaseApplication.get().getRealm();
            realm.beginTransaction();
            final Conversation storedConversation = realm.copyToRealmOrUpdate(conversationToStore);
            realm.commitTransaction();
            final Conversation conversationForBroadcast = realm.copyFromRealm(storedConversation);
            realm.close();

            return conversationForBroadcast;
        });
    }

    private Single<Conversation> saveMessage(
            @NonNull final Recipient receiver,
            @Nullable final SofaMessage message) {
        return Single.fromCallable(() -> {
            final Conversation conversationToStore = getOrCreateConversation(receiver);

            if (message != null && shouldSaveTimestampMessage(message, conversationToStore)) {
                final SofaMessage timestampMessage =
                        generateTimestampMessage();
                conversationToStore.addMessage(timestampMessage);
                broadcastNewChatMessage(receiver.getThreadId(), timestampMessage);
            }

            final Realm realm = BaseApplication.get().getRealm();
            realm.beginTransaction();

            if (message != null) {
                final SofaMessage storedMessage = realm.copyToRealmOrUpdate(message);
                if(conversationToStore.getThreadId().equals(watchedThreadId)) {
                    conversationToStore.setLatestMessage(storedMessage);
                } else {
                    conversationToStore.setLatestMessageAndUpdateUnreadCounter(storedMessage);
                }
            }

            final Conversation storedConversation = realm.copyToRealmOrUpdate(conversationToStore);
            realm.commitTransaction();
            final Conversation conversationForBroadcast = realm.copyFromRealm(storedConversation);
            realm.close();

            return conversationForBroadcast;
        });
    }

    @NonNull
    private Conversation getOrCreateConversation(final User user) {
        final Recipient recipient = new Recipient(user);
        return getOrCreateConversation(recipient);
    }

    @NonNull
    private Conversation getOrCreateConversation(final Group group) {
        final Recipient recipient = new Recipient(group);
        return getOrCreateConversation(recipient);
    }

    @NonNull
    private Conversation getOrCreateConversation(final Recipient recipient) {
        final Conversation existingConversation = loadWhere(THREAD_ID_FIELD, recipient.getThreadId());
        return existingConversation == null
                ? new Conversation(recipient)
                : existingConversation;
    }

    private SofaMessage generateTimestampMessage() {
        return new SofaMessage().makeNewTimeStampMessage();
    }

    private boolean shouldSaveTimestampMessage(final SofaMessage message,
                                               final Conversation conversation) {
        if (!message.isUserVisible()) return false;
        final long newMessageTimestamp = message.getCreationTime();
        final long latestMessageTimestamp = conversation.getUpdatedTime();
        return newMessageTimestamp - latestMessageTimestamp > FIFTEEN_MINUTES;
    }

    private void resetUnreadMessageCounter(final String threadId) {
        Single.fromCallable(() -> {
            final Conversation storedConversation = loadWhere(THREAD_ID_FIELD, threadId);
            if (storedConversation == null) {
                return null;
            }

            final Realm realm = BaseApplication.get().getRealm();
            realm.beginTransaction();
            storedConversation.resetUnreadCounter();
            realm.insertOrUpdate(storedConversation);
            realm.commitTransaction();
            realm.close();
            return storedConversation;
        })
        .observeOn(Schedulers.immediate())
        .subscribeOn(Schedulers.from(dbThread))
        .subscribe(
                this::broadcastConversationChanged,
                this::handleError
        );
    }

    public List<Conversation> loadAll() {
        final Realm realm = BaseApplication.get().getRealm();
        final RealmQuery<Conversation> query = realm.where(Conversation.class);
        final RealmResults<Conversation> results = query.findAllSorted("updatedTime", Sort.DESCENDING);
        final List<Conversation> allConversations = realm.copyFromRealm(results);
        realm.close();
        return allConversations;
    }

    private void broadcastConversationChanged(final Conversation conversation) {
        CONVERSATION_CHANGED_SUBJECT.onNext(conversation);
    }

    public Single<Conversation> loadByThreadId(final String threadId) {
        return Single.fromCallable(() -> {
            resetUnreadMessageCounter(threadId);
            return loadWhere(THREAD_ID_FIELD, threadId);
        });
    }

    private Conversation loadWhere(final String fieldName, final String value) {
        final Realm realm = BaseApplication.get().getRealm();
        final Conversation result = realm
                .where(Conversation.class)
                .equalTo(fieldName, value)
                .findFirst();
        final Conversation queriedConversation = result == null ? null : realm.copyFromRealm(result);
        realm.close();
        return queriedConversation;
    }

    public void updateMessage(final Recipient receiver, final SofaMessage message) {
        Completable.fromAction(() -> {
            final Realm realm = BaseApplication.get().getRealm();
            realm.beginTransaction();
            realm.insertOrUpdate(message);
            realm.commitTransaction();
            realm.close();
        })
        .observeOn(Schedulers.immediate())
        .subscribeOn(Schedulers.from(dbThread))
        .subscribe(
                () -> broadcastUpdatedChatMessage(receiver.getThreadId(), message),
                this::handleError
        );
    }

    public Completable deleteByThreadId(final String threadId) {
        return Completable.fromAction(() -> {
            final Realm realm = BaseApplication.get().getRealm();
            realm.beginTransaction();
            realm
                    .where(Conversation.class)
                    .equalTo(THREAD_ID_FIELD, threadId)
                    .findFirst()
                    .deleteFromRealm();
            realm.commitTransaction();
            realm.close();
        });
    }

    public void deleteMessageById(final Recipient receiver, final SofaMessage message) {
        Completable.fromAction(() -> {
            final Realm realm = BaseApplication.get().getRealm();
            realm.beginTransaction();
            realm
                    .where(SofaMessage.class)
                    .equalTo(MESSAGE_ID_FIELD, message.getPrivateKey())
                    .findFirst()
                    .deleteFromRealm();
            realm.commitTransaction();
            realm.close();
        })
        .observeOn(Schedulers.immediate())
        .subscribeOn(Schedulers.from(dbThread))
        .subscribe(
                () -> broadcastDeletedChatMessage(receiver.getThreadId(), message),
                this::handleError
        );
    }

    public boolean areUnreadMessages() {
        final Realm realm = BaseApplication.get().getRealm();
        final Conversation result = realm
                .where(Conversation.class)
                .greaterThan("numberOfUnread", 0)
                .findFirst();
        final boolean areUnreadMessages = result != null;
        realm.close();
        return areUnreadMessages;
    }

    public Single<SofaMessage> getSofaMessageById(final String id) {
        return Single.fromCallable(() -> {
            final Realm realm = BaseApplication.get().getRealm();
            final SofaMessage result =
                    realm
                            .where(SofaMessage.class)
                            .equalTo("privateKey", id)
                            .findFirst();
            final SofaMessage sofaMessage = realm.copyFromRealm(result);
            realm.close();
            return sofaMessage;
        });
    }

    public Completable acceptConversation(final Conversation conversation) {
        return Completable.fromAction(() -> {
            final Realm realm = BaseApplication.get().getRealm();
            realm.beginTransaction();
            conversation.setAccepted();
            realm.copyToRealmOrUpdate(conversation);
            realm.commitTransaction();
            realm.close();
        });
    }

    private void broadcastNewChatMessage(final String threadId, final SofaMessage newMessage) {
        if (watchedThreadId == null || !watchedThreadId.equals(threadId)) {
            return;
        }
        NEW_MESSAGE_SUBJECT.onNext(newMessage);
    }

    private void broadcastUpdatedChatMessage(final String threadId, final SofaMessage updatedMessage) {
        if (watchedThreadId == null || !watchedThreadId.equals(threadId)) {
            return;
        }
        UPDATED_MESSAGE_SUBJECT.onNext(updatedMessage);
    }

    private void broadcastDeletedChatMessage(final String threadId, final SofaMessage deletedMessage) {
        if (watchedThreadId == null || !watchedThreadId.equals(threadId)) {
            return;
        }
        DELETED_MESSAGE_SUBJECT.onNext(deletedMessage);
    }

    private void handleError(final Throwable throwable) {
        LogUtil.exception(getClass(), throwable);
    }
}
