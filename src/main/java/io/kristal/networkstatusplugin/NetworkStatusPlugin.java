package io.kristal.networkstatusplugin;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import org.cobaltians.cobalt.Cobalt;
import org.cobaltians.cobalt.fragments.CobaltFragment;
import org.cobaltians.cobalt.plugin.CobaltAbstractPlugin;
import org.cobaltians.cobalt.plugin.CobaltPluginWebContainer;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

public class NetworkStatusPlugin extends CobaltAbstractPlugin implements NetworkChangeReceiver.NetworkChangeListener
{
	// TAG
	private static final String TAG = NetworkStatusPlugin.class.getSimpleName();

	/***********************************************************************************************
	 *
	 * MEMBERS
	 *
	 **********************************************************************************************/

	private static final String JSPluginName = "networkStatus";

	private static final String JSActionQueryState = "getState";
	private static final String JSActionQueryType = "getType";
	private static final String JSActionStartStatusMonitoring = "startStatusMonitoring";
	private static final String JSActionStopStatusMonitoring = "stopStatusMonitoring";
	private static final String JSActionOnNetworkChanged = "onStatusChanged";

	private static final String kJSState = "state";
	private static final String kJSType = "type";

	private static final String STATE_DISCONNECTED = "disconnected";
	private static final String STATE_CONNECTED = "connected";

	private static final String TYPE_WIFI = "wifi";
	private static final String TYPE_MOBILE = "mobile";
	private static final String TYPE_ETHERNET = "ethernet";
	private static final String TYPE_VPN = "vpn";
	private static final String TYPE_BLUETOOTH = "bluetooth";
	private static final String TYPE_UNKNOWN = "unknown";
	private static final String TYPE_NONE = "none";

	private NetworkChangeReceiver networkChangeReceiver;

	private WeakReference<CobaltFragment> mFragment;


	private static NetworkStatusPlugin sInstance;

	public static CobaltAbstractPlugin getInstance(CobaltPluginWebContainer webContainer)
	{
		if (sInstance == null)
			sInstance = new NetworkStatusPlugin();

		sInstance.addWebContainer(webContainer);

		return sInstance;
	}

	@Override
	public void onMessage(CobaltPluginWebContainer webContainer, JSONObject message)
	{
		try {
			String action = message.getString(Cobalt.kJSAction);
			Context context = webContainer.getActivity();

			mFragment = new WeakReference<>(webContainer.getFragment());

			switch (action)
			{
				case JSActionQueryState:
					sendStateCallback(webContainer, message.getString(Cobalt.kJSCallback), getState(context));
					break;

				case JSActionQueryType:
					sendTypeCallback(webContainer, message.getString(Cobalt.kJSCallback), getType(context));
					break;

				case JSActionStartStatusMonitoring:
					startStatusMonitoring(context);
					break;

				case JSActionStopStatusMonitoring:
					stopStatusMonitoring();
					break;

				default:
					if (Cobalt.DEBUG)
						Log.d(TAG, "onMessage: unknown action " + action);
					break;
			}
		}
		catch (JSONException exception) {
			if (Cobalt.DEBUG)
				Log.d(TAG, "onMessage: action field missing or is not a string or data field is missing or is not an object");

			exception.printStackTrace();
		}
	}

	/***********************************************************************************************
	 *
	 * METHODS
	 *
	 **********************************************************************************************/

	private void sendCallback(CobaltPluginWebContainer webContainer, String callback, String key, String value) {
		try {
			JSONObject data = new JSONObject();
			data.put(key, value);
			webContainer.getFragment().sendCallback(callback, data);
		}
		catch (JSONException exception) {
			exception.printStackTrace();
		}
	}

	private void sendStateCallback(CobaltPluginWebContainer webContainer, String callback, String status) {
		sendCallback(webContainer, callback, kJSState, status);
	}

	private void sendTypeCallback(CobaltPluginWebContainer webContainer, String callback, String type) {
		sendCallback(webContainer, callback, kJSType, type);
	}

	private void sendStatusChangedCallback(String state, String type)
	{
		CobaltFragment fragment = mFragment.get();
		if (fragment != null) {
			try {
				JSONObject data = new JSONObject();
				data.put(kJSState, state);
				data.put(kJSType, type);

				JSONObject message = new JSONObject();
				message.put(Cobalt.kJSType, Cobalt.JSTypePlugin);
				message.put(Cobalt.kJSPluginName, JSPluginName);
				message.put(Cobalt.kJSAction, JSActionOnNetworkChanged);
				message.put(Cobalt.kJSData, data);
				fragment.sendMessage(message);
			}
			catch (JSONException exception) {
				exception.printStackTrace();
			}
		}
	}

	private void startStatusMonitoring(Context context) {
		if (checkNetworkChangePermission(context)) {
			if (networkChangeReceiver != null)
				stopStatusMonitoring();

			networkChangeReceiver = new NetworkChangeReceiver(context, this);
		}
		else if (Cobalt.DEBUG)
			Log.d(TAG, "Cannot start network monitoring: permission CHANGE_NETWORK_STATE denied");
	}

	private void stopStatusMonitoring() {
		if (networkChangeReceiver != null) {
			networkChangeReceiver.remove();
			networkChangeReceiver = null;
		}
	}

	private String getState(Context context)
	{
		if (!checkNetworkStatePermission(context)) {
			if (Cobalt.DEBUG)
				Log.d(TAG, "Cannot get network state: permission ACCESS_NETWORK_STATE denied");

			return STATE_DISCONNECTED;
		}

		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo info = cm.getActiveNetworkInfo();

		if (info != null &&
				(info.getState() == NetworkInfo.State.CONNECTED || info.getState() == NetworkInfo.State.CONNECTING))
			return STATE_CONNECTED;
		else
			return STATE_DISCONNECTED;
	}

	private String getType(Context context)
	{
		if (!checkNetworkStatePermission(context)) {
			if (Cobalt.DEBUG)
				Log.d(TAG, "Cannot get network type: permission ACCESS_NETWORK_STATE denied");

			return TYPE_NONE;
		}

		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo info = cm.getActiveNetworkInfo();

		if (info != null) {
			switch (info.getType())
			{
				case ConnectivityManager.TYPE_WIFI:
					return TYPE_WIFI;

				case ConnectivityManager.TYPE_MOBILE:
					return TYPE_MOBILE;

				case ConnectivityManager.TYPE_BLUETOOTH:
					return TYPE_BLUETOOTH;

				case ConnectivityManager.TYPE_ETHERNET:
					return TYPE_ETHERNET;

				case ConnectivityManager.TYPE_WIMAX:
					return TYPE_MOBILE;

				case ConnectivityManager.TYPE_MOBILE_DUN:
					return TYPE_MOBILE;

				case ConnectivityManager.TYPE_VPN:
					return TYPE_VPN;

				default:
					return TYPE_UNKNOWN;
			}
		}
		else {
			return TYPE_NONE;
		}
	}

	@Override
	public void onNetworkChange(Context context) {
		sendStatusChangedCallback(getState(context), getType(context));
	}

	/***********************************************************************************************
	 *
	 * PERMISSIONS
	 *
	 **********************************************************************************************/

	private boolean checkPermission(Context context, String permission)
	{
		return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
	}

	private boolean checkNetworkStatePermission(Context context) {
		return checkPermission(context, Manifest.permission.ACCESS_NETWORK_STATE);
	}

	private boolean checkNetworkChangePermission(Context context) {
		return checkPermission(context, Manifest.permission.CHANGE_NETWORK_STATE);
	}
}
