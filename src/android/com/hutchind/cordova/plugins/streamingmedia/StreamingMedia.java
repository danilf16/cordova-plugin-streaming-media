package com.hutchind.cordova.plugins.streamingmedia;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.content.Intent;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;

public class StreamingMedia extends CordovaPlugin {
	public static final String DEFAULT_LANGUAGE = "en";

	public static final String ACTION_PLAY_AUDIO = "playAudio";
	public static final String ACTION_PLAY_VIDEO = "playVideo";

	private static final int ACTIVITY_CODE_PLAY_MEDIA = 7;

	private CallbackContext callbackContext;

	private static final String TAG = "StreamingMediaPlugin";

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		this.callbackContext = callbackContext;
		JSONObject options = null;

		try {
			options = args.getJSONObject(1);
		} catch (JSONException e) {
			// Developer provided no options. Leave options null.
		}

		if (ACTION_PLAY_AUDIO.equals(action)) {
			return playAudio(args.getString(0), options);
		} else if (ACTION_PLAY_VIDEO.equals(action)) {
			return playVideo(args.getString(0), options);
		} else {
			callbackContext.error("streamingMedia." + action + " is not a supported method.");
			return false;
		}
	}

	private boolean playAudio(String url, JSONObject options) {
		return play(SimpleAudioStream.class, url, options);
	}

	private boolean playVideo(String url, JSONObject options) {
		return play(SimpleVideoStream.class, url, options);
	}

	private boolean play(final Class activityClass, final String url, final JSONObject options) {
		final CordovaInterface cordovaObj = cordova;
		final CordovaPlugin plugin = this;

		cordova.getActivity().runOnUiThread((Runnable) () -> {
			final Intent streamIntent = new Intent(cordovaObj.getActivity().getApplicationContext(), activityClass);
			Bundle extras = new Bundle();
			extras.putString("mediaUrl", url);

			if (options != null) {
				Iterator<String> optKeys = options.keys();
				while (optKeys.hasNext()) {
					try {
						final String optKey = (String)optKeys.next();
						if (options.get(optKey).getClass().equals(String.class)) {
							extras.putString(optKey, (String)options.get(optKey));
							Log.v(TAG, "Added option: " + optKey + " -> " + options.get(optKey));
						} else if (options.get(optKey).getClass().equals(Boolean.class)) {
							extras.putBoolean(optKey, (Boolean)options.get(optKey));
							Log.v(TAG, "Added option: " + optKey + " -> " + options.get(optKey));
						} else if (options.get(optKey).getClass().equals(Integer.class)) {
							extras.putInt(optKey, (int)options.get(optKey));
							Log.v(TAG, "Added option: " + optKey + " -> " + options.get(optKey));
						}

					} catch (JSONException e) {
						Log.e(TAG, "JSONException while trying to read options. Skipping option.");
					}
				}
				streamIntent.putExtras(extras);
			}

			cordovaObj.startActivityForResult(plugin, streamIntent, ACTIVITY_CODE_PLAY_MEDIA);
		});
		return true;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		Log.v(TAG, "onActivityResult: " + requestCode + " " + resultCode);
		super.onActivityResult(requestCode, resultCode, intent);

		if (ACTIVITY_CODE_PLAY_MEDIA == requestCode) {
			if (Activity.RESULT_OK == resultCode) {
				if (intent != null && intent.hasExtra("message")) {
					String message = intent.getStringExtra("message");
					this.callbackContext.success(message);
				} else if (intent != null && intent.hasExtra("finishAt")) {
					long position = intent.getLongExtra("finishAt", -1);

					JSONObject res = new JSONObject();
					try {
						res.put("finishAt", position / 1000);
					} catch (JSONException e) {
						e.printStackTrace();
					}

					callbackContext.success(res);
				} else {
					this.callbackContext.success();
				}
			} else if (Activity.RESULT_CANCELED == resultCode) {
				String errMsg = "Error";
				if (intent != null && intent.hasExtra("message")) {
					errMsg = intent.getStringExtra("message");
				}
				this.callbackContext.error(errMsg);
			}
		}
	}
}
