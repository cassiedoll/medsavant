/**
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.ut.biolab.medsavant;

import org.ut.biolab.medsavant.shared.serverapi.CustomTablesAdapter;
import org.ut.biolab.medsavant.shared.serverapi.OntologyManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.NetworkManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.SessionManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.UserManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.CohortManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.AnnotationManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.VariantManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.SetupAdapter;
import org.ut.biolab.medsavant.shared.serverapi.GeneSetManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.LogManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.MedSavantServerRegistry;
import org.ut.biolab.medsavant.shared.serverapi.SettingsManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.ProjectManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.NotificationManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.DBUtilsAdapter;
import org.ut.biolab.medsavant.shared.serverapi.ReferenceManagerAdapter;
import org.ut.biolab.medsavant.shared.serverapi.PatientManagerAdapter;
import java.rmi.*;
import java.rmi.registry.*;
import java.awt.Insets;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

import com.jidesoft.plaf.LookAndFeelFactory;
import gnu.getopt.Getopt;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.NoRouteToHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLHandshakeException;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.swing.UIDefaults;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.plaf.InsetsUIResource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ut.biolab.medsavant.client.controller.SettingsController;
import org.ut.biolab.medsavant.client.util.MedSavantExceptionHandler;
import org.ut.biolab.medsavant.client.util.ServerModificationInvocationHandler;
import org.ut.biolab.medsavant.shared.util.MiscUtils;
import org.ut.biolab.medsavant.client.view.MedSavantFrame;
import org.ut.biolab.medsavant.client.view.splash.SplashFrame;
import org.ut.biolab.medsavant.client.view.util.DialogUtils;
import org.ut.biolab.medsavant.shared.model.exception.LockException;
import org.ut.biolab.medsavant.shared.model.SessionExpiredException;
import org.ut.biolab.medsavant.shared.serverapi.RegionSetManagerAdapter;
import org.ut.biolab.medsavant.shared.util.VersionSettings;
import org.ut.biolab.savant.analytics.savantanalytics.AnalyticsAgent;

public class MedSavantClient implements MedSavantServerRegistry {

    private static final Log LOG = LogFactory.getLog(MedSavantClient.class);
    public static CustomTablesAdapter CustomTablesManager;
    public static AnnotationManagerAdapter AnnotationManagerAdapter;
    public static CohortManagerAdapter CohortManager;
    public static GeneSetManagerAdapter GeneSetManager;
    public static LogManagerAdapter LogManager;
    public static NetworkManagerAdapter NetworkManager;
    public static OntologyManagerAdapter OntologyManager;
    public static PatientManagerAdapter PatientManager;
    public static ProjectManagerAdapter ProjectManager;
    public static UserManagerAdapter UserManager;
    public static SessionManagerAdapter SessionManager;
    public static SettingsManagerAdapter SettingsManager;
    public static RegionSetManagerAdapter RegionSetManager;
    public static ReferenceManagerAdapter ReferenceManager;
    public static DBUtilsAdapter DBUtils;
    public static SetupAdapter SetupManager;
    public static VariantManagerAdapter VariantManager; //proxy
    public static NotificationManagerAdapter NotificationManager;
    public static boolean initialized = false;
    //private static MedSavantFrame frame;
    //private static String restartCommand;
    private static String[] restartCommand;
    private static boolean restarting = false;
    private static final Object managerLock = new Object();

    //Proxy the adapters to process annotations and fire events to the cache controller.
    private static void initProxies() {
        VariantManager = (VariantManagerAdapter) Proxy.newProxyInstance(
                VariantManager.getClass().getClassLoader(),
                new Class[]{VariantManagerAdapter.class},
                new ServerModificationInvocationHandler<VariantManagerAdapter>(VariantManager));

        CohortManager = (CohortManagerAdapter) Proxy.newProxyInstance(
                CohortManager.getClass().getClassLoader(),
                new Class[]{CohortManagerAdapter.class},
                new ServerModificationInvocationHandler<CohortManagerAdapter>(CohortManager));

        PatientManager = (PatientManagerAdapter) Proxy.newProxyInstance(
                PatientManager.getClass().getClassLoader(),
                new Class[]{PatientManagerAdapter.class},
                new ServerModificationInvocationHandler<PatientManagerAdapter>(PatientManager));

        RegionSetManager = (RegionSetManagerAdapter) Proxy.newProxyInstance(
                RegionSetManager.getClass().getClassLoader(),
                new Class[]{RegionSetManagerAdapter.class},
                new ServerModificationInvocationHandler<RegionSetManagerAdapter>(RegionSetManager));

        OntologyManager = (OntologyManagerAdapter) Proxy.newProxyInstance(
                OntologyManager.getClass().getClassLoader(),
                new Class[]{OntologyManagerAdapter.class},
                new ServerModificationInvocationHandler<OntologyManagerAdapter>(OntologyManager));
    }

    /**
     * Restarts MedSavant (This function has NOT been tested with Web Start)
     */
    public static void restart() {
        if (!restarting) {
            restarting = true;
            try {
                /*  if (msg != null) {
                 DialogUtils.displayMessage("MedSavant needs to restart.", msg);
                 }*/
                SettingsController.getInstance().setBoolean("BootFromLogout", true);
                System.out.println("Restarting with "+restartCommand[0]);
                Runtime.getRuntime().exec(restartCommand);
                System.exit(0);
            } catch (IOException e) { //thrown by exec
                DialogUtils.displayError("Error restarting MedSavant. Please restart MedSavant manually.");
                LOG.error(e);
            } catch (Exception e) {
                LOG.error(e);
            }
        }
    }

    public static void setRestartCommand(String[] args) {
        List<String> restartCommandList = new ArrayList<String>();
        
        String launcher = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        restartCommandList.add(launcher);
        for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            restartCommandList.add(jvmArg);            
        }
        restartCommandList.add("-cp");
        restartCommandList.add(ManagementFactory.getRuntimeMXBean().getClassPath());
        restartCommandList.add(MedSavantClient.class.getName());
        for (String arg : args){
            restartCommandList.add(arg);
        }
        
        restartCommand = restartCommandList.toArray(new String[restartCommandList.size()]);        
    }

    static public void main(String args[]) {
        AnalyticsAgent.onStartSession("MedSavant", VersionSettings.getVersionString());

        // Avoids "Comparison method violates its general contract" bug.
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7075600
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
        setRestartCommand(args);
        setExceptionHandler();

        verifyJIDE();
        setLAF();

        // initialize settings
        SettingsController.getInstance();

        Getopt g = new Getopt("MedSavant", args, "h:p:d:u:w:");
        int c;

        while ((c = g.getopt()) != -1) {
            switch (c) {
                case 'h':
                    String host = g.getOptarg();
                    SettingsController.getInstance().setServerAddress(host);
                    break;
                case 'p':
                    int port = Integer.parseInt(g.getOptarg());
                    SettingsController.getInstance().setServerPort(port + "");
                    break;
                case 'd':
                    String dbname = g.getOptarg();
                    SettingsController.getInstance().setDBName(dbname);
                    break;
                case 'u':
                    String username = g.getOptarg();
                    SettingsController.getInstance().setUsername(username);
                    break;
                case 'w':
                    String password = g.getOptarg();
                    SettingsController.getInstance().setPassword(password);
                    break;
                case '?':
                    break; // getopt() already printed an error
                default:
                    System.out.print("getopt() returned " + c + "\n");
            }
        }

        LOG.info("MedSavant booted");
        
        SplashFrame loginFrame = new SplashFrame();
        loginFrame.setVisible(true);

        
    }

    public static void initializeRegistry(String serverAddress, String serverPort) throws RemoteException, NotBoundException, NoRouteToHostException, ConnectIOException {

        if (initialized) {
            return;
        }
        
        
        int port = (new Integer(serverPort)).intValue();

        Registry registry;

        LOG.debug("Connecting to MedSavantServerEngine @ " + serverAddress + ":" + serverPort + "...");

        try {
            registry = LocateRegistry.getRegistry(serverAddress, port, new SslRMIClientSocketFactory());
            LOG.debug("Retrieving adapters...");
            setAdaptersFromRegistry(registry);
            LOG.info("Connected with SSL/TLS Encryption");
        } catch (ConnectIOException ex) {
            if (ex.getCause() instanceof SSLHandshakeException) {
                registry = LocateRegistry.getRegistry(serverAddress, port);
                LOG.debug("Retrieving adapters...");
                setAdaptersFromRegistry(registry);
                LOG.info("Connected without SSL/TLS encryption");
            }
        }
        LOG.debug("Done");

    }

    private static void setAdaptersFromRegistry(Registry registry) throws RemoteException, NotBoundException, NoRouteToHostException, ConnectIOException {
        CustomTablesAdapter CustomTablesManager;
        AnnotationManagerAdapter AnnotationManagerAdapter;
        CohortManagerAdapter CohortManager;
        GeneSetManagerAdapter GeneSetManager;
        LogManagerAdapter LogManager;
        NetworkManagerAdapter NetworkManager;
        OntologyManagerAdapter OntologyManager;
        PatientManagerAdapter PatientManager;
        ProjectManagerAdapter ProjectManager;
        UserManagerAdapter UserManager;
        SessionManagerAdapter SessionManager;
        SettingsManagerAdapter SettingsManager;
        RegionSetManagerAdapter RegionSetManager;
        ReferenceManagerAdapter ReferenceManager;
        DBUtilsAdapter DBUtils;
        SetupAdapter SetupManager;
        VariantManagerAdapter VariantManager;
        NotificationManagerAdapter NotificationManager;

        //   try {
        AnnotationManagerAdapter = (AnnotationManagerAdapter) registry.lookup(ANNOTATION_MANAGER);
        CohortManager = (CohortManagerAdapter) (registry.lookup(COHORT_MANAGER));
        LogManager = (LogManagerAdapter) registry.lookup(LOG_MANAGER);
        NetworkManager = (NetworkManagerAdapter) registry.lookup(NETWORK_MANAGER);
        OntologyManager = (OntologyManagerAdapter) registry.lookup(ONTOLOGY_MANAGER);
        PatientManager = (PatientManagerAdapter) registry.lookup(PATIENT_MANAGER);
        ProjectManager = (ProjectManagerAdapter) registry.lookup(PROJECT_MANAGER);
        GeneSetManager = (GeneSetManagerAdapter) registry.lookup(GENE_SET_MANAGER);
        ReferenceManager = (ReferenceManagerAdapter) registry.lookup(REFERENCE_MANAGER);
        RegionSetManager = (RegionSetManagerAdapter) registry.lookup(REGION_SET_MANAGER);
        SessionManager = (SessionManagerAdapter) registry.lookup(SESSION_MANAGER);
        SettingsManager = (SettingsManagerAdapter) registry.lookup(SETTINGS_MANAGER);
        UserManager = (UserManagerAdapter) registry.lookup(USER_MANAGER);
        VariantManager = (VariantManagerAdapter) registry.lookup(VARIANT_MANAGER);
        DBUtils = (DBUtilsAdapter) registry.lookup(DB_UTIL_MANAGER);
        SetupManager = (SetupAdapter) registry.lookup(SETUP_MANAGER);
        CustomTablesManager = (CustomTablesAdapter) registry.lookup(CUSTOM_TABLES_MANAGER);
        NotificationManager = (NotificationManagerAdapter) registry.lookup(NOTIFICATION_MANAGER);

        if (Thread.interrupted()) {
            return;
        }

        synchronized (managerLock) {
            MedSavantClient.CustomTablesManager = CustomTablesManager;
            MedSavantClient.AnnotationManagerAdapter = AnnotationManagerAdapter;
            MedSavantClient.CohortManager = CohortManager;
            MedSavantClient.GeneSetManager = GeneSetManager;
            MedSavantClient.LogManager = LogManager;
            MedSavantClient.NetworkManager = NetworkManager;
            MedSavantClient.OntologyManager = OntologyManager;
            MedSavantClient.PatientManager = PatientManager;
            MedSavantClient.ProjectManager = ProjectManager;
            MedSavantClient.UserManager = UserManager;
            MedSavantClient.SessionManager = SessionManager;
            MedSavantClient.SettingsManager = SettingsManager;
            MedSavantClient.RegionSetManager = RegionSetManager;
            MedSavantClient.ReferenceManager = ReferenceManager;
            MedSavantClient.DBUtils = DBUtils;
            MedSavantClient.SetupManager = SetupManager;
            MedSavantClient.VariantManager = VariantManager;
            MedSavantClient.NotificationManager = NotificationManager;

            initProxies();
        }
    }

    private static void setLAF() {
        try {

            // UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel"); //Metal works with sliders.
            //UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel"); //GTK doesn't work with sliders.
            //UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"); //Nimbus doesn't work with sliders.
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                LOG.debug("Installed LAF: " + info.getName() + " class: " + info.getClassName());
            }
            LOG.debug("System LAF is: " + UIManager.getSystemLookAndFeelClassName());
            LOG.debug("Cross platform LAF is: " + UIManager.getCrossPlatformLookAndFeelClassName());

            LookAndFeelFactory.addUIDefaultsInitializer(new LookAndFeelFactory.UIDefaultsInitializer() {
                public void initialize(UIDefaults defaults) {
                    Map<String, Object> defaultValues = new HashMap<String, Object>();
                    defaultValues.put("Slider.trackWidth", new Integer(7));
                    defaultValues.put("Slider.majorTickLength", new Integer(6));
                    defaultValues.put("Slider.highlight", new ColorUIResource(255, 255, 255));
                    defaultValues.put("Slider.horizontalThumbIcon", javax.swing.plaf.metal.MetalIconFactory.getHorizontalSliderThumbIcon());
                    defaultValues.put("Slider.verticalThumbIcon", javax.swing.plaf.metal.MetalIconFactory.getVerticalSliderThumbIcon());
                    defaultValues.put("Slider.focusInsets", new InsetsUIResource(0, 0, 0, 0));

                    for (Map.Entry<String, Object> e : defaultValues.entrySet()) {
                        if (defaults.get(e.getKey()) == null) {
                            LOG.debug("Missing key " + e.getKey() + ", using default value " + e.getValue());
                            defaults.put(e.getKey(), e.getValue());
                        } else {
                            LOG.debug("Found key " + e.getKey() + " with value " + defaults.get(e.getKey()));
                        }
                    }
                }
            });

            if (MiscUtils.WINDOWS) {
                LookAndFeelFactory.installJideExtension(LookAndFeelFactory.XERTO_STYLE_WITHOUT_MENU);
            } else {
                LookAndFeelFactory.installJideExtension();
            }

            LookAndFeelFactory.installDefaultLookAndFeelAndExtension();

            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");

            UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));

            //tooltips
            UIManager.put("ToolTip.background", new ColorUIResource(255, 255, 255));
            ToolTipManager.sharedInstance().setDismissDelay(8000);
            ToolTipManager.sharedInstance().setInitialDelay(500);

        } catch (Exception x) {
            LOG.error("Unable to install look & feel.", x);
        }

    }

    private static void verifyJIDE() {
        com.jidesoft.utils.Lm.verifyLicense("Marc Fiume", "Savant Genome Browser", "1BimsQGmP.vjmoMbfkPdyh0gs3bl3932");
    }

    private static void setExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        LOG.info("Global exception handler caught: " + t.getName() + ": " + e);

                        if (e instanceof InvocationTargetException) {
                            e = ((InvocationTargetException) e).getCause();
                        }

                        if (e instanceof SessionExpiredException) {
                            SessionExpiredException see = (SessionExpiredException) e;
                            MedSavantExceptionHandler.handleSessionExpiredException(see);
                            return;
                        }
                        
                        if (e instanceof LockException) {
                            DialogUtils.displayMessage("Cannot modify database", "<html>Another process is making changes.<br/>Please try again later.</html>");
                            return;
                        }

                        e.printStackTrace();
                        DialogUtils.displayException("Error", e.getLocalizedMessage(), e);
                    }
                });
    }
}
