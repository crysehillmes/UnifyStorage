package org.cryse.unifystorage.explorer.ui.common;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.cryse.unifystorage.AbstractFile;
import org.cryse.unifystorage.RemoteFile;
import org.cryse.unifystorage.StorageProvider;
import org.cryse.unifystorage.credential.Credential;
import org.cryse.unifystorage.explorer.DataContract;
import org.cryse.unifystorage.explorer.R;
import org.cryse.unifystorage.explorer.ui.MainActivity;
import org.cryse.unifystorage.explorer.ui.adapter.FileAdapter;
import org.cryse.unifystorage.utils.sort.FileSorter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import butterknife.Bind;
import butterknife.ButterKnife;

public abstract class StorageProviderFragment<
        RF extends RemoteFile,
        SP extends StorageProvider<RF>
        > extends AbstractFragment implements  FileAdapter.OnFileClickListener<RF> {
    private AtomicBoolean mDoubleBackPressedOnce = new AtomicBoolean(false);
    private Handler mHandler = new Handler();

    private final Runnable mBackPressdRunnable = new Runnable() {
        @Override
        public void run() {
            mDoubleBackPressedOnce.set(false);
        }
    };

    protected SP mStorageProvider;
    protected FileAdapter<RF> mCollectionAdapter;
    protected Credential mCredential;
    protected RF mCurrentDirectory;
    protected Stack<BrowserState<RF>> mBackStack = new Stack<>();

    @Bind(R.id.toolbar)
    Toolbar mToolbar;
    List<RF> mFiles = new ArrayList<RF>();
    Comparator<AbstractFile> mFileComparator;

    @Bind(R.id.fragment_storageprovider_recyclerview_files)
    RecyclerView mCollectionView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();

        if (bundle != null)
            mCredential = bundle.getParcelable(DataContract.ARG_CREDENTIAL);
        mFileComparator = FileSorter.FileNameComparator.getInstance(true);
        mCollectionAdapter = new FileAdapter<>(getActivity(), mFiles);
        mCollectionAdapter.setOnFileClickListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View fragmentView = inflater.inflate(R.layout.fragment_storageprovider, container, false);
        ButterKnife.bind(this, fragmentView);
        setupToolbar();
        setupRecyclerView();
        loadDefaultDirectory();
        return fragmentView;
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
                        if(!mBackStack.empty()) {
                            if (!StorageProviderFragment.this.mDoubleBackPressedOnce.get()) {
                                BrowserState<RF> currentState = mBackStack.pop();
                                mCurrentDirectory = currentState.currentDirectory;
                                //loadDirectory(mCurrentDirectory);
                                mCollectionAdapter.replaceWith(currentState.files);
                                LinearLayoutManager manager = (LinearLayoutManager) mCollectionView.getLayoutManager();
                                manager.scrollToPositionWithOffset(currentState.scrollPosition, (int) currentState.scrollOffset);
                                StorageProviderFragment.this.mDoubleBackPressedOnce.set(true);
                                mHandler.postDelayed(mBackPressdRunnable, 400);

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
            actionBar.setHomeAsUpIndicator(R.drawable.ic_action_drawer_menu);
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
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (mHandler != null) { mHandler.removeCallbacks(mBackPressdRunnable); }
    }

    private void setupRecyclerView() {
        mCollectionView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mCollectionView.setHasFixedSize(true);
        mCollectionAdapter.replaceWith(mFiles);
        mCollectionView.setAdapter(mCollectionAdapter);
    }

    protected void loadDefaultDirectory() {
        mCurrentDirectory = mStorageProvider.getRootDirectory();
        loadDirectory(mCurrentDirectory, false);
    }

    protected abstract SP buildStorageProvider(Credential credential);

    @Override
    public void onFileClick(View view, int position, RF file) {
        if(file.isDirectory()) {
            loadDirectory(file, true);
        } else {
            openFile(file);
        }
    }

    @Override
    public void onFileLongClick(View view, int position, RF file) {

    }

    protected void openFile(RF file) {

    }

    protected void loadDirectory(RF file, boolean saveStack) {
        if(saveStack) {
            mBackStack.push(saveBrowserState());
        }
        mCurrentDirectory = file;

        List<RF> files = mStorageProvider.list(file);
        handleFileSort(files);
        handleHiddenFile(files);
        mCollectionAdapter.replaceWith(files);
    }

    protected void handleFileSort(List<RF> files) {
        Collections.sort(files, mFileComparator);
    }

    protected void handleHiddenFile(List<RF> files) {
        for(Iterator<RF> iterator = files.iterator(); iterator.hasNext(); ) {
            RF file = iterator.next();
            if(file.getName().startsWith("."))
                iterator.remove();
        }
    }

    private BrowserState<RF> saveBrowserState() {
        LinearLayoutManager manager = (LinearLayoutManager) mCollectionView.getLayoutManager();
        int firstItem = manager.findFirstVisibleItemPosition();
        View firstItemView = manager.findViewByPosition(firstItem);
        float topOffset = firstItemView.getTop();
        return new BrowserState<>(
                mCurrentDirectory,
                new ArrayList<>(mFiles),
                firstItem,
                topOffset
        );
    }

    private static class BrowserState<RF> {
        public RF currentDirectory;
        public List<RF> files;
        public int scrollPosition;
        public float scrollOffset;

        public BrowserState() {

        }

        public BrowserState(RF currentDirectory, List<RF> files, int scrollPosition, float scrollOffset) {
            this.files = files;
            this.currentDirectory = currentDirectory;
            this.scrollPosition = scrollPosition;
            this.scrollOffset = scrollOffset;
        }
    }
}
