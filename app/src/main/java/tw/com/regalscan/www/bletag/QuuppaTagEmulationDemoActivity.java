/**
 * Quuppa Android Tag Emulation Demo application.
 * <p/>
 * Copyright 2015 Quuppa Oy
 * <p/>
 * Disclaimer
 * THE SOURCE CODE, DOCUMENTATION AND SPECIFICATIONS ARE PROVIDED “AS IS”. ALL LIABILITIES, WARRANTIES AND CONDITIONS, EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION TO THOSE CONCERNING MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT
 * OF THIRD PARTY INTELLECTUAL PROPERTY RIGHTS ARE HEREBY EXCLUDED.
 */
package tw.com.regalscan.www.bletag;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;


import java.util.Arrays;


public class QuuppaTagEmulationDemoActivity extends Activity implements View.OnClickListener {
    /** For logging */
    private static final String TAG = "QuuppaTagEmulDemoActiv";
    /** Name of the stored preferences */
    public static final String PREFS_NAME = "MyPrefsFile";
    /** Name of the stored manual tag id */
    public static final String PREFS_MANUAL_TAG_ID = "tagID";
    /** Name of the stored use-device-mac flag */
    public static final String PREFS_USE_DEVICE_MAC = "useDeviceMac";
    /** name of the stored advertizing mode pref */
    public static final String ADV_MODE = "advMode";
    /** name of the stored advertizing tx power pref */
    public static final String ADV_TX_POWER = "advTxPower";
    /** Display names of the Adventizing Modes */
    private final String[] advModes = new String[]{"ADVERTISE_MODE_LOW_POWER", "ADVERTISE_MODE_BALANCED", "ADVERTISE_MODE_LOW_LATENCY"};
    /** Display names of the various TX power settings */
    private final String[] advTxPowers = new String[]{"ADVERTISE_TX_POWER_ULTRA_LOW", "ADVERTISE_TX_POWER_LOW", "ADVERTISE_TX_POWER_MEDIUM", "ADVERTISE_TX_POWER_HIGH"};

    /** reference to the #BluetoothLeAdvertiser */
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    /** reference to the custom UI view that renders the pulsing Q */
    private PulsingQView pulsingView;
    /** flag indicating whether Direction Packet advertising has been started */
    private boolean dfPacketAdvRunning;
    /** flag indicating whether Data Packet advertising has been started */
    private boolean dataPacketAdvRunning;
    /** incrementing counter for Data Packets */
    private byte dataPacketCounter = 0;
    /** Object for synchronization */
    private final Object dataPacketLock = new Object();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);
        pulsingView = (PulsingQView) findViewById(R.id.pulsingQView);
        // make this listen to view's clicks...
        pulsingView.setOnClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_setTagID:
                setTagId();
                return true;
            case R.id.action_setAdvMode:
                setAdvMode();
                return true;
            case R.id.action_setAdvTxPower:
                setAdvTxPower();
                return true;
            case R.id.action_showAbout:
                //Intent intent = new Intent(this, AboutScreenActivity.class);
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle("Quuppa Tag Emulation Demo app");
                alert.setMessage("Version 1.4, Copyright 2015 Quuppa Oy");

                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                });
                alert.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    public void onClick(View v) {
        if (pulsingView.equals(v))
            toggleDFPacketAdv();
    }

    /**
     * Shows a dialog for the user to set the Tag ID and persists that to Android Shared Preferences
     */
    private void setTagId() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Quuppa Tag ID");

        LayoutInflater inflater = this.getLayoutInflater();
        final View view = inflater.inflate(R.layout.set_tag_id_dialog, null);
        builder.setView(view);

        final RadioGroup tagIdGroup = (RadioGroup) view.findViewById(R.id.tagIdGroup);
        final RadioButton useDeviceMac = (RadioButton) view.findViewById(R.id.useDeviceMac);
        final RadioButton useManualID = (RadioButton) view.findViewById(R.id.useManuallyDefined);
        final EditText manualID = (EditText) view.findViewById(R.id.manualID);
        final EditText deviceMac = (EditText) view.findViewById(R.id.deviceMac);

        tagIdGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i == R.id.useManuallyDefined) {
                    manualID.setEnabled(true);
                } else {
                    manualID.setEnabled(false);
                }
            }
        });

        deviceMac.setText(getMACAddress().id);
        deviceMac.setEnabled(false);
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        manualID.setText(settings.getString(PREFS_MANUAL_TAG_ID, "123456789012"));
        tagIdGroup.check(settings.getBoolean(PREFS_USE_DEVICE_MAC, true) ? R.id.useDeviceMac : R.id.useManuallyDefined);

        // add buttons
        builder.setPositiveButton("Ok", null);
        builder.setNegativeButton("Cancel", null);

        final AlertDialog dialog = builder.create();
        dialog.show();

        //Overriding the handler immediately so that we can do validation and control dismissal of the dialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (useManualID.isChecked()) {
                    String tagID = manualID.getText().toString();
                    if (tagID.length() != 12) {
                        Toast.makeText(QuuppaTagEmulationDemoActivity.this, "Tag ID must be 12 characters!", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!tagID.matches("[a-f0-9]{12}")) {
                        Toast.makeText(QuuppaTagEmulationDemoActivity.this, "Tag ID must be only hex characters! [0-9,a-f]", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                // persist!
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PREFS_MANUAL_TAG_ID, manualID.getText().toString());
                editor.putBoolean(PREFS_USE_DEVICE_MAC, useDeviceMac.isChecked());
                editor.apply();

                if (dfPacketAdvRunning) {
                    toggleDFPacketAdv();
                    toggleDFPacketAdv();
                }
                dialog.dismiss();
            }
        });
    }


    /**
     * Shows a dialog for the user to set the Adventising Mode and persists that to Android Shared Preferences
     */
    private void setAdvMode() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        int mode = settings.getInt(ADV_MODE, 0);

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Choose advertisement mode");
        alert.setMessage("Please refer to the Android spec to find out what the values mean.");
        final Spinner dropdown = new Spinner(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, advModes);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(mode);
        alert.setView(dropdown);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                int value = dropdown.getSelectedItemPosition();
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt(ADV_MODE, value);
                editor.apply();
                if (dfPacketAdvRunning) {
                    toggleDFPacketAdv();
                    toggleDFPacketAdv();
                }
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });
        alert.show();
    }


    /**
     * Shows a dialog for the user to set the Adventising TX Power and persists that to Android Shared Preferences
     */
    private void setAdvTxPower() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        int mode = settings.getInt(ADV_TX_POWER, 0);

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Choose advertisement TX power");
        alert.setMessage("Please refer to the Android spec to find out what the values mean.");
        final Spinner dropdown = new Spinner(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, advTxPowers);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(mode);
        alert.setView(dropdown);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                int value = dropdown.getSelectedItemPosition();
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt(ADV_TX_POWER, value);
                editor.apply();
                if (dfPacketAdvRunning) {
                    toggleDFPacketAdv();
                    toggleDFPacketAdv();
                }
            }
        });
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });
        alert.show();
    }


    /** Creates a byte array with the given tag ID */
    private byte[] createQuuppaAddress(String tagID) {
        byte[] bytes = new byte[6];
        bytes[0] = (byte) Integer.parseInt(tagID.substring(0, 2), 16);
        bytes[1] = (byte) Integer.parseInt(tagID.substring(2, 4), 16);
        bytes[2] = (byte) Integer.parseInt(tagID.substring(4, 6), 16);
        bytes[3] = (byte) Integer.parseInt(tagID.substring(6, 8), 16);
        bytes[4] = (byte) Integer.parseInt(tagID.substring(8, 10), 16);
        bytes[5] = (byte) Integer.parseInt(tagID.substring(10, 12), 16);
        return bytes;
    }

    /** Constructs a byte array using the Data Packet Specification.
     * Please see the 'Specification of Quuppa Tag Emulation using Bluetooth Wireless Technology' -document for more details.
     * @param button1 boolean to raise one bit in the packet
     * @param tagID Tag ID to be injected into the packet
     * @param counter counter value to be injected into the packet
     * @return constructed byte array
     */
    private byte[] createBytesWithQuuppaDataPacket(boolean button1, TagID tagID, byte counter) {
        // Please see the 'Specification of Quuppa Tag Emulation using Bluetooth Wireless Technology' -document for details how to create header
        byte header = counter;
        // bake in the Quuppa Tag ID Type
        if (tagID.isDeviceMac)
            header |= (byte) (2 << 4);  // Quuppa Tag ID Type: 0x01 = "Copy of the public Bluetooth Device Address of the Quuppa Tag (should be globally unique)"
        else
            header |= (byte) (1 << 4);  // 0x1 = "Generated by the SW developer (no guarantees for being globally unique)"

        byte[] bytes = new byte[]{
                (byte) 0xF0, // Quuppa Packet ID: Quuppa Data Packet
                (byte) header, // Payload header
                (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, // Quuppa Address payload, will be replaced shortly...
                // Payload, 16 octets
                (byte) 0xFF, // first byte: Developer Specific Data
                (byte) 0x1, (byte) 0x0, // Developer Specific ID
                (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0
        };

        // inject Quuppa Address into byte array
        byte[] qAddress = createQuuppaAddress(tagID.id);
        System.arraycopy(qAddress, 0, bytes, 2, 6);

        // inject button flags to array
        if (button1)
            bytes[11] |= 0x1;  // raise button 1 bit

        return bytes;
    }


    /** Constructs a byte array using the Direction Finding Packet Specification.
     * Please see the 'Specification of Quuppa Tag Emulation using Bluetooth Wireless Technology' -document for more details.
     * @param tagID Tag ID to be injected into the packet
     * @param mode One of the values of AdvertiseSettings.ADVERTISE_MODE_*. The value is injected in the DF Packet as indication of transmit rate.
     * @param txPower One of the values of AdvertiseSettings.ADVERTISE_TX_*. The value is injected in the DF Packet as indication of transmit power.
     * @return constructed byte array
     */
    private byte[] createBytesWithQuuppaDFPacket(TagID tagID, int mode, int txPower) {
        // Please see the 'Specification of Quuppa Tag Emulation using Bluetooth Wireless Technology' -document for details
        // bake in the Quuppa Tag ID Type
        byte header = 0;
        if (tagID.isDeviceMac)
            header = (byte) (2 << 4);  // Quuppa Tag ID Type: 0x01 = "Copy of the public Bluetooth Device Address of the Quuppa Tag (should be globally unique)"
        else
            header = (byte) (1 << 4);  // 0x1 = "Generated by the SW developer (no guarantees for being globally unique)"

        // bake in the TX power...
        if (txPower == AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)
            header |= (0); // NOP in fact...
        else if (txPower == AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            header |= (1 << 2);
        else if (txPower == AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            header |= (2 << 2);
        else if (txPower == AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            header |= (3 << 2);
        // bake in the TX rate...
        if (mode == AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)  // about 1hz
            header |= 0; // NOP in fact...
        else if (mode == AdvertiseSettings.ADVERTISE_MODE_BALANCED)  // about 4hz
            header |= 1;
        else if (mode == AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // about 8Hz
            header |= 2;

        byte[] bytes = new byte[]{
                (byte) 0x01, // Quuppa Packet ID
                (byte) 0x21, // Device Type (0x21, android)
                header, // Payload header
                (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, // Quuppa Address payload, will be replaced shortly...
                (byte) 0xb4, // checksum, calculated later
                (byte) 0x67, (byte) 0xF7, (byte) 0xDB, (byte) 0x34, (byte) 0xC4, (byte) 0x03, (byte) 0x8E, (byte) 0x5C, (byte) 0x0B, (byte) 0xAA, (byte) 0x97, (byte) 0x30, (byte) 0x56, (byte) 0xE6 // DF field, 14 octets
        };

        // inject Quuppa Address into byte array
        byte[] qAddress = createQuuppaAddress(tagID.id);
        System.arraycopy(qAddress, 0, bytes, 3, 6);

        // calculate CRC and inject
        try {
            bytes[9] = CRC8.simpleCRC(Arrays.copyOfRange(bytes, 1, 9));
        } catch (Exception e) {
            Log.d(TAG, "CRC failed");
            return null;
        }
        return bytes;
    }

    private TagID getTagId() {
        // first check that the tag id is already set
        TagID tag = new TagID();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        if (settings.getBoolean(PREFS_USE_DEVICE_MAC, true)) {
            try {
                tag = getMACAddress();
            } catch (Exception e) {
                tag.id = "123456789012";
            }
            return tag;
        } else {
            tag.id = settings.getString(PREFS_MANUAL_TAG_ID, "123456789012");
            tag.isDeviceMac = false;
        }
        return tag;
    }

    private TagID getMACAddress() {
        // try to read the mac address
        TagID tag = new TagID();
        tag.id = BluetoothAdapter.getDefaultAdapter().getAddress();
        tag.id = tag.id.replace(":", "").toLowerCase();
        tag.isDeviceMac = true;
        return tag;
    }

    /**
     * Toggles the Direction Finding Packet broadcast on/off.
     */
    private void toggleDFPacketAdv() {
        if (!dfPacketAdvRunning) {
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            TagID tagID = getTagId();
            if (tagID == null) {
                Toast.makeText(QuuppaTagEmulationDemoActivity.this, "Tag ID not set, please enter it in the settings!", Toast.LENGTH_LONG).show();
                return;
            }
            int mode = settings.getInt(ADV_MODE, AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
            int tx = settings.getInt(ADV_TX_POWER, AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
            byte[] bytes = createBytesWithQuuppaDFPacket(tagID, mode, tx);
            if (bytes == null) {
                Toast.makeText(QuuppaTagEmulationDemoActivity.this, "Failed to create Address Payload!", Toast.LENGTH_LONG).show();
                return;
            }
            dfPacketAdvRunning = startAdvertising(mode, tx, bytes, dfPacketAdvCallback);
        } else {
            stopAdvertising(dfPacketAdvCallback);
            dfPacketAdvRunning = false;
            pulsingView.setIsPulsing(false);
        }
    }

    /**
     * Starts Data Packet advertising. This demonstrates how to send Developer Specific Data within Quuppa Data Packet advertisements.
     */
    private void startDataPacketAdv() {
        synchronized (dataPacketLock) {
            if (dataPacketAdvRunning) {
                stopDataPacketAdv();
            }

            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            TagID tagID = getTagId();
            if (tagID == null) {
                Toast.makeText(QuuppaTagEmulationDemoActivity.this, "Tag ID not set, please enter it in the settings!", Toast.LENGTH_LONG).show();
                return;
            }
            int mode = settings.getInt(ADV_MODE, AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
            int tx = settings.getInt(ADV_TX_POWER, AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
            dataPacketCounter++;
            if (dataPacketCounter > 15)
                dataPacketCounter = 0;
            // this is where we set one bit to true to be sent as Developer Specific Data.
            byte[] bytes = createBytesWithQuuppaDataPacket(true, tagID, dataPacketCounter);
            if (bytes == null) {
                Toast.makeText(QuuppaTagEmulationDemoActivity.this, "Failed to create Status Payload!", Toast.LENGTH_LONG).show();
                return;
            }
            dataPacketAdvRunning = startAdvertising(mode, tx, bytes, dataPacketAdvCallback);
        }
    }


    /**
     * Stops Data Packet advertising.
     */
    private void stopDataPacketAdv() {
        synchronized (dataPacketLock) {
            if (dataPacketAdvRunning) {
                stopAdvertising(dataPacketAdvCallback);
                dataPacketAdvRunning = false;
            }
        }
    }

    /**
     * Stops Advertising for given callback instance.
     * @param callback AdvertiseCallback instance to be stopped.
     */
    private void stopAdvertising(AdvertiseCallback callback) {
        if (bluetoothLeAdvertiser == null)
            return;
        bluetoothLeAdvertiser.stopAdvertising(callback);
    }

    /**
     * Starts Advertising using given callback instance.
     * @param mode One of the values in AdvertiseSettings.
     * @param txPower One of the values in AdvertiseSettings.
     * @param bytes Bytes to be advertized.
     * @param callback Callback that gives ID to the advertising instance.
     * @return true, if requesting for Advertiser was successfull, false otherwise. Please also check the callback status to see if advertiser is really running.
     */
    private boolean startAdvertising(int mode, int txPower, byte[] bytes, AdvertiseCallback callback) {
        try {
            BluetoothManager btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter btAdapter = btManager.getAdapter();
            if (btAdapter.isEnabled()) {
                AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                        .setAdvertiseMode(mode)
                        .setTxPowerLevel(txPower)
                        .setConnectable(true)
                        .build();

                AdvertiseData advertisementData = new AdvertiseData.Builder()
                        .setIncludeTxPowerLevel(false)
                        .addManufacturerData(0x00C7, bytes)
                        .build();

                bluetoothLeAdvertiser = btAdapter.getBluetoothLeAdvertiser();
                bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertisementData, callback);
            } else {
                final String message = "Start Bluetooth failed. Maybe turn it on?";
                Log.d("AdvCallback", message);
                QuuppaTagEmulationDemoActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(QuuppaTagEmulationDemoActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
                return false;
            }
            return true;
        } catch (Exception e) {
            final String message = "Start Bluetooth failed. " + e.getMessage();
            Log.d("AdvCallback", message);
            QuuppaTagEmulationDemoActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(QuuppaTagEmulationDemoActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
            return false;
        }
    }


    private class TagID {
        public String id;
        public boolean isDeviceMac;

        public TagID() {
        }

        public TagID(String id, boolean isDeviceMac) {
            this.id = id;
            this.isDeviceMac = isDeviceMac;
        }
    }

    /**
     * Callback used for Direction Finding Packet advertisements.
     */
    private final AdvertiseCallback dfPacketAdvCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings advertiseSettings) {
            final String message = "DF Broadcast started with tagID: " + getTagId().id + ", " + advModes[advertiseSettings.getMode()] + ", " + advTxPowers[advertiseSettings.getTxPowerLevel()];
            Log.d("AdvCallback", message);
            QuuppaTagEmulationDemoActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(QuuppaTagEmulationDemoActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
            pulsingView.setIsPulsing(true);
        }

        @Override
        public void onStartFailure(int i) {
            final String message = "Start broadcast failed error code: " + i;
            Log.e("AdvCallback", message);
            QuuppaTagEmulationDemoActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(QuuppaTagEmulationDemoActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
            pulsingView.setIsPulsing(false);
        }
    };

    /**
     * Callback used for Data Packet advertisements.
     */
    private final AdvertiseCallback dataPacketAdvCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings advertiseSettings) {
        }

        @Override
        public void onStartFailure(int i) {
            final String message = "Start Status broadcast failed error code: " + i;
            Log.e("AdvCallback", message);
            QuuppaTagEmulationDemoActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(QuuppaTagEmulationDemoActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    };
}

