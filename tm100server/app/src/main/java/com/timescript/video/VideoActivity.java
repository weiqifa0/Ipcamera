package com.timescript.video;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import com.timescript.tm100server.R;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class VideoActivity extends Activity
        implements CameraView.CameraReadyCallback {
    public static String TAG="TIMECam";
    private final int ServerPort = 8080;
    private final int StreamingPort = 8088;
    private final int PictureWidth = 320;//176;
    private final int PictureHeight = 240;//144;
    private final int MediaBlockNumber = 2;//3;
    private final int MediaBlockSize = 1024*512;//1024*512;
    private final int EstimatedFrameNumber = 1;//3;
    private final int StreamingInterval = 40;//100;

    private StreamingServer streamingServer = null;
    private TeaServer webServer = null;
    private CameraView cameraView = null;
    private WebView webView;

    ExecutorService executor = Executors.newFixedThreadPool(3);
    VideoEncodingTask videoTask = new  VideoEncodingTask();
    private ReentrantLock previewLock = new ReentrantLock();
    boolean inProcessing = false;

    public static boolean connected = false;

    byte[] yuvFrame = new byte[1920*1280*2];

    MediaBlock[] mediaBlocks = new MediaBlock[MediaBlockNumber];
    int mediaWriteIndex = 0;
    int mediaReadIndex = 0;

    Handler streamingHandler;


    //
    //  Activiity's event handler
    //
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        // application setting
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // load and setup GUI
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video);
        //setContentView(R.layout.video_test);
        //webView = (WebView) findViewById(R.id.webview);


        // init audio and camera
        for(int i = 0; i < MediaBlockNumber; i++) {
            mediaBlocks[i] = new MediaBlock(MediaBlockSize);
        }
        resetMediaBuffer();

        if ( initWebServer() ) {
            //initAudio();
            initCamera();
        } else {
            return;
        }

        try {
            streamingServer = new StreamingServer(StreamingPort);
            streamingServer.start();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        }

        streamingHandler = new Handler();
        streamingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                doStreaming();
            }
        }, StreamingInterval);

        //loadWebView();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onPause() {
        super.onPause();
        exit();

        //System.exit(0);
    }

    public void loadWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                // TODO Auto-generated method stub
                super.onShowCustomView(view, callback);
            }

        });
        webView.loadUrl("http://127.0.0.1:8080");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // TODO Auto-generated method stub
                // 返回值是true的时候控制去WebView打开，为false调用系统浏览器或第三方浏览器
                view.loadUrl(url);
                return true;
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        super.dispatchKeyEvent(event);
        if((event.getKeyCode() == KeyEvent.KEYCODE_V) && (event.getAction() == KeyEvent.ACTION_DOWN)) {
            exit();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    private void exit() {
        streamingHandler.removeCallbacks(runnable);
        if(streamingServer != null) {
            streamingServer.inStreaming = false;
            try {
                streamingServer.stop();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            streamingServer = null;
        }
        if ( webServer != null) {
            webServer.stop();
            webServer = null;
        }

        previewLock.lock();
        if ( cameraView != null) {
            cameraView.StopPreview();
            cameraView.Release();
            cameraView = null;
        }
        previewLock.unlock();

        finish();
    }

    //
    //  Interface implementation
    //
    public void onCameraReady() {
        cameraView.StopPreview();
        //cameraView.setupCamera(PictureWidth, PictureHeight, 4, 25.0, previewCb);
        cameraView.setupCamera(PictureWidth, PictureHeight, 2, 25.0, previewCb);

        nativeInitMediaEncoder(cameraView.Width(), cameraView.Height());

        cameraView.StartPreview();
    }

    //
    //  Internal help functions
    //
    private boolean initWebServer() {

        String ipAddr = wifiIpAddress(this);
        if ( ipAddr != null ) {
            try{
                webServer = new TeaServer(8080, this);
                webServer.registerCGI("/cgi/query", doQuery);
            }catch (IOException e){
                webServer = null;
            }
        }

        TextView tv = (TextView)findViewById(R.id.tv_message);
        if ( webServer != null) {
            return true;
        } else {
            if ( ipAddr == null) {
                tv.setText( getString(R.string.msg_wifi_error) );
            } else {
                tv.setText( getString(R.string.msg_port_error) );
            }
            return false;
        }
    }
    private void initCamera() {
        SurfaceView cameraSurface = (SurfaceView)findViewById(R.id.surface_camera);
        cameraView = new CameraView(cameraSurface);
        cameraView.setCameraReadyCallback(this);
    }

    private void resetMediaBuffer() {
        synchronized(VideoActivity.this) {
            for (int i = 1; i < MediaBlockNumber; i++) {
                mediaBlocks[i].reset();
            }
            mediaWriteIndex = 0;
            mediaReadIndex = 0;
        }
    }

    public Runnable runnable = new Runnable() {
        @Override
        public void run() {
            doStreaming();
        }
    };

    private void doStreaming () {
        synchronized(VideoActivity.this) {

            if(streamingServer.inStreaming == false) {
                Log.d(TAG, "inStreaming is false, stop stream");
                return;
            }
            MediaBlock targetBlock = mediaBlocks[mediaReadIndex];
            if ( targetBlock.flag == 1) {
                streamingServer.sendMedia( targetBlock.data(), targetBlock.length());
                targetBlock.reset();

                mediaReadIndex ++;
                if ( mediaReadIndex >= MediaBlockNumber) {
                    mediaReadIndex = 0;
                }
            }
        }

        streamingHandler.postDelayed(runnable, StreamingInterval);

    }

    protected String wifiIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e("WIFIIP", "Unable to get host address.");
            ipAddressString = null;
        }

        return ipAddressString;
    }

    //
    //  Internal help class and object definment
    //
    int cnt = 0;
    private PreviewCallback previewCb = new PreviewCallback() {
        public void onPreviewFrame(byte[] frame, Camera c) {
            previewLock.lock();
            doVideoEncode(frame);
            c.addCallbackBuffer(frame);
            previewLock.unlock();
        }
    };

    private void doVideoEncode(byte[] frame) {
        if ( (inProcessing == true) || (cameraView == null)) {
            return;
        }
        inProcessing = true;

        int picWidth = cameraView.Width();
        int picHeight = cameraView.Height();
        int size = picWidth*picHeight + picWidth*picHeight/2;
        System.arraycopy(frame, 0, yuvFrame, 0, size);

        executor.execute(videoTask);
    }


    private TeaServer.CommonGatewayInterface doQuery = new TeaServer.CommonGatewayInterface () {
        @Override
        public String run(Properties parms) {
            String ret = "";
            if ( streamingServer.inStreaming == true ) {
                ret = "{\"state\": \"busy\"}";
            } else {
                ret = "{\"state\": \"ok\",";
                ret = ret + "\"width\": \"" + cameraView.Width() + "\",";
                ret = ret + "\"height\": \"" + cameraView.Height() + "\"}";
            }
            return ret;
        }

        @Override
        public InputStream streaming(Properties parms) {
            return null;
        }
    };

    private class VideoEncodingTask implements Runnable {
        private byte[] resultNal = new byte[1024*1024];
        private byte[] videoHeader = new byte[8];

        public VideoEncodingTask() {
            videoHeader[0] = (byte) 0x19;
            videoHeader[1] = (byte) 0x79;
        }

        public void run() {
            if((streamingServer!=null) && (streamingServer.inStreaming == false)) {
                inProcessing = false;
                return;
            }
            MediaBlock currentBlock = mediaBlocks[ mediaWriteIndex ];
            if ( currentBlock.flag == 1) {
                inProcessing = false;
                return;
            }

            int intraFlag = 0;
            if ( currentBlock.videoCount == 0) {
                intraFlag = 1;
            }
            //int millis = (int)(System.currentTimeMillis() % 65535);
            int ret = nativeDoVideoEncode(yuvFrame, resultNal, intraFlag);
            if ( ret <= 0) {
                return;
            }

            // timestamp
            //videoHeader[2] = (byte)(millis & 0xFF);
            //videoHeader[3] = (byte)((millis>>8) & 0xFF);
            // length
            videoHeader[4] = (byte)(ret & 0xFF);
            videoHeader[5] = (byte)((ret>>8) & 0xFF);
            videoHeader[6] = (byte)((ret>>16) & 0xFF);
            videoHeader[7] = (byte)((ret>>24) & 0xFF);

            synchronized(VideoActivity.this) {
                if ( currentBlock.flag == 0) {
                    boolean changeBlock = false;

                    if ( currentBlock.length() + ret + 8 <= MediaBlockSize ) {
                        currentBlock.write( videoHeader, 8 );
                        currentBlock.writeVideo( resultNal, ret);
                    } else {
                        changeBlock = true;
                    }

                    if ( changeBlock == false ) {
                        if ( currentBlock.videoCount >= EstimatedFrameNumber) {
                            changeBlock = true;
                        }
                    }

                    if ( changeBlock == true) {
                        currentBlock.flag = 1;

                        mediaWriteIndex ++;
                        if ( mediaWriteIndex >= MediaBlockNumber) {
                            mediaWriteIndex = 0;
                        }
                    }
                }

            }

            inProcessing = false;
        }
    }

    private class StreamingServer extends WebSocketServer {
        private WebSocket mediaSocket = null;
        public boolean inStreaming = false;
        ByteBuffer buf = ByteBuffer.allocate(MediaBlockSize);
        Lock lock = new ReentrantLock();

        public StreamingServer( int port) throws UnknownHostException {
            super( new InetSocketAddress( port ) );
        }

        public boolean sendMedia(byte[] data, int length) {
            boolean ret = false;

            if ( inStreaming == true) {
                buf.clear();
                buf.put(data, 0, length);
                buf.flip();
            }

            if ( inStreaming == true) {
                try {
                    mediaSocket.send(buf);
                }catch (Exception e) {
                    e.printStackTrace();
                }
                ret = true;
            }

            return ret;
        }

        @Override
        public void onOpen( WebSocket conn, ClientHandshake handshake ) {
            Log.d(TAG, "onOpen");
            if ( inStreaming == true) {
                Log.d(TAG, "connected by other client");
                conn.close();
            } else {
                resetMediaBuffer();
                mediaSocket = conn;
                inStreaming = true;
                //doStreaming();
                streamingHandler.postDelayed(runnable, StreamingInterval);
            }
        }

        @Override
        public void onClose( WebSocket conn, int code, String reason, boolean remote ) {
            Log.d(TAG, "onClose");
            if (conn == mediaSocket) {
                lock.lock();
                inStreaming = false;
                mediaSocket.close();
                mediaSocket = null;
                lock.unlock();

                //jimmy for test
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        exit();
                    }
                }).start();
            }
        }

        @Override
        public void onError( WebSocket conn, Exception ex ) {
            if (conn == mediaSocket) {
                ex.printStackTrace();
                mediaSocket.close();
                inStreaming = false;
                mediaSocket = null;
            }
        }

        @Override
        public void onMessage( WebSocket conn, ByteBuffer blob ) {

        }

        @Override
        public void onMessage( WebSocket conn, String message ) {

        }

    }
    private native void nativeInitMediaEncoder(int width, int height);
    private native void nativeReleaseMediaEncoder(int width, int height);
    private native int nativeDoVideoEncode(byte[] in, byte[] out, int flag);
    private native int nativeDoAudioEncode(byte[] in, int length, byte[] out);

    static {
        System.loadLibrary("MediaEncoder");
    }

}