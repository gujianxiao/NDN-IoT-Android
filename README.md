# NDN-IoT-Android
This is an applicatione example to show basic function of NDN communication, security sign-on and trust policy switching between the Android phone and development boards using ndn-lite. Specifically, this application consists of two parts: User application in the Android phone and [ndn-lite application](https://github.com/gujianxiao/ndn-lite-application-for-nRF52840-BLE_version/tree/master) in the development board. The user application is a general Android application that provides UI in terms of available devices, basic devices information and turst policy options etc. The ndn-lite application uses ndn-lite so as to provide NDN based communication, security sign-on and trust policy switching functions etc. Currently this application uses BLE as the face to transmit packets between the Android phone and development boards.
## Requeriments:
    Android phone (>=Android 6.0)
    Min SDK version: 23
## Get start:
1) Download or copy the repository into your local directory.  
2) Open Android project `NDN-IoT-Android`. Then, compile and build the project.  
3) Connect your Android phone with the PC and turn on the debug mode of the phone. Run the project and choose the phone as the target device (remember to open all permission of the installed application called `NDN-IoT`).
4) Login the application and press the button on the right-up side. If there is any device finishing the security sign-on, it will should in the UI. You can also press the button again to find the new device (As for now, the max number of the devices is two). 
5) Press the picture of the device, you can see the trust policy options: "Only controller" or "All nodes". "Only controller" means the user cannot turn on the LED 1 by pressing Button 1 or send command from the other board. "All nodes" means inverse. Press the option you want, the phone will send trust policy switching Interest to the device, which will blink it's LED 4 to indicate the switching.
## Tips:
* Please make sure your [Android Studio](https://developer.android.com/studio/?gclid=EAIaIQobChMI18_1gPnL3wIVyiCtBh2SfwrAEAAYASAAEgLkEPD_BwE) is the newest version, or you may cannot build the project corectly.
* The user Android application is a very modest start for now. The other functions on the application UI may not work and we will try to finish it as soon as possible.
## License

# ndn-lite-android-support-library
I. OVERVIEW
The NDN-Iot-Android library uses the ndn-lite-android-support-library, which is simply meant to provide Android developers with an easier way to develop applications that are compatible with the NDN-Lite library; the goal is to allow intercommunication between Android devices and constrained devices.

The ndn-lite-android-support-library contains two main features; API's for doing NDN over BLE communication, and API's for acting as a controller in the secure sign on protocol. Both of these higher level API's depend on a lower level API for doing basic BLE communication.

a. More Information Regarding the NDN over BLE Support

The library provides the BLEFace object for doing NDN over BLE communication; this object abstracts away the lower level BLE communication support of the library and provides the user a partially implemented Face (which inherits from the Face object of the jndn library). The example shows how this BLEFace object can be used to send interests and satisfy incoming interests with data.

b. More information Regarding the Sign On Protocol Controller Support

The API for the secure sign on protocol controller provided uses the BLE API's of the support library to handle the sign on protocol for the user. As shown in the example application, the user only needs to initialize the BLEUnicastConnectionMaintainer and the SignOnBasicControllerBLE, and then add devices that are expected to undergo the sign on protocol through the SignOnBasicControllerBLE.addDevicePendingSignOn function. All things related to transport (e.g. maintaining BLE connections to devices using the ndn-lite library) and the secure sign on protocol (e.g. processing and responding to messages) are handled internally. The user will simply receive a callback when the sign on protocol has been completed for a particular device.

c. More Information Regarding the Basic BLE Communication Support

The main lower level BLE communication functionality offered by the library are scanning for other devices and connecting / communicating with them through BLE unicast connections.

The scanning functionality can be customized, or simply used with default values.

The lower level BLE communication support of the library facilitates communication with BLE devices through a predefined "data transfer" characteristic, which it is expected that the BLE devices being connected to have. If the BLE device being connected to does not have at least one "data transfer" characteristic in its services with the exact same UUID as is defined in the BLECentralTransport class, then no data transfer using the lower level BLE communication support of the library will be possible.

It is assumed that the central device (the phone) and the peripheral device (the BLE device) will not send data to each other at the same time, since they use a single characteristic for both sending and receiving (for simplicity). Separate characteristics for sending and receiving may be implemented in the future.

The Android application demonstrates using these functionalities of the lower level BLE communication support of the library: 1) Scanning for devices (optionally filtering by service uuid) 2) Connecting to devices a) Establishing connection b) Discovering services that contain the data transfer characteristic c) Enabling notifications for data transfer characteristics on all services that contain the data transfer characteristic d) Negotiating an MTU of the maximum characteristic value size, 512 3) Exchanging data with devices a) Sending data to a device c) Receiving data from the device through data transfer characteristic notifications

II. INSTALLATION
***NOTE: You should clone the repository with the --recursive option; see section b) of installation for why.

a) Using the NDN Lite Android Support library for your Android application

If you would like to use the ndn-lite-android-support-library in your Android application, you can follows these steps (it is assumed that you are using Android Studio):

Clone this repository to your machine.
Open the android_library_and_example application in Android Studio.
Go to the Gradle tool window - if you cannot see the Gradle tool window, go to View > Tool Windows > Gradle - if you see "nothing to show" in the Gradle tool window, clean and rebuild your project and do a gradle sync
In the Gradle tool window, go to android_library_and_example > ndnlitesupport > build, and double click on assemble.
After assembling has finished, you should have an aar file for the ndnlitesupport library in the following directory: /android_library_and_example/ndnlitesupport/build/outputs/aar
Inside of your Android Studio project, go to File > New > Module
Select to "Import .JAR/.AAR Package"
When selecting the .aar file to import, navigate to /android_library_and_example/ndnlitesupport/build/outputs/aar/ndnlitesupport-debug.aar
After pressing Finish, go to File > Project Structure, and go to the Modules section. Click on your main application module, and go to the dependencies tab.
Inside of the dependencies tab, click on the green plus sign on the right hand side of the window, and select to add a "Module dependency".
Select ndnlitesupport from the window that pops up.
You should now be able to use the library in your Android application.
