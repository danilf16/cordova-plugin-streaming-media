package com.hutchind.cordova.plugins.streamingmedia;

import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

public class SimpleVideoStream extends AppCompatActivity {
	protected PlayerView playerView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(getResourceId("layout", "activity_video"));
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		Bundle b = getIntent().getExtras();
		String mVideoUrl = b != null ? b.getString("mediaUrl") : null;
		if (b == null || mVideoUrl == null) {
			finish();
		}

		SimpleExoPlayer player = new SimpleExoPlayer.Builder(getApplicationContext()).build();
		MediaItem mediaItem = MediaItem.fromUri(mVideoUrl);

		playerView = findViewById(getResourceId("id", "player_view"));
		playerView.requestFocus();
		playerView.setPlayer(player);

		player.setMediaItem(mediaItem);
		player.prepare();
		player.play();

		hideSystemUI();

		ImageButton imageButton = findViewById(getResourceId("id", "exo_close"));
		imageButton.setOnClickListener(v -> {
			player.stop();
			finish();
		});
	}

	private void hideSystemUI() {
		View decorView = getWindow().getDecorView();
		decorView.setSystemUiVisibility(
			View.SYSTEM_UI_FLAG_IMMERSIVE
			| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
			| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
			| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
			| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
			| View.SYSTEM_UI_FLAG_FULLSCREEN);
	}

	private int getResourceId(String type, String name) {
		Application app = getApplication();
		String packageName = app.getPackageName();

		return app.getResources().getIdentifier(name, type, packageName);
	}
}
