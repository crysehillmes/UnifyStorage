package org.cryse.unifystorage.explorer.files;


import org.cryse.unifystorage.AbstractFile;
import org.cryse.unifystorage.RemoteFile;
import org.cryse.unifystorage.RxStorageProvider;
import org.cryse.unifystorage.StorageProvider;
import org.cryse.unifystorage.credential.Credential;
import org.cryse.unifystorage.explorer.application.StorageProviderManager;
import org.cryse.unifystorage.explorer.base.BasePresenter;
import org.cryse.unifystorage.explorer.event.NewTaskEvent;
import org.cryse.unifystorage.explorer.event.RxEventBus;
import org.cryse.unifystorage.explorer.executor.PostExecutionThread;
import org.cryse.unifystorage.explorer.executor.ThreadExecutor;
import org.cryse.unifystorage.explorer.interactor.CreateFolderUseCase;
import org.cryse.unifystorage.explorer.interactor.DefaultSubscriber;
import org.cryse.unifystorage.explorer.interactor.GetFilesUseCase;
import org.cryse.unifystorage.explorer.interactor.UseCase;
import org.cryse.unifystorage.explorer.model.StorageProviderInfo;
import org.cryse.unifystorage.explorer.model.StorageProviderRecord;
import org.cryse.unifystorage.explorer.service.task.CreateFolderTask;
import org.cryse.unifystorage.explorer.service.task.DeleteTask;
import org.cryse.unifystorage.explorer.service.task.DownloadTask;
import org.cryse.unifystorage.explorer.utils.BrowserState;
import org.cryse.unifystorage.explorer.utils.CollectionViewState;
import org.cryse.unifystorage.explorer.utils.cache.FileCacheRepository;
import org.cryse.unifystorage.io.comparator.NameFileComparator;
import org.cryse.unifystorage.providers.localstorage.LocalStorageProvider;
import org.cryse.unifystorage.utils.DirectoryInfo;

import java.util.Comparator;
import java.util.Stack;

public class FilesPresenter implements FilesContract.Presenter {
    private final FilesContract.View mFilesView;
    // private int mStorageProviderRecordId = DataContract.CONST_EMPTY_STORAGE_PROVIDER_RECORD_ID;
    // private Credential mCredential;
    private StorageProviderInfo mStorageProviderInfo;

    private StorageProviderRecord mStorageProviderRecord;

    private final RxStorageProvider mRxStorageProvider;
    protected ThreadExecutor mThreadExecutor;
    protected PostExecutionThread mPostExecutionThread;
    protected FileCacheRepository mFileCacheRepository;
    private GetFilesUseCase mGetFilesUseCase;
    private CreateFolderUseCase mCreateFolderUseCase;
    private boolean mFirstLoad = true;
    private boolean mShowHiddenFile = false;
    public DirectoryInfo mDirectory;
    private Comparator<AbstractFile> mFileComparator;
    protected Stack<BrowserState> mBackwardStack = new Stack<>();

    protected FilesPresenter(
            FilesContract.View filesView,
            int storageProviderRecordId,
            Credential credential,
            StorageProvider storageProvider,
            ThreadExecutor threadExecutor,
            PostExecutionThread postExecutionThread,
            FileCacheRepository fileCacheRepository,
            String[] extras
    ) {
        this.mFilesView = filesView;
        this.mRxStorageProvider = new RxStorageProvider(storageProvider);
        this.mStorageProviderInfo = new StorageProviderInfo(storageProviderRecordId, credential, extras);
        this.mStorageProviderRecord = StorageProviderManager.getInstance().loadStorageProviderRecord(storageProviderRecordId);
        this.mThreadExecutor = threadExecutor;
        this.mPostExecutionThread = postExecutionThread;
        this.mFileCacheRepository = fileCacheRepository;
        this.mFileComparator = NameFileComparator.NAME_INSENSITIVE_COMPARATOR;

        this.mGetFilesUseCase = new GetFilesUseCase(mRxStorageProvider, mThreadExecutor, mPostExecutionThread);
        this.mCreateFolderUseCase = new CreateFolderUseCase(mRxStorageProvider, mThreadExecutor, mPostExecutionThread);

        this.mRxStorageProvider.getStorageProvider().setOnTokenRefreshListener(new StorageProvider.OnTokenRefreshListener() {
            @Override
            public void onTokenRefresh(Credential refreshedCredential) {
                mStorageProviderRecord.setCredentialData(refreshedCredential.persist());
                StorageProviderManager.getInstance().updateStorageProviderRecord(mStorageProviderRecord, true);

                FilesPresenter.this.mStorageProviderInfo.setCredential(refreshedCredential);
            }
        });
        this.mFilesView.setPresenter(this);
    }

    @Override
    public boolean showWatchChanges() {
        return isLocalStorage();
    }

    @Override
    public StorageProviderInfo getStorageProviderInfo() {
        return mStorageProviderInfo;
    }

    private boolean isLocalStorage() {
        return mRxStorageProvider.getStorageProvider() instanceof LocalStorageProvider;
    }

    @Override
    public void result(int requestCode, int resultCode) {

    }

    public boolean isAtTopPath() {
        return mBackwardStack.empty();
    }

    @Override
    public void backToParent(String targetPath, CollectionViewState collectionViewState) {
        mBackwardStack.push(new BrowserState(mDirectory, collectionViewState));
        for (int i = 0; i < mBackwardStack.size(); i++) {
            if (mBackwardStack.get(i).directory.directory.getPath().equals(targetPath)) {
                if(isLocalStorage()) {
                    this.mDirectory = mBackwardStack.get(i).directory;
                    loadFiles(mDirectory, true, false, mBackwardStack.get(i).collectionViewState);
                } else {
                    mFilesView.onLeaveDirectory(mDirectory);
                    this.mDirectory = mBackwardStack.get(i).directory;
                    mDirectory.setShowHiddenFiles(mShowHiddenFile, mFileComparator);
                    mFilesView.showFiles(mDirectory, mBackwardStack.get(i).collectionViewState);
                    // mFilesView.onCollectionViewStateRestore(mBackwardStack.get(i).collectionViewState);
                }

                /*mDirectory.setShowHiddenFiles(mShowHiddenFile, mFileComparator);
                mFilesView.showFiles(mDirectory);
                mFilesView.setLoadingIndicator(false);
                mFilesView.onCollectionViewStateRestore(mBackwardStack.get(i).collectionViewState);*/
                break;
            }
        }
    }

    public boolean onBackPressed() {
        if (!mBackwardStack.empty()) {
            BrowserState currentState = mBackwardStack.pop();
            mFilesView.onLeaveDirectory(mDirectory);
            mDirectory = currentState.directory;
            if(isLocalStorage()) {
                mDirectory = currentState.directory;
                loadFiles(mDirectory, true, false, currentState.collectionViewState);
            } else {
                mFilesView.onLeaveDirectory(mDirectory);
                mDirectory.setShowHiddenFiles(mShowHiddenFile, mFileComparator);
                mFilesView.showFiles(mDirectory, currentState.collectionViewState);
                // mFilesView.onCollectionViewStateRestore(currentState.collectionViewState);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public DirectoryInfo getDirectory() {
        return mDirectory;
    }

    @Override
    public void loadFiles(DirectoryInfo directoryInfo, boolean forceUpdate) {
        loadFiles(directoryInfo, forceUpdate || mFirstLoad, true, null);
        mFirstLoad = false;
    }

    @Override
    public void loadFiles(final DirectoryInfo directoryInfo, boolean forceUpdate, final boolean showLoadingUI, final CollectionViewState state) {
        if(!forceUpdate) return;
        if (showLoadingUI/* && ((directoryInfo != null && !directoryInfo.hasMore) || (directoryInfo == null))*/) {
            mFilesView.setLoadingIndicator(true);
        }
        this.mGetFilesUseCase.execute(
                new GetFilesUseCase.RequestValues(directoryInfo),
                new DefaultSubscriber<UseCase.SingleResponseValue<DirectoryInfo>>() {
                    @Override
                    public void onCompleted() {
                        super.onCompleted();
                        mFilesView.setLoadingIndicator(false);
                    }

                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);
                        mFilesView.setLoadingIndicator(false);
                        mDirectory = directoryInfo;
                        mFilesView.showError(mDirectory, e);
                    }

                    @Override
                    public void onNext(UseCase.SingleResponseValue<DirectoryInfo> singleResponseValue) {
                        super.onNext(singleResponseValue);
                        mFilesView.onLeaveDirectory(mDirectory);
                        mDirectory = singleResponseValue.getValue();
                        mDirectory.setShowHiddenFiles(mShowHiddenFile, mFileComparator);
                        mFilesView.showFiles(mDirectory, state);
                    }
                }
        );
    }

    @Override
    public void createFile() {

    }

    @Override
    public void createFolder(RemoteFile parent, String name) {
        RxEventBus.getInstance().sendEvent(
                new NewTaskEvent(
                        new CreateFolderTask(
                                mStorageProviderInfo,
                                parent,
                                name
                        )
                )
        );
    }

    public void deleteFiles(RemoteFile[] files) {
        RxEventBus.getInstance().sendEvent(
                new NewTaskEvent(
                        new DeleteTask(
                                mStorageProviderInfo,
                                false,
                                files
                        )
                )
        );
    }

    public void setShowHiddenFiles(boolean show) {
        this.mShowHiddenFile = show;
        if(mDirectory != null) {
            mDirectory.setShowHiddenFiles(mShowHiddenFile, mFileComparator);
            // mDirectory.notifyChange();
            mFilesView.showFiles(mDirectory, null);
            // if (mDataListener != null) mDataListener.onDirectoryChanged(mDirectory.get());
        }
    }

    @Override
    public void openFileDetails(RemoteFile requestedFile) {

    }

    @Override
    public void onFileClick(RemoteFile file, CollectionViewState collectionViewState) {
        if (file.isDirectory()) {
            mBackwardStack.push(new BrowserState(mDirectory, collectionViewState));
            loadFiles(DirectoryInfo.fromDirectory(file), true);
        } else {
            if(file.needsDownload()) {
                downloadFile(file);
            } else {
                openFile(file);
            }
        }
    }

    private void openFile(RemoteFile file) {
        mFilesView.openFileByPath(file.getPath(), true);
    }

    private void downloadFile(final RemoteFile file) {
        final int token = file.getId().hashCode();
        final String fileName = file.getName();
        final long fileSize = file.size();
        final String localPath = mFileCacheRepository.getFullCachePathForFile(
                mRxStorageProvider.getStorageProviderName(),
                mStorageProviderRecord.getUuid(),
                file
        );

        RxEventBus.getInstance().sendEvent(
                new NewTaskEvent(
                        new DownloadTask(
                                mStorageProviderInfo,
                                file,
                                localPath,
                                false
                        )
                )
        );

        /*RxEventBus.getInstance().sendEvent(
                new StartDownloadEvent(
                        mStorageProviderInfo,
                        token,
                        file,
                        localPath,
                        true
                )
        );*/
        /*final Subscription[] subscription = new Subscription[1];
        final DownloadFileMessage downloadFileMessage = new DownloadFileMessage(file.getId().hashCode(), "", fileName);
        downloadFileMessage.setCurrentSize(0);
        downloadFileMessage.setFileSize(fileSize);
        DialogInterface.OnDismissListener dismissListener = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mDownloadFileUseCase.unsubscribe();
                RxSubscriptionUtils.checkAndUnsubscribe(subscription[0]);
            }
        };
        downloadFileMessage.setOnDismissListener(dismissListener);
        downloadFileMessage.setAction(BasicMessage.MessageAction.CREATE);
        mFilesView.showMessage(downloadFileMessage);*/
        /*mDownloadFileUseCase.execute(new DownloadFileUseCase.RequestValues(file, localPath),
                new DefaultSubscriber<UseCase.SingleResponseValue<InputStream>>() {
                    @Override
                    public void onCompleted() {
                        super.onCompleted();
                        // downloadFileMessage.setAction(BasicMessage.MessageAction.DISMISS);
                        // mFilesView.showMessage(downloadFileMessage);
                        // String uriString = mFileCacheRepository.getUriForFile(new File(localPath));
                        // mFilesView.openFileByUri(uriString, true);
                    }

                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);
                        // downloadFileMessage.setAction(BasicMessage.MessageAction.DISMISS);
                        // mFilesView.showMessage(downloadFileMessage);
                    }

                    @Override
                    public void onNext(final UseCase.SingleResponseValue<InputStream> inputStreamSingleResponseValue) {
                        super.onNext(inputStreamSingleResponseValue);

                        *//*subscription[0] = OperationObserables.downloadFileObservable(file, localPath, inputStreamSingleResponseValue.getValue())
                                .subscribeOn(Schedulers.from(mThreadExecutor))
                                .observeOn(mPostExecutionThread.getScheduler())
                                .subscribe(new DefaultSubscriber<Long>() {
                                    @Override
                                    public void onCompleted() {
                                        super.onCompleted();
                                        downloadFileMessage.setAction(BasicMessage.MessageAction.DISMISS);
                                        mFilesView.showMessage(downloadFileMessage);
                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                        super.onError(e);
                                        downloadFileMessage.setAction(BasicMessage.MessageAction.DISMISS);
                                        mFilesView.showMessage(downloadFileMessage);
                                    }

                                    @Override
                                    public void onNext(Long aLong) {
                                        super.onNext(aLong);
                                        downloadFileMessage.setAction(BasicMessage.MessageAction.UPDATE);
                                        downloadFileMessage.setCurrentSize(aLong);
                                        mFilesView.showMessage(downloadFileMessage);
                                    }
                                });*//*
                    }
                });*/
    }

    @Override
    public void onFileLongClick(RemoteFile file) {

    }

    @Override
    public void start() {
        loadFiles(null, false);
    }

    public static class Builder extends BasePresenter.Builder<FilesContract.Presenter, FilesContract.View> {
        private int providerId;
        private Credential credential;
        private StorageProvider storageProvider;
        private ThreadExecutor threadExecutor;
        private PostExecutionThread postExecutionThread;
        private FileCacheRepository fileCacheRepository;
        private String[] extras;

        public Builder() {

        }

        @Override
        public FilesPresenter build() {
            return new FilesPresenter(
                    view,
                    providerId,
                    credential,
                    storageProvider,
                    threadExecutor,
                    postExecutionThread,
                    fileCacheRepository,
                    extras
            );
        }

        public Builder view(FilesContract.View view) {
            this.view = view;
            return this;
        }

        @Override
        public Builder threadExecutor(ThreadExecutor threadExecutor) {
            this.threadExecutor = threadExecutor;
            return this;
        }

        @Override
        public Builder postExecutionThread(PostExecutionThread postExecutionThread) {
            this.postExecutionThread = postExecutionThread;
            return this;
        }

        public Builder fileCacheRepository(FileCacheRepository fileCacheRepository) {
            this.fileCacheRepository = fileCacheRepository;
            return this;
        }

        public Builder providerId(int providerId) {
            this.providerId = providerId;
            return this;
        }

        public Builder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        public Builder storageProvider(StorageProvider storageProvider) {
            this.storageProvider = storageProvider;
            return this;
        }

        public Builder extras(String[] extras) {
            this.extras = extras;
            return this;
        }
    }
}
