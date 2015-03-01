package com.philuren.overpassandroidclient;

import java.net.URI;
import java.util.Iterator;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.philuren.overpassandroidclient.websockets.WebSocketClient;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Erik Westenius 23060489 on 28/02/15.
 * Contact: erik.westenius@sonymobile.com
 */

public class WebSocketService extends Service {

    public static final String ACTION_PING = "com.philuren.overpass.ACTION_PING";
    public static final String ACTION_CONNECT = "com.philuren.overpass.ACTION_CONNECT";
    public static final String ACTION_SHUT_DOWN = "com.philuren.overpass.ACTION_SHUT_DOWN";
    public static final String STRING_SOCKET_ADDRESS = "com.philuren.overpass.STRING_SOCKET_ADDRESS";

    private static final String TAG = "PushingService";
    private static final String INT_USER_ID = "com.philuren.overpass.INT_USER_ID";

    private WebSocketClient mClient;
    private final IBinder mBinder = new PushingServiceBinder();
    private boolean mShutDown = false;
    private Handler mHandler;
    private int mUserId = -1;

    public static Intent startIntent(Context context) {
        Intent i = new Intent(context, WebSocketService.class);
        i.setAction(ACTION_CONNECT);
        i.putExtra(INT_USER_ID, 1337);
        return i;
    }

    public static Intent pingIntent(Context context) {
        Intent i = new Intent(context, WebSocketService.class);
        i.setAction(ACTION_PING);
        return i;
    }

    public static Intent closeIntent(Context context) {
        Intent i = new Intent(context, WebSocketService.class);
        i.setAction(ACTION_SHUT_DOWN);
        return i;
    }

    public void ping() throws JSONException {
        JSONObject request = new JSONObject();
        request.put("action", "ping");
        request.put("id", mUserId);
        send(request.toString());
    }

    public void send(JSONObject request) {
        send(request.toString());
    }

    private void send(String message) {
        if (mClient != null) {
            if (mClient.isConnected())
                mClient.send(message);
            else
                mClient.connect(); //todo: cue actions
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "OnBind!");
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler();

        Log.d(TAG, "Creating Service " + this.toString());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying Service " + this.toString());
        if (mClient != null) mClient.disconnect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        WakeLock wakelock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EECS780 Service");
        wakelock.acquire();
        if (intent != null) Log.i(TAG, intent.toUri(0));
        mShutDown = false;
        if (mClient == null) {
            if (intent.getAction().equals(ACTION_CONNECT)) {
                String socketAddress = intent.getStringExtra(STRING_SOCKET_ADDRESS);
                mClient = new WebSocketClient(URI.create(socketAddress), new WebSocketClient.Listener() {
                    @Override
                    public void onConnect() {
                        Log.d(TAG, "onConnect");
                    }

                    @Override
                    public void onMessage(final String message) {
                        Log.d(TAG, "onMessage: " + message);
                        PowerManager.WakeLock wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EECS780 Service");
                        wakeLock.acquire();
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(WebSocketService.this, "message: " + message, Toast.LENGTH_SHORT).show();
                            }
                        });
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    parseRequest(getApplicationContext(), new JSONObject(message));
                                } catch (JSONException e) {
                                    Log.e(TAG, e.getMessage());
                                    Toast.makeText(WebSocketService.this, "JSON error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        wakeLock.release();
                    }


                    @Override
                    public void onMessage(byte[] data) {
                        //ignore
                    }

                    @Override
                    public void onDisconnect(int code, String reason) {
                        Log.d(TAG, String.format("onDisconnect Code: %d Reason: %s", code, reason));
                        if (!mShutDown) {
                            startService(startIntent(WebSocketService.this));
                        } else {
                            stopSelf();
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.d(TAG, "Client onError: " + error);
                        startService(startIntent(WebSocketService.this));
                    }
                }, null);
            }
        }

        if (!mClient.isConnected()) {
            mClient.connect();
        }

        if (intent != null) {
            if (ACTION_CONNECT.equals(intent.getAction())) {
                mUserId = intent.getIntExtra(INT_USER_ID, -1);
            } else if (ACTION_SHUT_DOWN.equals(intent.getAction())) {
                mShutDown = true;
                if (mClient.isConnected()) mClient.disconnect();
            }
        }

        if (intent == null || !intent.getAction().equals(ACTION_SHUT_DOWN)) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            PendingIntent operation = PendingIntent.getService(this, 0, WebSocketService.pingIntent(this), PendingIntent.FLAG_NO_CREATE);
            if (operation == null) {
                operation = PendingIntent.getService(this, 0, WebSocketService.pingIntent(this), PendingIntent.FLAG_UPDATE_CURRENT);
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AlarmManager.INTERVAL_HALF_HOUR, operation);
            }
        }

        wakelock.release();
        return START_STICKY;
    }

    private void parseRequest(Context context, JSONObject request) {
        Iterator<String> keys = request.keys();
        String key;
        try {
            while (keys.hasNext()) {
                key = keys.next();
                if (key.equals("url")) {
                    Log.d(TAG, "SHOULD HANDLE URL: " + request.getString(key));
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(request.getString(key)));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getApplication().startActivity(i);
                } else if (key.equals("toast")) {
                    Toast.makeText(WebSocketService.this, "TOASTING: " + request.getString(key), Toast.LENGTH_SHORT).show();
                } else if (key.equals("notify")) {
                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(this)
                                    .setSmallIcon(R.drawable.ic_launcher)
                                    .setContentTitle("Overpass")
                                    .setContentText(request.getString(key));
                    NotificationManager notificationManager =
                            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    notificationManager.notify(1337, mBuilder.build());
                } else if (key.equals("location")) {
                    if (request.getInt("id") != mUserId) {
                        Toast.makeText(context, "Should respond with location...", Toast.LENGTH_SHORT).show();
                        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                        Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        double longitude = location.getLongitude();
                        double latitude = location.getLatitude();

                        JSONObject response = new JSONObject();
                        response.put("id", mUserId);
                        response.put("cmd", "location");
                        response.put("location", "" + latitude + "," + longitude);
                        send(response);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public class PushingServiceBinder extends Binder {
        WebSocketService getService() {
            return WebSocketService.this;
        }
    }
}
