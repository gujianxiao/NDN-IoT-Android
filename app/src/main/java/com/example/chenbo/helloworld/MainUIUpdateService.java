
/*
 * Copyright (C) 2018-2019 Bo Chen
 *
 * This file is subject to the terms and conditions of the GNU Lesser
 * General Public License v2.1. See the file LICENSE in the top level
 * directory for more details.
 */

package com.example.chenbo.helloworld;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class MainUIUpdateService extends Service {
    private String TAG="MainUIUpdateService";
    public MainUIUpdateService() {
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate: MainUIUpdateService...");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: MainUIUpdateService...");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: MainUIUpdateService...");
        super.onDestroy();
    }
    private UIUpdateBinder uiUpdateBinder=new UIUpdateBinder();

    class UIUpdateBinder extends Binder{
        public void startToUPdate(){
            Log.d(TAG, "startToUpdate: ");
        }
        public void endToUpdate(){
            Log.d(TAG, "endToUpdate: ");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return uiUpdateBinder;
      //  throw new UnsupportedOperationException("Not yet implemented");
    }

}
