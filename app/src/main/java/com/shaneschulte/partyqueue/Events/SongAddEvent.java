package com.shaneschulte.partyqueue.Events;

import com.shaneschulte.partyqueue.SongRequest;

public class SongAddEvent {

    public final SongRequest request;

    public SongAddEvent(SongRequest r) {
        this.request = r;
    }
}
