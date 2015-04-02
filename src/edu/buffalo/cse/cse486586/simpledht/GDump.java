package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class GDump implements OnClickListener {
	private static final String TAG = LDump.class.getName();
	private static final String KEY_FIELD = "key";
	private static final String VALUE_FIELD = "value";

	private final TextView mTextView;
	private final ContentResolver mContentResolver;
	private final Uri mUri;
	private boolean received = false;

	public GDump(TextView _tv, ContentResolver _cr) {
		mTextView = _tv;
		mContentResolver = _cr;
		mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
	}

	private Uri buildUri(String scheme, String authority) {
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority(authority);
		uriBuilder.scheme(scheme);
		return uriBuilder.build();
	}
	
	public void onClick(View v) {
		new Task().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
	
	private class Task extends AsyncTask<Void, String, Void>{

		@Override
		protected Void doInBackground(Void... params) {
			// TODO Auto-generated method stub
			received = testQuery().isEmpty();
			if (!testQuery().isEmpty()) {
				String result = testQuery();
				publishProgress("Query success with number of files \n" + result);
			} else {
				publishProgress("Query fail\n");
			}
			return null;
		}
		protected void onProgressUpdate(String...strings) {
			mTextView.append(strings[0]);
			return;
		}
		private String testQuery(){
			int numRows = 0;
			String fileName;
			String fileValue;
			StringBuilder contentValues = new StringBuilder("");
			try{
				Cursor resultCursor = mContentResolver.query(mUri, null,
						"*", null, null);
				if (resultCursor == null) {
					Log.e(TAG, "Result null");
					throw new Exception();
				}else{
					resultCursor.moveToFirst();
					while(resultCursor.isAfterLast() == false){
						int keyIndex = resultCursor.getColumnIndex(KEY_FIELD);
						int valeuIndex = resultCursor.getColumnIndex(VALUE_FIELD);
						fileName = resultCursor.getString(keyIndex);
						fileValue = resultCursor.getString(valeuIndex);
						contentValues.append(fileName + " " + fileValue + "\n");
						Log.v("LDump",fileName + " " + fileValue);
						resultCursor.moveToNext();
						numRows++;
					}
					resultCursor.close();
					received = true;
				}
			}catch (Exception e){
				System.out.println(e.toString());
				e.printStackTrace();
			}
			return contentValues.toString();
		}
	}
}
