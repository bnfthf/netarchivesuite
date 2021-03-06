/*
 * #%L
 * Netarchivesuite - harvester
 * %%
 * Copyright (C) 2005 - 2014 The Royal Danish Library, the Danish State and University Library,
 *             the National Library of France and the Austrian National Library.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

package dk.netarkivet.harvester.harvesting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.netarkivet.common.CommonSettings;
import dk.netarkivet.common.distribute.arcrepository.ArcRepositoryClientFactory;
import dk.netarkivet.common.distribute.arcrepository.BatchStatus;
import dk.netarkivet.common.distribute.arcrepository.BitarchiveRecord;
import dk.netarkivet.common.distribute.arcrepository.HarvesterArcRepositoryClient;
import dk.netarkivet.common.distribute.indexserver.Index;
import dk.netarkivet.common.distribute.indexserver.IndexClientFactory;
import dk.netarkivet.common.distribute.indexserver.JobIndexCache;
import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.NotificationType;
import dk.netarkivet.common.utils.NotificationsFactory;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.common.utils.StringUtils;
import dk.netarkivet.common.utils.batch.FileBatchJob;
import dk.netarkivet.common.utils.cdx.ArchiveExtractCDXJob;
import dk.netarkivet.common.utils.cdx.CDXRecord;
import dk.netarkivet.harvester.HarvesterSettings;
import dk.netarkivet.harvester.datamodel.HarvestDefinitionInfo;
import dk.netarkivet.harvester.datamodel.HeritrixTemplate;
import dk.netarkivet.harvester.datamodel.Job;
import dk.netarkivet.harvester.harvesting.metadata.MetadataEntry;
import dk.netarkivet.harvester.harvesting.metadata.MetadataFile;
import dk.netarkivet.harvester.harvesting.report.DomainStatsReport;
import dk.netarkivet.harvester.harvesting.report.HarvestReport;
import dk.netarkivet.harvester.harvesting.report.HarvestReportFactory;
import dk.netarkivet.harvester.harvesting.report.HarvestReportGenerator;

/**
 * This class handles all the things in a single harvest that are not related directly related either to launching
 * Heritrix or to handling JMS messages.
 */
public class HarvestController {

    /** The instance logger. */
    private static final Logger log = LoggerFactory.getLogger(HarvestController.class);

    /** The singleton instance of this class. Calling cleanup() on the instance will null this field. */
    private static HarvestController instance;

    /** The max time to wait for heritrix to close last ARC or WARC files (in secs). */
    private static final int WAIT_FOR_HERITRIX_TIMEOUT_SECS = 5;

    /** The ArcRepositoryClient used to communicate with the ArcRepository to store the generated arc-files. */
    private HarvesterArcRepositoryClient arcRepController;

    /**
     * Private constructor controlled by getInstance().
     */
    private HarvestController() {
        arcRepController = ArcRepositoryClientFactory.getHarvesterInstance();
    }

    /**
     * Get the instance of the singleton HarvestController.
     *
     * @return The singleton instance.
     */
    public static synchronized HarvestController getInstance() {
        if (instance == null) {
            instance = new HarvestController();
        }
        return instance;
    }

    /**
     * Clean up this singleton, releasing the ArcRepositoryClient and removing the instance. This instance should not be
     * used after this method has been called. After this has been called, new calls to getInstance will return a new
     * instance.
     */
    public void cleanup() {
        if (arcRepController != null) {
            arcRepController.close();
        }
        resetInstance();
    }

    /**
     * Reset the singleton instance.
     */
    private static void resetInstance() {
        instance = null;
    }

    /**
     * Writes the files involved with a harvests. Creates the Heritrix arcs directory to ensure that this directory
     * exists in advance.
     *
     * @param crawldir The directory that the crawl should take place in.
     * @param job The Job object containing various harvest setup data.
     * @param hdi The object encapsulating documentary information about the harvest.
     * @param metadataEntries Any metadata entries sent along with the job that should be stored for later use.
     * @return An object encapsulating where these files have been written.
     */
    public HeritrixFiles writeHarvestFiles(File crawldir, Job job, HarvestDefinitionInfo hdi,
            List<MetadataEntry> metadataEntries) {
    	// FIXME this hardwires the HeritrixFiles to H1
        final HeritrixFiles files = HeritrixFiles.getH1HeritrixFilesWithDefaultJmxFiles(crawldir, job);

        // If this job is a job that tries to continue a previous job
        // using the Heritrix recover.gz log, and this feature is enabled,
        // then try to fetch the recover.log from the metadata-arc-file.
        if (job.getContinuationOf() != null && Settings.getBoolean(HarvesterSettings.RECOVERlOG_CONTINUATION_ENABLED)) {
            tryToRetrieveRecoverLog(job, files);
        }

        // Create harvestInfo file in crawldir
        // & create preharvest-metadata-1.arc
        log.debug("Writing persistent job data for job {}", job.getJobID());
        // Check that harvestInfo does not yet exist

        // Write job data to persistent storage (harvestinfo file)
        new PersistentJobData(files.getCrawlDir()).write(job, hdi);
        // Create jobId-preharvest-metadata-1.arc for this job
        writePreharvestMetadata(job, metadataEntries, crawldir);

        files.writeSeedsTxt(job.getSeedListAsString());

        files.writeOrderXml(job.getOrderXMLdoc());
        // Only retrieve index if deduplication is not disabled in the template.
        if (job.getOrderXMLdoc().IsDeduplicationEnabled()) {
            log.debug("Deduplication enabled. Fetching deduplication index..");
            files.setIndexDir(fetchDeduplicateIndex(metadataEntries));
        } else {
            log.debug("Deduplication disabled.");
        }

        // Create Heritrix arcs directory before starting Heritrix to ensure
        // the arcs directory exists in advance.
        boolean created = files.getArcsDir().mkdir();
        if (!created) {
            log.warn("Unable to create arcsdir: {}", files.getArcsDir());
        }
        // Create Heritrix warcs directory before starting Heritrix to ensure
        // the warcs directory exists in advance.
        created = files.getWarcsDir().mkdir();
        if (!created) {
            log.warn("Unable to create warcsdir: {}", files.getWarcsDir());
        }

        return files;
    }

    /**
     * This method attempts to retrieve the Heritrix recover log from the job which this job tries to continue. If
     * successful, the Heritrix template is updated accordingly.
     *
     * @param job The harvest Job object containing various harvest setup data.
     * @param files Heritrix files related to this harvestjob.
     */
    private void tryToRetrieveRecoverLog(Job job, HeritrixFiles files) {
        Long previousJob = job.getContinuationOf();
        List<CDXRecord> metaCDXes = null;
        try {
            metaCDXes = getMetadataCDXRecordsForJob(previousJob);
        } catch (IOFailure e) {
            log.debug("Failed to retrive CDX of metatadata records. "
                    + "Maybe the metadata arcfile for job {} does not exist in repository", previousJob, e);
        }

        CDXRecord recoverlogCDX = null;
        if (metaCDXes != null) {
            for (CDXRecord cdx : metaCDXes) {
                if (cdx.getURL().matches(MetadataFile.RECOVER_LOG_PATTERN)) {
                    recoverlogCDX = cdx;
                }
            }
            if (recoverlogCDX == null) {
                log.debug("A recover.gz log file was not found in metadata-arcfile");
            } else {
                log.debug("recover.gz log found in metadata-arcfile");
            }
        }

        BitarchiveRecord br = null;
        if (recoverlogCDX != null) { // Retrieve recover.gz from metadata.arc file
            br = ArcRepositoryClientFactory.getViewerInstance().get(recoverlogCDX.getArcfile(),
                    recoverlogCDX.getOffset());
            if (br != null) {
                log.debug("recover.gz log retrieved from metadata-arcfile");
                if (files.writeRecoverBackupfile(br.getData())) {
                    // modify order.xml, so Heritrix recover-path points
                    // to the retrieved recoverlog
                    insertHeritrixRecoverPathInOrderXML(job, files);
                } else {
                    log.warn("Failed to retrieve and write recoverlog to disk.");
                }
            } else {
                log.debug("recover.gz log not retrieved from metadata-arcfile");
            }
        }
    }

    /**
     * Insert the correct recoverpath in the order.xml for the given harvestjob.
     *
     * @param job A harvestjob
     * @param files Heritrix files related to this harvestjob.
     */
    private void insertHeritrixRecoverPathInOrderXML(Job job, HeritrixFiles files) {
    	
        HeritrixTemplate temp = job.getOrderXMLdoc(); 
    	temp.setRecoverlogNode(files.getRecoverBackupGzFile());
    	job.setOrderXMLDoc(temp); // Update template associated with job
    }

    /**
     * Writes pre-harvest metadata to the "metadata" directory.
     *
     * @param harvestJob a given Job.
     * @param metadata the list of metadata entries to write to metadata file.
     * @param crawlDir the directory, where the metadata will be written.
     * @throws IOFailure If there are errors in writing the metadata.
     */
    private void writePreharvestMetadata(Job harvestJob, List<MetadataEntry> metadata, File crawlDir) throws IOFailure {
        if (metadata.size() == 0) {
            // Do not generate preharvest metadata file for empty list
            return;
        }

        // make sure that metadata directory exists
        File metadataDir = new File(crawlDir, IngestableFiles.METADATA_SUB_DIR);
        metadataDir.mkdir();
        if (!(metadataDir.exists() && metadataDir.isDirectory())) {
            throw new IOFailure("Unable to write preharvest metadata for job '" + harvestJob.getJobID()
                    + "' to directory '" + metadataDir.getAbsolutePath() + "', as directory does not exist.");
        }

        // Serializing the MetadataEntry objects to the metadataDir
        MetadataEntry.storeMetadataToDisk(metadata, metadataDir);
    }

    /**
     * Creates the actual HeritrixLauncher instance and runs it, after the various setup files have been written.
     *
     * @param files Description of files involved in running Heritrix. Not Null.
     * @throws ArgumentNotValid if an argument isn't valid.
     */
    public void runHarvest(HeritrixFiles files) throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(files, "HeritrixFiles files");
        HeritrixLauncher hl = HeritrixLauncherFactory.getInstance(files);
        hl.doCrawl();
    }

    /**
     * Controls storing all files involved in a job. The files are 1) The actual ARC/WARC files, 2) The metadata files
     * The crawl.log is parsed and information for each domain is generated and stored in a AbstractHarvestReport object
     * which is sent along in the crawlstatusmessage.
     * <p>
     * Additionally, any leftover open ARC files are closed and harvest documentation is extracted before upload starts.
     *
     * @param files The HeritrixFiles object for this crawl. Not Null.
     * @param errorMessage A place where error messages accumulate. Not Null.
     * @param failedFiles List of files that failed to upload. Not Null.
     * @return An object containing info about the domains harvested.
     * @throws ArgumentNotValid if an argument isn't valid.
     */
    public HarvestReport storeFiles(HeritrixFiles files, StringBuilder errorMessage, List<File> failedFiles)
            throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(files, "HeritrixFiles files");
        ArgumentNotValid.checkNotNull(errorMessage, "StringBuilder errorMessage");
        ArgumentNotValid.checkNotNull(failedFiles, "List<File> failedFiles");
        long jobID = files.getJobID();
        try {
            IngestableFiles inf = new IngestableFiles(files);

            inf.closeOpenFiles(WAIT_FOR_HERITRIX_TIMEOUT_SECS);
            // Create a metadata ARC file
            HarvestDocumentation.documentHarvest(inf);
            // Upload all files

            // Check, if arcsdir or warcsdir is empty
            // Send a notification, if this is the case
            if (inf.getArcFiles().isEmpty() && inf.getWarcFiles().isEmpty()) {
                String errMsg = "Probable error in Heritrix job setup. "
                        + "No arcfiles or warcfiles generated by Heritrix for job " + jobID;
                log.warn(errMsg);
                NotificationsFactory.getInstance().notify(errMsg, NotificationType.WARNING);
            } else {
                if (!inf.getArcFiles().isEmpty()) {
                    uploadFiles(inf.getArcFiles(), errorMessage, failedFiles);
                }
                if (!inf.getWarcFiles().isEmpty()) {
                    uploadFiles(inf.getWarcFiles(), errorMessage, failedFiles);
                }
            }

            // Now the ARC/WARC files have been uploaded,
            // we finally upload the metadata archive file.
            uploadFiles(inf.getMetadataArcFiles(), errorMessage, failedFiles);

            // Make the harvestReport ready for uploading
            HarvestReportGenerator hrg = new HarvestReportGenerator(files);
            DomainStatsReport dsr = new DomainStatsReport(hrg.getDomainStatsMap(), hrg.getDefaultStopReason());
            return HarvestReportFactory.generateHarvestReport(dsr);

        } catch (IOFailure e) {
            String errMsg = "IOFailure occurred, while trying to upload files";
            log.warn(errMsg, e);
            throw new IOFailure(errMsg, e);
        }
    }

    /**
     * Upload given files to the archive repository.
     *
     * @param files List of (ARC/WARC) files to upload.
     * @param errorMessage Accumulator for error messages.
     * @param failedFiles Accumulator for failed files.
     */
    private void uploadFiles(List<File> files, StringBuilder errorMessage, List<File> failedFiles) {
        // Upload all archive files
        if (files != null) {
            for (File f : files) {
                try {
                    log.info("Uploading file '{}' to arcrepository.", f.getName());
                    arcRepController.store(f);
                    log.info("File '{}' uploaded successfully to arcrepository.", f.getName());
                } catch (Exception e) {
                    File oldJobsDir = new File(Settings.get(HarvesterSettings.HARVEST_CONTROLLER_OLDJOBSDIR));
                    String errorMsg = "Error uploading arcfile '" + f.getAbsolutePath() + "' Will be moved to '"
                            + oldJobsDir.getAbsolutePath() + "'";
                    errorMessage.append(errorMsg).append("\n").append(e.toString()).append("\n");
                    log.warn(errorMsg, e);
                    failedFiles.add(f);
                }
            }
        }
    }

    /**
     * Retrieve the list of jobs for deduplicate reduction.
     * <p>
     * Runs through all metadata entries, finding duplicate reduction entries, and parsing all jobIDs in them, warning
     * only on errors.
     *
     * @param metadataEntries list of metadataEntries.
     * @return the list of jobs for deduplicate reduction.
     */
    private List<Long> parseJobIDsForDuplicateReduction(List<MetadataEntry> metadataEntries) {
        // find metadataEntry for duplicatereduction if any.
        List<Long> result = new ArrayList<Long>();
        for (MetadataEntry me : metadataEntries) {
            if (me.isDuplicateReductionMetadataEntry()) {
                String s = new String(me.getData());
                if (s.isEmpty()) { // An empty string is now possible
                    continue;
                }
                String[] longs = s.split(",");
                for (String stringLong : longs) {
                    try {
                        result.add(Long.parseLong(stringLong));
                    } catch (NumberFormatException e) {
                        log.warn("Unable to convert String '{}' in duplicate reduction jobid list metadataEntry '{}'"
                                + " to a jobID. Ignoring.", stringLong, s, e);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Get an index for deduplication. This will make a call to the index server, requesting an index for the given IDs.
     * The files will then be cached locally.
     * <p>
     * If we request index for IDs that don't exist/have problems, we get a smaller set of IDs in our cache files, and
     * next time we ask for the same index, we will call the index server again. This will be handled well, though,
     * because if the ids are still missing, we will get a reply telling us to use the cached smaller index anyway.
     *
     * @param metadataEntries list of metadataEntries top get jobIDs from.
     * @return a directory containing the index itself.
     * @throws IOFailure on errors retrieving the index from the client. FIXME Better forgiving handling of no index
     * available Add setting for disable deduplication if no index available
     */
    private File fetchDeduplicateIndex(List<MetadataEntry> metadataEntries) {
        // Get list of jobs, which should be used for duplicate reduction
        // and retrieve a luceneIndex from the IndexServer
        // based on the crawl.logs from these jobs and their CDX'es.
        Set<Long> jobIDsForDuplicateReduction = new HashSet<Long>(parseJobIDsForDuplicateReduction(metadataEntries));

        // The client for requesting job index.
        JobIndexCache jobIndexCache = IndexClientFactory.getDedupCrawllogInstance();

        // Request the index and return the index file.
        Index<Set<Long>> jobIndex = jobIndexCache.getIndex(jobIDsForDuplicateReduction);
        // Check which jobs didn't become part of the index.
        Set<Long> diffSet = new HashSet<Long>(jobIDsForDuplicateReduction);
        diffSet.removeAll(jobIndex.getIndexSet());
        if (log.isDebugEnabled()) {
            log.debug("Received deduplication index containing {} jobs. {}", jobIndex.getIndexSet().size(),
                    ((diffSet.size() > 0) ? "Missing jobs: " + StringUtils.conjoin(",", diffSet) : ""));
        }

        return jobIndex.getIndexFile();
    }

    /**
     * Submit a batch job to generate cdx for all metadata files for a job, and report result in a list.
     *
     * @param jobid The job to get cdx for.
     * @return A list of cdx records.
     * @throws ArgumentNotValid If jobid is 0 or negative.
     * @throws IOFailure On trouble generating the cdx
     */
    public static List<CDXRecord> getMetadataCDXRecordsForJob(long jobid) {
        ArgumentNotValid.checkPositive(jobid, "jobid");
        FileBatchJob cdxJob = new ArchiveExtractCDXJob(false);
        cdxJob.processOnlyFilesMatching(jobid + "-metadata-[0-9]+\\.(w)?arc(\\.gz)?");
        File f;
        try {
            f = File.createTempFile(jobid + "-reports", ".cdx", FileUtils.getTempDir());
        } catch (IOException e) {
            throw new IOFailure("Could not create temporary file", e);
        }
        BatchStatus status = ArcRepositoryClientFactory.getViewerInstance().batch(cdxJob,
                Settings.get(CommonSettings.USE_REPLICA_ID));
        status.getResultFile().copyTo(f);
        List<CDXRecord> records;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(f));
            records = new ArrayList<CDXRecord>();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String[] parts = line.split("\\s+");
                CDXRecord record = new CDXRecord(parts);
                records.add(record);
            }
        } catch (IOException e) {
            throw new IOFailure("Unable to read results from file '" + f + "'", e);
        } finally {
            IOUtils.closeQuietly(reader);
            FileUtils.remove(f);
        }
        return records;
    }

}
