package com.shaneschulte.partyqueue.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.shaneschulte.partyqueue.HostingService.PartyService;
import com.shaneschulte.partyqueue.R;

public class LandingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_landing);

        if(PartyService.hasAuth()) {
            Intent toBintent = new Intent(this, HostActivity.class);
            startActivity(toBintent);
        }
    }

    public void startHost(View view) {
        Toast.makeText(this, "Starting server...", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, HostActivity.class));
    }

    public void startJoin(View view) {
        startActivity(new Intent(this, ScannerActivity.class));
    }

    public void startHelp(View view) {
        startActivity(new Intent(this, HelpActivity.class));
    }
}
