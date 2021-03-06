package larry.baby.rain.adapter;

import java.util.Arrays;

import larry.baby.rain.R;
import larry.baby.rain.activity.LibraryActivity;
import larry.baby.rain.common.util.CompatHoneycomb;
import larry.baby.rain.common.util.MediaUtils;
import larry.baby.rain.common.util.Playlist;
import larry.baby.rain.constant.PrefKeys;
import larry.baby.rain.entity.Limiter;
import larry.baby.rain.service.PlaybackService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * PagerAdapter that manages the library media ListViews.
 */
public class LibraryPagerAdapter extends PagerAdapter implements Handler.Callback, ViewPager.OnPageChangeListener, View.OnCreateContextMenuListener, AdapterView.OnItemClickListener {
	/**
	 * The number of unique list types. The number of visible lists may be
	 * smaller.
	 */
//	public static final int MAX_ADAPTER_COUNT = 6;
	/**
	 * The human-readable title for each list. The positions correspond to the
	 * MediaUtils ids, so e.g. TITLES[MediaUtils.TYPE_SONG] = R.string.songs
	 */
	public static final int[] TITLES = { R.string.artists, R.string.albums, R.string.songs, R.string.playlists, R.string.genres, R.string.files };
	/**
	 * Default tab order.
	 */
//	public static final int[] DEFAULT_ORDER = { MediaUtils.TYPE_ARTIST, MediaUtils.TYPE_ALBUM, MediaUtils.TYPE_SONG, MediaUtils.TYPE_PLAYLIST, MediaUtils.TYPE_GENRE, MediaUtils.TYPE_FILE };
	private Cursor playListCursor;
	/**
	 * The user-chosen tab order.
	 */
	public int[] mTabOrder;
	/**
	 * The number of visible tabs.
	 */
	private int mTabCount;
	/**
	 * The ListView for each adapter. Each index corresponds to that list's
	 * MediaUtils id.
	 */
	private final ListView[] mLists;
	/**
	 * The adapters. Each index corresponds to that adapter's MediaUtils id.
	 */
	public LibraryAdapter[] mAdapters;
	/**
	 * Whether the adapter corresponding to each index has stale data.
	 */
	private final boolean[] mRequeryNeeded;
	/**
	 * The artist adapter instance, also stored at
	 * mAdapters[MediaUtils.TYPE_ARTIST].
	 */
	private MediaAdapter mArtistAdapter;
	/**
	 * The album adapter instance, also stored at
	 * mAdapters[MediaUtils.TYPE_ALBUM].
	 */
	private MediaAdapter mAlbumAdapter;
	/**
	 * The song adapter instance, also stored at
	 * mAdapters[MediaUtils.TYPE_SONG].
	 */
	private MediaAdapter mSongAdapter;
	/**
	 * The playlist adapter instance, also stored at
	 * mAdapters[MediaUtils.TYPE_PLAYLIST].
	 */
	MediaAdapter mPlaylistAdapter;
	/**
	 * The genre adapter instance, also stored at
	 * mAdapters[MediaUtils.TYPE_GENRE].
	 */
	private MediaAdapter mGenreAdapter;
	/**
	 * The file adapter instance, also stored at
	 * mAdapters[MediaUtils.TYPE_FILE].
	 */
	private FileSystemAdapter mFilesAdapter;
	/**
	 * The adapter of the currently visible list.
	 */
	private LibraryAdapter mCurrentAdapter;
	/**
	 * The index of the current page.
	 */
	private int mCurrentPage;
	/**
	 * A limiter that should be set when the album adapter is created.
	 */
	private Limiter mPendingAlbumLimiter;
	/**
	 * A limiter that should be set when the song adapter is created.
	 */
	private Limiter mPendingSongLimiter;
	/**
	 * A limiter that should be set when the files adapter is created.
	 */
	private Limiter mPendingFileLimiter;
	/**
	 * List positions stored in the saved state, or null if none were stored.
	 */
	private int[] mSavedPositions;
	/**
	 * The LibraryActivity that owns this adapter. The adapter will be notified
	 * of changes in the current page.
	 */
	private final LibraryActivity mActivity;
	/**
	 * A Handler running on the UI thread.
	 */
	private final Handler mUiHandler;
	/**
	 * A Handler running on a worker thread.
	 */
	private final Handler mWorkerHandler;
	/**
	 * The text to be displayed in the first row of the artist, album, and song
	 * limiters.
	 */
	private String mHeaderText;
	private TextView mArtistHeader;
	private TextView mAlbumHeader;
	private TextView mSongHeader;
	/**
	 * The current filter text, or null if none.
	 */
	private String mFilter;
	/**
	 * The position of the songs page, or -1 if it is hidden.
	 */
	public int mSongsPosition = -1;
	/**
	 * The position of the albums page, or -1 if it is hidden.
	 */
	public int mAlbumsPosition = -1;
	/**
	 * The position of the artists page, or -1 if it is hidden.
	 */
	public int mArtistsPosition = -1;
	/**
	 * The position of the genres page, or -1 if it is hidden.
	 */
	public int mGenresPosition = -1;

	private final ContentObserver mPlaylistObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange) {
			if (mPlaylistAdapter != null) {
				postRequestRequery(mPlaylistAdapter);
			}
		}
	};

	/**
	 * Create the LibraryPager.
	 *
	 * @param activity
	 *            The LibraryActivity that will own this adapter. The activity
	 *            will receive callbacks from the ListViews.
	 * @param workerLooper
	 *            A Looper running on a worker thread.
	 */
	public LibraryPagerAdapter(LibraryActivity activity, Looper workerLooper) {
		mActivity = activity;
		mUiHandler = new Handler(this);
		mWorkerHandler = new Handler(workerLooper, this);
		mCurrentPage = -1;
		activity.getContentResolver().registerContentObserver(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, true, mPlaylistObserver);

		playListCursor = Playlist.queryPlaylists(mActivity.getContentResolver());
		mTabCount = playListCursor.getCount();
		mLists = new ListView[mTabCount];
		mRequeryNeeded = new boolean[mTabCount];
		mAdapters = new LibraryAdapter[mTabCount];
	}

	/**
	 * Load the tab order from SharedPreferences.
	 *
	 * @return True if order has changed.
	 */
//	public boolean loadTabOrder() {
//		if(playListCursor == null){
//			return false;
//		}
//		String in = PlaybackService.getSettings(mActivity).getString(PrefKeys.TAB_ORDER, null);
//		int playListCount = playListCursor.getCount();
//		int[] order;
//		int count;
//		if (in == null || in.length() != playListCount) {
//			order = DEFAULT_ORDER;
//			count = playListCount;
//		} else {
//			char[] chars = in.toCharArray();
//			order = new int[playListCount];
//			count = 0;
//			for (int i = 0; i != playListCount; ++i) {
//				char v = chars[i];
//				if (v >= 128) {
//					v -= 128;
//					if (v >= playListCount) {
//						// invalid media type; use default order
//						order = DEFAULT_ORDER;
//						count = playListCount;
//						break;
//					}
//					order[count++] = v;
//				}
//			}
//		}
//
//		if (count != mTabCount || !Arrays.equals(order, mTabOrder)) {
//			mTabOrder = order;
//			mTabCount = count;
//			notifyDataSetChanged();
//			computeExpansions();
//			return true;
//		}
//
//		return false;
//	}

	/**
	 * Determines whether adapters should be expandable from the visibility of
	 * the adapters each expands to. Also updates
	 * mSongsPosition/mAlbumsPositions.
	 */
//	public void computeExpansions() {
//		int[] order = mTabOrder;
//		int songsPosition = -1;
//		int albumsPosition = -1;
//		int artistsPosition = -1;
//		int genresPosition = -1;
//		for (int i = mTabCount; --i != -1;) {
//			switch (order[i]) {
//			case MediaUtils.TYPE_ALBUM:
//				albumsPosition = i;
//				break;
//			case MediaUtils.TYPE_SONG:
//				songsPosition = i;
//				break;
//			case MediaUtils.TYPE_ARTIST:
//				artistsPosition = i;
//				break;
//			case MediaUtils.TYPE_GENRE:
//				genresPosition = i;
//				break;
//			}
//		}
//
//		if (mArtistAdapter != null)
//			mArtistAdapter.setExpandable(songsPosition != -1 || albumsPosition != -1);
//		if (mAlbumAdapter != null)
//			mAlbumAdapter.setExpandable(songsPosition != -1);
//		if (mGenreAdapter != null)
//			mGenreAdapter.setExpandable(songsPosition != -1);
//
//		mSongsPosition = songsPosition;
//		mAlbumsPosition = albumsPosition;
//		mArtistsPosition = artistsPosition;
//		mGenresPosition = genresPosition;
//	}

	@Override
	public Object instantiateItem(ViewGroup container, int position) {
		ListView view = mLists[position];

		if (view == null) {
			LibraryActivity activity = mActivity;
			LayoutInflater inflater = activity.getLayoutInflater();
			LibraryAdapter adapter;
			TextView header = null;

			Cursor cursor = playListCursor;
			cursor.moveToPosition(position);
			long id = cursor.getLong(0);
			String name = cursor.getString(1);

			mPendingSongLimiter = null;
			mSongHeader = header = (TextView) inflater.inflate(R.layout.library_row, null);
			adapter = mPlaylistAdapter = new MediaAdapter(activity, position, id, null);

			view = (ListView) inflater.inflate(R.layout.listview, null);
			view.setOnCreateContextMenuListener(this);
			view.setOnItemClickListener(this);
			view.setTag(position);
			if (header != null) {
				header.setText(name);
				header.setTag(id);
				view.addHeaderView(header);
			}
			view.setAdapter(adapter);

			enableFastScroll(view);
			adapter.setFilter(mFilter);

			mAdapters[position] = adapter;
			mLists[position] = view;
			mRequeryNeeded[position] = true;
		}

		requeryIfNeeded(position);
		container.addView(view);
		return view;
	}

	@Override
	public int getItemPosition(Object item) {
		int position = (Integer) ((ListView) item).getTag();
		if(position >= 0){
			return position;
		}
		return POSITION_NONE;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object) {
		container.removeView((View) object);
	}

	@Override
	public CharSequence getPageTitle(int position) {
		return mActivity.getResources().getText(TITLES[mTabOrder[position]]);
	}

	@Override
	public int getCount() {
		return mTabCount;
	}

	@Override
	public boolean isViewFromObject(View view, Object object) {
		return view == object;
	}

	@Override
	public void setPrimaryItem(ViewGroup container, int position, Object object) {
//		int type = mTabOrder[position];
		LibraryAdapter adapter = mAdapters[position];
		if (position != mCurrentPage || adapter != mCurrentAdapter) {
			requeryIfNeeded(position);
			mCurrentAdapter = adapter;
			mCurrentPage = position;
			mActivity.onPageChanged(position, adapter);
		}
	}

	@Override
	public void restoreState(Parcelable state, ClassLoader loader) {
		Bundle in = (Bundle) state;
		mPendingAlbumLimiter = (Limiter) in.getSerializable("limiter_albums");
		mPendingSongLimiter = (Limiter) in.getSerializable("limiter_songs");
		mPendingFileLimiter = (Limiter) in.getSerializable("limiter_files");
		mSavedPositions = in.getIntArray("pos");
	}

	@Override
	public Parcelable saveState() {
		Bundle out = new Bundle(10);
		if (mAlbumAdapter != null)
			out.putSerializable("limiter_albums", mAlbumAdapter.getLimiter());
		if (mSongAdapter != null)
			out.putSerializable("limiter_songs", mSongAdapter.getLimiter());
		if (mFilesAdapter != null)
			out.putSerializable("limiter_files", mFilesAdapter.getLimiter());

		int[] savedPositions = new int[mTabCount];
		ListView[] lists = mLists;
		for (int i = mTabCount; --i != -1;) {
			if (lists[i] != null) {
				savedPositions[i] = lists[i].getFirstVisiblePosition();
			}
		}
		out.putIntArray("pos", savedPositions);
		return out;
	}

	/**
	 * Sets the text to be displayed in the first row of the artist, album, and
	 * song lists.
	 */
	public void setHeaderText(String text) {
		if (mArtistHeader != null)
			mArtistHeader.setText(text);
		if (mAlbumHeader != null)
			mAlbumHeader.setText(text);
		if (mSongHeader != null)
			mSongHeader.setText(text);
		mHeaderText = text;
	}

	/**
	 * Clear a limiter.
	 *
	 * @param type
	 *            Which type of limiter to clear.
	 */
	public void clearLimiter(int type) {
		if (type == MediaUtils.TYPE_FILE) {
			if (mFilesAdapter == null) {
				mPendingFileLimiter = null;
			} else {
				mFilesAdapter.setLimiter(null);
				requestRequery(mFilesAdapter);
			}
		} else {
			if (mAlbumAdapter == null) {
				mPendingAlbumLimiter = null;
			} else {
				mAlbumAdapter.setLimiter(null);
				loadSortOrder(mAlbumAdapter);
				requestRequery(mAlbumAdapter);
			}
			if (mSongAdapter == null) {
				mPendingSongLimiter = null;
			} else {
				mSongAdapter.setLimiter(null);
				loadSortOrder(mSongAdapter);
				requestRequery(mSongAdapter);
			}
		}
	}

	/**
	 * Update the adapters with the given limiter.
	 *
	 * @param limiter
	 *            The limiter to set.
	 * @return The tab type that should be switched to to expand the row.
	 */
	public int setLimiter(Limiter limiter) {
		int tab;

		switch (limiter.type) {
		case MediaUtils.TYPE_ALBUM:
			if (mSongAdapter == null) {
				mPendingSongLimiter = limiter;
			} else {
				mSongAdapter.setLimiter(limiter);
				loadSortOrder(mSongAdapter);
				requestRequery(mSongAdapter);
			}
			tab = mSongsPosition;
			break;
		case MediaUtils.TYPE_ARTIST:
			if (mAlbumAdapter == null) {
				mPendingAlbumLimiter = limiter;
			} else {
				mAlbumAdapter.setLimiter(limiter);
				loadSortOrder(mAlbumAdapter);
				requestRequery(mAlbumAdapter);
			}
			if (mSongAdapter == null) {
				mPendingSongLimiter = limiter;
			} else {
				mSongAdapter.setLimiter(limiter);
				loadSortOrder(mSongAdapter);
				requestRequery(mSongAdapter);
			}
			tab = mAlbumsPosition;
			if (tab == -1)
				tab = mSongsPosition;
			break;
		case MediaUtils.TYPE_GENRE:
			if (mAlbumAdapter == null) {
				mPendingAlbumLimiter = limiter;
			} else {
				mAlbumAdapter.setLimiter(limiter);
				loadSortOrder(mAlbumAdapter);
				requestRequery(mAlbumAdapter);
			}
			if (mSongAdapter == null) {
				mPendingSongLimiter = null;
			} else {
				mSongAdapter.setLimiter(limiter);
				loadSortOrder(mSongAdapter);
				requestRequery(mSongAdapter);
			}
			tab = mSongsPosition;
			break;
		case MediaUtils.TYPE_FILE:
			if (mFilesAdapter == null) {
				mPendingFileLimiter = limiter;
			} else {
				mFilesAdapter.setLimiter(limiter);
				requestRequery(mFilesAdapter);
			}
			tab = -1;
			break;
		default:
			throw new IllegalArgumentException("Unsupported limiter type: " + limiter.type);
		}

		return tab;
	}

	/**
	 * Returns the limiter set on the current adapter or null if there is none.
	 */
	public Limiter getCurrentLimiter() {
		LibraryAdapter current = mCurrentAdapter;
		if (current == null)
			return null;
		return current.getLimiter();
	}

	/**
	 * Run on query on the adapter passed in obj.
	 *
	 * Runs on worker thread.
	 */
	private static final int MSG_RUN_QUERY = 0;
	/**
	 * Save the sort mode for the adapter passed in obj.
	 *
	 * Runs on worker thread.
	 */
	private static final int MSG_SAVE_SORT = 1;
	/**
	 * Call {@link LibraryPagerAdapter#requestRequery(LibraryAdapter)} on the
	 * adapter passed in obj.
	 *
	 * Runs on worker thread.
	 */
	private static final int MSG_REQUEST_REQUERY = 2;
	/**
	 * Commit the cursor passed in obj to the adapter at the index passed in
	 * arg1.
	 *
	 * Runs on UI thread.
	 */
	private static final int MSG_COMMIT_QUERY = 3;

	@Override
	public boolean handleMessage(Message message) {
		switch (message.what) {
		case MSG_RUN_QUERY: {
			LibraryAdapter adapter = (LibraryAdapter) message.obj;
			int index = adapter.getPageIndex();
			Handler handler = mUiHandler;
			handler.sendMessage(handler.obtainMessage(MSG_COMMIT_QUERY, index, 0, adapter.query()));
			break;
		}
		case MSG_COMMIT_QUERY: {
			int index = message.arg1;
			mAdapters[index].commitQuery(message.obj);
			int pos;
			if (mSavedPositions == null) {
				pos = 0;
			} else {
				pos = mSavedPositions[index];
				mSavedPositions[index] = 0;
			}
			mLists[index].setSelection(pos);
			break;
		}
		case MSG_SAVE_SORT: {
			MediaAdapter adapter = (MediaAdapter) message.obj;
			SharedPreferences.Editor editor = PlaybackService.getSettings(mActivity).edit();
			editor.putInt(String.format("sort_%d_%d", adapter.getMediaType(), adapter.getLimiterType()), adapter.getSortMode());
			editor.commit();
			break;
		}
		case MSG_REQUEST_REQUERY:
			requestRequery((LibraryAdapter) message.obj);
			break;
		default:
			return false;
		}

		return true;
	}

	/**
	 * Requery the given adapter. If it is the current adapter, requery
	 * immediately. Otherwise, mark the adapter as needing a requery and requery
	 * when its tab is selected.
	 *
	 * Must be called on the UI thread.
	 */
	public void requestRequery(LibraryAdapter adapter) {
		if (adapter == mCurrentAdapter) {
			postRunQuery(adapter);
		} else {
			mRequeryNeeded[adapter.getMediaType()] = true;
			// Clear the data for non-visible adapters (so we don't show the old
			// data briefly when we later switch to that adapter)
			adapter.clear();
		}
	}

	/**
	 * Call {@link LibraryPagerAdapter#requestRequery(LibraryAdapter)} on the UI
	 * thread.
	 *
	 * @param adapter
	 *            The adapter, passed to requestRequery.
	 */
	public void postRequestRequery(LibraryAdapter adapter) {
		Handler handler = mUiHandler;
		handler.sendMessage(handler.obtainMessage(MSG_REQUEST_REQUERY, adapter));
	}

	/**
	 * Schedule a query to be run for the given adapter on the worker thread.
	 *
	 * @param adapter
	 *            The adapter to run the query for.
	 */
	private void postRunQuery(LibraryAdapter adapter) {
		mRequeryNeeded[adapter.getPageIndex()] = false;
		Handler handler = mWorkerHandler;
		handler.removeMessages(MSG_RUN_QUERY, adapter);
		handler.sendMessage(handler.obtainMessage(MSG_RUN_QUERY, adapter));
	}

	/**
	 * Requery the adapter of the given type if it exists and needs a requery.
	 *
	 * @param type
	 *            One of MediaUtils.TYPE_*
	 */
	private void requeryIfNeeded(int type) {
		LibraryAdapter adapter = mAdapters[type];
		if (adapter != null && mRequeryNeeded[type]) {
			postRunQuery(adapter);
		}
	}

	/**
	 * Invalidate the data for all adapters.
	 */
	public void invalidateData() {
		for (LibraryAdapter adapter : mAdapters) {
			if (adapter != null) {
				postRequestRequery(adapter);
			}
		}
	}

	/**
	 * Set the saved sort mode for the given adapter. The adapter should be
	 * re-queried after calling this.
	 *
	 * @param adapter
	 *            The adapter to load for.
	 */
	public void loadSortOrder(MediaAdapter adapter) {
		String key = String.format("sort_%d_%d", adapter.getMediaType(), adapter.getLimiterType());
		int def = adapter.getDefaultSortMode();
		int sort = PlaybackService.getSettings(mActivity).getInt(key, def);
		adapter.setSortMode(sort);
	}

	/**
	 * Set the sort mode for the current adapter. Current adapter must be a
	 * MediaAdapter. Saves this sort mode to preferences and updates the list
	 * associated with the adapter to display the new sort mode.
	 *
	 * @param mode
	 *            The sort mode. See {@link MediaAdapter#setSortMode(int)} for
	 *            details.
	 */
	public void setSortMode(int mode) {
		MediaAdapter adapter = (MediaAdapter) mCurrentAdapter;
		if (mode == adapter.getSortMode())
			return;

		adapter.setSortMode(mode);
		requestRequery(adapter);

		// Force a new FastScroller to be created so the scroll sections
		// are updated.
		ListView view = mLists[mTabOrder[mCurrentPage]];
		view.setFastScrollEnabled(false);
		enableFastScroll(view);

		Handler handler = mWorkerHandler;
		handler.sendMessage(handler.obtainMessage(MSG_SAVE_SORT, adapter));
	}

	/**
	 * Set a new filter on all the adapters.
	 */
	public void setFilter(String text) {
		if (text.length() == 0)
			text = null;

		mFilter = text;
		for (LibraryAdapter adapter : mAdapters) {
			if (adapter != null) {
				adapter.setFilter(text);
				requestRequery(adapter);
			}
		}
	}

	@Override
	public void onPageScrollStateChanged(int state) {
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
	}

	@Override
	public void onPageSelected(int position) {
		// onPageSelected and setPrimaryItem are called in similar cases, and it
		// would be nice to use just one of them, but each has caveats:
		// - onPageSelected isn't called when the ViewPager is first
		// initialized
		// - setPrimaryItem isn't called until scrolling is complete, which
		// makes tab bar and limiter updates look bad
		// So we use both.
		setPrimaryItem(null, position, null);
	}

	/**
	 * Creates the row data used by LibraryActivity.
	 */
	private static Intent createHeaderIntent(View header) {
		int type = (Integer) header.getTag();
		Intent intent = new Intent();
		intent.putExtra(LibraryAdapter.DATA_ID, LibraryAdapter.HEADER_ID);
		intent.putExtra(LibraryAdapter.DATA_TYPE, type);
		return intent;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		View targetView = info.targetView;
		Intent intent = info.id == -1 ? createHeaderIntent(targetView) : mCurrentAdapter.createData(targetView);
		mActivity.onCreateContextMenu(menu, intent);
	}

	@Override
	public void onItemClick(AdapterView<?> list, View view, int position, long id) {
		Intent intent = id == -1 ? createHeaderIntent(view) : mCurrentAdapter.createData(view);
		mActivity.onItemClicked(intent);
	}

	/**
	 * Enables FastScroller on the given list, ensuring it is always visible and
	 * suppresses the match drag position feature in newer versions of Android.
	 *
	 * @param list
	 *            The list to enable.
	 */
	private void enableFastScroll(ListView list) {
		mActivity.mFakeTarget = true;
		list.setFastScrollEnabled(true);
		CompatHoneycomb.setFastScrollAlwaysVisible(list);
		mActivity.mFakeTarget = false;
	}
}
