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

package com.toshi.util;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.toshi.R;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.AmountActivity;
import com.toshi.view.activity.AttachmentConfirmationActivity;
import com.toshi.view.activity.FullscreenImageActivity;
import com.toshi.view.activity.ViewUserActivity;
import com.toshi.view.activity.WebViewActivity;

import java.io.File;

public class ChatNavigation {

    public void startAttachmentPicker(final AppCompatActivity activity,
                                      final String path) {
        if (activity == null) return;

        final File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(activity, activity.getString(R.string.no_file_found), Toast.LENGTH_SHORT).show();
            return;
        }

        final FileUtil fileUtil = new FileUtil();
        final String mimeType = fileUtil.getMimeTypeFromFilename(path);
        final Uri fileUri = fileUtil.getUriFromFile(file);

        startExternalActivity(activity, fileUri, mimeType);
    }

    private void startExternalActivity(final AppCompatActivity activity,
                                       final Uri uri,
                                       final String mimeType) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        PermissionUtil.grantUriPermission(activity, intent, uri);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, activity.getString(R.string.no_app_found), Toast.LENGTH_SHORT).show();
        }
    }

    public void startCameraActivity(final AppCompatActivity activity,
                                    final Uri photoUri,
                                    final int requestCode) {
        final Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        PermissionUtil.grantUriPermission(activity, cameraIntent, photoUri);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        activity.startActivityForResult(cameraIntent, requestCode);
    }

    public void startAttachmentActivity(final AppCompatActivity activity,
                                        final int requestCode) {
        final Intent attachmentIntent =  Intent.createChooser(
                new Intent()
                        .setType("*/*")
                        .setAction(Intent.ACTION_GET_CONTENT)
                        .addCategory(Intent.CATEGORY_OPENABLE),
                BaseApplication.get().getString(R.string.select_picture)
        );

        activity.startActivityForResult(attachmentIntent, requestCode);
    }

    public void startPaymentRequestActivityForResult(final AppCompatActivity activity,
                                                     final int requestCode) {
        final Intent intent = new Intent(activity, AmountActivity.class)
                .putExtra(AmountActivity.VIEW_TYPE, PaymentType.TYPE_REQUEST);
        activity.startActivityForResult(intent, requestCode);
    }

    public void startPaymentActivityForResult(final AppCompatActivity activity,
                                              final int requestCode) {
        final Intent intent = new Intent(activity, AmountActivity.class)
                .putExtra(AmountActivity.VIEW_TYPE, PaymentType.TYPE_SEND);
        activity.startActivityForResult(intent, requestCode);
    }

    public void startWebViewActivity(final AppCompatActivity activity,
                                     final String actionUrl) {
        final Intent intent = new Intent(activity, WebViewActivity.class)
                .putExtra(WebViewActivity.EXTRA__ADDRESS, actionUrl);
        activity.startActivity(intent);
    }

    public void startProfileActivityWithId(final AppCompatActivity activity,
                                           final String ownerAddress) {
        final Intent intent = new Intent(activity, ViewUserActivity.class)
                .putExtra(ViewUserActivity.EXTRA__USER_ADDRESS, ownerAddress);
        activity.startActivity(intent);
    }

    public void startProfileActivityWithUsername(final AppCompatActivity activity,
                                                 final String username) {
        final Intent intent = new Intent(activity, ViewUserActivity.class)
                .putExtra(ViewUserActivity.EXTRA__USER_NAME, username);
        activity.startActivity(intent);
    }

    public void startAttachmentConfirmationActivity(final AppCompatActivity activity,
                                                    final Uri uri,
                                                    final int requestCode) {
        final Intent confirmationIntent = new Intent(activity, AttachmentConfirmationActivity.class)
                .putExtra(AttachmentConfirmationActivity.ATTACHMENT_URI, uri);
        activity.startActivityForResult(confirmationIntent, requestCode);
    }

    public void startImageActivity(final AppCompatActivity activity,
                                   final String filePath) {
        final Intent intent = new Intent(activity, FullscreenImageActivity.class)
                .putExtra(FullscreenImageActivity.FILE_PATH, filePath);
        activity.startActivity(intent);
    }
}
