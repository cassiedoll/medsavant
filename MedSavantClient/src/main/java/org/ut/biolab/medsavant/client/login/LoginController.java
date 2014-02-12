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
package org.ut.biolab.medsavant.client.login;

import java.net.NoRouteToHostException;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.concurrent.Semaphore;
import javax.swing.SwingUtilities;
import org.apache.commons.httpclient.NameValuePair;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ut.biolab.medsavant.MedSavantClient;
import org.ut.biolab.medsavant.client.controller.SettingsController;
import org.ut.biolab.medsavant.shared.model.UserLevel;
import org.ut.biolab.medsavant.client.project.ProjectChooser;
import org.ut.biolab.medsavant.client.project.ProjectController;
import org.ut.biolab.medsavant.client.project.ProjectWizard;
import org.ut.biolab.medsavant.shared.serverapi.LogManagerAdapter.LogType;
import org.ut.biolab.medsavant.client.util.ClientMiscUtils;
import org.ut.biolab.medsavant.client.util.Controller;
import org.ut.biolab.medsavant.client.util.MedSavantWorker;
import org.ut.biolab.medsavant.client.view.splash.MedSavantServerInfo;
import org.ut.biolab.medsavant.client.view.util.DialogUtils;
import org.ut.biolab.medsavant.shared.util.VersionSettings;
import org.ut.biolab.medsavant.shared.util.WebResources;
import org.ut.biolab.savant.analytics.savantanalytics.AnalyticsAgent;

/**
 *
 * @author mfiume
 */
public class LoginController extends Controller<LoginEvent> {

    private static final Log LOG = LogFactory.getLog(LoginController.class);
    private final static Object EVENT_LOCK = new Object();
    private static LoginController instance;
    private String userName;
    private String password;
    private UserLevel level;
    private boolean loggedIn = false;
    private static String sessionId;

    public static String getSessionID() {
        return sessionId;
    }

    public static LoginController getInstance() {
        if (instance == null) {
            instance = new LoginController();
        }
        return instance;
    }
    private String dbname;
    private String serverAddress;

    private synchronized void setLoggedIn(final boolean loggedIn) {

        try {
            AnalyticsAgent.log(
                    new NameValuePair("login-event", loggedIn ? "LoggedIn" : "LoggedOut"));
        } catch (Exception e) {
        }
        
        Thread t = new Thread() {
            @Override
            public void run() {
                
                synchronized (EVENT_LOCK) {
                    LoginController.this.loggedIn = loggedIn;

                    if (loggedIn) {
                        fireEvent(new LoginEvent(LoginEvent.Type.LOGGED_IN));
                    } else {
                        unregister();
                        fireEvent(new LoginEvent(LoginEvent.Type.LOGGED_OUT));
                    }
                    
                    if (!loggedIn) {
                        if (!SettingsController.getInstance().getRememberPassword()) {
                            password = "";
                        }
                    }
                }
            }
        };
        t.start();
    }

    public String getPassword() {
        return password;
    }

    public String getUserName() {
        return userName;
    }

    public UserLevel getUserLevel() {
        return level;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    private void finishLogin(String un, String pw) {
        //determine privileges
        level = UserLevel.NONE;
        try {
            LOG.info("Starting session...");
            sessionId = MedSavantClient.SessionManager.registerNewSession(un, pw, dbname);
            LOG.info("... done.  My session ID is: " + sessionId);

            if (userName.equals("root")) {
                level = UserLevel.ADMIN;
            } else {
                level = MedSavantClient.UserManager.getUserLevel(sessionId, userName);
            }
        } catch (Exception ex) {
            fireEvent(new LoginEvent(ex));
            return;
        }

        SettingsController settings = SettingsController.getInstance();
        settings.setUsername(un);
        if (settings.getRememberPassword()) {
            settings.setPassword(pw);
        }else{
            settings.setPassword("");
        }

        //test connection
        try {
            MedSavantClient.SessionManager.testConnection(sessionId);
        } catch (Exception ex) {
            fireEvent(new LoginEvent(ex));
            return;
        }

        //check server version
        try {
            String clientVersion = VersionSettings.getVersionString();
            String serverVersion = MedSavantClient.SettingsManager.getServerVersion();
            
            if (!VersionSettings.isClientCompatibleWithServer(clientVersion, serverVersion)) {
                DialogUtils.displayMessage("Version Mismatch", "<html>Your client version (" + clientVersion + ") is not compatible with the server (" + serverVersion + ").<br>Visit " + WebResources.URL + " to get the correct version.</html>");
                fireEvent(new LoginEvent(LoginEvent.Type.LOGIN_FAILED));
                return;
            }
        } catch (Exception ex) {
            LOG.error("Error comparing versions.", ex);
            ex.printStackTrace();
            DialogUtils.displayMessage("Problem Comparing Versions", 
                    "<html>We could not determine compatibility between MedSavant and your database.<br>"
                            + "Please ensure that your versions are compatible before continuing.</html>");
        }
        try {
            LOG.info("Setting up project");
            if (setProject()) {
                LOG.info("Finalizing login");
                setLoggedIn(true);
            } else {
                fireEvent(new LoginEvent(LoginEvent.Type.LOGIN_FAILED));
            }
        } catch (Exception ex) {
            ClientMiscUtils.reportError("Error signing in: %s", ex);
            fireEvent(new LoginEvent(LoginEvent.Type.LOGIN_FAILED));
        }
    }
    private Semaphore semLogin = new Semaphore(1, true);
    private MedSavantWorker<Void> currentLoginThread;

    public void cancelCurrentLoginAttempt() { //idempotent
        if (currentLoginThread != null) {
            LOG.info("Cancelling sign in");
            currentLoginThread.cancel(true);
        }
    }

    public synchronized void login(MedSavantServerInfo server) {
        login(server.getUsername(), server.getPassword(), server.getDatabase(), server.getHost(), server.getPort() + "");
    }
    
    public synchronized void login(final String un, final String pw, final String dbname, final String serverAddress, final String serverPort) {
        //init registry
        try {
            cancelCurrentLoginAttempt();

            currentLoginThread = new MedSavantWorker<Void>("Login") {
                @Override
                protected Void doInBackground() {
                    try {
                        LOG.info("Initializing registry");
                        MedSavantClient.initializeRegistry(serverAddress, serverPort);
                    } catch (final Exception ex) { //server isn't running medsavant, or is down.
                        if (!this.isCancelled()) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    fireEvent(new LoginEvent(new NoRouteToHostException("Can't connect to " + serverAddress + ": " + serverPort)));
                                }
                            });

                            cancelCurrentLoginAttempt();
                        }
                    }

                    return null;
                }

                @Override
                protected void showSuccess(Void result) {
                    if (this.isCancelled()) {
                        LOG.info("Signed in, but cancelled");
                        return;
                    }
                    try {
                        semLogin.acquire();
                        getInstance().serverAddress = serverAddress;
                        getInstance().dbname = dbname;
                        //register session
                        userName = un;
                        password = pw;
                        if (!LoginController.getInstance().isLoggedIn()) {
                            
                            finishLogin(un, pw);
                        }
                        semLogin.release();
                    } catch (Exception ex) {
                        LOG.info("Aborted login...");
                    }
                }
            };
            currentLoginThread.execute();
            //MedSavantClient.initializeRegistry(serverAddress, serverPort);
            if (Thread.interrupted()) {
                LOG.info("Aborted login...");
                return;
            }
        } catch (Exception ex) {
            fireEvent(new LoginEvent(ex));
            return;
        }

    }

    /**
     * Logout
     */
    public void logout() {
        try {
            LOG.info("Logging out...");
            setLoggedIn(false);
            AnalyticsAgent.onEndSession(true);
            this.unregister();
        } catch (Exception ex) {
        }
    }

    public void unregister() {

        // queue session for unregistration
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    MedSavantClient.SessionManager.unregisterSession(sessionId);
                } catch (RemoteException ex) {
                    LOG.info("Error while logging out: " + ClientMiscUtils.getMessage(ex));
                } catch (Exception ex) {
                    LOG.info("Error while logging out: " + ClientMiscUtils.getMessage(ex));
                }
            }
        };
        new Thread(r).start();
    }

    /**
     * Set the initial project. If there are none, prompt to create one. If
     * there is one, select it. If there is more than one, prompt to select one.
     */
    private boolean setProject() throws SQLException, RemoteException {
        ProjectController pc = ProjectController.getInstance();
        String[] projNames = pc.getProjectNames();

        String proj = null;
        if (projNames.length == 0) {
            if (level == UserLevel.ADMIN) {
                DialogUtils.displayMessage("Welcome to MedSavant", "To begin using MedSavant, you will need to create a project.");
                //if (result == DialogUtils.OK) {
                new ProjectWizard().setVisible(true);
                projNames = pc.getProjectNames();
                if (projNames.length > 0) {
                    proj = projNames[0];
                }

            } else {
                DialogUtils.displayMessage("Welcome to MedSavant", "No projects have been started. Please contact your administrator.");
            }
        } else if (projNames.length == 1) {
            proj = projNames[0];
        } else {
            ProjectChooser d = new ProjectChooser(projNames);
            d.setVisible(true);
            proj = d.getSelected();
        }
        if (proj != null) {
            pc.setProject(proj);
            pc.setDefaultReference();
        }
        return proj != null;
    }

    public String getServerAddress() {
        return this.serverAddress;
    }

    public String getDatabaseName() {
        return this.dbname;
    }

    
}
