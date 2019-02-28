//-----------------------------------------------------------------------------
//
// (C) Brandon Valosek, 2011 <bvalosek@gmail.com>
//
//-----------------------------------------------------------------------------

package com.bvalosek.cpuspy;

// imports
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import com.bvalosek.cpuspy.CpuStateMonitor.CpuState;
import com.bvalosek.cpuspy.CpuStateMonitor.CpuStateMonitorException;

/** main application class */
public class CpuSpyApp extends Application {

    private static final String PREF_NAME = "CpuSpyPreferences";
    private static final String PREF_OFFSETS = "offsets";

    /** the long-living object used to monitor the system frequency states */
    private CpuStateMonitor stateMonitor = new CpuStateMonitor();

    private String kernelVersion = "";

    /**
     * On application start, load the saved offsets and stash the
     * current kernel version string
     */
    @Override public void onCreate(){
        super.onCreate();
        loadOffsets();
        updateKernelVersion();
    }

    /** @return the kernel version string */
    public String getKernelVersion() {
        return kernelVersion;
    }

    /** @return the internal CpuStateMonitor object */
    public CpuStateMonitor getCpuStateMonitor() {
        return stateMonitor;
    }

    /**
     * Load the saved string of offsets from preferences and put it into
     * the state monitor
     */
    public void loadOffsets() {
        SharedPreferences settings = getSharedPreferences(
                PREF_NAME, MODE_PRIVATE);
        String prefs = settings.getString (PREF_OFFSETS, "");

        if (prefs == null || prefs.length() < 1) {
            return;
        }

        // split the string by peroids and then the info by commas and load
        Map<Integer, Long> offsets = new HashMap<>();
        String[] sOffsets = prefs.split(",");
        for (String offset : sOffsets) {
            String[] parts = offset.split(" ");
            offsets.put (Integer.parseInt(parts[0]),
                         Long.parseLong(parts[1]));
        }

        stateMonitor.setOffsets(offsets);
    }

    /**
     * Save the state-time offsets as a string
     * e.g. "100 24, 200 251, 500 124 etc
     */
    public void saveOffsets() {
        SharedPreferences settings = getSharedPreferences(
                PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();

        // build the string by iterating over the freq->duration map
        String str = "";
        for (Map.Entry<Integer, Long> entry :
                stateMonitor.getOffsets().entrySet()) {
            str += entry.getKey() + " " + entry.getValue() + ",";
        }

        editor.putString(PREF_OFFSETS, str);
        editor.commit();
    }

    /** Try to read the kernel version string from the proc fileystem */
    public void updateKernelVersion() {
        try {
            Process p = Runtime.getRuntime().exec("uname -a");
            InputStream is;
            if (p.waitFor() == 0) {
                is = p.getInputStream();
            } else {
                is = p.getErrorStream();
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            kernelVersion = br.readLine();
            br.close();
        } catch (Exception ex) {
            kernelVersion = "ERROR: " + ex.getMessage();
        }
    }
}
