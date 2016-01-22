package com.timescript.message;

import android.util.Log;

import com.timescript.tm100server.MainService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * Created by jimmy on 12/22/15.
 */
public class UDPServer {
    private String TAG = "UDPServer";
    private String clientIP = null;
    Socket socket = null;
    private boolean connected = false;
    private ObjectInputStream objectInputStream;
    private ObjectOutputStream objectOutputStream;

    public String getClientIP(){
        return clientIP;
    }

    public void waitForConn(){
        Log.d(TAG, "waitForConn");
        new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket udpSocket = null;
                byte[] data = new byte[256];
                try {
                    udpSocket = new DatagramSocket(51503);
                } catch (SocketException e) {
                    e.printStackTrace();
                }

                while(true) {
                    try {
                        Log.d(TAG, "wait for connect ...");
                        DatagramPacket udpPacket = new DatagramPacket(data, data.length);
                        if(udpSocket == null) {
                            return;
                        }
                        udpSocket.receive(udpPacket);
                        if(udpPacket.getAddress() != null) {
                            //if not the first connect, just send ack
                            if(clientIP == null) {
                                clientIP = udpPacket.getAddress().toString().substring(1);
                                sendACK(clientIP, true);
                                connected = true;
                                readMsg();
                                Log.d(TAG, clientIP + " connected");
                            } else{
                                if(!clientIP.equals(udpPacket.getAddress().toString().substring(1))) {
                                    sendACK(udpPacket.getAddress().toString().substring(1), false);
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void processMsg(Object obj) {
        MainService.processMsg(obj);
    }

    public void readMsg() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(socket == null) {
                        return;
                    }
                    Object obj;
                    objectInputStream = new ObjectInputStream(socket.getInputStream());
                    while((obj = objectInputStream.readObject()) != null) {
                        processMsg(obj);
                    }
                } catch (IOException e) {
                    Log.d(TAG, "client socket disconnected");
                    clientIP = null;
                    connected = false;
                } catch (ClassNotFoundException e) {

                }
            }
        }).start();
    }

    public void sendMsg(String msg) {
        if(clientIP != null) {
            try {
                socket = new Socket(clientIP, 51504);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream());
                out.print(msg);
                out.flush();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private void sendACK(String ip, boolean first){
        int error_cnt = 0;
        if(ip != null) {
            while(true) {
                try {
                    error_cnt++;
                    if (first) {
                        socket = new Socket(ip, 51504);
                        objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                        String str = "Hydrodent:TRUE";
                        objectOutputStream.writeObject(str);
                        objectOutputStream.flush();
                        return;
                    } else {
                        Socket tmpSocket = new Socket(ip, 51504);
                        ObjectOutputStream out = new ObjectOutputStream(tmpSocket.getOutputStream());
                        String str = "Hydrodent:FALSE";
                        out.writeObject(str);
                        out.flush();
                        out.close();
                        tmpSocket.close();
                        return;
                    }
                } catch (Exception e) {
                    Log.d(TAG, "socket.getOutputStream() error");
                    e.printStackTrace();
                    if(error_cnt == 3)
                        return;
                }
            }
        }
    }
}
