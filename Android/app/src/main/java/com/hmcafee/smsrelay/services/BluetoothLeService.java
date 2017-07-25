package com.hmcafee.smsrelay.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.provider.Telephony;
import android.support.annotation.Nullable;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.Calendar;
import java.util.UUID;

public class BluetoothLeService extends Service {
    // Constants
    private static final String TAG = BluetoothLeService.class.getSimpleName();
    private static final UUID SERVICE_UUID = UUID.fromString("a031c3a4-7928-49a9-89da-075e8b17f307");
    private static final UUID LASTRECEIVEDDATETIME_CHARACTERISTIC_UUID = UUID.fromString("4ba0a84b-3a2c-4cc0-b235-d7bacf24adbf");
    // Private members
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothGattService mBluetoothGattService;
    private BluetoothGattCharacteristic mLastReceivedDateTimeCharacteristic;
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.d(TAG, "GATT onConnectionStateChange (" + status + "): " + newState);
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.d(TAG, "GATT onServiceAdded (" + status + "): " + service.getUuid().toString());
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.d(TAG, "GATT onCharacteristicReadRequest: " + characteristic.getUuid().toString());
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, characteristic.getValue());
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.d(TAG, "GATT onCharacteristicWriteRequest: " + characteristic.getUuid().toString());

        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
        }
    };

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG, "Advertiser started successfully.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.e(TAG, "Advertiser start failure.");
        }
    };

    private BroadcastReceiver mSmsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // handle SMS
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            for (SmsMessage message : messages) {
                handleSms(message);
            }
        }
    };

    private void handleSms(SmsMessage message) {
        Log.d(TAG, "Handling new SMS message from " + message.getOriginatingAddress());
        if (mLastReceivedDateTimeCharacteristic != null) {
            mLastReceivedDateTimeCharacteristic.setValue(
                (int)(message.getTimestampMillis() / 1000),
                BluetoothGattCharacteristic.FORMAT_UINT32,
                0
            );
        }
    }

    private void startServer() {
        // Make sure Bluetooth LE is a thing on this device.
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            Log.e(TAG, "Device does not support Bluetooth/Bluetooth LE. Service abort.");
            stopSelf(); // Shut er down
        }
        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled. Service abort.");
            stopSelf(); // Bluetooth needs to be enabled.
        }
        // Instantiate and configure GATT server
        mBluetoothGattServer
            = mBluetoothManager.openGattServer(this, mGattServerCallback);
        mBluetoothGattService
            = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        mLastReceivedDateTimeCharacteristic
            = new BluetoothGattCharacteristic(
                LASTRECEIVEDDATETIME_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM
            );
        mBluetoothGattService.addCharacteristic(mLastReceivedDateTimeCharacteristic);
        mBluetoothGattServer.addService(mBluetoothGattService);
        Log.i(TAG, "Started GATT server.");
        // Instantiate and configure Bluetooth LE Advertiser
        BluetoothLeAdvertiser advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertiseSettings.Builder advertiserSettingsBuilder = new AdvertiseSettings.Builder();
        advertiserSettingsBuilder.setConnectable(true);
        advertiserSettingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        advertiserSettingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
        AdvertiseData.Builder advertiserDataBuilder = new AdvertiseData.Builder();
        advertiserDataBuilder.setIncludeDeviceName(true);
        advertiser.startAdvertising(
                advertiserSettingsBuilder.build(),
                advertiserDataBuilder.build(),
                mAdvertiseCallback
        );
        Log.i(TAG, "Started BTLE advertiser.");
        // Start listening for SMS
        IntentFilter smsIntentFilter = new IntentFilter();
        smsIntentFilter.addAction("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(mSmsReceiver, smsIntentFilter);

        // Test
        mLastReceivedDateTimeCharacteristic.setValue(123, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Start services on background thread
        final Thread thread = new Thread() {
            @Override
            public void run() {
                startServer();
            }
        };
        thread.start();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mSmsReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            return false;
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }
        return true;
    }
}
