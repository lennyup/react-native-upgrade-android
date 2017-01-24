package com.lenny.modules.upgrade;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by lenny on 2017/1/1.
 */

public class UpgradeModule extends ReactContextBaseJavaModule {
    private static  final  String RECEIVER_ACTION = "com.android.upgrade";
    private final int SHOWDOWNLOADDIALOG = 88;
    private final int UPDATEDOWNLOADDIALOG = 99;
    private final int DOWNLOADFINISHED = 66;
    private final int DOWNLOADFAIL = 77;
    private int contentLength;//要下载文件的大小
    private  ProgressDataReceiver progressReceiver;
    private Handler handler = new Handler(Looper.getMainLooper()){

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOWDOWNLOADDIALOG://显示正在下载的对话框
                    sendReceiver("0000", "0", contentLength);
                    break;

                case UPDATEDOWNLOADDIALOG://刷新正在下载对话框的内容
                    sendReceiver("0001", msg.obj.toString(), contentLength);
                    break;
                case DOWNLOADFINISHED://下载完成后进行的操作
                    sendReceiver("0002", contentLength + "", contentLength);
                    showToast("下载成功，跳转安装...");
                    InstallAPK((String) msg.obj);
                    break;
                case DOWNLOADFAIL:
                    showToast("访问服务器失败");
                default:
                    break;
            }
        };
    };

    public UpgradeModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "UpgradeModule";
    }
    @ReactMethod
    public void init() {
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(RECEIVER_ACTION);
        mIntentFilter.setPriority(500);
        progressReceiver = new ProgressDataReceiver();
        getReactApplicationContext().registerReceiver(progressReceiver, mIntentFilter);
    }
    @ReactMethod
    public void cancelDownLoad() {
        getReactApplicationContext().unregisterReceiver(progressReceiver); // 取消广播
        handler.removeCallbacksAndMessages(null);
    }

    @ReactMethod
    public void startDownLoad(final String downloadUrl,String version, String fileName) {
        if (!isSDcardExist()) {
            showToast("SD卡不存在，下载失败");
            return;
        }
//        final String downloadUrl = "http://www.online-cmcc.com/gfms/app/apk/4GTraffic2MM.apk";
        final String filePath = getDownloadPath() + File.separator +fileName+".apk";
        if (fileIsExists(filePath) && isLastVersion(filePath, version)) {
            InstallAPK(filePath);
            return;
        }
        showToast("下载开始");
        new Thread(){
            private InputStream inputStream;
            private FileOutputStream fos;
            public void run() {
//                Looper.prepare();
                try {
                    URL url = new URL(downloadUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(30000);
                    //http请求不要gzip压缩，否则获取的文件大小可以小于文件的实际大小
                    conn .setRequestProperty("Accept-Encoding", "identity");
                    int responseCode = conn.getResponseCode();
                    if(responseCode == 200){
                        inputStream = conn.getInputStream();
                        File file = new File(filePath);
                        fos = new FileOutputStream(file);
                        contentLength = conn.getContentLength();
                        System.out.println("文件的大小：：" + contentLength);
                        int fileLengthFromHeader = Integer.parseInt(conn.getHeaderField("Content-Length"));
                        System.out.println("根据头文件获取文件的大小：：" + fileLengthFromHeader);

                        //子线程不能显示和刷新UI
                        Message msg = Message.obtain();
                        msg.what = SHOWDOWNLOADDIALOG;
                        handler.sendMessage(msg);

                        byte[] buffer = new byte[1024];
                        int len = 0;
                        int count = 0;
                        while(((len = inputStream.read(buffer)) != -1)){
                            fos.write(buffer, 0, len);
                            ++count;
                            if (count % 10 == 0) { //减少发送次数
                                int curlength = (int) file.length();
                                Message updateMsg = Message.obtain();
                                updateMsg.what = UPDATEDOWNLOADDIALOG;
                                updateMsg.obj = curlength;
                                handler.sendMessage(updateMsg);
                                System.out.println("file.length()::" + curlength);
                            }
                        }

                        if(file.length() == contentLength){
                            //下载完成
                            Message finishedMsg = Message.obtain();
                            finishedMsg.what = DOWNLOADFINISHED;
                            finishedMsg.obj = filePath;
                            handler.sendMessage(finishedMsg);
                        }
                    }else{
                        Message msg = Message.obtain();
                        msg.what = DOWNLOADFAIL;
                        handler.sendMessage(msg);
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    System.out.println("MalformedURLException:" + e.getMessage());
                } catch (IOException e2) {
                    e2.printStackTrace();
                    System.out.println("IOException:" + e2.getMessage());
                }finally{
                    try {
                        if(inputStream != null){
                            inputStream.close();
                        }

                        if(fos != null){
                            fos.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.out.println("IOException:" + e.getMessage());
                    }
                }
            };
        }.start();
    }
    private void showToast(String info) {
        Toast.makeText(getReactApplicationContext(), info, Toast.LENGTH_SHORT).show();
    }
    private String getDownloadPath(){
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    private boolean isSDcardExist(){
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    private boolean fileIsExists(String strFile) {
        try
        {
            File f=new File(strFile);
            if(!f.exists())
            {
                return false;
            }

        }
        catch (Exception e)
        {
            return false;
        }

        return true;
    }

    private boolean isLastVersion(String filePath, String version) {
        PackageManager pm = getReactApplicationContext().getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(filePath, PackageManager.GET_ACTIVITIES);
        if (info != null) {
            int code = info.versionCode;
            String appVersion = info.versionName;
            try {
                int appCode = pm.getPackageInfo(getReactApplicationContext().getPackageName(), 0).versionCode;
                if (code > appCode && appVersion.equals(version)) {
                    return  true;
                }
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
        return  false;
    }

    private void InstallAPK(String filePath){
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setDataAndType(Uri.parse("file://" + filePath),"application/vnd.android.package-archive");
        getReactApplicationContext().startActivity(i);
        cancelDownLoad();
        System.exit(0);
    }

    private  void sendReceiver(String code, String currLength, Integer fileLength) {
        Intent intent = new Intent(RECEIVER_ACTION);
        intent.putExtra("code", code);
        intent.putExtra("currLength", currLength);
        intent.putExtra("fileLength", fileLength);
        getReactApplicationContext().sendBroadcast(intent);
    }

    private class ProgressDataReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (RECEIVER_ACTION.equals(action)) {
                WritableMap params = Arguments.createMap();
                params.putString("code", intent.getStringExtra("code"));
                params.putString("downSize", intent.getStringExtra("currLength"));
                params.putInt("fileSize", intent.getIntExtra("fileLength", 0));
                getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("progress", params);;
            }
        }
    }
}