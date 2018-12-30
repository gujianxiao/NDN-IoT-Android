package com.example.chenbo.helloworld;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

//import com.intel.jndn.utils.Publisher;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.HmacWithSha256Signature;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.KeyLocatorType;
import net.named_data.jndn.Name;
import net.named_data.jndn.NetworkNack;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnNetworkNack;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnRegisterSuccess;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.util.Blob;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

// these are not used in the current publishing of device location information because the
// mobile terminal app in c# doesn't use these publish subscribe objects from the jndn utils


public class NFDService extends Service {

    private static String TAG = "NFDService";

    // strings for the intent filter
    public final static String NFD_STOPPED = "NFD_STOPPED";
    public final static String FACE_CLOSED = "FACE_CLOSED";
    public final static String INTEREST_RECEIVED = "INTEREST_RECEIVED";
    public final static String INTEREST_DISCOVER_RECEIVED = "INTEREST_DISCOVER_RECEIVED";
    public final static String INTEREST_CONNECT_RECEIVED = "INTEREST_CONNECT_RECEIVED";
    public final static String INTEREST_GET_SERVICES_RECEIVED = "INTEREST_GET_SERVICES_RECEIVED";
    public final static String DATA_RECEIVED = "DATA_RECEIVED";
    public final static String INTEREST_SENT = "INTEREST_SENT";
    public final static String DATA_SENT = "DATA_SENT";
    public final static String INTEREST_DISCONNECT_RECEIVED = "INTEREST_DISCONNECT_RECEIVED";
    public final static String INTEREST_CHARACTERISTIC_ACTION_RECEIVED = "INTEREST_CHARACTERISTIC_ACTION_RECEIVED";
    public final static String INTEREST_DESCRIPTOR_ACTION_RECEIVED = "INTEREST_DESCRIPTOR_ACTION_RECEIVED";
    public final static String INTEREST_READ_FROM_DEVICE = "INTEREST_READ_FROM_DEVICE";
    public final static String SEND_LOCATION_DATA = "SEND_LOCATION_DATA";
    public final static String DEVICE_LOCATION_INTEREST_NACK = "DEVICE_LOCATION_INTEREST_NACK";
    public final static String DEVICE_LOCATION_INTEREST_TIMEOUT = "DEVICE_LOCATION_INTEREST_TIMEOUT";
    public final static String DEVICE_LOCATION_INTEREST_FAILEDVERIFY = "DEVICE_LOCATION_INTEREST_FAILEDVERIFY";
    public final static String DEVICE_LOCATION_INTEREST_DATA = "DEVICE_LOCATION_INTEREST_DATA";
    public final static String DEVICE_LOCATION_INTEREST_EXCEPTION = "DEVICE_LOCATION_INTEREST_EXCEPTION";

    // strings for intent extras
    public final static String DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public final static String DATA_PACKET_AS_BYTE_ARRAY = "DATA_PACKET_AS_BYTE_ARRAY";

    // stores the last data packet we retrieved from a device; that way, interest to read from a device
    // can be satisfied by this device's location data
    HashMap<String, Byte[]> lastDataPacketForDevices;

    // variables related to writing and reading a key from a file
    /******************************************************************************/
    // hard coded hmacKey, the app writes this to a file and then reads it back for demonstration purposes
    static byte hmacKeyTest[] = { 1, 2, 3, 4 };
    // testing reading the hmackey in from external memory; key is read from file and then stored in this array
    static byte hmacKey[] = {};
    // path in external storage that key file is stored to
    String keyPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test";
    /*********************************************************************************************/

    private Face mFace;
    private KeyChain mKeyChain;
    // the Publisher from jndn utils is not currently used to publish device status information; this is because
    // there is no corresponding object in the .NET library, so we just make the mobile terminal send out interests
    // every second and this NFDService publishes information
    //Publisher statusPublisher;
    boolean networkThreadCurrentlyRunning = false;

    // just kind of a hack, will respond with the current state to every 5th status interest just in case something gets restarted or something
    int mStatusInterestCounter = 0;
    int mStatusInterestInterval = 5;

    // sends string data to NFD face, using the name passed in for the data name
    public void sendDataToNFDFace(String data, String prefix) {

        String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());

        String prefixWithTimestamp = prefix + "/" + timeStamp;

        Name dataName = new Name(prefixWithTimestamp);

        Data sendData = new Data();
        sendData.setName(dataName);
        Blob content = new Blob(data.toString());
        sendData.setContent(content);

        HmacWithSha256Signature signature = new HmacWithSha256Signature();

        signature.getKeyLocator().setType(KeyLocatorType.KEYNAME);
        signature.getKeyLocator().setKeyName(new Name("key1"));

        sendData.setSignature(signature);

        KeyChain.signWithHmacWithSha256(sendData, new Blob(hmacKey));

        Log.d(TAG, "we entered the sendDataToNFDFace function: \n" +
                "Name: " + dataName.toString() + "\n" +
                "Content: \n" + content.toString()
        );

        class OneShotTask implements Runnable {
            Data data;
            OneShotTask(Data d) { data = d; }
            public void run() {
                try {
                    mFace.putData(data);
                } catch (IOException e) {
                    Log.d(TAG, "failure when responding to data interest: " + e.toString());
                }
            }
        }
        Thread t = new Thread(new OneShotTask(sendData));
        t.start();

    }

    // sends a data packet in byte array form directly to NFD face
    public void sendDataPacketAsBytesToNFDFace(byte[] dataPacketAsBytes) {

        Data dataPacket = interpretByteArrayAsData(dataPacketAsBytes);

        Log.d(TAG, "sending data packet with name: " + dataPacket.getName().toString());

        class OneShotTask implements Runnable {
            Data data;
            OneShotTask(Data d) { data = d; }
            public void run() {
                try {
                    mFace.putData(data);
                } catch (IOException e) {
                    Log.d(TAG, "failure when responding to data interest: " + e.toString());
                }
            }
        }
        Thread t = new Thread(new OneShotTask(dataPacket));
        t.start();
    }

    private final OnInterestCallback OnInterestDeviceRead = new OnInterestCallback() {
        @Override
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {

            Log.d(TAG, "we got an interest to directly read a data packet from the arduino");

            String interestName = interest.getName().toString();

            String macAddressNoColons = interestName.substring(interestName.lastIndexOf("/") + 1);

            String macAddressWithColons = BluetoothHelperFunctions.addColonsToMACAddress(macAddressNoColons);


            Intent directlyReadFromDeviceIntent = new Intent(INTEREST_READ_FROM_DEVICE);
            directlyReadFromDeviceIntent.putExtra(DEVICE_ADDRESS, macAddressWithColons);
            sendBroadcast(directlyReadFromDeviceIntent);

        }
    };

    private final OnInterestCallback OnInterestDiscover = new OnInterestCallback() {
        @Override
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {

            Log.d(TAG, "we got an interest to discover devices");

            Log.d(TAG, "list of last discovered devices: " + BLMainActivity.getInstance().lastListOfDiscoveredDevices);

            sendDataToNFDFace(BLMainActivity.getInstance().lastListOfDiscoveredDevices + "---" +
            BLMainActivity.getInstance().bleService.getAllConnectedDevices(),
            getString(R.string.baseName) + "/" + "discover");

            Intent interestDiscoverReceivedIntent = new Intent(INTEREST_DISCOVER_RECEIVED);
            sendBroadcast(interestDiscoverReceivedIntent);

        }
    };

    private final OnInterestCallback OnInterestStatus = new OnInterestCallback() {
        @Override
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {

            Log.d(TAG, "we got an interest for device status");

            if (mStatusInterestCounter == 0) {
                Log.d(TAG, "we got our first status interest");

                String beaconsInRangeListString =
                        DeviceInfoHelperFunctions.createLocationBeaconInRangeList(
                                BLMainActivity.getInstance().addressToLocation,
                                BLMainActivity.getInstance().devicesInRangeLastNScansHashMap.keySet());

                sendDataToNFDFace(beaconsInRangeListString, getString(R.string.baseName) + "/status");

                mStatusInterestCounter++;

            }
            else if (mStatusInterestCounter == mStatusInterestInterval) {
                mStatusInterestCounter = 0;
            }
            else {
                mStatusInterestCounter++;
            }
        }
    };

    private final OnRegisterSuccess OnPrefixRegisterSuccess = new OnRegisterSuccess() {
        @Override
        public void onRegisterSuccess(Name prefix, long registeredPrefixId) {
            Log.d(TAG, "successfully registered data prefix: " + prefix);
        }
    };

    private final OnRegisterFailed OnPrefixRegisterFailed = new OnRegisterFailed() {
        @Override
        public void onRegisterFailed(Name prefix) {
            Log.d(TAG, "we failed to register the data prefix: " + prefix);
        }
    };

    // expresses an interest for a device's location with a given MAC address
    public void expressDeviceLocationInterest(final String deviceAddress) {

        Log.d(TAG, "express device location interest got called");

        Interest interest = new Interest(new Name(getString(R.string.baseName) + "/read/" +
                BluetoothHelperFunctions.removeColonsFromMACAddress(deviceAddress)));

        interest.setInterestLifetimeMilliseconds(500);

        class OneShotTask implements Runnable {
            Interest interest;
            OneShotTask(Interest i) { interest = i; }
            public void run() {
                try {
                    mFace.expressInterest(interest, OnDeviceLocationData, OnDeviceLocationInterestTimeout, OnDeviceLocationInterestNack);
                } catch (IOException e) {

                    Intent deviceLocationInterestExceptionIntent = new Intent(DEVICE_LOCATION_INTEREST_EXCEPTION);

                    deviceLocationInterestExceptionIntent.putExtra(DEVICE_ADDRESS, deviceAddress);

                    sendBroadcast(deviceLocationInterestExceptionIntent);

                    e.printStackTrace();
                }
            }
        }
        Thread t = new Thread(new OneShotTask(interest));
        t.start();

    }

    private final OnData OnDeviceLocationData = new OnData() {
        @Override
        public void onData(Interest interest, Data data) {
            String nameString = data.getName().toString();
            String deviceAddress = BluetoothHelperFunctions.addColonsToMACAddress(nameString.substring(nameString.lastIndexOf("/") + 1));

            Blob keyBlob = new Blob(hmacKey);

            if(KeyChain.verifyDataWithHmacWithSha256(data, keyBlob)) {
                Log.d("receivedPacketData", "successfully verified data with name: " + nameString);
                Log.d(TAG, "received device location data for " + nameString);

                Intent deviceLocationDataIntent = new Intent(DEVICE_LOCATION_INTEREST_DATA);

                deviceLocationDataIntent.putExtra(DEVICE_ADDRESS, deviceAddress);
                deviceLocationDataIntent.putExtra(DATA_PACKET_AS_BYTE_ARRAY, data.getDefaultWireEncoding().getImmutableArray());

                sendBroadcast(deviceLocationDataIntent);

            }
            else {
                Log.d("receivedPacketData", "failed to verify data with name: " + data.getName().toString() + ", ignoring it");

                Intent deviceLocationFailedVerifyIntent = new Intent(DEVICE_LOCATION_INTEREST_FAILEDVERIFY);

                deviceLocationFailedVerifyIntent.putExtra(DEVICE_ADDRESS, deviceAddress);

                sendBroadcast(deviceLocationFailedVerifyIntent);
            }

        }
    };

    private final OnTimeout OnDeviceLocationInterestTimeout = new OnTimeout() {
        @Override
        public void onTimeout(Interest interest) {
            String nameString = interest.getName().toString();
            String deviceAddress = BluetoothHelperFunctions.addColonsToMACAddress(nameString.substring(nameString.lastIndexOf("/") + 1));

            Intent deviceLocationInterestTimeoutIntent = new Intent(DEVICE_LOCATION_INTEREST_TIMEOUT);
            deviceLocationInterestTimeoutIntent.putExtra(DEVICE_ADDRESS, deviceAddress);

            sendBroadcast(deviceLocationInterestTimeoutIntent);

            Log.d(TAG, "timed out waiting for " + nameString);
        }
    };

    private final OnNetworkNack OnDeviceLocationInterestNack = new OnNetworkNack() {
        @Override
        public void onNetworkNack(Interest interest, NetworkNack networkNack) {
            String nameString = interest.getName().toString();
            String deviceAddress = BluetoothHelperFunctions.addColonsToMACAddress(nameString.substring(nameString.lastIndexOf("/") + 1));

            Intent deviceLocationInterestNackIntent = new Intent(DEVICE_LOCATION_INTEREST_NACK);
            deviceLocationInterestNackIntent.putExtra(DEVICE_ADDRESS, deviceAddress);

            sendBroadcast(deviceLocationInterestNackIntent);

            Log.d(TAG, "received NACK for " + nameString);
        }
    };

    private void registerDataPrefix () {
        Log.d(TAG, "registering data prefix...");
        try {
            mFace.registerPrefix(new Name(getString(R.string.baseName) + "/discover"), OnInterestDiscover,
                    OnPrefixRegisterFailed, OnPrefixRegisterSuccess);
            mFace.registerPrefix(new Name(getString(R.string.baseName) + "/read"), OnInterestDeviceRead,
                    OnPrefixRegisterFailed, OnPrefixRegisterSuccess);
            mFace.registerPrefix(new Name(getString(R.string.baseName) + "/status"), OnInterestStatus,
                    OnPrefixRegisterFailed, OnPrefixRegisterSuccess);
        } catch (IOException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void initializeKeyChain() {
        Log.d(TAG, "initializing keychain");
        MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
        MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
        IdentityManager identityManager = new IdentityManager(identityStorage, privateKeyStorage);
        mKeyChain = new KeyChain(identityManager);
        mKeyChain.setFace(mFace);
    }

    private void setCommandSigningInfo() {
        Log.d(TAG, "setting command signing info");
        Name defaultCertificateName;
        try {
            defaultCertificateName = mKeyChain.getDefaultCertificateName();
        } catch (SecurityException e) {
            Log.d(TAG, "unable to get default certificate name");

            // NOTE: This is based on apps-NDN-Whiteboard/helpers/Utils.buildTestKeyChain()...
            Name testIdName = new Name("/test/identity");
            try {
                defaultCertificateName = mKeyChain.createIdentityAndCertificate(testIdName);
                mKeyChain.getIdentityManager().setDefaultIdentity(testIdName);
                Log.d(TAG, "created default ID: " + defaultCertificateName.toString());
            } catch (SecurityException e2) {
                defaultCertificateName = new Name("/bogus/certificate/name");
            }
        }
        mFace.setCommandSigningInfo(mKeyChain, defaultCertificateName);
    }

    private final Thread networkThread = new Thread(new Runnable() {

        @Override
        public void run () {
            Log.d(TAG, "network thread started");
            try {
                mFace = new Face("localhost");
                initializeKeyChain();
                setCommandSigningInfo();
                registerDataPrefix();

            } catch (Exception e) {
                Log.w(TAG, "error during network thread initialization" + e.getMessage());
            }
            while (networkThreadCurrentlyRunning) {
                try {
                    mFace.processEvents();
                    Thread.sleep(100); // avoid hammering the CPU
                } catch (IOException e) {
                    Log.w(TAG, "error in processEvents loop" + e.getMessage());
                } catch (Exception e) {
                    Log.w(TAG, "error in processEvents loop" + e.getMessage());
                }
            }
            doFinalCleanup();
            Log.d(TAG, "network thread stopped");
        }
    });

    private final IBinder mBinder = new NFDService.LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        loadKeyFile();

        if (!networkThreadCurrentlyRunning) {
            networkThreadCurrentlyRunning = true;
            networkThread.start();
        }

        lastDataPacketForDevices = new HashMap<>();

        return mBinder;
    }

    public class LocalBinder extends Binder {
        NFDService getService() {
            return NFDService.this;
        }
    }

    public static IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(NFD_STOPPED);
        filter.addAction(FACE_CLOSED);
        filter.addAction(INTEREST_RECEIVED);
        filter.addAction(DATA_RECEIVED);
        filter.addAction(INTEREST_SENT);
        filter.addAction(DATA_SENT);
        filter.addAction(INTEREST_CONNECT_RECEIVED);
        filter.addAction(INTEREST_GET_SERVICES_RECEIVED);
        filter.addAction(INTEREST_DISCOVER_RECEIVED);
        filter.addAction(INTEREST_DISCONNECT_RECEIVED);
        filter.addAction(INTEREST_CHARACTERISTIC_ACTION_RECEIVED);
        filter.addAction(INTEREST_DESCRIPTOR_ACTION_RECEIVED);
        filter.addAction(INTEREST_READ_FROM_DEVICE);
        filter.addAction(DEVICE_LOCATION_INTEREST_NACK);
        filter.addAction(DEVICE_LOCATION_INTEREST_TIMEOUT);
        filter.addAction(DEVICE_LOCATION_INTEREST_FAILEDVERIFY);
        filter.addAction(DEVICE_LOCATION_INTEREST_DATA);
        filter.addAction(DEVICE_LOCATION_INTEREST_EXCEPTION);
        return filter;
    }

    private void doFinalCleanup() {
        Log.d(TAG, "cleaning up and resetting service...");
        if (mFace != null) mFace.shutdown();
        mFace = null;
        Log.d(TAG, "service cleanup/reset complete");
    }

    // makes an interest and then turns it into a byte array in order to be sent to the BLE module
    public static byte[] makeByteArrayInterestPacketForBLEService(String name) {

        Interest interest = new Interest(new Name(name));

        Blob interestBlob = interest.wireEncode();

        if (interestBlob == null) {
            Log.d(TAG, "something went wrong making the byte array out of the interest packet");
            return null;
        }

        byte[] interestByteArray = interestBlob.getImmutableArray();

        if (interestByteArray != null) {
            return interestByteArray;
        }

        return null;
    }

    // interprets a byte array as an interest packet
    public static void interpretByteArrayAsInterest(byte[] interestByteArray) {

        Interest interest = new Interest(new Name(""));

        Blob blobInterest = new Blob(interestByteArray);

        try {
            interest.wireDecode(blobInterest);
        } catch (EncodingException e) {
            e.printStackTrace();
        }


        Log.d("receivedPacketInterest", "received name: " + interest.getName().toString());

    }

    // interprets a byte array as a data packet
    public static Data interpretByteArrayAsData(byte[] dataByteArray) {

        Data data = new Data(new Name(""));

        Blob blobData = new Blob(dataByteArray);

        try {
            data.wireDecode(blobData);
        } catch (EncodingException e) {
            e.printStackTrace();
        }

        String dataName = data.getName().toString();

        Blob keyBlob = new Blob(hmacKey);

        if(KeyChain.verifyDataWithHmacWithSha256(data, keyBlob)) {
            Log.d("receivedPacketData", "successfully verified data with name: " + dataName);
        }
        else {
            Log.d("receivedPacketData", "failed to verify data with name: " + dataName + ", ignoring it");
            return null;
        }



        Log.d("receivedPacketData", "received name: " + dataName);
        Log.d("receivedPacketData", "received number: " + data.getContent().toString());

        return data;

    }

    // function that writes the key to a file and then reads the key back from the file
    // this is just to show that we can read the key from a file on the Android phone
    public void loadKeyFile() {

        File dir = new File(keyPath);

        dir.mkdirs();

        File file = new File(keyPath + "/testFile.txt");
        String saveText = new String(hmacKeyTest);

        FileHelperFunctions.save(file, saveText);

        byte[] loaded = FileHelperFunctions.load(file);

        Log.d(TAG, "loaded text: " + loaded.toString());

        hmacKey = Arrays.copyOfRange(loaded, 0, 4);

        for (int i = 0; i < hmacKey.length; i++) {
            Log.d(TAG, "Element " + i + " of key: " + Byte.toString(hmacKey[i]));
        }

    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "we got told to destroy ourselves");
        super.onDestroy();
        mFace.shutdown();
        networkThread.interrupt();
    }
}