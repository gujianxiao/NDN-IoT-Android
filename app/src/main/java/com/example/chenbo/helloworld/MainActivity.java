package com.example.chenbo.helloworld;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.security.v2.CertificateV2;
import net.named_data.jndn.util.Blob;

import java.io.IOException;

import NDNLiteSupport.BLEFace.BLEFace;
import NDNLiteSupport.BLEUnicastConnectionMaintainer.BLEUnicastConnectionMaintainer;
import NDNLiteSupport.NDNLiteSupportInit;
import NDNLiteSupport.SignOnBasicControllerBLE.SignOnBasicControllerBLE;
import NDNLiteSupport.SignOnBasicControllerBLE.secureSignOn.SignOnControllerResultCodes;

import static NDNLiteSupport.SignOnBasicControllerBLE.secureSignOn.SignOnControllerConsts.KD_PUB_CERTIFICATE_NAME_PREFIX;
import static NDNLiteSupport.SignOnBasicControllerBLE.secureSignOn.secureSignOnVariants.SecureSignOnVariantStrings.SIGN_ON_VARIANT_BASIC_ECC_256;
import static NDNLiteSupport.SignOnBasicControllerBLE.secureSignOn.utils.SecurityHelpers.asnEncodeRawECPublicKeyBytes;
import static com.example.chenbo.helloworld.HARDCODED_EXPERIMENTATION_SIGN_ON_BLE_ECC_256.*;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,SendInterestTask.Callback {

    private String TAG="MainActivity";

    private int signOnFlag=0;
    private int signOnCount=0;


    private TextView m_log;

    private MainUIUpdateService.UIUpdateBinder uiUpdateBinder;

//    // Log for tagging.
//    private final String TAG = MainActivity.class.getSimpleName();

    // Reference to interact with the secure sign on controller over BLE singleton.
    private SignOnBasicControllerBLE m_SignOnBasicControllerBLE;

    // Reference to interact with the ble unicast connection maintainer. Both the
    // BLEFace and the SignOnBasicControllerBLE object depend on this to proactively
    // maintain connections to devices using the ndn lite library. You MUST initialize
    // this for the SignOnBasicControllerBLE and BLEFace to work; if you do not initialize
    // this, then you will never actually connect to any devices over BLE.
    private BLEUnicastConnectionMaintainer m_BLEUnicastConnectionMaintainer;

    // Reference to manage a BLE face that is created to interact with a device after sign on.
    private BLEFace m_bleFace;
    private BLEFace m_bleFace2;


    // The device identifier of the example nRF52840, in hex string format.
    private String m_expectedDeviceIdentifierHexString = "010101010101010101010101";
    private String m_expectedDeviceIdentifierHexString2 = "010101010101010101010102";

    // Callback for when an interest is received. In this example, the nRf52840 sends an interest to
    // us after sign on is complete, and triggers this callback.
    OnInterestCallback onInterest = new OnInterestCallback() {
        @Override
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filter) {
            Log.i(TAG, "onInterest got called, prefix of interest: " + prefix.toUri());

            if (prefix.toUri().equals(KD_PUB_CERTIFICATE_NAME_PREFIX + m_expectedDeviceIdentifierHexString)) {
                Log.i(TAG, "Got interest for certificate of device with device identifier: " +
                        m_expectedDeviceIdentifierHexString);


                try {
                    Log.i(TAG, "Responding to interest from device with its certificate...");
                    face.putData(
                            SignOnBasicControllerBLE.getInstance().
                                    getKDPubCertificateOfDevice(m_expectedDeviceIdentifierHexString)
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (prefix.toUri().equals(KD_PUB_CERTIFICATE_NAME_PREFIX + m_expectedDeviceIdentifierHexString2)) {
                Log.i(TAG, "Got interest for certificate of device with device identifier: " +
                        m_expectedDeviceIdentifierHexString2);


                try {
                    Log.i(TAG, "Responding to interest from device with its certificate...");
                    face.putData(
                            SignOnBasicControllerBLE.getInstance().
                                    getKDPubCertificateOfDevice(m_expectedDeviceIdentifierHexString2)
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    SignOnBasicControllerBLE.SecureSignOnBasicControllerBLECallbacks m_secureSignOnBasicControllerBLECallbacks =
            new SignOnBasicControllerBLE.SecureSignOnBasicControllerBLECallbacks() {

                @Override
                public void onDeviceSignOnComplete(String deviceIdentifierHexString) {
                    Log.i(TAG, "Onboarding was successful for device with device identifier hex string : " +
                            deviceIdentifierHexString);
                    Log.i(TAG, "Mac address of device succesfully onboarded: " +
                            m_SignOnBasicControllerBLE.getMacAddressOfDevice(deviceIdentifierHexString));
                    Log.i(TAG, "Name of device's KDPubCertificate: " +
                            m_SignOnBasicControllerBLE.getKDPubCertificateOfDevice(deviceIdentifierHexString)
                                    .getName().toUri()

                    );
                    signOnFlag=1;
                    signOnCount++;

                    // Create a BLE face to the device that onboarding completed successfully for.
                    if(deviceIdentifierHexString.equals(m_expectedDeviceIdentifierHexString)) {
                        Log.i(TAG, "onDeviceSignOnComplete: create ble face for board 1");
                        m_bleFace = new BLEFace(m_SignOnBasicControllerBLE.getMacAddressOfDevice(deviceIdentifierHexString),
                                onInterest);
                    }
                    else if(deviceIdentifierHexString.equals(m_expectedDeviceIdentifierHexString2)) {
                        Log.i(TAG, "onDeviceSignOnComplete: create ble face for board 2");
                        m_bleFace2 = new BLEFace(m_SignOnBasicControllerBLE.getMacAddressOfDevice(deviceIdentifierHexString),
                                onInterest);
                    }
                    else
                        Log.i(TAG, "onDeviceSignOnComplete: wrong device identifier...");
                }

                @Override
                public void onDeviceSignOnError(String deviceIdentifierHexString,
                                                SignOnControllerResultCodes.SignOnControllerResultCode resultCode) {
                    if (deviceIdentifierHexString != null) {
                        Log.i(TAG, "Sign on error for device with device identifier hex string : " + deviceIdentifierHexString +
                                " and mac address " + m_SignOnBasicControllerBLE.getMacAddressOfDevice(deviceIdentifierHexString) + "\n" +
                                "SignOnControllerResultCode: " + resultCode);
                    }
                    else {
                        Log.w(TAG, "Sign on error for unknown device." + "\n" +
                                "SignOnControllerResultCode: " + resultCode);
                    }
                }
            };


    private ServiceConnection uiServiceConnection=new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            uiUpdateBinder=(MainUIUpdateService.UIUpdateBinder)service;
            uiUpdateBinder.startToUPdate();
            uiUpdateBinder.endToUpdate();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    public void callbackData(Data data) {
        Log.i(TAG, "callbackData: get callback data here");
        //According to different data to do different corresponse
    }

    public class UIUpdateTask extends AsyncTask {
        ImageView phonePic=(ImageView) findViewById(R.id.nexus);
        EditText phoneText=(EditText) findViewById(R.id.phoneInfo);

        ImageView devicePic=(ImageView) findViewById(R.id.board);
        EditText deviceText=(EditText) findViewById(R.id.boardInfo);

        ImageView arrow=(ImageView) findViewById(R.id.arrow_vertical);

        ImageView devicePic2=(ImageView) findViewById(R.id.board2);
        EditText deviceText2=(EditText) findViewById(R.id.boardInfo2);

        ImageView arrow2=(ImageView) findViewById(R.id.arrow_vertical2);

        Button switchButton1=(Button)findViewById(R.id.switch1);
        Button switchButton2=(Button)findViewById(R.id.switch2);
        @Override
        protected Object doInBackground(Object[] objects) {
            Log.d(TAG, "doInBackground: UIUpdateTask");
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            //Toast.makeText(MainActivity.this,"Finishing scan",Toast.LENGTH_SHORT ).show();
            super.onPostExecute(o);
        }

        @Override
        protected void onProgressUpdate(Object[] values) {
            Log.d(TAG, "doInBackground: UIUpdateTask");
            super.onProgressUpdate(values);
        }

        @Override

        protected void onPreExecute(){
            Log.d(TAG, "onPreExecute: UIUpdateTask");
            //set visible of pic;
            if(signOnCount==1) {
                phonePic.setVisibility(View.VISIBLE);
                phoneText.setVisibility(View.VISIBLE);

                devicePic.setVisibility(View.VISIBLE);
                deviceText.setVisibility(View.VISIBLE);
                arrow.setVisibility(View.VISIBLE);
                switchButton1.setVisibility(View.VISIBLE);
            }
            if(signOnCount>1) {
                devicePic.setVisibility(View.VISIBLE);
                deviceText.setVisibility(View.VISIBLE);
                arrow.setVisibility(View.VISIBLE);
                switchButton1.setVisibility(View.VISIBLE);

                devicePic2.setVisibility(View.VISIBLE);
                deviceText2.setVisibility(View.VISIBLE);
                arrow2.setVisibility(View.VISIBLE);
                switchButton2.setVisibility(View.VISIBLE);
            }


        }

    }


//    public class signOnTask extends AsyncTask {
//        @Override
//        protected Object doInBackground(Object[] objects) {
//            Log.d(TAG, "doInBackground: signOnTask");
//            NDNLiteSupportInit.NDNLiteSupportInit();
//
//            CertificateV2 trustAnchorCertificate = new CertificateV2();
//
//// initializing the BLEUnicastConnectionMaintainer
//            // (YOU MUST DO THIS FOR SecureSignOnControllerBLE AND BLEFace TO FUNCTION AT ALL)
//            m_BLEUnicastConnectionMaintainer = BLEUnicastConnectionMaintainer.getInstance();
//            m_BLEUnicastConnectionMaintainer.initialize(MainActivity.this);
//
//            // initializing the SignOnControllerBLE
//            m_SignOnBasicControllerBLE = SignOnBasicControllerBLE.getInstance();
//            m_SignOnBasicControllerBLE.initialize(SIGN_ON_VARIANT_BASIC_ECC_256,
//                    m_secureSignOnBasicControllerBLECallbacks, trustAnchorCertificate);
//
//            // Creating a certificate from the device's preknown KS key pair public key.
//            CertificateV2 KSpubCertificateDevice1 = new CertificateV2();
//            try {
//                KSpubCertificateDevice1.setContent(
//                        new Blob(asnEncodeRawECPublicKeyBytes(BOOTSTRAP_ECC_PUBLIC_NO_POINT_IDENTIFIER))
//                );
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//
//            // Adding the device to the SignOnControllerBLE's list of devices pending onboarding; if
//            // this is not done, the SignOnControllerBLE would ignore bootstrapping requests from the
//            // device.
//            m_SignOnBasicControllerBLE.addDevicePendingSignOn(KSpubCertificateDevice1, DEVICE_IDENTIFIER,
//                    SECURE_SIGN_ON_CODE);
//
//            return null;
//        }
//
//        @Override
//        protected void onPostExecute(Object o) {
//            //Toast.makeText(MainActivity.this,"Finishing scan",Toast.LENGTH_SHORT ).show();
//            super.onPostExecute(o);
//        }
//
//        @Override
//        protected void onProgressUpdate(Object[] values) {
//            Log.d(TAG, "doInBackground: signOnTask");
//            super.onProgressUpdate(values);
//        }
//
//        @Override
//
//        protected void onPreExecute(){
//            Log.d(TAG, "onPreExecute: signOnTask");
//        }
//
//    }

    public class SendInterestTaskV3 extends AsyncTask <Name,Integer,Boolean> {
        //private final String TAG="SendInterestTask";
        // Face face=new Face();
        Data comeBackData=new Data();
        @Override
        protected void onPreExecute(){
            Log.i(TAG, "onPreExecute:  execute sending interest success!");
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            Log.i(TAG, "onPostExecute:  execute sending interest success!");
            super.onPostExecute(aBoolean);
        }

        @Override
        protected Boolean doInBackground(Name... PendingName) {
            Log.i(TAG, "doInBackground of SIV3: get into sending interest do in background");
            incomingData incomD = new incomingData();
            //String tempName=new Name(PendingName);
            Interest pendingInterest=new Interest(PendingName[0]);
//        pendingInterest.setName(PendingName);
            try{
                m_bleFace2.expressInterest(pendingInterest,incomD);
                m_bleFace2.processEvents();

                // We need to sleep for a few milliseconds so we don't use
                // 100% of

                // the CPU.

                Thread.sleep(50);
            }catch (Exception e){
                e.printStackTrace();
            }
            return false;
        }



        //Implementation of Ondata, OnTimeout
        private class incomingData implements OnData{
//        @Override
//        public void onNetworkNack(Interest interest, NetworkNack networkNack) {
//            Log.i(TAG, "networkNack for interest:" + interest.getName().toUri());
//            Log.i(TAG, "networkNack:" + networkNack.toString());
//        }
//
//        @Override
//        public void onTimeout(Interest interest) {
//            Log.i(TAG, "Time out for interest:" + interest.getName().toUri());
//        }

            @Override
            public void onData(Interest interest, Data data) {
                Log.i(TAG, "Got data packet with name:" + data.getName().toUri());
                String msg = data.getContent().toString();
                Log.i(TAG, "onData: " + msg);
                if (msg.length() == 0) {
                    Log.i(TAG, "Data is null");
                } else if (msg.length() > 0) {
                    comeBackData.setContent(data.getContent());
                }
            }
        }
    }


    public class SendInterestTaskV2 extends AsyncTask <Name,Integer,Boolean> {
        //private final String TAG="SendInterestTask";
        // Face face=new Face();
        Data comeBackData=new Data();
        @Override
        protected void onPreExecute(){
            Log.i(TAG, "onPreExecute:  execute sending interest success!");
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            Log.i(TAG, "onPostExecute:  execute sending interest success!");
            super.onPostExecute(aBoolean);
        }

        @Override
        protected Boolean doInBackground(Name... PendingName) {
            Log.i(TAG, "doInBackground of SIV2: get into sending interest do in background");
             incomingData incomD = new incomingData();
            //String tempName=new Name(PendingName);
            Interest pendingInterest=new Interest(PendingName[0]);
//        pendingInterest.setName(PendingName);
            try{
                m_bleFace.expressInterest(pendingInterest,incomD);
                m_bleFace.processEvents();

                // We need to sleep for a few milliseconds so we don't use
                // 100% of

                // the CPU.

                Thread.sleep(50);
            }catch (Exception e){
                e.printStackTrace();
            }
            return false;
        }



        //Implementation of Ondata, OnTimeout
        private class incomingData implements OnData{
//        @Override
//        public void onNetworkNack(Interest interest, NetworkNack networkNack) {
//            Log.i(TAG, "networkNack for interest:" + interest.getName().toUri());
//            Log.i(TAG, "networkNack:" + networkNack.toString());
//        }
//
//        @Override
//        public void onTimeout(Interest interest) {
//            Log.i(TAG, "Time out for interest:" + interest.getName().toUri());
//        }

            @Override
            public void onData(Interest interest, Data data) {
                Log.i(TAG, "Got data packet with name:" + data.getName().toUri());
                String msg = data.getContent().toString();
                Log.i(TAG, "onData: " + msg);
                if (msg.length() == 0) {
                    Log.i(TAG, "Data is null");
                } else if (msg.length() > 0) {
                    comeBackData.setContent(data.getContent());
                }
            }
        }
    }




    private void initializeUI() {

        m_log = (TextView) findViewById(R.id.boardInfo);

    }

    private void logMessageUI(final String TAG, final String msg) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                m_log.append(TAG + ":" + "\n");
                m_log.append(msg + "\n");
                m_log.append("------------------------------" + "\n");
            }
        });
    }

    private void logMessage(String TAG, String msg) {
        Log.d(TAG, msg);
        logMessageUI(TAG, msg);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        initializeUI();

        NDNLiteSupportInit.NDNLiteSupportInit();

        CertificateV2 trustAnchorCertificate = new CertificateV2();

// initializing the BLEUnicastConnectionMaintainer
        // (YOU MUST DO THIS FOR SecureSignOnControllerBLE AND BLEFace TO FUNCTION AT ALL)
        m_BLEUnicastConnectionMaintainer = BLEUnicastConnectionMaintainer.getInstance();
        m_BLEUnicastConnectionMaintainer.initialize(this);

        // initializing the SignOnControllerBLE
        m_SignOnBasicControllerBLE = SignOnBasicControllerBLE.getInstance();
        m_SignOnBasicControllerBLE.initialize(SIGN_ON_VARIANT_BASIC_ECC_256,
                m_secureSignOnBasicControllerBLECallbacks, trustAnchorCertificate);

        // Creating a certificate from the device's preknown KS key pair public key.
        CertificateV2 KSpubCertificateDevice1 = new CertificateV2();
        try {
            KSpubCertificateDevice1.setContent(
                    new Blob(asnEncodeRawECPublicKeyBytes(BOOTSTRAP_ECC_PUBLIC_NO_POINT_IDENTIFIER))
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Adding the device to the SignOnControllerBLE's list of devices pending onboarding; if
        // this is not done, the SignOnControllerBLE would ignore bootstrapping requests from the
        // device.
        m_SignOnBasicControllerBLE.addDevicePendingSignOn(KSpubCertificateDevice1, DEVICE_IDENTIFIER,
                SECURE_SIGN_ON_CODE);
        m_SignOnBasicControllerBLE.addDevicePendingSignOn(KSpubCertificateDevice1, DEVICE_IDENTIFIER_2,
                SECURE_SIGN_ON_CODE);







        ImageView phonePic=(ImageView) findViewById(R.id.nexus);
        EditText phoneText=(EditText) findViewById(R.id.phoneInfo);

        ImageView devicePic=(ImageView) findViewById(R.id.board);
        EditText deviceText=(EditText) findViewById(R.id.boardInfo);

        ImageView arrow=(ImageView) findViewById(R.id.arrow_vertical);

        ImageView devicePic2=(ImageView) findViewById(R.id.board2);
        EditText deviceTex2=(EditText) findViewById(R.id.boardInfo2);

        ImageView arrow2=(ImageView) findViewById(R.id.arrow_vertical2);

        Button optionButton=(Button) findViewById(R.id.option_button);

        Switch switchButton1=(Switch)findViewById(R.id.switch1);

        Name bootstrapInterest= new Name("/NDN-IoT/boostrap");
        Name commandInterest= new Name("/NDN-IoT/TrustChange");





        switchButton1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    //bootstrap operation can be here
                    Log.d(TAG, "onClick: switchButtton1...");
                    Toast.makeText(MainActivity.this,"Turn on the device",Toast.LENGTH_SHORT).show();
                    switchButton1.setText("ON");
                }
                else
                    //shut down operation can be here
                    switchButton1.setText("OFF");
                Toast.makeText(MainActivity.this,"Turn off the device",Toast.LENGTH_SHORT).show();
            }
        });

        Switch switchButton2=(Switch)findViewById(R.id.switch2);
        switchButton2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    //bootstrap operation can be here
                    Log.d(TAG, "onClick: switchButtton2...");
                    Toast.makeText(MainActivity.this,"Turn on the device",Toast.LENGTH_SHORT).show();
                    switchButton2.setText("ON");
                }
                else
                    //shut down operation can be here
                    switchButton2.setText("OFF");
                Toast.makeText(MainActivity.this,"Turn off the device",Toast.LENGTH_SHORT).show();
            }
        });

        optionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float arrowLocationX=arrow.getTranslationX();
                float arrowLocationY=arrow.getTranslationY();
                float arrow2LocationX=arrow2.getTranslationX();
                float arrow2LocationY=arrow2.getTranslationY();
                Log.d(TAG, "arrowLocationX:"+arrowLocationX+"; arrowLocationY:"+arrowLocationY);
                //创建弹出式菜单对象（最低版本11）
                PopupMenu popup = new PopupMenu(MainActivity.this, v);//第二个参数是绑定的那个view
                //获取菜单填充器
                MenuInflater inflater = popup.getMenuInflater();
                //填充菜单
                inflater.inflate(R.menu.controller_option, popup.getMenu());
                //绑定菜单项的点击事件
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.ping_b1:
                                AlertDialog.Builder pingDialog=new AlertDialog.Builder(MainActivity.this);
                                pingDialog.setTitle("Ping/Bootstrap B_1");
                                pingDialog.setMessage("Ping/Bootstrap the device?");
                                pingDialog.setCancelable(false);
                                pingDialog.setPositiveButton("Bootstrap", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Toast.makeText(MainActivity.this, "Start to bootstrap B_1", Toast.LENGTH_SHORT).show();
                                        //send bootstrap Interest here
                                        Name bootstrapInterestB1=new Name(bootstrapInterest);
                                        bootstrapInterestB1.append("/Board1");
                                        SendInterestTaskV2 SITask=new SendInterestTaskV2();
                                        SITask.execute(bootstrapInterestB1);
                                        //new SendInterestTaskV2(MainActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,bootstrapInterestB1);

                                        Log.d(TAG, "onClick: start animation of arrow");
                                        ObjectAnimator animator = ObjectAnimator.ofFloat(arrow, "translationY",arrowLocationY,arrowLocationY+140);

// ofFloat()作用有两个
// 1. 创建动画实例
// 2. 参数设置：参数说明如下
// Object object：需要操作的对象
// String property：需要操作的对象的属性
// float ....values：动画初始值 & 结束值（不固定长度）
// 若是两个参数a,b，则动画效果则是从属性的a值到b值
// 若是三个参数a,b,c，则则动画效果则是从属性的a值到b值再到c值
// 以此类推
// 至于如何从初始值 过渡到 结束值，同样是由估值器决定，此处ObjectAnimator.ofFloat（）是有系统内置的浮点型估值器FloatEvaluator，同ValueAnimator讲解

                                        animator.setDuration(4000);
                                        // 设置动画运行的时长

                                        //animator.setStartDelay(500);
                                        // 设置动画延迟播放时间

                                        animator.setRepeatCount(2);
                                        // 设置动画重复播放次数 = 重放次数+1
                                        // 动画播放次数 = infinite时,动画无限重复

                                        animator.setRepeatMode(ValueAnimator.RESTART);
                                        // 设置重复播放动画模式
                                        // ValueAnimator.RESTART(默认):正序重放
                                        // ValueAnimator.REVERSE:倒序回放
                                        animator.addListener(new Animator.AnimatorListener() {
                                            @Override
                                            public void onAnimationStart(Animator animation) {

                                            }

                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                arrow.setTranslationX(arrowLocationX);
                                                arrow.setTranslationY(arrowLocationY);
                                                Toast.makeText(MainActivity.this,"finish bootstrap actuator_1",Toast.LENGTH_SHORT).show();

                                            }

                                            @Override
                                            public void onAnimationCancel(Animator animation) {

                                            }

                                            @Override
                                            public void onAnimationRepeat(Animator animation) {

                                            }
                                        });
                                        animator.start();
                                        // 启动动画

                                        Log.d(TAG, "onClick: finish animation");
                                    }
                                });
                                pingDialog.setNegativeButton("Ping", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Toast.makeText(MainActivity.this, "Start to ping B_1", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                pingDialog.show();
                                break;
                            case R.id.ping_b2:
                                AlertDialog.Builder pingDialog2=new AlertDialog.Builder(MainActivity.this);
                                pingDialog2.setTitle("Ping/Bootstrap B_2");
                                pingDialog2.setMessage("Ping/Bootstrap the device?");
                                pingDialog2.setCancelable(false);
                                pingDialog2.setPositiveButton("Bootstrap", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Toast.makeText(MainActivity.this, "Start to bootstrap B_2", Toast.LENGTH_SHORT).show();
                                        Name bootstrapInterestB2=new Name(bootstrapInterest);
                                        bootstrapInterestB2.append("/Board2");
                                        SendInterestTaskV2 SITask=new SendInterestTaskV2();
                                        SITask.execute(bootstrapInterestB2);
                                        //new SendInterestTaskV2(MainActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,bootstrapInterestB2);
                                        Log.d(TAG, "onClick: start animation of arrow2");
                                        ObjectAnimator animator = ObjectAnimator.ofFloat(arrow2, "translationY",arrowLocationY,arrow2LocationY+140);

// ofFloat()作用有两个
// 1. 创建动画实例
// 2. 参数设置：参数说明如下
// Object object：需要操作的对象
// String property：需要操作的对象的属性
// float ....values：动画初始值 & 结束值（不固定长度）
// 若是两个参数a,b，则动画效果则是从属性的a值到b值
// 若是三个参数a,b,c，则则动画效果则是从属性的a值到b值再到c值
// 以此类推
// 至于如何从初始值 过渡到 结束值，同样是由估值器决定，此处ObjectAnimator.ofFloat（）是有系统内置的浮点型估值器FloatEvaluator，同ValueAnimator讲解

                                        animator.setDuration(4000);
                                        // 设置动画运行的时长

                                        //animator.setStartDelay(500);
                                        // 设置动画延迟播放时间

                                        animator.setRepeatCount(2);
                                        // 设置动画重复播放次数 = 重放次数+1
                                        // 动画播放次数 = infinite时,动画无限重复

                                        animator.setRepeatMode(ValueAnimator.RESTART);
                                        // 设置重复播放动画模式
                                        // ValueAnimator.RESTART(默认):正序重放
                                        // ValueAnimator.REVERSE:倒序回放
                                        animator.addListener(new Animator.AnimatorListener() {
                                            @Override
                                            public void onAnimationStart(Animator animation) {

                                            }

                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                arrow2.setTranslationX(arrow2LocationX);
                                                arrow2.setTranslationY(arrow2LocationY);
                                                Toast.makeText(MainActivity.this,"finish bootstrap actuator_1",Toast.LENGTH_SHORT).show();

                                            }

                                            @Override
                                            public void onAnimationCancel(Animator animation) {

                                            }

                                            @Override
                                            public void onAnimationRepeat(Animator animation) {

                                            }
                                        });
                                        animator.start();
                                        // 启动动画

                                        Log.d(TAG, "onClick: finish animation");
                                    }
                                });
                                pingDialog2.setNegativeButton("Ping", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Toast.makeText(MainActivity.this, "Start to ping B_2", Toast.LENGTH_SHORT).show();
                                        }
                                });
                                pingDialog2.show();
                                break;
                            case R.id.system_info:
                                Toast.makeText(MainActivity.this, "Three nodes", Toast.LENGTH_SHORT).show();
                                break;
                            default:
                                break;
                        }
                        return false;
                    }
                });
                //显示(这一行代码不要忘记了)
                popup.show();
            }
        });





        DisplayMetrics dm = getResources().getDisplayMetrics();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        devicePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //创建弹出式菜单对象（最低版本11）
                PopupMenu popup = new PopupMenu(MainActivity.this, v);//第二个参数是绑定的那个view
                //获取菜单填充器
                MenuInflater inflater = popup.getMenuInflater();
                //填充菜单
                inflater.inflate(R.menu.board_option, popup.getMenu());
                //绑定菜单项的点击事件
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                                                     @Override
                                                     public boolean onMenuItemClick(MenuItem item) {
                                                         switch (item.getItemId()) {
                                                             case R.id.authority_controller:
                                                                 Name commandInterestB1_1=new Name("/NDN-IoT/TrustChange/Board1/ControllerOnly");
                                                                 SendInterestTaskV2 SITask=new SendInterestTaskV2();
                                                                 Log.i(TAG, "onMenuItemClick: constructed name is:"+commandInterestB1_1.toString());
                                                                 SITask.execute(commandInterestB1_1);
                                                                 //new SendInterestTaskV2(MainActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,commandInterestB1_1);
                                                                 Toast.makeText(MainActivity.this,"Start to change the authority to controller",Toast.LENGTH_LONG).show();
                                                                 break;
                                                             case R.id.authority_all:
                                                                 Name commandInterestB1_2=new Name("/NDN-IoT/TrustChange/Board1/AllNode");
                                                                 SendInterestTaskV2 SITask2=new SendInterestTaskV2();
                                                                 Log.i(TAG, "onMenuItemClick: constructed name is:"+commandInterestB1_2.toString());
                                                                 SITask2.execute(commandInterestB1_2);
                                                                 //new SendInterestTaskV2(MainActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,commandInterestB1_1);
                                                                 Toast.makeText(MainActivity.this,"Start to change the authority to all node",Toast.LENGTH_LONG).show();
                                                                 break;
                                                             default:
                                                                 break;
                                                         }
                                                         return false;
                                                     }
                                                 });
                popup.show();

//                AlertDialog.Builder deviceDialog=new AlertDialog.Builder(MainActivity.this);
//                deviceDialog.setTitle("nRF52840");
//                deviceDialog.setMessage("Reset the device?");
//                deviceDialog.setCancelable(false);
//                deviceDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        //start to bootstrap the device here
//                        Toast.makeText(MainActivity.this,"Start to change the authority",Toast.LENGTH_LONG).show();
//                        }
//                });
//                deviceDialog.setNegativeButton("NO", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                   //done nothing here
//                    }
//                });
//                deviceDialog.show();
            }
        });
        devicePic2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //创建弹出式菜单对象（最低版本11）
                PopupMenu popup = new PopupMenu(MainActivity.this, v);//第二个参数是绑定的那个view
                //获取菜单填充器
                MenuInflater inflater = popup.getMenuInflater();
                //填充菜单
                inflater.inflate(R.menu.board_option, popup.getMenu());
                //绑定菜单项的点击事件
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.authority_controller:
                                Name commandInterestB2_1=new Name("/NDN-IoT/TrustChange/Board2/ControllerOnly");
                                SendInterestTaskV3 SITask=new SendInterestTaskV3();
                                Log.i(TAG, "onMenuItemClick: constructed name is:"+commandInterestB2_1.toString());
                                SITask.execute(commandInterestB2_1);
                                //new SendInterestTaskV2(MainActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,commandInterestB2_1);
                                Toast.makeText(MainActivity.this,"Start to change the authority to controller",Toast.LENGTH_LONG).show();
                                break;
                            case R.id.authority_all:
                                Name commandInterestB2_2=new Name("/NDN-IoT/TrustChange/Board2/AllNode");
                                SendInterestTaskV3 SITask2=new SendInterestTaskV3();
                                Log.i(TAG, "onMenuItemClick: constructed name is:"+commandInterestB2_2.toString());
                                SITask2.execute(commandInterestB2_2);
                                //new SendInterestTaskV2(MainActivity.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,commandInterestB1_2);
                                Toast.makeText(MainActivity.this,"Start to change the authority to all node",Toast.LENGTH_LONG).show();
                                break;
                            default:
                                break;
                        }
                        return false;
                    }
                });
                popup.show();


//                AlertDialog.Builder deviceDialog=new AlertDialog.Builder(MainActivity.this);
//                deviceDialog.setTitle("nRF52840");
//                deviceDialog.setMessage("Reset the device?");
//                deviceDialog.setCancelable(false);
//                deviceDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        //start to bootstrap the device here
//                        Toast.makeText(MainActivity.this,"Start to change the authority",Toast.LENGTH_LONG).show();
//
//
//                    }
//                });
//                deviceDialog.setNegativeButton("NO", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        //done nothing here
//                    }
//                });
//                deviceDialog.show();
            }
        });




//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings&&signOnFlag==1) {
            //start to show pic when receive Interest...
            Log.i(TAG, "Ready to show the pic in the map: ");
//            Intent mainUIUpdateService= new Intent(MainActivity.this,MainUIUpdateService.class);
//            bindService(mainUIUpdateService,uiServiceConnection,BIND_AUTO_CREATE);
            new UIUpdateTask().execute();
//            unbindService(uiServiceConnection);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            Log.d(TAG, "onNavigationItemSelected: BLIntent Start...");
            Intent BLIntent=new Intent(MainActivity.this,BLMainActivity.class);
            startActivity(BLIntent,ActivityOptions.makeSceneTransitionAnimation(this).toBundle());
            //
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
