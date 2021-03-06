package fr.guillaumevillena.opendnsupdater.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SwitchCompat;

import com.wooplr.spotlight.SpotlightConfig;
import com.wooplr.spotlight.utils.SpotlightSequence;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import fr.guillaumevillena.opendnsupdater.BuildConfig;
import fr.guillaumevillena.opendnsupdater.OpenDnsUpdater;
import fr.guillaumevillena.opendnsupdater.R;
import fr.guillaumevillena.opendnsupdater.Receivers.ConnectivityJob;
import fr.guillaumevillena.opendnsupdater.event.InterfaceUpdatedEvent;
import fr.guillaumevillena.opendnsupdater.event.IpUpdatedEvent;
import fr.guillaumevillena.opendnsupdater.tasks.CheckFakePhishingSite;
import fr.guillaumevillena.opendnsupdater.tasks.CheckUsingOpenDNS;
import fr.guillaumevillena.opendnsupdater.tasks.ExternalIpFinder;
import fr.guillaumevillena.opendnsupdater.tasks.TaskFinished;
import fr.guillaumevillena.opendnsupdater.tasks.UpdateOnlineIP;
import fr.guillaumevillena.opendnsupdater.utils.ConnectivityUtil;
import fr.guillaumevillena.opendnsupdater.utils.DateUtils;
import fr.guillaumevillena.opendnsupdater.utils.PreferenceCodes;
import fr.guillaumevillena.opendnsupdater.utils.RequestCodes;
import fr.guillaumevillena.opendnsupdater.utils.SimplerCountdown;
import fr.guillaumevillena.opendnsupdater.utils.StateSwitcher;
import fr.guillaumevillena.opendnsupdater.vpnService.service.OpenDnsVpnService;
import fr.guillaumevillena.opendnsupdater.vpnService.util.server.DNSServerHelper;

import static fr.guillaumevillena.opendnsupdater.TestState.ERROR;
import static fr.guillaumevillena.opendnsupdater.TestState.RUNNING;
import static fr.guillaumevillena.opendnsupdater.TestState.SUCCESS;
import static fr.guillaumevillena.opendnsupdater.TestState.UNKNOWN;


public class MainActivity extends AppCompatActivity implements TaskFinished {


    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int ACTIVATE_VPN_SERVICE = 3;
    private StateSwitcher filterPhishingStateSwitcher;
    private StateSwitcher ipAddressUpdatedStateSwitcher;
    private StateSwitcher useOpendnsStateSwitcher;
    private StateSwitcher openDNSWebsiteStateSwitcher;

    private SwitchCompat switchEnableNotification;
    private SwitchCompat switchEnableAutoUpdate;
    private SwitchCompat switchEnableEnableOpendnsServers;

    private TextView ipValue;
    private TextView interfaceValue;
    private TextView lastUpdateDateTextView;
    private ImageButton refreshButton;
    private ImageButton settingButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!OpenDnsUpdater.getPrefs().getBoolean(PreferenceCodes.FIRST_TIME_CONFIG_FINISHED, false))
            startActivity(new Intent(this, ApplicationWizard.class));

        setContentView(R.layout.activity_main);


        lastUpdateDateTextView = findViewById(R.id.mainactivity_last_update);

        TextView textViewVersion = findViewById(R.id.mainactivity_app_version);
        textViewVersion.setText(getString(R.string.app_version, BuildConfig.VERSION_NAME));

        // Getting useful widget from the view.

        ipValue = findViewById(R.id.ip_text_value);
        interfaceValue = findViewById(R.id.interface_text_value);

        settingButton = findViewById(R.id.setting_button);
        refreshButton = findViewById(R.id.refresh_button);

        ProgressBar progressBarIpAddressUpdate = findViewById(R.id.progressBar_ip_updated);
        ProgressBar progressBarFilterPhising = findViewById(R.id.progressBar_filter_phising);
        ProgressBar progressBarUsingOpenDns = findViewById(R.id.progressBar_using_opendns);
        ProgressBar progressBarOpenDnsWebsite = findViewById(R.id.progressBar_status_website_check);

        AppCompatImageView imgStatusIpAdressUpdate = findViewById(R.id.img_status_ip_updated);
        AppCompatImageView imgStatusFilterPhishing = findViewById(R.id.img_status_filter_phishing);
        AppCompatImageView imgStatusUsingOpendns = findViewById(R.id.img_status_using_opendns);
        AppCompatImageView imgStatusOpenDNSWebiste = findViewById(R.id.img_status_website_check);

        switchEnableNotification = findViewById(R.id.switch_enable_notifications);
        switchEnableAutoUpdate = findViewById(R.id.switch_auto_update);
        switchEnableEnableOpendnsServers = findViewById(R.id.switch_enable_opendns_server);

        this.filterPhishingStateSwitcher = new StateSwitcher();
        this.ipAddressUpdatedStateSwitcher = new StateSwitcher();
        this.useOpendnsStateSwitcher = new StateSwitcher();
        this.openDNSWebsiteStateSwitcher = new StateSwitcher();

        initStateSwitcher(this.filterPhishingStateSwitcher, progressBarFilterPhising, imgStatusFilterPhishing);
        initStateSwitcher(this.ipAddressUpdatedStateSwitcher, progressBarIpAddressUpdate, imgStatusIpAdressUpdate);
        initStateSwitcher(this.useOpendnsStateSwitcher, progressBarUsingOpenDns, imgStatusUsingOpendns);
        initStateSwitcher(this.openDNSWebsiteStateSwitcher, progressBarOpenDnsWebsite, imgStatusOpenDNSWebiste);

        this.ipAddressUpdatedStateSwitcher.setCurrentState(RUNNING);
        this.filterPhishingStateSwitcher.setCurrentState(RUNNING);
        this.useOpendnsStateSwitcher.setCurrentState(RUNNING);
        this.openDNSWebsiteStateSwitcher.setCurrentState(RUNNING);

        // Connect events to switches and buttons
        switchEnableAutoUpdate.setOnCheckedChangeListener((compoundButton, state) -> setAutoUpdater(state));
        switchEnableNotification.setOnCheckedChangeListener((compoundButton, state) -> setNotifications(state));
        switchEnableEnableOpendnsServers.setOnCheckedChangeListener((compoundButton, state) -> setOpenDnsServers(state));

        settingButton.setOnClickListener(view -> openSettings());
        refreshButton.setOnClickListener(view -> refreshOpenDnsStatus());
        restoreSettings();
        refreshOpenDnsStatus();

        showSpotlight();

    }

    private void showSpotlight() {

        //The spotlight library manage the fact that they are displayed only once.
        // But for performance and to avoid creating useless objects, let's use a shared preference key.

        if (!OpenDnsUpdater.getPrefs().getBoolean(PreferenceCodes.SPOTLIGHT_SHOWN, false)) {

            SpotlightConfig config = new SpotlightConfig();
            config.setIntroAnimationDuration(getResources().getInteger(R.integer.intro_annimation_duration));
            config.setRevealAnimationEnabled(true);
            config.setPerformClick(false);
            config.setFadingTextDuration(getResources().getInteger(R.integer.fading_text_duration));
            config.setHeadingTvColor(getResources().getColor(R.color.spotlight_heading_textview));
            config.setHeadingTvSize(16);
            config.setShowTargetArc(true);
            config.setSubHeadingTvColor(getResources().getColor(R.color.colorTextIcon));
            config.setSubHeadingTvSize(16);
            config.setMaskColor(getResources().getColor(R.color.spotlight_backplane));
            config.setLineAnimationDuration(getResources().getInteger(R.integer.line_annimation_duration));
            config.setLineAndArcColor(getResources().getColor(R.color.spotlight_line_color));
            config.setDismissOnTouch(true);
            config.setDismissOnBackpress(true);

            SpotlightSequence seq = SpotlightSequence.getInstance(this, config);

            //Switches
            seq.addSpotlight(this.switchEnableAutoUpdate, getString(R.string.spotlight_title_auto_update_switch),
                    getString(R.string.spotlight_content_auto_update_switch),
                    PreferenceCodes.SPOTLIGHT_ID_SWITCH_AUTO_UPDATED);
            seq.addSpotlight(this.switchEnableEnableOpendnsServers,
                    getString(R.string.spotlight_title_start_vpn_server),
                    getString(R.string.spotlight_content_start_vpn_server),
                    PreferenceCodes.SPOTLIGHT_ID_SWITCH_VPN_SERVER);
            seq.addSpotlight(this.switchEnableNotification,
                    getString(R.string.spotlight_title_show_notifications),
                    getString(R.string.spotlight_content_show_notifications),
                    PreferenceCodes.SPOTLIGHT_ID_SWITCH_NOTIFICATION);

            //StateSwitchers

            seq.addSpotlight(this.ipAddressUpdatedStateSwitcher.getView(),
                    getString(R.string.spotlight_title_state_switcher_ip_updated),
                    getString(R.string.spotlight_content_state_switcher_ip_updated),
                    PreferenceCodes.SPOTLIGHT_ID_STATUS_IP_UPDATED);
            seq.addSpotlight(this.openDNSWebsiteStateSwitcher.getView(),
                    getString(R.string.spotlight_title_state_switcher_using_opendns_server),
                    getString(R.string.spotlight_content_state_switcher_using_opendns_server),
                    PreferenceCodes.SPOTLIGHT_ID_STATUS_USING_OPENDNS);
            seq.addSpotlight(this.filterPhishingStateSwitcher.getView(),
                    getString(R.string.spotlight_title_state_switcher_is_filtering),
                    getString(R.string.spotlight_content_state_switcher_is_filtering),
                    PreferenceCodes.SPOTLIGHT_ID_STATUS_FILTERING_WEBSITES);
            seq.addSpotlight(this.useOpendnsStateSwitcher.getView(),
                    getString(R.string.spotlight_title_state_switcher_using_vpn_service),
                    getString(R.string.spotlight_content_state_switcher_using_vpn_service),
                    PreferenceCodes.SPOTLIGHT_ID_STATUS_USING_VPN);

            // General buttons

            seq.addSpotlight(this.refreshButton,
                    getString(R.string.spotlight_title_top_button_refresh_all),
                    getString(R.string.spotlight_content_top_button_refresh),
                    PreferenceCodes.SPOTLIGHT_ID_TOP_BUTTON_REFRESH);
            seq.addSpotlight(this.settingButton,
                    getString(R.string.spotlight_title_top_button_settings),
                    getString(R.string.spotlight_content_top_button_settings),
                    PreferenceCodes.SPOTLIGHT_ID_TOP_BUTTON_SETTINGS);

            seq.startSequence();
            OpenDnsUpdater.getPrefs().edit().putBoolean(PreferenceCodes.SPOTLIGHT_SHOWN, true).apply();
        }

    }

    private void restoreSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());

        switchEnableNotification.setChecked(prefs.getBoolean(PreferenceCodes.APP_NOTIFY, false));
        switchEnableEnableOpendnsServers.setChecked(prefs.getBoolean(PreferenceCodes.APP_DNS, false));
        switchEnableAutoUpdate.setChecked(prefs.getBoolean(PreferenceCodes.APP_AUTO_UPDATE, false));

        printDateLastUpdate();

    }

    private void printDateLastUpdate() {
        long lastUpdate = OpenDnsUpdater.getPrefs().getLong(PreferenceCodes.OPENDNS_LAST_UPDATE, -1);
        Log.d(TAG, "restoreSettings: " + lastUpdate);
        lastUpdateDateTextView.setText(getString(R.string.main_activity_last_ip_update, lastUpdate != -1 ? DateUtils.getDate(this, lastUpdate) : getString(R.string.text_never)));
    }

    private void initStateSwitcher(StateSwitcher stateSwitcher, ProgressBar progressBar, AppCompatImageView imgStatus) {


        stateSwitcher.setDefaults(imgStatus, R.drawable.ic_block_grey_24dp);
        stateSwitcher.setCurrentState(UNKNOWN);

        stateSwitcher.putDrawable(R.drawable.ic_close_red_24dp, ERROR);
        stateSwitcher.putDrawable(R.drawable.ic_check_green_24dp, SUCCESS);
        stateSwitcher.putDrawable(-1, RUNNING);

        stateSwitcher.putView(progressBar, RUNNING);
        stateSwitcher.putView(imgStatus, ERROR);
        stateSwitcher.putView(imgStatus, SUCCESS);

    }

    private void setAutoUpdater(boolean state) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        SharedPreferences.Editor prefEditor = prefs.edit();

        prefEditor.putBoolean(PreferenceCodes.APP_AUTO_UPDATE, state);
        prefEditor.apply();
    }

    private void setNotifications(boolean state) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        SharedPreferences.Editor prefEditor = prefs.edit();

        prefEditor.putBoolean(PreferenceCodes.APP_NOTIFY, state);
        prefEditor.apply();
    }

    private void setOpenDnsServers(boolean state) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        SharedPreferences.Editor prefEditor = prefs.edit();

        prefEditor.putBoolean(PreferenceCodes.APP_DNS, state);
        prefEditor.apply();

        if (state)
            activateService();
        else {
            if (OpenDnsVpnService.isActivated()) {
                OpenDnsUpdater.deactivateService(this);
            }
        }

        this.refreshOpenDnsStatus();


    }

    public void activateService() {
        Intent intent = VpnService.prepare(OpenDnsUpdater.getInstance());
        if (intent != null) {
            startActivityForResult(intent, ACTIVATE_VPN_SERVICE);
        } else {
            onActivityResult(ACTIVATE_VPN_SERVICE, Activity.RESULT_OK, null);
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RequestCodes.SETTIGNS:
                restoreSettings();
                refreshOpenDnsStatus();
                break;
            case ACTIVATE_VPN_SERVICE:
                if (resultCode == Activity.RESULT_OK) {
                    OpenDnsVpnService.primaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getPrimary());
                    OpenDnsVpnService.secondaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getSecondary());
                    OpenDnsUpdater.getInstance().startService(OpenDnsUpdater.getServiceIntent(getApplicationContext()).setAction(OpenDnsVpnService.ACTION_ACTIVATE));
                }
        }

    }

    private void openSettings() {
        Intent intent = new Intent(this, GlobalSettingsActivity.class);
        this.startActivityForResult(intent, RequestCodes.SETTIGNS);
    }

    private void refreshOpenDnsStatus() {

        this.ipAddressUpdatedStateSwitcher.setCurrentState(RUNNING);
        this.filterPhishingStateSwitcher.setCurrentState(RUNNING);
        this.useOpendnsStateSwitcher.setCurrentState(RUNNING);
        this.openDNSWebsiteStateSwitcher.setCurrentState(RUNNING);

        onInterfaceChanged(new InterfaceUpdatedEvent(ConnectivityUtil.getActiveNetworkName(getApplicationContext())));

        new SimplerCountdown(1500) {
            @Override
            public void onFinish() {
                useOpendnsStateSwitcher.setCurrentState(OpenDnsVpnService.isActivated() ? SUCCESS : ERROR);
            }
        };

        new SimplerCountdown(1000) {
            @Override
            public void onFinish() {
                new ExternalIpFinder().execute();
                new CheckUsingOpenDNS(MainActivity.this).execute();
                new UpdateOnlineIP(MainActivity.this).execute();
                new CheckFakePhishingSite(MainActivity.this).execute();
            }
        };

    }


    @Override
    protected void onPause() {
        super.onPause();

        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void onIpUpdated(IpUpdatedEvent event) {
        runOnUiThread(() -> ipValue.setText(event.getIp()));
    }

    @Subscribe
    public void onInterfaceChanged(InterfaceUpdatedEvent event) {
        runOnUiThread(() -> interfaceValue.setText(event.getIface()));
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (!ConnectivityJob.isJobsStarted())
            ConnectivityJob.setScheduler(this);


        EventBus.getDefault().register(this);

        Log.d(TAG, "onResume: Starting to update the UI with fresh informations ");
        this.refreshOpenDnsStatus();


    }


    @Override
    public void onTaskFinished(AsyncTask task, Boolean result) {
        if (task instanceof CheckUsingOpenDNS) {
            Log.d(TAG, "onTaskFinished: CheckUsinOpenDNS ");
            openDNSWebsiteStateSwitcher.setCurrentState(result ? SUCCESS : ERROR);
        } else if (task instanceof UpdateOnlineIP) {
            ipAddressUpdatedStateSwitcher.setCurrentState(result ? SUCCESS : ERROR);
        } else if (task instanceof CheckFakePhishingSite) {
            filterPhishingStateSwitcher.setCurrentState(result ? SUCCESS : ERROR);
        }

        printDateLastUpdate();

    }
}
