package com.websmithing.gpstracker;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class LocationService extends Service implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "LocationService";
    private String defaultUploadWebsite;
    public int id;
    private boolean currentlyProcessingLocation = false;
    private LocationRequest locationRequest;
    private LocationClient locationClient;


    @Override
    public void onCreate() {
        super.onCreate();
        defaultUploadWebsite = getString(R.string.default_upload_website);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // if we are currently trying to get a location and the alarm manager has called this again,
        // no need to start processing a new location.
        if (!currentlyProcessingLocation) {
            currentlyProcessingLocation = true;
            startTracking();
        }

        return START_NOT_STICKY;
    }

    private void startTracking() {
        Log.d(TAG, "startTracking");

        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
            locationClient = new LocationClient(this,this,this);

            if (!locationClient.isConnected() || !locationClient.isConnecting()) {
                locationClient.connect();
            }
        } else {
            Log.e(TAG, "unable to connect to google play services.");
        }
    }

    protected void sendLocationDataToWebsite(Location location) {

        // formatted datetime format
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        String date = df.format(Calendar.getInstance().getTime());

        SharedPreferences sharedPreferences = this.getSharedPreferences("com.websmithing.gpstracker.prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        float totalDistanceInMeters = sharedPreferences.getFloat("totalDistanceInMeters", 0f);
        boolean firstTimeGettingPosition = sharedPreferences.getBoolean("firstTimeGettingPosition", true);

        if (firstTimeGettingPosition) {
            editor.putBoolean("firstTimeGettingPosition", false);
        } else {
            Location previousLocation = new Location("");
            previousLocation.setLatitude(sharedPreferences.getFloat("previousLatitude", 0f));
            previousLocation.setLongitude(sharedPreferences.getFloat("previousLongitude", 0f));

            float distance = location.distanceTo(previousLocation);
            totalDistanceInMeters += distance;
            editor.putFloat("totalDistanceInMeters", totalDistanceInMeters);
        }

        editor.putFloat("previousLatitude", (float)location.getLatitude());
        editor.putFloat("previousLongitude", (float)location.getLongitude());
        editor.apply();

        final RequestParams requestParams = new RequestParams();
        requestParams.put('"' + "latitude" + '"', '"'+Double.toString(location.getLatitude())+ '"' + "}"+ ",");
        requestParams.put('"' + "longitude" + '"', '"'+Double.toString(location.getLongitude()) + '"' + ",");

        requestParams.put('"' + "date" + '"', '"' + date + '"' +",");

        /*
        requestParams.put("locationmethod", location.getProvider());
        Double speedInMilesPerHour = location.getSpeed()* 2.2369;
        requestParams.put("speed",  Integer.toString(speedInMilesPerHour.intValue()));
        */
        if (totalDistanceInMeters > 0) {
            Log.d(TAG, "distanceeeee" + String.format("%.1f", totalDistanceInMeters / 1609)); // in miles,
            Log.d(TAG, "distanceeeeee" + String.format(String.valueOf(totalDistanceInMeters* 0.001))); // in km
            writeToFileDistance(String.valueOf(totalDistanceInMeters* 0.001));
        } else {
            Log.d(TAG, "distanceeeee 0.0"); // in miles
        }
        /*
        requestParams.put("username", sharedPreferences.getString("userName", ""));
        requestParams.put("phonenumber", sharedPreferences.getString("appID", "")); // uuid
        requestParams.put("sessionid", sharedPreferences.getString("sessionID", "")); // uuid

        Double accuracyInFeet = location.getAccuracy()* 3.28;
        requestParams.put("accuracy",  Integer.toString(accuracyInFeet.intValue()));

        Double altitudeInFeet = location.getAltitude() * 3.28;
        requestParams.put("extrainfo",  Integer.toString(altitudeInFeet.intValue()));

        requestParams.put("eventtype", "android");

        Float direction = location.getBearing();
        requestParams.put("direction",  Integer.toString(direction.intValue()));
        */
        final String uploadWebsite = sharedPreferences.getString("defaultUploadWebsite", defaultUploadWebsite);
        Toast.makeText(getApplicationContext(), R.string.user_needs_to_restart_trackingg, Toast.LENGTH_LONG).show();

        //Log.d(TAG, "aaaaaaaaaaaaaaa"+requestParams.toString());
        writeToFile(requestParams.toString());

       /* LoopjHttpClient.get(uploadWebsite, requestParams, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
                LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - success", uploadWebsite, requestParams, responseBody, headers, statusCode, null);
                stopSelf();
            }
            @Override
            public void onFailure(int statusCode, org.apache.http.Header[] headers, byte[] errorResponse, Throwable e) {
                LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - failure", uploadWebsite, requestParams, errorResponse, headers, statusCode, e);
                stopSelf();
            }

        });*/

          //post
          LoopjHttpClient.post("www.google.com", requestParams, new AsyncHttpResponseHandler() {

             @Override
             public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                 LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - success", uploadWebsite, requestParams, responseBody, headers, statusCode, null);
                 Log.d(TAG, requestParams.toString());
                 stopSelf();
             }

             @Override
             public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                 //LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - failure", uploadWebsite, requestParams, errorResponse, headers, statusCode, e);
                 stopSelf();
             }
         });
    }

    private void  writeToFileDistance(String s){
        try{
        File root = new File(Environment.getExternalStorageDirectory(), "DistanceTracker");
        if (!root.exists()) {
            root.mkdirs();
        }
        File gpxfile = new File(root, "DistanceTracker.txt");
        BufferedWriter bW;
        bW = new BufferedWriter(new FileWriter(gpxfile));
        Log.d(TAG, "uuuuuuuuu"+s);
        bW.write(s);
        bW.flush();
        bW.close();
    }
    catch(IOException e)
    {
        e.printStackTrace();
    }

    }

    private void writeToFile(String s) {
        try
        {
            File root = new File(Environment.getExternalStorageDirectory(), "RaritanTracker");
            if (!root.exists()) {
                root.mkdirs();
            }

            File gpxfile = new File(root, "RaritanTracker.txt");

            BufferedWriter bW;
            bW = new BufferedWriter(new FileWriter(gpxfile, true));
            s=s.substring(0,s.length()-1);
            s = s.replaceAll("&", "");
            s=s.replaceAll('"' + "date", "{" + '"' + "date");
            s=s.replaceAll("=", ":");
            s=s+"],";
            Log.d(TAG, "ooooooooooooooo"+s);
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

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            Log.e(TAG, "position: " + location.getLatitude() + ", " + location.getLongitude() + " accuracy: " + location.getAccuracy());

            // we have our desired accuracy of 500 meters so lets quit this service,
            // onDestroy will be called and stop our location uodates
            if (location.getAccuracy() < 500.0f) {
                stopLocationUpdates();
                sendLocationDataToWebsite(location);
            }
        }
    }

    private void stopLocationUpdates() {
        if (locationClient != null && locationClient.isConnected()) {
            locationClient.removeLocationUpdates(this);
            locationClient.disconnect();
        }
    }

    // Called by Location Services when the request to connect the client finishes successfully. At this point, you request the current location or start periodic updates
    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(1000); // milliseconds
        locationRequest.setFastestInterval(1000); // the fastest rate in milliseconds at which your app can handle location updates
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationClient.requestLocationUpdates(locationRequest, this);
    }

    // Called by Location Services if the connection to the location client drops because of an error.
    @Override
    public void onDisconnected() {
        Log.e(TAG, "onDisconnected");

        stopLocationUpdates();
        stopSelf();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed");

        stopLocationUpdates();
        stopSelf();
    }

    /* private String readFromFile() {
        BufferedReader reader = null;

            File file = new File(Environment.getExternalStorageDirectory(), "RaritanTracker");
            *//*if (!file.exists()) {
                Log.d(TAG, "NOT EXISTT");
            }
            else {Log.d(TAG, "EXISTT");}*//*
            File gpxfile = new File(file, "RaritanTracker.txt");
            *//*if (!gpxfile.exists()) {
            Log.d(TAG, "NOT EXISTTT");
            }
            else {Log.d(TAG, "EXISTTT");}*//*

        try {
            reader = new BufferedReader(new FileReader(gpxfile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        StringBuilder total = new StringBuilder();
        String line;
        String test =null;
        *//*try {
            test = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }*//*

        try {
            while ((line = reader.readLine()) != null) {
                total.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return total.toString();
    }*/
}
