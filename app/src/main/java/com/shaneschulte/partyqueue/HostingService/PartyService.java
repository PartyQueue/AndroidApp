package com.shaneschulte.partyqueue.HostingService;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.session.IMediaSession;
import android.util.Log;

import com.shaneschulte.partyqueue.Activities.HostActivity;
import com.shaneschulte.partyqueue.R;
import com.shaneschulte.partyqueue.SongRequest;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.models.UserPrivate;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import rx.Subscription;
import rxbonjour.RxBonjour;
import rxbonjour.broadcast.BonjourBroadcast;

public class PartyService extends Service implements SpotifyPlayer.NotificationCallback, ConnectionStateCallback {

    public final String TAG = "PartyService";

    private String CLIENT_ID;

    private static final int mStartMode = START_STICKY;       // indicates how to behave if the service is killed
    private final IBinder mBinder = new PartyServiceBinder();      // interface for clients that bind
    private static final boolean mAllowRebind = true; // indicates whether onRebind should be used
    private SpotifyPlayer mPlayer;
    private SongRequestManager mManager;
    private AndroidWebServer mServer;
    private Subscription nsd;

    @Override
    public void onCreate() {
        Log.d(TAG, "Party Service Created");
        CLIENT_ID = this.getString(R.string.CLIENT_ID);
        mManager = new SongRequestManager();
        mServer = new AndroidWebServer(8000, getResources(), mManager);
    }

    private void registerService(String name, int port) {
        BonjourBroadcast<?> broadcast = RxBonjour.newBroadcast("_partyQueue._tcp")
                .name(name)
                .port(port)
                .build();

        nsd = broadcast.start(this).subscribe();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Party Service Started");
        return mStartMode;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Party Service Bound");

        SpotifyApi api = new SpotifyApi().setAccessToken(intent.getStringExtra("AuthToken"));

        // Attempt to register server to their name
        api.getService().getMe(new Callback<UserPrivate>() {
            @Override
            public void success(UserPrivate userPrivate, Response response) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    Log.d("SpotifyMetadata", "Success, name: "+userPrivate.display_name);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        registerService(userPrivate.display_name, 8000);
                    }
                }
            }


            @Override
            public void failure(RetrofitError error) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    Log.d("SpotifyMetadata", "Failed, reverting to 'Party Queue'");
                    registerService("Party Queue", 8000);
                }
            }
        });

        startPlayer(intent.getStringExtra("AuthToken"));

        Intent resultIntent = new Intent(this, HostActivity.class);

        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.common_google_signin_btn_icon_light)
                        .setContentTitle("My notification")
                        .setContentText("Hello World!")
                        .setContentIntent(resultPendingIntent);

        Notification note = mBuilder.build();
        note.flags|=Notification.FLAG_NO_CLEAR;

        startForeground(5760, note);
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Party Service Unbound");

        return mAllowRebind;
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "Party Service Destroyed");

        Spotify.destroyPlayer(this);

        if(nsd != null) nsd.unsubscribe();

        if(mServer.isAlive()) {
            mServer.stop();
        }
        super.onDestroy();
    }

    public List<SongRequest> getQueue() {
        return mManager.getQueue();
    }

    public SongRequest getNowPlaying() {
        return mManager.getNowPlaying();
    }

    public class PartyServiceBinder extends Binder {
        public PartyService getService() {
            // Return this instance of LocalService so clients can call public methods
            return PartyService.this;
        }
    }

    // Initialize Spotify Player
    private void startPlayer(String accessToken) {
        Config playerConfig = new Config(this, accessToken, CLIENT_ID);
        Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
            @Override
            public void onInitialized(SpotifyPlayer spotifyPlayer) {
                mPlayer = spotifyPlayer;
                mPlayer.addConnectionStateCallback(PartyService.this);
                mPlayer.addNotificationCallback(PartyService.this);
                mManager.startPlayer(mPlayer);

                // Attempt to set up server when mPlayer is initialized
                try {
                    mServer.start();
                    Log.d("Httpd", "Server starting!");
                } catch (IOException e) {
                    Log.e("Httpd", e.getMessage());
                    stopSelf();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                Log.e(TAG, "Could not initialize player: " + throwable.getMessage());
                stopSelf();
            }
        });
    }
    
    // Spotify Player Callbacks

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d(TAG, "Playback event received: " + playerEvent.name());
        mManager.handlePlaybackEvent(playerEvent);
    }

    @Override
    public void onLoggedIn() {
        mManager.logIn();
        Log.d(TAG, "User logged in");
    }

    @Override
    public void onLoggedOut() {
        mManager.logOut();
        Log.d(TAG, "User logged out");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d(TAG, "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d(TAG, "Temporary error occurred");
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.d(TAG, "Playback error received: " + error.name());
        switch (error) {
            // Handle error type as necessary
            default:
                break;
        }
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d(TAG, "Received connection message: " + message);
    }
    
}
