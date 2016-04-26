package org.cryse.unifystorage.explorer.ui.common;

import android.content.DialogInterface;
import android.databinding.DataBindingUtil;
import android.databinding.Observable;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.afollestad.impression.widget.breadcrumbs.BreadCrumbLayout;
import com.afollestad.impression.widget.breadcrumbs.Crumb;
import com.afollestad.materialcab.MaterialCab;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;

import org.cryse.unifystorage.RemoteFile;
import org.cryse.unifystorage.credential.Credential;
import org.cryse.unifystorage.explorer.PrefsConst;
import org.cryse.unifystorage.explorer.R;
import org.cryse.unifystorage.explorer.databinding.FragmentStorageProviderBinding;
import org.cryse.unifystorage.explorer.event.AbstractEvent;
import org.cryse.unifystorage.explorer.event.EventConst;
import org.cryse.unifystorage.explorer.event.FileDeleteEvent;
import org.cryse.unifystorage.explorer.event.FileDeleteResultEvent;
import org.cryse.unifystorage.explorer.service.FileOperation;
import org.cryse.unifystorage.explorer.service.LongOperationService;
import org.cryse.unifystorage.explorer.ui.MainActivity;
import org.cryse.unifystorage.explorer.ui.adapter.FileAdapter;
import org.cryse.unifystorage.explorer.utils.CollectionViewState;
import org.cryse.unifystorage.explorer.utils.MenuUtils;
import org.cryse.unifystorage.explorer.utils.RandomUtils;
import org.cryse.unifystorage.explorer.utils.ResourceUtils;
import org.cryse.unifystorage.explorer.utils.copy.CopyManager;
import org.cryse.unifystorage.explorer.utils.copy.CopyTask;
import org.cryse.unifystorage.explorer.viewmodel.FileListViewModel;
import org.cryse.unifystorage.utils.FileSizeUtils;
import org.cryse.utils.preference.BooleanPrefs;
import org.cryse.utils.preference.Prefs;
import org.cryse.utils.selector.SelectableRecyclerViewAdapter;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import butterknife.Bind;
import butterknife.ButterKnife;

public abstract class AbstractStorageProviderFragment extends AbstractFragment implements
        FileAdapter.OnFileClickListener,
        FileListViewModel.DataListener,
        SelectableRecyclerViewAdapter.OnSelectionListener,
        MaterialCab.Callback {
    private AtomicBoolean mDoubleBackPressedOnce = new AtomicBoolean(false);
    private Handler mHandler = new Handler();

    private final Runnable mBackPressdRunnable = new Runnable() {
        @Override
        public void run() {
            mDoubleBackPressedOnce.set(false);
        }
    };
    protected int mStorageProviderRecordId;
    protected Credential mCredential;
    protected String[] mExtras;

    protected FragmentStorageProviderBinding mBinding;
    protected FileListViewModel mViewModel;
    protected FileAdapter mCollectionAdapter;
    protected MaterialDialog mDownloadDialog;

    protected BooleanPrefs mShowHiddenFilesPrefs;
    protected MenuItem mShowHiddenFilesMenuItem;
    protected MaterialCab mCab;

    @Bind(R.id.toolbar)
    Toolbar mToolbar;

    @Bind(R.id.breadCrumbs)
    BreadCrumbLayout mBreadCrumbLayout;

    @Bind(R.id.fragment_storageprovider_recyclerview_files)
    RecyclerView mCollectionView;

    @Bind(R.id.fragment_storageprovider_fab_menu)
    FloatingActionMenu mFabMenu;

    @Bind(R.id.fragment_storageprovider_fab_new_directory)
    FloatingActionButton mFabNewDirectory;

    @Bind(R.id.fragment_storageprovider_fab_new_file)
    FloatingActionButton mFabNewFile;

    @Bind(R.id.fragment_storageprovider_fab_paste)
    FloatingActionButton mFabPaste;

    private int mPrimaryColor;
    private int mAccentColor;
    private int mAccentColorDark;
    private int mToolbarContentColor;
    private int mFabIconColor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        readArguments();
        mCollectionAdapter = new FileAdapter(getActivity());
        mCollectionAdapter.setOnFileClickListener(this);
        mCollectionAdapter.setOnSelectionListener(this);
        mViewModel = buildViewModel(mCredential);
        mViewModel.buildStorageProvider();
        mShowHiddenFilesPrefs = Prefs.getBooleanPrefs(
                PrefsConst.PREFS_SHOW_HIDDEN_FILES,
                PrefsConst.PREFS_SHOW_HIDDEN_FILES_VALUE
        );
        mViewModel.setShowHiddenFiles(mShowHiddenFilesPrefs.get());
    }

    protected void readArguments() {

    }

    protected abstract String getLogTag();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_storage_provider, container, false);
        mBinding.setViewModel(mViewModel);
        View fragmentView = mBinding.getRoot();

        ButterKnife.bind(this, fragmentView);
        setupToolbar();
        setupRecyclerView();
        setupBreadCrumb();
        setupFab();
        return fragmentView;
    }

    private void setupFab() {
        if(CopyManager.getInstance().hasCopyTask()) {
            mFabMenu.setVisibility(View.GONE);
            mFabPaste.setVisibility(View.VISIBLE);
        } else {
            mFabMenu.setVisibility(View.VISIBLE);
            mFabPaste.setVisibility(View.GONE);
        }
        mFabNewDirectory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialDialog.Builder(getContext())
                        .title(R.string.dialog_title_create_new_directory)
                        .content(R.string.dialog_content_create_new_directory)
                        .inputType(InputType.TYPE_CLASS_TEXT)
                        .input(null, null, false, new MaterialDialog.InputCallback() {
                            @Override
                            public void onInput(MaterialDialog dialog, CharSequence input) {
                                mViewModel.createDirectory(mViewModel.getDirectory().directory, input.toString());
                            }
                        })
                        .show();
                mFabMenu.close(true);
            }
        });
        mFabPaste.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menuPasteFile();
            }
        });
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyColorToViews();
    }

    private void applyColorToViews() {
        mPrimaryColor = ResourceUtils.primaryColor(getContext());
        mToolbarContentColor = ResourceUtils.toolbarTextColor(getContext());
        /*mPrimaryColor = Config.primaryColor(getContext(), mATEKey);
        int textColorHint = ResourceUtils.adjustAlpha(mToolbarContentColor, 0.54f);
        int arrowColor = ResourceUtils.adjustAlpha(mToolbarContentColor, 1.0f);
        mBreadCrumbLayout.setCrumbActiveColor(mToolbarContentColor);
        mBreadCrumbLayout.setCrumbInactiveColor(textColorHint);
        mBreadCrumbLayout.setArrowColor(arrowColor);
        mBreadCrumbLayout.setBackgroundColor(mPrimaryColor);
        // ATE.apply(mBreadCrumbLayout, mATEKey);
        mAccentColor = Config.accentColor(getContext(), mATEKey);
        int colorDarken = ResourceUtils.makeColorDarken(mAccentColor, 0.8f);
        int colorDarken2 = ResourceUtils.makeColorDarken(mAccentColor, 0.9f);
        int mFabIconColor = ResourceUtils.isColorLight(mAccentColor) ? Color.BLACK : Color.WHITE;
        mFabMenu.setMenuButtonColorNormal(mAccentColor);
        mFabMenu.setMenuButtonColorPressed(colorDarken);
        mFabMenu.setMenuButtonColorRipple(colorDarken2);
        mFabMenu.getMenuIconView().setColorFilter(mFabIconColor, PorterDuff.Mode.SRC_ATOP);
        ResourceUtils.applyColorToFab(mFabNewDirectory, mAccentColor, colorDarken, colorDarken2, R.drawable.ic_format_folder, mFabIconColor);
        ResourceUtils.applyColorToFab(mFabNewFile, mAccentColor, colorDarken, colorDarken2, R.drawable.ic_format_file, mFabIconColor);
        ResourceUtils.applyColorToFab(mFabPaste, mAccentColor, colorDarken, colorDarken2, R.drawable.ic_action_paste, mFabIconColor);*/
        mFabNewDirectory.setImageDrawable(ResourceUtils.makeTintedDrawable(getActivity(), R.drawable.ic_format_folder, Color.WHITE));
        mFabNewFile.setImageDrawable(ResourceUtils.makeTintedDrawable(getActivity(), R.drawable.ic_format_file, Color.WHITE));
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getActivity().invalidateOptionsMenu();
        if (getView() != null) {
            getView().setFocusableInTouchMode(true);
            getView().requestFocus();
            getView().setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        if(mCollectionAdapter.isInSelection() || !mViewModel.isAtTopPath()) {
                            if (!AbstractStorageProviderFragment.this.mDoubleBackPressedOnce.get()) {
                                AbstractStorageProviderFragment.this.mDoubleBackPressedOnce.set(true);
                                mHandler.postDelayed(mBackPressdRunnable, 400);
                                if (mCollectionAdapter.isInSelection()) {
                                    mCollectionAdapter.clearSelection();
                                } else if (!mViewModel.isAtTopPath()) {
                                    mViewModel.onBackPressed();
                                }
                            } else {
                                // Click Back too quick, ignore.
                            }
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
    }

    private void setupToolbar() {
        getAppCompatActivity().setSupportActionBar(mToolbar);
        ActionBar actionBar = getAppCompatActivity().getSupportActionBar();
        if(actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            Drawable indicatorDrawable = ResourceUtils.makeTintedDrawable(getActivity(), R.drawable.ic_action_menu_drawer, Color.WHITE);
            actionBar.setHomeAsUpIndicator(indicatorDrawable);
        }
    }

    private void setupBreadCrumb() {
        mBreadCrumbLayout.setCallback(new BreadCrumbLayout.SelectionCallback() {
            @Override
            public void onCrumbSelection(Crumb crumb, int index) {
                if (index == -1) {
                    //onBackPressed();
                } else {
                    String activeFile = null;
                    if (mBreadCrumbLayout.getActiveIndex() > -1) {
                        activeFile = mBreadCrumbLayout.getCrumb(mBreadCrumbLayout.getActiveIndex()).getPath();
                    }
                    if (crumb.getPath() != null && activeFile != null &&
                            crumb.getPath().equals(activeFile)) {
                        // When the target path is current, scroll file list to top.
                        mCollectionView.scrollToPosition(0);
                        /*Fragment frag = getFragmentManager().findFragmentById(R.id.content_frame);
                        ((MediaFragment) frag).jumpToTop(true);*/
                    } else {
                        mViewModel.jumpBack(crumb.getPath(), getCollectionViewState());
                    }
                }
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_filelist, menu);
        mShowHiddenFilesMenuItem = menu.findItem(R.id.action_show_hidden_files);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if(mShowHiddenFilesMenuItem != null) {
            mShowHiddenFilesMenuItem.setChecked(mShowHiddenFilesPrefs.get());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if(getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).getNavigationDrawer().openDrawer();
                    return true;
                } else {
                    return false;
                }
            case R.id.action_show_hidden_files:
                boolean isShow = mShowHiddenFilesPrefs.get();
                mShowHiddenFilesPrefs.set(!isShow);
                if(mShowHiddenFilesMenuItem!= null)
                    mShowHiddenFilesMenuItem.setChecked(!isShow);
                mViewModel.setShowHiddenFiles(!isShow);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        mViewModel.destroy();
        if (mHandler != null) { mHandler.removeCallbacks(mBackPressdRunnable); }
    }

    private void setupRecyclerView() {
        mCollectionView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mCollectionView.setAdapter(mCollectionAdapter);
    }

    protected abstract FileListViewModel buildViewModel(Credential credential);

    @Override
    public void onFileClick(View view, int position, RemoteFile file) {
        if(mCollectionAdapter.isInSelection()) {
            mCollectionAdapter.toggleSelection(position);
        } else {
            mViewModel.onFileClick(file, getCollectionViewState());
        }
    }

    @Override
    public void onFileLongClick(View view, int position, RemoteFile file) {
        mCollectionAdapter.toggleSelection(position);
        mViewModel.onFileLongClick(file);
    }

    private CollectionViewState getCollectionViewState() {
        LinearLayoutManager manager = (LinearLayoutManager) mCollectionView.getLayoutManager();
        int position = manager.findFirstVisibleItemPosition();
        View firstItemView = manager.findViewByPosition(position);
        if(firstItemView != null) {
            float offset = firstItemView.getTop();
            return new CollectionViewState(position, offset);
        } else
            return CollectionViewState.EMPTY;
    }

    @Override
    public void onStorageProviderReady() {
        mViewModel.mDirectory.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable observable, int i) {
                mCollectionAdapter.replaceWith( mViewModel.mDirectory.get().files);
                updateBreadcrumb( mViewModel.mDirectory.get().directory.getPath());
            }
        });
    }

    @Override
    public void onDirectoryItemDelete(int position) {
        mCollectionAdapter.remove(position);
    }

    @Override
    public void onCollectionViewStateRestore(CollectionViewState collectionViewState) {
        LinearLayoutManager manager = (LinearLayoutManager) mCollectionView.getLayoutManager();
        manager.scrollToPositionWithOffset(collectionViewState.position, (int) collectionViewState.offset);
    }

    @Override
    public void onCredentialRefreshed(Credential credential) {
        this.mCredential = credential;
    }

    @Override
    public void onShowDownloadDialog(String fileName, long fileSize, DialogInterface.OnDismissListener dismissListener) {
        if(mDownloadDialog != null && mDownloadDialog.isShowing()) mDownloadDialog.dismiss();
        mDownloadDialog = new MaterialDialog.Builder(getContext())
                .title(R.string.dialog_title_opening_file)
                .content(R.string.dialog_content_opening)
                .progress(false, 100, false)
                .dismissListener(dismissListener)
                .show();
    }

    @Override
    public void onUpdateDownloadDialog(String fileName, long readSize, long fileSize) {
        if(mDownloadDialog != null && mDownloadDialog.isShowing()) {
            int newPercent = (int) Math.round(((double) readSize  / (double) fileSize) * 100.0d);
            int currentPercent = mDownloadDialog.getCurrentProgress();
            if(newPercent > currentPercent) {
                mDownloadDialog.incrementProgress(newPercent - currentPercent);
                mDownloadDialog.setContent(
                        String.format(
                                "%s / %s",
                                FileSizeUtils.humanReadableByteCount(readSize, true),
                                FileSizeUtils.humanReadableByteCount(fileSize, true)
                        ));
            }
        }
    }

    @Override
    public void onDismissDownloadDialog() {
        if(mDownloadDialog != null && mDownloadDialog.isShowing()) {
            mDownloadDialog.dismiss();
            mDownloadDialog = null;
        }
    }

    @Override
    public void onShowErrorDialog(String title, String content) {
        new MaterialDialog.Builder(getContext())
                .title(title)
                .content(content)
                .positiveText(android.R.string.ok)
                .show();
    }

    public void updateBreadcrumb(String path) {
        if (path == null || TextUtils.isEmpty(mBreadCrumbLayout.getTopPath())) {
            // Initial directory
            // path = mCurrentDirectory.getPath();
            mBreadCrumbLayout.setTopPath(path);
        }

        Crumb crumb = new Crumb(getContext(), path);
        updateBreadcrumb(crumb, true, true);
    }

    public void updateBreadcrumb(Crumb crumb, boolean forceRecreate, boolean addToHistory) {
        if (forceRecreate) {
            // Rebuild artificial history, most likely first time load
            mBreadCrumbLayout.clearHistory();
            String path = crumb.getPath();
            while (path != null) {
                mBreadCrumbLayout.addHistory(new Crumb(getContext(), path));
                if (mBreadCrumbLayout.isTopPath(path)) {
                    break;
                }
                path = new File(path).getParent();
            }
            mBreadCrumbLayout.reverseHistory();
        } else if (addToHistory) {
            mBreadCrumbLayout.addHistory(crumb);
        }
        mBreadCrumbLayout.setActiveOrAdd(crumb, forceRecreate);
    }

    @Override
    public void onSelectionStart() {
        mCab = new MaterialCab(getAppCompatActivity(), R.id.cab_stub)
                .setMenu(R.menu.menu_cab_fileselection)
                .setBackgroundColor(mPrimaryColor)
                .setCloseDrawableRes(R.drawable.ic_action_menu_close)
                .start(this);
    }

    @Override
    public void onSelectionEnd() {
        if(mCab != null && mCab.isActive())
            mCab.finish();
    }

    @Override
    public void onSelect(int currentSelectionCount, int...positions) {
        if(mCab != null && mCab.isActive())
            mCab.setTitle(Integer.toString(currentSelectionCount));
    }

    @Override
    public void onDeselect(int currentSelectionCount, int...positions) {
        if(mCab != null && mCab.isActive())
            mCab.setTitle(Integer.toString(currentSelectionCount));
    }

    @Override
    public boolean onCabCreated(MaterialCab materialCab, Menu menu) {
        MenuUtils.showMenuItemIcon(menu);
        // ATE.applyMenu(getActivity(), mATEKey, menu);
        //DrawableCompat.setTint(materialCab.getToolbar().getNavigationIcon(), mToolbarContentColor);
        return true;
    }

    @Override
    public boolean onCabItemClicked(MenuItem menuItem) {
        int menuItemId = menuItem.getItemId();
        switch (menuItemId) {
            case R.id.action_cab_delete:
                menuDeleteFile();
                break;
            case R.id.action_cab_select_all:
                menuSelectAll();
                break;
            case R.id.action_cab_copy:
                menuCopyFile();
                break;
        }
        return true;
    }

    @Override
    public boolean onCabFinished(MaterialCab materialCab) {
        if(mCollectionAdapter.isInSelection())
            mCollectionAdapter.clearSelection();
        return true;
    }

    protected void menuDeleteFile() {
        RemoteFile[] files = mCollectionAdapter.getSelectionItems(RemoteFile.class);
        mCollectionAdapter.clearSelection();
        LongOperationService.LongOperationBinder longOperationBinder = getMainActivity().getLongOperationBinder();
        longOperationBinder.doOperation(
                new FileOperation(
                        FileOperation.FileOperationCode.DELETE,
                        RandomUtils.nextInt(),
                        new FileOperation.StorageProviderInfo(
                                mStorageProviderRecordId,
                                mCredential,
                                mExtras
                        ),
                        mViewModel.getDirectory().directory,
                        files
                )
        );
    }

    protected void menuCopyFile() {
        RemoteFile[] files = mCollectionAdapter.getSelectionItems(RemoteFile.class);
        mCollectionAdapter.clearSelection();
        CopyManager.getInstance().setCopyTask(new CopyTask(mStorageProviderRecordId, files));
    }

    protected void menuPasteFile() {
        if (CopyManager.getInstance().hasCopyTask()) {
            CopyTask task = CopyManager.getInstance().getCurrentCopyTask();
            RemoteFile[] files = task.fileToCopy;
            Toast.makeText(getContext(), "Paste!", Toast.LENGTH_SHORT).show();
            CopyManager.getInstance().cancelCopyTask();
            LongOperationService.LongOperationBinder longOperationBinder = getMainActivity().getLongOperationBinder();
            longOperationBinder.doOperation(
                    new FileOperation(
                            FileOperation.FileOperationCode.COPY,
                            RandomUtils.nextInt(),
                            new FileOperation.StorageProviderInfo(
                                    mStorageProviderRecordId,
                                    mCredential,
                                    mExtras
                            ),
                            mViewModel.getDirectory().directory,
                            files
                    )
            );
        }
    }

    @Override
    protected void onEvent(AbstractEvent event) {
        super.onEvent(event);
        int eventId = event.eventId();
        switch (eventId) {
            case EventConst.EVENT_ID_FILE_DELETE:
                onFileDeleteEvent((FileDeleteEvent) event);
                break;
            case EventConst.EVENT_ID_FILE_DELETE_RESULT:
                onFileDeleteResultEvent((FileDeleteResultEvent) event);
                break;
            case EventConst.EVENT_ID_SELECT_COPY_EVENT:
                Toast.makeText(getContext(), String.format("Select %d files to copy.", CopyManager.getInstance().getCurrentCopyTask().fileToCopy.length), Toast.LENGTH_SHORT).show();
                mFabMenu.setVisibility(View.GONE);
                mFabPaste.setVisibility(View.VISIBLE);
                break;
            case EventConst.EVENT_ID_CANCEL_SELECT_COPY_EVENT:
                Toast.makeText(getContext(), "Copy cancel.", Toast.LENGTH_SHORT).show();
                mFabMenu.setVisibility(View.VISIBLE);
                mFabPaste.setVisibility(View.GONE);
                break;
        }
    }

    protected void onFileDeleteEvent(FileDeleteEvent fileDeleteEvent) {
        if(fileDeleteEvent.providerId == this.mStorageProviderRecordId) {
            if (fileDeleteEvent.success) {
                mViewModel.onDeleteFileEvent(fileDeleteEvent);
            } else {
                Toast.makeText(getContext(), "Delete: " + fileDeleteEvent.fileName + " failed.", Toast.LENGTH_SHORT).show();
            }

        }
    }

    protected void onFileDeleteResultEvent(FileDeleteResultEvent fileDeleteResultEvent) {
        if(fileDeleteResultEvent.providerId == this.mStorageProviderRecordId) {
            if(fileDeleteResultEvent.succes) {
            } else {
                Toast.makeText(getContext(), fileDeleteResultEvent.errorMessage, Toast.LENGTH_SHORT).show();
            }
        }
    }

    protected void menuSelectAll() {
        mCollectionAdapter.selectAll();
    }

    protected MainActivity getMainActivity() {
        return (MainActivity) getAppCompatActivity();
    }
}