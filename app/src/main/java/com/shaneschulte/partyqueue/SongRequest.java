package com.shaneschulte.partyqueue;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import kaaes.spotify.webapi.android.models.Track;


public class SongRequest {

    public final String track;
    public final String addedBy;
    public final String ip;
    public final int id;
    private Track meta;
    public final JSONObject json;

    public SongRequest(String track, String addedBy, String ip, int id, Track meta) {
        this.track      = track;
        this.addedBy    = addedBy;
        this.ip         = ip;
        this.id         = id;
        this.meta       = meta;

        // Create JSON object for easier POST requests
        json = new JSONObject();
        try {
            json.put("track",   track   );
            json.put("addedBy", addedBy );
            json.put("ip",      ip      );
        } catch (JSONException e) {
            Log.e("SongRequest", e.getMessage());
        }
    }

    public SongRequest(String track, String addedBy, String ip, int id) {
        this(track, addedBy, ip, id, null);
    }

    public SongRequest(String track, String addedBy, String ip) {
        this(track, addedBy, ip, 0, null);
    }

    public Track getMeta() {
        return meta;
    }
    public SongRequest setMeta(Track meta) {
        this.meta = meta;
        return this;
    }
    public boolean hasMeta() {
        return meta != null;
    }
}