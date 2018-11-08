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
            //set visible of pic;

            phonePic.setVisibility(View.VISIBLE);
            phoneText.setVisibility(View.VISIBLE);

            devicePic.setVisibility(View.VISIBLE);
            deviceText.setVisibility(View.VISIBLE);
            arrow.setVisibility(View.VISIBLE);
            devicePic2.setVisibility(View.VISIBLE);
            deviceText2.setVisibility(View.VISIBLE);
            arrow2.setVisibility(View.VISIBLE);

            switchButton1.setVisibility(View.VISIBLE);
            switchButton2.setVisibility(View.VISIBLE);


        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
                                        Toast.makeText(MainActivity.this, "Start to bootstrap B_1", Toast.LENGTH_SHORT).show();
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
                                        Toast.makeText(MainActivity.this, "Start to bootstrap B_2", Toast.LENGTH_SHORT).show();
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
                AlertDialog.Builder deviceDialog=new AlertDialog.Builder(MainActivity.this);
                deviceDialog.setTitle("nRF52840");
                deviceDialog.setMessage("Reset the device?");
                deviceDialog.setCancelable(false);
                deviceDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //start to bootstrap the device here
                        Toast.makeText(MainActivity.this,"Start to reset the device",Toast.LENGTH_LONG).show();
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
