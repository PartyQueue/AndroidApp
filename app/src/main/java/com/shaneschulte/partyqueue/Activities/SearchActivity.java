package com.shaneschulte.partyqueue.Activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.StringRequest;
import com.shaneschulte.partyqueue.PartyApp;
import com.shaneschulte.partyqueue.R;
import com.shaneschulte.partyqueue.SongRequest;
import com.shaneschulte.partyqueue.TrackAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.TracksPager;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class SearchActivity extends AppCompatActivity {

    public static final String TAG = "SearchActivity";

    SpotifyService spotify;
    ListView listView;
    TrackAdapter adapter;
    ArrayList<SongRequest> results;
    String host_add_url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        // Register Spotify web API objects
        SpotifyApi api = new SpotifyApi();
        spotify = api.getService();

        // Configure ListView
        results = new ArrayList<>();
        adapter = new TrackAdapter(this, results);
        listView = (ListView) findViewById(R.id.lvItems);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(
                (parent, view, position, id) -> {
                    SongRequest sr = adapter.getItem(position);
                    requestSong(sr);
                    adapter.remove(sr);
                });

        host_add_url = getIntent().getStringExtra("HOST_URL");

        initiateSearch(getIntent().getStringExtra("query"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_close:
                finish();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Issues a song request to the server via POST.
     * @param request SongRequest object. Does not require metadata.
     */
    void requestSong(SongRequest request) {

        // Volley POST request to server
        PartyApp.getInstance().addToRequestQueue(
                new StringRequest(Request.Method.POST, host_add_url,
                response -> Toast.makeText(this, "Song Requested", Toast.LENGTH_SHORT).show(),
                error -> VolleyLog.e("Error: %s", error.getMessage())) {
                // Override to provide POST request parameters
                @Override
                protected Map<String, String> getParams() {
                    Map<String, String> params = new HashMap<>();
                    params.put("track", request.track.replace("spotify:track:", ""));
                    params.put("addedBy", "Shane");
                    return params;
                }
        });
    }

    /**
     * Issues the search query to Spotify's Web API
     * @param query The string being searched.
     */
    void initiateSearch(String query) {
        spotify.searchTracks(query, new Callback<TracksPager>() {
            @Override
            public void success(TracksPager tracksPager, Response response) {
                adapter.clear();
                for(Track t : tracksPager.tracks.items) {
                    SongRequest r = new SongRequest(t.uri, "", "");
                    r.setMeta(t);
                    adapter.add(r);
                }
            }

            @Override
            public void failure(RetrofitError error) {
                Log.e("Spotify Web API",error.getMessage());
            }
        });
    }
}
