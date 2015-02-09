package com.example.ishida.atvscanner;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAssignedNumbers;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends ActionBarActivity {

    private static final String TAG = "AtvScanner";
    private static final UUID proximityUUID = UUID.fromString("e2c56db5-dffb-48d2-b060-d0f5a71096e0");
    private static final int major = 1;
    private static final int minor = 1;
    private static final int targetRssi = -60;

    static final UUID service_uuid = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
    static final UUID characteristic_uuid = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");
    static final UUID characteristic_uuid2 = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    private int connectionState = STATE_DISCONNECTED;
    private TextView textView;
    private String userName;

    private LinkedList<GATTRequest> requestQueue = new LinkedList<GATTRequest>();

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
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
                    stopScan();
                    connectGatt(device);
                    connectionState = STATE_CONNECTING;
                }
            }
        }
    };
    private BluetoothLeScanner bleScanner;
    private ScanCallback scanCallback;

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
                startScan();
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered: ");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service: services) {
                    Log.d(TAG, service.toString());
                    if (service.getUuid().equals(service_uuid)) {
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristic_uuid);
                        if (characteristic != null) {
                            Log.d(TAG, "Found Characteristic 1");
                            appendStatus("Read Characteristic");
                            gatt.readCharacteristic(characteristic);
                            //postGATTRequest(new GATTReadRequest(characteristic));
                            int prop = characteristic.getProperties();
                            if ((prop | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                gatt.setCharacteristicNotification(characteristic, true);
                            }
                        }
                        characteristic = service.getCharacteristic(characteristic_uuid2);
                        if (characteristic != null) {
                            Log.d(TAG, "Found Characteristic 2");
                            appendStatus("Write Characteristic");
                            characteristic.setValue(userName);
                            //gatt.writeCharacteristic(characteristic);
                            postGATTRequest(new GATTWriteRequest(characteristic));
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
            processGATTRequest();
            readCharacteristic(characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicWrite: " + status + ", " + characteristic.getStringValue(0));
            processGATTRequest();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged: ");
            processGATTRequest();
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

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.d(TAG, "Scan result: " + callbackType + ", " + result);
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    Log.d(TAG, "Batch scan result");
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.d(TAG, "Scan Failed: " + errorCode);
                }
            };
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // open Cursor for Profile
        Cursor mCursor = getContentResolver().query(
                ContactsContract.Profile.CONTENT_URI, null, null, null, null);

        mCursor.moveToFirst();

        // retrieve UserName
        int nameIndex = mCursor
                .getColumnIndex(ContactsContract.Profile.DISPLAY_NAME);
        userName = mCursor.getString(nameIndex);
        mCursor.close();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startScan();
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
        Log.d(TAG, "Connecting to " + device.getName());
        appendStatus("Connecting to " + device.getName());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bluetoothGatt = device.connectGatt(MainActivity.this, false, gattCallback);
                //bluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
            }
        });
    }

    private void startScan() {
        if (bluetoothAdapter != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                        startLScan();
                    } else {
                        bluetoothAdapter.startLeScan(leScanCallback);
                    }
                }
            });

            appendStatus("scan started");
        } else {
            appendStatus("Failed to start scan");
        }
    }

    private void startLScan() {
        ScanFilter filter = new ScanFilter.Builder().setManufacturerData(BluetoothAssignedNumbers.APPLE, createManufactureData(), mask).build();
        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(filter);
        ScanSettings settings = new ScanSettings.Builder().setReportDelay(0).setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        bleScanner.startScan(filters, settings, scanCallback);
    }

    private void stopScan() {
        if (bluetoothAdapter != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                        stopLScan();
                    } else {
                        bluetoothAdapter.stopLeScan(leScanCallback);
                    }
                }
            });
            appendStatus("scan stopped");
        } else {
            appendStatus("Failed to stop scan");
        }
    }

    private void stopLScan() {
        bleScanner.stopScan(scanCallback);
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

    private abstract class GATTRequest {

        protected BluetoothGattCharacteristic characteristic;
        GATTRequest(BluetoothGattCharacteristic characteristic) {
            this.characteristic = characteristic;
        }

        abstract void process(final BluetoothGatt gatt);

    }

    private class GATTReadRequest extends GATTRequest{

        GATTReadRequest(BluetoothGattCharacteristic characteristic) {
            super(characteristic);
        }

        @Override
        void process(final BluetoothGatt gatt) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    gatt.readCharacteristic(characteristic);
                }
            });
        }
    }

    private class GATTWriteRequest extends GATTRequest{

        GATTWriteRequest(BluetoothGattCharacteristic characteristic) {
            super(characteristic);
        }

        @Override
        void process(final BluetoothGatt gatt) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    gatt.writeCharacteristic(characteristic);
                }
            });
        }
    }

    synchronized private void postGATTRequest(GATTRequest request) {

            requestQueue.offer(request);

    }

    synchronized private void processGATTRequest() {
        GATTRequest request = requestQueue.poll();
        if (request != null) {
            request.process(bluetoothGatt);
        }
    }

    private static byte[] createManufactureData() {
        ByteBuffer bb = ByteBuffer.allocate(23);

        bb.putShort((short) 0x0215); //iBeacon
        bb.putLong(proximityUUID.getMostSignificantBits());
        bb.putLong(proximityUUID.getLeastSignificantBits());
        bb.putShort((short) 0x0001); //major
        bb.putShort((short) 0x0001); //minor
        bb.put((byte) 0xc5); //Tx Power

        return bb.array();
    }

    private static byte[] mask = {
            (byte)0x1, (byte)0x1,
            (byte)0x1, (byte)0x1, (byte)0x1, (byte)0x1, (byte)0x1, (byte)0x1, (byte)0x1, (byte)0x1,
            (byte)0x1, (byte)0x1, (byte)0x1, (byte)0x1, (byte)0x1, (byte)0x1, (byte)0x1, (byte)0x1,
            (byte)0x1, (byte)0x1,
            (byte)0x1, (byte)0x1,
            (byte)0x0
    };
}
