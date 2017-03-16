package com.shaneschulte.partyqueue.Events;

import com.shaneschulte.partyqueue.SongRequest;

public class SongRemoveEvent {

    public final SongRequest request;

    public SongRemoveEvent(SongRequest request) {
        this.request = request;
    }
}
