package com.example.chenbo.helloworld;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.List;

/**
 * Created by edward on 3/13/18.
 */

public class BluetoothHelperFunctions {

    // this is the number of characters in a MAC address including colons
    private static final int MAC_ADDRESS_LENGTH = 17;

    // this is the number of characters in a MAC address with no colons
    private static final int MAC_ADDRESS_LENGTH_NO_COLONS = 12;

    public static String createServicesInfoString(List<BluetoothGattService> services) {
        String discoveredServicesString = "";

        for (BluetoothGattService service : services) {
            String serviceUUID = "S " + service.getUuid().toString();

            List<BluetoothGattCharacteristic> discoveredCharacteristics = service.getCharacteristics();

            discoveredServicesString += serviceUUID + "\n";

            for (BluetoothGattCharacteristic characteristic : discoveredCharacteristics) {

                String characteristicUUID = "C "  + characteristic.getUuid().toString();

                int properties = characteristic.getProperties();

                String propertiesString = checkCharacteristicProperties(properties);

                List<BluetoothGattDescriptor> discoveredDescriptors = characteristic.getDescriptors();

                discoveredServicesString += characteristicUUID + " " + propertiesString + "\n";

                for (BluetoothGattDescriptor descriptor : discoveredDescriptors) {

                    String descriptorUUID = "D " + descriptor.getUuid().toString();

                    int permissions = descriptor.getPermissions();

                    String permissionsString = checkDescriptorPermissions(permissions);

                    discoveredServicesString += descriptorUUID + " " + permissionsString + "\n";

                }

            }
        }

        return discoveredServicesString;
    }

    public static String checkCharacteristicProperties(int properties) {
        String propertiesString = "";

        if ((properties & BluetoothGattCharacteristic.PROPERTY_BROADCAST) > 0) {
            propertiesString += "B-";
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) > 0) {
            propertiesString += "EP-";
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
            propertiesString += "I-";
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            propertiesString += "N-";
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
            propertiesString += "R-";
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) > 0) {
            propertiesString += "SW-";
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
            propertiesString += "W-";
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
            propertiesString += "WNR-";
        }

        return propertiesString;
    }


    // kind of strange, i was trying to make this work and then read somewhere online that descriptor
    // permissions don't work or something? to be honest i'm vague on this, but i know this function
    // might not work
    public static String checkDescriptorPermissions(int permissions) {
        String permissionsString = "";

        if ((permissions & BluetoothGattDescriptor.PERMISSION_READ) > 0) {
            permissionsString += "R-";
        }
        if ((permissions & BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED) > 0) {
            permissionsString += "RE-";
        }
        if ((permissions & BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM) > 0) {
            permissionsString += "REM-";
        }
        if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE) > 0) {
            permissionsString += "W-";
        }
        if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED) > 0) {
            permissionsString += "WE-";
        }
        if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM) > 0) {
            permissionsString += "WEM-";
        }
        if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED) > 0) {
            permissionsString += "WS-";
        }
        if ((permissions & BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED_MITM) > 0) {
            permissionsString += "WSM-";
        }

        return permissionsString;
    }

    // the reason for the functions to add and remove colons from MAC addresses is because when
    // i was sending colon characters over ndn they were turned into %3% in interest and data names;
    // i don't know if i was doing something wrong but just to fix it real quick i just sent mac
    // addresses over ndn with no colons and have these functions to add and remove colons to do this
    // (you need the colons in the mac address string to use the BLE API of android)
    public static String addColonsToMACAddress(String address) {

        if (address.length() < 12)
            return "";

        StringBuilder sb = new StringBuilder(MAC_ADDRESS_LENGTH);

        sb.append(address.substring(0,2));
        sb.append(':');
        sb.append(address.substring(2,4));
        sb.append(':');
        sb.append(address.substring(4,6));
        sb.append(':');
        sb.append(address.substring(6,8));
        sb.append(':');
        sb.append(address.substring(8,10));
        sb.append(':');
        sb.append(address.substring(10,12));

        return sb.toString();
    }

    public static String removeColonsFromMACAddress(String address) {

        if (address.length() < 17)
            return "";

        StringBuilder sb = new StringBuilder(MAC_ADDRESS_LENGTH_NO_COLONS);

        sb.append(address.substring(0,2));
        sb.append(address.substring(3,5));
        sb.append(address.substring(6,8));
        sb.append(address.substring(9,11));
        sb.append(address.substring(12,14));
        sb.append(address.substring(15,17));

        String noColons = sb.toString();

        //Log.d("MAC string builder ", noColons);

        return noColons;
    }

    // Bluetooth Spec V4.0 - Vol 3, Part C, section 8
    public static String parseScanRecord(byte[] scanRecord) {
        StringBuilder output = new StringBuilder();
        int i = 0;
        while (i < scanRecord.length) {
            int len = scanRecord[i++] & 0xFF;
            if (len == 0) break;
            switch (scanRecord[i] & 0xFF) {
                case 0x01:
                    output.append("«Flags»: ")
                            .append(HexAsciiHelper.bytesToHex(scanRecord, i+1, len))
                            .append("\n");
                    break;
                case 0x02:
                    output.append("«Incomplete List of 16-bit Service Class UUIDs»: ")
                            .append(HexAsciiHelper.bytesToHex(scanRecord, i+1, len))
                            .append("\n");
                    break;
                case 0x03:
                    output.append("«Complete List of 16-bit Service Class UUIDs»: ")
                            .append(HexAsciiHelper.bytesToHex(scanRecord, i+1, len))
                            .append("\n");
                    break;
                case 0x04:
                    output.append("«Incomplete List of 32-bit Service Class UUIDs»: ")
                            .append(HexAsciiHelper.bytesToHex(scanRecord, i+1, len))
                            .append("\n");
                    break;
                case 0x05:
                    output.append("«Complete List of 32-bit Service Class UUIDs»: ")
                            .append(HexAsciiHelper.bytesToHex(scanRecord, i+1, len))
                            .append("\n");
                    break;
                case 0x06:
                    output.append("«Incomplete List of 128-bit Service Class UUIDs»: ")
                            .append(HexAsciiHelper.bytesToHex(scanRecord, i+1, len))
                            .append("\n");
                    break;
                case 0x07: // list of uuid's
                    output.append("«Complete List of 128-bit Service Class UUIDs»: ")
                            .append(HexAsciiHelper.bytesToHex(scanRecord, i+1, len))
                            .append("\n");
                    break;
                case 0x08:
                    output.append("«Shortened Local Name»: ")
                            .append(HexAsciiHelper.bytesToHex(scanRecord, i+1, len))
                            .append("\n");
                    break;
                case 0x09:
                    output.append("«Complete Local Name»: ")
                            .append(HexAsciiHelper.bytesToAsciiMaybe(scanRecord, i+1, len))
                            .append("\n");
                    break;
                // https://www.bluetooth.org/en-us/specification/assigned-numbers/generic-access-profile
                case 0x0A: // Tx Power
                    output.append("«Tx Power Level»: ").append(scanRecord[i+1])
                            .append("\n");
                    break;
                case 0xFF: // Manufacturer Specific data (RFduinoBLE.advertisementData)
                    output.append("«Manufacturer Specific Data»: ")
                            .append(HexAsciiHelper.bytesToHex(scanRecord, i + 1, len))
                            .append("\n");

                    String ascii = HexAsciiHelper.bytesToAsciiMaybe(scanRecord, i + 1, len);
                    if (ascii != null) {
                        output.append(" (\"").append(ascii).append("\")")
                                .append("\n");
                    }
                    break;
            }
            i += len;
        }
        return output.toString();
    }
}
