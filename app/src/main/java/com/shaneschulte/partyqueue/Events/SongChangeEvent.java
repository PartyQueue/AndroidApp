package com.shaneschulte.partyqueue.Events;

import com.shaneschulte.partyqueue.SongRequest;

public class SongChangeEvent {

    public final SongRequest request;

    public SongChangeEvent(SongRequest r) {
        this.request = r;
    }
}
