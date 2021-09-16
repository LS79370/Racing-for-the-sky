package com.cnam.enjmin.racingforthesky.utils;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

public class PythonLinker implements Runnable
{
    private String ip1;
    private int port1;
    private Socket socket;
    private InetAddress inAdr;
    private PrintWriter out;
    private boolean stopWorker;
    private DataInputStream inputStream;

    public PythonLinker(String ip, int port)
    {
        ip1=ip;
        port1=port;
    }

    @Override
    public void run()
    {
        try
        {
            inAdr = InetAddress.getByName(ip1);
            socket = new Socket(inAdr,port1);
            inputStream = new DataInputStream(socket.getInputStream());
            send("query");
            receive();
        }
        catch(Exception e){}
    }

    public void send(String str)
    {
        try
        {
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),true);
            out.println(str);
            //receive();
        }
        catch(Exception e){}
    }

    /*public void receive()
    {
        stopWorker = false;
        Thread workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted()&&!stopWorker)
                {
                    try{
                        int n = inputStream.available();
                        if(n>0)
                        {
                            byte[] received = new byte[n];
                            inputStream.read(received);
                            String data = new String(received,"US-ASCII");
                            h.post(new Runnable()
                            {
                                public void run()
                                {
                                    try{
                                        Log.d("PythonLinker", "Hello world !");
                                    }
                                    catch(Exception x){}
                                }
                            });
                        }
                    }catch(Exception e)
                    {
                        stopWorker=true;
                    }
                }
            }
        });
        workerThread.start();
    }*/
}
