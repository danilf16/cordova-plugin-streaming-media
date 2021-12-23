package com.hutchind.cordova.plugins.streamingmedia;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.StyledPlayerView;

public class SimpleVideoStream extends AppCompatActivity {
	protected StyledPlayerView playerView;
	private ExoPlayer player;
	private ImageButton closeButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(getResourceId("layout", "activity_video"));
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);

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

		playerView.setControllerVisibilityListener(visibility -> {
			if (visibility == View.VISIBLE && closeButton != null) {
				closeButton.setVisibility(View.VISIBLE);
			}

			if (visibility == View.GONE && closeButton != null) {
				closeButton.setVisibility(View.GONE);
			}
		});

		player.setTrackSelectionParameters(
				player.getTrackSelectionParameters()
						.buildUpon()
						.setPreferredAudioLanguage("en")
						.setPreferredTextLanguage("en")
						.build());

		player.setMediaItem(mediaItem);
		player.prepare();
		player.play();

		playerView.hideController();

		closeButton = findViewById(getResourceId("id", "exo_close"));
		closeButton.setOnClickListener(v -> {
			player.stop();
			setResult(Activity.RESULT_OK);
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
