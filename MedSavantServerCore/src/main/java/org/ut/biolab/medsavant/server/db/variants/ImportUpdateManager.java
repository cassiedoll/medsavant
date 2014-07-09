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
package org.ut.biolab.medsavant.server.db.variants;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import org.ut.biolab.medsavant.server.serverapi.VariantManager;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.medsavant.annotation.impl.JannovarVCFPreProcessor;
import org.medsavant.api.annotation.AnnotationPipeline;
import org.medsavant.api.annotation.TabixAnnotation;
import org.medsavant.api.annotation.TabixAnnotator;
import org.medsavant.api.annotation.TabixVariantAnnotator;
import org.medsavant.api.annotation.impl.AnnotationPipelineImpl;
import org.medsavant.api.annotation.impl.TabixAnnotatorImpl;
import org.medsavant.api.common.GlobalWrapper;
import org.medsavant.api.common.MedSavantSecurityException;
import org.medsavant.api.common.MedSavantSession;
import org.medsavant.api.common.storage.MedSavantFile;
import org.medsavant.api.vcfstorage.MedSavantFileDirectoryException;
import org.medsavant.api.vcfstorage.MedSavantFileSubDirectory;
import org.medsavant.api.common.impl.MedSavantServerJob;
import org.ut.biolab.medsavant.server.MedSavantServerEngine;
import org.ut.biolab.medsavant.server.db.ConnectionController;
import org.ut.biolab.medsavant.server.db.MedSavantDatabase;
import static org.ut.biolab.medsavant.server.db.MedSavantDatabase.VariantFileTableSchema;
import org.ut.biolab.medsavant.server.db.util.CustomTables;
import org.ut.biolab.medsavant.server.serverapi.AnnotationLogManager;
import org.ut.biolab.medsavant.server.serverapi.AnnotationManager;
import org.ut.biolab.medsavant.server.serverapi.PatientManager;
import org.ut.biolab.medsavant.server.serverapi.ProjectManager;
import org.ut.biolab.medsavant.shared.db.TableSchema;
import org.ut.biolab.medsavant.shared.format.BasicPatientColumns;
import org.ut.biolab.medsavant.shared.format.BasicVariantColumns;
import org.ut.biolab.medsavant.shared.format.CustomField;
import org.ut.biolab.medsavant.shared.model.Annotation;
import org.ut.biolab.medsavant.shared.model.AnnotationLog;
import org.ut.biolab.medsavant.shared.model.SessionExpiredException;
import org.ut.biolab.medsavant.shared.util.BinaryConditionMS;
import org.ut.biolab.medsavant.shared.util.MedSavantFileUtils;
import org.ut.biolab.medsavant.shared.util.MedSavantLegacyUtils;

/**
 *
 * @author mfiume
 */
public class ImportUpdateManager {

    private static final Log LOG = LogFactory.getLog(ImportUpdateManager.class);

    private static List<MedSavantFile> registerNewVCFs(MedSavantSession session, File[] allVCFFiles) throws IOException, MedSavantSecurityException, MedSavantFileDirectoryException {
        MedSavantFileSubDirectory dir = null;
        try {
            dir = MedSavantServerEngine.getInstance().getServerContext().getMedSavantFileDirectory().getVCFDirectory(session);
        } catch (MedSavantSecurityException mse) {
            throw mse;
        } catch (MedSavantFileDirectoryException msfde) {
            throw msfde;
        }

        List<MedSavantFile> vcfList = new ArrayList<MedSavantFile>(allVCFFiles.length);

        try {
            for (File f : allVCFFiles) {
                MedSavantFile registeredFile = dir.registerMedSavantFile(session, f.getName());
                LOG.info("Copying file " + f.getName() + " to directory");
                MedSavantFileUtils.copy(f, registeredFile);

                vcfList.add(registeredFile);
            //            int fileID = VariantManager.addEntryToFileTable(sessID, updateID, projectID, referenceID, originalVCF);
                //register the file.
            }
            return vcfList;

        } catch (MedSavantFileDirectoryException msfde) {
            for (File f : allVCFFiles) {
                dir.deleteMedSavantFile(session, f.getName());
            }
            throw msfde;
        } catch (IOException ie) {
            for (File f : allVCFFiles) {
                dir.deleteMedSavantFile(session, f.getName());
            }
            throw ie;
        }
    }

    public static void osgi_doImport(final String sessionID, final int projectID, final int referenceID, final File[] allVCFFiles, final boolean includeHomozygousReferenceCalls, final boolean preAnnotateWithJannovar, final boolean doPhasing, final String[][] tags) throws IOException, SQLException, Exception {
        final MedSavantSession session = MedSavantLegacyUtils.getMedSavantSession(sessionID);
        DateFormat dateFormat = new SimpleDateFormat("MMM dd - HH:mm:ss");

        MedSavantServerJob importJob = new MedSavantServerJob(session.getUser().getUsername(), session.getProject().getDatabaseName() + ": VCF Import, " + dateFormat.format(new Date()), null) {
            @Override
            public boolean run() throws Exception {
                
                AnnotationPipeline apl = new AnnotationPipelineImpl(); //this constructor is temporary.
                
                if (doPhasing || preAnnotateWithJannovar) {
                    apl.addVCFPreProcessor(new JannovarVCFPreProcessor(GlobalWrapper.getServerContext()));
                }
                if (doPhasing) {
                    // Put phasing code here.
                    // apb.addVCFPreProcessor(new PhaserPreProcessor());
                }

                //Lookup instance of the tabix anntoator.
                TabixAnnotator ta = new TabixAnnotatorImpl();

                //Look up all annotations registered to this project and reference.
                TabixAnnotation annotations[] = AnnotationManager.getInstance().getAnnotationFormats(sessionID, projectID, referenceID);
                for (TabixAnnotation tan : annotations) {
                    //Lookup tabix annotation.            
                    TabixVariantAnnotator tva = new TabixVariantAnnotator(ta, tan); //this is the local wrapper

                    //Add it to our annotation pipeline.
                    apl.addVariantAnnotator(tva);
                }
                
                //Register VCF files with directory registry.
                List<MedSavantFile> registeredFiles = registerNewVCFs(session, allVCFFiles);
                
                
                apl.annotate(session, this.getJobProgressMonitor(), registeredFiles);
                return true;
            }
        };
        MedSavantServerEngine.getInstance().getServerContext().getExecutionService().runJobInCurrentThread(importJob);
                   
        //NOTE: It shouldn't be necessary to return an update ID on import.
    }

    /**
     * IMPORT FILES INTO AN EXISTING TABLE
     */
    /*
    public static int doImport(final String sessionID, final int projectID, final int referenceID, final File[] allVCFFiles, final boolean includeHomozygousReferenceCalls, final boolean preAnnotateWithJannovar, final boolean doPhasing, final String[][] tags) throws IOException, SQLException, Exception {

        String userId = SessionManager.getInstance().getUserForSession(sessionID);
        final String database = SessionManager.getInstance().getDatabaseForSession(sessionID);
        DateFormat dateFormat = new SimpleDateFormat("MMM dd - HH:mm:ss");
        final int updateID = AnnotationLogManager.getInstance().addAnnotationLogEntry(sessionID, projectID, referenceID, AnnotationLog.Action.ADD_VARIANTS);
        //Create a dummy job to contain all the sub jobs (threads).
        MedSavantServerJob importJob = new MedSavantServerJob(userId, database + ": VCF Import, " + dateFormat.format(new Date()), null) {
            @Override
            public boolean run() throws Exception {
                //Assert database state reflects that of published variants.
                ProjectManager.getInstance().restorePublishedFileTable(sessionID);

                LOG.info("Starting import");
                File workingDirectory = DirectorySettings.generateDateStampDirectory(DirectorySettings.getTmpDirectory());
                LOG.info("Working directory is " + workingDirectory.getAbsolutePath());
                int startFile = 0;
                int endFile = Math.min(allVCFFiles.length, MedSavantServerEngine.getInstance().getMaxThreads());
                List<TSVFile> allAnnotatedFiles = new ArrayList<TSVFile>();
                int[] allAnnotationIDs = new int[0];
                File workingDir = null;

                //Create directory where annotated TSV files will be moved to.  In the future, dumping variants from the main table
                //won't be necessary as we can reuse the TSVs.
                File finalAnnotationDestDir = DirectorySettings.getAnnotatedTSVDirectory(database, projectID, referenceID);
                if (!finalAnnotationDestDir.exists()) {
                    finalAnnotationDestDir.mkdirs();
                }

                try {
                    getJobProgressMonitor().setMessage("Preparing database for new variants...");
                    int[] annotationIDs = AnnotationManager.getInstance().getAnnotationIDs(sessionID, projectID, referenceID);
                    CustomField[] customFields = ProjectManager.getInstance().getCustomVariantFields(sessionID, projectID, referenceID, ProjectManager.getInstance().getNewestUpdateID(sessionID, projectID, referenceID, false));
                    int numVariantsImported = 0;
                    ProjectManager.getInstance().setCustomVariantFields(sessionID, projectID, referenceID, updateID, customFields);

                    //Process the given files in groups no larger then MedSavantServerEngine.getMaxThreads().                      
                    while (startFile < endFile) {
                        workingDir = createSubdir(workingDirectory, "annotate_upload");
                        File[] vcfFiles = ArrayUtils.subarray(allVCFFiles, startFile, endFile);
                        getJobProgressMonitor().setMessage("Preparing VCFs " + startFile + " - " + endFile + " of " + allVCFFiles.length + " for further annotations");
                        // prepare for annotation
                        TSVFile[] importedTSVFiles
                                = null;

                        getJobProgressMonitor().setMessage("Annotating VCFs " + startFile + " - " + endFile + " of " + allVCFFiles.length);

                        for (TSVFile file : importedTSVFiles) {
                            numVariantsImported += file.getNumLines();
                        }

                        TSVFile[] annotatedFiles = null;

                        //Save the annotated Files, delete everything else.  These can be reused if annotations are later changed.
                        for (TSVFile annotatedFile : annotatedFiles) {
                            
                          //   File f = annotatedFile.getFile();
                          //   File dst = new File(finalAnnotationDestDir, f.getName());
                          //   LOG.info("Renaming " + annotatedFile.getFile().getAbsolutePath() + " to " + dst.getAbsolutePath());
                          //   if (!annotatedFile.getFile().renameTo(dst)) {
                          //   LOG.error("Couldn't rename " + annotatedFile.getFile().getAbsolutePath() + " to " + dst.getAbsolutePath());
                          //   }
                             
                            File dst = new File(finalAnnotationDestDir, annotatedFile.getFile().getName());
                            annotatedFile.moveTo(dst);
                            allAnnotatedFiles.add(annotatedFile);
                        }

                        allAnnotationIDs = ArrayUtils.addAll(allAnnotationIDs, annotationIDs);
                        if (VariantManager.REMOVE_WORKING_DIR) {
                            MiscUtils.deleteDirectory(workingDir);
                        }

                        startFile += MedSavantServerEngine.getInstance().getMaxThreads();
                        endFile = Math.min(allVCFFiles.length, endFile + MedSavantServerEngine.getInstance().getMaxThreads());
                    }//end while

                    getJobProgressMonitor().setMessage("Done annotating, loading all variants into database.");
                    workingDir = createSubdir(workingDirectory, "annotate_upload");
                    String currentTableName
                            = ProjectManager.getInstance().getNameOfVariantTable(sessionID, projectID, referenceID, true, false);

                    String tableNameSubset = ProjectManager.getInstance().addVariantTableToDatabase(
                            sessionID,
                            projectID,
                            referenceID,
                            updateID,
                            annotationIDs,
                            customFields,
                            true);

                    appendTSVFilesToVariantTable(sessionID, projectID, referenceID, updateID, allAnnotatedFiles.toArray(new TSVFile[allAnnotatedFiles.size()]), currentTableName);

                    //Recreate subset table.
                    int[] fileIds = getFileIds(sessionID, projectID, referenceID, updateID);
                    String viewName = DBSettings.getVariantViewName(projectID, referenceID);
                    int totalNumVariants = numVariantsImported + VariantManager.getInstance().getNumFilteredVariantsHelper(sessionID, viewName, new Condition[0][]);
                    TableSchema publishedVariantView = CustomTables.getInstance().getCustomTableSchema(sessionID, viewName);
                    TableSchema unpublishedVariantTable = CustomTables.getInstance().getCustomTableSchema(sessionID, currentTableName);
                    TableSchema publishedFileTable = MedSavantDatabase.VariantFileIBTableSchema;
                    TableSchema unpublishedFileTable = MedSavantDatabase.VariantFileTableSchema;

                    TableSchema tmpTable = null;
                    try {
                        //Copy unpublished file table to a temporary infobright table.                            
                        tmpTable = SetupMedSavantDatabase.makeTemporaryVariantFileIBTable(sessionID);
                        SelectQuery query = new SelectQuery();
                        query.addAllTableColumns(unpublishedFileTable.getTable());
                        query.addCondition(ComboCondition.and(
                                BinaryCondition.equalTo(unpublishedFileTable.getDBColumn(VariantFileTableSchema.COLUMNNAME_OF_PROJECT_ID), projectID),
                                BinaryCondition.equalTo(unpublishedFileTable.getDBColumn(VariantFileTableSchema.COLUMNNAME_OF_REFERENCE_ID), referenceID)
                        ));
                        DBUtils.copyQueryResultToNewTable(sessionID, query, tmpTable.getTableName(), this);

                        //Join against the unpublished variants against the temporary table to get all previously published 
                        //variants, plus the newly imported variants. 
                        query = new SelectQuery();
                        query.addAllTableColumns(unpublishedVariantTable.getTable());
                        query.addJoin(JoinType.INNER,
                                unpublishedVariantTable.getTable(),
                                tmpTable.getTable(),
                                BinaryCondition.equalTo(unpublishedVariantTable.getDBColumn(BasicVariantColumns.FILE_ID), tmpTable.getDBColumn(VariantFileTableSchema.COLUMNNAME_OF_FILE_ID)));

                        //Restrict the results of the join using the condition RAND() < getSubsetFraction(totalNumVariants)...  This
                        //takes a uniformly random sample from the table of approximately 
                        //totalNumVariants*getSubsetFraction(totalNumVariants) variants.
                        query.addCondition(BinaryCondition.lessThan(
                                new FunctionCall(
                                        new Function() {
                                            @Override
                                            public String getFunctionNameSQL() {
                                                return "RAND";
                                            }

                                        }), VariantManager.getSubsetFraction(totalNumVariants), true));

                        //String qstr = query.toString();
                        //LOG.info(qstr);
                        //Copy the results of the restricted join to the new subset table called 'tableNameSubset'.
                        DBUtils.copyQueryResultToNewTable(sessionID, query, tableNameSubset, this);
                    } finally {
                        //cleanup the temporary table.
                        if (tmpTable != null) {
                            DBUtils.dropTable(sessionID, tmpTable.getTableName());
                        }
                    }

                    registerTable(sessionID, projectID, referenceID, updateID, currentTableName, tableNameSubset, allAnnotationIDs);
                    VariantManagerUtils.addTagsToUpload(sessionID, updateID, tags);
                    // create patients for all DNA ids in this update
                    createPatientsForUpdate(sessionID, currentTableName, projectID, updateID);
                } finally {
                    if (VariantManager.REMOVE_WORKING_DIR) {
                        if (workingDirectory != null && workingDirectory.exists()) {
                            MiscUtils.deleteDirectory(workingDirectory);
                        }

                        //This code is temporary and will be removed.  Annotated TSV files should
                        //be retained to avoid table dumps and reloads.  When this is implemented, 
                        //stale TSVs should also be cleaned by modifying ProjectController.cleanStaleGenotypeFiles
                        MiscUtils.deleteDirectory(finalAnnotationDestDir);
                    }
                }

                LOG.info("Finished import");
                return true;
            }
        };

        MedSavantServerEngine.getInstance().runJobInCurrentThread(importJob);

        return updateID;
    }*/

    //Returns a list of all file ids, regardless of whether or not they've been published.
    private static int[] getFileIds(String sessionId, int projectID, int referenceID, int updateID) throws SQLException, SessionExpiredException {
        TableSchema table = MedSavantDatabase.VariantFileTableSchema;
        SelectQuery sq = new SelectQuery();
        sq.addFromTable(table.getTable());
        sq.addColumns(table.getDBColumn(VariantFileTableSchema.COLUMNNAME_OF_FILE_ID));
        sq.addCondition(BinaryCondition.equalTo(table.getDBColumn(VariantFileTableSchema.COLUMNNAME_OF_REFERENCE_ID), referenceID));
        sq.addCondition(BinaryCondition.equalTo(table.getDBColumn(VariantFileTableSchema.COLUMNNAME_OF_PROJECT_ID), projectID));
        sq.addCondition(BinaryCondition.equalTo(table.getDBColumn(VariantFileTableSchema.COLUMNNAME_OF_UPLOAD_ID), updateID));

        ResultSet rs = ConnectionController.executeQuery(sessionId, sq.toString());
        List<Integer> fileIds = new ArrayList<Integer>();
        while (rs.next()) {
            fileIds.add(rs.getInt(VariantFileTableSchema.COLUMNNAME_OF_FILE_ID));
        }

        return ArrayUtils.toPrimitive(fileIds.toArray(new Integer[fileIds.size()]));
    }

    /**
     * UPDATE AN EXISTING TABLE -- needs updating!
     */
    @Deprecated
    public static int doUpdate(final String sessionID, final int projectID, final int referenceID, final int[] annotationIDs, final CustomField[] customFields, final boolean publishUponCompletion) throws Exception {
        throw new UnsupportedOperationException("doUpdate not done yet");
       //return 0;
       /*
        String userId = SessionManager.getInstance().getUserForSession(sessionID);
        DateFormat dateFormat = new SimpleDateFormat("MMM dd - HH:mm:ss");
        final String existingVariantTableName = ProjectManager.getInstance().getNameOfVariantTable(sessionID, projectID, referenceID, true, false);
        final String existingViewName = DBSettings.getVariantViewName(projectID, referenceID);
        final int updateID = AnnotationLogManager.getInstance().addAnnotationLogEntry(sessionID, projectID, referenceID, AnnotationLog.Action.UPDATE_TABLE);
        final String database = SessionManager.getInstance().getDatabaseForSession(sessionID);
        //Create a dummy job to contain all the sub jobs (threads).
        MedSavantServerJob updateJob = new MedSavantServerJob(userId, database + ": VCF Update - " + dateFormat.format(new Date()), null) {
            @Override
            public boolean run() throws Exception {
                File workingDirectory = DirectorySettings.generateDateStampDirectory(DirectorySettings.getTmpDirectory());

                try {
                    ProjectManager.getInstance().restorePublishedFileTable(sessionID);

                    getJobProgressMonitor().setMessage("Writing existing variants to file");
                    TSVFile existingViewAsTSV = doDumpTableAsTSV(sessionID, existingViewName, createSubdir(workingDirectory, "dump"));
                    getJobProgressMonitor().setMessage("Annotating...");

                    String tableName = ProjectManager.getInstance().addVariantTableToDatabase(
                            sessionID,
                            projectID,
                            referenceID,
                            updateID,
                            annotationIDs,
                            customFields,
                            false);

                    String tableNameSubset = ProjectManager.getInstance().addVariantTableToDatabase(
                            sessionID,
                            projectID,
                            referenceID,
                            updateID,
                            annotationIDs,
                            customFields,
                            true);

                    File workingDir = createSubdir(workingDirectory, "annotate_upload");
                    ProjectManager.getInstance().setCustomVariantFields(sessionID, projectID, referenceID, updateID, customFields);

                    //TSVFile[] annotatedFiles
//                            = annotateTSVFiles(sessionID, updateID, projectID, referenceID, annotationIDs, customFields, new TSVFile[]{existingViewAsTSV}, workingDir, this);

                    TSVFile[] annotatedFiles = null;
   //                 appendTSVFilesToVariantTable(sessionID, projectID, referenceID, updateID, annotatedFiles, tableName);

                    //recreate subset table                                        
                    SelectQuery sq = new SelectQuery();
                    TableSchema table = CustomTables.getInstance().getCustomTableSchema(sessionID, tableName);
                    sq.addFromTable(table.getTable());
                    sq.addAllColumns();
                    sq.addCondition(VariantManager.getSubsetRestrictionCondition(existingViewAsTSV.getNumLines()));
                    DBUtils.copyQueryResultToNewTable(sessionID, sq, tableNameSubset);

                    registerTable(sessionID, projectID, referenceID, updateID, tableName, tableNameSubset, annotationIDs);

                    if (publishUponCompletion) {
                        publishLatestUpdate(sessionID, projectID);
                    }
                } finally {
                    if (VariantManager.REMOVE_WORKING_DIR) {
                        MiscUtils.deleteDirectory(workingDirectory);
                    }
                }
                return true;
            }
        };
        MedSavantServerEngine.getInstance().runJobInCurrentThread(updateJob);
        return updateID;
        */
    }

    /**
     * PUBLICATION AND STATUS
     */
    private static void publishLatestUpdate(String sessionID, int projectID) throws RemoteException, Exception {
        VariantManager.getInstance().publishVariants(sessionID, projectID);
    }

    private static void setAnnotationStatus(String sessionID, int updateID, AnnotationLog.Status status) throws SQLException, SessionExpiredException {
        AnnotationLogManager.getInstance().setAnnotationLogStatus(sessionID, updateID, status);
    }

    /**
     * TABLE MANAGEMENT
     */
    private static void registerTable(String sessionID, int projectID, int referenceID, int updateID, String tableName, String tableNameSub, int[] annotationIDs) throws RemoteException, SQLException, SessionExpiredException {
        //add entries to tablemap
        ProjectManager.getInstance().addTableToMap(sessionID, projectID, referenceID, updateID, false, tableName, annotationIDs, tableNameSub);

    }
   

 /*   private static TSVFile doDumpTableAsTSV(String sessionID, String tableName, File workingDir) throws SQLException, IOException, InterruptedException, SessionExpiredException {
        LOG.info("Dumping existing table to file, working directory is " + workingDir.getAbsolutePath());
        //note tableName could also be a view name.
        File outfile = new File(workingDir, tableName + ".dump");
        VariantManagerUtils.variantTableToTSVFile(sessionID, tableName, outfile);
        int nv = VariantManager.getInstance().getNumFilteredVariantsHelper(sessionID, tableName, new Condition[][]{{Condition.EMPTY}});
        return new TSVFile(outfile, nv);
    }*/

    private static File createSubdir(File parent, String child) throws IOException, InterruptedException {

        File dir = new File(parent, child);
        dir.mkdirs();

        // TODO: is this necessary?
        Process p = Runtime.getRuntime().exec("chmod -R a+wx " + dir);
        p.waitFor();

        return dir;
    }
   
    private static Annotation[] getAnnotationsFromIDs(int[] annotIDs, String sessID) throws RemoteException, SQLException, SessionExpiredException {
        int numAnnotations = annotIDs.length;
        Annotation[] annotations = new Annotation[numAnnotations];
        for (int i = 0; i < numAnnotations; i++) {
            annotations[i] = AnnotationManager.getInstance().getAnnotation(sessID, annotIDs[i]);
            LOG.info("\t" + (i + 1) + ". " + annotations[i].getProgram() + " " + annotations[i].getReferenceName() + " " + annotations[i].getVersion());
        }
        return annotations;
    }

    private static void createPatientsForUpdate(String sessionID, String tableName, int projectID, int updateID) throws RemoteException, SQLException, SessionExpiredException {

        // get the table schema for the update
        /*
         TableSchema table = CustomTables.getInstance().getCustomTableSchema(sessionID,
         ProjectManager.getInstance().getVariantTableName(sessionID, updateID, projectID, referenceID)
         );
         */
        TableSchema table = CustomTables.getInstance().getCustomTableSchema(sessionID, tableName);
        SelectQuery query = new SelectQuery();
        query.addFromTable(table.getTable());
        query.setIsDistinct(true);
        query.addColumns(table.getDBColumn(BasicVariantColumns.DNA_ID.getColumnName()));
        query.addCondition(BinaryConditionMS.equalTo(table.getDBColumn(BasicVariantColumns.UPLOAD_ID.getColumnName()), updateID));

        List<String> dnaIDs = new ArrayList<String>();

        LOG.info("Creating patient for update " + query.toString());

        ResultSet rs = ConnectionController.executeQuery(sessionID, query.toString());

        while (rs.next()) {
            String dnaID = rs.getString(1);
            dnaIDs.add(dnaID);
        }

        rs.close();

        // get a map of DNA ids to Hospital IDs
        Map<String, String> dnaIDToHospitalIDMap = PatientManager.getInstance().getValuesFromDNAIDs(sessionID, projectID, BasicPatientColumns.HOSPITAL_ID.getColumnName(), dnaIDs);

        LOG.info("Getting orphaned DNA IDs");
        for (String dnaID : dnaIDs) {
            if (dnaIDToHospitalIDMap.containsKey(dnaID)) {
                LOG.info("Already a patient with DNA ID: " + dnaID);
            } else {
                LOG.info("No patient with DNA ID " + dnaID + ", creating one");

                // create a patient with Hospital ID equal to the DNA ID
                List<CustomField> patientFields = new ArrayList<CustomField>();
                List<String> fieldValues = new ArrayList<String>();
                patientFields.add(PatientManager.HOSPITAL_ID);
                fieldValues.add(dnaID);
                patientFields.add(PatientManager.DNA_IDS);
                fieldValues.add(dnaID);
                PatientManager.getInstance().addPatient(sessionID, projectID, patientFields, fieldValues);
            }
        }
    }   

    /*
    private static void appendTSVFilesToVariantTable(String sessionID, int projectID, int referenceID, int updateID,
            TSVFile[] annotatedTSVFiles, String tableName)
            throws RemoteException, SessionExpiredException, SQLException, IOException, InterruptedException {

//        int variantTableID = ProjectManager.getInstance().getNewestUpdateID(sessionID, projectID, referenceID, true);
        //Note, table is NOT locked.  Publishing involves inserting the updateID in the view table.
        //appendTSVFilesToTable(sessionID, projectID, referenceID, updateID, variantTableID, annotationIDs, annotatedTSVFiles, tableName, tableNameSubset, createSubdir(workingDir, "subset"));
        try {
            org.ut.biolab.medsavant.server.serverapi.LogManager.getInstance().addServerLog(
                    sessionID,
                    LogManagerAdapter.LogType.INFO,
                    "Uploading " + annotatedTSVFiles.length + " TSV files");
        } catch (RemoteException ex) {
        } catch (SessionExpiredException ex) {
        }
        for (TSVFile af : annotatedTSVFiles) {
            LOG.info("Uploading " + af.getFile().getAbsolutePath() + "...");
            VariantManagerUtils.uploadTSVFileToVariantTable(sessionID, af.getFile(), tableName);

        }

        //No new tables should have been created, so it's not necessary to drop any existing tables.
        //dropTablesPriorToUpdateID(sessionID, projectID, referenceID, updateID);
        setAnnotationStatus(sessionID, updateID, AnnotationLog.Status.PENDING);
    }*/
}
