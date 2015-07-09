package com.websmithing.gpstracker;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by Dimitar on 3/5/2015.
 */
public class MapLoader extends Activity {
    private static final String TAG = "GpsTrackerActivity";
    private static TextView txt;
    private String tmp_position;
    private int position;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maploader);
        txt = (TextView) findViewById(R.id.gpstxt);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            tmp_position = extras.getString("position");
        }

        position=Integer.parseInt(tmp_position);
        String test = readFromFile();
        test = test.replaceAll(  "\\}\\]\\,\\{", "},{");  //}],{
        String[] testt = test.split("\\[");
        if(testt.length==0){
            testt = new String[]{"Error", "Error"};
        }
         for (int i=0;i<=testt.length;i++){
             if (position==i){
                 txt.setText(testt[i+1]);
             }
        }
    }

    //read the string from the file
    private String readFromFile() {
        BufferedReader reader = null;
        File file = new File(Environment.getExternalStorageDirectory(), "RaritanTracker");
        File gpxfile = new File(file, "RaritanTracker.txt");
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
}
