package com.shaneschulte.partyqueue.HostingService;

import android.content.res.Resources;

import com.shaneschulte.partyqueue.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

class AndroidWebServer extends NanoHTTPD {

    private final SongRequestManager mManager;
    private final Resources res;

    AndroidWebServer(int port, Resources res, SongRequestManager mManager) {
        super(port);
        this.res = res;
        this.mManager = mManager;
    }

    @Override
    public Response serve(IHTTPSession session) {
        switch(session.getMethod()) {


            case POST:

                if(session.getUri().equals("/remove")) {
                    // Track removal request
                    String ip, track;
                    Map<String, String> params = new HashMap<>();
                    try {
                        session.parseBody(params);
                    } catch (IOException | ResponseException e) {
                        break;
                    }
                    if(!session.getParameters().containsKey("track")) break;
                    track = session.getParameters().get("track").get(0);
                    ip = session.getRemoteIpAddress();

                    if(mManager.removeRequest(track, ip)) {
                        return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "");
                    }
                    else {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "");
                    }
                }

                if(session.getUri().equals("/add")) {

                    // Track add request
                    String addedBy, track;
                    Map<String, String> params = new HashMap<>();
                    try {
                        session.parseBody(params);
                    } catch (IOException | ResponseException e) {
                        break;
                    }

                    if(!session.getParameters().containsKey("track")) break;
                    if(session.getParameters().get("track").isEmpty()) break;
                    if(!session.getParameters().containsKey("addedBy")) addedBy = "Unknown";
                    else if(session.getParameters().get("addedBy").isEmpty()) addedBy = "Unknown";
                    else addedBy = session.getParameters().get("addedBy").get(0);
                    track = session.getParameters().get("track").get(0);

                    mManager.requestNewSong(track, addedBy, session.getRemoteIpAddress());

                    //SongRequest r = new SongRequest(track, addedBy, session.getRemoteIpAddress());
                    /*unprocessed.add(r);
                    */

                    return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "");
                }
                break;


            case GET:

                // Basic Requests
                if(session.getUri().equals("/")) {
                    return newFixedLengthResponse(Response.Status.OK, "text/html", res.openRawResource(R.raw.app), -1);
                }
                if(session.getUri().equals("/favicon.ico")) {
                    return newFixedLengthResponse(Response.Status.OK, "image/x-icon", res.openRawResource(R.raw.favicon), -1);
                }

                // Request for current queue
                if(session.getUri().equals("/queue")) {
                    JSONArray arr = new JSONArray();
                    if(mManager.getNowPlaying() != null) arr.put(mManager.getNowPlaying().json);
                    for(int i = 0; i < mManager.getQueue().size(); ++i) {
                        JSONObject o = mManager.getQueue().get(i).json;
                        if (o != null) {
                            arr.put(o);
                        }
                    }
                    return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, arr.toString());
                }
                break;
            default:
                break;
        }
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "");
    }
}