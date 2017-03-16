package com.shaneschulte.partyqueue.Events;

import com.shaneschulte.partyqueue.SongRequest;

public class SongAddEvent {

    public final SongRequest request;
    public final int index;

    public SongAddEvent(SongRequest r, int index) {
        this.request = r;
        this.index = index;
    }
}
