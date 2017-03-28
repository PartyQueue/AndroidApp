package com.shaneschulte.partyqueue.hostingservice;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.shaneschulte.partyqueue.events.SongAddEvent;
import com.shaneschulte.partyqueue.events.SongChangeEvent;
import com.shaneschulte.partyqueue.events.SongPausePlayEvent;
import com.shaneschulte.partyqueue.events.SongRemoveEvent;
import com.shaneschulte.partyqueue.PartyApp;
import com.shaneschulte.partyqueue.SongRequest;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.SpotifyPlayer;
import com.squareup.otto.Produce;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
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
    private List<SongRequest> songQueue, beforeQueue;
    private SongRequest       songNowPlaying;

    public static final int LENGTH_KEEP = 1800; // Prevent for 0.5 hours after play

    private static final String TAG = "SongRequestManager";
    private boolean loggedIn;
    private HashMap<String, Integer> alreadyPlayedMap;
    private String country;
    private boolean paused;

    SongRequestManager() {
        SpotifyApi api = new SpotifyApi();
        spotify = api.getService();

        songNowPlaying = null;
        loggedIn = false;
        paused = true;

        PartyApp.getInstance().getBus().register(this);

        beforeQueue = new ArrayList<>();
        songQueue   = new ArrayList<>();
        alreadyPlayedMap = new HashMap<>();
    }

    void startPlayer(SpotifyPlayer mPlayer) {
        this.mPlayer = mPlayer;
        playNextSong();
    }

    void logIn() {
        loggedIn = true;
        playNextSong();
    }

    void cleanUp() {
        PartyApp.getInstance().getBus().unregister(this);
    }

    void logOut() {
        loggedIn = false;
    }

    void setCountry(String s) {
        this.country = s;
    }
    String getCountry() {
        return country;
    }
    public boolean hasCountry() {
        return country != null;
    }

    public JSONArray getQueueJSON() {
        JSONArray arr = new JSONArray();
        if(getNowPlaying() != null) arr.put(getNowPlayingJSON());
        for(int i = 0; i < getQueue().size(); ++i) {
            JSONObject o = getQueue().get(i).json;
            if (o != null) {
                arr.put(o);
            }
        }
        return arr;
    }

    synchronized private void playNextSong() {
        if(mPlayer == null) return;
        if(!loggedIn) return;

        if(songQueue.isEmpty()) {
            songNowPlaying = null;
            postEvent(new SongChangeEvent(null));
            return;
        }

        songNowPlaying = songQueue.get(0);
        alreadyPlayedMap.put(songNowPlaying.track, Calendar.getInstance().get(Calendar.SECOND));
        songQueue.remove(0);

        mPlayer.playUri(null, "spotify:track:"+songNowPlaying.track, 0, 0);
        postEvent(new SongChangeEvent(songNowPlaying));
    }

    boolean alreadyPlayed(String track) {
        for(SongRequest r : beforeQueue) if(r.track.equals(track)) return true;
        for(SongRequest r : songQueue) if(r.track.equals(track)) return true;

        int time = Calendar.getInstance().get(Calendar.SECOND);

        for(String r : alreadyPlayedMap.keySet()) {
            if(time - LENGTH_KEEP > alreadyPlayedMap.get(r)) continue;
            if(r.equals(track)) return true;
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
        beforeQueue.add(r);
        spotify.getTrack(track, new Callback<Track>() {
            @Override
            public void success(Track track, retrofit.client.Response response) {
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
                beforeQueue.remove(r);
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
                postEvent(new SongPausePlayEvent(true, getTimeRemaining()));
                paused = true;
                break;
            case kSpPlaybackNotifyPlay:
                postEvent(new SongPausePlayEvent(false, getTimeRemaining()));
                paused = false;
                break;
            case kSpPlaybackNotifyAudioDeliveryDone:
                playNextSong();
                break;
            default:
                break;
        }
    }

    @Produce
    public SongPausePlayEvent lastEvent() {
        return new SongPausePlayEvent(paused, getTimeRemaining());
    }

    List<SongRequest> getQueue() {
        return Collections.unmodifiableList(songQueue);
    }

    public void postEvent(Object o) {
        new Handler(Looper.getMainLooper()).post(() -> PartyApp.getInstance().getBus().post(o));
    }

    SongRequest getNowPlaying() {
        return songNowPlaying;
    }

    boolean removeRequest(String track, String ip) {
        for(SongRequest r : songQueue) {
            if(track.equals(r.track)) {
                if(ip.equals(r.ip) || ip.equals("host")) {
                    postEvent(new SongRemoveEvent(r));
                    songQueue.remove(r);
                    alreadyPlayedMap.remove(r.track);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public long getTimeRemaining() {
        if(songNowPlaying == null) return 0;
        return songNowPlaying.getMeta().duration_ms - mPlayer.getPlaybackState().positionMs;
    }

    public JSONObject getNowPlayingJSON() {
        if(songNowPlaying == null) return null;
        try {
            JSONObject clone = new JSONObject(songNowPlaying.json.toString());
            long time = songNowPlaying.getMeta().duration_ms - mPlayer.getPlaybackState().positionMs;
            if(!mPlayer.getPlaybackState().isPlaying) time = Math.max(30000, time);
            return clone.put("time", time);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void skipSong() {
        if(!songQueue.isEmpty()) playNextSong();
    }

    public void playPause() {
        if(songNowPlaying == null) return;
        if(paused) mPlayer.resume(null);
        else mPlayer.pause(null);
    }

    public boolean isPaused() {
        return paused;
    }

}
