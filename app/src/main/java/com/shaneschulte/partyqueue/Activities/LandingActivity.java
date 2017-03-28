package com.shaneschulte.partyqueue.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.shaneschulte.partyqueue.hostingservice.PartyService;
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter your name:");

// Set up the input
        final EditText input = new EditText(this);
// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

// Set up the buttons
        builder.setPositiveButton("OK", (dialog, which) -> startActivity(new Intent(this, ScannerActivity.class).putExtra("name", input.getText().toString())));
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    public void startHelp(View view) {
        startActivity(new Intent(this, HelpActivity.class));
    }
}
