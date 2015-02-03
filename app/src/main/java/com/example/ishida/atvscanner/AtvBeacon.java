package com.example.ishida.atvscanner;

import android.bluetooth.BluetoothDevice;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by ishida on 2015/01/20.
 */
public class AtvBeacon {

    public UUID proximityUUID;
    public int major;
    public int minor;
    public int power;

    String address;
    long lastUpdate;

    private ByteBuffer bb;

    static public AtvBeacon create(BluetoothDevice device, byte[] scanRecord) {
        if (isBeacon(scanRecord)) {
            return new AtvBeacon(device.getAddress(), scanRecord);
        } else {
            return null;
        }
    }

    static public AtvBeacon parse(AtvBeacon beacon, byte[] scanRecord) {
        if (isBeacon(scanRecord)) {
            beacon.bb = ByteBuffer.wrap(scanRecord);
            beacon.parseBeacon();
            beacon.lastUpdate = System.currentTimeMillis();
            return beacon;
        } else {
            return null;
        }


    }

    private AtvBeacon(String address, byte[] scanRecord) {
        this.address = new String(address);
        bb = ByteBuffer.wrap(scanRecord);
        parseBeacon();
        lastUpdate = System.currentTimeMillis();
    }

    AtvBeacon(String address) {
        this.address = address;
    }

    public AtvBeacon() {};

    @Override
    public boolean equals(Object object) {
        AtvBeacon beacon = (AtvBeacon)object;

        return this.address.equals(beacon.address);
    }

    private void parseBeacon() {
        //DO NOT CHANGE ORDER
        proximityUUID = parseUUID();
        major = parseMajor();
        minor = parseMinor();
        power = parsePower();
    }

    private UUID parseUUID() {
        bb.position(9);
        return new UUID(bb.getLong(), bb.getLong());
    }

    private int parseMajor() {
        //bb.position(25);
        return bb.getChar();
    }

    private int parseMinor() {
        //bb.position(27);
        return bb.getChar();
    }

    private int parsePower() {
        //bb.position(29);
        return bb.get();
    }



    public UUID getProximityUUID() {
        return proximityUUID;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPower() {
        return power;
    }


    public String getLastUpdate() {
        Date date = new Date(lastUpdate);
        return date.toString();
    }

    public Map<String, Object> map() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("proximityUUID", proximityUUID.toString());
        map.put("major", major);
        map.put("minor", minor);
        map.put("power", power);
        map.put("lastUpdate", getLastUpdate());

        return map;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("uuid=" + proximityUUID.toString());
        buf.append(", major=" + major);
        buf.append(", minor=" + minor);
        buf.append(", power=" + power);
        return buf.toString();
    }

    static private boolean isBeacon(byte[] scanRecord) {
        boolean flag = false;
        if(scanRecord.length > 30)
        {
            if((scanRecord[5] == (byte)0x4c) && (scanRecord[6] == (byte)0x00) &&
                    (scanRecord[7] == (byte)0x02) && (scanRecord[8] == (byte)0x15)) {
                flag = true;
            }
        }
        return flag;
    }

}
