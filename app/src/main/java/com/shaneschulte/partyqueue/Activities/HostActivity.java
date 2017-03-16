package com.shaneschulte.partyqueue.Activities;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.shaneschulte.partyqueue.Events.SongAddEvent;
import com.shaneschulte.partyqueue.Events.SongChangeEvent;
import com.shaneschulte.partyqueue.Events.SongRemoveEvent;
import com.shaneschulte.partyqueue.PartyApp;
import com.shaneschulte.partyqueue.HostingService.PartyService;
import com.shaneschulte.partyqueue.R;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.squareup.otto.Subscribe;

public class HostActivity extends PartyActivity {

    public static final String TAG = "HostActivity";

    private ServiceConnection mServiceConnection;
    private PartyService service;
    private boolean mBound;

    private static final String REDIRECT_URI = "party-queue-app://callback";

    private ShareActionProvider mShareActionProvider;

    // SongRequest code that will be used to verify if the result comes from correct activity
    // Can be any integer
    private static final int REQUEST_CODE = 5670;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_host);
        mBound = false;

        super.onCreate(savedInstanceState);

        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mBound = true;
                HostActivity.this.service = ((PartyService.PartyServiceBinder)service).getService();
                Log.d(TAG, "Service Connected");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mBound = false;
                Log.d(TAG, "Service Disconnected");
            }
        };

        String CLIENT_ID = this.getString(R.string.CLIENT_ID);

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
    }

    @Override
    public void onPause() {
        Log.d(TAG, "PAUSING");
        PartyApp.getInstance().getBus().unregister(this);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        PartyApp.getInstance().getBus().register(this);
        if(mBound) {
            trackAdapter.clear();
            trackAdapter.addAll(service.getQueue());
            if(service.getNowPlaying() != null) playNewSong(service.getNowPlaying());
        }
        Log.d(TAG, "RESUMING");
    }

    @Subscribe
    public void onSongAddEvent(SongAddEvent e) {
        trackAdapter.insert(e.request, e.index);
    }

    @Subscribe
    public void onSongChangeEvent(SongChangeEvent e) {
        playNewSong(e.request);
        if(!trackAdapter.isEmpty()) trackAdapter.remove(e.request);
    }

    @Subscribe
    public void onSongRemoveEvent(SongRemoveEvent e) {
        trackAdapter.remove(e.request);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.host_options, menu);
        // Locate MenuItem with ShareActionProvider
        MenuItem item = menu.findItem(R.id.action_share);
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
        //create the sharing intent
        setShareIntent("http://" + getMyHostname() + ":8000");

        //Search bar

        SearchManager searchManager = (SearchManager)
                getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchMenuItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchMenuItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d("SEARCH", "User searched for "+query);
                Intent searchIntent = new Intent(HostActivity.this, SearchActivity.class);
                searchIntent.putExtra("query", query);
                searchIntent.putExtra("HOST_URL", "http://"+getMyHostname()+":8000/add");
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_end:
                finish();
                return false;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setShareIntent(String text) {
        if (mShareActionProvider == null) return;
        Log.d("SHARE INTENT", text);
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");

        sharingIntent.putExtra(Intent.EXTRA_TEXT, text);

        //then set the sharingIntent
        mShareActionProvider.setShareIntent(sharingIntent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {

                Intent intent2 = new Intent(HostActivity.this, PartyService.class);
                intent2.putExtra("AuthToken", response.getAccessToken());
                bindService(intent2, mServiceConnection , BIND_AUTO_CREATE);

                return;
            }
            // Auhtorization failed
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "Destroying Host Activity");

        super.onDestroy();

        if(mBound) {
            unbindService(mServiceConnection);
        }
    }
}
