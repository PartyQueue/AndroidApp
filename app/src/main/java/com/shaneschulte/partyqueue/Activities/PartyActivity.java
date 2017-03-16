package com.shaneschulte.partyqueue.Activities;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.shaneschulte.partyqueue.Events.SongChangeEvent;
import com.shaneschulte.partyqueue.PartyApp;
import com.shaneschulte.partyqueue.R;
import com.shaneschulte.partyqueue.SongRequest;
import com.shaneschulte.partyqueue.TrackAdapter;
import com.shaneschulte.partyqueue.Utils;
import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.ArrayList;

import kaaes.spotify.webapi.android.models.Track;

public abstract class PartyActivity extends AppCompatActivity {

    static protected String __hostname;

    protected Target target;



    //UI Garbage
    protected int oldColor;
    protected ListView listView;
    //protected Chronometer duration;
    protected RelativeLayout nowPlayingLayout;
    protected RelativeLayout backgroundZone;
    protected TextView nothingQueued, songName, artistName, addedBy;
    protected ImageView fadedArt, albumArt;
    protected RelativeLayout gradientZone;
    protected MenuItem searchMenuItem;
    protected SearchView searchView;
    protected TrackAdapter trackAdapter;

    protected String getMyHostname() {
        if(__hostname != null) return __hostname;
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        __hostname = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        return __hostname;
    }

    private boolean checkWifi() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) return false; // Wi-Fi adapter is OFF
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        return !(wifiInfo == null || wifiInfo.getNetworkId() == -1);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkWifi()) { //App can't work without wifi connection
            Toast.makeText(this, "Requires WiFi Connection", Toast.LENGTH_LONG).show();
            finish();
        }

        oldColor = 0;

        nothingQueued = (TextView) findViewById(R.id.nothingPlaying);
        artistName = (TextView) findViewById(R.id.nowArtist);
        songName = (TextView) findViewById(R.id.nowTitle);
        //duration = (Chronometer) findViewById(R.id.nowDuration);
        addedBy = (TextView) findViewById(R.id.nowAddedBy);
        albumArt = (ImageView) findViewById(R.id.nowImage);
        nowPlayingLayout = (RelativeLayout) findViewById(R.id.nowPlayingLayout);
        fadedArt = (ImageView) findViewById(R.id.fadedArt);
        gradientZone = (RelativeLayout) findViewById(R.id.backgroundZone);
        listView = (ListView) findViewById(R.id.lvItems);
        trackAdapter = new TrackAdapter(this, new ArrayList<>());
        listView.setAdapter(trackAdapter);


        target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                updateAesthetics(bitmap);
            }

            @Override
            public void onBitmapFailed(Drawable errorDrawable) {
                Log.e("Picasso","Download of a bitmap failed");
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {}
        };
    }

    public void togglePlaying(boolean on) {
        if(on) {
            nothingQueued.setVisibility(View.INVISIBLE);
            nowPlayingLayout.setVisibility(View.VISIBLE);
            listView.setVisibility(View.VISIBLE);
        }
        else {
            nothingQueued.setVisibility(View.VISIBLE);
            nowPlayingLayout.setVisibility(View.INVISIBLE);
            listView.setVisibility(View.INVISIBLE);
        }
    }

    public void playNewSong(SongRequest r) {
        //Update UI
        togglePlaying(true);
        Track track = r.getMeta();
        songName.setText(track.name);
        artistName.setText(Utils.artistString(track.artists));
        addedBy.setText(getString(R.string.addedBy, r.addedBy));
        //duration.setBase(SystemClock.elapsedRealtime() + track.duration_ms);
        //duration.start();
        Picasso.with(this).load(track.album.images.get(0).url).into(target);
    }

    protected void updateAesthetics(Bitmap result) {
        albumArt.setImageBitmap(result);
        Bitmap result2 = Utils.fastblur(result, 0.35f, 7);
        fadedArt.setImageBitmap(result2);
        int c1 = Palette.from(result).generate().getVibrantColor(Color.BLACK);
        float[] hsv = new float[3];
        Color.colorToHSV(c1, hsv);
        hsv[1] = Math.min(hsv[1], 0.5f);
        hsv[2] = Math.min(Math.max(hsv[1], 0.2f), 0.6f);
        int c2 = Color.HSVToColor(hsv);
        final ValueAnimator valueAnimator = ValueAnimator.ofObject(new ArgbEvaluator(),
                oldColor,
                c2);
        oldColor = c2;
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(final ValueAnimator animator) {
                GradientDrawable gd = new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {(Integer) animator.getAnimatedValue(),0x000});

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    gradientZone.setBackground(gd);
                }
            }

        });
        valueAnimator.setDuration(1000);
        valueAnimator.start();
    }
}
