package com.websmithing.gpstracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;

public class GpsTrackerActivity extends ActionBarActivity {
    private static final String TAG = "GpsTrackerActivity";

    private String defaultUploadWebsite;
    public int historyId;
    private static EditText txtUserName;
    private static EditText txtWebsite;
    private static Button trackingButton;
    private static Button UploadButton;
    private static ListView myListView;
    private static TextView distance;

    private boolean currentlyTracking;
    private RadioGroup intervalRadioGroup;
    private int intervalInMinutes = 1;
    private AlarmManager alarmManager;
    private Intent gpsTrackerIntent;
    private PendingIntent pendingIntent;
    ArrayList<String> HistoryArray = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpstracker);

        defaultUploadWebsite = getString(R.string.default_upload_website);

        txtWebsite = (EditText)findViewById(R.id.txtWebsite);
        txtUserName = (EditText)findViewById(R.id.txtUserName);
        intervalRadioGroup = (RadioGroup)findViewById(R.id.intervalRadioGroup);
        trackingButton = (Button)findViewById(R.id.trackingButton);
        UploadButton = (Button)findViewById(R.id.UploadButton);
        txtUserName.setImeOptions(EditorInfo.IME_ACTION_DONE);
        myListView = (ListView) findViewById(R.id.listView);
        distance = (TextView) findViewById(R.id.distance);

        TabHost tabHost = (TabHost) findViewById(R.id.tabHost2);
        tabHost.setup();
        TabHost.TabSpec tabs = tabHost.newTabSpec("tracking");
        tabs.setContent(R.id.Track);
        tabs.setIndicator("Tracking");
        tabHost.addTab(tabs);
        tabs = tabHost.newTabSpec("import");
        tabs.setContent(R.id.Import);
        tabs.setIndicator("Import");
        tabHost.addTab(tabs);
        tabs = tabHost.newTabSpec("history");
        tabs.setContent(R.id.History);
        tabs.setIndicator("History");
        tabHost.addTab(tabs);

        SharedPreferences sharedPreferences = this.getSharedPreferences("com.websmithing.gpstracker.prefs", Context.MODE_PRIVATE);
        currentlyTracking = sharedPreferences.getBoolean("currentlyTracking", false);

        boolean firstTimeLoadindApp = sharedPreferences.getBoolean("firstTimeLoadindApp", true);
        if (firstTimeLoadindApp) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("firstTimeLoadindApp", false);
            editor.putString("appID", UUID.randomUUID().toString());
            editor.apply();
        }


        intervalRadioGroup.setOnCheckedChangeListener(
                new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup radioGroup, int i) {
                        saveInterval();
                    }
                });

        trackingButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                trackLocation(view);
            }
        });
        UploadButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                UploadToServer(view);
            }
        });
        myListView.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        String test = String.valueOf(parent.getItemAtPosition(position));
                        Toast.makeText(getApplicationContext(), Integer.toString(position), Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(getBaseContext(), MapLoader.class);
                        intent.putExtra("position", Integer.toString(position));
                        startActivity(intent);
                    }
                }

        );

        Thread t = new Thread() {

            String FileNameTmp = "DistanceTracker";
            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(5000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String readDistance=readFromFile(FileNameTmp);
                                float f = Float.parseFloat(readDistance);
                                String distanceStr=String.format("%.2g%n",f);
                                distance.setText("Distance Covered: "+distanceStr+" km");
                            }
                        });
                    }
                } catch (InterruptedException e) {
                }
            }
        };

        t.start();


        File file = new File(Environment.getExternalStorageDirectory(), "RaritanTracker");

        File gpxfile = new File(file, "RaritanTracker.txt");
             if (!gpxfile.exists()) {
            Log.d(TAG, "NOT EXISTTT");
            }
            else {
                 Log.d(TAG, "EXISTTT");
                 gpxfile.delete();
             }
    }

    private void saveInterval() {
        if (currentlyTracking) {
            Toast.makeText(getApplicationContext(), R.string.user_needs_to_restart_tracking, Toast.LENGTH_LONG).show();
        }

        SharedPreferences sharedPreferences = this.getSharedPreferences("com.websmithing.gpstracker.prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        switch (intervalRadioGroup.getCheckedRadioButtonId()) {
            case R.id.i1:
                editor.putInt("intervalInMinutes", 1);
                break;
            /*case R.id.i5:
                editor.putInt("intervalInMinutes", 5);
                break;*/
            /*case R.id.i15:
                editor.putInt("intervalInMinutes", 15);
                break;*/
        }

        editor.apply();
    }

    private void startAlarmManager() {
        Log.d(TAG, "startAlarmManager"); //see how many times this appear

        Context context = getBaseContext();
        alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        gpsTrackerIntent = new Intent(context, GpsTrackerAlarmReceiver.class);

        pendingIntent = PendingIntent.getBroadcast(context, 0, gpsTrackerIntent, 0);

        SharedPreferences sharedPreferences = this.getSharedPreferences("com.websmithing.gpstracker.prefs", Context.MODE_PRIVATE);
        intervalInMinutes = sharedPreferences.getInt("intervalInMinutes", 1);

        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime(),
                intervalInMinutes * 10000, // 60000 = 1 minute HERE
                pendingIntent);
    }

    private void cancelAlarmManager() {
        Log.d(TAG, "cancelAlarmManager");

        Context context = getBaseContext();
        Intent gpsTrackerIntent = new Intent(context, GpsTrackerAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, gpsTrackerIntent, 0);
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }

    //called when UploadButton is tapped
    protected void UploadToServer(View v) {
        String JSONstring;
        String FileNameTmp="RaritanTracker";
        String test = readFromFile(FileNameTmp); //this could be wrong if it doesnt want to upload, new function for read then in order to read the distance covered
        test = test.replaceAll(  "\\}\\{", "},{");
        test = test.replaceAll(  "\\]\\[", "],[");
        test=test.substring(0,test.length()-1);
        test = test.replaceAll(  "\\}\\]\\,\\{", "},{");  //}],{
        JSONstring = "["+test+"]";

        final String uploadWebsite =  defaultUploadWebsite;
        final RequestParams requestParams = new RequestParams();
        requestParams.put("User",JSONstring);

        //post to server
        LoopjHttpClient.post("https://lutcodecamp-niklaskolbe.c9.io/biketracks/create/fromapp", requestParams, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - success", uploadWebsite, requestParams, responseBody, headers, statusCode, null);
                Log.d(TAG, requestParams.toString());
                //stopSelf();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                //LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - failure", uploadWebsite, requestParams, errorResponse, headers, statusCode, e);
                //stopSelf();
            }
        });


    }

    //read the string from the file
    private String readFromFile(String FileNameTmp) {
        BufferedReader reader = null;
        File file = new File(Environment.getExternalStorageDirectory(), FileNameTmp);
        File gpxfile = new File(file, FileNameTmp+".txt");
        try {
            reader = new BufferedReader(new FileReader(gpxfile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        StringBuilder total = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                total.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return total.toString();
    }

    // called when trackingButton is tapped
    protected void trackLocation(View v) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String date = df.format(Calendar.getInstance().getTime());
        historyId++;
        if (historyId % 2 == 0) {// even
             String end="]";

        } else { //odd On pressed trackingButton for starting the tracking, an item with date will be added to the ListView in History
            String start="[";
            writeToFile(start);
            //Log.d(TAG, "wwwwwwwww"+historyId);
            HistoryArray.add("Recorded route on: "+date);
            ListAdapter testList = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, HistoryArray);
            myListView.setAdapter(testList);

        }

        SharedPreferences sharedPreferences = this.getSharedPreferences("com.websmithing.gpstracker.prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if (!saveUserSettings()) {
            return;
        }

        if (!checkIfGooglePlayEnabled()) {
            return;
        }

        if (currentlyTracking) {
            cancelAlarmManager();
            currentlyTracking = false;
            editor.putBoolean("currentlyTracking", false);
            editor.putString("sessionID", "");
        } else {
            startAlarmManager();
            currentlyTracking = true;
            editor.putBoolean("currentlyTracking", true);
            editor.putFloat("totalDistanceInMeters", 0f);
            editor.putBoolean("firstTimeGettingPosition", true);
            editor.putString("sessionID",  UUID.randomUUID().toString());
        }

        editor.apply();
        setTrackingButtonState();
    }

    private void writeToFile(String s) {
        try
        {
            File root = new File(Environment.getExternalStorageDirectory(), "RaritanTracker");
            if (!root.exists()) {
                root.mkdirs();
            }
            Log.d(TAG, root.getAbsolutePath() + "mmmmmmmmmm");
            File gpxfile = new File(root, "RaritanTracker.txt");

            BufferedWriter bW;
            bW = new BufferedWriter(new FileWriter(gpxfile, true));

            bW.write(s);
            bW.newLine();
            bW.flush();
            bW.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }

    }

    private boolean saveUserSettings() {
        if (textFieldsAreEmptyOrHaveSpaces()) {
            return false;
        }

        SharedPreferences sharedPreferences = this.getSharedPreferences("com.websmithing.gpstracker.prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        switch (intervalRadioGroup.getCheckedRadioButtonId()) {
            case R.id.i1:
                editor.putInt("intervalInMinutes", 1);
                break;
            /*case R.id.i5:
                editor.putInt("intervalInMinutes", 5);
                break;*/
            /*case R.id.i15:
                editor.putInt("intervalInMinutes", 15);
                break;*/
        }

        editor.putString("userName", txtUserName.getText().toString().trim());
        editor.putString("defaultUploadWebsite", txtWebsite.getText().toString().trim());

        editor.apply();

        return true;
    }

    private boolean textFieldsAreEmptyOrHaveSpaces() {
        String tempUserName = txtUserName.getText().toString().trim();
        String tempWebsite = txtWebsite.getText().toString().trim();

        if (tempWebsite.length() == 0 || hasSpaces(tempWebsite) || tempUserName.length() == 0 || hasSpaces(tempUserName)) {
            Toast.makeText(this, R.string.textfields_empty_or_spaces, Toast.LENGTH_LONG).show();
            return true;
        }

        return false;
    }

    private boolean hasSpaces(String str) {
        return ((str.split(" ").length > 1) ? true : false);
    }

    private void displayUserSettings() {
        SharedPreferences sharedPreferences = this.getSharedPreferences("com.websmithing.gpstracker.prefs", Context.MODE_PRIVATE);
        intervalInMinutes = sharedPreferences.getInt("intervalInMinutes", 1);

        switch (intervalInMinutes) {
            case 1:
                intervalRadioGroup.check(R.id.i1);
                break;
           /* case 5:
                intervalRadioGroup.check(R.id.i5);
                break;*/
            /*case 15:
                intervalRadioGroup.check(R.id.i15);
                break;*/
        }

        txtWebsite.setText(sharedPreferences.getString("ADD SERVER", defaultUploadWebsite));
        txtUserName.setText(sharedPreferences.getString("userName", ""));
    }

    private boolean checkIfGooglePlayEnabled() {
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
            return true;
        } else {
            Log.e(TAG, "unable to connect to google play services.");
            Toast.makeText(getApplicationContext(), R.string.google_play_services_unavailable, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void setTrackingButtonState() {
        if (currentlyTracking) {
            trackingButton.setBackgroundResource(R.drawable.green_tracking_button);
            trackingButton.setTextColor(Color.BLACK);
            trackingButton.setText(R.string.tracking_is_on);
        } else {
            trackingButton.setBackgroundResource(R.drawable.red_tracking_button);
            trackingButton.setTextColor(Color.WHITE);
            trackingButton.setText(R.string.tracking_is_off);
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume(); 

        displayUserSettings();
        setTrackingButtonState();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
