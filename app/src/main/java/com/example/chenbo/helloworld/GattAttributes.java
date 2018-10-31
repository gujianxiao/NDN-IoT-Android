package com.example.chenbo.helloworld;

import java.util.HashMap;

/**
 * Adapted from https://developer.android.com/samples/BluetoothLeGatt/src/com.example.android.bluetoothlegatt/SampleGattAttributes.html.
 * Just added more services and characteristics than there were in the original, which just had a couple for demonstration purposes.
 */

public class GattAttributes {
    private static HashMap<String, String> attributes = new HashMap();

    // services
    public static String GENERIC_ACCESS = "00001800-0000-1000-8000-00805f9b34fb";
    public static String HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb";
    public static String DEVICE_INFORMATION_SERVICE = "0000180a-0000-1000-8000-00805f9b34fb";
    public static String GENERIC_ATTRIBUTE_SERVICE = "00001801-0000-1000-8000-00805f9b34fb";

    // characteristics
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb";
    public static String APPEARANCE = "00002a01-0000-1000-8000-00805f9b34fb";
    public static String PERIPHERAL_PRIVACY_FLAG = "00002a02-0000-1000-8000-00805f9b34fb";
    public static String RECONNECTION_ADDRESS = "00002a03-0000-1000-8000-00805f9b34fb";
    public static String PERIPHERAL_PREFERRED_CONNECTION_PARAMETERS = "00002a04-0000-1000-8000-00805f9b34fb";
    public static String SERVICE_CHANGED = "00002a05-0000-1000-8000-00805f9b34fb";
    public static String MODEL_NUMBER_STRING = "00002a24-0000-1000-8000-00805f9b34fb";

    // descriptors
    public static String CHARACTERISTIC_USER_DESCRIPTION = "00002901-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIGURATION = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        // Sample Services.
        attributes.put(HEART_RATE_SERVICE, "Heart Rate Service");
        attributes.put(DEVICE_INFORMATION_SERVICE, "Device Information Service");
        attributes.put(GENERIC_ACCESS, "Generic Access");
        attributes.put(GENERIC_ATTRIBUTE_SERVICE, "Generic Attribute Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        attributes.put(DEVICE_NAME, "Device Name");
        attributes.put(APPEARANCE, "Appearance");
        attributes.put(PERIPHERAL_PRIVACY_FLAG, "Peripheral Privacy Flag");
        attributes.put(RECONNECTION_ADDRESS, "Reconnection Address");
        attributes.put(PERIPHERAL_PREFERRED_CONNECTION_PARAMETERS, "Peripheral Preferred Connection Parameters");
        attributes.put(SERVICE_CHANGED, "Service Changed");
        attributes.put(MODEL_NUMBER_STRING, "Model Number String");
        // Sample Descriptors.
        attributes.put(CHARACTERISTIC_USER_DESCRIPTION, "Characteristic User Description");
        attributes.put(CLIENT_CHARACTERISTIC_CONFIGURATION, "Client Characteristic Configuration");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}