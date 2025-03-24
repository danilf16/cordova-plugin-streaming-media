package com.hutchind.cordova.plugins.streamingmedia;

import android.content.Context;
import android.view.KeyEvent;
import androidx.annotation.OptIn;
import androidx.media3.cast.CastPlayer;
import androidx.media3.cast.SessionAvailabilityListener;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;
import com.google.android.gms.cast.framework.CastContext;

@UnstableApi
class PlayerManager implements Player.Listener, SessionAvailabilityListener {
	private static final int ENDING_THRESHOLD_MS = 60 * 1000;

	private final PlayerView playerView;
	private final Player localPlayer;
	private final CastPlayer castPlayer;

	private Player currentPlayer;
	private final MediaItem mediaItem;

	private boolean isAlreadySought = false;
	private long startFrom;

	/**
	 * Creates a new manager for {@link ExoPlayer} and {@link CastPlayer}.
	 *
	 * @param context A {@link Context}.
	 * @param playerView The {@link PlayerView} for playback.
	 * @param castContext The {@link CastContext}.
	 */
	public PlayerManager(Context context, PlayerView playerView, CastContext castContext, MediaItem mediaItem, String preferredLanguage, long startFrom) {
		this.playerView = playerView;
		this.mediaItem = mediaItem;
		this.startFrom = startFrom;

		localPlayer = new ExoPlayer.Builder(context)
				.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
				.build();
		localPlayer.addListener(this);
		setPlayerPreferredLanguage(localPlayer, preferredLanguage);

		castPlayer = new CastPlayer(castContext);
		castPlayer.addListener(this);
		castPlayer.setSessionAvailabilityListener(this);
		setPlayerPreferredLanguage(castPlayer, preferredLanguage);

		setCurrentPlayer(castPlayer.isCastSessionAvailable() ? castPlayer : localPlayer);
	}

	// Queue manipulation methods.
	public long stop() {
		if (castPlayer != null) {
			castPlayer.stop();
		}

		if (localPlayer != null) {
			localPlayer.stop();

			long duration = localPlayer.getDuration();
			long position = localPlayer.getCurrentPosition();

			return duration - position < ENDING_THRESHOLD_MS ? 0 : position;
		}

		return 0;
	}

	/**
	 * Dispatches a given {@link KeyEvent} to the corresponding view of the current player.
	 *
	 * @param event The {@link KeyEvent}.
	 * @return Whether the event was handled by the target view.
	 */
	public boolean dispatchKeyEvent(KeyEvent event) {
		return playerView.dispatchKeyEvent(event);
	}

	/** Releases the manager and the players that it holds. */
	public void release() {
		castPlayer.setSessionAvailabilityListener(null);
		castPlayer.release();
		playerView.setPlayer(null);
		localPlayer.release();
	}

	// Player.Listener implementation.
	@Override
	public void onPlaybackStateChanged(@Player.State int playbackState) {
		if (playbackState == ExoPlayer.STATE_READY && !isAlreadySought) {
			long duration = localPlayer.getDuration();

			if (duration - startFrom < ENDING_THRESHOLD_MS) {
				startFrom = 0;
			}

			localPlayer.seekTo(startFrom);
			isAlreadySought = true;
		}
	}

	@Override
	public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {}

	@Override
	public void onTimelineChanged(Timeline timeline, int reason) {}

	@Override
	public void onTracksChanged(Tracks tracks) {}

	// CastPlayer.SessionAvailabilityListener implementation.
	@Override
	public void onCastSessionAvailable() {
		setCurrentPlayer(castPlayer);
	}

	@Override
	public void onCastSessionUnavailable() {
		setCurrentPlayer(localPlayer);
	}

	// Internal methods.
	private void setPlayerPreferredLanguage(Player player, String preferredLanguage) {
		player.setTrackSelectionParameters(
				player.getTrackSelectionParameters().buildUpon()
						.setPreferredAudioLanguages(preferredLanguage, StreamingMedia.DEFAULT_LANGUAGE)
						.setPreferredTextLanguages(preferredLanguage, StreamingMedia.DEFAULT_LANGUAGE)
						.build()
		);
	}

	@OptIn(markerClass = UnstableApi.class)
	private void setCurrentPlayer(Player currentPlayer) {
		if (this.currentPlayer == currentPlayer) {
			return;
		}

		playerView.setPlayer(currentPlayer);
		playerView.setControllerHideOnTouch(currentPlayer == localPlayer);
		if (currentPlayer == castPlayer) {
			playerView.setControllerShowTimeoutMs(0);
			playerView.showController();
		} else { // currentPlayer == localPlayer
			playerView.setControllerShowTimeoutMs(PlayerControlView.DEFAULT_SHOW_TIMEOUT_MS);
			playerView.setDefaultArtwork(null);
		}

		// Player state management.
		long playbackPositionMs = C.TIME_UNSET;
		boolean playWhenReady = false;

		Player previousPlayer = this.currentPlayer;
		if (previousPlayer != null) {
			// Save state from the previous player.
			int playbackState = previousPlayer.getPlaybackState();
			if (playbackState != Player.STATE_ENDED) {
				playbackPositionMs = previousPlayer.getCurrentPosition();
				playWhenReady = previousPlayer.getPlayWhenReady();
			}

			previousPlayer.stop();
			previousPlayer.clearMediaItems();
		}

		this.currentPlayer = currentPlayer;

		currentPlayer.setMediaItem(mediaItem, playbackPositionMs);
		currentPlayer.setPlayWhenReady(playWhenReady);
		currentPlayer.prepare();
	}
}
