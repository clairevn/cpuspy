/*
  -----------------------------------------------------------------------------

  (C) Brandon Valosek, 2011 <bvalosek@gmail.com>

  -----------------------------------------------------------------------------
 */

package com.bvalosek.cpuspy;

import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CpuTimeInStateMonitor is a class responsible for querying the system and getting
 * the time-in-state information, as well as allowing the user to set/reset
 * allCpuIgnoredTimeInState to "restart" the state timers
 */
public class CpuTimeInStateMonitor {

    CpuTimeInStateMonitor() {
        int count = 0;
        while (true) {
            File file = new File(String.format(Locale.getDefault(), TimeInStateFormatterString, count));
            if (!file.exists()) {
                this.cpuCount = count;
                break;
            }
            count++;
        }
    }

    private static final String TimeInStateFormatterString =
            "/sys/devices/system/cpu/cpu%d/cpufreq/stats/time_in_state";

    public Integer getCpuCount() {
        return cpuCount;
    }

    private Integer cpuCount;

    private Integer currentCpu = 4;

    public void setCurrentCpu(Integer currentCpu) {
        this.currentCpu = currentCpu;
    }

    private volatile SparseArray<List<CpuTimeInState>> allCpuTimeInStates = new SparseArray<>();
    private volatile SparseArray<Map<Integer, Long>> allCpuIgnoredTimeInState = new SparseArray<>();

    public class CpuStateMonitorException extends Exception {
        CpuStateMonitorException(String s) {
            super(s);
        }
    }

    public static class CpuTimeInState implements Comparable<CpuTimeInState> {
        CpuTimeInState(Integer frequency, long duration) {
            this.frequency = frequency;
            this.duration = duration;
        }

        public Integer frequency;
        public long duration;

        public int compareTo(CpuTimeInState state) {
            return this.frequency.compareTo(state.frequency);
        }
    }

    /**
     * @return List of CpuTimeInState with the allCpuIgnoredTimeInState applied
     */
    public List<CpuTimeInState> getCurrentCpuTimeInState() {
        List<CpuTimeInState> states = new ArrayList<>();

        for (CpuTimeInState state : this.allCpuTimeInStates.get(this.currentCpu, new ArrayList<CpuTimeInState>())) {
            long duration = state.duration;
            @SuppressWarnings("ConstantConditions")
            long ignoredDuration = this.allCpuIgnoredTimeInState.
                    get(this.currentCpu, new ArrayMap<Integer, Long>()).
                    getOrDefault(state.frequency, 0L);
            if (ignoredDuration <= duration) {
                duration -= ignoredDuration;
            } else {
                /*
                ignoredDuration > duration implies our ignoredDurations are now invalid,
                so clear and recall this function
                 */
                this.allCpuIgnoredTimeInState.get(this.currentCpu).clear();
                return getCurrentCpuTimeInState();
            }
            states.add(new CpuTimeInState(state.frequency, duration));
        }

        return states;
    }

    /**
     * @return Sum of all state durations including deep sleep, accounting
     * for allCpuIgnoredTimeInState
     */
    public long getCurrentCpuTotalStateTime() {
        long totalTime = 0;
        long ignoredTime = 0;

        for (CpuTimeInState state :
                this.allCpuTimeInStates.get(this.currentCpu, new ArrayList<CpuTimeInState>())) {
            totalTime += state.duration;
        }

        for (Map.Entry<Integer, Long> entry :
                this.allCpuIgnoredTimeInState.get(
                        this.currentCpu, new ArrayMap<Integer, Long>()).entrySet()) {
            ignoredTime += entry.getValue();
        }

        return totalTime - ignoredTime;
    }

    /**
     * Updates the current time in allCpuTimeInStates and then sets the allCpuIgnoredTimeInState map to the
     * current duration, effectively "zeroing out" the timers
     */
    public void resetAllCpuIgnoredTimeInStates() throws CpuStateMonitorException {
        for (int i = 0; i < this.allCpuIgnoredTimeInState.size(); i++) {
            this.allCpuIgnoredTimeInState.get(i).clear();
        }
        updateAllCpuTimeInState();

        for (int i = 0; i < this.allCpuTimeInStates.size(); i++) {
            for (CpuTimeInState state : this.allCpuTimeInStates.get(i)) {
                allCpuIgnoredTimeInState.get(i).put(state.frequency, state.duration);
            }
        }
    }

    public void removeAllCpuIgnoredTimeInState() {
        allCpuIgnoredTimeInState.clear();
    }

    /**
     * Update a list of all the CPU frequency allCpuTimeInStates, which contains
     * both a frequency and a duration (time spent in that state)
     */
    public void updateAllCpuTimeInState() throws CpuStateMonitorException {
        for (Integer cpuIndex = 0; cpuIndex < this.cpuCount; cpuIndex++) {
            try {
                InputStream timeInStateSource = new FileInputStream(
                        String.format(Locale.getDefault(),
                                TimeInStateFormatterString,
                                cpuIndex));
                BufferedReader timeInStateContent = new BufferedReader(
                        new InputStreamReader(timeInStateSource));
                this.allCpuTimeInStates.put(cpuIndex, readTimeInState(timeInStateContent));
                if (this.allCpuIgnoredTimeInState.get(cpuIndex) == null) {
                    this.allCpuIgnoredTimeInState.put(cpuIndex, new ArrayMap<Integer, Long>());
                }
                timeInStateSource.close();
            } catch (IOException e) {
                throw new CpuStateMonitorException(
                        String.format("Problem opening \"" + TimeInStateFormatterString + "\" file",
                                this.currentCpu));
            }

            long sleepTime = (SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()) / 10;
            this.allCpuTimeInStates.get(cpuIndex).add(new CpuTimeInState(0, sleepTime));
            Collections.sort(this.allCpuTimeInStates.get(cpuIndex), Collections.reverseOrder());
        }
    }

    /**
     * read from a provided BufferedReader the state lines into the
     * States member field
     */
    private List<CpuTimeInState> readTimeInState(BufferedReader timeInStateLines)
            throws CpuStateMonitorException {
        try {
            String line;
            List<CpuTimeInState> cpuTimeInState = new ArrayList<>();
            while ((line = timeInStateLines.readLine()) != null) {
                String[] column = line.split(" ");
                cpuTimeInState.add(
                        new CpuTimeInState(Integer.parseInt(column[0]), Long.parseLong(column[1]))
                );
            }

            return cpuTimeInState;
        } catch (IOException e) {
            throw new CpuStateMonitorException(
                    String.format("Problem processing \"" +
                                    TimeInStateFormatterString +
                                    "\" file",
                            this.currentCpu));
        }
    }
}
