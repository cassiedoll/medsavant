package org.ut.biolab.medsavant.server;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ut.biolab.medsavant.clientapi.ClientCallbackAdapter;
import org.ut.biolab.medsavant.db.util.ConnectionController;
import org.ut.biolab.medsavant.db.util.SessionConnection;
import org.ut.biolab.medsavant.db.util.query.SettingsQueryUtil;
import org.ut.biolab.medsavant.util.MedSavantServerUnicastRemoteObject;
import org.ut.biolab.medsavant.util.MiscUtils;
import org.ut.biolab.medsavant.serverapi.SessionAdapter;
import org.ut.biolab.medsavant.server.mail.CryptoUtils;

/**
 *
 * @author mfiume
 */
public class SessionController extends MedSavantServerUnicastRemoteObject implements SessionAdapter {

    int lastSessionId = 0;
    private static SessionController instance;

    public static synchronized SessionController getInstance() throws RemoteException {
        if (instance == null) {
            instance = new SessionController();
        }
        return instance;
    }

    public SessionController() throws RemoteException {
        super();
    }

    @Override
    public synchronized String registerNewSession(String uname, String pw, String dbname) throws RemoteException {

        int newSessionIdNumber = ++lastSessionId;
        String sessionId = CryptoUtils.encrypt(newSessionIdNumber + "");
        //String sessionId = ++lastSessionId + "";

        boolean success = ConnectionController.registerCredentials(sessionId, uname, pw, dbname);

        if (success) {
            System.out.println("Registered session: " + sessionId);
            return sessionId;
        } else {
            return null;
        }
    }

    @Override
    public void unregisterSession(String sessionId) throws RemoteException, SQLException {
        ConnectionController.removeSession(sessionId);
        System.out.println("Unregistered session: " + sessionId);
    }

    @Override
    public boolean testConnection(String sessionId) throws RemoteException, SQLException {
        Connection c = ConnectionController.connectPooled(sessionId);
        if(c == null){
            return false;
        } else {
            c.close();
            return true;
        }
    }

    public String getUserForSession(String sid) {
        return ConnectionController.getUserForSession(sid);
    }

    @Override
    public void registerCallback(String sessionId, final ClientCallbackAdapter cca) throws RemoteException {
        ConnectionController.addCallback(sessionId, cca);
    }

    public String getDatabaseForSession(String sid) {
        return ConnectionController.getDatabaseForSession(sid);
    }

    public void terminateSessionsForDatabase(String dbname) {
        terminateSessionsForDatabase(dbname, null);
    }

    public void terminateAllSessions(String message) {
        for(String dbName : ConnectionController.getDbNames()){
            terminateSessionsForDatabase(dbName, message);
        }
    }

    public void terminateSessionsForDatabase(String dbname, final String message) {

        System.out.println("Terminating sessions for database " + dbname);

        List<String> sessionIDsToTerminate = new ArrayList<String>();

        for (String sid : ConnectionController.getSessionIDs()) {
            try {
                if (SessionController.getInstance().getDatabaseForSession(sid).equals(dbname)) {

                    sessionIDsToTerminate.add(sid);
                    // terminate session for this client
                }
            } catch (Exception ex) {
                Logger.getLogger(SessionController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        for (final String sid : sessionIDsToTerminate) {
            Thread t = new Thread(){
                @Override
                public void run(){
                    try {
                        System.out.print("Terminating session " + sid + "...");
                        ClientCallbackAdapter ca = ConnectionController.getCallback(sid);
                        SessionController.getInstance().unregisterSession(sid);

                        if (ca != null) {
                            ca.sessionTerminated(message);
                        }
                        System.out.println("Complete");
                    } catch (Exception ex) {
                        System.out.println("Failed");
                        Logger.getLogger(SessionController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            };
            t.start();
        }
    }
}