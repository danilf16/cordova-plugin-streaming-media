package com.hutchind.cordova.plugins.streamingmedia;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.StyledPlayerView;

public class SimpleVideoStream extends AppCompatActivity {
	private static final int ENDING_THRESHOLD_MS = 60 * 1000;
	private static final String DEFAULT_LANGUAGE = "en";

	protected StyledPlayerView playerView;
	private ExoPlayer player;
	private ImageButton closeButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(getResourceId("layout", "activity_video"));
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		Bundle b = getIntent().getExtras();
		String mVideoUrl = b != null ? b.getString("mediaUrl") : null;
		if (b == null || mVideoUrl == null) {
			finish();
		}

		player = new ExoPlayer.Builder(getApplicationContext()).build();
		MediaItem mediaItem = MediaItem.fromUri(mVideoUrl);

		if (b != null && b.containsKey("stopAt")) {
			int stopAt = b.getInt("stopAt");

			player.createMessage(((messageType, payload) -> stopVideo()))
				.setLooper(Looper.getMainLooper())
				.setPosition(stopAt)
				.setDeleteAfterDelivery(true)
				.send();
		}

		playerView = findViewById(getResourceId("id", "player_view"));
		playerView.requestFocus();
		playerView.setPlayer(player);

		playerView.setShowPreviousButton(false);
		playerView.setShowNextButton(false);

		playerView.setControllerVisibilityListener((StyledPlayerView.ControllerVisibilityListener) visibility -> {
			if (visibility == View.VISIBLE && closeButton != null) {
				closeButton.setVisibility(View.VISIBLE);
			}

			if (visibility == View.GONE && closeButton != null) {
				closeButton.setVisibility(View.GONE);
			}
		});

		if (b != null && b.containsKey("language")) {
			String lang = b.getString("language");
			if (lang == null) {
				lang = DEFAULT_LANGUAGE;
			}

			player.setTrackSelectionParameters(
					player.getTrackSelectionParameters()
							.buildUpon()
							.setPreferredAudioLanguages(lang, DEFAULT_LANGUAGE)
							.setPreferredTextLanguages(lang, DEFAULT_LANGUAGE)
							.build());
		}

		player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
		player.setMediaItem(mediaItem);

		final boolean[] isAlreadySought = {false};

		player.addListener(new Player.Listener() {
			@Override
			public void onPlaybackStateChanged(int playbackState) {
				if (playbackState == ExoPlayer.STATE_READY && !isAlreadySought[0]) {
					if (b != null && b.containsKey("startFrom")) {
						long position = b.getInt("startFrom", 0) * 1000L;
						long duration = player.getDuration();

						if (duration - position < ENDING_THRESHOLD_MS) {
							position = 0;
						}

						player.seekTo(position);
						isAlreadySought[0] = true;
					}
				}
			}
		});

		player.prepare();
		player.play();
		playerView.hideController();

		closeButton = findViewById(getResourceId("id", "exo_close"));
		closeButton.setOnClickListener(v -> {
			player.stop();

			Intent intent = new Intent();

			if (player.getDuration() - player.getCurrentPosition() < ENDING_THRESHOLD_MS) {
				intent.putExtra("finishAt", 0);
			} else {
				intent.putExtra("finishAt", player.getCurrentPosition());
			}

			setResult(Activity.RESULT_OK, intent);
			finish();
		});
	}

	@Override
	protected void onDestroy() {
		if (player != null) {
			player.stop();
			player.release();
		}

		super.onDestroy();
	}

	private void stopVideo() {
		Intent intent = new Intent();
		intent.putExtra("message", "stopped");

		player.stop();
		setResult(Activity.RESULT_OK, intent);
		finish();
	}

	private int getResourceId(String type, String name) {
		Application app = getApplication();
		String packageName = app.getPackageName();

		return app.getResources().getIdentifier(name, type, packageName);
	}
}
