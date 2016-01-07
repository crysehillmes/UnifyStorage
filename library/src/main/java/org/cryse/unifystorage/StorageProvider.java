package org.cryse.unifystorage;

import android.support.v4.util.Pair;

import org.cryse.unifystorage.credential.Credential;
import org.cryse.unifystorage.io.StreamProgressListener;
import org.cryse.unifystorage.utils.DirectoryInfo;
import org.cryse.unifystorage.utils.ProgressCallback;

import java.io.InputStream;
import java.util.List;

public interface StorageProvider<RF extends RemoteFile, CR extends Credential> {
    String getStorageProviderName();

    RF getRootDirectory() throws StorageException;

    DirectoryInfo<RF, List<RF>> list(RF parent) throws StorageException;

    DirectoryInfo<RF, List<RF>> list() throws StorageException;

    RF createDirectory(RF parent, String name) throws StorageException;

    RF createDirectory(String name) throws StorageException;

    RF createFile(RF parent, String name, InputStream input, ConflictBehavior behavior) throws StorageException;

    RF createFile(RF parent, String name, InputStream input) throws StorageException;

    RF createFile(String name, InputStream input) throws StorageException;

    RF createFile(RF parent, String name, LocalFile file, ConflictBehavior behavior) throws StorageException;

    RF createFile(RF parent, String name, LocalFile file) throws StorageException;

    RF createFile(String name, LocalFile file) throws StorageException;

    boolean exists(RF parent, String name) throws StorageException;

    boolean exists(String name) throws StorageException;

    RF getFile(RF parent, String name) throws StorageException;

    RF getFile(String name) throws StorageException;

    RF getFileById(String id) throws StorageException;

    RF updateFile(RF remote, InputStream input, FileUpdater updater) throws StorageException;

    RF updateFile(RF remote, InputStream input) throws StorageException;

    RF updateFile(RF remote, LocalFile local, FileUpdater updater) throws StorageException;

    RF updateFile(RF remote, LocalFile local) throws StorageException;

    Pair<RF, Boolean> deleteFile(RF file) throws StorageException;

    void copyFile(RF target, RF file, final ProgressCallback callback);

    void moveFile(RF target, RF file, final ProgressCallback callback);

    RF getFileDetail(RF file) throws StorageException;

    RF getFilePermission(RF file) throws StorageException;

    RF updateFilePermission(RF file) throws StorageException;

    StorageUserInfo getUserInfo() throws StorageException;

    StorageUserInfo getUserInfo(boolean forceRefresh) throws StorageException;

    CR getRefreshedCredential();

    RemoteFileDownloader<RF> download(RF file) throws StorageException;

    boolean shouldRefreshCredential();

    HashAlgorithm getHashAlgorithm() throws StorageException;
}
