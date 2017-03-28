package com.shaneschulte.partyqueue.activities;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.shaneschulte.partyqueue.R;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import rx.Subscription;
import rxbonjour.RxBonjour;
import rxbonjour.model.BonjourService;

public class ScannerActivity extends AppCompatActivity {

    private final String TAG = "ScannerActivity";

    private List<InetAddress> hosts;
    private List<Integer> ports;
    private ArrayAdapter<String> arrayAdapter;
    private Subscription sub;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scanner);

        List<String> services =     new ArrayList<>();
        hosts =                     new ArrayList<>();
        ports =                     new ArrayList<>();

        username = getIntent().getStringExtra("name");

        ListView listView = (ListView) findViewById(R.id.serviceList);
        arrayAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                services);
        listView.setAdapter(arrayAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {

            Intent intent = new Intent(getBaseContext(), ClientActivity.class);
            final String host_url = "http:/" + hosts.get(position) + ":" + ports.get(position);
            intent.putExtra("HOST_URL", host_url);
            intent.putExtra("name", username);

            startActivity(intent);
            finish();
        });

        sub = RxBonjour.newDiscovery(this, "_partyQueue._tcp", true)
                .subscribe(bonjourEvent -> {
                    BonjourService item = bonjourEvent.getService();
                    switch (bonjourEvent.getType()) {
                        case ADDED:
                            Log.d(TAG, "Added "+item.getName());
                            arrayAdapter.add(item.getName());
                            hosts.add(item.getHost());
                            ports.add(item.getPort());
                            break;

                        case REMOVED:
                            Log.d(TAG, "Removed "+item.getName());
                            int i = arrayAdapter.getPosition(item.getName());
                            hosts.remove(i);
                            ports.remove(i);
                            arrayAdapter.remove(item.getName());
                            break;
                    }
                }, error -> {
                    // Service discovery failed, for instance
                    Log.e(TAG, error.getMessage());
                });
        }

        @Override
        public void onDestroy() {
            sub.unsubscribe();
            super.onDestroy();
        }
    }
