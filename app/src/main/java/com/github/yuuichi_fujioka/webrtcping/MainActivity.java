package com.github.yuuichi_fujioka.webrtcping;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.github.clans.fab.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;


import io.skyway.Peer.ConnectOption;
import io.skyway.Peer.DataConnection;
import io.skyway.Peer.OnCallback;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerOption;

public class MainActivity extends AppCompatActivity implements Handler.Callback {

    private Peer peer;
    private DataConnection dc;
    private Handler h = new Handler(this);

    private static final int MSG_UPDATE_VIEW = 0;

    private String myId;

    private static final String TAG = "WEbRTCTest";

    private static final String API_KEY = "<API Key>";

    private Thread pingThread;


    private FloatingActionButton fabConnect;
    private FloatingActionButton fabOpen;
    private FloatingActionButton fabStart;
    private FloatingActionButton fabStop;
    private FloatingActionButton fabClose;
    private TextView idView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Log.e(TAG, thread.getName(), ex);
            }
        });
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fabConnect = (FloatingActionButton) findViewById(R.id.fabConnect);
        fabOpen = (FloatingActionButton) findViewById(R.id.fabOpen);
        fabStart = (FloatingActionButton) findViewById(R.id.fabStart);
        fabStop = (FloatingActionButton) findViewById(R.id.fabStop);
        fabClose = (FloatingActionButton) findViewById(R.id.fabClose);
        idView = (TextView) findViewById(R.id.MyId);
        statusView = (TextView) findViewById(R.id.Status);

        setUpButtons();
        updateView();
    }

    private void setUpButtons() {

        fabConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                PeerOption options = new PeerOption();
                options.debug = Peer.DebugLevelEnum.NO_LOGS;
                options.key = API_KEY;
                options.domain = "localhost";
                peer = new Peer(view.getContext(), options);
                peer.on(Peer.PeerEventEnum.CONNECTION, new OnCallback() {
                    @Override
                    public void onCallback(Object o) {

                        if (!(o instanceof DataConnection)) {
                            return;
                        }
                        dc = (DataConnection) o;
                        setUpDC();
                        h.sendEmptyMessage(MSG_UPDATE_VIEW);
                    }
                });
                peer.on(Peer.PeerEventEnum.OPEN, new OnCallback() {
                    @Override
                    public void onCallback(Object o) {
                        myId = (String) o;
                        h.sendEmptyMessage(MSG_UPDATE_VIEW);
                    }
                });
                peer.on(Peer.PeerEventEnum.ERROR, new OnCallback() {
                    @Override
                    public void onCallback(Object o) {
                        h.sendEmptyMessage(MSG_UPDATE_VIEW);
                    }
                });
                peer.on(Peer.PeerEventEnum.CLOSE, new OnCallback() {
                    @Override
                    public void onCallback(Object o) {
                        h.sendEmptyMessage(MSG_UPDATE_VIEW);
                    }
                });
            }
        });
        fabOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                peer.listAllPeers(new OnCallback() {
                    @Override
                    public void onCallback(Object o) {

                        if (!(o instanceof JSONArray)) {
                            return;
                        }
                        JSONArray peers = (JSONArray) o;
                        try {
                            for (int i = 0; i < peers.length(); i++) {
                                String peerId = peers.getString(i);
                                Log.i(TAG, "Peer: " + peerId);
                                if (peerId.equals(myId)) {
                                    continue;
                                }

                                ConnectOption option = new ConnectOption();
                                option.serialization = DataConnection.SerializationEnum.NONE; // default BINATRYとドキュメントにはあるが、指定しないとNullPointerExceptionが出る。
                                option.reliable = false;
                                dc = peer.connect(peerId, option);
                                setUpDC();
                                h.sendEmptyMessage(MSG_UPDATE_VIEW);
                                break;
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "UnExpected Peer List", e);
                        }
                    }
                });
            }
        });

        fabStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dc == null || !dc.isOpen) {
                    return;
                }
                pingThread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            loop();
                        } catch (InterruptedException e) {

                        }
                    }

                    private void loop() throws InterruptedException {
                        long lastSendAt = System.currentTimeMillis();
                        long fps = 120;
                        long coolDownTime = 1000 / fps;
                        while (!isInterrupted()) {
                            if (dc == null || !dc.isOpen) {
                                Log.e(TAG, "DC has Dead!");
                                return;
                            }
                            dc.send("ping");
                            long now = System.currentTimeMillis();
                            long sleepTime = coolDownTime - (now - lastSendAt);
                            lastSendAt = now;
                            if (sleepTime > 0) {
                                sleep(sleepTime);
                            }
                        }
                    }
                };
                pingThread.start();
                h.sendEmptyMessage(MSG_UPDATE_VIEW);
            }
        });
        fabStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pingThread == null) {
                    return;
                }
                Thread t = pingThread;
                pingThread = null;
                t.interrupt();
                h.sendEmptyMessage(MSG_UPDATE_VIEW);
            }
        });
        fabClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pingThread != null) {
                    return;
                }
                if (dc == null) {
                    return;
                }
                dc.close();
                peer.disconnect();
                dc = null;
                peer = null;
                h.sendEmptyMessage(MSG_UPDATE_VIEW);
            }
        });

    }

    private void updateView() {
        if (peer == null || peer.isDisconnected) {
            fabOpen.setEnabled(false);
            fabStart.setEnabled(false);
            fabStop.setEnabled(false);
            fabClose.setEnabled(false);


            idView.setText("None");
            statusView.setText("Not Connected");
            return;
        }

        StringBuilder status = new StringBuilder("Connected");

        fabOpen.setEnabled(true);
        fabClose.setEnabled(true);
        boolean dcIsValid = !(dc == null || !dc.isOpen);
        fabStart.setEnabled(dcIsValid && pingThread == null);
        fabStop.setEnabled(dcIsValid && pingThread != null);

        if (dcIsValid) {
            status.append("\nDC is Open");
            if (pingThread != null) {
                status.append("\nPinging");
            }
        } else {
            status.append("\nDC is Close");
        }

        idView.setText(myId);
        statusView.setText(status.toString());
    }

    private void setUpDC() {
        dc.on(DataConnection.DataEventEnum.OPEN, new OnCallback() {
            @Override
            public void onCallback(Object o) {
                Log.i(TAG, "DC is Open!");
                h.sendEmptyMessage(MSG_UPDATE_VIEW);
            }
        });
        dc.on(DataConnection.DataEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object o) {
                Log.i(TAG, "DC is Close!");
                dc = null;
                h.sendEmptyMessage(MSG_UPDATE_VIEW);
            }
        });
        dc.on(DataConnection.DataEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object o) {
                Log.i(TAG, "DC is Close!");
                dc = null;
                h.sendEmptyMessage(MSG_UPDATE_VIEW);
            }
        });
        dc.on(DataConnection.DataEventEnum.DATA, new OnCallback() {
            @Override
            public void onCallback(Object o) {
                String data = (String) o;
                if (data.contains("ping") && dc != null) {
                    dc.send(data.replaceAll("ping", "pong"));
                }
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_UPDATE_VIEW:
                updateView();
                break;
        }
        return false;
    }
}
