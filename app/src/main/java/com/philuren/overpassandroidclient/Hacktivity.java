package com.philuren.overpassandroidclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;


public class Hacktivity extends ActionBarActivity {

    private static final String TAG = "Hacktivity";

    private WebSocketService mService;
    boolean mBound = false;

    public ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "disconnected from server");
            mService = null;
            mBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Connected to service");
            mService = ((WebSocketService.PushingServiceBinder) service).getService();
            mService.onStartCommand(null, 0, 0);
            mBound = true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hacktivity);
        stopService(WebSocketService.closeIntent(this));
    }

    public void startWebSocketService(View view) {
        TextView textView = (TextView) findViewById(R.id.server_socket_address);
        stopService(WebSocketService.closeIntent(this));
        bindWebSocketService(textView.getText().toString());
    }

    public void onPush(View view) {
        if (mService != null ) {
            try {
                mService.ping();
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindWebSocketService(getResources().getString(R.string.socket_host));
    }

    private void bindWebSocketService(String socketAddress){
        Intent i = WebSocketService.startIntent(this);
        i.putExtra(WebSocketService.STRING_SOCKET_ADDRESS, socketAddress);
        startService(i);
        bindService(i, mConnection, Context.BIND_IMPORTANT);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }
}
