package com.example.ishida.atvscanner;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.List;
import java.util.UUID;


public class MainActivity extends ActionBarActivity {

    private static final String TAG = "AtvScanner";
    private static final UUID proximityUUID = UUID.fromString("e2c56db5-dffb-48d2-b060-d0f5a71096e0");
    private static final int major = 1;
    private static final int minor = 1;
    private static final int targetRssi = -60;

    static final UUID service_uuid = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
    static final UUID characteristic_uuid = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");

    private int connectionState = STATE_DISCONNECTED;
    private TextView textView;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            AtvBeacon beacon = AtvBeacon.create(device, scanRecord);
            if (beacon != null) { // iBeacon advertisement
                // see if identity machs
                if ((beacon.getProximityUUID().equals(proximityUUID)) &&
                        (beacon.getMajor() == major) && (beacon.getMinor() == minor)) {
                    //Log.d(TAG, "Found Atv Beacon! " + beacon + rssi);
                    if (beacon.getPower() >= rssi) {
                        //Log.d(TAG, "Not close enough.");
                        return;
                    }
                    bluetoothAdapter.stopLeScan(leScanCallback);
                    connectGatt(device);
                    connectionState = STATE_CONNECTING;
                }
            }
        }
    };
    private BluetoothGatt bluetoothGatt;
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange: " + status + "->" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                appendStatus("Connected to GATT server.");
                connectionState = STATE_CONNECTED;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                appendStatus("Disconnected from GATT server.");
                connectionState = STATE_DISCONNECTED;
                if (bluetoothAdapter != null) {
                    bluetoothAdapter.startLeScan(leScanCallback);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered: ");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service: services) {
                    Log.d(TAG, service.toString());
                    if (service.getUuid().equals(service_uuid)) {
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristic_uuid);
                        if (characteristic != null) {
                            Log.d(TAG, "Found Service & Characteristic");
                            appendStatus("Read Characteristic");
                            gatt.readCharacteristic(characteristic);
                            int prop = characteristic.getProperties();
                            if ((prop | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                gatt.setCharacteristicNotification(characteristic, true);
                            }
                        }
                    }
                }
            } else {
                Log.d(TAG, "stat = " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicRead: ");
            readCharacteristic(characteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged: ");
            readCharacteristic(characteristic);
        }
    };

    private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(characteristic_uuid)) {
            final byte[] data = characteristic.getValue();
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            Log.d(TAG, "data:" + new String(data) + " " + stringBuilder.toString());
            appendStatus("data: " + new String(data));
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = (TextView)findViewById(R.id.text);

        bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bluetoothAdapter != null) {
            bluetoothAdapter.stopLeScan(leScanCallback);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bluetoothAdapter != null) {
            bluetoothAdapter.startLeScan(leScanCallback);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            if (connectionState == STATE_CONNECTED) {
                bluetoothGatt.disconnect();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void connectGatt(final BluetoothDevice device) {
        if (connectionState != STATE_DISCONNECTED) {
            Log.d(TAG, "Not disconnected. Ignored.");
            return;
        }
        Log.d(TAG, "Connecting to " + device);
        appendStatus("Connecting to " + device);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bluetoothGatt = device.connectGatt(MainActivity.this, false, gattCallback);
            }
        });
    }

    private void appendStatus(final String status) {
        //Log.d(TAG, "append: " + status);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String current = textView.getText().toString();
                textView.setText(current + "\n" + status);
            }
        });
    }
}
