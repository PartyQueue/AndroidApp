package com.shaneschulte.partyqueue.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.android.volley.toolbox.StringRequest;
import com.shaneschulte.partyqueue.Events.SongChangeEvent;
import com.shaneschulte.partyqueue.PartyApp;
import com.shaneschulte.partyqueue.R;
import com.shaneschulte.partyqueue.SongRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Track;
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

            if(jArray.length() == 0) togglePlaying(false);
            else togglePlaying(true);

            for (int i=0; i < jArray.length(); i++) {
                JSONObject a = jArray.getJSONObject(i);

                String track    = a.getString("track");
                String addedBy  = a.has("addedBy") ? a.getString("addedBy") : "Unknown";
                String ip       = a.has("ip")      ? a.getString("ip")      : "0.0.0.0";

                final SongRequest r = new SongRequest(track, addedBy, ip, i);

                spotify.getTrack(track, new Callback<Track>() {
                    @Override
                    public void success(Track track, Response response) {
                        r.setMeta(track);
                        if(r.id == 0) playNewSong(r);
                        else trackAdapter.add(r);
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        Log.e(TAG, "Failed to load metadata for track: "+track);
                    }
                });
            }
        } catch(JSONException e) {
            Log.d("JSON ERROR", e.toString());
            togglePlaying(false);
        }
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

        mRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override public void onRefresh() {
                initiateRefresh();
            }
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
