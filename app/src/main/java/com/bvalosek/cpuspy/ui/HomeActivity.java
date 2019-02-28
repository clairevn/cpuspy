//-----------------------------------------------------------------------------
//
// (C) Brandon Valosek, 2011 <bvalosek@gmail.com>
//
//-----------------------------------------------------------------------------

package com.bvalosek.cpuspy.ui;

// imports

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

/*
 * main activity class
 */
public class HomeActivity extends Activity {
    private static final String TAG = "CpuSpy";

    private CpuSpyApp app = null;

    // the views
    private LinearLayout uiStatesView = null;
    private TextView uiAdditionalStates = null;
    private TextView uiTotalStateTime = null;
    private TextView uiHeaderAdditionalStates = null;
    private TextView uiHeaderTotalStateTime = null;
    private TextView uiStatesWarning = null;
    private TextView uiKernelString = null;

    /*
     * whether or not we're updating the data in the background
     */
    private boolean updatingData = false;

    /*
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
            updatingData = savedInstanceState.getBoolean("updatingData");
        }
    }

    /*
     * When the activity is about to change orientation
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("updatingData", updatingData);
    }


    /*
     * Update the view when the application regains focus
     */
    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    /*
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

    /*
     * called when we want to inflate the menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // request inflater from activity and inflate into its menu
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.home_menu, menu);

        // made it
        return true;
    }

    /*
     * called to handle a menu event
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // what it do mayne
        switch (item.getItemId()) {
            /* pressed the load menu button */
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

        // made it
        return true;
    }

    /*
     * Generate and update all UI elements
     */
    public void updateView() {
        /* Get the CpuStateMonitor from the app, and iterate over all states,
         * creating a row if the duration is > 0 or otherwise marking it in
         * extraStates (missing) */
        CpuStateMonitor monitor = app.getCpuStateMonitor();
        uiStatesView.removeAllViews();
        List<String> extraStates = new ArrayList<>();
        for (CpuState state : monitor.getStates()) {
            if (state.duration > 0) {
                generateStateRow(state, uiStatesView);
            } else {
                if (state.freq == 0) {
                    extraStates.add("Deep Sleep");
                } else {
                    extraStates.add(state.freq / 1000 + " MHz");
                }
            }
        }

        // show the red warning label if no states found
        if (monitor.getStates().size() == 0) {
            uiStatesWarning.setVisibility(View.VISIBLE);
            uiHeaderTotalStateTime.setVisibility(View.GONE);
            uiTotalStateTime.setVisibility(View.GONE);
            uiStatesView.setVisibility(View.GONE);
        }

        // update the total state time
        long totTime = monitor.getTotalStateTime() / 100;
        uiTotalStateTime.setText(longToString(totTime));

        // for all the 0 duration states, add the the Unused State area
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

        // kernel line
        uiKernelString.setText(app.getKernelVersion());
    }

    /*
     * Attempt to update the time-in-state info
     */
    public void refreshData() {
        if (!updatingData) {
            new RefreshStateDataTask(this).execute((Void) null);
        }
    }

    /*
     * Flag updating in progress
     */
    public void setUpdating(boolean updating) {
        updatingData = updating;
    }

    /*
     * Getcpu app inside
     */
    public CpuSpyApp getApp() {
        return this.app;
    }

    /*
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

    /*
     * generate a View that corresponds to a CPU freq state row as specified
     * by the state parameter
     */
    private void generateStateRow(CpuState state, ViewGroup parent) {
        // inflate the XML into a view in the parent
        LayoutInflater inf = LayoutInflater.from(app);
        LinearLayout theRow = (LinearLayout) inf.inflate(
                R.layout.state_row, parent, false);

        // what percentage we've got
        CpuStateMonitor monitor = app.getCpuStateMonitor();
        float per = (float) state.duration * 100 /
                monitor.getTotalStateTime();
        String sPer = (int) per + "%";

        // state name
        String sFreq;
        if (state.freq == 0) {
            sFreq = "Deep Sleep";
        } else {
            sFreq = state.freq / 1000 + " MHz";
        }

        // duration
        long tSec = state.duration / 100;
        String sDur = longToString(tSec);

        // map UI elements to objects
        TextView freqText = theRow.findViewById(R.id.ui_freq_text);
        TextView durText = theRow.findViewById(
                R.id.ui_duration_text);
        TextView perText = theRow.findViewById(
                R.id.ui_percentage_text);
        ProgressBar bar = theRow.findViewById(R.id.ui_bar);

        // modify the row
        freqText.setText(sFreq);
        perText.setText(sPer);
        durText.setText(sDur);
        bar.setProgress((int) per);

        // add it to parent and return
        parent.addView(theRow);
    }

    /*
     * Keep updating the state data off the UI thread for slow devices
     */
    protected static class RefreshStateDataTask extends AsyncTask<Void, Void, Void> {

        private static WeakReference<HomeActivity> activityRef;

        RefreshStateDataTask(HomeActivity context) {
            activityRef = new WeakReference<>(context);
        }

        /*
         * Stuff to do on a seperate thread
         */
        @Override
        protected Void doInBackground(Void... v) {
            CpuStateMonitor monitor = activityRef.get().getApp().getCpuStateMonitor();
            try {
                monitor.updateStates();
            } catch (CpuStateMonitorException e) {
                Log.e(TAG, "Problem getting CPU states");
            }

            return null;
        }

        /*
         * Executed on the UI thread right before starting the task
         */
        @Override
        protected void onPreExecute() {
            activityRef.get().setUpdating(true);
        }

        /*
         * Executed on UI thread after task
         */
        @Override
        protected void onPostExecute(Void v) {
            activityRef.get().setUpdating(false);
            activityRef.get().updateView();
        }
    }

    /*
     * logging
     */
    private void log(String s) {
        ;
    }
}
