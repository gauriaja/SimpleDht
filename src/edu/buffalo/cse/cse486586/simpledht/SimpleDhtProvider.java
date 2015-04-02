package edu.buffalo.cse.cse486586.simpledht;

import java.net.ServerSocket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {
	static final String TAG = SimpleDhtActivity.class.getSimpleName();
	static final String REMOTE_PORT_0 = "11108";
	static final String REMOTE_PORT_1 = "11112";
	static final String REMOTE_PORT_2 = "11116";
	static final String REMOTE_PORT_3 = "11120";
	static final String REMOTE_PORT_END = "11124";
	static final String KEY_FIELD = "key";
	static final String VALUE_FIELD = "value";
	static final int SERVER_PORT = 10000;
	private static final String JOIN_PORT = "11108";
	private String myPort;
	private String myChordId;
	private String succChordId;
	private String succIPAddress;
	private static Uri mUri;
	private String globalQueryResult = null;
	private String singleQueryResult = null;
	
	public CountDownLatch latch;
	
	private Uri buildUri(String scheme, String authority) {
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.authority(authority);
		uriBuilder.scheme(scheme);
		return uriBuilder.build();
	}

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
    	int mDeletedRows = 0;
    	if(myPort.equals(succIPAddress)){
    		if(selection.equals("*") || selection.equals("@")){
    			//Delete all the files
    			deleteMyFiles();
    		}else{
    			if(getContext().deleteFile(selection)){
    				mDeletedRows = 1;
    				Log.v(myPort,"File " + selection + " deleted");
    			}else{
    				Log.v(myPort,"Files not deleted");
    			}
    		}
    	}else{
    		if(selection.equals("@")){
    			deleteMyFiles();
    		}else if(selection.equals("*")){
    			sendDeleteAllFileMsg();
    		}else{
    			String fileId = null;
				try{
					fileId = genHash(selection);
				}catch(Exception e){
					Log.v(myPort,e.toString());
				}
    			if(myChordId.compareTo(succChordId) > 0){
    				if(myChordId.compareTo(fileId) < 0){
    					//Delete File From successor
    					sendDeleteFileMsg(selection);
    				}else if(myChordId.compareTo(fileId) > 0 && succChordId.compareTo(fileId) > 0){
    					//Delete File From successor
    					sendDeleteFileMsg(selection);
    				}else{
    					//Forward Delete to my successor
    					forwardDeleteMsg(selection);
    				}
    			}else if(myChordId.compareTo(fileId) < 0 && succChordId.compareTo(fileId) > 0){
    				//Delete File from successor
    				sendDeleteFileMsg(selection);
    			}else{
    				forwardDeleteMsg(selection);
    			}
    		}
    	}
        return mDeletedRows;
    }

    private void forwardDeleteMsg(String selection) {
		// TODO Auto-generated method stub
		String deleteMsg = new String("forwardDeleteFile" + "$" + myPort + "$" + succIPAddress + "$" + selection);
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteMsg,myPort);
	}

	private void sendDeleteFileMsg(String selection) {
		// TODO Auto-generated method stub
		String deleteMsg = new String("deleteFile" + "$" + myPort + "$" + succIPAddress + "$" + selection);
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteMsg, myPort);
	}

	private void sendDeleteAllFileMsg() {
		// TODO Auto-generated method stub
    	deleteMyFiles();
		String deleteMsg = new String("deleteAllFiles" + "$" + myPort + "$" + succIPAddress);
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteMsg, myPort);
	}

	private void deleteMyFiles() {
		// TODO Auto-generated method stub
    	int mDeletedRows = 0;
    	String files[] = getContext().fileList();
		for(int i = 0 ;i < files.length;i++){
			if(getContext().deleteFile(files[i])){
				mDeletedRows++;
				Log.v(myPort, "File " + files[i] + " deleted");
			}else{
				Log.v(myPort, "Delete Failed");
			}
		}
		Log.v(myPort,"All Files deleted");
	}

	@Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
    	String fileName = values.getAsString("key");
    	String value = values.getAsString("value") + "\n";
    	try{
    		String fileId = genHash(fileName);
    		handleInsert(fileId, fileName, value);
    	}catch (Exception e){
    		Log.v(myPort,e.toString());
    	}
        return null;
    }

    private void handleInsert(String fileId, String fileName, String value) {
		// TODO Auto-generated method stub
    	//I m the only node present in the system
		if(myChordId.equals(succChordId)){
			writeToMyContentProvider(fileName,value);
		}else if(myChordId.compareTo(succChordId) > 0){
			//Add the end of the chord
			if(myChordId.compareTo(fileId) < 0 && succChordId.compareTo(fileId) < 0){
				//map to the successor - send successor msg to successor
				sendInsertMsg(succIPAddress,fileName,value);
			}else if(myChordId.compareTo(fileId) > 0 && succChordId.compareTo(fileId) > 0){
				sendInsertMsg(succIPAddress,fileName,value);
			}else{
				//thus forward it to the smallest node
				Log.v(myPort,"Corner Case Received " + fileId);
				forwardInsertMsg(succIPAddress,fileId,fileName,value);
			}
		}else{
			if(myChordId.compareTo(fileId) < 0 && succChordId.compareTo(fileId) > 0){
				sendInsertMsg(succIPAddress,fileName,value);
			}else{
				forwardInsertMsg(succIPAddress, fileId, fileName, value);
			}
		}
	}

	private void forwardInsertMsg(String succIPAddress2, String fileId,
			String fileName, String value) {
		// TODO Auto-generated method stub
		String forwardInsertMsg = new String("forwardInsert" + "$" + succIPAddress + 
				"$" + fileId + "$" + fileName + "$" + value);
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, forwardInsertMsg,myPort);
	}

	private void writeToMyContentProvider(String fileName, String value) {
		// TODO Auto-generated method stub
		try{
			FileOutputStream fOut = getContext().openFileOutput(fileName, Context.MODE_PRIVATE);
			fOut.write(value.getBytes());
			fOut.close();
			Log.v(myPort,  "File added to my content provider " + fileName + " " + value);
		}catch (Exception e){
			System.out.println(e.toString());
		}
	}

	private void sendInsertMsg(String succIPAddress2, String fileName,
			String value) {
		// TODO Auto-generated method stub
		String insertMsg = new String("insert" + "$" + succIPAddress2 + "$" + fileName + "$" + value);
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertMsg, myPort);
	}

	@Override
    public boolean onCreate() {
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		myPort = String.valueOf((Integer.parseInt(portStr) * 2));
		mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
		Log.v(TAG, portStr);
		try {
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
			//While creating the application on AVD, it must send a join message to the avd - 11108
			if(!portStr.equals("5554")){
				try{
					myChordId = new String(genHash(portStr));
					Log.v(myPort,myChordId);
					String joinMessage = new String("join" + "$" + myPort + "$" + portStr + "$" + myChordId);
					new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, joinMessage, myPort);
				}catch (Exception e){
					try{
						myChordId = new String(genHash(portStr));
						succChordId = new String(genHash(portStr));
						succIPAddress = new String(myPort);
						Log.v(myPort,myChordId);
						Log.v(TAG, "Node Created\n");
						return false;
					}catch (Exception newE){
						Log.v(TAG + portStr, newE.toString());
					}
				}finally{
					try{
						myChordId = new String(genHash(portStr));
						succChordId = new String(genHash(portStr));
						succIPAddress = new String(myPort);
						Log.v(myPort,myChordId);
						Log.v(TAG, "Node Created\n");
						return false;
					}catch (Exception newE){
						Log.v(TAG + portStr, newE.toString());
					}
				}
			}
			if(portStr.equals("5554")){
				//Set the predecessor value to its own ID
				try{
					myChordId = new String(genHash(portStr));
					succChordId = new String(genHash(portStr));
					succIPAddress = new String(myPort);
					Log.v(myPort,myChordId);
					Log.v(TAG, "Ring Initialized\n");
				}catch (Exception e){
					Log.v(TAG + portStr, e.toString());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG, "Can't create a ServerSocket");
			return false;
		}
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // TODO Auto-generated method stub
    	String columns[] = {"key","value"};
    	String files[] = getContext().fileList();
    	String key = "";
    	String value = "";
    	StringBuilder fileValues = new StringBuilder("");
    	String element;
    	MatrixCursor mCursor = new MatrixCursor(columns);
    	if(!myPort.equals(succIPAddress)){
    		try{
    			if(selection.equals("@")){
    				if(files.length > 0){
    					for(int i = 0; i < files.length; i++){
    						InputStreamReader in = new InputStreamReader(getContext().openFileInput(files[i]));
    						BufferedReader reader = new BufferedReader(in);
    						String line = null;
    						while((line = reader.readLine()) != null){
    							value = new String(line);
    						}
    						in.close();
    						String fields[] = {files[i],value};
    						mCursor.addRow(fields);
    					}
    				}else{
    					Log.v(myPort,"My Files have been deleted");
    				}
    			}else if(selection.equals("*")){
    				sendGlobalQueryThread();
    				if(globalQueryResult.equals("allFilesDeleted")){
    					Log.v(myPort,"All Files have been deleted");
    				}
    				else{
    					StringTokenizer keys = new StringTokenizer(globalQueryResult,"^");
    					while(keys.hasMoreTokens()){
    						element = keys.nextToken();
    						StringTokenizer values = new StringTokenizer(element,",");
    						key = values.nextToken();
    						value = values.nextToken();
    						String fields[] = {key,value};
    						mCursor.addRow(fields);
    					}
    				}
    			}else{
    				Log.v(myPort, "Query for " + selection);
    				String fileHash = null;
    				try{
    					fileHash = genHash(selection);
    				}catch(Exception e){
    					Log.v(myPort,e.toString());
    				}
    				if(myChordId.compareTo(succChordId) > 0){
    					if(myChordId.compareTo(fileHash) < 0){
    						//Fetch file from successor
    						sendfetchFile(selection);
    						StringTokenizer keys = new StringTokenizer(singleQueryResult,",");
    						String fileName;
    						String fileValue;
    						while(keys.hasMoreTokens()){
    							fileName = keys.nextToken();
    							fileValue = keys.nextToken();
    							String fields[] = {fileName,fileValue};
    							mCursor.addRow(fields);
    							Log.v(myPort, "FileFound");
    						}
    					}else if(myChordId.compareTo(fileHash) > 0 && succChordId.compareTo(fileHash) > 0){
    						//Fetch file from successor
    						sendfetchFile(selection);
    						StringTokenizer keys = new StringTokenizer(singleQueryResult,",");
    						String fileName;
    						String fileValue;
    						while(keys.hasMoreTokens()){
    							fileName = keys.nextToken();
    							fileValue = keys.nextToken();
    							String fields[] = {fileName,fileValue};
    							mCursor.addRow(fields);
    							Log.v(myPort, "FileFound");
    						}
    					}else{
    						//Forward file query
    						fowardFileQuery(selection);
    						StringTokenizer keys = new StringTokenizer(singleQueryResult,",");
    						String fileName;
    						String fileValue;
    						while(keys.hasMoreTokens()){
    							fileName = keys.nextToken();
    							fileValue = keys.nextToken();
    							String fields[] = {fileName,fileValue};
    							mCursor.addRow(fields);
    						}
    						Log.v(myPort,"File to be Searched");
    					}
    				}else if(myChordId.compareTo(fileHash) < 0 && succChordId.compareTo(fileHash) > 0){
    					//Fetch File from Successor
    					sendfetchFile(selection);
						StringTokenizer keys = new StringTokenizer(singleQueryResult,",");
						String fileName;
						String fileValue;
						while(keys.hasMoreTokens()){
							fileName = keys.nextToken();
							fileValue = keys.nextToken();
							String fields[] = {fileName,fileValue};
							mCursor.addRow(fields);
						}
    				}else{
    					//Forward File query
    					fowardFileQuery(selection);
    					StringTokenizer keys = new StringTokenizer(singleQueryResult,",");
						String fileName;
						String fileValue;
						while(keys.hasMoreTokens()){
							fileName = keys.nextToken();
							fileValue = keys.nextToken();
							String fields[] = {fileName,fileValue};
							mCursor.addRow(fields);
						}
    				}
    			}
    		}catch (Exception e){
    			Log.v(selection, "File not found\n");
    		}
    	}else{
    		if(selection.equals("@") || selection.equals("*")){
    			try{
    				if(files.length > 0){
    					for(int i = 0; i < files.length; i++){
    						InputStreamReader in = new InputStreamReader(getContext().openFileInput(files[i]));
    						BufferedReader reader = new BufferedReader(in);
    						String line = null;
    						while((line = reader.readLine()) != null){
    							value = new String(line);
    						}
    						in.close();
    						String fields[] = {files[i],value};
    						mCursor.addRow(fields);
    					}
    				}else{
    					Log.v(myPort,"All Files Have been deleted");
    				}
    			}catch( Exception IO){
    				Log.v(myPort,IO.toString());
    			}
    		}else{
    			try{
    				InputStreamReader in = new InputStreamReader(getContext().openFileInput(selection));
    				BufferedReader reader = new BufferedReader(in);
    				String line = null;
    				while((line = reader.readLine()) != null){
    					fileValues.append(line);
    				}
    				in.close();
    				String fields[] = {selection,fileValues.toString()};
    				mCursor.addRow(fields);
    			}catch(Exception e){
    				Log.v(myPort, e.toString() + "Error Here");
    			}
    		}
    	}
    	return mCursor;
    }
    
    private void fowardFileQuery(String selection) {
		// TODO Auto-generated method stub
    	final String fileName = selection;
    	Thread test = new Thread(new Runnable(){
			String msgToSend = new String("fileForward" + "$" + myPort + "$" + fileName);
			StringTokenizer msgType = new StringTokenizer(msgToSend,"$");
			String msg = msgType.nextToken();
			String myAddress = msgType.nextToken();
			public void run(){
				try{
					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(succIPAddress));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					latch = new CountDownLatch(1);
					latch.await();
				}catch(Exception IO){
					Log.v(myPort,IO.toString());
				}
				Log.v(myPort,"Forward File Query for " + fileName + " " + succIPAddress );
			}
		});
		test.start();
		
		try {
			test.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	private void sendfetchFile(String selection) {
		// TODO Auto-generated method stub
    	final String fileName = selection;
    	Log.v(myPort,"Running the Thread");
		Thread test = new Thread(new Runnable(){
			String msgToSend = new String("fileFetch" + "$" + myPort + "$" + succIPAddress + "$" + fileName);
			StringTokenizer msgType = new StringTokenizer(msgToSend,"$");
			String msg = msgType.nextToken();
			String myAddress = msgType.nextToken();
			String address = msgType.nextToken();
			public void run(){
				try{
					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(address));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					latch = new CountDownLatch(1);
					latch.await();
				}catch(Exception IO){
					Log.v(myPort,IO.toString());
				}
				Log.v(myPort,"Send File " + fileName + " " + address);
			}
		});
		test.start();
		
		try {
			test.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void sendGlobalQueryThread(){
    	Thread test = new Thread(new Runnable(){
    		String msgToSend = new String("globalQuery2" + "$" + myPort + "$" + succIPAddress);
			StringTokenizer msgType = new StringTokenizer(msgToSend,"$");
			String msg = msgType.nextToken();
			String myAddress = msgType.nextToken();
			String address = msgType.nextToken();
    		public void run(){
				try{
					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(address));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					latch = new CountDownLatch(1);
					latch.await();
				}catch(Exception IO){
					Log.v(myPort,IO.toString());
				}
				Log.v(myPort,"Global Querying " + address );
    		}
    	});
    	test.start();
    	
    	try {
			test.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
	@Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
    
    private class ServerTask extends AsyncTask<ServerSocket, String, String> {

		@Override
		protected String doInBackground(ServerSocket... sockets) {
			ServerSocket serverSocket = sockets[0];
			Socket socket;
			String result = null;
			try{
				while(true){
					socket = serverSocket.accept();
					BufferedReader inputBuffer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					StringBuilder storeAllMessages = new StringBuilder();
					String inputMessage;
					while((inputMessage = inputBuffer.readLine()) != null){
						storeAllMessages.append(inputMessage);
					}
					publishProgress(storeAllMessages.toString());
					result = globalQueryResult;
				}
			}catch(IOException e){
				System.out.println(e);
			}
			return result;
		}
		protected void onProgressUpdate(String...strings) {
			String rawMsg = strings[0].trim();
			StringTokenizer rawMsgTokens = new StringTokenizer(rawMsg,"$");
			String msgType = rawMsgTokens.nextToken();
			String result = new String("");
			if(msgType.equals("join")){
				String portAddress = rawMsgTokens.nextToken();
				String portString = rawMsgTokens.nextToken();
				String portChordId = rawMsgTokens.nextToken();
				handleNodeJoins(portAddress,portString,portChordId);
				Log.v(myPort, "Join Message Received from " + portAddress);
			}
			if(msgType.equals("Joined")){
				String portAddress = rawMsgTokens.nextToken();;
				String successor = rawMsgTokens.nextToken();
				String successorId = rawMsgTokens.nextToken();
				handleJoinedMsg(successor,successorId);
				Log.v(myPort, "Inserted in the ring " + " and successor " + successor);
			}
			if(msgType.equals("forwardJoin")){
				Log.v(myPort, "Received Forward Join");
				String myAddress = rawMsgTokens.nextToken();
				String newNodeIPAddress = rawMsgTokens.nextToken();
				String newNodePortStr = rawMsgTokens.nextToken();
				String newNodeChordId = rawMsgTokens.nextToken();
				handleNodeJoins(newNodeIPAddress, newNodePortStr, newNodeChordId);
			}
			if(msgType.equals("update")){
				String myAddress = rawMsgTokens.nextToken();
				String updateSuccessorIPAddress = rawMsgTokens.nextToken();
				String updateSuccessorChordId = rawMsgTokens.nextToken();
				handleUpdateMsg(updateSuccessorIPAddress, updateSuccessorChordId);
				Log.v(myAddress, "New Successor + " + updateSuccessorIPAddress);
			}
			if(msgType.equals("insert")){
				String myAddress = rawMsgTokens.nextToken();
				String fileName = rawMsgTokens.nextToken();
				String fileValue = rawMsgTokens.nextToken();
				writeToMyContentProvider(fileName,fileValue);
			}
			if(msgType.equals("forwardInsert")){
				String myAddress = rawMsgTokens.nextToken();
				String fileId = rawMsgTokens.nextToken();
				String fileName = rawMsgTokens.nextToken();
				String fileValue = rawMsgTokens.nextToken();
				handleInsert(fileId,fileName,fileValue);
			}
			if(msgType.equals("globalQuery2")){
				String senderAddress = rawMsgTokens.nextToken();
				if(myPort.equals(senderAddress)){
					Log.v(myPort,"Query Message Delivered to self");
				}else{
					String myAddress = rawMsgTokens.nextToken();
					String msgToQuery = convertValuesToString();
					Log.v(myPort,"Global Query from Thread Received");
					forwardGlobalQuery2(senderAddress,msgToQuery,succIPAddress);
				}
			}
			if(msgType.equals("forwardQuery2")){
				Log.v(myPort,"forwardQuery2 message " + rawMsg);
				try{
					String senderAddress = rawMsgTokens.nextToken();
					if(myPort.equals(senderAddress)){
						String myAddress = rawMsgTokens.nextToken();
						if(rawMsgTokens.hasMoreTokens()){
							String allMsgs = rawMsgTokens.nextToken();
							if(allMsgs.equals("%")){
								//Empty Values
								String myValues = convertValuesToString();
								if(myValues.equals("%")){
									//Nothing in my content provider
									globalQueryResult = "allFilesDeleted";
									result = decryptMsg(globalQueryResult);
									Log.v(myPort, "Query * after delete");
								}else{
									globalQueryResult = myValues;
									result = decryptMsg(globalQueryResult);
								}
							}else{
								//Not all values are empty
								String myValues = convertValuesToString();
								if(myValues.equals("%")){
									//If my Values are empty
									globalQueryResult = allMsgs;
									result = decryptMsg(globalQueryResult);
									Log.v(myPort, "");
								}else{
									//If my Values are not empty
									globalQueryResult = myValues;
									globalQueryResult = new String(myValues + allMsgs);
									result = decryptMsg(globalQueryResult);
									Log.v(myPort,"Global Query * " + globalQueryResult);
								}
							}
							Log.v(myPort,"Answer Obtained" + result);
						}else{
							//When there is no next token empty message is received
							//No File send from the predecessor
							String myValues = convertValuesToString();
							if(myValues.isEmpty()){
								//All files have been deleted thus 
								globalQueryResult = "allFilesDeleted";
								Log.v(myPort, "Query * after delete");
							}
						}
						latch.countDown();
					}else{
						String myAddress = rawMsgTokens.nextToken();
						if(rawMsgTokens.hasMoreTokens()){
							String prevMsg = rawMsgTokens.nextToken();
							String msgToBeAttached;
							String myValues;
							Log.v(myPort, myAddress + prevMsg);
							if(prevMsg.equals("%")){
								//Received null from the previous successor
								myValues = convertValuesToString();
								if(myValues.equals("%")){
									//If my result is null pass the same identifier
									msgToBeAttached = "%";
								}else{
									msgToBeAttached = myValues;
								}
							}else{
								//Received proper value from the successor
								myValues = convertValuesToString();
								if(myValues.equals("%")){
									msgToBeAttached = prevMsg;
								}else{
									msgToBeAttached = new String(prevMsg.concat(convertValuesToString()));
								}
							}
							Log.v(myPort,"Global Query Forwared to " + succIPAddress);
							forwardGlobalQuery2(senderAddress,msgToBeAttached,succIPAddress);
						}else{
							Log.v(myPort, rawMsg);
						}
					}
				}catch(NoSuchElementException e){
					Log.v(myPort,e.toString());
				}
			}
			if(msgType.equals("fileFetch")){
				String senderAddress = rawMsgTokens.nextToken();
				String myAddress = rawMsgTokens.nextToken();
				String selection = rawMsgTokens.nextToken();
				fetchFile(senderAddress,selection);
			}
			if(msgType.equals("fileResult")){
				String senderAddress = rawMsgTokens.nextToken();
				Log.v(myPort, "File Received");
				singleQueryResult = rawMsgTokens.nextToken();
				Log.v(myPort, "File Query Returned " + singleQueryResult);
				latch.countDown();
			}
			if(msgType.equals("fileForward")){
				String senderAddress = rawMsgTokens.nextToken();
				String fileName = rawMsgTokens.nextToken();
				findFileLocation(senderAddress,fileName);
			}
			if(msgType.equals("returnFile")){
				String senderAddress = rawMsgTokens.nextToken();
				String myAddress = rawMsgTokens.nextToken();
				String selection = rawMsgTokens.nextToken();
				fetchFile(senderAddress,selection);
			}
			if(msgType.equals("forwardReturnFile")){
				String senderAddress = rawMsgTokens.nextToken();
				String myAddress = rawMsgTokens.nextToken();
				String selection = rawMsgTokens.nextToken();
				findFileLocation(senderAddress,selection);
			}
			if(msgType.equals("deleteAllFiles")){
				String senderAddress = rawMsgTokens.nextToken();
				String myAddress = rawMsgTokens.nextToken();
				if(myPort.equals(senderAddress)){
					//All files have been deleted
					Log.v(myPort,"Deletion * Complete");
				}else{
					sendDeleteAllFileMsg();
					Log.v(myPort,"Deletion * forwarded to " + succIPAddress);
				}
			}
			if(msgType.equals("deleteFile")){
				String senderAddress = rawMsgTokens.nextToken();
				String myAddress = rawMsgTokens.nextToken();
				String fileName = rawMsgTokens.nextToken();
				Log.v(myPort, "Delete File received from " + senderAddress);
				deleteFile(fileName);
			}
			if(msgType.equals("forwardDeleteFile")){
				String senderAddress = rawMsgTokens.nextToken();
				String myAddress = rawMsgTokens.nextToken();
				String fileName = rawMsgTokens.nextToken();
				Log.v(myPort, " Forward Delete file received from " + senderAddress );
				findFileDeleteLocation(fileName);
			}
			return;
		}
    }
    
    private void findFileDeleteLocation(String fileName) {
		// TODO Auto-generated method stub
		String fileId;
		try{
			fileId = genHash(fileName);
			if(myChordId.compareTo(succChordId) > 0){
				if(myChordId.compareTo(fileId) < 0){
					sendDeleteFileMsg(fileName);
				}else if(myChordId.compareTo(fileId) > 0 && succChordId.compareTo(fileId) > 0){
					sendDeleteFileMsg(fileName);
				}else{
					forwardDeleteMsg(fileName);
				}
			}else if(myChordId.compareTo(fileId) < 0 && succChordId.compareTo(fileId) > 0){
				sendDeleteFileMsg(fileName);
			}else{
				forwardDeleteMsg(fileName);
			}
		}catch(Exception e){
			Log.v(myPort,e.toString());
		}
	}
    
    private void deleteFile(String fileName) {
		// TODO Auto-generated method stub
		if(getContext().deleteFile(fileName)){
			Log.v(myPort,"File " + fileName + " deleted");
		}else{
			Log.v(myPort, "wrong delivery");
		}
	}
    
    private void findFileLocation(String senderAddress, String fileName) {
		// TODO Auto-generated method stub
		String fileId;
		try{
			fileId = genHash(fileName);
			if(myChordId.compareTo(succChordId) > 0){
				if(myChordId.compareTo(fileId) < 0){
					returnResultMsg(senderAddress,fileName);
				}else if(myChordId.compareTo(fileId) > 0 && succChordId.compareTo(fileId) > 0){
					returnResultMsg(senderAddress,fileName);
				}else{
					forwardFileFetch(senderAddress,fileName);
				}
			}else if(myChordId.compareTo(fileId) < 0 && succChordId.compareTo(fileId) > 0){
				returnResultMsg(senderAddress,fileName);
			}else{
				forwardFileFetch(senderAddress,fileName);
			}
		}catch(Exception e){
			Log.v(myPort,e.toString());
		}
	}
    
    private void forwardFileFetch(String senderAddress, String fileName) {
		// TODO Auto-generated method stub
    	String msgToSend = new String("forwardReturnFile" + "$" + senderAddress + "$" + succIPAddress + "$" + fileName);
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, myPort);
	}

	private void returnResultMsg(String senderAddress, String fileName) {
		// TODO Auto-generated method stub
		String msgToSend = new String("returnFile" + "$" + senderAddress + "$" + succIPAddress + "$" + fileName);
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, myPort);
	}

	private void fetchFile(String senderAddress, String selection) {
		// TODO Auto-generated method stub
    	
    	String columns[] = {"key","value"};
    	String key = "";
    	String value = "";
    	MatrixCursor mCursor = new MatrixCursor(columns);
    	try{
    		InputStreamReader in = new InputStreamReader(getContext().openFileInput(selection));
    		BufferedReader reader = new BufferedReader(in);
    		String line = null;
    		while((line = reader.readLine()) != null){
    			value = line;
    		}
    		in.close();
    		String fields[] = {selection,value};
    		mCursor.addRow(fields);
    		Log.v(myPort,selection+value);
    	}catch (Exception IO){
    		Log.v(myPort,"File Not Found");
    	}
    	int keyIndex = mCursor.getColumnIndex(KEY_FIELD);
    	int valueIndex = mCursor.getColumnIndex(VALUE_FIELD);
    	mCursor.moveToFirst();
    	String returnKey = mCursor.getString(keyIndex);
    	String returnValue = mCursor.getString(valueIndex);
    	mCursor.close();
    	sendFileQueryResult(senderAddress,returnKey,returnValue);
	}
    
    private void sendFileQueryResult(String senderAddress, String returnKey,
			String returnValue) {
		// TODO Auto-generated method stub
    	String msgToSend = new String("fileResult" + "$" + senderAddress + "$" + returnKey + "," + returnValue);
    	new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, myPort);
	}

	private void forwardGlobalQuery2(String senderAddress,
			String msgToQuery, String succIPAddress2) {
		// TODO Auto-generated method stub
    	final String finalAddress = senderAddress;
    	final String msgToAttach = msgToQuery;
    	final String succAddress = succIPAddress2;
    	String globalQueryMsg = new String ("forwardQuery2" + "$" + finalAddress + 
    			"$" + succAddress + "$" + msgToAttach);
    	new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, globalQueryMsg, myPort);
    }
    
	private String convertValuesToString() {
		// TODO Auto-generated method stub
		String columns[] = {"key","value"};
    	String files[] = getContext().fileList();
    	String value = "";
    	StringBuilder msgOfString = new StringBuilder("");
    	try{
    		for(int i = 0; i < files.length; i++){
    			InputStreamReader in = new InputStreamReader(getContext().openFileInput(files[i]));
    			BufferedReader reader = new BufferedReader(in);
    			String line = null;
    			while((line = reader.readLine()) != null){
    				value = new String(line);
    			}
    			in.close();
    			msgOfString.append(files[i] + "," + value + "^");	
    		}
    		Log.v(myPort,"Answer Attach " + msgOfString.toString());
    		if(msgOfString.toString().isEmpty()){
    			msgOfString.append("%");
    		}else{
    			Log.v(myPort, "My message " + msgOfString.toString());
    		}
    		return msgOfString.toString();
    	}catch(Exception IO){
    		Log.v(TAG,IO.toString());
    	}
    	return msgOfString.toString();
	}
    public String decryptMsg(String globalQueryResult2) {
		// TODO Auto-generated method stub
    	StringTokenizer keys = new StringTokenizer(globalQueryResult2,"^");
    	StringBuilder list = new StringBuilder("");
    	while(keys.hasMoreTokens()){
    		list.append(keys.nextToken() + "\n");
    	}
		return list.toString();
	}

	private void handleNodeJoins(String portAddress, String portString, String portChordId ){
    	//Code to handle Node Joining - every message is sent to the port 11108
    	try{
    		Log.v(myPort, "Handling Join Messages of " + portAddress);
    		if(myChordId.equals(succChordId)){
    			//I am the only one in the node then update my pointers.
    			succChordId = portChordId;
    			succIPAddress = portAddress;
    			sendJoinedMsg(portAddress,myPort,myChordId);
    		}else if(myChordId.compareTo(succChordId) > 0){
    			Log.v(myPort, myChordId + " > " + succChordId + " = " + succIPAddress);
    			if(myChordId.compareTo(portChordId) < 0){
    				Log.v(myPort, myChordId + " < " + portChordId + " = " + portAddress);
    				changeSuccessor(portAddress,portChordId);
    			}else if(myChordId.compareTo(portChordId) > 0 && succChordId.compareTo(portChordId) > 0){
    				Log.v(myPort, myChordId + " > " + portChordId + " = " + portAddress);
    				changeSuccessor(portAddress,portChordId);
    			}else if(myChordId.compareTo(portChordId) > 0 && succChordId.compareTo(portChordId) < 0){
    				sendForwardMsg(succIPAddress,portAddress,portString,portChordId);
    			}
    		}else if(myChordId.compareTo(portChordId) < 0 && succChordId.compareTo(portChordId) > 0){
    			changeSuccessor(portAddress,portChordId);
    		}else{
    			sendForwardMsg(succIPAddress,portAddress,portString,portChordId);
    		}
    	}catch (Exception e){
    		Log.v(TAG,e.toString());
    	}
    }
    
    public void changeSuccessor(String portAddress, String portChordId){
    	String newNodeUpdateSuccAddress = succIPAddress;
    	String newNodeUpdateSuccChordId = succChordId;
    	succIPAddress = portAddress;
    	succChordId = portChordId;
    	Log.v(myPort, "Change Successor for " + myPort + " to " + succIPAddress);
    	sendUpdateMsg(portAddress,newNodeUpdateSuccAddress, newNodeUpdateSuccChordId);
    }
    
    public void handleJoinedMsg(String successor, String successorId){
    	succIPAddress = successor;
    	succChordId = successorId;
    	Log.v(myPort , "In HandleJoinedMsg ");
    }
    
    public void handleUpdateMsg(String newSuccIPAddress, String newSuccChordID){
    	succIPAddress = newSuccIPAddress;
    	succChordId = newSuccChordID;
    	Log.v(myPort,"My Successor Updated to " + succIPAddress);
    }
    
    public void sendJoinedMsg(String portAddress, String successor, String successorId){
    	String msgToSend = new String("Joined" + "$" + portAddress + "$" + successor + "$" + successorId);
    	new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend, myPort);
    }
    
    public void sendUpdateMsg(String newNodeAddress, String newNodeSuccAddress, String newNodeSuccId){
    	String updateMsg = new String("update" + "$" + newNodeAddress + "$" + newNodeSuccAddress + "$" + newNodeSuccId);
    	new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, updateMsg, myPort);
    }
    public void sendForwardMsg(String succAddress, String newNodeAddress, String newNodeString, String newNodeChordId){
    	String msgToForward = new String("forwardJoin" + "$" + succAddress + "$" + newNodeAddress + 
    			"$" + newNodeString + "$" + newNodeChordId);
    	new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToForward, myPort);
    }
    
    
    private class ClientTask extends AsyncTask<String, Void, Void> {
		@Override
		protected Void doInBackground(String... msgs) {
			try {
				String msgToSend = msgs[0];
				StringTokenizer msgType = new StringTokenizer(msgToSend,"$");
				String type = msgType.nextToken();
				Socket socket;
				if(type.equals("join")){
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}),Integer.parseInt(JOIN_PORT));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"Join Message Sent to " + JOIN_PORT);
				}
				if(type.equals("forwardJoin")){
					String succAddress = msgType.nextToken();
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}),Integer.parseInt(succAddress));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"Forward Join Message sent to " + succAddress);
				}
				if(type.equals("Joined")){
					String address = msgType.nextToken();
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(address));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort, "Joined Message sent to " + address);
				}
				if(type.equals("update")){
					//Send Two messages to each of the nodes
					String address = msgType.nextToken();
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(address));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"Update Successor Message sent to " + address);
				}
				if(type.equals("insert")){
					//Send Insert Message
					String address = msgType.nextToken();
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(address));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"Insert Message Sent to " + address);
				}
				if(type.equals("forwardInsert")){
					String address = msgType.nextToken();
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(address));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"Forward Insert Message Sent to " + address);
				}if(type.equals("forwardQuery2")){
					String senderAddress = msgType.nextToken();
					String address = msgType.nextToken();
					String msgToAttach = msgType.nextToken();
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(address));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"Global Forward Query Message Sent to " + msgToAttach);
				}if(type.equals("fileResult")){
					String senderAddress = msgType.nextToken();
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(senderAddress));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"File Result " + senderAddress);
				}if(type.equals("returnFile")){
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(succIPAddress));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"Return File Query Sent to " + succIPAddress);
				}if(type.equals("forwardReturnFile")){
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(succIPAddress));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"Forward Return File Query Sent to " + succIPAddress);
				}if(type.equals("deleteAllFiles")){
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(succIPAddress));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"Delete All * message sent to " + succIPAddress);
				}if(type.equals("deleteFile")){
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(succIPAddress));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"Delete file send to " + succIPAddress);
				}if(type.equals("forwardDeleteFile")){
					socket = new Socket(InetAddress.getByAddress(new byte[]{10,0,2,2}), Integer.parseInt(succIPAddress));
					DataOutputStream output = new DataOutputStream(socket.getOutputStream());
					output.writeBytes(msgToSend);
					socket.close();
					Log.v(myPort,"Forward Delete file to " + succIPAddress);
				}
			} catch (UnknownHostException e) {
				Log.e(TAG, "ClientTask UnknownHostException");
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(TAG, "ClientTask socket IOException");
			}
			return null;
		}
    }
}
