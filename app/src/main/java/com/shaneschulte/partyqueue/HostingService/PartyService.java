package com.shaneschulte.partyqueue.HostingService;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.shaneschulte.partyqueue.Activities.HostActivity;
import com.shaneschulte.partyqueue.Events.SongAddEvent;
import com.shaneschulte.partyqueue.Events.SongChangeEvent;
import com.shaneschulte.partyqueue.Events.SongPausePlayEvent;
import com.shaneschulte.partyqueue.Events.SongRemoveEvent;
import com.shaneschulte.partyqueue.PartyApp;
import com.shaneschulte.partyqueue.R;
import com.shaneschulte.partyqueue.SongRequest;
import com.shaneschulte.partyqueue.Utils;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;
import com.squareup.otto.Subscribe;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
    public final static int NOTEID = 5760;

    private String CLIENT_ID;

    private static final int mStartMode = START_STICKY;       // indicates how to behave if the service is killed
    private final IBinder mBinder = new PartyServiceBinder();      // interface for clients that bind
    private static final boolean mAllowRebind = true; // indicates whether onRebind should be used
    private SpotifyPlayer mPlayer;
    private SongRequestManager mManager;
    private AndroidWebServer mServer;
    private Subscription nsd;

    public static final String ACTION_NEXT = "com.shaneschulte.partyqueue.action_skip";
    public static final String ACTION_PP = "com.shaneschulte.partyqueue.action_pp";
    private static boolean initialized = false;

    public static boolean hasAuth() {
        return initialized;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Party Service Created");
        CLIENT_ID = this.getString(R.string.CLIENT_ID);
        mManager = new SongRequestManager();
        mServer = new AndroidWebServer(8000, getResources(), mManager);
        initialized = false;
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
        Log.d(TAG, "Start Command Triggered");
        if(intent == null) return mStartMode;
        if(intent.hasExtra("AuthToken") && !initialized) {
            initialized = true;
            SpotifyApi api = new SpotifyApi().setAccessToken(intent.getStringExtra("AuthToken"));

            // Attempt to register server to their name
            api.getService().getMe(new Callback<UserPrivate>() {
                @Override
                public void success(UserPrivate userPrivate, Response response) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        Log.d("SpotifyMetadata", "Success, name: "+userPrivate.display_name);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            registerService(userPrivate.display_name, 8000);
                            mManager.setCountry(userPrivate.country);
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

            PartyApp.getInstance().getBus().register(this);

            startForeground(NOTEID, buildNote(null, false));
            return mStartMode;
        }
        String action = intent.getAction();
        if(action != null) {
            if (action.equals(ACTION_NEXT)) {
                Log.d(TAG, "SKIP REQUESTED");
                mManager.skipSong();
            } else if (action.equals(ACTION_PP)) {
                Log.d(TAG, "Pause/Play requested");
                mManager.playPause();
            }
        }
        return mStartMode;
    }

    @Subscribe
    public void onSongChange(SongChangeEvent e) {
        startForeground(NOTEID, buildNote(e.request, false));
    }

    @Subscribe
    public void onSongPlay(SongPausePlayEvent e) {
        startForeground(NOTEID, buildNote(mManager.getNowPlaying(), e.paused));
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Party Service Bound");
        return mBinder;
    }

    private Notification buildNote(SongRequest r, boolean paused) {
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        new Intent(this, HostActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        PendingIntent skipPendingIntent =
                PendingIntent.getService(
                        this,
                        0,
                        new Intent(this, PartyService.class).setAction(ACTION_NEXT),
                        0
                );

        PendingIntent ppPendingIntent =
                PendingIntent.getService(
                        this,
                        0,
                        new Intent(this, PartyService.class).setAction(ACTION_PP),
                        0
                );

        String title, artist;
        if(r != null) {
            title = r.getMeta().name;
            artist = Utils.artistString(r.getMeta().artists);
        }
        else {
            title = "Nothing Playing";
            artist = "Add songs to the queue to get started!";
        }
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_android_notification)
                        .setContentTitle(title)
                        .setContentText(artist)
                        .setContentIntent(resultPendingIntent);

        mBuilder.setShowWhen(false);
        mBuilder.setColor(Color.argb(255, 30, 215, 96));

        if(paused) mBuilder.addAction(R.drawable.ic_media_play_light, "Resume Playing", ppPendingIntent);
        else if(r != null) mBuilder.addAction(R.drawable.ic_media_stop_light, "Skip This Song", skipPendingIntent);
        Notification note = mBuilder.build();
        note.flags|=Notification.FLAG_NO_CLEAR;
        return note;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Party Service Unbound");
        return mAllowRebind;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "Party Service Rebound");
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "Party Service Destroyed");
        PartyApp.getInstance().getBus().unregister(this);
        mManager.cleanUp();

        if(initialized) {
            Spotify.destroyPlayer(this);
            initialized = false;
        }

        if(nsd != null) nsd.unsubscribe();

        if(mServer.isAlive()) {
            mServer.stop();
        }
        super.onDestroy();
    }

    public List<SongRequest> getQueue() {
        return mManager.getQueue();
    }

    public boolean isPaused() { return mManager.isPaused(); };

    public SongRequest getNowPlaying() {
        return mManager.getNowPlaying();
    }

    public long getTimeRemaining() {
        return mManager.getTimeRemaining();
    }

    public void removeSong(SongRequest item) {
        mManager.removeRequest(item.track, "host");
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
