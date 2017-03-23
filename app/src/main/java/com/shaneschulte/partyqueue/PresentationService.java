package com.shaneschulte.partyqueue;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.google.android.gms.cast.CastPresentation;
import com.google.android.gms.cast.CastRemoteDisplayLocalService;
import com.shaneschulte.partyqueue.R;

public class PresentationService extends CastRemoteDisplayLocalService {
    private FirstScreenPresentation mPresentation;

    @Override
    public void onCreatePresentation(Display display) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            createPresentation(display);
        }
    }

    @Override
    public void onDismissPresentation() {
        dismissPresentation();
    }

    private final static class FirstScreenPresentation extends CastPresentation {

        public FirstScreenPresentation(Context context,
                                       Display display) {
            super(context, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_landing);
        }
    }

    private void dismissPresentation() {
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void createPresentation(Display display) {
        dismissPresentation();
        mPresentation = new FirstScreenPresentation(this, display);
        try {
            mPresentation.show();
        } catch (WindowManager.InvalidDisplayException ex) {
            Log.e("PresentationService", "Unable to show presentation, display was " +
                    "removed.", ex);
            dismissPresentation();
        }
    }
}
