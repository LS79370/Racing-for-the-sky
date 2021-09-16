package com.cnam.enjmin.racingforthesky.utils;

import android.os.AsyncTask;

import com.cnam.enjmin.racingforthesky.MainActivity;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class PythonLinker extends AsyncTask<Void,Void,Void>
{
    private Socket socket;

    @Override
    protected Void doInBackground(Void... params){
        try
        {
            InetAddress inetAddress = InetAddress.getByName(MainActivity.IP);
            socket = new java.net.Socket(inetAddress, MainActivity.PORT);
            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
            dataOutputStream.writeBytes(MainActivity.REQUEST);
            dataOutputStream.close();
            socket.close();
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }
}