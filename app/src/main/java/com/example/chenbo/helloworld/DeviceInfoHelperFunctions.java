package com.example.chenbo.helloworld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * Created by edward on 5/6/18.
 */

public class DeviceInfoHelperFunctions {

    public static String makeDeviceInfo (String deviceName, String deviceAddress) {
        return "Device name: " + deviceName + "\nDevice Address:\n" + deviceAddress;
    }

    public static String getDeviceNameFromDeviceInfo (String deviceInfo) {

        return deviceInfo.substring(deviceInfo.indexOf(":") + 2, deviceInfo.indexOf("\n"));

    }

    public static String getDeviceAddressFromDeviceInfo (String deviceInfo) {

        return deviceInfo.substring(deviceInfo.lastIndexOf("\n") + 1);

    }

    // scanRecords are not currently used, but they can be eventually if we want to put the device's location information
    // into the 31 byte advertisement packet payload
    public static String discoveredDeviceInfoToString(ArrayList<String> devices, HashMap<String, byte[]> scanRecords) {

        String list = "";

        for (int i = 0; i < devices.size(); i++) {

            String[] deviceSplit = devices.get(i).split("\n");
            list += deviceSplit[0] + "," + deviceSplit[1] + " " + deviceSplit[2] + "\n";

        }

        return list;
    }

    public static String createLocationBeaconInRangeList(HashMap<String, String> addressToLocation, Set<String> deviceInfos) {

        String locationBeaconsInRangeList = "";

        for (String deviceInfo : deviceInfos) {
            String deviceLocation = addressToLocation.get(BluetoothHelperFunctions.removeColonsFromMACAddress(getDeviceAddressFromDeviceInfo(deviceInfo)));
            if (deviceLocation != null) {
                locationBeaconsInRangeList += deviceInfo + "\n"
                        + "Device location: " +
                        deviceLocation
                        +"\n---\n";
            }
        }

        return locationBeaconsInRangeList;
    }

}
