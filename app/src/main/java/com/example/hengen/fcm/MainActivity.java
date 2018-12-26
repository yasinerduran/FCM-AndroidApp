package com.example.hengen.fcm;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;

import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;



public class MainActivity extends AppCompatActivity {

    // GUI Components
    private TextView mBluetoothStatus;
    private TextView mReadBuffer;
    private TextView mTransmitBuffer;
    private TextView targetTemperature ;
    private TextView currentTemperature ;
    private Button mOffBtn;
    private Button mListPairedDevicesBtn;
    private Button mDiscoverBtn;
    private Button set60Btn;
    private Button set70Btn;
    private Button set80Btn;
    private Button set90Btn;
    private Button set100Btn;
    private Button statusBtn;
    private Button increaseBtn;
    private Button decreaseBtn;
    private Button controlBtn;
    public BluetoothAdapter mBTAdapter;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ListView mDevicesListView;

    private boolean controlBtnStatus = false;

    private final String TAG = MainActivity.class.getSimpleName();
    private Handler mHandler; // Our main handler that will receive callback notifications
    private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Connect UI
        Button mScanBtn = (Button) findViewById(R.id.scan);
        mDiscoverBtn = (Button) findViewById(R.id.discover);
        mListPairedDevicesBtn = (Button) findViewById(R.id.paired);
        controlBtn = (Button) findViewById(R.id.control_button);
        statusBtn = (Button) findViewById(R.id.resistance_status);
        increaseBtn = (Button) findViewById(R.id.increase_temp);
        decreaseBtn = (Button) findViewById(R.id.decrease_temp);
        set60Btn = (Button) findViewById(R.id.set60);
        set70Btn = (Button) findViewById(R.id.set70);
        set80Btn = (Button) findViewById(R.id.set80);
        set90Btn = (Button) findViewById(R.id.set90);
        set100Btn = (Button) findViewById(R.id.set100);

        targetTemperature = (TextView) findViewById(R.id.target_temp);
        currentTemperature = (TextView) findViewById(R.id.current_temp);
        mDevicesListView = (ListView)findViewById(R.id.devicesListView);

        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio
        mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);
        mBTArrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);

        // Ask for location permission if not already allowed
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        // Message Handler Input Stream is Here.
        mHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                if(msg.what == MESSAGE_READ){
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                        messageHandler(readMessage);//Handles message.
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }

                }
                if(msg.what == CONNECTING_STATUS){
                    if(msg.arg1 == 1) {
                        Context context = getApplicationContext();
                        CharSequence text = "Connected to Device: " + (String)(msg.obj);
                        int duration = Toast.LENGTH_SHORT;

                        Toast toast = Toast.makeText(context, text, duration);
                        toast.show();
                    }
                    else{
                        Context context = getApplicationContext();
                        CharSequence text = "Connection Failed!";
                        int duration = Toast.LENGTH_SHORT;
                        Toast toast = Toast.makeText(context, text, duration);
                        toast.show();
                    }
                }
            }
        };


        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            //mBluetoothStatus.setText("Status: Bluetooth not found");
            Toast.makeText(getApplicationContext(),"Bluetooth device not found!",Toast.LENGTH_SHORT).show();
        }
        else {
            // System On Off button
            controlBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    //First check to make sure thread created
                    if(mConnectedThread != null) {
                        if(controlBtnStatus){
                            messageHandler("SET-SYSTEM-OFF");
                            //mConnectedThread.write("OFF");
                            controlBtn.setText("OFF");
                            controlBtnStatus = false;
                        }
                        else {
                            messageHandler("SET-SYSTEM-ON");
                            //mConnectedThread.write("ON");
                            controlBtn.setText("ON");
                            controlBtnStatus = true;
                        }
                    }
                }
            });

            // Increase Temperature Listener.
            increaseBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    int temp = Integer.parseInt(targetTemperature.getText().toString());
                    temp +=1;
                    targetTemperature.setText(String.valueOf(temp));
                    messageHandler("SET-TARGET_TEMPERATURE-"+targetTemperature.getText());
                }
            });

            // Decrease Temperature Listener.
            decreaseBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    int temp = Integer.parseInt(targetTemperature.getText().toString());
                    temp -=1;
                    targetTemperature.setText(String.valueOf(temp));
                    messageHandler("SET-TARGET_TEMPERATURE-"+targetTemperature.getText());
                }
            });

            // Buttons set direcly chance temperature.
            set60Btn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    targetTemperature.setText("60");
                    messageHandler("SET-TARGET_TEMPERATURE-"+targetTemperature.getText());
                }
            });
            set70Btn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    targetTemperature.setText("70");
                    messageHandler("SET-TARGET_TEMPERATURE-"+targetTemperature.getText());
                }
            });
            set80Btn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    targetTemperature.setText("80");
                    messageHandler("SET-TARGET_TEMPERATURE-"+targetTemperature.getText());
                }
            });
            set90Btn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    targetTemperature.setText("90");
                    messageHandler("SET-TARGET_TEMPERATURE-"+targetTemperature.getText());
                }
            });
            set100Btn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    targetTemperature.setText("100");
                    messageHandler("SET-TARGET_TEMPERATURE-"+targetTemperature.getText());
                }
            });


            mScanBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    bluetoothOn(v);
                }
            });

            /*
            mOffBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    bluetoothOff(v);
                }
            });
            */


            // List paired devices
            mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    listPairedDevices(v);
                }
            });

            // Discovering
            mDiscoverBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    discover(v);
                }
            });
        }
    }

    public void bluetoothOn(View view){
        if (!mBTAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            //mBluetoothStatus.setText("Bluetooth enabled");
            Toast.makeText(getApplicationContext(),"Bluetooth turned on",Toast.LENGTH_SHORT).show();

        }
        else{
            Toast.makeText(getApplicationContext(),"Bluetooth is already on", Toast.LENGTH_SHORT).show();
        }
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data){
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                //mBluetoothStatus.setText("Enabled");
            }
            //else
                //mBluetoothStatus.setText("Disabled");
        }
    }

    public void bluetoothOff(View view){
        mBTAdapter.disable(); // turn off
        //mBluetoothStatus.setText("Bluetooth disabled");
        Toast.makeText(getApplicationContext(),"Bluetooth turned Off", Toast.LENGTH_SHORT).show();
    }

    public void discover(View view){
        // Check if the device is already discovering
        if(mBTAdapter.isDiscovering()){
            mBTAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(),"Discovery stopped",Toast.LENGTH_SHORT).show();
        }
        else{
            if(mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear(); // clear items
                mBTAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();
                registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            }
            else{
                Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
            }
        }
    }

    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    public void listPairedDevices(View view){
        mPairedDevices = mBTAdapter.getBondedDevices();
        if(mBTAdapter.isEnabled()) {
            // put it's one to the adapter
            for (BluetoothDevice device : mPairedDevices)
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());

            Toast.makeText(getApplicationContext(), "Show Paired Devices", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            if(!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                return;
            }

            //mBluetoothStatus.setText("Connecting...");
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0,info.length() - 17);

            // Spawn a new thread to avoid blocking the GUI one
            new Thread()
            {
                public void run() {
                    boolean fail = false;

                    BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

                    try {
                        mBTSocket = createBluetoothSocket(device);
                    } catch (IOException e) {
                        fail = true;
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }
                    // Establish the Bluetooth socket connection.
                    try {
                        mBTSocket.connect();
                    } catch (IOException e) {
                        try {
                            fail = true;
                            mBTSocket.close();
                            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                    .sendToTarget();
                        } catch (IOException e2) {
                            //insert code to deal with this
                            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    if(fail == false) {
                        mConnectedThread = new ConnectedThread(mBTSocket);
                        mConnectedThread.start();

                        mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                                .sendToTarget();
                    }
                }
            }.start();
        }
    };

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BTMODULEUUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
        }
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if(bytes != 0) {
                        buffer = new byte[1024];
                        SystemClock.sleep(100); //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available(); // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget(); // Send the obtained bytes to the UI activity
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            input+="\n";
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    // When input stream completed, emerging data separates for significance level.
    public void messageHandler(String message){
        String[] seperated = message.split("-");
        switch (seperated[0]){
            case "SET":
                switch (seperated[1]){
                        // Phone -> Machine Feature
                    case "TARGET_TEMPERATURE":
                        mConnectedThread.write("SET-TARGET_TEMPERATURE-"+targetTemperature.getText());
                        break;

                        // Phone -> Machine
                    case "SYSTEM":
                        mConnectedThread.write(message);
                        break;

                        // Machine -> Phone Feature
                    case "ACTIVITY_RESISTANCE":
                        if(seperated[2] == "ON")
                            statusBtn.setBackgroundColor(Color.RED);
                        else
                            statusBtn.setBackgroundColor(Color.GRAY);
                        break;

                        // Machine -> Phone Feature
                    case "CURRENT_TEMPERATURE":
                        currentTemperature.setText(seperated[2]);
                        break;

                        // Machine -> Phone Feature
                    case "TOAST":
                        Toast.makeText(getApplicationContext(),seperated[2],Toast.LENGTH_SHORT).show();
                        break;

                    default:
                        break;
                }
                break;
            case "GET":
                switch (seperated[1]){
                        //  Machine -> Phone
                    case "TARGET_TEMPERATURE":
                        mConnectedThread.write("SET-TARGET_TEMPERATURE-"+targetTemperature.getText());
                        break;

                        //  Machine -> Phone
                    case "TEST":
                        mConnectedThread.write("SET-TEST-OK");
                        break;
                    default:
                        break;
                }
            default:
                break;
        }

    }

}
