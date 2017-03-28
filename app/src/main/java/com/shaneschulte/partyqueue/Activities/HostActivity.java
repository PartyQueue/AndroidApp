package com.shaneschulte.partyqueue.activities;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.shaneschulte.partyqueue.events.NameChangeEvent;
import com.shaneschulte.partyqueue.events.QueueResetEvent;
import com.shaneschulte.partyqueue.events.SongAddEvent;
import com.shaneschulte.partyqueue.events.SongChangeEvent;
import com.shaneschulte.partyqueue.events.SongPausePlayEvent;
import com.shaneschulte.partyqueue.events.SongRemoveEvent;
import com.shaneschulte.partyqueue.hostingservice.PartyService;
import com.shaneschulte.partyqueue.PartyApp;
import com.shaneschulte.partyqueue.R;
import com.shaneschulte.partyqueue.SongRequest;
import com.shaneschulte.partyqueue.Utils;
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
    private String username;

    private MenuItem mPP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_host);

        super.onCreate(savedInstanceState);

        username = "Host";

        listView.setLongClickable(true);
        listView.setOnItemLongClickListener((parent, v, position, id) -> {
            //Do your tasks here
            AlertDialog.Builder alert = new AlertDialog.Builder(
                    HostActivity.this);
            alert.setTitle("Remove Song");
            alert.setMessage("Are you sure you want to remove this song?");
            alert.setPositiveButton("REMOVE", (dialog, which) -> {
                SongRequest r = trackAdapter.getItem(position);
                service.removeSong(r);
                trackAdapter.remove(r);
                dialog.dismiss();
            });
            alert.setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss());

            alert.show();

            return true;
        });

        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mBound = true;
                HostActivity.this.service = ((PartyService.PartyServiceBinder)service).getService();
                synchronized(trackAdapter) {
                    trackAdapter.clear();
                    trackAdapter.addAll(HostActivity.this.service.getQueue());
                }
                if(HostActivity.this.service.getNowPlaying() != null)
                    playNewSong(HostActivity.this.service.getNowPlaying(),
                            HostActivity.this.service.getTimeRemaining());

                Log.d(TAG, "Service Connected");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mBound = false;
                Log.d(TAG, "Service Disconnected");
            }
        };

        mBound = false;
        bindService(new Intent(this, PartyService.class), mServiceConnection, 0);

        if(PartyService.hasAuth()) return;

        String CLIENT_ID = this.getString(R.string.CLIENT_ID);

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
    }


    @Override
    public void onStop() {
        Log.d(TAG, "PAUSING");
        PartyApp.getInstance().getBus().unregister(this);
        cd.cancel();
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();

        PartyApp.getInstance().getBus().register(this);
        if(mBound) {
            synchronized(trackAdapter) {
                trackAdapter.clear();
                trackAdapter.addAll(service.getQueue());
            }
            if(service.getNowPlaying() != null) playNewSong(service.getNowPlaying(), service.getTimeRemaining());
        }
        Log.d(TAG, "RESUMING");
    }

        @Subscribe
        public void onSongAddEvent (SongAddEvent e) {
            synchronized(trackAdapter) { trackAdapter.insert(e.request, e.index); }
        }

        @Subscribe
        public void onSongChangeEvent (SongChangeEvent e) {

        if (e.request == null) {
            togglePlaying(false);
            return;
        }
        playNewSong(e.request, e.request.getMeta().duration_ms);
            synchronized(trackAdapter) { if (!trackAdapter.isEmpty()) trackAdapter.remove(e.request); }
    }

        @Subscribe
        public void onSongRemoveEvent (SongRemoveEvent e){
            synchronized(trackAdapter) { trackAdapter.remove(e.request); }
    }

    @Subscribe
    public void onQueueResetEvent (QueueResetEvent e){
        synchronized(trackAdapter) { trackAdapter.clear(); }
    }

    @Subscribe
    public void onNameEvent (NameChangeEvent e){
        username = e.username;
    }

    @Subscribe
    public void onPausePlay(SongPausePlayEvent e) {
        if(mPP == null) return;
        if(e.paused) {
            mPP.setTitle(getString(R.string.play));
            cd.cancel();
            duration.setText(Utils.timeString(e.timeRemaining));
        }
        else {
            mPP.setTitle(getString(R.string.pause));
            cd.cancel();
            cd = new CountDown(e.timeRemaining).start();
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.host_options, menu);

        mPP = menu.findItem(R.id.action_pp);

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
                searchIntent.putExtra("name", username);
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
        Intent i;
        switch (item.getItemId()) {
            case R.id.action_end:
                if(mBound) {
                    mBound = false;
                    unbindService(mServiceConnection);
                    stopService(new Intent(this, PartyService.class));
                }
                finish();
                return false;

            case R.id.action_skip:
                i = new Intent(this, PartyService.class);
                i.setAction(PartyService.ACTION_NEXT);
                startService(i);
                return false;

            case R.id.action_pp:
                i = new Intent(this, PartyService.class);
                i.setAction(PartyService.ACTION_PP);
                startService(i);
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
                startService(intent2);
                bindService(intent2, mServiceConnection , 0);

                return;
            }
            // Auhtorization failed
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "Destroying Host Activity");
        if(mBound) {
            mBound = false;
            unbindService(mServiceConnection);
        }
        super.onDestroy();
    }
}
