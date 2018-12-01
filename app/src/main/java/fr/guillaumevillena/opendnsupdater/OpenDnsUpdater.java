package fr.guillaumevillena.opendnsupdater;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import fr.guillaumevillena.opendnsupdater.VpnService.service.OpenDnsVpnService;
import fr.guillaumevillena.opendnsupdater.VpnService.util.server.DNSServer;
import fr.guillaumevillena.opendnsupdater.VpnService.util.server.DNSServerHelper;


public class OpenDnsUpdater extends Application {

    public static final List<DNSServer> DNS_SERVERS = new ArrayList<DNSServer>() {{
        add(new DNSServer("208.67.222.222", R.string.server_opendns_primary));
        add(new DNSServer("208.67.220.220", R.string.server_opendns_secondary));
    }};
    private static final String SHORTCUT_ID_ACTIVATE = "shortcut_activate";

    private static OpenDnsUpdater instance = null;
    private SharedPreferences prefs;

    public static SharedPreferences getPrefs() {
        return getInstance().prefs;
    }

    public static Intent getServiceIntent(Context context) {
        return new Intent(context, OpenDnsVpnService.class);
    }

    public static boolean switchService() {
        if (OpenDnsVpnService.isActivated()) {
            deactivateService(instance);
            return false;
        } else {
            activateService(instance);
            return true;
        }
    }

    public static boolean activateService(Context context) {
        Intent intent = VpnService.prepare(context);
        if (intent != null) {
            return false;
        } else {
            OpenDnsVpnService.primaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getPrimary());
            OpenDnsVpnService.secondaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getSecondary());
            context.startService(OpenDnsUpdater.getServiceIntent(context).setAction(OpenDnsVpnService.ACTION_ACTIVATE));
            return true;
        }
    }

    public static void deactivateService(Context context) {
        context.startService(getServiceIntent(context).setAction(OpenDnsVpnService.ACTION_DEACTIVATE));
        context.stopService(getServiceIntent(context));
    }

    public static OpenDnsUpdater getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;
        PreferenceManager.setDefaultValues(this, R.xml.perf_settings, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onTerminate() {
        Log.d("OpenDnsUpdater", "onTerminate");
        super.onTerminate();

        instance = null;
        prefs = null;
    }
}