package com.hutchind.cordova.plugins.streamingmedia;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.mediarouter.app.MediaRouteButton;

import com.google.android.gms.cast.framework.CastButtonFactory;

public class SimpleVideoStream extends AppCompatActivity {
	private static final int ENDING_THRESHOLD_MS = 60 * 1000;
	private static final String DEFAULT_LANGUAGE = "en";

	protected PlayerView playerView;
	private ExoPlayer player;
	private ImageButton closeButton;
	private MediaRouteButton mrButton;

	@Override
	@OptIn(markerClass = UnstableApi.class)
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

		playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
			if (visibility == View.VISIBLE) {
				if (closeButton != null) {
					closeButton.setVisibility(View.VISIBLE);
				}

				if (mrButton != null) {
					mrButton.setVisibility(View.VISIBLE);
				}
			}

			if (visibility == View.GONE) {
				if (closeButton != null) {
					closeButton.setVisibility(View.GONE);
				}

				if (mrButton != null) {
					mrButton.setVisibility(View.GONE);
				}
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

		mrButton = findViewById(getResourceId("id", "exo_cast_button"));
		CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), mrButton);
		mrButton.setRemoteIndicatorDrawable(ContextCompat.getDrawable(this, getResourceId("drawable", "mr_button_dark")));
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
