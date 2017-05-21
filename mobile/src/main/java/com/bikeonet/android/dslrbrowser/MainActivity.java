package com.bikeonet.android.dslrbrowser;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;

import com.bikeonet.android.dslrbrowser.content.CameraItem;
import com.bikeonet.android.dslrbrowser.content.CameraList;
import com.bikeonet.android.dslrbrowser.content.PhotoItem;
import com.bikeonet.android.dslrbrowser.content.PhotoList;
import com.bikeonet.android.dslrbrowser.messaging.LocalBroadcastMessageBuilder;
import com.bikeonet.android.dslrbrowser.messaging.NotificationBuilder;
import com.bikeonet.android.dslrbrowser.upnp.BrowseManager;
import com.bikeonet.android.dslrbrowser.upnp.ContentDirectoryRegistryListener;
import com.bikeonet.android.dslrbrowser.util.DownloadManager;
import com.bikeonet.android.dslrbrowser.util.LocationStore;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.registry.RegistryListener;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements CameraItemFragment.OnCameraListFragmentInteractionListener,
        PhotoListFragment.OnPhotoListFragmentInteractionListener, SettingsFragment.OnFragmentInteractionListener {


    CameraItemFragment cameraListFragment = CameraItemFragment.newInstance(1);
    SettingsFragment settingsFragment = SettingsFragment.newInstance("", "");
    PhotoListFragment photoListFragment = PhotoListFragment.newInstance(4);
    private LocationManager locationManager;

    private class UpdateUIListReceiver extends BroadcastReceiver {
        // Prevents instantiation
        private UpdateUIListReceiver() {
        }

        // Called when the BroadcastReceiver gets an Intent it's registered to receive
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getExtras().containsKey(LocalBroadcastMessageBuilder.CAMERA_LIST_NEW_CONTENT)) {
                cameraListFragment.getViewAdapter().notifyDataSetChanged();
            }

            if (intent.getExtras().containsKey(LocalBroadcastMessageBuilder.PHOTO_LIST_NEW_CONTENT)) {
                if (photoListFragment != null && photoListFragment.getViewAdapter() != null) {
                    photoListFragment.getViewAdapter().notifyDataSetChanged();
                }
            }

        }
    }

    UpdateUIListReceiver updateCameraListReceiver = new UpdateUIListReceiver();
    private RegistryListener registryListener;
    private AndroidUpnpService upnpService;
    private ServiceConnection serviceConnection;

    @Override
    public void onCameraListFragmentInteraction(CameraItem item) {
        Log.d(this.getClass().getName(), item.toString());
    }

    @Override
    public void onPhotoListFragmentInteraction(PhotoItem item) {
        Log.d(this.getClass().getName(), item.toString());
    }

    @Override
    public void onFragmentInteraction(Uri uri) {
        Log.d(this.getClass().getName(), uri.toString());
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    showCameraList();
                    return true;
                case R.id.navigation_dashboard:
                    showPhotoList();
                    return true;
                case R.id.navigation_notifications:
                    showSettings();
                    return true;
            }
            return false;
        }

    };

    private void showSettings() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.content, settingsFragment, settingsFragment.getTag());
        ft.commit();
    }

    private void showPhotoList() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.content, photoListFragment, photoListFragment.getTag());
        ft.commit();
    }

    private void showCameraList() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.content, cameraListFragment, cameraListFragment.getTag());
        ft.commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        NotificationBuilder.setContext(this.getApplicationContext());

        doInitializeLocationManager();

        showCameraList();

        this.registryListener = new ContentDirectoryRegistryListener(this.getApplicationContext());
        this.serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                upnpService = (AndroidUpnpService) service;
                BrowseManager.initializeInstance(upnpService);
                upnpService.getRegistry().addListener(registryListener);
                upnpService.getControlPoint().search();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(this.getClass().getName(), "UPNP Service Disconnected.");
                upnpService = null;
            }
        };

        bindService(new Intent(this, AndroidUpnpServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE);

        IntentFilter updateCameraListIntentFilter = new IntentFilter(LocalBroadcastMessageBuilder.UPDATE_UI_LIST);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                updateCameraListReceiver,
                updateCameraListIntentFilter);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (upnpService != null) {
            upnpService.getRegistry().removeListener(registryListener);
        }

        // This will stop the UPnP service if nobody else is bound to it
        unbindService(serviceConnection);
    }

    /**
     * This hook is called whenever an item in your options menu is selected.
     * The default implementation simply returns false to have the normal
     * processing happen (calling the item's Runnable or sending a message to
     * its Handler as appropriate).  You can use this method for any items
     * for which you would like to do processing without those other
     * facilities.
     * <p>
     * <p>Derived classes should call through to the base class for it to
     * perform the default menu handling.</p>
     *
     * @param item The menu item that was selected.
     * @return boolean Return false to allow normal menu processing to
     * proceed, true to consume it here.
     * @see #onCreateOptionsMenu
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.search_upnp:
                CameraList.reset();
                PhotoList.ITEMS.clear();
                if (cameraListFragment.getViewAdapter() != null) {
                    cameraListFragment.getViewAdapter().notifyDataSetChanged();
                }
                if (photoListFragment != null && photoListFragment.getViewAdapter() != null) {
                    photoListFragment.getViewAdapter().notifyDataSetChanged();
                }
                if (upnpService != null && upnpService.getControlPoint() != null) {
                    upnpService.getControlPoint().search();
                }
                return true;
            case R.id.download_all:
                Log.d(this.getClass().getName(), "Download all images option menu selected");
                if ( checkSDCardAvailable() ) {
                    File pdir = getExternalMediaDirs()[0];
                    File dcim = new File(pdir.getAbsolutePath() + "/"+getDownloadDirectory());
                    if ( createDir(dcim)) {
                        DownloadManager dm = new DownloadManager(dcim.getAbsolutePath(), this);
                        PhotoItem[] imageArray = PhotoList.ITEMS.toArray(new PhotoItem[PhotoList.ITEMS.size()]);
                        dm.execute(imageArray);
                    }
                    else {
                        showErrorDialog("Error", "Failed to create storage folder " + dcim, "Ok");
                    }
                }
                else {
                    showErrorDialog("Error", "Storage media not available", "Ok");
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }
    private void showErrorDialog(String title, String message, String confirmation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(confirmation, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }

        });
        AlertDialog alert = builder.create();
        alert.show();
    }
    private String getDownloadDirectory() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        boolean downloadToAlbum = prefs.getBoolean("download_to_album", false);

        if (downloadToAlbum) {
            Date today = new Date();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            return format.format(today);
        }
        else {
            return "";
        }
    }

    private static boolean createDir(File dir) {
        if ( dir.exists()) {
            return true;
        }

        return dir.mkdirs();
    }

    private boolean checkSDCardAvailable() {
        boolean mExternalStorageAvailable = false;
        boolean mExternalStorageWriteable = false;
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // We can read and write the media
            mExternalStorageAvailable = mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // We can only read the media
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else {
            // Something else is wrong. It may be one of many other states, but all we need
            //  to know is we can neither read nor write
            mExternalStorageAvailable = mExternalStorageWriteable = false;
        }

        return mExternalStorageAvailable && mExternalStorageWriteable;
    }

    private void doInitializeLocationManager() {

        locationManager = (LocationManager) this
                .getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
                0, new LocationListener() {

                    @Override
                    public void onLocationChanged(Location location) {
                        LocationStore.getInstance().setLastLocation(location);
                    }

                    @Override
                    public void onStatusChanged(String provider, int status,
                                                Bundle extras) {
                        Log.i(this.getClass().getName(), provider + " status changed " + status);

                    }

                    /**
                     * 07-12 10:55:22.208: I/com.bikeonet.android.dslrbrowser.DslrBrowserApplication(1431): gps disabled
                     * 07-12 10:55:33.349: I/com.bikeonet.android.dslrbrowser.DslrBrowserApplication(1431): gps enabled
                     */

                    @Override
                    public void onProviderEnabled(String provider) {
                        Log.i(this.getClass().getName(), provider + " enabled");
                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                        Log.i(this.getClass().getName(), provider + " disabled");
                    }


                });
    }
}
