package com.cnam.enjmin.racingforthesky.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.content.PermissionChecker;

import java.util.Arrays;

import static android.support.v4.app.ActivityCompat.requestPermissions;
import static android.support.v4.content.PermissionChecker.checkSelfPermission;

public class PermissionsManager
{
    // For Android 6.0 (API Level 25)  permission requests

    public static final int REQ_PERMISSION_THISAPP = 0; // unique code for permissions request
    private static boolean useCameraFlag = true;               // we want to use the camera
    public static boolean cameraPermissionGranted = false;   // have CAMERA permission

    public void getPermissions(Activity activity)
    {
        String TAG = "getPermissions";
        //if (DBG) Log.v(TAG, "in getPermissions()");
        if (Build.VERSION.SDK_INT >= 23)
        {            // need to ask at runtime as of Android 6.0
            String sPermissions[] = new String[2];    // space for possible permission strings
            int nPermissions = 0;    // count of permissions to be asked for
            if (useCameraFlag)
            {    // protection level: dangerous
                if (checkSelfPermission(activity.getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    cameraPermissionGranted = true;
                else sPermissions[nPermissions++] = Manifest.permission.CAMERA;
            }
            if (!(checkSelfPermission(activity.getApplicationContext(), Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED))
                sPermissions[nPermissions++] = Manifest.permission.INTERNET;
            if (nPermissions > 0)
            {
                //if (DBG) Log.d(TAG, "Need to ask for " + nPermissions + " permissions");
                if (nPermissions < sPermissions.length)
                    sPermissions = Arrays.copyOf(sPermissions, nPermissions);

                requestPermissions(activity, sPermissions, REQ_PERMISSION_THISAPP);    // start the process
            }
        }
        else
       {    // in earlier API, permission is dealt with at install time, not run time
            if (useCameraFlag) cameraPermissionGranted = true;
        }
    }
}
