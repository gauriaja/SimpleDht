package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

public class SimpleDhtActivity extends Activity {
	static final String TAG = SimpleDhtActivity.class.getSimpleName();
	static final String REMOTE_PORT_0 = "11108";
	static final String REMOTE_PORT_1 = "11112";
	static final String REMOTE_PORT_2 = "11116";
	static final String REMOTE_PORT_3 = "11120";
	static final String REMOTE_PORT_END = "11124";
	static final int SERVER_PORT = 10000;
	private static final String JOIN_PORT = "11108";
	private String myPort;
	private Uri buildUri(String scheme, String authority) {
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority(authority);
		uriBuilder.scheme(scheme);
		return uriBuilder.build();
	}
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_dht_main);
        
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button3).setOnClickListener(
                new OnTestClickListener(tv, getContentResolver()));
        findViewById(R.id.button1).setOnClickListener(
        		new LDump(tv,getContentResolver()));
        findViewById(R.id.button2).setOnClickListener(
        		new GDump(tv,getContentResolver()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_simple_dht_main, menu);
        return true;
    }
}
