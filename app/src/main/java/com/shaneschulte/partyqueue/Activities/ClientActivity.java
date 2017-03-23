package com.shaneschulte.partyqueue.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.android.volley.toolbox.StringRequest;
import com.shaneschulte.partyqueue.PartyApp;
import com.shaneschulte.partyqueue.R;
import com.shaneschulte.partyqueue.SongRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.Tracks;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class ClientActivity extends PartyActivity {

    public static final String TAG = "ClientActivity";

    private String prevJson;
    private String hostname;
    private SwipeRefreshLayout mRefresh;

    private SpotifyService spotify;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.client_options, menu);

        //Search bar
        MenuItem searchMenuItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchMenuItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Intent searchIntent = new Intent(ClientActivity.this, SearchActivity.class);
                searchIntent.putExtra("query", query);
                searchIntent.putExtra("HOST_URL", hostname+"/add");
                startActivity(searchIntent);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        return true;
    }

    public void addSongs(String json) {
        prevJson = json;
        trackAdapter.clear();

        mRefresh.setRefreshing(false);
        Log.d("JSON", json);
        try {
            JSONArray jArray = new JSONArray(json);

            if(jArray.length() == 0) {
                togglePlaying(false);
                return;
            }
            else togglePlaying(true);

            ArrayList<SongRequest> reqs = new ArrayList<>();
            StringBuilder builder = new StringBuilder();
            long timeT = 15000;
            for (int i=0; i < Math.min(20, jArray.length()); i++) {
                if(i > 0) builder.append(",");
                JSONObject a = jArray.getJSONObject(i);

                String track    = a.getString("track");
                String addedBy  = a.has("addedBy") ? a.getString("addedBy") : "Unknown";
                String ip       = a.has("ip")      ? a.getString("ip")      : "0.0.0.0";
                timeT           = a.has("time")    ? a.getLong("time")+1000 : timeT;

                addedBy = addedBy.replaceAll("[^A-Za-z0-9 ]", "");
                addedBy = addedBy.substring(0, Math.min(addedBy.length(), 15));

                reqs.add(new SongRequest(track, addedBy, ip, i));
                builder.append(track);
            }
            final long time = timeT;
            Log.d(TAG, builder.toString());

            spotify.getTracks(builder.toString(), new Callback<Tracks>() {
                @Override
                public void success(Tracks tracks, Response response) {
                    for(int j=0; j<tracks.tracks.size(); j++) {
                        SongRequest r = reqs.get(j);
                        Track t = tracks.tracks.get(j);

                        if(t == null) return;

                        r.setMeta(t);
                        if(r.id == 0) playNewSong(r, time);
                        else trackAdapter.add(r);
                    }
                }

                @Override
                public void failure(RetrofitError error) {
                    Log.e(TAG, "Failed to load metadata: "+error.getMessage()+error.getUrl());
                }
            });
        } catch(JSONException e) {
            Log.d("JSON ERROR", e.toString());
            togglePlaying(false);
        }
    }

    @Override
    protected void timerFinished() {
        initiateRefresh();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setContentView(R.layout.activity_client);

        super.onCreate(savedInstanceState);



        prevJson = "";
        SpotifyApi api = new SpotifyApi();
        spotify = api.getService();

        hostname = getIntent().getStringExtra("HOST_URL");
        mRefresh = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);

        mRefresh.setOnRefreshListener(this::initiateRefresh);

        listView.setLongClickable(true);
        listView.setOnItemLongClickListener((parent, v, position, id) -> {
            //Do your tasks here
            SongRequest r = trackAdapter.getItem(position);
            if(r == null) return true;
            if(!r.ip.equals(getMyHostname())) return true;
            AlertDialog.Builder alert = new AlertDialog.Builder(ClientActivity.this);
            alert.setTitle("Delete Song");
            alert.setMessage("Are you sure you want to delete this song?");
            alert.setPositiveButton("DELETE",
                    (dialog, which) -> PartyApp.getInstance().addToRequestQueue(
                            new StringRequest(com.android.volley.Request.Method.POST, hostname+"/remove",
                                    response -> {
                                        trackAdapter.remove(r);
                                        dialog.dismiss();
                                    },
                                    error -> Log.e("REMOVE", "Song Remove failed")) {
                                // Override to provide POST request parameters
                                @Override
                                protected Map<String, String> getParams() {
                                    Map<String, String> params = new HashMap<>();
                                    params.put("track", r.track.replace("spotify:track:", ""));
                                    return params;
                                }
                            }));
        alert.setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss());

        alert.show();

        return true;
    });
    }

    public void initiateRefresh() {
        mRefresh.setRefreshing(true);
        // Instantiate the RequestQueue.
        String url = hostname + "/queue";

        // Request a string response from the provided URL.
        PartyApp.getInstance().addToRequestQueue(
                new StringRequest(com.android.volley.Request.Method.GET, url,
                        response -> {
                            if(response.equals(prevJson)) {
                                mRefresh.setRefreshing(false);
                                return;
                            }
                            addSongs(response);
                        },
                        error -> Log.d("VolleyError", error.toString())
                ), TAG);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "RESUMING");
        initiateRefresh();
    }
}
