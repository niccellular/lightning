
package com.atakmap.android.lightning;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.lightning.plugin.R;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;

import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class LightningDropDownReceiver extends DropDownReceiver implements
        OnStateListener {

    public static final String TAG = "Lightning";

    public static final String SHOW_PLUGIN = "com.atakmap.android.lightning.SHOW_PLUGIN";
    private final View templateView;
    private final Context pluginContext;

    private WebSocket webSocket;
    private OkHttpClient client;

    private Button start;
    private int toggle = 0;
    private Float distance;
    private SharedPreferences sharedPreference;
    /**************************** CONSTRUCTOR *****************************/

    public LightningDropDownReceiver(final MapView mapView,
            final Context context) {
        super(mapView);
        this.pluginContext = context;

        // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
        // In this case, using it is not necessary - but I am putting it here to remind
        // developers to look at this Inflator
        templateView = PluginLayoutInflater.inflate(context,
                R.layout.main_layout, null);

        start = templateView.findViewById(R.id.btn);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((toggle++ % 2) == 0) {
                    Start();
                    start.setText("Stop");
                } else {
                    Stop();
                    start.setText("Start");
                }
            }
        });
        sharedPreference = PreferenceManager.getDefaultSharedPreferences(mapView.getContext().getApplicationContext());
        distance = Float.valueOf(sharedPreference.getString("plugin_nimb_distance", "1000"));
    }

    /**************************** PUBLIC METHODS *****************************/

    public void disposeImpl() {
    }

    public static String decode(String b) {
        String a;
        HashMap<Integer, String> e = new HashMap<>();
        List<String> g = new ArrayList<>();
        int h = 256;
        int o = h;
        char[] d = b.toCharArray();
        char c = d[0];
        String f = String.valueOf(c);
        g.add(f);
        for (int i = 1; i < d.length; i++) {
            int ascii = (int) d[i];
            a = h > ascii ? String.valueOf(d[i]) : e.containsKey(ascii) ? e.get(ascii) : f + c;
            g.add(a);
            c = a.charAt(0);
            e.put(o, f + c);
            o++;
            f = a;
        }
        StringBuilder result = new StringBuilder();
        for (String str : g) {
            result.append(str);
        }
        return result.toString();
    }

    private void Start() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        client = builder.build();
        Request request = new Request.Builder()
                .url("wss://ws1.blitzortung.org")
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {

            private static final int NORMAL_CLOSURE_STATUS = 1000;
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send("{\"a\":111}");
                Log.i("WebSockets", "Connection accepted!");
            }
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                //Log.d(TAG, decode(text));

                JSONObject jsonObject = null;
                try {
                    jsonObject = new JSONObject(decode(text));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                Double lat = null;
                Double lng = null;
                try {

                    lat = jsonObject.getDouble("lat");
                    lng = jsonObject.getDouble("lon");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                GeoPoint gp = new GeoPoint(lat, lng);
                distance = Float.valueOf(sharedPreference.getString("plugin_lightning_distance", "1000"));
                Log.d(TAG, String.format("DistanceTo: %f", getMapView().getSelfMarker().getPoint().distanceTo(gp)));
                if (getMapView().getSelfMarker().getPoint().distanceTo(gp) > distance) {
                    return;
                }

                CotEvent cotEvent = new CotEvent();

                CoordinatedTime time = new CoordinatedTime();
                cotEvent.setTime(time);
                cotEvent.setStart(time);
                cotEvent.setStale(time.addMinutes(1));

                cotEvent.setUID(UUID.randomUUID().toString());

                cotEvent.setType("a-n-G-E-S");

                cotEvent.setHow("m-g");

                CotPoint cotPoint = new CotPoint(lat, lng, CotPoint.UNKNOWN,
                        CotPoint.UNKNOWN, CotPoint.UNKNOWN);
                cotEvent.setPoint(cotPoint);

                CotDetail cotDetail = new CotDetail("detail");
                CotDetail cotContact = new CotDetail("contact");
                cotContact.setAttribute("callsign","Lightning Strike");
                cotDetail.addChild(cotContact);

                cotEvent.setDetail(cotDetail);

                CotDetail cotRemark = new CotDetail("remarks");
                cotRemark.setAttribute("source", "blitzortung lightning plugin");
                //cotRemark.setInnerText(String.format(Locale.US, "RAW: %s", decode(text)));

                cotDetail.addChild(cotRemark);

                if (cotEvent.isValid())
                    CotMapComponent.getInternalDispatcher().dispatch(cotEvent);
                else
                    Log.e(TAG, "cotEvent was not valid");
            }
            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
            }
            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(NORMAL_CLOSURE_STATUS, null);
                Log.i("WebSockets", "Closing : " + code + " / " + reason);
            }
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.i("WebSockets", "Error : " + t.getMessage());
        }});
    }

    private void Stop() {
        webSocket.close(1000, "OK");
    }


    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(SHOW_PLUGIN)) {

            Log.d(TAG, "showing plugin drop down");
            showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this);


        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }

}
