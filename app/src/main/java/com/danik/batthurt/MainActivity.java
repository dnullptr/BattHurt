package com.danik.batthurt;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.os.Build;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends AppCompatActivity {
    TextView batPercent,batWattage,charWatt;
    EditText fbhURL;
    IntentFilter ifilter;
    Intent batteryStatus;
    int level,scale;
    float batteryPct;
    public Handler handler;
    public static int timeToWait=5000,tries=0,prevLevel;
    FirebaseDatabase database;
    DatabaseReference myRef;
    DatabaseReference myChargeRef;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        charWatt=findViewById(R.id.charWatt);
        batWattage=findViewById(R.id.batteryWatt);
        charWatt.setVisibility(View.GONE);
        batWattage.setVisibility(View.GONE);
        fbhURL=findViewById(R.id.editTextFBH);

        database = FirebaseDatabase.getInstance();   //db
        myRef = database.getReference("CurrentBattery_"+Build.MODEL); //db ref
        myChargeRef=database.getReference("CurrentIsCharging_"+Build.MODEL);
        if(myRef.getKey().length() >0)
            fbhURL.setText(R.string.firebase_database_url);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }

        mainLoop();
    }

    public void mainLoop() {


        Log.d("TAG", "run: Start");
        charWatt = findViewById(R.id.charWatt);
        batPercent = findViewById(R.id.batteryPercent);
        batWattage = findViewById(R.id.batteryWatt);


        ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
        level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        batteryPct = level * 100 / (float) scale;

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

        if (isCharging) {
            charWatt.setVisibility(View.VISIBLE);
            batWattage.setVisibility(View.VISIBLE);
        }

        batPercent.setText("" + batteryPct);
        batWattage.setText(acCharge?"AC Wall Charger":usbCharge?"USB Slow Charger":"Other Charger");

        handler = new Handler();
        if (batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) > prevLevel && tries<3) {

            prevLevel=batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            timeToWait = 30000; tries++;
            Log.d("TAG", "mainLoop: did a cycle now will sleep for 30 sec");
        }
        else if(tries<=3)
        {
            prevLevel=batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            tries++;
            if(tries==3){
                timeToWait=10000;
                tries=0;
            }


        }
        myRef.setValue(prevLevel);
        myChargeRef.setValue(isCharging);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        long timeNow=System.currentTimeMillis();
        long thirtySec=1000*30;

        Intent intent =new Intent(MainActivity.this,ReminderBroadcast.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(MainActivity.this,0,intent,0);
        alarmManager.set(AlarmManager.RTC_WAKEUP,timeNow+thirtySec,pendingIntent );
        Log.d("ALARM", "mainLoop: reset RTC Wake");
        handler.postDelayed(new Runnable() {
            public void run() {
                Log.d("TAG", "in handler and run ");
                mainLoop();
            }
        }, timeToWait);
    }




}
