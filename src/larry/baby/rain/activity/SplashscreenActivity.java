package larry.baby.rain.activity;

import larry.baby.rain.R;
import larry.baby.rain.constant.PrefKeys;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;

public class SplashscreenActivity extends Activity {
	private final int REQUEST_CODE_PICK_DIR = 1;

	private Animation endAnimation;

	private Handler endAnimationHandler;
	private Runnable endAnimationRunnable;

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.splashscreen);
		findViewById(R.id.splashlayout);

		endAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
		endAnimation.setFillAfter(true);

		endAnimationHandler = new Handler();
		endAnimationRunnable = new Runnable() {
			@Override
			public void run() {
				findViewById(R.id.splashlayout).startAnimation(endAnimation);
			}
		};

		endAnimation.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				showDirectoryBrowserActivity();
			}
		});

		// set splash window show time
		endAnimationHandler.removeCallbacks(endAnimationRunnable);
		endAnimationHandler.postDelayed(endAnimationRunnable, 2000);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		String selectedFolder = "/sdcard";
		// selected folder
		if (resultCode == RESULT_OK) {
			String newDir = data.getStringExtra(FileBrowserActivity.returnDirectoryParameter);
//			Toast.makeText(this, "Received DIRECTORY path from file browser:\n" + newDir, Toast.LENGTH_LONG).show();

			selectedFolder = newDir;
		}

		// write selected folder into SharedPreferences.
		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putString(PrefKeys.SDCARD_SEARCH_DIRECTORY, selectedFolder);
		editor.commit();

		launchLibraryActivity();
		super.onActivityResult(requestCode, resultCode, data);
	}

	/**
	 * launch {@link LibraryActivity} after selected music folder.
	 */
	private void launchLibraryActivity() {
		LibraryActivity.launch(SplashscreenActivity.this);
		SplashscreenActivity.this.finish();
	}

	/**
	 * If first time start player, show FileBrowserActivity for choosing music folder,
	 * next time, just launch {@link LibraryActivity}.
	 */
	final void showDirectoryBrowserActivity() {
		String sdcardSearchDir = PreferenceManager.getDefaultSharedPreferences(this).getString(PrefKeys.SDCARD_SEARCH_DIRECTORY, null);
		if (sdcardSearchDir == null) {
			Intent fileExploreIntent = new Intent(FileBrowserActivity.INTENT_ACTION_SELECT_DIR, null, this, FileBrowserActivity.class);
			startActivityForResult(fileExploreIntent, REQUEST_CODE_PICK_DIR);
		} else {
//			Toast.makeText(this, "Get search directory from preference:\n" + sdcardSearchDir, Toast.LENGTH_LONG).show();
			launchLibraryActivity();
		}
	}
}