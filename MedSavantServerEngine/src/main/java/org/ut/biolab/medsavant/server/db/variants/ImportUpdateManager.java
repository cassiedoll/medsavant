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
import com.healthmarketscience.sqlbuilder.ComboCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import org.ut.biolab.medsavant.server.serverapi.VariantManager;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SelectQuery.JoinType;
import com.healthmarketscience.sqlbuilder.SetOperationQuery;
import com.healthmarketscience.sqlbuilder.UnaryCondition;
import com.healthmarketscience.sqlbuilder.UnionQuery;
import java.io.File;
import java.io.FileNotFoundException;
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
import org.ut.biolab.medsavant.server.MedSavantServerJob;
import org.ut.biolab.medsavant.server.MedSavantServerEngine;
import org.ut.biolab.medsavant.server.db.ConnectionController;
import org.ut.biolab.medsavant.server.db.MedSavantDatabase;
import static org.ut.biolab.medsavant.server.db.MedSavantDatabase.VariantFileIBTableSchema;
import static org.ut.biolab.medsavant.server.db.MedSavantDatabase.VariantFileTableSchema;
import org.ut.biolab.medsavant.server.db.util.CustomTables;
import org.ut.biolab.medsavant.server.db.util.DBSettings;
import org.ut.biolab.medsavant.server.db.util.DBUtils;
import org.ut.biolab.medsavant.server.serverapi.AnnotationLogManager;
import org.ut.biolab.medsavant.server.serverapi.AnnotationManager;
import org.ut.biolab.medsavant.server.serverapi.PatientManager;
import org.ut.biolab.medsavant.server.serverapi.ProjectManager;
import org.ut.biolab.medsavant.server.serverapi.ReferenceManager;
import org.ut.biolab.medsavant.server.serverapi.SessionManager;
import org.ut.biolab.medsavant.shared.db.TableSchema;
import org.ut.biolab.medsavant.shared.format.BasicPatientColumns;
import org.ut.biolab.medsavant.shared.format.BasicVariantColumns;
import org.ut.biolab.medsavant.shared.format.CustomField;
import org.ut.biolab.medsavant.shared.model.Annotation;
import org.ut.biolab.medsavant.shared.model.AnnotationLog;
import org.ut.biolab.medsavant.shared.model.SessionExpiredException;
import org.ut.biolab.medsavant.shared.serverapi.LogManagerAdapter;
import org.ut.biolab.medsavant.shared.util.BinaryConditionMS;
import org.ut.biolab.medsavant.shared.util.DirectorySettings;
import org.ut.biolab.medsavant.shared.util.MiscUtils;

/**
 *
 * @author mfiume
 */
public class ImportUpdateManager {

    private static final Log LOG = LogFactory.getLog(ImportUpdateManager.class);

    /**
     * IMPORT FILES INTO AN EXISTING TABLE
     */
    public static int doImport(final String sessionID, final int projectID, final int referenceID, final File[] allVCFFiles, final boolean includeHomozygousReferenceCalls, final boolean preAnnotateWithJannovar, final String[][] tags) throws IOException, SQLException, Exception {

        String userId = SessionManager.getInstance().getUserForSession(sessionID);
        final String database = SessionManager.getInstance().getDatabaseForSession(sessionID);
        DateFormat dateFormat = new SimpleDateFormat("MMM dd - HH:mm:ss");
        final int updateID = AnnotationLogManager.getInstance().addAnnotationLogEntry(sessionID, projectID, referenceID, AnnotationLog.Action.ADD_VARIANTS);
        //Create a dummy job to contain all the sub jobs (threads).
        MedSavantServerJob importJob = new MedSavantServerJob(userId, database + ": VCF Import, " + dateFormat.format(new Date()), null) {
            @Override
            public boolean run() throws Exception {
                ProjectManager.getInstance().restorePublishedFileTable(sessionID);
                LOG.info("Starting import");
                File workingDirectory = DirectorySettings.generateDateStampDirectory(DirectorySettings.getTmpDirectory());
                LOG.info("Working directory is " + workingDirectory.getAbsolutePath());
                int startFile = 0;
                int endFile = Math.min(allVCFFiles.length, MedSavantServerEngine.getMaxThreads());

                List<TSVFile> allAnnotatedFiles = new ArrayList<TSVFile>();
                int[] allAnnotationIDs = new int[0];
                //File annotationDir = DirectorySettings.getAnnotatedTSVDirectory(database, projectID);
                File workingDir = null;

                File finalAnnotationDestDir = DirectorySettings.getAnnotatedTSVDirectory(database, projectID, referenceID);
                if (!finalAnnotationDestDir.exists()) {
                    finalAnnotationDestDir.mkdirs();
                }

                //annotationDir.mkdirs();
                try {
                    //Could run this in a different thread, but make sure to wait for that thread at the end!
                    getJobProgress().setMessage("Preparing database for new variants...");
                    int[] annotationIDs = AnnotationManager.getInstance().getAnnotationIDs(sessionID, projectID, referenceID);
                    CustomField[] customFields = ProjectManager.getInstance().getCustomVariantFields(sessionID, projectID, referenceID, ProjectManager.getInstance().getNewestUpdateID(sessionID, projectID, referenceID, false));
                    int numVariantsImported = 0;
                    //Dumping the database should not be necessary if we retain copies of the TSVs from which the 
                    //table was originally built.  This line is temporary and will be removed in the near future.
                    //File existingTableAsTSV = doDumpTableAsTSV(sessionID, existingVariantTableName, createSubdir(workingDirectory, "dump"), true);
                    ProjectManager.getInstance().setCustomVariantFields(sessionID, projectID, referenceID, updateID, customFields);

                    while (startFile < endFile) {
                        workingDir = createSubdir(workingDirectory, "annotate_upload");
                        getJobProgress().setMessage("Performing functional annotations for VCFs " + startFile + " - " + endFile + " of " + allVCFFiles.length);

                        File[] vcfFiles = ArrayUtils.subarray(allVCFFiles, startFile, endFile);

                        /*if (preAnnotateWithJannovar) {
                         org.ut.biolab.medsavant.server.serverapi.LogManager.getInstance().addServerLog(sessionID, LogManagerAdapter.LogType.INFO, "Annotating VCF files with Jannovar");
                         vcfFiles = new Jannovar(ReferenceManager.getInstance().getReferenceName(sessionID, referenceID)).annotateVCFFiles(vcfFiles, database, projectID, workingDir);
                         }*/
                        getJobProgress().setMessage("Preparing VCFs " + startFile + " - " + endFile + " of " + allVCFFiles.length + " for further annotations");
                        // prepare for annotation
                        TSVFile[] importedTSVFiles
                                = doConvertVCFToTSV(sessionID, vcfFiles, preAnnotateWithJannovar, updateID, projectID, referenceID,
                                        includeHomozygousReferenceCalls, workingDirectory, this);

                        getJobProgress().setMessage("Annotating VCFs " + startFile + " - " + endFile + " of " + allVCFFiles.length);

                        for (TSVFile file : importedTSVFiles) {
                            numVariantsImported += file.getNumLines();
                        }
                        //Instead of saving the split annotatedFiles, just save the importedTSVFiles??  Then use named pipes to split and 
                        //load into infobright on the fly?
                        TSVFile[] annotatedFiles = annotateTSVFiles(sessionID, updateID, projectID, referenceID, annotationIDs, customFields, importedTSVFiles, workingDir, this);

                        //Save the annotated Files, delete everything else.  These can be reused if annotations are later changed.
                        for (TSVFile annotatedFile : annotatedFiles) {
                            /*
                             File f = annotatedFile.getFile();
                             File dst = new File(finalAnnotationDestDir, f.getName());
                             LOG.info("Renaming " + annotatedFile.getFile().getAbsolutePath() + " to " + dst.getAbsolutePath());
                             if (!annotatedFile.getFile().renameTo(dst)) {
                             LOG.error("Couldn't rename " + annotatedFile.getFile().getAbsolutePath() + " to " + dst.getAbsolutePath());
                             }
                             */
                            File dst = new File(finalAnnotationDestDir, annotatedFile.getFile().getName());
                            annotatedFile.moveTo(dst);
                            allAnnotatedFiles.add(annotatedFile);
                        }

                        allAnnotationIDs = ArrayUtils.addAll(allAnnotationIDs, annotationIDs);
                        if (VariantManager.REMOVE_WORKING_DIR) {
                            MiscUtils.deleteDirectory(workingDir);
                        }

                        startFile += MedSavantServerEngine.getMaxThreads();
                        endFile = Math.min(allVCFFiles.length, endFile + MedSavantServerEngine.getMaxThreads());
                    }//end while

                    getJobProgress().setMessage("Done annotating, loading all variants into database.");
                    workingDir = createSubdir(workingDirectory, "annotate_upload");
                    String currentTableName
                            = ProjectManager.getInstance().getNameOfVariantTable(sessionID, projectID, referenceID, true, false);
                    /* String currentTableNameSubset
                     = ProjectManager.getInstance().getNameOfVariantTable(sessionID, projectID, referenceID, true, true);*/

                    String tableNameSubset = ProjectManager.getInstance().addVariantTableToDatabase(
                            sessionID,
                            projectID,
                            referenceID,
                            updateID,
                            annotationIDs,
                            customFields,
                            true);

                    appendTSVFilesToVariantTable(sessionID, projectID, referenceID, updateID, allAnnotatedFiles.toArray(new TSVFile[allAnnotatedFiles.size()]), currentTableName);
                    //registerTable(sessionID, projectID, referenceID, updateID, currentTableName, currentTableNameSubset, allAnnotationIDs);

                    int[] fileIds = getFileIds(sessionID, projectID, referenceID, updateID);
                    String viewName = DBSettings.getVariantViewName(projectID, referenceID);
                    int totalNumVariants = numVariantsImported + VariantManager.getInstance().getNumFilteredVariantsHelper(sessionID, viewName, new Condition[0][]);
                    TableSchema publishedVariantView = CustomTables.getInstance().getCustomTableSchema(sessionID, viewName);
                    TableSchema unpublishedVariantTable = CustomTables.getInstance().getCustomTableSchema(sessionID, currentTableName);
                    TableSchema publishedFileTable = MedSavantDatabase.VariantFileIBTableSchema;
                    /*
                     UnionQuery uq = new UnionQuery(SetOperationQuery.Type.UNION);
                     SelectQuery[] sq = new SelectQuery[2];
                     sq[0] = new SelectQuery();
                     sq[0].addFromTable(publishedVariantView.getTable());
                     sq[0].addAllTableColumns(publishedVariantView.getTable());

                     sq[1] = new SelectQuery();
                     sq[1].addFromTable(unpublishedVariantTable.getTable());
                     sq[1].addAllTableColumns(unpublishedVariantTable.getTable());
                     */
                    Condition[] fileRestrictionCondition = new Condition[fileIds.length];
                    int i = 0;
                    for (int fileId : fileIds) {
                        fileRestrictionCondition[i++] = BinaryCondition.equalTo(unpublishedVariantTable.getDBColumn(BasicVariantColumns.FILE_ID), fileId);
                    }

                    SelectQuery query = new SelectQuery();
                    query.addAllTableColumns(unpublishedVariantTable.getTable());
                    query.addJoin(JoinType.LEFT_OUTER,
                            unpublishedVariantTable.getTable(),
                            publishedFileTable.getTable(),
                            BinaryCondition.equalTo(unpublishedVariantTable.getDBColumn(BasicVariantColumns.FILE_ID), publishedFileTable.getDBColumn(VariantFileTableSchema.COLUMNNAME_OF_FILE_ID)));

                    query.addCondition(
                            ComboCondition.or(
                                    UnaryCondition.isNotNull(publishedFileTable.getDBColumn(VariantFileTableSchema.COLUMNNAME_OF_FILE_ID)),
                                    ComboCondition.or(fileRestrictionCondition)
                            )
                    );

                    /*
                    
                    
                     Condition[] cc
                     = {
                     VariantManager.getSubsetRestrictionCondition(totalNumVariants),};
                     */
                    /* sq[1].addCondition(
                     ComboCondition.and(new Condition[]{ComboCondition.and(cc), ComboCondition.or(fileRestrictionCondition)})
                     );*/
                    /*                  sq[1].addCondition(ComboCondition.or(fileRestrictionCondition));
                     uq.addQueries(sq);
                     "("+uq.toString()+") as t1 WHERE RAND() < "*/
                    /*                    
                     sq.addJoin(
                     SelectQuery.JoinType.INNER, 
                     unpublishedVariantTable.getTable(), 
                     publishedFileTable.getTable(), 
                     BinaryCondition.equalTo(unpublishedVariantTable.getDBColumn(BasicVariantColumns.FILE_ID), publishedFileTable.getDBColumn(VariantFileTableSchema.COLUMNNAME_OF_FILE_ID))
                     );                    
                     sq.addCondition(ComboCondition.and(cc));
                     sq.addAllTableColumns(unpublishedVariantTable.getTable());*/
                    String qs = "SELECT ot.* FROM (" + query.toString() + ") as ot WHERE RAND() <= " + VariantManager.getSubsetFraction(totalNumVariants);
                    VariantManager.getSubsetRestrictionCondition(totalNumVariants).toString();
                    DBUtils.copyQueryResultToNewTable(sessionID, qs, tableNameSubset, this);

                    registerTable(sessionID, projectID, referenceID, updateID, currentTableName, tableNameSubset, allAnnotationIDs);

//uploadTSVFiles(sessionID, updateID, projectID, referenceID, allAnnotationIDs, ArrayUtils.addAll(allAnnotatedFiles, existingTableAsTSV), workingDir);                                       
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
        //Although importing takes a long time, the outer 'import' job doesn't that much work,
        //and mostly waits on other threads.
        //MedSavantServerEngine.submitShortJob(importJob).get();
        MedSavantServerEngine.runJobInCurrentThread(importJob);

        return updateID;
    }

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
     * UPDATE AN EXISTING TABLE
     */
    public static int doUpdate(final String sessionID, final int projectID, final int referenceID, final int[] annotationIDs, final CustomField[] customFields, final boolean publishUponCompletion) throws Exception {
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

                    getJobProgress().setMessage("Writing existing variants to file");
                    TSVFile existingViewAsTSV = doDumpTableAsTSV(sessionID, existingViewName, createSubdir(workingDirectory, "dump"));
                    getJobProgress().setMessage("Annotating...");
                    //TODO: shouldn't these (tablename and tablenamesub) functions consider the customfields?
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

                    TSVFile[] annotatedFiles
                            = annotateTSVFiles(sessionID, updateID, projectID, referenceID, annotationIDs, customFields, new TSVFile[]{existingViewAsTSV}, workingDir, this);

                    appendTSVFilesToVariantTable(sessionID, projectID, referenceID, updateID, annotatedFiles, tableName);

                    //recreate subset table                                        
                    SelectQuery sq = new SelectQuery();
                    TableSchema table = CustomTables.getInstance().getCustomTableSchema(sessionID, tableName);
                    sq.addFromTable(table.getTable());                    
                    sq.addAllColumns();
                    sq.addCondition(VariantManager.getSubsetRestrictionCondition(existingViewAsTSV.getNumLines()));
                    DBUtils.copyQueryResultToNewTable(sessionID, sq, tableNameSubset);
                    
                    registerTable(sessionID, projectID, referenceID, updateID, tableName, tableNameSubset, annotationIDs);

                    //annotateAndUploadTSVFiles(sessionID, updateID, projectID, referenceID, annotationIDs, customFields, new File[]{existingTableAsTSV}, createSubdir(workingDirectory, "annotate_upload"), this);
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
        //MedSavantServerEngine.submitShortJob(updateJob).get();
        MedSavantServerEngine.runJobInCurrentThread(updateJob);
        return updateID;
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

    @Deprecated
    private static void dropTablesPriorToUpdateID(String sessionID, int projectID, int referenceID, int updateID) throws RemoteException, SQLException, SessionExpiredException {
        int minId = -1;
        int maxId = updateID - 1;
        ProjectManager.getInstance().removeTables(sessionID, projectID, referenceID, minId, maxId);

    }

    /**
     * PARSING
     */
    public static TSVFile[] doConvertVCFToTSV(String sessID, File[] vcfFiles, boolean preAnnotateWithJannovar, int updateID, int projectID, int referenceID, boolean includeHomozygousReferenceCalls, File workingDirectory, MedSavantServerJob parentJob) throws Exception {
        final String database = SessionManager.getInstance().getDatabaseForSession(sessID);
        File outDir = createSubdir(workingDirectory, "converted");
        LOG.info("Converting VCF files to TSV, working directory is " + outDir.getAbsolutePath());

        File[] processedVCFs = vcfFiles;
        if (preAnnotateWithJannovar) {
            org.ut.biolab.medsavant.server.serverapi.LogManager.getInstance().addServerLog(sessID, LogManagerAdapter.LogType.INFO, "Annotating VCF files with Jannovar");
            processedVCFs = new Jannovar(ReferenceManager.getInstance().getReferenceName(sessID, referenceID)).annotateVCFFiles(vcfFiles, database, projectID, workingDirectory);
        }

        // parse each vcf file in a separate thread with a separate file ID
        List<MedSavantServerJob> threads = new ArrayList<MedSavantServerJob>(vcfFiles.length);
        String stamp = System.nanoTime() + "";
        //int fileID = 0;
        //for (File vcfFile : vcfFiles) {
        for (int i = 0; i < processedVCFs.length; ++i) {
            File vcfFile = processedVCFs[i];
            File originalVCF = vcfFiles[i];
            int fileID = VariantManager.addEntryToFileTable(sessID, updateID, projectID, referenceID, originalVCF);
            File outFile = new File(outDir, "tmp_" + stamp + "_" + fileID + ".tdf");
            threads.add(new VariantParser(sessID, parentJob, vcfFile, outFile, updateID, fileID, includeHomozygousReferenceCalls));

            //threads[fileID] = t;
            fileID++;
            LOG.info("Queueing thread to parse " + vcfFile.getAbsolutePath());
        }
        //MedSavantServerEngine.getLongExecutorService().invokeAll(threads);
        MedSavantServerEngine.submitLongJobs(threads);

        //VariantManagerUtils.processThreadsWithLimit(threads);
        // tab separated files
        TSVFile[] tsvFiles = new TSVFile[threads.size()];

        LOG.info("All parsing annotation threads done");

        int i = 0;
        for (MedSavantServerJob msg : threads) {
            VariantParser t = (VariantParser) msg;
            tsvFiles[i++] = new TSVFile(new File(t.getOutputFilePath()), t.getNumVariants());
            if (!t.didSucceed()) {
                LOG.info("At least one parser thread errored out");
                throw t.getException();
            }
        }

        return tsvFiles;
    }

    private static TSVFile doDumpTableAsTSV(String sessionID, String tableName, File workingDir) throws SQLException, IOException, InterruptedException, SessionExpiredException {
        LOG.info("Dumping existing table to file, working directory is " + workingDir.getAbsolutePath());
        //note tableName could also be a view name.
        File outfile = new File(workingDir, tableName + ".dump");
        VariantManagerUtils.variantTableToTSVFile(sessionID, tableName, outfile);
        int nv = VariantManager.getInstance().getNumFilteredVariantsHelper(sessionID, tableName, new Condition[][]{{Condition.EMPTY}});
        return new TSVFile(outfile, nv);
    }

    private static File createSubdir(File parent, String child) throws IOException, InterruptedException {

        File dir = new File(parent, child);
        dir.mkdirs();

        // TODO: is this necessary?
        Process p = Runtime.getRuntime().exec("chmod -R a+wx " + dir);
        p.waitFor();

        return dir;
    }

    private static File[] splitFilesByDNAAndFileID(TSVFile[] tsvFiles, File workingDir) throws FileNotFoundException, IOException {

        LOG.info("Splitting " + tsvFiles.length + " files by DNA and FileID, working directory is " + workingDir.getAbsolutePath());

        File[] splitTSVFiles = new File[0];
        //TODO: thread each of these
        for (TSVFile file : tsvFiles) {
            File[] someSplitFiles = VariantManagerUtils.splitTSVFileByFileAndDNAID(workingDir, file.getFile());
            splitTSVFiles = ArrayUtils.addAll(splitTSVFiles, someSplitFiles);
        }

        return splitTSVFiles;
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

    private static TSVFile[] annotateTSVFiles(String sessionID, File[] tsvFiles, Annotation[] annotations, CustomField[] customFields, File createSubdir, MedSavantServerJob parentJob) throws Exception {
        return VariantManagerUtils.annotateTSVFiles(sessionID, tsvFiles, annotations, customFields, parentJob);
    }

    /*
     @Deprecated
     private static void annotateAndUploadTSVFilesOld(String sessionID, int updateID, int projectID, int referenceID, int[] annotationIDs, CustomField[] customFields, File[] tsvFiles, File workingDir, MedSavantServerJob parentJob) throws Exception {
     File[] annotatedFiles = annotateTSVFiles(sessionID, updateID, projectID, referenceID, annotationIDs, customFields, tsvFiles, workingDir, parentJob);
     uploadTSVFiles(sessionID, updateID, projectID, referenceID, annotationIDs, annotatedFiles, workingDir);
     }*/
    private static TSVFile[] annotateTSVFiles(String sessionID, int updateID, int projectID, int referenceID, int[] annotationIDs, CustomField[] customFields, TSVFile[] tsvFiles, File workingDir, MedSavantServerJob parentJob) throws Exception {
        try {
            org.ut.biolab.medsavant.server.serverapi.LogManager.getInstance().addServerLog(
                    sessionID,
                    LogManagerAdapter.LogType.INFO,
                    "Annotating TSV files, working directory is " + workingDir.getAbsolutePath());
        } catch (RemoteException ex) {
        } catch (SessionExpiredException ex) {
        }

        LOG.info("Annotating TSV files, working directory is " + workingDir.getAbsolutePath());

        File[] splitTSVFiles = null;
        try {
            //get annotation information
            Annotation[] annotations = getAnnotationsFromIDs(annotationIDs, sessionID);
            parentJob.getJobProgress().setMessage("Preparing variants for annotation");
            splitTSVFiles = splitFilesByDNAAndFileID(tsvFiles, createSubdir(workingDir, "split"));
            TSVFile[] annotatedTSVFiles = annotateTSVFiles(sessionID, splitTSVFiles, annotations, customFields, createSubdir(workingDir, "annotate"), parentJob);
            return annotatedTSVFiles;
        } catch (Exception e) {
            AnnotationLogManager.getInstance().setAnnotationLogStatus(sessionID, updateID, AnnotationLog.Status.ERROR);
            throw e;
        } finally {
            //assert splitTSVFiles are deleted.
            if (splitTSVFiles == null) {
                for (File splitTSVFile : splitTSVFiles) {
                    if (splitTSVFile.exists()) {
                        splitTSVFile.delete();
                    }
                }
            }
        }
    }

    private static void appendTSVFilesToVariantTable(String sessionID, int projectID, int referenceID, int updateID,
            TSVFile[] annotatedTSVFiles, String tableName)
            throws RemoteException, SessionExpiredException, SQLException, IOException, InterruptedException {

        int variantTableID = ProjectManager.getInstance().getNewestUpdateID(sessionID, projectID, referenceID, true);

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
            /* if (initialStepSize == 1 && VariantManagerUtils.determineStepForSubset(numLines) > 1) {
             //abort writing to the subset table.
             initialStepSize = -1;
             }*/
        }

        //No new tables should have been created, so it's not necessary to drop any existing tables.
        //dropTablesPriorToUpdateID(sessionID, projectID, referenceID, updateID);
        setAnnotationStatus(sessionID, updateID, AnnotationLog.Status.PENDING);
    }
    /*
     @Deprecated
     private static void appendTSVFilesToVariantTableOld(String sessionID, int projectID, int referenceID, int updateID,
     int[] annotationIDs, File[] annotatedTSVFiles, File workingDir)
     throws RemoteException, SessionExpiredException, SQLException, IOException, InterruptedException {

     String currentTableName
     = ProjectManager.getInstance().getVariantTableName(sessionID, projectID, referenceID, true, false);
     String currentTableNameSubset
     = ProjectManager.getInstance().getVariantTableName(sessionID, projectID, referenceID, true, true);

     int variantTableID = ProjectManager.getInstance().getNewestUpdateID(sessionID, projectID, referenceID, true);

     //Note, table is NOT locked.  Publishing involves inserting the updateID in the view table.
     appendTSVFilesToTable(sessionID, projectID, referenceID, variantTableID, annotationIDs, annotatedTSVFiles, currentTableName, currentTableNameSubset, createSubdir(workingDir, "subset"));

     registerTable(sessionID, projectID, referenceID, updateID, currentTableName, currentTableNameSubset, annotationIDs);
     //No new tables should have been created, so it's not necessary to drop any existing tables.
     //dropTablesPriorToUpdateID(sessionID, projectID, referenceID, updateID);
     setAnnotationStatus(sessionID, updateID, AnnotationLog.Status.PENDING);
     }

     @Deprecated
     private static void uploadTSVFiles(String sessionID, int updateID, int projectID, int referenceID, int[] annotationIDs, File[] annotatedTSVFiles, File workingDir) throws RemoteException, SessionExpiredException, SQLException, IOException, InterruptedException {

     //TODO: shouldn't these (tablename and tablenamesub) functions consider the customfields?
     String tableName = ProjectManager.getInstance().createVariantTable(
     sessionID,
     projectID,
     referenceID,
     updateID,
     annotationIDs,
     true);

     String tableNameSubset = ProjectManager.getInstance().createVariantTable(
     sessionID,
     projectID,
     referenceID,
     updateID,
     annotationIDs,
     false,
     true);
     uploadTSVFiles(sessionID, projectID, referenceID, updateID, annotationIDs, annotatedTSVFiles, tableName, tableNameSubset, createSubdir(workingDir, "subset"));
     registerTable(sessionID, projectID, referenceID, updateID, tableName, tableNameSubset, annotationIDs);
     dropTablesPriorToUpdateID(sessionID, projectID, referenceID, updateID);
     setAnnotationStatus(sessionID, updateID, AnnotationLog.Status.PENDING);
     }
     */

    @Deprecated
    private static void appendTSVFilesToTableOld(String sessionID, int projectID, int referenceID, int updateID, int variantTableID, int[] annotationIDs, TSVFile[] annotatedTSVFiles, String tableName, String tableNameSub, File workingDir) throws SQLException, IOException, SessionExpiredException {

        try {
            org.ut.biolab.medsavant.server.serverapi.LogManager.getInstance().addServerLog(
                    sessionID,
                    LogManagerAdapter.LogType.INFO,
                    "Uploading " + annotatedTSVFiles.length + " TSV files");
        } catch (RemoteException ex) {
        } catch (SessionExpiredException ex) {
        }

        //existing variants without the about-to-be-included files.
        String viewName = ProjectManager.getInstance().getNameOfVariantTable(sessionID, projectID, referenceID, true, false);

        //number of lines currently in the table.
        // int numLines = VariantManager.getInstance().getNumFilteredVariantsHelper(sessionID, viewName, new Condition[][]{});
        //number of lines in incoming TSV files
        //for (AnnotatedTSVFile af : annotatedTSVFiles) {
        //  numLines += af.getNumLines();
        //}
        //int initialStepSize = VariantManagerUtils.determineStepForSubset(numLines);
        // upload all the TSV files to the table
        for (TSVFile af : annotatedTSVFiles) {
            LOG.info("Uploading " + af.getFile().getAbsolutePath() + "...");
            VariantManagerUtils.uploadTSVFileToVariantTable(sessionID, af.getFile(), tableName);
            /* if (initialStepSize == 1 && VariantManagerUtils.determineStepForSubset(numLines) > 1) {
             //abort writing to the subset table.
             initialStepSize = -1;
             }*/
        }

        /*
         if (initialStepSize == -1) {
         //Need to recreate sub table from scratch.  
         LOG.info("Dumping variants to file for sub table");
            
         int currentStep = VariantManagerUtils.determineStepForSubset(numLines);
                        
         //Dump variants in existing sub table view to a file.  Note this does not include the 
         //as yet unpublished variants (i.e. those in annotatedTSVFiles).
         File subDump = new File(workingDir, viewName + "sub.tsv");           
         VariantManagerUtils.variantTableToTSVFile(sessionID, viewName, subDump, null, true, currentStep);
         LOG.info("Loading into subset table: " + tableNameSub);
         VariantManagerUtils.dropTableIfExists(sessionID, tableNameSub);
         CustomField[] customFields = 
         ProjectManager.getInstance().getCustomVariantFields(sessionID, projectID, referenceID, variantTableID);
               
               
         tableNameSub = ProjectManager.getInstance().addVariantTableToDatabase(
         sessionID,
         projectID,
         referenceID,
         variantTableID,
         annotationIDs,
         customFields,
         true);
                        
         VariantManagerUtils.uploadTSVFileToVariantTable(sessionID, subDump, tableNameSub, null, -1);
         }
         */
    }
}
