/*
  -----------------------------------------------------------------------------

  (C) Brandon Valosek, 2011 <bvalosek@gmail.com>

  -----------------------------------------------------------------------------
 */

package com.bvalosek.cpuspy.ui;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bvalosek.cpuspy.CpuSpyApp;
import com.bvalosek.cpuspy.CpuStateMonitor;
import com.bvalosek.cpuspy.CpuStateMonitor.CpuState;
import com.bvalosek.cpuspy.CpuStateMonitor.CpuStateMonitorException;
import com.bvalosek.cpuspy.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends Activity {
    private static final String logTag = "CpuSpy";

    private CpuSpyApp app = null;

    /**
     * Views
     */
    private LinearLayout uiStatesView = null;
    private TextView uiAdditionalStates = null;
    private TextView uiTotalStateTime = null;
    private TextView uiHeaderAdditionalStates = null;
    private TextView uiHeaderTotalStateTime = null;
    private TextView uiStatesWarning = null;
    private TextView uiKernelString = null;

    /**
     * Indicate that we're updating the data in the background
     */
    private boolean updatingInProgress = false;

    /**
     * Initialize the Activity
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // inflate the view, stash the app context, and get all UI elements
        setContentView(R.layout.home_layout);
        app = (CpuSpyApp) getApplicationContext();
        findViews();

        // set title to version string
        setTitle(getResources().getText(R.string.app_name) + " v" +
                getResources().getText(R.string.version_name));

        // see if we're updating data during a config change (rotate screen)
        if (savedInstanceState != null) {
            updatingInProgress = savedInstanceState.getBoolean("updatingInProgress");
        }
    }

    /**
     * When the activity is about to change orientation
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("updatingInProgress", updatingInProgress);
    }


    /**
     * Update the view when the application regains focus
     */
    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    /**
     * Map all of the UI elements to member variables
     */
    private void findViews() {
        uiStatesView = findViewById(R.id.ui_states_view);
        uiKernelString = findViewById(R.id.ui_kernel_string);
        uiAdditionalStates = findViewById(
                R.id.ui_additional_states);
        uiHeaderAdditionalStates = findViewById(
                R.id.ui_header_additional_states);
        uiHeaderTotalStateTime = findViewById(
                R.id.ui_header_total_state_time);
        uiStatesWarning = findViewById(R.id.ui_states_warning);
        uiTotalStateTime = findViewById(R.id.ui_total_state_time);
    }

    /**
     * Inflate the menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.home_menu, menu);

        return true;
    }

    /**
     * Handle menu item selected
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                refreshData();
                break;
            case R.id.menu_reset:
                try {
                    app.getCpuStateMonitor().setOffsets();
                } catch (CpuStateMonitorException e) {
                    log(e.toString());
                }

                app.saveOffsets();
                updateView();
                break;
            case R.id.menu_restore:
                app.getCpuStateMonitor().removeOffsets();
                app.saveOffsets();
                updateView();
                break;
        }

        return true;
    }

    /**
     * Generate and update all UI elements
     */
    public void updateView() {
        CpuStateMonitor monitor = app.getCpuStateMonitor();
        uiStatesView.removeAllViews();
        List<String> extraStates = new ArrayList<>();
        for (CpuState state : monitor.getStates()) {
            if (state.duration > 0) {
                generateStateRow(state, uiStatesView);
                continue;
            }

            if (state.freq == 0) {
                extraStates.add("Deep Sleep");
            } else {
                extraStates.add(state.freq / 1000 + " MHz");
            }
        }

        if (monitor.getStates().size() == 0) {
            uiStatesWarning.setVisibility(View.VISIBLE);
            uiHeaderTotalStateTime.setVisibility(View.GONE);
            uiTotalStateTime.setVisibility(View.GONE);
            uiStatesView.setVisibility(View.GONE);
        }

        long totalTime = monitor.getTotalStateTime() / 100;
        uiTotalStateTime.setText(longToString(totalTime));

        if (extraStates.size() > 0) {
            int n = 0;
            StringBuilder additionStatesBuilder = new StringBuilder();

            for (String s : extraStates) {
                if (n++ > 0)
                    additionStatesBuilder.append(", ");
                additionStatesBuilder.append(s);
            }

            uiAdditionalStates.setVisibility(View.VISIBLE);
            uiHeaderAdditionalStates.setVisibility(View.VISIBLE);
            uiAdditionalStates.setText(additionStatesBuilder.toString());
        } else {
            uiAdditionalStates.setVisibility(View.GONE);
            uiHeaderAdditionalStates.setVisibility(View.GONE);
        }

        uiKernelString.setText(app.getKernelVersion());
    }

    /**
     * Attempt to update the time-in-state info
     */
    public void refreshData() {
        if (!updatingInProgress) {
            new RefreshStateDataTask(this).execute((Void) null);
        }
    }

    public void flagUpdatingInProgress(boolean inProgress) {
        updatingInProgress = inProgress;
    }

    public CpuSpyApp getApp() {
        return this.app;
    }

    /**
     * @return A nicely formatted String representing tSec seconds
     */
    private static String longToString(long tSec) {
        long h = (long) Math.floor((float) tSec / (60 * 60));
        long m = (long) Math.floor(((float) tSec - h * 60 * 60) / 60);
        long s = tSec % 60;
        String sDur;
        sDur = h + ":";
        if (m < 10)
            sDur += "0";
        sDur += m + ":";
        if (s < 10)
            sDur += "0";
        sDur += s;

        return sDur;
    }

    /**
     * Generate a View that corresponds to a CPU freq state row as specified
     * by the state parameter
     */
    private void generateStateRow(CpuState state, ViewGroup parent) {
        LayoutInflater inf = LayoutInflater.from(app);
        LinearLayout theRow = (LinearLayout) inf.inflate(
                R.layout.state_row, parent, false);

        CpuStateMonitor monitor = app.getCpuStateMonitor();
        float per = (float) state.duration * 100 /
                monitor.getTotalStateTime();
        String statePercentage = (int) per + "%";

        String stateFrequency;
        if (state.freq == 0) {
            stateFrequency = "Deep Sleep";
        } else {
            stateFrequency = state.freq / 1000 + " MHz";
        }

        String stateDuration = longToString(state.duration / 100);

        TextView freqText = theRow.findViewById(R.id.ui_freq_text);
        TextView durText = theRow.findViewById(R.id.ui_duration_text);
        TextView perText = theRow.findViewById(R.id.ui_percentage_text);
        ProgressBar bar = theRow.findViewById(R.id.ui_bar);

        freqText.setText(stateFrequency);
        perText.setText(statePercentage);
        durText.setText(stateDuration);
        bar.setProgress((int) per);

        parent.addView(theRow);
    }

    /**
     * Asynchronously update the state data outside of the UI thread
     */
    protected static class RefreshStateDataTask extends AsyncTask<Void, Void, Void> {

        private static WeakReference<HomeActivity> activityRef;

        RefreshStateDataTask(HomeActivity context) {
            activityRef = new WeakReference<>(context);
        }

        /**
         * Stuff to do on a separate thread
         */
        @Override
        protected Void doInBackground(Void... v) {
            CpuStateMonitor monitor = activityRef.get().getApp().getCpuStateMonitor();
            try {
                monitor.updateStates();
            } catch (CpuStateMonitorException e) {
                activityRef.get().log("Problem getting CPU states");
            }

            return null;
        }

        /**
         * Executed on the UI thread right before starting the task
         */
        @Override
        protected void onPreExecute() {
            activityRef.get().flagUpdatingInProgress(true);
        }

        /**
         * Executed on UI thread after task
         */
        @Override
        protected void onPostExecute(Void v) {
            activityRef.get().flagUpdatingInProgress(false);
            activityRef.get().updateView();
        }
    }

    /**
     * Logging wrapper
     */
    private void log(String s) {
        Log.e(logTag, s);
    }
}
