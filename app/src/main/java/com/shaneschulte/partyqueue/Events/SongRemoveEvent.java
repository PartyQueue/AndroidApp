package com.shaneschulte.partyqueue.events;

import com.shaneschulte.partyqueue.SongRequest;

public class SongRemoveEvent {

    public final SongRequest request;

    public SongRemoveEvent(SongRequest request) {
        this.request = request;
    }
}
