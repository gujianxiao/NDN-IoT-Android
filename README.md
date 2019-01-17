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
* If you would like to use the ndn-lite-android-support-library in your Android application, you can follows these steps (it is assumed that you are using Android Studio):

1) Clone this repository to your machine.
2) Open the android_library_and_example application in Android Studio.
3) Go to the Gradle tool window - if you cannot see the Gradle tool window, go to View > Tool Windows > Gradle - if you see "nothing to show" in the Gradle tool window, clean and rebuild your project and do a gradle sync
4) In the Gradle tool window, go to android_library_and_example > ndnlitesupport > build, and double click on assemble.
5) After assembling has finished, you should have an aar file for the ndnlitesupport library in the following directory: /android_library_and_example/ndnlitesupport/build/outputs/aar
6) Inside of your Android Studio project, go to File > New > Module
7) Select to "Import .JAR/.AAR Package"
8) When selecting the .aar file to import, navigate to /android_library_and_example/ndnlitesupport/build/outputs/aar/ndnlitesupport-debug.aar
9) After pressing Finish, go to File > Project Structure, and go to the Modules section. Click on your main application module, and go to the dependencies tab.
10) Inside of the dependencies tab, click on the green plus sign on the right hand side of the window, and select to add a "Module dependency".
11) Select ndnlitesupport from the window that pops up.
You should now be able to use the library in your Android application.
## License


