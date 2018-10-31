package com.example.chenbo.helloworld;

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
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private String TAG="MainActivity";

    private MainUIUpdateService.UIUpdateBinder uiUpdateBinder;


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
    public class UIUpdateTask extends AsyncTask {
        ImageView devicePic=(ImageView) findViewById(R.id.board);
        ImageView phonePic=(ImageView) findViewById(R.id.nexus);
        EditText phoneText=(EditText) findViewById(R.id.phoneInfo);
        EditText deviceText=(EditText) findViewById(R.id.boardInfo);
        ImageView arrow=(ImageView) findViewById(R.id.arrow_right);
        @Override
        protected Object doInBackground(Object[] objects) {
            Log.d(TAG, "doInBackground: UIUpdateTask");
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            Toast.makeText(MainActivity.this,"Finishing scan",Toast.LENGTH_SHORT ).show();
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
            devicePic.setVisibility(View.VISIBLE);
            phonePic.setVisibility(View.VISIBLE);
            deviceText.setVisibility(View.VISIBLE);
            phoneText.setVisibility(View.VISIBLE);
            arrow.setVisibility(View.VISIBLE);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView devicePic=(ImageView) findViewById(R.id.board);
        ImageView phonePic=(ImageView) findViewById(R.id.nexus);
        EditText phoneText=(EditText) findViewById(R.id.phoneInfo);
        EditText deviceText=(EditText) findViewById(R.id.boardInfo);
        ImageView arrow=(ImageView) findViewById(R.id.arrow_right);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        devicePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder deviceDialog=new AlertDialog.Builder(MainActivity.this);
                deviceDialog.setTitle("nRF52840");
                deviceDialog.setMessage("Bootstrap the device?");
                deviceDialog.setCancelable(false);
                deviceDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //start to bootstrap the device here
                        Toast.makeText(MainActivity.this,"Start to bootstrap the device",Toast.LENGTH_LONG).show();
                    }
                });
                deviceDialog.setNegativeButton("NO", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                   //done nothing here
                    }
                });
                deviceDialog.show();
            }
        });




        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

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
        if (id == R.id.action_settings) {
            Intent mainUIUpdateService= new Intent(MainActivity.this,MainUIUpdateService.class);
            bindService(mainUIUpdateService,uiServiceConnection,BIND_AUTO_CREATE);
            new UIUpdateTask().execute();
            unbindService(uiServiceConnection);
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
            startActivity(BLIntent);
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
