package com.hutchind.cordova.plugins.streamingmedia;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import androidx.mediarouter.app.MediaRouteButton;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;

@UnstableApi
public class SimpleVideoStream extends AppCompatActivity {
	protected PlayerView playerView;
	private ImageButton closeButton;
	private MediaRouteButton mrButton;

	private PlayerManager playerManager;
	private CastContext castContext;

	@Override
	@OptIn(markerClass = UnstableApi.class)
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			castContext = CastContext.getSharedInstance(this);
		} catch (RuntimeException e) {
			Log.d("MOM_Cast", e.toString());
		}

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

		MediaItem mediaItem = new MediaItem.Builder()
				.setUri(mVideoUrl)
				.setMimeType(MimeTypes.APPLICATION_M3U8)
				.build();

		playerView = findViewById(getResourceId("id", "player_view"));
		playerManager = new PlayerManager(this, playerView, castContext, mediaItem, getLanguage(b), getStartFrom(b));

		playerView.requestFocus();
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

		playerView.hideController();

		closeButton = findViewById(getResourceId("id", "exo_close"));
		closeButton.setOnClickListener(v -> {
			long finishAt = playerManager.stop();

			Intent intent = new Intent();
			intent.putExtra("finishAt", finishAt);

			setResult(Activity.RESULT_OK, intent);
			finish();
		});

		mrButton = findViewById(getResourceId("id", "exo_cast_button"));
		CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), mrButton);
		mrButton.setRemoteIndicatorDrawable(ContextCompat.getDrawable(this, getResourceId("drawable", "mr_button_dark")));
	}

	@Override
	protected void onDestroy() {
		if (castContext == null) {
			return;
		}

		playerManager.release();
		playerManager = null;

		super.onDestroy();
	}

	private int getResourceId(String type, String name) {
		Application app = getApplication();
		String packageName = app.getPackageName();

		return app.getResources().getIdentifier(name, type, packageName);
	}

	private String getLanguage(Bundle bundle) {
		String lang = StreamingMedia.DEFAULT_LANGUAGE;

		if (bundle != null && bundle.containsKey("language")) {
			lang = bundle.getString("language");
		}

		return lang != null ? lang : StreamingMedia.DEFAULT_LANGUAGE;
	}

	private long getStartFrom(Bundle bundle) {
		if (bundle != null && bundle.containsKey("startFrom")) {
			return bundle.getInt("startFrom", 0) * 1000L;
		}

		return 0;
	}
}
