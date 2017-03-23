package com.shaneschulte.partyqueue.Activities;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRouter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.shaneschulte.partyqueue.R;
import com.shaneschulte.partyqueue.SongRequest;
import com.shaneschulte.partyqueue.TrackAdapter;
import com.shaneschulte.partyqueue.Utils;
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
    protected RelativeLayout nowPlayingLayout;
    protected RelativeLayout backgroundZone;
    protected TextView nothingQueued, songName, artistName, addedBy, duration;
    protected ImageView fadedArt, albumArt;
    protected RelativeLayout gradientZone;
    protected MenuItem searchMenuItem;
    protected SearchView searchView;
    protected TrackAdapter trackAdapter;
    protected CountDownTimer cd;

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
        duration = (TextView) findViewById(R.id.nowDuration);
        addedBy = (TextView) findViewById(R.id.nowAddedBy);
        albumArt = (ImageView) findViewById(R.id.nowImage);
        nowPlayingLayout = (RelativeLayout) findViewById(R.id.nowPlayingLayout);
        fadedArt = (ImageView) findViewById(R.id.fadedArt);
        gradientZone = (RelativeLayout) findViewById(R.id.backgroundZone);
        listView = (ListView) findViewById(R.id.lvItems);
        trackAdapter = new TrackAdapter(this, new ArrayList<>());
        listView.setAdapter(trackAdapter);

        cd = new CountDown(0);

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

    protected class CountDown extends CountDownTimer {
        protected CountDown(long millisInFuture) {
            super(millisInFuture, 1000);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            duration.setText(Utils.timeString(millisUntilFinished));
        }

        @Override
        public void onFinish() {
            duration.setText("0:00");
            timerFinished();
        }
    }

    protected void timerFinished() {}

    public void playNewSong(SongRequest r, long time) {
        //Update UI
        togglePlaying(true);
        Track track = r.getMeta();
        songName.setText(track.name);
        artistName.setText(Utils.artistString(track.artists));
        addedBy.setText(getString(R.string.addedBy, r.addedBy));
        cd.cancel();
        cd = new CountDown(time).start();
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
        valueAnimator.addUpdateListener(animator -> {
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {(Integer) animator.getAnimatedValue(),0x000});

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                gradientZone.setBackground(gd);
            }
        });
        valueAnimator.setDuration(1000);
        valueAnimator.start();
    }
}
