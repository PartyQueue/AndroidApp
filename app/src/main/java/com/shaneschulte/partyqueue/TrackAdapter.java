package com.shaneschulte.partyqueue;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;

import kaaes.spotify.webapi.android.models.Track;

public class TrackAdapter extends ArrayAdapter<SongRequest> {

    private Resources res;

    /**
     * Adapts SongRequests for display in a ListView.
     * @param context Context of the ListView
     * @param reqs Container of SongRequests
     */
    public TrackAdapter(Context context, ArrayList<SongRequest> reqs) {
        super(context, 0, reqs);
        res = context.getResources();
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        // Check if an existing view is being reused, otherwise inflate the view
        if(convertView == null)
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.queued_song, parent, false);

        // Get the data item for this position
        SongRequest r = getItem(position);
        if(r == null) return convertView;
        Track track = r.getMeta();

        // Lookup view for data population
        TextView song       = (TextView) convertView.findViewById(R.id.title);
        TextView artist     = (TextView) convertView.findViewById(R.id.artist);
        TextView addedBy    = (TextView) convertView.findViewById(R.id.addedBy);
        TextView duration   = (TextView) convertView.findViewById(R.id.duration);
        ImageView art       = (ImageView)convertView.findViewById(R.id.list_image);

        // Populate the data into the template view using the data object
        song.setText(track.name);
        artist.setText(Utils.artistString(track.artists));
        addedBy.setText(r.addedBy.equals("") ? "" : res.getString(R.string.addedBy, r.addedBy));
        duration.setText(Utils.timeString(track.duration_ms));

        // Download album art
        String url = track.album.images.get(1).url;
        Picasso.with(getContext()).load(url).placeholder(R.drawable.solid_bg).into(art);

        // Return the completed view to render on screen
        return convertView;
    }
}