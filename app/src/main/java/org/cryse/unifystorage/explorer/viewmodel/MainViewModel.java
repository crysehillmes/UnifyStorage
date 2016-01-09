package org.cryse.unifystorage.explorer.viewmodel;

import android.content.Context;

import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;

import org.cryse.unifystorage.explorer.R;
import org.cryse.unifystorage.explorer.application.StorageProviderManager;
import org.cryse.unifystorage.explorer.data.UnifyStorageDatabase;
import org.cryse.unifystorage.explorer.model.StorageProviderRecord;
import org.cryse.unifystorage.explorer.utils.DrawerItemUtils;

import java.util.ArrayList;
import java.util.List;

public class MainViewModel implements ViewModel {
    private IDrawerItem[] mDrawerItems;
    private Context mContext;
    private DataListener mDataListener;
    private UnifyStorageDatabase mUnifyStorageDatabase;
    private int mCurrentSelectionIdentifier;

    public MainViewModel(DataListener mDataListener, Context mContext) {
        this.mDataListener = mDataListener;
        this.mContext = mContext;
        this.mUnifyStorageDatabase = UnifyStorageDatabase.getInstance();
    }

    @Override
    public void destroy() {
    }

    public void updateDrawerItems(int currentSelectionIdentifier) {
        buildDrawerItems();
        mDataListener.onDrawerItemsChanged(mDrawerItems, currentSelectionIdentifier);
    }

    private void buildDrawerItems() {
        List<IDrawerItem> drawerItems = new ArrayList<>();


        /*// Firstly get all local storage devices:
        String[] externalStoragePaths = LocalStorageUtils.getStorageDirectories(mContext);
        int[] externalStorageTypes = DrawerItemUtils.getStorageDirectoryTypes(mContext, externalStoragePaths);

        // Secondly get all saved storage providers
        List<StorageProviderRecord> savedStorageProviders = mUnifyStorageDatabase.getSavedStorageProviders();
        int otherStorageProvidersCount = savedStorageProviders.size();

        // Finally the const items count
        int constDrawerItemsCount = 5;
        mDrawerItems = new IDrawerItem[externalStoragePaths.length + otherStorageProvidersCount + constDrawerItemsCount];

        // First insert all local storage devices
        for (int i = 0; i < externalStoragePaths.length; i++) {
            String path = externalStoragePaths[i];
            int type = externalStorageTypes[i];
            switch (type) {
                case DrawerItemUtils.STORAGE_DIRECTORY_INTERNAL_STORAGE:
                    drawerItems.add(new PrimaryDrawerItem().withName(mContext.getString(R.string.drawer_local_internal_storage))
                            .withTag(path)
                            .withIcon(R.drawable.ic_drawer_internal_storage)
                            .withIdentifier(type)
                            .withSelectable(true));
                    break;
                default:
                    drawerItems.add(new PrimaryDrawerItem().withName(Path.getFileName(path))
                            .withTag(path)
                            .withIcon(R.drawable.ic_drawer_sdcard)
                            .withIdentifier(type)
                            .withSelectable(true));
                    break;
            }
        }
        // Then the saved providers
        for (StorageProviderRecord record : savedStorageProviders) {
            drawerItems.add(new PrimaryDrawerItem()
                    .withName(record.getDisplayName())
                    .withDescription(record.getUserName())
                    .withTag(record)
                    .withIcon(R.drawable.ic_drawer_sdcard)
                    .withIdentifier(record.getId())
                    .withSelectable(true));
        }*/

        List<StorageProviderRecord> storageProviderRecords = StorageProviderManager.getInstance().loadStorageProviderRecordsWithLocal(mContext);
        for(StorageProviderRecord record : storageProviderRecords) {
            PrimaryDrawerItem item = new PrimaryDrawerItem();
            item.withName(record.getDisplayName()).withSelectable(true).withIdentifier(record.getId());
            switch (record.getProviderType()) {
                case StorageProviderRecord.PROVIDER_LOCAL_STORAGE:
                    if(record.getId() == DrawerItemUtils.STORAGE_DIRECTORY_INTERNAL_STORAGE) {
                        item.withTag(record.getExtraData())
                                .withIcon(R.drawable.ic_drawer_internal_storage);
                    } else if(record.getId() <= DrawerItemUtils.STORAGE_DIRECTORY_EXTERNAl_STORAGE_START) {
                        item.withTag(record.getExtraData())
                                .withIcon(R.drawable.ic_drawer_sdcard);
                    } else {
                        item.withTag(record)
                                .withIcon(R.drawable.ic_file_type_folder);
                    }
                    break;
                default:
                    item.withTag(record)
                        .withIcon(R.drawable.ic_drawer_cloud);
            }
            drawerItems.add(item);
        }

        // Finally const items
        drawerItems.add(new PrimaryDrawerItem().withName(R.string.drawer_add_storage_provider)
                .withIcon(R.drawable.ic_drawer_add_storage_provider)
                .withIdentifier(DrawerItemUtils.DRAWER_ITEM_ADD_PROVIDER)
                .withSelectable(false));
        drawerItems.add(new DividerDrawerItem());
        drawerItems.add(new PrimaryDrawerItem().withName(R.string.drawer_help_and_feedback)
                .withIdentifier(DrawerItemUtils.DRAWER_ITEM_HELP_FEEDBACK)
                .withSelectable(false));
        drawerItems.add(new PrimaryDrawerItem().withName(R.string.drawer_github_repo)
                .withIdentifier(DrawerItemUtils.DRAWER_ITEM_GITHUB_REPO)
                .withSelectable(false));
        drawerItems.add(new PrimaryDrawerItem().withName(R.string.drawer_settings)
                .withIdentifier(DrawerItemUtils.DRAWER_ITEM_SETTINGS)
                .withSelectable(false));
        mDrawerItems = drawerItems.toArray(new IDrawerItem[drawerItems.size()]);
    }

    public void onNavigationSelected(IDrawerItem drawerItem) {
        if(mDataListener != null)
            mDataListener.onNavigateTo(drawerItem);
    }

    public void addNewProvider(String displayName, String userName, int providerType, String credentialData, String extraData) {
        StorageProviderManager.getInstance().addStorageProviderRecord(displayName, userName, providerType, credentialData, extraData);
    }

    public interface DataListener {
        void onDrawerItemsChanged(IDrawerItem[] drawerItems, int selectionIdentifier);
        void onNavigateTo(IDrawerItem drawerItem);
    }
}
