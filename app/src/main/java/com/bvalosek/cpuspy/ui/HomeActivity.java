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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.bvalosek.cpuspy.CpuSpyApp;
import com.bvalosek.cpuspy.CpuTimeInStateMonitor;
import com.bvalosek.cpuspy.CpuTimeInStateMonitor.CpuStateMonitorException;
import com.bvalosek.cpuspy.CpuTimeInStateMonitor.CpuTimeInState;
import com.bvalosek.cpuspy.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends Activity implements AdapterView.OnItemSelectedListener {
    private static final String LOG_TAG = "CpuSpy";

    private CpuSpyApp app = null;

    /**
     * Views
     */
    private Spinner uiCpuSelector = null;
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

    private final String updateInProgressIndicator = "updatingInProgress";

    /**
     * Initialize the Activity
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.home_layout);
        this.app = (CpuSpyApp) getApplicationContext();
        populateView();


        List<String> cpuList = new ArrayList<>();
        for (int i = 0; i < this.app.getCpuStateMonitor().getCpuCount(); i++) {
            cpuList.add(String.format(Locale.getDefault(), "Core %d", i));
        }
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cpuList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.uiCpuSelector.setAdapter(adapter);
        this.uiCpuSelector.setOnItemSelectedListener(this);

        setTitle(getResources().getText(R.string.app_name) + " v" + getResources().getText(R.string.version_name));

        if (savedInstanceState != null) {
            this.updatingInProgress =
                    savedInstanceState.getBoolean(this.updateInProgressIndicator);
        }
    }

    /**
     * When the activity is about to change orientation
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(this.updateInProgressIndicator, updatingInProgress);
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
    private void populateView() {
        uiCpuSelector = findViewById(R.id.ui_cpu_selector);
        uiStatesView = findViewById(R.id.ui_states_view);
        uiKernelString = findViewById(R.id.ui_kernel_string);
        uiAdditionalStates = findViewById(R.id.ui_additional_states);
        uiHeaderAdditionalStates = findViewById(R.id.ui_header_additional_states);
        uiHeaderTotalStateTime = findViewById(R.id.ui_header_total_state_time);
        uiStatesWarning = findViewById(R.id.ui_states_warning);
        uiTotalStateTime = findViewById(R.id.ui_total_state_time);
    }

    /**
     * Handle cpu selector dropdown
     */
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        this.app.getCpuStateMonitor().setCurrentCpu((int) id);
        updateView();
    }

    public void onNothingSelected(AdapterView<?> parent) {
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
                    this.app.getCpuStateMonitor().resetAllCpuIgnoredTimeInStates();
                } catch (CpuStateMonitorException e) {
                    logError(e.toString());
                }
                updateView();
                break;
            case R.id.menu_restore:
                this.app.getCpuStateMonitor().removeAllCpuIgnoredTimeInState();
                updateView();
                break;
        }

        return true;
    }

    /**
     * Generate and update all UI elements
     */
    public void updateView() {
        CpuTimeInStateMonitor timeInStateMonitor = this.app.getCpuStateMonitor();
        uiStatesView.removeAllViews();
        List<String> extraStates = new ArrayList<>();
        for (CpuTimeInState timeInState : timeInStateMonitor.getCurrentCpuTimeInState()) {
            if (timeInState.duration > 0) {
                generateStateRow(timeInState, uiStatesView);
                continue;
            }

            if (timeInState.frequency == 0) {
                extraStates.add("Deep Sleep");
            } else {
                extraStates.add(timeInState.frequency / 1000 + " MHz");
            }
        }

        if (timeInStateMonitor.getCurrentCpuTimeInState().size() == 0) {
            uiStatesWarning.setVisibility(View.VISIBLE);
            uiHeaderTotalStateTime.setVisibility(View.GONE);
            uiTotalStateTime.setVisibility(View.GONE);
            uiStatesView.setVisibility(View.GONE);
        }

        long totalTime = timeInStateMonitor.getCurrentCpuTotalStateTime() / 100;
        uiTotalStateTime.setText(secondsToDuration(totalTime));

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
    private static String secondsToDuration(long tSec) {
        long h = (long) Math.floor((float) tSec / (60 * 60));
        long m = (long) Math.floor(((float) tSec - h * 60 * 60) / 60);
        long s = tSec % 60;
        StringBuilder duration = new StringBuilder();
        duration.append(h).append(":");
        if (m < 10)
            duration.append("0");
        duration.append(m).append(":");
        if (s < 10)
            duration.append("0");
        duration.append(s);

        return duration.toString();
    }

    /**
     * Generate a View that corresponds to a CPU freq state row as specified
     * by the state parameter
     */
    private void generateStateRow(CpuTimeInState state, ViewGroup parent) {
        LayoutInflater inf = LayoutInflater.from(app);
        LinearLayout theRow = (LinearLayout) inf.inflate(
                R.layout.state_row, parent, false);

        CpuTimeInStateMonitor monitor = app.getCpuStateMonitor();
        float percentageValue = (float) state.duration * 100 /
                monitor.getCurrentCpuTotalStateTime();
        String statePercentage = (int) percentageValue + "%";

        String stateFrequency;
        if (state.frequency == 0) {
            stateFrequency = "Deep Sleep";
        } else {
            stateFrequency = state.frequency / 1000 + " MHz";
        }

        String stateDuration = secondsToDuration(state.duration / 100);

        TextView frequency = theRow.findViewById(R.id.ui_freq_text);
        TextView duration = theRow.findViewById(R.id.ui_duration_text);
        TextView percentage = theRow.findViewById(R.id.ui_percentage_text);
        ProgressBar illustrator = theRow.findViewById(R.id.ui_bar);

        frequency.setText(stateFrequency);
        percentage.setText(statePercentage);
        duration.setText(stateDuration);
        illustrator.setProgress((int) percentageValue);

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
            CpuTimeInStateMonitor timeInState
                    = activityRef.get().getApp().getCpuStateMonitor();
            try {
                timeInState.updateAllCpuTimeInState();
            } catch (CpuStateMonitorException exception) {
                activityRef.get().logError(String.format(
                        Locale.getDefault(),
                        "Problem getting CPU states: %s",
                        exception.toString())
                );
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
    private void logError(String s) {
        Log.e(LOG_TAG, s);
    }
}
