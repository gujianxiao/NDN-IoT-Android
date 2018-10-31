package com.example.chenbo.helloworld;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.chenbo.helloworld.BLEService;
import com.example.chenbo.helloworld.BluetoothHelperFunctions;
import com.example.chenbo.helloworld.DeviceInfoHelperFunctions;
import com.example.chenbo.helloworld.MainActivity;
import com.example.chenbo.helloworld.NFDService;

import net.named_data.jndn.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class BLMainActivity extends AppCompatActivity {

    private static String TAG = "Main";

    public static BLMainActivity mainActivity;

    // constants for the permission request broadcasts
    private final int REQUEST_COARSE_LOCATION = 11;
    private final int REQUEST_READ_STORAGE = 12;
    private final int REQUEST_WRITE_STORAGE = 13;

    // constants for broadcasts to start waiting for a scan interval or to start another scan
    public final static String START_SCAN_INTERVAL_WAIT = "START_SCAN_INTERVAL_WAIT";
    public final static String START_NEW_SCAN = "START_NEW_SCAN";

    // this is a boolean variable to stop us from attempting to connect to multiple bluetooth devices at the same time;
    // the way we currently get data packets from the devices would break things if we did, it could be fixed in the future
    // by making changes to BLEService to keep track of which device it was getting characteristic change data from
    boolean currentlyConnectingOrConnected = false;

    // variables to check if we connected to a device on this scan; this is to make sure we don't try to connnect to
    // devices too close together
    boolean connectedOnThisScan = false;

    // the MAC address of the device we are currently trying to get the location of;
    // this variable is here so that the program can first send out an interest for the device's location over NDN
    // before actually trying to connect to the device; since the data packets with the device location are signed, any
    // phone that has gotten the device's location before can serve us the data
    String currentDeviceAddressToGetLocationFrom = "";

    // shared preferences object to store scan parameters for next time
    SharedPreferences mScanPreferences;
    SharedPreferences.Editor mScanPreferencesEditor;
    private static String SCAN_PERIOD = "SCAN_PERIOD";
    private static String SCAN_INTERVAL_START = "SCAN_INTERVAL_START";
    private static String SCAN_INTERVAL_MIN = "SCAN_INTERVAL_MIN";
    private static String SCAN_INTERVAL_MAX = "SCAN_INTERVAL_MAX";
    private static String SCAN_MULTIPLIER = "SCAN_MULTIPLIER";
    private static String MAX_TOLERABLE_MISSED_SCANS = "MAX_TOLERABLE_MISSED_SCANS";
    private static String BACKOFF_THRESHOLD = "BACKOFF_THRESHOLD";

    // the length of time that scans last for
    private int scanPeriod;
    // the time period between scans; this gets increased when we don't discover new devices and reset to 1000 when we do discover new devices
    private int scanInterval;
    // this is the maximum value the scan interval can reach
    private int maxInterval;
    // this is the minimum value the scan interval can reach; it will be reset to this when a new device is discovered
    private int minInterval;

    // defines how many times we can miss a device on consecutive scans before considering it out of range
    private int maxTolerableMissedScans;

    // stores the last list of discovered devices so that interests for devices can be satisfied immediately, rather than
    // waiting for the current scan to end
    public String lastListOfDiscoveredDevices= "";

    // bluetoothAdapter to enable bluetooth, start LE scanning
    private BluetoothAdapter bluetoothAdapter;

    // buttons for user interaction
    private Button toggleScanButton;
    private Button changeScanIntervalButton;
    private Button changeScanPeriodButton;
    private Button changeTolerableMissedButton;
    private Button changeMinIntervalButton;
    private Button changeMaxIntervalButton;
    private Button changeBackoffThresholdButton;
    private Button toggleAuthButton;

    // textViews to display selected device information
    private TextView selectedDeviceName;
    private TextView selectedDeviceAddress;
    private TextView scanIntervalDisplay;
    private TextView scanPeriodDisplay;
    private TextView maxTolerableMissedScansDisplay;
    private TextView minIntervalDisplay;
    private TextView maxIntervalDisplay;
    private TextView backoffThresholdDisplay;

    // this is true if we are in a cycle of scanning (we are either actually scanning, or waiting in a scan interval to scan again)
    private boolean currentlyInScanCycle = false;

    // this is true if we are doing beacon MAC address authentication, meaning we erase the beacon's location information after it goes
    // out of range so that each time we come in range of the beacon, we connect to it to affirm that the MAC address it advertises is
    // actually reachable and not another beacon spoofing the MAC address
    // if this is false, then there is no MAC address authentication; this can improve performance and is recommended in most cases
    private boolean beaconMACAuthentication = false;

    // variable to tell us how many scans it has been since we were in the range of location beacons
    int scansSinceLastInRangeOfLocationBeacons = 0;
    // the maximum amount of scans that we wait since being in the range of location beacons before starting to slow down the scan rate
    int backoffThreshold = 3;
    // this is what we multiply the scan interval by when we don't detect new devices on the previous scan; this is to reduce scanning
    // frequency to save battery
    private int intervalMultiplier;

    // variable that indicates whether there are any devices with active connections; if there are, we do the backoff strategy for scanning,
    // if there are not, then we keep the scan for every 1 second to make sure that we discover new devices as quickly as possible
    private boolean devicesWithActiveConnections = false;

    // this is used to asynchronously wait for scan periods and scan intervals to finish
    private Handler handler;

    // variables to handle the data for currently connected devices
    /******************************************************************/
    // Array List to hold display information for connected devices
    private ArrayList<String> connectedDevices;

    // arrayAdapter to display elements of connectedDevices Array list
    public ArrayAdapter<String> connectedDevicesArrayAdapter;

    // listview for displaying connected devices
    private ListView connectedDevicesListView;

    /******************************************************************/


    // variables to handle the data for devices discovered on scans
    /**********************************************************************/
    // HashMap to hold scanRecord information for discovered devices, maps device address to scan record
    private HashMap<String, byte[]> discoveredDevicesScanRecords;

    // ArrayList to hold display information for discovered devices
    private ArrayList<String> discoveredDevices;

    // ArrayList to hold discovered bluetooth device objects
    private ArrayList<BluetoothDevice> discoveredDevicesObjects;

    // HashSet to prevent duplicate entries into the ArrayList
    private HashSet<String> discoveredDevicesHashSet;

    // place to store hashset of previous list of discovered devices; this is so that calculations can be done on what devices
    // are still considered within range while a new scan starts
    private HashSet<String> lastDiscoveredDevicesHashSet;

    // arrayAdapter to display elements of discoveredDevices arrayList
    private ArrayAdapter<String> discoveredDevicesArrayAdapter;

    // listview for displaying discovered devices
    private ListView discoveredDevicesListView;
    /************************************************************************************/


    // variables to handle the data for devices  we consider still within range, which
    // isn't necessarily the same as the devices that were discovered on the last scan, as they might be missed on a few scans
    /**********************************************************************/

    // maps the device name and address to its missed scan count; this missed scan count is used to decide whether a device
    // should still be considered in range or not
    public HashMap<String, Integer> devicesInRangeLastNScansHashMap;

    // Array List to hold display information for advertising devices that the ble agent considers within range
    private ArrayList<String> advertisingDevicesWithinRange;

    public ArrayAdapter<String> advertisingDevicesWithinRangeArrayAdapter;

    // listview for displaying advertising devices that the agent still considers within range
    private ListView advertisingDevicesWithinRangeListView;

    /**********************************************************************/

    // variables to handle the data for maintaining mappings from device addresses to their locations once we discover them
    /********************************************************************************************/
    // maps device mac addresses to location information (will only be filled if the connected device can also be read from)
    public HashMap<String, String> addressToLocation;

    // Array List to hold display information for advertising devices that the ble agent considers within range
    private ArrayList<String> addressToLocationMappings;

    public ArrayAdapter<String> addressToLocationMappingsAdapter;

    // listview for displaying current mappings of address to location to the user
    ListView addressToLocationMappingsListView;

    /********************************************************************************************/

    // functions and objects related to maintaining the BLE scan cycle
    /********************************************************************************/
    // function to wait for the scan interval time period before starting the next scan
    private void waitForScanInterval() {

        Log.d(TAG, "we entered the waitForScanInterval function");
        // Stops scanning after a pre-defined scan period.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentlyInScanCycle)
                    sendBroadcast(new Intent(START_NEW_SCAN));
            }
        }, scanInterval);
        updateUI();
    }

    private final BroadcastReceiver startScanIntervalWaitListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (currentlyInScanCycle) {
                waitForScanInterval();
            }
        }
    };

    private final BroadcastReceiver startNewScanListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (currentlyInScanCycle) {
                scanForBleDevices();
            }
        }
    };

    /********************************************************************************/

    // bleService object and broadcastReceiver and serviceConnection
    /***********************************************************************/

    // object of bleService to do BLE related things
    public BLEService bleService;

    private final BroadcastReceiver bleStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (nfdService == null || bleService == null) {
                return;
            }

            if (intent.getAction().equals(BLEService.ACTION_CONNECTED)) {

                Log.d(TAG, "we got the action_connected intent from bleService");

                String[] connectionInformation = intent.getStringArrayExtra(BLEService.CONNECTION_INFORMATION);

                String macAddressNoColons = connectionInformation[0];
                String macAddress = BluetoothHelperFunctions.addColonsToMACAddress(macAddressNoColons);

                String deviceName = connectionInformation[1];

                String deviceInfo = DeviceInfoHelperFunctions.makeDeviceInfo(deviceName, macAddress);

                devicesWithActiveConnections = true;

            }
            else if (intent.getAction().equals(BLEService.ACTION_DISCONNECTED)) {

                Log.d(TAG, "we got the action_disconnected intent from bleService");

                currentlyConnectingOrConnected = false;

                String[] connectionInformation = intent.getStringArrayExtra(BLEService.CONNECTION_INFORMATION);

                String macAddressNoColons = connectionInformation[0];
                String macAddress = BluetoothHelperFunctions.addColonsToMACAddress(macAddressNoColons);

                String deviceName = connectionInformation[1];

            }
            else if (intent.getAction().equals(BLEService.ACTION_CONNECTING)) {

                Log.d(TAG, "we got the action_connecting intent from bleService");

                String macAddressNoColons = intent.getExtras().getString(BLEService.MAC_ADDRESS_NO_COLONS);

            }
            else if (intent.getAction().equals(BLEService.GOT_DATA_FROM_ARDUINO)) {

                byte[] dataByteArray = intent.getExtras().getByteArray(BLEService.DATA_PACKET_AS_BYTES);

                Data receivedData = NFDService.interpretByteArrayAsData(dataByteArray);

                if (receivedData == null) {
                    Log.d(TAG, "data verification failed, ignoring data packet");
                    return;
                }

                String dataName = receivedData.getName().toString();
                String dataLocation = receivedData.getContent().toString();

                String deviceAddressNoColons = dataName.substring(dataName.lastIndexOf("/") + 1);
                String deviceAddressWithColons = BluetoothHelperFunctions.addColonsToMACAddress(deviceAddressNoColons);

                addressToLocation.put(deviceAddressNoColons, dataLocation);
                addressToLocationMappingsAdapter.add(
                        "Device location: " + "\n" + dataLocation + "\n" +
                        "Device address: " + "\n" + deviceAddressWithColons);

                bleService.disconnect(BluetoothHelperFunctions.addColonsToMACAddress(deviceAddressNoColons));

                sendDataForBeaconsInRangeUpdate();

                // create Byte object array for storage
                Byte[] dataByteObjectArray = new Byte[dataByteArray.length];

                int i = 0;
                for (byte b : dataByteArray) {
                    dataByteObjectArray[i++] = b;
                }

                // stores the data packet retrieved for this device to satisfy later interests
                nfdService.lastDataPacketForDevices.put(deviceAddressWithColons, dataByteObjectArray);

                // sends data packet to NFD face to satisfy possible interests for this device's location data
                nfdService.sendDataPacketAsBytesToNFDFace(dataByteArray);

            }
            else if (intent.getAction().equals(BLEService.ADD_CONNECTED_DEVICE_LIST)) {

                String deviceInfo = intent.getExtras().getString(BLEService.DEVICE_INFO);

                connectedDevicesArrayAdapter.add(deviceInfo);

            }
            else if (intent.getAction().equals(BLEService.REMOVE_CONNECTED_DEVICE_LIST)) {

                String deviceInfo = intent.getExtras().getString(BLEService.DEVICE_INFO);

                connectedDevicesArrayAdapter.remove(deviceInfo);

            }
            updateUI();

        }

    };

    private final ServiceConnection bleServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected for bleService got called.");

            bleService = ((BLEService.LocalBinder) service).getService();
            if (bleService.initialize()) {

                startBLEScanCycle();

            } else {
                Log.d(TAG, "The initialize for bleService failed.");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bleService = null;
            Log.d(TAG, "ble service was disconnected");

            startBLEService();
        }
    };
    /********************************************************/

    // nfdService object and broadcastReceiver and serviceConnection for the NFDService
    /*****************************************************/
    private NFDService nfdService;

    private final BroadcastReceiver nfdStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String deviceAddress = intent.getExtras().getString(NFDService.DEVICE_ADDRESS);
            // this is not used in the current implementation, but if BLEApp gets an interest for /icear/beacon/discover,
            // it will proactively reset the BLE scan and turn the scan interval back to its lowest value; this was so that
            // a user could manually reset the BLEApp from its backoff strategy, although since the backoff strategy is not
            // used now this is not used
            if (action.equals(NFDService.INTEREST_DISCOVER_RECEIVED)) {

                stopBLEScanCycle();

                startBLEScanCycle();

            }
            else if (action.equals(NFDService.INTEREST_READ_FROM_DEVICE)) {

                //deviceAddress = intent.getExtras().getString(NFDService.DEVICE_ADDRESS);

                Log.d(TAG, "got interest to direct read from device with address: " + deviceAddress);

                if (nfdService.lastDataPacketForDevices.containsKey(deviceAddress)) {
                    nfdService.sendDataPacketAsBytesToNFDFace(
                            convertObjectByteArrayToByteArray(nfdService.lastDataPacketForDevices.get(deviceAddress)));
                }
                else {
                    bleService.getDataPacketFromArduino(deviceAddress);
                }
            }
            else if (action.equals(NFDService.DEVICE_LOCATION_INTEREST_DATA)) {

                //deviceAddress = intent.getExtras().getString(NFDService.DEVICE_ADDRESS);

                byte[] dataByteArray = intent.getExtras().getByteArray(NFDService.DATA_PACKET_AS_BYTE_ARRAY);

                Data receivedData = NFDService.interpretByteArrayAsData(dataByteArray);

                if (receivedData == null) {
                    Log.d(TAG, "data verification failed, ignoring data packet");
                    return;
                }

                String dataName = receivedData.getName().toString();
                String dataLocation = receivedData.getContent().toString();

                String deviceAddressNoColons = dataName.substring(dataName.lastIndexOf("/") + 1);
                String deviceAddressWithColons = BluetoothHelperFunctions.addColonsToMACAddress(deviceAddressNoColons);

                addressToLocation.put(deviceAddressNoColons, dataLocation);
                addressToLocationMappingsAdapter.add(
                        "Device location: " + "\n" + dataLocation + "\n" +
                                "Device address: " + "\n" + deviceAddressWithColons);

                bleService.disconnect(BluetoothHelperFunctions.addColonsToMACAddress(deviceAddressNoColons));

                sendDataForBeaconsInRangeUpdate();

                // create Byte object array for storage
                Byte[] dataByteObjectArray = new Byte[dataByteArray.length];

                int i = 0;
                for (byte b : dataByteArray) {
                    dataByteObjectArray[i++] = b;
                }

                // stores the data packet retrieved for this device to satisfy later interests
                nfdService.lastDataPacketForDevices.put(deviceAddressWithColons, dataByteObjectArray);

                // sends data packet to NFD face to satisfy possible interests for this device's location data
                nfdService.sendDataPacketAsBytesToNFDFace(dataByteArray);

            }
            else if (action.equals(NFDService.DEVICE_LOCATION_INTEREST_FAILEDVERIFY)) {

                //deviceAddress = intent.getExtras().getString(NFDService.DEVICE_ADDRESS);

                //bleService.connect(deviceAddress);

            }
            else if (action.equals(NFDService.DEVICE_LOCATION_INTEREST_TIMEOUT)) {

                //deviceAddress = intent.getExtras().getString(NFDService.DEVICE_ADDRESS);

                //bleService.connect(deviceAddress);
            }
            else if (action.equals(NFDService.DEVICE_LOCATION_INTEREST_NACK)) {

                //deviceAddress = intent.getExtras().getString(NFDService.DEVICE_ADDRESS);

                //bleService.connect(deviceAddress);
            }
            else if (action.equals(NFDService.DEVICE_LOCATION_INTEREST_EXCEPTION)) {

                //deviceAddress = intent.getExtras().getString(NFDService.DEVICE_ADDRESS);

                //bleService.connect(deviceAddress);
            }

            updateUI();
        }
    };

    private final ServiceConnection nfdServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected for nfdServiceConnection got called.");
            nfdService = ((NFDService.LocalBinder) service).getService();

            startBLEService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            nfdService = null;

            Log.d(TAG, "the nfd service was disconnected");

            startNFDService();
        }
    };
    /**********************************************************************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: start BL service...");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.blactivity_main);

        mainActivity = this;

        mScanPreferences = getSharedPreferences("ScanPreferences", Context.MODE_PRIVATE);
        mScanPreferencesEditor = mScanPreferences.edit();

        scanInterval = mScanPreferences.getInt(SCAN_INTERVAL_START, 3000);
        scanPeriod = mScanPreferences.getInt(SCAN_PERIOD, 3000);
        minInterval = mScanPreferences.getInt(SCAN_INTERVAL_MIN, 3000);
        maxInterval = mScanPreferences.getInt(SCAN_INTERVAL_MAX, 24000);
        maxTolerableMissedScans = mScanPreferences.getInt(MAX_TOLERABLE_MISSED_SCANS, 2);
        intervalMultiplier = mScanPreferences.getInt(SCAN_MULTIPLIER, 2);
        backoffThreshold = mScanPreferences.getInt(BACKOFF_THRESHOLD, 3);

        // getting default bluetooth Adapter for enabling bluetooth / doing LE scan
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Log.e(TAG, "ERROR, WAS NOT ABLE TO GET BLUETOOTH ADAPTER");
        }

        // registering receivers
        registerReceiver(nfdStatusListener, NFDService.getIntentFilter());
        registerReceiver(bleStatusListener, BLEService.getIntentFilter());
        registerReceiver(startScanIntervalWaitListener, new IntentFilter(START_SCAN_INTERVAL_WAIT));
        registerReceiver(startNewScanListener, new IntentFilter(START_NEW_SCAN));

        // getting references to TextView UI elements
        scanIntervalDisplay = (TextView) findViewById(R.id.scanIntervalDisplay);
        scanPeriodDisplay = (TextView) findViewById(R.id.scanPeriodDisplay);
        maxTolerableMissedScansDisplay = (TextView) findViewById(R.id.maxTolerableMissedDisplay);
        minIntervalDisplay = (TextView) findViewById(R.id.minScanIntervalDisplay);
        maxIntervalDisplay = (TextView) findViewById(R.id.maxScanIntervalDisplay);
        backoffThresholdDisplay = (TextView) findViewById(R.id.backoffThresholdDisplay);
        //selectedDeviceName = (TextView) findViewById(R.id.deviceName);
        //selectedDeviceAddress = (TextView) findViewById(R.id.deviceAddress);

        // getting references to ListView UI elements
        discoveredDevicesListView = (ListView) findViewById(R.id.discoveredDevicesList);
        advertisingDevicesWithinRangeListView = (ListView) findViewById(R.id.devicesInRangeList);
        connectedDevicesListView = (ListView) findViewById(R.id.connectedDevicesList);
        addressToLocationMappingsListView = (ListView) findViewById(R.id.mappingsList);

        // setting actions for when you click on an element of the list views
        /*****************************************************************************/

        // register onClickListener to handle click events on each item
        discoveredDevicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            // argument position gives the index of item which is clicked
            public void onItemClick(AdapterView<?> arg0, View v, int position, long arg3)
            {
                String selectedDevice = discoveredDevices.get(position);
                String deviceAddress = DeviceInfoHelperFunctions.getDeviceAddressFromDeviceInfo(selectedDevice);
                String deviceName = DeviceInfoHelperFunctions.getDeviceNameFromDeviceInfo(selectedDevice);

                //selectedDeviceName.setText(deviceName);
                //selectedDeviceAddress.setText(deviceAddress);

                updateUI();
            }
        });

        // register onClickListener to handle click events on each item
        advertisingDevicesWithinRangeListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            // argument position gives the index of item which is clicked
            public void onItemClick(AdapterView<?> arg0, View v, int position, long arg3)
            {
                String selectedDevice = advertisingDevicesWithinRange.get(position);
                String deviceAddress = DeviceInfoHelperFunctions.getDeviceAddressFromDeviceInfo(selectedDevice);
                String deviceName = DeviceInfoHelperFunctions.getDeviceNameFromDeviceInfo(selectedDevice);

                //selectedDeviceName.setText(deviceName);
                //selectedDeviceAddress.setText(deviceAddress);

                updateUI();
            }
        });

        // register onClickListener to handle click events on each item
        connectedDevicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            // argument position gives the index of item which is clicked
            public void onItemClick(AdapterView<?> arg0, View v, int position, long arg3)
            {
                String selectedDevice = connectedDevices.get(position);
                String deviceAddress = DeviceInfoHelperFunctions.getDeviceAddressFromDeviceInfo(selectedDevice);
                String deviceName = DeviceInfoHelperFunctions.getDeviceNameFromDeviceInfo(selectedDevice);

                //selectedDeviceName.setText(deviceName);
                //selectedDeviceAddress.setText(deviceAddress);

                updateUI();
            }
        });

        /*****************************************************************************/

        // initializing handler, which is used to limit the BLE scan to SCAN_PERIOD milliseconds
        handler = new Handler();

        // initializing data structures that hold various device related information
        discoveredDevices = new ArrayList<>();
        discoveredDevicesHashSet = new HashSet<>();
        lastDiscoveredDevicesHashSet = new HashSet<>();
        discoveredDevicesObjects = new ArrayList<>();
        discoveredDevicesScanRecords = new HashMap<>();
        addressToLocation = new HashMap<>();
        advertisingDevicesWithinRange = new ArrayList<>();
        connectedDevices = new ArrayList<>();
        devicesInRangeLastNScansHashMap = new HashMap<>();
        addressToLocationMappings = new ArrayList<>();

        // setting up array adapters by linking their data structure to their corresponding UI element
        /*****************************************************************************************************/
        discoveredDevicesArrayAdapter =
                new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, discoveredDevices);
        discoveredDevicesListView.setAdapter(discoveredDevicesArrayAdapter);

        advertisingDevicesWithinRangeArrayAdapter =
                new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, advertisingDevicesWithinRange);
        advertisingDevicesWithinRangeListView.setAdapter(advertisingDevicesWithinRangeArrayAdapter);

        connectedDevicesArrayAdapter =
                new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, connectedDevices);
        connectedDevicesListView.setAdapter(connectedDevicesArrayAdapter);

        addressToLocationMappingsAdapter =
                new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, addressToLocationMappings);
        addressToLocationMappingsListView.setAdapter(addressToLocationMappingsAdapter);

        /********************************************************************************************************/


        // Getting references to buttons and setting the button actions
        /***************************************************************************************************/

        // turn the scan on or off; as long as storage permissions are enabled, the app
        // will automatically start the scan upon start up
        toggleScanButton = (Button) findViewById(R.id.toggleScanButton);
        toggleScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (currentlyInScanCycle) {
                    stopBLEScanCycle();
                }
                else {
                    startBLEScanCycle();
                }

                updateUI();

            }
        });

        // button that lets you reset the scan interval; because there is a backoff strategy when the app is
        // connected to a beacon, the scan interval will automatically increase whenever no new devices are discovered
        // on scans; this button allows you to reset the scan interval back to its minimum value defined by minInterval
        // *** note that with the way that we currently get device location, there is no persistent connection, so
        // there will never be a backoff; the implementation has just been left here for possible future use
        changeScanIntervalButton = (Button) findViewById(R.id.changeScanIntervalButton);
        changeScanIntervalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(BLMainActivity.this);
                builder.setTitle("Enter New Value for Scan Interval");

                LinearLayout layout = new LinearLayout(BLMainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);

                // add a textbox to let the user enter a new scan period
                final EditText numBox = new EditText(BLMainActivity.this);

                numBox.setInputType(InputType.TYPE_CLASS_NUMBER);
                builder.setView(numBox);

                numBox.setHint("Scan Interval (in milliseconds)");
                layout.addView(numBox);

                // Set up the buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newScanInterval;

                        try {
                            newScanInterval = Integer.parseInt(numBox.getText().toString());
                        }
                        catch (NumberFormatException e) {
                            Log.w(TAG, "Number format exception: " + e.getMessage());

                            CharSequence text = "Number format exception: " + e.getMessage();
                            int duration = Toast.LENGTH_SHORT;

                            Toast toast = Toast.makeText(BLMainActivity.this, text, duration);
                            toast.show();
                            return;
                        }

                        // changes the starting, minimum, and maximum scan intervals since there is no backoff strategy for now
                        scanInterval = newScanInterval;
                        //minInterval = newScanInterval;
                        //maxInterval = newScanInterval;

                        mScanPreferencesEditor.putInt(SCAN_INTERVAL_START, newScanInterval).commit();
                        //mScanPreferencesEditor.putInt(SCAN_INTERVAL_MIN, newScanInterval).commit();
                        //mScanPreferencesEditor.putInt(SCAN_INTERVAL_MAX, newScanInterval).commit();

                        stopBLEScanCycle();

                        startBLEScanCycle();

                        updateUI();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.setView(layout);
                builder.show();

            }
        });

        // this button allows the user to specify a new scan period; the scan period is different from the scan interval in that
        // the scan interval is the time between consecutive scans, while the scan period is the actual amount of time that the
        // device goes into scanning mode for a particular scan
        changeScanPeriodButton = (Button) findViewById(R.id.changeScanPeriodButton);
        changeScanPeriodButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(BLMainActivity.this);
                builder.setTitle("Enter New Value for Scan Period");

                LinearLayout layout = new LinearLayout(BLMainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);

                // add a textbox to let the user enter a new scan period
                final EditText numBox = new EditText(BLMainActivity.this);

                numBox.setInputType(InputType.TYPE_CLASS_NUMBER);
                builder.setView(numBox);

                numBox.setHint("Scan Period (in milliseconds)");
                layout.addView(numBox);

                // Set up the buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newScanPeriod;

                        try {
                            newScanPeriod = Integer.parseInt(numBox.getText().toString());
                        }
                        catch (NumberFormatException e) {
                            Log.w(TAG, "Number format exception: " + e.getMessage());

                            CharSequence text = "Number format exception: " + e.getMessage();
                            int duration = Toast.LENGTH_SHORT;

                            Toast toast = Toast.makeText(BLMainActivity.this, text, duration);
                            toast.show();
                            return;
                        }

                        scanPeriod = newScanPeriod;

                        mScanPreferencesEditor.putInt(SCAN_PERIOD, newScanPeriod).commit();

                        updateUI();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.setView(layout);
                builder.show();


            }
        });

        // this allows the user to change the tolerable number of missed scans for a device to still be considered in range by
        // the application; this is here because it was found that devices were consistently missed on consecutive scans, which
        // is likely because of the different frequencies at which devices send out advertisement packets
        changeTolerableMissedButton = (Button) findViewById(R.id.changeTolerableMissedButton);
        changeTolerableMissedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(BLMainActivity.this);
                builder.setTitle("Enter New Value for Maximum Tolerable Missed Scans for A Device" +
                        " To be Considered In Range");

                LinearLayout layout = new LinearLayout(BLMainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Add a TextView here for the descriptor's index
                final EditText numBox = new EditText(BLMainActivity.this);

                numBox.setInputType(InputType.TYPE_CLASS_NUMBER);
                builder.setView(numBox);

                numBox.setHint("Max Tolerable Missed Scans");
                layout.addView(numBox); // Notice this is an add method

                // Set up the buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newMaxTolerableMissedScans;

                        try {
                            newMaxTolerableMissedScans = Integer.parseInt(numBox.getText().toString());
                        }
                        catch (NumberFormatException e) {
                            Log.w(TAG, "Number format exception: " + e.getMessage());

                            CharSequence text = "Number format exception: " + e.getMessage();
                            int duration = Toast.LENGTH_SHORT;

                            Toast toast = Toast.makeText(BLMainActivity.this, text, duration);
                            toast.show();
                            return;
                        }

                        maxTolerableMissedScans = newMaxTolerableMissedScans;

                        mScanPreferencesEditor.putInt(MAX_TOLERABLE_MISSED_SCANS, newMaxTolerableMissedScans).commit();

                        updateUI();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.setView(layout);
                builder.show();

            }
        });

        // changes the lowest value the scan interval can go to
        changeMinIntervalButton = (Button) findViewById(R.id.changeMinScanIntervalButton);
        changeMinIntervalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(BLMainActivity.this);
                builder.setTitle("Enter New Value for Minimum Scan Interval");

                LinearLayout layout = new LinearLayout(BLMainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Add a TextView here for the descriptor's index
                final EditText numBox = new EditText(BLMainActivity.this);

                numBox.setInputType(InputType.TYPE_CLASS_NUMBER);
                builder.setView(numBox);

                numBox.setHint("Minimum Scan Interval");
                layout.addView(numBox); // Notice this is an add method

                // Set up the buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newMinScanInterval;

                        try {
                            newMinScanInterval = Integer.parseInt(numBox.getText().toString());
                        }
                        catch (NumberFormatException e) {
                            Log.w(TAG, "Number format exception: " + e.getMessage());

                            CharSequence text = "Number format exception: " + e.getMessage();
                            int duration = Toast.LENGTH_SHORT;

                            Toast toast = Toast.makeText(BLMainActivity.this, text, duration);
                            toast.show();
                            return;
                        }

                        minInterval = newMinScanInterval;

                        mScanPreferencesEditor.putInt(SCAN_INTERVAL_MIN, newMinScanInterval).commit();

                        updateUI();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.setView(layout);
                builder.show();

            }
        });

        // changes the maximum value the scan interval can go to
        changeMaxIntervalButton = (Button) findViewById(R.id.changeMaxScanIntervalButton);
        changeMaxIntervalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(BLMainActivity.this);
                builder.setTitle("Enter New Value for Maximum Scan Interval");

                LinearLayout layout = new LinearLayout(BLMainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Add a TextView here for the descriptor's index
                final EditText numBox = new EditText(BLMainActivity.this);

                numBox.setInputType(InputType.TYPE_CLASS_NUMBER);
                builder.setView(numBox);

                numBox.setHint("Maximum Scan Interval");
                layout.addView(numBox); // Notice this is an add method

                // Set up the buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newMaxScanInterval;

                        try {
                            newMaxScanInterval = Integer.parseInt(numBox.getText().toString());
                        }
                        catch (NumberFormatException e) {
                            Log.w(TAG, "Number format exception: " + e.getMessage());

                            CharSequence text = "Number format exception: " + e.getMessage();
                            int duration = Toast.LENGTH_SHORT;

                            Toast toast = Toast.makeText(BLMainActivity.this, text, duration);
                            toast.show();
                            return;
                        }

                        maxInterval = newMaxScanInterval;

                        mScanPreferencesEditor.putInt(SCAN_INTERVAL_MAX, newMaxScanInterval).commit();

                        updateUI();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.setView(layout);
                builder.show();

            }
        });

        // this allows the user to change the tolerable number of missed scans for a device to still be considered in range by
        // the application; this is here because it was found that devices were consistently missed on consecutive scans, which
        // is likely because of the different frequencies at which devices send out advertisement packets
        changeBackoffThresholdButton = (Button) findViewById(R.id.changeBackoffThresholdButton);
        changeBackoffThresholdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(BLMainActivity.this);
                builder.setTitle("Enter New Value for Backoff Threshold (number of consecutive scans where no location beacons are in range " +
                        "at which scanInterval will begin to increase)");

                LinearLayout layout = new LinearLayout(BLMainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Add a TextView here for the descriptor's index
                final EditText numBox = new EditText(BLMainActivity.this);

                numBox.setInputType(InputType.TYPE_CLASS_NUMBER);
                builder.setView(numBox);

                numBox.setHint("New Backoff Threshold");
                layout.addView(numBox); // Notice this is an add method

                // Set up the buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int newBackoffThreshold;

                        try {
                            newBackoffThreshold = Integer.parseInt(numBox.getText().toString());
                        }
                        catch (NumberFormatException e) {
                            Log.w(TAG, "Number format exception: " + e.getMessage());

                            CharSequence text = "Number format exception: " + e.getMessage();
                            int duration = Toast.LENGTH_SHORT;

                            Toast toast = Toast.makeText(BLMainActivity.this, text, duration);
                            toast.show();
                            return;
                        }

                        backoffThreshold = newBackoffThreshold;

                        mScanPreferencesEditor.putInt(BACKOFF_THRESHOLD, newBackoffThreshold).commit();

                        updateUI();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.setView(layout);
                builder.show();

            }
        });

        // allows the user to toggle whether or not more serious beacon spoofing security is in place; if auth (authentication) is
        // on, then BLEApp3 will erase the data information of beacons once they go out of range and reconnect to the beacons when
        // they come back in range to confirm that the MAC address advertised by the beacon is indeed its own reachable MAC address,
        // and not another beacon pretending to be this beacon
        //
        // if auth is turned off, then the application will save the beacon's location information upon first connecting and not erase
        // it even after the beacon goes out of range; this is less straining on the beacons, and may also improve performance on the application
        // side as well; it is recommended to have extra MAC address authentication off for this reason
        toggleAuthButton = (Button) findViewById(R.id.toggleAuthButton);
        toggleAuthButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beaconMACAuthentication = !beaconMACAuthentication;

                updateUI();
            }
        });

        // checks for the different permission required for the different parts of the app to work on start up; if
        // any of these permissions aren't enabled, the app will request user for permission and won't start scanning until they are enabled
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED  ) {

            startBLEService();
            startNFDService();

        } else {

            requestLocationPermission();

        }

        // enables bluetooth upon app startup
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }

        // update UI so buttons have correct text, devices are properly displayed, etc
        updateUI();

    }

    // starts the ble service
    private void startBLEService() {
        Intent bleIntent = new Intent(BLMainActivity.this, BLEService.class);
        boolean testBLEServiceStart = bindService(bleIntent, bleServiceConnection, BIND_AUTO_CREATE);
        if (testBLEServiceStart) {
            Log.d(TAG, "bindService for bleService was successful");
        } else {
            Log.d(TAG, "bindService for bleService was not successful");
            startBLEService();
        }
    }

    // starts the nfd service
    private void startNFDService() {
        Intent nfdIntent = new Intent(BLMainActivity.this, NFDService.class);
        boolean testNfdServiceStart = bindService(nfdIntent, nfdServiceConnection, BIND_AUTO_CREATE);
        if (testNfdServiceStart) {
            Log.d(TAG, "bindService for nfdService was successful");
        } else {
            Log.d(TAG, "bindService for nfdService was not successful");
            startNFDService();
        }
    }

    // function to start scan cycle
    private void startBLEScanCycle () {

        currentlyInScanCycle = true;

        scanForBleDevices();

        discoveredDevicesHashSet.clear();
        discoveredDevicesArrayAdapter.clear();

        devicesInRangeLastNScansHashMap.clear();

        advertisingDevicesWithinRangeArrayAdapter.clear();

    }

    // function to stop scan cycle
    private void stopBLEScanCycle() {

        currentlyInScanCycle = false;

        bluetoothAdapter.stopLeScan(bleScanCallback);

        handler.removeCallbacksAndMessages(null);

    }

    // Stops scanning after scanPeriod milliseconds to save battery
    private void scanForBleDevices() {

        discoveredDevicesArrayAdapter.clear();
        discoveredDevicesHashSet.clear();
        discoveredDevicesScanRecords.clear();

        Log.d(TAG, "we entered the scanLeDevice function");
        // Stops scanning after a pre-defined scan period.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                bluetoothAdapter.stopLeScan(bleScanCallback);
                Log.d(TAG, "Within scan cycle: stopped LE scan after timer ran out");

                // after scan finishes, sends the data to NFD face
                if (nfdService != null) {
                    Log.d(TAG, "nfdService wasn't null");

                    // these two lines are for the eventual implementation of splitting scan results into different
                    // data packets, so that data packet size is not overwhelmed; for now the scan is limited to 10 devices
                    //int totalDevices = discoveredDevices.size();
                    //int numberOfExtraInterestsNeeded = totalDevices / MAX_DEVICES_IN_ONE_CHUNK;

                    lastListOfDiscoveredDevices = DeviceInfoHelperFunctions.discoveredDeviceInfoToString(discoveredDevices, discoveredDevicesScanRecords)
                            + "Device name: dummy device" + "," + "Device Address: 11:11:11:11:11:11";

                    //Log.d(TAG, stringArrayToString(discoveredDevices));

                    lastDiscoveredDevicesHashSet.clear();
                    lastDiscoveredDevicesHashSet.addAll(discoveredDevicesHashSet);

                    updateMissedScanCounts(devicesInRangeLastNScansHashMap, lastDiscoveredDevicesHashSet);

                    if (scansSinceLastInRangeOfLocationBeacons > backoffThreshold) {
                        Log.d(TAG, "We didn't detect location beacons for a long time, slowing down scan interval");
                        if (scanInterval < maxInterval) {
                            if (devicesWithActiveConnections) {
                                scanInterval *= intervalMultiplier;
                            }
                        }
                    }
                    else {
                        scanInterval = minInterval;
                    }

                    if (currentlyInScanCycle)
                        sendBroadcast(new Intent(START_SCAN_INTERVAL_WAIT));


                    /*
                    if (connectedOnThisScan) {
                        bleService.connect(currentDeviceAddressToGetLocationFrom);
                        connectedOnThisScan = false;
                        Log.d(TAG, "Connected on this scan...");
                    }
                    */


                    Log.d(TAG, "Currently connected devices: \n" +
                    bleService.getAllConnectedDevices());

                    if (advertisingDevicesWithinRange.size() == 0) {
                        // if there were no devices with in range for this scan, increment the counter
                        scansSinceLastInRangeOfLocationBeacons++;
                    }
                    else {
                        // if there were devices within range for this scan, reset the counter to 0
                        scansSinceLastInRangeOfLocationBeacons = 0;
                    }

                } else {
                    Log.d(TAG, "nfdService was null");
                }

                updateUI();
            }
        }, scanPeriod);

        Log.d(TAG, "Within scan cycle: started LE scanner");
        // we pass in the iear beacons' service uuid for reading and writing over bluetooth low energy, so that
        // only icear beacons are detected in scans
        bluetoothAdapter.startLeScan(new UUID[] {UUID.fromString(BLEService.hardcodedServiceUuid)}, bleScanCallback);

        updateUI();
    }

    // Manual device scan callback.
    private BluetoothAdapter.LeScanCallback bleScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     final byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final String deviceAddress = device.getAddress();
                            final String deviceAddressNoColons = BluetoothHelperFunctions.removeColonsFromMACAddress(deviceAddress);
                            String deviceName = device.getName();
                            String deviceInfo = DeviceInfoHelperFunctions.makeDeviceInfo(deviceName, deviceAddress);
                            if (!discoveredDevicesHashSet.contains(deviceInfo)) {

                                discoveredDevicesScanRecords.put(deviceAddress, scanRecord);
                                discoveredDevicesObjects.add(device);
                                discoveredDevicesHashSet.add(deviceInfo);
                                discoveredDevicesArrayAdapter.add(deviceInfo);

                                if (!devicesInRangeLastNScansHashMap.containsKey(deviceInfo)) {
                                    devicesInRangeLastNScansHashMap.put(deviceInfo, 0);

                                    if (addressToLocation.containsKey(deviceAddressNoColons)) {
                                        sendDataForBeaconsInRangeUpdate();
                                    }

                                    advertisingDevicesWithinRangeArrayAdapter.add(deviceInfo);
                                    // since we just came into range of a location beacon, we reset the scan counter for
                                    // being in range of location beacons and set the scan interval to the minimum
                                    scansSinceLastInRangeOfLocationBeacons = 0;
                                    scanInterval = minInterval;
                                }

                                class OneShotTask implements Runnable {
                                    OneShotTask() {
                                    }

                                    public void run() {
                                        if (!addressToLocation.containsKey(deviceAddressNoColons)) {

                                            /*
                                            if (!currentlyConnectingOrConnected) {
                                                if (!connectedOnThisScan) {
                                                    Log.d(TAG, "Didn't connect on this scan yet, found device to connect to.");
                                                    currentlyConnectingOrConnected = true;
                                                    currentDeviceAddressToGetLocationFrom = deviceAddress;
                                                    connectedOnThisScan = true;
                                                }
                                                else {
                                                    Log.d(TAG, "Already connected on this scan.");
                                                }
                                            }
                                            */



                                            // allows for devices to be removed from ble service's list of connected devices, rather
                                            // than waiting for the asynchronous device disconnected callback
                                            /*
                                            if (bleService.activeDevices.containsKey(deviceAddress)) {

                                                bleService.activeDevices.get(deviceAddress).close();

                                                bleService.activeDevices.remove(deviceAddress);

                                                connectedDevicesArrayAdapter.remove
                                                        (DeviceInfoHelperFunctions.makeDeviceInfo(deviceName, deviceAddress));

                                            }
                                            */

                                            bleService.connect(deviceAddress);

                                            //nfdService.expressDeviceLocationInterest(deviceAddress);
                                        }
                                    }
                                }

                                Thread t = new Thread(new OneShotTask());
                                t.start();

                                updateUI();
                            }
                        }
                    });
                }
            };

    private void sendDataForBeaconsInRangeUpdate() {
        String beaconsInRangeListString =
                DeviceInfoHelperFunctions.createLocationBeaconInRangeList(addressToLocation, devicesInRangeLastNScansHashMap.keySet());

        nfdService.sendDataToNFDFace(beaconsInRangeListString, getString(R.string.baseName) + "/status");
    }

    String updateMissedScanCounts (HashMap<String, Integer> devicesInRangeLastNScansHashMap, HashSet<String> discoveredDevicesHashSet) {

        String listOfDevicesThatLeftRange = "";

        ArrayList<String> devicesToRemove = new ArrayList<String>();

        for (String device : devicesInRangeLastNScansHashMap.keySet()) {

            int deviceMissedScanCount = devicesInRangeLastNScansHashMap.get(device);

            if (!discoveredDevicesHashSet.contains(device)) {
                // increments the deviceMissedScanCount by one for this device, since it wasn't discovered on the last scan
                devicesInRangeLastNScansHashMap.put(device,
                        ++deviceMissedScanCount);
            }
            else if (deviceMissedScanCount > 0){
                // resets the missed scan count of devices that were detected on this scan
                devicesInRangeLastNScansHashMap.put(device, 0);
            }

            if (deviceMissedScanCount > maxTolerableMissedScans) {
                devicesToRemove.add(device);

                listOfDevicesThatLeftRange += device + "\n" + "--------------------" + "\n";
            }
        }

        for (String device : devicesToRemove) {

            devicesInRangeLastNScansHashMap.remove(device);

            advertisingDevicesWithinRangeArrayAdapter.remove(device);

            String deviceAddressWithColons = DeviceInfoHelperFunctions.getDeviceAddressFromDeviceInfo(device);
            String deviceAddressNoColons =
                    BluetoothHelperFunctions.removeColonsFromMACAddress(deviceAddressWithColons);

            //Log.d(TAG, "UPDATEMISSEDSCANCOUNT: " + deviceAddressWithColons + ", " + deviceAddressNoColons);

            String dataLocation = addressToLocation.get(deviceAddressNoColons);

            if (beaconMACAuthentication) {
                if (dataLocation != null) {
                    addressToLocation.remove(deviceAddressNoColons);
                    addressToLocationMappingsAdapter.remove(
                            "Device location: " + "\n" + dataLocation + "\n" +
                                    "Device address: " + "\n" + deviceAddressWithColons);
                }
            }
        }

        if (!listOfDevicesThatLeftRange.equals("")) {
            sendDataForBeaconsInRangeUpdate();
        }

        Log.d(TAG, bleService.getAllConnectedDevices());

        return listOfDevicesThatLeftRange;
    }

    // this function is called throughout the program to update the UI
    void updateUI() {

        toggleScanButton.setText(
                currentlyInScanCycle ? "Scan Off" : "Scan On"
        );

        toggleAuthButton.setText(
                beaconMACAuthentication ? "MAC Auth Off" : "MAC Auth On"
        );

        scanIntervalDisplay.setText(Long.toString(scanInterval));

        scanPeriodDisplay.setText(Long.toString(scanPeriod));

        maxTolerableMissedScansDisplay.setText(Long.toString(maxTolerableMissedScans));

        maxIntervalDisplay.setText(Long.toString(maxInterval));

        minIntervalDisplay.setText(Long.toString(minInterval));

        backoffThresholdDisplay.setText(Long.toString(backoffThreshold));
    }

    // function for requesting location services permission from the user
    protected boolean requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "We didn't have the user's permission to use location services yet...");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION);
        } else {
            Log.d(TAG, "We DID have the user's permission to use location services");

            requestReadStoragePermission();

            return true;
        }

        return false;
    }

    // function for requesting read storage permission from the user
    protected boolean requestReadStoragePermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "We didn't have the user's permission to read storage yet...");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_STORAGE);
        } else {
            Log.d(TAG, "We DID have the user's permission to read storage");
            requestWriteStoragePermission();
            return true;
        }

        return false;
    }

    // function for requesting write storage permission from the user
    protected boolean requestWriteStoragePermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "We didn't have the user's permission to write storage yet...");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
        } else {
            Log.d(TAG, "We DID have the user's permission to write storage");

            startNFDService();

            return true;
        }

        return false;
    }

    // function that checks the result of asking the user for location services permission
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_COARSE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "We got the user's permission to do location services....");

                    requestReadStoragePermission();

                } else {
                    Log.d(TAG, "We didn't get user's permission to do location services...");

                    requestLocationPermission();
                }
                break;
            }
            case REQUEST_READ_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "We got the user's permission to do read external storage services....");

                    requestWriteStoragePermission();
                } else {
                    Log.d(TAG, "We didn't get user's permission to do read external storage services...");

                    requestReadStoragePermission();
                }
                break;
            }
            case REQUEST_WRITE_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "We got the user's permission to do write external storage services....");

                    startNFDService();

                } else {
                    Log.d(TAG, "We didn't get user's permission to do write external storage services...");

                    requestWriteStoragePermission();
                }
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (nfdServiceConnection != null)
            unbindService(nfdServiceConnection);

        if (bleServiceConnection != null)
            unbindService(bleServiceConnection);

        unregisterReceiver(nfdStatusListener);
        unregisterReceiver(bleStatusListener);
        unregisterReceiver(startNewScanListener);
        unregisterReceiver(startScanIntervalWaitListener);
    }

    public static BLMainActivity getInstance() {
        return mainActivity;
    }

    Byte[] convertByteArrayToObjectByteArray (byte[] arr) {

        Byte[] objectByteArray = new Byte[arr.length];

        int i = 0;
        for (byte b: arr) {
            objectByteArray[i++] = new Byte(b);
        }

        return objectByteArray;
    }

    byte[] convertObjectByteArrayToByteArray (Byte[] arr) {

        byte[] byteArray = new byte[arr.length];

        int i = 0;
        for (Byte b: arr) {
            byteArray[i++] = b.byteValue();
        }

        return byteArray;
    }
}
