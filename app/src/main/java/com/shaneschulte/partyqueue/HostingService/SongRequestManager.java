package com.shaneschulte.partyqueue.HostingService;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.shaneschulte.partyqueue.Events.SongAddEvent;
import com.shaneschulte.partyqueue.Events.SongChangeEvent;
import com.shaneschulte.partyqueue.Events.SongPauseEvent;
import com.shaneschulte.partyqueue.Events.SongPlayEvent;
import com.shaneschulte.partyqueue.Events.SongRemoveEvent;
import com.shaneschulte.partyqueue.PartyApp;
import com.shaneschulte.partyqueue.SongRequest;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.SpotifyPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Track;
import retrofit.Callback;
import retrofit.RetrofitError;

class SongRequestManager {

    private SpotifyPlayer mPlayer;
    private SpotifyService spotify;
    private List<SongRequest> songQueue;
    private SongRequest       songNowPlaying;

    private static final String TAG = "SongRequestManager";
    private boolean loggedIn;

    SongRequestManager() {
        SpotifyApi api = new SpotifyApi();
        spotify = api.getService();

        songNowPlaying = null;
        loggedIn = false;

        songQueue   = new ArrayList<>();
    }

    void startPlayer(SpotifyPlayer mPlayer) {
        this.mPlayer = mPlayer;
        playNextSong();
    }

    void logIn() {
        loggedIn = true;
        playNextSong();
    }

    void logOut() {
        loggedIn = false;
    }

    synchronized private void playNextSong() {
        if(mPlayer == null) return;
        if(!loggedIn) return;

        if(songQueue.isEmpty()) {
            songNowPlaying = null;
            return;
        }

        songNowPlaying = songQueue.get(0);
        songQueue.remove(0);

        mPlayer.playUri(null, "spotify:track:"+songNowPlaying.track, 0, 0);
        postEvent(new SongChangeEvent(songNowPlaying));
    }

    private boolean isInQueue(String track) {
        for(SongRequest r : songQueue) {
            if(r.track.equals(track)) {
                return true;
            }
        }
        return false;
    }

    private int findRequestInsertIndex(SongRequest r) {
        Set<String> set = new HashSet<>();
        int i;
        for(i=0; i<songQueue.size(); ++i) {
            if(set.contains(songQueue.get(i).ip)) { // Duplicate detected
                if(!set.contains(r.ip)) return i; // Found our spot
                set.clear();
            }
            set.add(songQueue.get(i).ip);
        }
        return i;
    }

    void requestNewSong(String track, String addedBy, String remoteIpAddress) {
        SongRequest r = new SongRequest(track, addedBy, remoteIpAddress);
        spotify.getTrack(track, new Callback<Track>() {
            @Override
            public void success(Track track, retrofit.client.Response response) {
                if(isInQueue(r.track)) return;
                r.setMeta(track);
                if(songNowPlaying == null) {
                    songQueue.add(r);
                    playNextSong();
                }
                else {
                    int index = findRequestInsertIndex(r);
                    songQueue.add(index, r);
                    postEvent(new SongAddEvent(r, index));
                }
            }

            @Override
            public void failure(RetrofitError error) {
                Log.e(TAG, error.getMessage());
            }
        });
    }

    void handlePlaybackEvent(PlayerEvent playerEvent) {
        switch (playerEvent) {
            case kSpPlaybackNotifyLostPermission:
                mPlayer.resume(null);
                break;
            case kSpPlaybackNotifyPause:
                postEvent(new SongPauseEvent());
                break;
            case kSpPlaybackNotifyPlay:
                postEvent(new SongPlayEvent());
                break;
            case kSpPlaybackNotifyAudioDeliveryDone:
                playNextSong();
                break;
            default:
                break;
        }
    }

    List<SongRequest> getQueue() {
        return Collections.unmodifiableList(songQueue);
    }

    private void postEvent(Object o) {
        new Handler(Looper.getMainLooper()).post(() -> PartyApp.getInstance().getBus().post(o));
    }

    SongRequest getNowPlaying() {
        return songNowPlaying;
    }

    boolean removeRequest(String track, String ip) {
        for(SongRequest r : songQueue) {
            if(track.equals(r.track)) {
                if(ip.equals(r.ip)) {
                    postEvent(new SongRemoveEvent(r));
                    songQueue.remove(r);
                    return true;
                }
                return false;
            }
        }
        return false;
    }
}
