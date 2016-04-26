package org.cryse.unifystorage.explorer.utils.openfile;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.view.View;
import android.webkit.MimeTypeMap;

import com.afollestad.materialdialogs.MaterialDialog;

import org.cryse.unifystorage.explorer.R;

import java.io.File;
import java.util.List;

public class AndroidOpenFileUtils implements OpenFileUtils {
    private Context mContext;

    public AndroidOpenFileUtils(Context context) {
        this.mContext = context;
    }

    @Override
    public void openFileByPath(String filePath, boolean useSystemSelector) {
        openFileByUri(Uri.fromFile(new File(filePath)).toString(), useSystemSelector);
    }

    @Override
    public void openFileByUri(String uriString, boolean useSystemSelector) {
        Uri uri = Uri.parse(uriString);
        MimeTypeMap mimeMap = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.getPath());
        String mime = mimeMap.getMimeTypeFromExtension(extension);
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (mime != null) {
            intent.setDataAndType(uri, mime);
            List<ResolveInfo> resolveInfos = mContext.getPackageManager().queryIntentActivities(intent, 0);
            if (!resolveInfos.isEmpty()) {
                if (useSystemSelector)
                    mContext.startActivity(intent);
                else {
                    buildCustomOpenChooser(mContext, uri, resolveInfos);
                }
                return;
            }
        }
        openUnknownFile(uri);
    }

    private void openUnknownFile(Uri uri) {
        new MaterialDialog.Builder(mContext)
                .title(R.string.dialog_title_open_as_type)
                .items(R.array.array_open_as_type)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                    }
                })
                .show();
    }

    private void buildCustomOpenChooser(Context context, Uri uri, List<ResolveInfo> resolveInfos) {
        String[] items = new String[resolveInfos.size()];
        for (int i = 0; i < resolveInfos.size(); i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            items[i] = resolveInfo.activityInfo.name;
        }
        new MaterialDialog.Builder(context)
                .title(R.string.dialog_title_open_as_type)
                .items(items)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                    }
                })
                .show();
    }
}