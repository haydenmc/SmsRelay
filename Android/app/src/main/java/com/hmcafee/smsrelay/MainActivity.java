package com.hmcafee.smsrelay;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.hmcafee.smsrelay.services.BluetoothLeService;

public class MainActivity extends AppCompatActivity {
    private final int PERMISSION_REQUEST_CODE_RECEIVE_SMS = 0;
    private boolean hasSmsPermission = false;

    private void getSmsPermission(Context context) {
        // Check permissions
        if (hasSmsPermission) {
            return;
        }
        int sms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS);
        if (sms == PackageManager.PERMISSION_GRANTED) {
            hasSmsPermission = true;
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_SMS}, PERMISSION_REQUEST_CODE_RECEIVE_SMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE_RECEIVE_SMS: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    hasSmsPermission = true;
                }
            }
        }
    }

//    private void readSmsMessages(Context context) {
//        if (!hasSmsPermission) {
//            getSmsPermission(context);
//            return;
//        }
//        ContentResolver contentResolver = context.getContentResolver();
//        Cursor cursor = contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null);
//        if (cursor == null) {
//            System.out.println("Could not query for SMS messages.");
//            return;
//        }
//        if (cursor.moveToFirst()) {
//            do {
//                String smsDate = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
//                String smsNumber = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
//                String smsBody = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
//                System.out.println(smsDate + " " + smsNumber + ": " + smsBody);
//            } while (cursor.moveToNext());
//        } else {
//            System.out.println("No SMS messages found.");
//        }
//    }

    public void click_buttonStartService(View view) {
        Log.i(getLocalClassName(), "Attempting to start service...");
        startService(new Intent(this, BluetoothLeService.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSmsPermission(this);
    }
}
