package org.ut.biolab.medsavant.serverapi;

import com.healthmarketscience.sqlbuilder.Condition;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.List;
import org.ut.biolab.medsavant.model.Cohort;
import org.ut.biolab.medsavant.model.SimplePatient;

/**
 *
 * @author mfiume
 */
public interface CohortQueryUtilAdapter extends Remote {

    public List<SimplePatient> getIndividualsInCohort(String sid,int projectId, int cohortId) throws SQLException, RemoteException;
    public List<String> getDNAIdsInCohort(String sid,int cohortId) throws SQLException, RemoteException;
    public List<String> getIndividualFieldFromCohort(String sid,int cohortId, String columnname) throws SQLException, RemoteException;
    public void addPatientsToCohort(String sid,int[] patientIds, int cohortId) throws SQLException, RemoteException;
    public void removePatientsFromCohort(String sid,int[] patientIds, int cohortId) throws SQLException, RemoteException;
    public List<Cohort> getCohorts(String sid,int projectId) throws SQLException, RemoteException;
    public void addCohort(String sid,int projectId, String name) throws SQLException, RemoteException;
    public void removeCohort(String sid,int cohortId) throws SQLException, RemoteException;
    public void removeCohorts(String sid,Cohort[] cohorts) throws SQLException, RemoteException;
    public List<Integer> getCohortIds(String sid,int projectId) throws SQLException, RemoteException;
    public void removePatientReferences(String sid,int projectId, int patientId) throws SQLException, RemoteException;
    public int getNumVariantsInCohort(String sid,int projectId, int referenceId, int cohortId, Condition[][] conditions) throws SQLException, InterruptedException, RemoteException;
}
