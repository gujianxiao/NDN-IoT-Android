package com.example.chenbo.helloworld;


import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothGatt.GATT_FAILURE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;

/*
 * Adapted from:
 * http://developer.android.com/samples/BluetoothLeGatt/src/com.example.android.bluetoothlegatt/BluetoothLeService.html
 */

public class BLEService extends Service {
    private final static String TAG = BLEService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    // a mapping of device mac address to the bluetoothgatt object associated with its connection, devices that we are connected to
    // are added to this and then removed when disconnected
    public HashMap<String, BluetoothGatt> activeDevices;

    // the strings for broadcasts to MainActivity that are directly relevant to getting data from the arduino
    public final static String ACTION_CONNECTING = "ACTION_CONNECTING";
    public final static String ACTION_CONNECTED = "ACTION_CONNECTED";
    public final static String ACTION_DISCONNECTED = "ACTION_DISCONNECTED";
    public final static String GOT_DATA_FROM_ARDUINO = "GOT_DATA_FROM_ARDUINO";
    public final static String ADD_CONNECTED_DEVICE_LIST = "ADD_CONNECTED_DEVICE_LIST";
    public final static String REMOVE_CONNECTED_DEVICE_LIST = "REMOVE_CONNECTED_DEVICE_LIST";

    // strings for broadcasts to MainActivity that are not as directly relevant to getting data from the arduino
    // (they are kept here just in case they are needed in the future)
    public final static String ACTION_DISCOVERING_SERVICES = "ACTION_DISCOVERING_SERVICES";
    public final static String ACTION_SERVICES_DISCOVERED = "ACTION_SERVICES_DISCOVERED";
    public final static String ACTION_SERVICE_DISCOVERY_FAILED = "ACTION_SERVICE_DISCOVERY_FAILED";
    public final static String CHARACTERISTIC_CHANGE_DATA_AVAILABLE = "CHARACTERISTIC_CHANGE_DATA_AVAILABLE";
    public final static String ACTION_WRITE_SUCCESS = "ACTION_WRITE_SUCCESS";
    public final static String CHARACTERISTIC_READ_ACTION_DATA_AVAILABLE = "CHARACTERISTIC_READ_ACTION_DATA_AVAILABLE";
    public final static String DESCRIPTOR_READ_ACTION_DATA_AVAILABLE = "DESCRIPTOR_READ_ACTION_DATA_AVAILABLE";
    public final static String ACTION_NOTIFY_DATA_AVAILABLE = "ACTION_NOTIFY_DATA_AVAILABLE";

    // string constants for values to be attached to intents
    public final static String ACTION_VALUE = "ACTION_VALUE";
    public final static String UUID_LIST = "UUID_LIST";
    public final static String MAC_ADDRESS = "MAC_ADDRESS";
    public final static String MAC_ADDRESS_NO_COLONS = "MAC_ADDRESS";
    public final static String CONNECTION_INFORMATION = "CONNECTION_INFORMATION";
    public final static String DATA_PACKET_AS_BYTES = "DATA_PACKET_AS_BYTES";
    public final static String DEVICE_INFO = "DEVICE_INFO";
    public final static String NOTIFY_STATUS = "NOTIFY_STATUS";

    // constants to check for characteristic properties
    public final static String CHARACTERISTIC_READ = "CHARACTERISTIC_READ";
    public final static String CHARACTERISTIC_WRITE = "CHARACTERISTIC_WRITE";
    public final static String CHARACTERISTIC_NOTIFY = "CHARACTERISTIC_NOTIFY";
    public final static String CHARACTERISTIC_INDICATE = "CHARACTERISTIC_INDICATE";

    // harcoded service and characteristic uuids for rfduino
    public static String hardcodedServiceUuid = "ccc433f0-be8f-4dc8-b6f0-5343e6100eb4";
    public static String hardcodedReadAndNotifyCharacteristicUuid = "ccc433f1-be8f-4dc8-b6f0-5343e6100eb4";
    public static String hardcodedWriteCharacteristicUuid = "ccc433f2-be8f-4dc8-b6f0-5343e6100eb4";

    // *** hardcoded service and characteristic uuids for older version of beacon using arduino uno and HM10 ble module
    // hard coded service uuid and characteristic uuid; should eventually be replaced by some uuids used for ice ar service/characteristic
    // these are the uuids of the service and characteristic for serial reading and writing from the Bluetooth module used for demonstration,
    // the HM10 module: (https://www.amazon.com/DSD-TECH-Bluetooth-iBeacon-Arduino/dp/B06WGZB2N4/ref=sr_1_1?ie=UTF8&qid=1525497876&sr=8-1&keywords=hm10)
    //public static String hardcodedServiceUuid = "0000ffe0-0000-1000-8000-00805f9b34fb";
    //public static String hardcodedReadAndNotifyCharacteristicUuid = "0000ffe1-0000-1000-8000-00805f9b34fb";
    //public static String hardcodedWriteCharacteristicUuid = "0000ffe1-0000-1000-8000-00805f9b34fb";

    // some variables to keep track of fragmented packets being received over BLE
    /********************************************************************************************/

    // mapping of MAC address to a device's current receiving state
    HashMap<String, ReceivingPacketState> receivingStates;

    // the maximum amount of milliseconds to wait after entering a receiving state to abort it as a failure if it doesnt finish in time
    final int MAX_RECEIVING_TIME = 4000;

    /*
    boolean receivingLengthMode = false;
    boolean receivingFragmentsMode = false;
    int currentReceivingPacketLength = 0;
    byte[] currentReceivedByteArray = {};
    int currentReceivedFragmentOffset = 0;
    // 0 means receiving data, 1 means receiving interest
    int receivingDataOrInterest = 0;
    byte[] copyOfLastReceivedByteArray = {};
    */

    // constant to check for receiving status of byte array
    public final static String DONE_RECEIVING_BYTE_ARRAY = "DONE_RECEIVING_BYTE_ARRAY";
    public final static String RECEIVING_INFO = "RECEIVING_INFO";
    public final static String RECEIVING_STATUS_FAILURE = "RECEIVING_STATUS_FAILURE";
    public final static String RECEIVING_STATUS_SUCCESS = "RECEIVING_STATUS_SUCCESS";

    // broadcastreceiver to listen for when we have finished receiving a packet as a byte array; after we are done receiving,
    // we do processing on the byte array
    BroadcastReceiver doneReceivingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(DONE_RECEIVING_BYTE_ARRAY)) {

                String[] receivingInfo = intent.getExtras().getStringArray(RECEIVING_INFO);
                String receivingStatus = receivingInfo[1];

                String deviceAddress = receivingInfo[0];

                ReceivingPacketState currentReceivingPacketState = getCurrentReceivingPacketState(deviceAddress, "Done receiving receiver");

                if (currentReceivingPacketState == null) {
                    return;
                }

                if (currentReceivingPacketState.currentReceivedByteArray == null) {
                    return;
                }

                if (receivingStatus.equals(RECEIVING_STATUS_SUCCESS)) {
                    Log.d(TAG, "Done receiving receiver: we got a signal that we successfully received a byte array over BLE.");

                }
                else if (receivingStatus.equals(RECEIVING_STATUS_FAILURE)) {
                    Log.w(TAG, "Done receiving receiver: we got a signal that we failed to receive a byte array over BLE.");

                }


                for (int i = 0; i < currentReceivingPacketState.currentReceivingPacketLength; i++) {
                    Log.d(TAG, "received array element " + i + ": " + Integer.toString(Byte.toUnsignedInt(
                            currentReceivingPacketState.currentReceivedByteArray[i])));
                }


                currentReceivingPacketState.copyOfLastReceivedByteArray = new byte[currentReceivingPacketState.currentReceivedByteArray.length];

                currentReceivingPacketState.copyOfLastReceivedByteArray =
                        Arrays.copyOfRange(currentReceivingPacketState.currentReceivedByteArray,
                                0,
                                currentReceivingPacketState.currentReceivedByteArray.length);

                if (currentReceivingPacketState.receivingDataOrInterest == 0) {
                    NFDService.interpretByteArrayAsData(currentReceivingPacketState.copyOfLastReceivedByteArray);

                    Intent gotDataFromArduinoIntent = new Intent(GOT_DATA_FROM_ARDUINO);

                    gotDataFromArduinoIntent.putExtra(DATA_PACKET_AS_BYTES, currentReceivingPacketState.copyOfLastReceivedByteArray);
                    gotDataFromArduinoIntent.putExtra(MAC_ADDRESS, deviceAddress);

                    sendBroadcast(gotDataFromArduinoIntent);
                }
                else if (currentReceivingPacketState.receivingDataOrInterest == 1)
                    NFDService.interpretByteArrayAsInterest(currentReceivingPacketState.copyOfLastReceivedByteArray);

                // resets all the variables associated with recieving a packet byte array over BLE
                currentReceivingPacketState.receivingLengthMode = false;
                currentReceivingPacketState.receivingFragmentsMode = false;
                currentReceivingPacketState.currentReceivingPacketLength = 0;
                currentReceivingPacketState.currentReceivedByteArray = null;
                currentReceivingPacketState.currentReceivedFragmentOffset = 0;

            }
        }
    };

    private void broadcastReceivingSuccess(String deviceAddress) {

        Intent doneReceivingByteArrayIntent = new Intent(DONE_RECEIVING_BYTE_ARRAY);

        String[] receivingInfo = new String[2];

        receivingInfo[0] = deviceAddress;
        receivingInfo[1] = RECEIVING_STATUS_SUCCESS;

        doneReceivingByteArrayIntent.putExtra(RECEIVING_INFO, receivingInfo);

        LocalBroadcastManager.getInstance(BLEService.this).sendBroadcast(doneReceivingByteArrayIntent);

    }

    private void broadcastReceivingFailure(String deviceAddress) {

        Intent doneReceivingByteArrayIntent = new Intent(DONE_RECEIVING_BYTE_ARRAY);

        String[] receivingInfo = new String[2];

        receivingInfo[0] = deviceAddress;
        receivingInfo[1] = RECEIVING_STATUS_FAILURE;

        doneReceivingByteArrayIntent.putExtra(RECEIVING_INFO, receivingInfo);

        LocalBroadcastManager.getInstance(BLEService.this).sendBroadcast(doneReceivingByteArrayIntent);

    }

    /********************************************************************************************/

    // some variables to keep track of fragmented packets being sent over BLE
    // have to fragment because BLE API only supports sending arrays of 20 or less bytes; will cut off any more
    /********************************************************************************************/

    // mapping of MAC address to sending packet state
    HashMap<String, SendingPacketState> sendingStates;

    // maximum amount of time to wait after starting sending to abort it as a failure
    int MAX_SENDING_TIME = 4000;

    /*
    byte[] currentSendingByteArray = {};
    String currentAddressForFragments = "";
    boolean sendingOutFragmentsMode = false;
    int fragmentOffset = 0;
    */

    // constant to check for sending status of byte array
    public final static String DONE_SENDING_BYTE_ARRAY = "DONE_SENDING_BYTE_ARRAY";
    public final static String SENDING_INFO = "SENDING_INFO";
    public final static String SENDING_STATUS_SUCCESS = "SENDING_STATUS_SUCCESS";
    public final static String SENDING_STATUS_FAILURE = "SENDING_STATUS_FAILURE";

    // broadcastreceiver to detect when we are done sending a packet byte array over BLE
    BroadcastReceiver doneSendingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(DONE_SENDING_BYTE_ARRAY)) {

                String[] sendingInfo = intent.getExtras().getStringArray(SENDING_INFO);
                String receivingStatus = sendingInfo[1];

                if (receivingStatus.equals(SENDING_STATUS_SUCCESS)) {
                    Log.d(TAG, "Done sending receiver: we got a signal that we successfully sent a byte array over BLE.");
                } else if (receivingStatus.equals(SENDING_STATUS_FAILURE)) {
                    Log.d(TAG, "Done sending receiver: we got a signal that we failed to send a byte array over BLE.");
                }

                String deviceAddress = sendingInfo[0];

                SendingPacketState currentSendingPacketState = getCurrentSendingPacketState(deviceAddress, "Done sending receiver");

                if (currentSendingPacketState == null) {
                    return;
                }

                // resets variables associated with sending a packet byte array over BLE
                Log.d(TAG, deviceAddress+"---" + "Setting sendingOutfragmentsMode to false from within on done receiving.");
                currentSendingPacketState.sendingOutFragmentsMode = false;
                currentSendingPacketState.fragmentOffset = 0;

            }
        }
    };

    private void broadcastSendingSuccess(String deviceAddress) {
        Intent doneSendingByteArrayIntent = new Intent(DONE_SENDING_BYTE_ARRAY);

        String[] sendingInfo = new String[2];

        sendingInfo[0] = deviceAddress;
        sendingInfo[1] = SENDING_STATUS_SUCCESS;

        doneSendingByteArrayIntent.putExtra(SENDING_INFO, sendingInfo);

        LocalBroadcastManager.getInstance(BLEService.this).sendBroadcast(doneSendingByteArrayIntent);
    }

    private void broadcastSendingFailure(String deviceAddress) {
        Intent doneSendingByteArrayIntent = new Intent(DONE_SENDING_BYTE_ARRAY);

        String[] sendingInfo = new String[2];

        sendingInfo[0] = deviceAddress;
        sendingInfo[1] = SENDING_STATUS_FAILURE;

        doneSendingByteArrayIntent.putExtra(SENDING_INFO, sendingInfo);

        LocalBroadcastManager.getInstance(BLEService.this).sendBroadcast(doneSendingByteArrayIntent);
    }

    /********************************************************************************************/

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            String deviceAddress = gatt.getDevice().getAddress();
            String noColonsInMACAddress = BluetoothHelperFunctions.removeColonsFromMACAddress(deviceAddress);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to BLE Device.");

                Intent connectedIntent = new Intent(ACTION_CONNECTED);

                if (!activeDevices.containsKey(deviceAddress)) {

                    activeDevices.put(deviceAddress, gatt);

                    if (activeDevices.get(deviceAddress) == null) {
                        Log.d(TAG, "null gatt object for given device address from on connection state change");
                    }

                    String deviceInfo = DeviceInfoHelperFunctions.makeDeviceInfo(gatt.getDevice().getName(), deviceAddress);
                    Intent updateConnectedDevicesListIntent = new Intent(ADD_CONNECTED_DEVICE_LIST);
                    updateConnectedDevicesListIntent.putExtra(DEVICE_INFO, deviceInfo);
                    sendBroadcast(updateConnectedDevicesListIntent);
                }

                String[] information = new String[2];
                information[0] = BluetoothHelperFunctions.removeColonsFromMACAddress(gatt.getDevice().getAddress());
                information[1] = gatt.getDevice().getName();

                connectedIntent.putExtra(CONNECTION_INFORMATION, information);

                Log.d(TAG, deviceAddress+"---" + "getDataPacketFromArduinoAfterConnecting called after getting conencted signal.");
                getDataPacketFromArduinoAfterConnecting(gatt.getDevice().getAddress());

                sendBroadcast(connectedIntent);

                if (sendingStates.get(deviceAddress) != null) {
                    SendingPacketState currentSendingPacketState = getCurrentSendingPacketState(deviceAddress,"on Connected");

                    // resets variables associated with sending a packet byte array over BLE
                    Log.d(TAG, deviceAddress+"---" + "Setting sendingOutfragmentsMode to false from within on connected.");
                    currentSendingPacketState.sendingOutFragmentsMode = false;
                    currentSendingPacketState.fragmentOffset = 0;
                }

                if (receivingStates.get(deviceAddress) != null) {
                    ReceivingPacketState currentReceivingPacketState = getCurrentReceivingPacketState(deviceAddress, "on Connected");

                    // resets all the variables associated with recieving a packet byte array over BLE
                    currentReceivingPacketState.receivingLengthMode = false;
                    currentReceivingPacketState.receivingFragmentsMode = false;
                    currentReceivingPacketState.currentReceivingPacketLength = 0;
                    currentReceivingPacketState.currentReceivedByteArray = null;
                    currentReceivingPacketState.currentReceivedFragmentOffset = 0;
                }


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from BLE device.");

                Intent disconnectedIntent = new Intent(ACTION_DISCONNECTED);

                String[] information = new String[2];
                information[0] = BluetoothHelperFunctions.removeColonsFromMACAddress(gatt.getDevice().getAddress());
                information[1] = gatt.getDevice().getName();

                disconnectedIntent.putExtra(CONNECTION_INFORMATION, information);
                disconnectedIntent.putExtra(ACTION_DISCONNECTED, noColonsInMACAddress);

                sendBroadcast(disconnectedIntent);

                BluetoothGatt tempGatt = activeDevices.get(deviceAddress);

                if (tempGatt != null) {
                    tempGatt.close();
                    Log.d(TAG, "closed a gatt");
                }

                if (!getAllConnectedDevices().contains(gatt.getDevice().getAddress())) {
                    Log.d(TAG, "removing device with address: " + deviceAddress + " from list of active devices");
                    activeDevices.remove(deviceAddress);
                }
                else {
                    Log.d(TAG, "Disconnect for device with address " + gatt.getDevice().getAddress() + " failed, reattempting...");
                    gatt.disconnect();
                }

                String deviceInfo = DeviceInfoHelperFunctions.makeDeviceInfo(gatt.getDevice().getName(), deviceAddress);
                Intent updateConnectedDevicesListIntent = new Intent(REMOVE_CONNECTED_DEVICE_LIST);
                updateConnectedDevicesListIntent.putExtra(DEVICE_INFO, deviceInfo);
                sendBroadcast(updateConnectedDevicesListIntent);

                /*
                for (String addr : activeDevices.keySet()) {
                    Log.d(TAG, "CURRENT ADDRESSES IN ACTIVE DEVICES OF BLESERVICE: " + addr);
                }
                */

                if (sendingStates.get(deviceAddress) != null) {
                    SendingPacketState currentSendingPacketState = getCurrentSendingPacketState(deviceAddress,"on Connected");

                    // resets variables associated with sending a packet byte array over BLE
                    Log.d(TAG, deviceAddress+"---" + "Setting sendingOutfragmentsMode to false from within on disconnected.");
                    currentSendingPacketState.sendingOutFragmentsMode = false;
                    currentSendingPacketState.fragmentOffset = 0;
                }

                if (receivingStates.get(deviceAddress) != null) {
                    ReceivingPacketState currentReceivingPacketState = getCurrentReceivingPacketState(deviceAddress, "on Connected");

                    // resets all the variables associated with recieving a packet byte array over BLE
                    currentReceivingPacketState.receivingLengthMode = false;
                    currentReceivingPacketState.receivingFragmentsMode = false;
                    currentReceivingPacketState.currentReceivingPacketLength = 0;
                    currentReceivingPacketState.currentReceivedByteArray = null;
                    currentReceivingPacketState.currentReceivedFragmentOffset = 0;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            Log.d(TAG, gatt.getDevice().getAddress()+"---" + "entered on services discovered");

            String macAddressNoColons = BluetoothHelperFunctions.removeColonsFromMACAddress(gatt.getDevice().getAddress());

            if (status == GATT_SUCCESS) {
                List<BluetoothGattService> discoveredServices = gatt.getServices();

                String discoveredServicesString = BluetoothHelperFunctions.createServicesInfoString(discoveredServices);

                Intent servicesDiscoveredIntent = new Intent(ACTION_SERVICES_DISCOVERED);

                String[] information = new String[3];
                information[0] = discoveredServicesString;
                information[1] = macAddressNoColons;
                information[2] = gatt.getDevice().getName();

                servicesDiscoveredIntent.putExtra(CONNECTION_INFORMATION, information);

                Log.d(TAG, "SUCCESSFULLY DISCOVERED SERVICES");

                Log.d(TAG, gatt.getDevice().getAddress()+"---" + "getDataPacketFromArduinoAfterDiscoveringServices called " +
                        "from within onServicesdiscovered.");
                getDataPacketFromArduinoAfterDiscoveringServices(gatt.getDevice().getAddress());

                sendBroadcast(servicesDiscoveredIntent);
            }
            else if (status == GATT_FAILURE) {
                Intent serviceDiscoveryFailedIntent = new Intent(ACTION_SERVICE_DISCOVERY_FAILED);
                serviceDiscoveryFailedIntent.putExtra(MAC_ADDRESS_NO_COLONS, macAddressNoColons);
                sendBroadcast(serviceDiscoveryFailedIntent);
            }
            else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == GATT_SUCCESS) {
                Log.d(TAG, "we got a callback that we successfully read a characteristic");
                broadcastUpdate(CHARACTERISTIC_READ_ACTION_DATA_AVAILABLE, gatt.getDevice().getAddress(), characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            String deviceAddress = gatt.getDevice().getAddress();

            Log.d(TAG, "there was a change in the BTLE module's characteristic: " + characteristic.getStringValue(0));
            broadcastUpdate(CHARACTERISTIC_CHANGE_DATA_AVAILABLE, gatt.getDevice().getAddress(), characteristic);

            boolean allTildes = true;
            boolean allDashes = true;
            byte[] receivedData = characteristic.getValue();

            boolean modeOne = true;
            boolean modeTwo = true;

            // these are the messages that the arduino is coded to send back to us through the BLE module
            // when it switches modes
            // the read modes on the arduino are as follows:
            // mode 0: means that the arduino is waiting for a '~' to put it into length reading mode (mode 1)
            // mode 1: means arduino got a '~' character, arduino is now waiting for a packet length (integer in short format), once arduino gets the length it goes into packet reading mode (mode 2)
            // mode 2: means arduino got the packet length, arduino is now reading as many bytes as the packet length, and then doing processing on the full packet, after
            //         which it goes back into mode 0
            String modeOneString = "mode 1             /";
            String modeTwoString = "mode 2             /";

            for (int i = 0; i < receivedData.length && i < modeOneString.length(); i++) {
                if (receivedData[i] != modeOneString.charAt(i))
                    modeOne = false;
                if (receivedData[i] != modeTwoString.charAt(i))
                    modeTwo = false;
            }

            if (modeOne) {
                Log.d(TAG, "we got a signal from the arduino that it's in mode 1, address: " + deviceAddress);
                sendPacketLength(gatt.getDevice().getAddress());
            }

            if (modeTwo) {
                Log.d(TAG, "we got a signal from the arduino that it's in mode 2, address: " + deviceAddress);
                sendActualPacket(gatt.getDevice().getAddress());
            }


            for (byte b : receivedData) {
                if (b != '~') {
                    // a string of 20 tilde characters is how the arduino tells us that it is about to
                    // send us a data packet; first it will send us two bytes for the packet length, and then
                    // it will send us the actual packet
                    allTildes = false;
                }
                if (b != '-') {
                    // a string of 20 dash characters is how the arduino tells us that it is about to
                    // send us an interest packet; first it will send us two bytes for the packet length, and then
                    // it will send us the actual packet
                    allDashes = false;
                }
            }

            if (allTildes) {
                Log.d(TAG, "we got signal from arduino to enter length receiving mode for a data packet");
                if (!receivingStates.containsKey(deviceAddress)) {
                    receivingStates.put(deviceAddress, new ReceivingPacketState());
                } else {
                    ReceivingPacketState currentReceivingPacketState = getCurrentReceivingPacketState(deviceAddress, "allTildes, onCharacteristicChanged");

                    if (currentReceivingPacketState == null)
                        return;

                    // resets all the variables associated with recieving a packet byte array over BLE
                    currentReceivingPacketState.receivingLengthMode = false;
                    currentReceivingPacketState.receivingFragmentsMode = false;
                    currentReceivingPacketState.currentReceivingPacketLength = 0;
                    currentReceivingPacketState.currentReceivedByteArray = null;
                    currentReceivingPacketState.currentReceivedFragmentOffset = 0;
                }

                ReceivingPacketState currentReceivingPacketState = getCurrentReceivingPacketState(deviceAddress, "allTildes, onCharacteristicChanged");

                if (currentReceivingPacketState == null)
                    return;

                currentReceivingPacketState.receivingDataOrInterest = 0;
                currentReceivingPacketState.receivingLengthMode = true;
            }
            else if (allDashes) {
                Log.d(TAG, "we got signal from arduino to enter length receiving mode for an interest packet");
                if (!receivingStates.containsKey(deviceAddress)) {
                    receivingStates.put(deviceAddress, new ReceivingPacketState());
                } else {
                    ReceivingPacketState currentReceivingPacketState = getCurrentReceivingPacketState(deviceAddress, "allDashes, onCharacteristicChanged");

                    if (currentReceivingPacketState == null)
                        return;

                    // resets all the variables associated with recieving a packet byte array over BLE
                    currentReceivingPacketState.receivingLengthMode = false;
                    currentReceivingPacketState.receivingFragmentsMode = false;
                    currentReceivingPacketState.currentReceivingPacketLength = 0;
                    currentReceivingPacketState.currentReceivedByteArray = null;
                    currentReceivingPacketState.currentReceivedFragmentOffset = 0;
                }

                ReceivingPacketState currentReceivingPacketState = getCurrentReceivingPacketState(deviceAddress, "allDashes, onCharacteristicChanged");

                if (currentReceivingPacketState == null)
                    return;

                currentReceivingPacketState.receivingDataOrInterest = 1;
                currentReceivingPacketState.receivingLengthMode = true;
            }
            // for now will assume the arduino uno will never send us a packet longer than max representable number by an unsigned short
            else if (receivingStates.get(deviceAddress) != null && receivingStates.get(deviceAddress).receivingLengthMode) {

                ReceivingPacketState currentReceivingPacketState = getCurrentReceivingPacketState(deviceAddress, "receivingLengthMode, onCharacteristicChanged");

                if (currentReceivingPacketState == null)
                    return;

                currentReceivingPacketState.receivingLengthMode = false;

                int indexOfFirstSpace = 0;
                for (int i = 0 ; i < 20; i++) {
                    if (receivedData[i] == 32) {
                        indexOfFirstSpace = i;
                        break;
                    }
                }

                if (indexOfFirstSpace == 0) {
                    Log.d(TAG, "there was some error in receiving length data; all spaces");
                }
                else if (indexOfFirstSpace == 1) {
                    currentReceivingPacketState.currentReceivingPacketLength = 0x000000 | receivedData[0];
                }
                else if (indexOfFirstSpace == 2) {
                    currentReceivingPacketState.currentReceivingPacketLength = ((receivedData[0] << 8) & 0x0000ff00) | (receivedData[1] & 0xff) & 0xffff;
                }
                else {
                    // problem; we only expect the arduino uno to send packets of 1000 bytes or less because
                    // of the arduino uno's memory connstraints, meaning
                    // it should not send us more than 2 bytes of length data
                    Log.d(TAG, "there was some error in receiving length data; number too large (more than 1000 bytes)");
                }

                Log.d(TAG, "received length: " + currentReceivingPacketState.currentReceivingPacketLength);

                currentReceivingPacketState.currentReceivedByteArray = new byte[currentReceivingPacketState.currentReceivingPacketLength];

                currentReceivingPacketState.receivingFragmentsMode = true;
            }
            else if (receivingStates.get(deviceAddress) != null && receivingStates.get(deviceAddress).receivingFragmentsMode) {

                ReceivingPacketState currentReceivingPacketState = getCurrentReceivingPacketState(deviceAddress, "receivingFragmentsMode, onCharacteristicChanged");

                if (currentReceivingPacketState == null)
                    return;

                Log.d(TAG, "detected characteristic change while in receiving fragments mode");
                Log.d(TAG, "current received fragment offset: " + Integer.toString(currentReceivingPacketState.currentReceivedFragmentOffset));

                int offsetInBytes = currentReceivingPacketState.currentReceivedFragmentOffset * 20;

                if ((currentReceivingPacketState.currentReceivedFragmentOffset + 1) * 20 > currentReceivingPacketState.currentReceivingPacketLength) {
                    // this means we are receiving the last fragment:

                    Log.d(TAG, "receiving last fragment with offset " + currentReceivingPacketState.currentReceivedFragmentOffset);

                    int lengthOfLastFragment = currentReceivingPacketState.currentReceivingPacketLength - (offsetInBytes);

                    for (int i = 0; i < lengthOfLastFragment; i++) {
                        currentReceivingPacketState.currentReceivedByteArray[i + offsetInBytes] = receivedData[i];
                    }

                    currentReceivingPacketState.receivingFragmentsMode = false;

                    broadcastReceivingSuccess(deviceAddress);
                }
                else {
                    // this means we are receiving beginning or middle fragments

                    Log.d(TAG, "receiving middle fragment with offset " + currentReceivingPacketState.currentReceivedFragmentOffset);

                    for (int i = 0; i < 20; i++) {
                        currentReceivingPacketState.currentReceivedByteArray[i + offsetInBytes] = receivedData[i];
                    }

                    currentReceivingPacketState.currentReceivedFragmentOffset++;
                }

            }
        }


        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            String deviceAddress = gatt.getDevice().getAddress();

            Log.d(TAG, deviceAddress+"---" + "entered on characteristic write.");

            if (status == GATT_SUCCESS) {
                Log.d(TAG, "we got a callback that we successfully wrote a characteristic");
                //broadcastUpdate(ACTION_WRITE_SUCCESS, characteristic);

                SendingPacketState currentSendingPacketState = getCurrentSendingPacketState(deviceAddress, "On Characteristic Write");

                if (currentSendingPacketState == null) {
                    broadcastSendingFailure(deviceAddress);
                    return;
                }

                if (currentSendingPacketState.sendingOutFragmentsMode) {
                    if (currentSendingPacketState.currentSendingByteArray != null) {
                        Log.d(TAG, "current byte array length: " + currentSendingPacketState.currentSendingByteArray.length);


                    Log.d(TAG, "current values in byte array: ");
                    for (int i = 0; i < currentSendingPacketState.currentSendingByteArray.length; i++) {
                        Log.d(TAG, Integer.toString(Byte.toUnsignedInt(currentSendingPacketState.currentSendingByteArray[i])));
                    }


                        Log.d(TAG, deviceAddress+"---" + "Calling finishSendingByteArrayOverbLE from within on Characteristic Write.");
                        finishSendingByteArrayOverBLE(gatt.getDevice().getAddress());
                    } else {
                        Log.d(TAG, "current byte array was null, sending intent to end sending process");

                        broadcastSendingSuccess(gatt.getDevice().getAddress());
                    }
                }
                else {
                    Log.d(TAG, deviceAddress+"---" + "Sending out fragments mode was false when we tried to continue sending the packet.");
                }
            }
            else {
                Log.d(TAG, "We got a callback that we failed to write a characteristic.");

                broadcastSendingFailure(deviceAddress);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == GATT_SUCCESS) {
                Log.d(TAG, "we got a callback that we successfully read a descriptor");
                broadcastUpdate(DESCRIPTOR_READ_ACTION_DATA_AVAILABLE, gatt.getDevice().getAddress(), descriptor);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);

            Log.d(TAG, gatt.getDevice().getAddress()+"---" + "entered on descriptor write.");
            sendTilde(gatt.getDevice().getAddress());
        }
    };

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {

        Log.d(TAG, "entering the connect function of bleService");

        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        if (getAllConnectedDevices().contains(address)) {
            Log.d(TAG, "already connected to device");
            return true;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        Log.d(TAG, "Trying to connect to remote device with the address: " + address);

        device.connectGatt(this, false, mGattCallback);

        Log.d(TAG, "Trying to create a new connection.");

        Intent connectingIntent = new Intent(ACTION_CONNECTING);

        String noColonsInMACAddress = BluetoothHelperFunctions.removeColonsFromMACAddress(address);
        connectingIntent.putExtra(MAC_ADDRESS_NO_COLONS, noColonsInMACAddress);

        Log.d(TAG, "no colon mac address" + noColonsInMACAddress);

        sendBroadcast(connectingIntent);

        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect(final String address) {

        BluetoothGatt currentGatt = activeDevices.get(address);

        if (mBluetoothAdapter == null || currentGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized OR no active connection for the address given");
            return;
        }
        currentGatt.disconnect();
    }

    /**
     * After service stops, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {

        if (activeDevices == null || activeDevices.size() == 0) {
            return;
        }

        for (String deviceAddress : activeDevices.keySet()) {
            activeDevices.get(deviceAddress).close();
            activeDevices.remove(deviceAddress);
        }
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristicUuid uuid of characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(String deviceAddress, String serviceUuid, String characteristicUuid,
                                              boolean enabled) {

        BluetoothGatt currentGatt = activeDevices.get(deviceAddress);

        if (mBluetoothAdapter == null || currentGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized OR no gatt object found for device address");
            return;
        }

        BluetoothGattCharacteristic characteristic = getCharacteristic(currentGatt, serviceUuid, characteristicUuid);

        if (characteristic == null) {
            Log.d(TAG, "set characteirstic notification failed because we didn't find the " +
                    "characteristic for given service and characteristic uuids");
            return;
        }

        if (!checkForCharacteristicProperties(characteristic, CHARACTERISTIC_NOTIFY)) {
            Log.d(TAG, "no notify property for characteristic to set notification");
            return;
        }

        boolean writeSuccess = currentGatt.setCharacteristicNotification(characteristic, enabled);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            boolean descriptorWriteSuccess = currentGatt.writeDescriptor(descriptor);

            String status = "";

            if (writeSuccess && descriptorWriteSuccess) {
                status = "success";
                Log.d(TAG, "setting notification was successful");
            } else {
                status = "failure";
                Log.d(TAG, "setting notification failed");
            }

            Intent notifyDataIntent = new Intent(ACTION_NOTIFY_DATA_AVAILABLE);

            notifyDataIntent.putExtra(NOTIFY_STATUS, status);

            String[] characteristicInfo = new String[3];

            characteristicInfo[0] = currentGatt.getDevice().getAddress();
            characteristicInfo[1] = characteristic.getService().getUuid().toString();
            characteristicInfo[2] = characteristic.getUuid().toString();

            notifyDataIntent.putExtra(UUID_LIST, characteristicInfo);

            sendBroadcast(notifyDataIntent);
        }
    }

    // writes a byte array to a given characteristic; returns false if the array's length is greater than
    // 20 because 20 bytes is the BLE packet size limit
    public boolean send(byte[] data, String deviceAddress, String serviceUuid, String characteristicUuid) {

        if (data.length > 20) {
            return false;
        }

        BluetoothGatt currentGatt = activeDevices.get(deviceAddress);

        for (String addr : activeDevices.keySet()) {
            Log.d("Addrs", addr);
        }

        if (currentGatt == null) {
            String byteString = "";
            try {
                byteString = new String(data, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            Log.w(TAG, "no active connection for given device address, " + deviceAddress + ", trying to send" +
                    "data: " + byteString);
            return false;
        }

        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }

        BluetoothGattCharacteristic characteristic = getCharacteristic(currentGatt, serviceUuid, characteristicUuid);

        if (characteristic == null) {
            Log.d(TAG, "send failed because we didn't find the characteristic for given service and characteristic uuids");
            return false;
        }

        if (!checkForCharacteristicProperties(characteristic, CHARACTERISTIC_WRITE)) {
            return false;
        }

        Log.d(TAG, "send function was called correctly");

        Log.d(TAG, characteristic.getUuid().toString());

        characteristic.setValue(data);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        return currentGatt.writeCharacteristic(characteristic);
    }

    // functions used to send byte array to arduino over BLE
    /*****************************************************************************************************/
    public void sendByteArrayOverBLE(String deviceAddress, byte[] byteArray, boolean startOfArray) {

        Log.d(TAG, deviceAddress+"---" + "send byte array over BLE called.");

        if (byteArray.length <= 20) {
            send(byteArray, deviceAddress, hardcodedServiceUuid,
                    hardcodedWriteCharacteristicUuid);
            return;
        }

        SendingPacketState currentSendingPacketState = getCurrentSendingPacketState(deviceAddress, "send byte array over BLE");

        if (currentSendingPacketState == null) {
            return;
        }

        if (startOfArray && currentSendingPacketState.sendingOutFragmentsMode) {
            Log.d(TAG, "we are in the middle of sending out another fragment; try again later");
            return;
        }

        if (startOfArray) {
            Log.d(TAG, "we got a request to send out an array larger than 20 bytes; starting fragmentation process");

            Log.d(TAG, deviceAddress+"---" + "setting sendingOutfragmentsMode to true from within sendingbytearrayOVerbLE.");
            currentSendingPacketState.sendingOutFragmentsMode = true;
            currentSendingPacketState.fragmentOffset++;

            currentSendingPacketState.currentSendingByteArray = Arrays.copyOfRange(byteArray, 20, byteArray.length);

            byte[] arrayToSend = Arrays.copyOfRange(byteArray, 0, 20);

            send(arrayToSend, deviceAddress, hardcodedServiceUuid,
                    hardcodedWriteCharacteristicUuid);
        }

    }

    // this function is repeatedly called within BLEService to finish sending out a BLE packet
    private void finishSendingByteArrayOverBLE(String deviceAddress) {

        Log.d(TAG, deviceAddress+"---" + "Entered the finish sending byte array over bLE function.");

        SendingPacketState currentSendingPacketState = getCurrentSendingPacketState(deviceAddress, "send byte array over BLE");

        if (currentSendingPacketState == null) {
            return;
        }

        if (currentSendingPacketState.currentSendingByteArray.length >= 20) {
            Log.d(TAG, "we are sending a middle fragment");
            Log.d(TAG, deviceAddress+"---" + "Sending a middle fragment inside of finishsendingbytearrayoverbLE.");

            currentSendingPacketState.fragmentOffset++;

            byte[] arrayToSend = Arrays.copyOfRange(currentSendingPacketState.currentSendingByteArray, 0, 20);



            Log.d(TAG, "values in middle fragment: ");
            for (int i = 0; i < arrayToSend.length; i++ ){
                Log.d(TAG, Integer.toString(Byte.toUnsignedInt(arrayToSend[i])));
            }



            send(arrayToSend, deviceAddress, hardcodedServiceUuid,
                    hardcodedWriteCharacteristicUuid);

            byte[] tempCopyRemainder = Arrays.copyOfRange(currentSendingPacketState.currentSendingByteArray,
                    20,
                    currentSendingPacketState.currentSendingByteArray.length);
            currentSendingPacketState.currentSendingByteArray = new byte[currentSendingPacketState.currentSendingByteArray.length - 20];
            currentSendingPacketState.currentSendingByteArray = Arrays.copyOfRange(tempCopyRemainder, 0, tempCopyRemainder.length);
        }
        else {

            Log.d(TAG, deviceAddress+"---" + "Sending an end fragment inside of finishsendingbytearrayoverbLE.");

            currentSendingPacketState.fragmentOffset++;

            Log.d(TAG, "we are sending an end fragment");

            byte[] arrayToSend = Arrays.copyOfRange(currentSendingPacketState.currentSendingByteArray,
                    0,
                    currentSendingPacketState.currentSendingByteArray.length);

            Log.d(TAG, "values in end fragment: ");


            for (int i = 0; i < arrayToSend.length; i++) {
                Log.d(TAG, Integer.toString(Byte.toUnsignedInt(arrayToSend[i])));
            }


            send(arrayToSend, deviceAddress, hardcodedServiceUuid,
                    hardcodedWriteCharacteristicUuid);

            Log.d(TAG, deviceAddress+"---" + "Setting sendingOutFragmentsMode to false from within finishSendingBytearrayOverbLE.");
            currentSendingPacketState.sendingOutFragmentsMode = false;
            currentSendingPacketState.currentSendingByteArray = null;
        }
    }
    /******************************************************************************************************/

    // functions related to sending an interest and receiving data from the arduino
    /*******************************************************************************************/
    // starts the process of sending an interest to the arduino and getting a data packet back
    // the interest that is sent is /icear/beacon/read/<deviceAddress>, and the data that is returned
    // is /icear/beacon/read/<deviceAddress> which contains the device's location
    public void getDataPacketFromArduino(String deviceAddress) {

        if (activeDevices.containsKey(deviceAddress)) {
            Log.d(TAG, "already connected to device, proceeding to get data packet...");
            getDataPacketFromArduinoAfterConnecting(deviceAddress);
        } else {
            Log.d(TAG, "weren't connected to device, attempting to connect...");
            connect(deviceAddress);
        }
    }

    // this is called after successfully connecting to device to discover its services
    private void getDataPacketFromArduinoAfterConnecting(String deviceAddress) {

        Log.d(TAG, deviceAddress+"---" + "entered the getDataPacketFromArduinoAfterConnecting");

        BluetoothGatt currentGatt = activeDevices.get(deviceAddress);

        if (currentGatt == null) {
            Log.d(TAG, "failed to find gatt for given device address, " + deviceAddress);
        }

        currentGatt.discoverServices();

    }

    // this is called after successfully getting the services of the device
    private void getDataPacketFromArduinoAfterDiscoveringServices(String deviceAddress) {
        Log.d(TAG, deviceAddress+"---" + "entered getDataPacketFromArduinoAfterDiscoveringServices");
        setCharacteristicNotification(deviceAddress, hardcodedServiceUuid, hardcodedReadAndNotifyCharacteristicUuid, true);
    }

    /*********************************************************************************************/


    // functions related to sending a data packet to the arduino
    /*****************************************************************************************/
    // this is called after successfully setting notifications on to send the ~ character to the device
    private void sendTilde(String deviceAddress) {

        Log.d(TAG, deviceAddress+"---" + "entered send Tilde, after the on descriptor write.");

        if (!sendingStates.containsKey(deviceAddress))
            sendingStates.put(deviceAddress, new SendingPacketState());
        else {
            SendingPacketState currentSendingPacketState = getCurrentSendingPacketState(deviceAddress, "send tilde");

            if (currentSendingPacketState == null) {
                return;
            }

            // resets all variables associated with sending an interest packet over BLE
            Log.d(TAG, deviceAddress+"---" + "Setting sendingOutfragmentsMode to false from within sendTilde.");
            currentSendingPacketState.sendingOutFragmentsMode = false;
            currentSendingPacketState.fragmentOffset = 0;
        }

        Log.d(TAG, deviceAddress+"---" + "called sendBytearrayOverBLE from within sendTilde.");
        sendByteArrayOverBLE(deviceAddress, new byte[] {'~'}, true);
    }

    // this is called after the arduino goes into mode 1, since mode 1 on the arduino means it is
    // waiting for packet length
    private void sendPacketLength(String deviceAddress) {

        Log.d(TAG, "sending packet length");

        SendingPacketState currentSendingPacketState = getCurrentSendingPacketState(deviceAddress, "send packet length");

        if (currentSendingPacketState == null) {
            return;
        }

        currentSendingPacketState.currentInterestByteArrayToSend = NFDService.makeByteArrayInterestPacketForBLEService("/icear/beacon/read/"
        + BluetoothHelperFunctions.removeColonsFromMACAddress(deviceAddress));

        Log.d(TAG, "name of interest being sent over BLE: " + "/icear/beacon/read/"
                + BluetoothHelperFunctions.removeColonsFromMACAddress(deviceAddress));

        short dataShort = (short) (currentSendingPacketState.currentInterestByteArrayToSend.length & 0xffff);

        byte[] length = {(byte) (dataShort >> 8), (byte) (dataShort & 0xff)};

        sendByteArrayOverBLE(deviceAddress, length, true);
    }

    // this is called after the arduino goes into mode 2, since mode 2 on the arduino means it is
    // waiting for the actual packet
    private void sendActualPacket(String deviceAddress) {
        Log.d(TAG, "sending actual packet");

        SendingPacketState currentSendingPacketState = getCurrentSendingPacketState(deviceAddress, "send packet bytes");

        if (currentSendingPacketState == null) {
            return;
        }

        sendByteArrayOverBLE(deviceAddress, currentSendingPacketState.currentInterestByteArrayToSend, true);
    }
    /******************************************************************************************/

    boolean checkForCharacteristicProperties(BluetoothGattCharacteristic characteristic, String action) {
        final int charaProp = characteristic.getProperties();

        if (action.equals(CHARACTERISTIC_READ)) {
            if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                Log.d(TAG, "the characteristic pressed had the read property");
                return true;
            } else {
                Log.d(TAG, "the characteristic pressed did not have the read property");
                return false;
            }
        }
        else if (action.equals(CHARACTERISTIC_WRITE)) {
            if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
                Log.d(TAG, "the characteristic pressed had some kind of write property");
                return true;
            } else {
                Log.d(TAG, "the characteristic pressed did not have the write no response property");
                return false;
            }
        }
        else if (action.equals(CHARACTERISTIC_NOTIFY)) {
            if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                Log.d(TAG, "the characteristic pressed had the notify property");
                return true;
            } else {
                Log.d(TAG, "the characteristic pressed did not have the notify property");
                return false;
            }
        }
        else if (action.equals(CHARACTERISTIC_INDICATE)) {

            if ((charaProp & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                Log.d(TAG, "the characteristic pressed had the indicate property");
                return true;
            } else {
                Log.d(TAG, "the characteristic pressed did not have the indicate property");
                return false;
            }
        }
        else {
            Log.d(TAG, "the action for characteristic we received was not read, notify, write, or indicate");
            return true;
        }
    }

    BluetoothGattCharacteristic getCharacteristic(BluetoothGatt gattObject, String serviceUuid, String characteristicUuid) {

        BluetoothGattService service = gattObject.getService(UUID.fromString(serviceUuid));
        BluetoothGattCharacteristic characteristic;

        if (service == null) {
            Log.w(TAG, "no service found for given service uuid");
            return null;
        } else {
            characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid));

            if (characteristic == null) {
                Log.w(TAG, "no characteristic found for given characteristic uuid");
                return null;
            }
        }

        return characteristic;
    }

    public static IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CONNECTED);
        filter.addAction(ACTION_DISCONNECTED);
        filter.addAction(ACTION_CONNECTING);
        filter.addAction(ACTION_DISCOVERING_SERVICES);
        filter.addAction(CHARACTERISTIC_READ_ACTION_DATA_AVAILABLE);
        filter.addAction(DESCRIPTOR_READ_ACTION_DATA_AVAILABLE);
        filter.addAction(CHARACTERISTIC_CHANGE_DATA_AVAILABLE);
        filter.addAction(ACTION_WRITE_SUCCESS);
        filter.addAction(ACTION_NOTIFY_DATA_AVAILABLE);
        filter.addAction(ACTION_SERVICES_DISCOVERED);
        filter.addAction(ACTION_SERVICE_DISCOVERY_FAILED);
        filter.addAction(GOT_DATA_FROM_ARDUINO);
        filter.addAction(ADD_CONNECTED_DEVICE_LIST);
        filter.addAction(REMOVE_CONNECTED_DEVICE_LIST);
        return filter;
    }

    // this returns a string with all the devices we are currently connected to
    public String getAllConnectedDevices() {

        String connectedDevicesString = "";

        for (BluetoothDevice d : mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT)) {
            connectedDevicesString +=
                    "Device name: " + d.getName() + "," +
                            "Device Address: " + d.getAddress() + "\n";
        }

        return connectedDevicesString;
    }

    private void broadcastUpdate(final String action,
                                 final String deviceAddress, final BluetoothGattCharacteristic characteristic) {

        final Intent intent = new Intent(action);

        String[] characteristicInfo = new String[3];

        characteristicInfo[0] = deviceAddress;
        characteristicInfo[1] = characteristic.getService().getUuid().toString();
        characteristicInfo[2] = characteristic.getUuid().toString();

        intent.putExtra(UUID_LIST, characteristicInfo);
        intent.putExtra(ACTION_VALUE, characteristic.getValue());
        sendBroadcast(intent, Manifest.permission.BLUETOOTH);
    }


    private void broadcastUpdate(final String action,
                                 final String deviceAddress, final BluetoothGattDescriptor descriptor) {

        final Intent intent = new Intent(action);

        String[] descriptorInfo = new String[4];

        descriptorInfo[0] = deviceAddress;
        descriptorInfo[1] = descriptor.getCharacteristic().getService().getUuid().toString();
        descriptorInfo[2] = descriptor.getCharacteristic().getUuid().toString();
        descriptorInfo[3] = descriptor.getUuid().toString();

        intent.putExtra(UUID_LIST, descriptorInfo);
        intent.putExtra(ACTION_VALUE, descriptor.getValue());
        sendBroadcast(intent, Manifest.permission.BLUETOOTH);
    }

    public class LocalBinder extends Binder {
        BLEService getService() {
            return BLEService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {

        // closes all of the BluetoothGatt objects associated with connected devices if the service is unbound
        close();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(doneSendingReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(doneReceivingReceiver);

        return super.onUnbind(intent);


    }

    private final IBinder mBinder = new LocalBinder();

    // gets called after the ble service is bound by main activity
    public boolean initialize() {

        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(doneSendingReceiver, new IntentFilter(DONE_SENDING_BYTE_ARRAY));
        LocalBroadcastManager.getInstance(this).registerReceiver(doneReceivingReceiver, new IntentFilter(DONE_RECEIVING_BYTE_ARRAY));

        activeDevices = new HashMap<>();
        receivingStates = new HashMap<>();
        sendingStates = new HashMap<>();

        return true;
    }

    ReceivingPacketState getCurrentReceivingPacketState(String deviceAddress, String place) {

        ReceivingPacketState currentReceivingPacketState = receivingStates.get(deviceAddress);

        if (currentReceivingPacketState == null) {
            Log.w(TAG,"Current receiving packet state was null at: " + place + ", " + deviceAddress);
            return null;
        }

        return currentReceivingPacketState;
    }

    SendingPacketState getCurrentSendingPacketState(String deviceAddress, String place) {

        SendingPacketState currentSendingPacketState = sendingStates.get(deviceAddress);

        if (currentSendingPacketState == null) {
            Log.w(TAG,"Current sending packet state was null at: " + place + ", " + deviceAddress);
            return null;
        }

        return currentSendingPacketState;
    }
}
