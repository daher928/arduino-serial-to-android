package com.daher.arduinousb;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int SERIAL_BAUD_RATE = 9600;
    private static final int ARDUINO_VENDOR_ID = 0x2341;
    private static final String END_OF_LINE = "\r\n";
    //to let our app be the default app that access USB connected devices
    public final String ACTION_USB_PERMISSION = "com.daher.arduinousb.USB_PERMISSION";

    Button startButton, stopButton;
    TextView tvData;
    String streamLine = "";

    //USB SDK Setup required classes
    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);
        startButton = (Button) findViewById(R.id.buttonStart);
        stopButton = (Button) findViewById(R.id.buttonStop);
        tvData = (TextView) findViewById(R.id.textView);
        tvData.setMovementMethod(new ScrollingMovementMethod());
        setUiEnabled(false);



    }

    @Override
    protected void onStart() {
        super.onStart();
        //Receiver to detect any usb connected device
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(broadcastReceiver);
    }

    public void setUiEnabled(boolean bool) {
        startButton.setEnabled(!bool);
        stopButton.setEnabled(bool);
        //tvData.setEnabled(bool);

    }

    public void onClickStart(View view) {
        clearTV();

        //Check connected devices and connect to the device.
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (deviceVID == ARDUINO_VENDOR_ID)//Arduino Vendor ID
                {
                    PendingIntent pi = PendingIntent
                            .getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);

                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        }
    }

    public void onClickStop(View view) {
        setUiEnabled(false);
        serialPort.close();
        tvAppend("\nSerial Connection Closed! \n");

    }
    private void tvAppend(final CharSequence text) {

        Log.d("OTGD", text.toString());

        tvData.post(new Runnable() {
                @Override
                public void run() {
//                    if (text.toString().contains("\n\r"))
//                        tvData.append("\n");
                    tvData.append(text + "\n");//setText(tvData.getText().toString() + text);
                }
            });
    }
    private void clearTV() {
        tvData.post(new Runnable() {
            @Override
            public void run() {
                tvData.setText("");
            }
        });

    }



    //--------------- USB SERIAL HANDLING LOGIC -----------------//
    //Callback for any data received from the USB device

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {

        //Here we receive the data
            try {
                String data = new String(arg0);

                streamLine += data;

                if (streamLine.contains(END_OF_LINE)){
                    String[] split = streamLine.split(END_OF_LINE);
                    String toPrint = split[0];
                    streamLine = split[1];
                    tvAppend(toPrint);
                }

//                if (data.contains("\r")){
//                    String split = data.split("\r")[0];
//                    if (!split.isEmpty()) {
//                        String split2 = streamLine.split("\n")[0];
//                        if (!split2.isEmpty()) {
//                            streamLine += split2;
//                        }
//                    } else {
//                        streamLine = streamLine.split("\n")[0];
//                    }
//                    tvAppend(MainActivity.this.streamLine);
//                    tvAppend("\n");
//                    streamLine = "";
//                }
//                else {
//                    streamLine+=data;
//                }

                //data.concat("\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
                //

        }
    };


    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {

                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    connection = usbManager.openDevice(device);

                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);

                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            setUiEnabled(true);
                            serialPort.setBaudRate(SERIAL_BAUD_RATE);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(mCallback);

                            clearTV();

                            tvAppend("Serial Connection Opened!\n");

                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                onClickStart(startButton);
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                onClickStop(stopButton);

            }
        }

        ;
    };

}
