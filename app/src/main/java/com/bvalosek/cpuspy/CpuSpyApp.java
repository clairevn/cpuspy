/*
  -----------------------------------------------------------------------------

  (C) Brandon Valosek, 2011 <bvalosek@gmail.com>

  -----------------------------------------------------------------------------
 */

package com.bvalosek.cpuspy;

import android.app.Application;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CpuSpyApp extends Application {

    /**
     * The long-living object used to monitor the system frequency states
     */
    private volatile CpuTimeInStateMonitor timeInStateMonitor = new CpuTimeInStateMonitor();

    private String kernelVersion = "";

    /**
     * On application start, load the saved offsets and stash the
     * current kernel version string
     */
    @Override
    public void onCreate() {
        super.onCreate();
        updateKernelVersion();
    }

    /**
     * @return the kernel version string
     */
    public String getKernelVersion() {
        return this.kernelVersion;
    }

    /**
     * @return the internal CpuTimeInStateMonitor object
     */
    public CpuTimeInStateMonitor getCpuStateMonitor() {
        return this.timeInStateMonitor;
    }

    /**
     * Try to read the kernel version string from the proc fileystem
     */
    public void updateKernelVersion() {
        try {
            Process p = Runtime.getRuntime().exec("uname -a");
            InputStream programOutput;
            if (p.waitFor() == 0) {
                programOutput = p.getInputStream();
            } else {
                programOutput = p.getErrorStream();
            }
            BufferedReader programOutputReader = new BufferedReader(new InputStreamReader(programOutput));
            this.kernelVersion = programOutputReader.readLine();
            programOutputReader.close();
        } catch (Exception ex) {
            this.kernelVersion = "ERROR: " + ex.getMessage();
        }
    }
}
