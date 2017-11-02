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


import com.toshi.crypto.HDWallet;

import java.io.File;
import java.util.UUID;

import io.realm.DynamicRealm;
import io.realm.DynamicRealmObject;
import io.realm.FieldAttribute;
import io.realm.RealmMigration;
import io.realm.RealmObjectSchema;
import io.realm.RealmSchema;

public class DbMigration implements RealmMigration {

    private final HDWallet wallet;

    public DbMigration(final HDWallet wallet) {
        this.wallet = wallet;
    }

    @Override
    public void migrate(final DynamicRealm realm, long oldVersion, final long newVersion) {

        final RealmSchema schema = realm.getSchema();

        // Migrate to version 1:
        // Add a new field on User.
        if (oldVersion == 0) {
            schema.get("User")
                    .addField("cacheTimestamp", long.class);
            oldVersion++;
        }

        // Migrate to version 2:
        // Add PendingMessage table
        if (oldVersion == 1) {
            schema.create("PendingMessage")
                    .addField("privateKey", String.class, FieldAttribute.PRIMARY_KEY)
                    .addRealmObjectField("receiver", schema.get("User"))
                    .addRealmObjectField("sofaMessage", schema.get("SofaMessage"));
            oldVersion++;
        }

        // Migrate to version 3:
        // Add attachmentFilename to SofaMessage table
        if (oldVersion == 2) {
            schema.get("SofaMessage")
                    .addField("attachmentFilename", String.class);
            oldVersion++;
        }

        // Migrate to version 4:
        // Add support for reputation to User.
        if (oldVersion == 3) {
            schema.get("User")
                    .addField("reputation_score", Double.class)
                    .addField("review_count", int.class);
            oldVersion++;
        }

        // Migrate to version 5:
        // Move the custom data to top level on a User
        if (oldVersion == 4) {
            final RealmObjectSchema userSchema = schema.get("User");
            userSchema
                    .addField("about", String.class)
                    .addField("avatar", String.class)
                    .addField("location", String.class)
                    .addField("name", String.class)
                    .transform(obj -> {
                        final DynamicRealmObject customUserInfo = obj.getObject("customUserInfo");
                        obj.set("about", customUserInfo.getString("about"));
                        obj.set("avatar", customUserInfo.getString("avatar"));
                        obj.set("location", customUserInfo.getString("location"));
                        obj.set("name", customUserInfo.getString("name"));
                    })
                    .removeField("customUserInfo");
            oldVersion++;
        }

        // Migrate to version 6:
        // Rename attachmentFilename to attachmentFilePath
        if (oldVersion == 5) {
            final RealmObjectSchema sofaMessageSchema = schema.get("SofaMessage");
            sofaMessageSchema.renameField("attachmentFilename", "attachmentFilePath");
            oldVersion++;
        }

        // Migrate to version 7:
        // Add reference to Sender; this deleted all old messages
        // Migration is too much effort considering we're yet to go live.
        if (oldVersion == 6) {
            final RealmObjectSchema sofaMessageSchema = schema.get("SofaMessage");
            sofaMessageSchema
                    .removeField("sentByLocal")
                    .addRealmObjectField("sender", schema.get("User"));
            oldVersion++;
        }

        // Migrate to version 8:
        // Encrypt and shard
        if (oldVersion == 7) {
            realm.writeEncryptedCopyTo(new File(realm.getPath(), this.wallet.getOwnerAddress()), this.wallet.generateDatabaseEncryptionKey());
            oldVersion++;
        }

        if (oldVersion == 8) {
            if (schema.contains("BlockedUser")) {
                schema.remove("BlockedUser");
            }
            final RealmObjectSchema blockedUserSchema = schema.create("BlockedUser");
            blockedUserSchema.addField("owner_address", String.class, FieldAttribute.PRIMARY_KEY);
            oldVersion++;
        }

        if (oldVersion == 9) {
            final RealmObjectSchema userSchema = schema.get("User");
            if (userSchema.hasField("customAppInfo")) {
                userSchema.removeField("customAppInfo");
            }

            if (!userSchema.hasField("is_app")) {
                userSchema.addField("is_app", boolean.class);
            }

            if (schema.contains("CustomAppInformation")) {
                schema.remove("CustomAppInformation");
            }
            oldVersion++;
        }

        // Rename "Conversation" to "ContactThread"
        if (oldVersion == 10) {
            final RealmObjectSchema conversationSchema = schema.get("Conversation");
            if (conversationSchema != null) {
                conversationSchema.renameField("conversationId", "threadId");
                schema.rename("Conversation", "ContactThread");
            }
            oldVersion++;
        }

        // Add Group table
        // Add Recipient table
        // Rename "ContactThread" to "Conversation"
        // Split out recipient into seperate table
        if (oldVersion == 11) {
            if (schema.get("Group") == null) {
                schema.create("Group")
                        .addField("id", String.class, FieldAttribute.PRIMARY_KEY)
                        .addField("title", String.class)
                        .addRealmListField("members", schema.get("User"));
            }

            if (schema.get("Recipient") == null) {
                schema.create("Recipient")
                        .addField("id", String.class, FieldAttribute.PRIMARY_KEY)
                        .addRealmObjectField("user", schema.get("User"))
                        .addRealmObjectField("group", schema.get("Group"));
            }

            schema
                    .rename("ContactThread", "Conversation")
                    .addRealmObjectField("recipient", schema.get("Recipient"))
                    .transform(obj -> {
                        final String primaryKey = UUID.randomUUID().toString();
                        final DynamicRealmObject newRecipient = realm.createObject("Recipient", primaryKey);
                        newRecipient.setObject("user", obj.getObject("member"));
                        obj.set("recipient", newRecipient);
                    })
                    .removeField("member");
            oldVersion++;
        }

        if (oldVersion == 12) {
            final RealmObjectSchema userSchema = schema.get("User");
            if (!userSchema.hasField("is_public")) {
                userSchema.addField("is_public", boolean.class);
            }
            oldVersion++;
        }

        // Port Recipient ids to be threadIds
        if (oldVersion == 13) {
            schema
                    .get("Conversation")
                    .transform(obj -> {
                        // Get existing recipient object
                        final DynamicRealmObject recipient = obj.getObject("recipient");
                        final String threadId = obj.getString("threadId");
                        final DynamicRealmObject user = recipient.getObject("user");
                        final DynamicRealmObject group = recipient.getObject("group");

                        // Create new object with correct PK
                        final DynamicRealmObject newRecipient = realm.createObject("Recipient", threadId);
                        newRecipient.setObject("user", user);
                        newRecipient.setObject("group", group);

                        // Delete old recipient
                        recipient.deleteFromRealm();

                        // Set new recipient
                        obj.set("recipient", newRecipient);
                    });

            oldVersion++;
        }

        // Port PendingMessageStore to using Recipient
        if (oldVersion == 14) {
            schema
                    .get("PendingMessage")
                    .addRealmObjectField("tempReceiver", schema.get("Recipient"))
                    .transform(obj -> {
                        final DynamicRealmObject oldUser = obj.getObject("receiver");
                        final String primaryKey = oldUser.getString("owner_address");
                        final DynamicRealmObject newRecipient = realm.createObject("Recipient", primaryKey);
                        newRecipient.setObject("user", oldUser);
                        obj.set("tempReceiver", newRecipient);
                    })
                    .removeField("receiver")
                    .renameField("tempReceiver", "receiver");

            oldVersion++;
        }

        if (oldVersion == 15) {
            schema.get("User")
                .addField("average_rating", Double.class);
            oldVersion++;
        }

        if (oldVersion == 16) {
            if (schema.get("SofaError") == null) {
                schema.create("SofaError")
                        .addField("id", String.class, FieldAttribute.PRIMARY_KEY)
                        .addField("message", String.class);
            }

            if (!schema.get("SofaMessage").hasField("errorMessage")) {
                schema.get("SofaMessage")
                        .addRealmObjectField("errorMessage", schema.get("SofaError"));
            }

            oldVersion++;
        }

        if (oldVersion == 17) {
            schema.get("Conversation")
                    .addField("isAccepted", boolean.class)
                    .transform(obj -> {
                        obj.setBoolean("isAccepted", true);
                    });

            oldVersion++;
        }
    }

    @Override
    public int hashCode() {
        return DbMigration.class.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        return object != null && object instanceof DbMigration;
    }
}
