/*
 * #%L
 * Netarchivesuite - common
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
package dk.netarkivet.common.utils.batch;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.utils.ChecksumCalculator;
import dk.netarkivet.common.utils.KeyValuePair;

/**
 * Class responsible for checksumming a list of files.
 */
@SuppressWarnings({"serial"})
public class ChecksumJob extends FileBatchJob {

    /** The log. */
    protected static final transient Logger log = LoggerFactory.getLogger(ChecksumJob.class);

    /**
     * Characters used for separating a file identifier from the checksum in the output from a checksum job.
     */
    public static final String STRING_FILENAME_SEPARATOR = "##";

    /** The constructor. */
    public ChecksumJob() {
        // Keep the batchJobTimeout at default (-1) so it will be overridden
        // by the settings for default batch timeout.
    }

    /**
     * Initialization of a ChecksumJob: a new structure for storing files failed is created.
     *
     * @param os The output stream where the output data is written.
     * @see FileBatchJob#initialize(OutputStream)
     */
    public void initialize(OutputStream os) {
    }

    /**
     * Generates MD5 checksum for file identified by 'file' and writes the checksum to the given OutputStream. Errors
     * during checksumming are logged and files on which checksumming fails are stored in filesFailed.
     *
     * @param file The file to process.
     * @param os The outputStream to write the result to
     * @return false, if errors occurred while processing the file
     * @see FileBatchJob#processFile(File, OutputStream)
     */
    public boolean processFile(File file, OutputStream os) {
        ArgumentNotValid.checkNotNull(file, "file");
        try {
            os.write((file.getName() + STRING_FILENAME_SEPARATOR + ChecksumCalculator.calculateMd5(file) + "\n")
                    .getBytes());
        } catch (IOException e) {
            log.warn("Checksumming of file {} failed: ", file.getName(), e);
            return false;
        }
        return true;
    }

    /**
     * Finishing the job requires nothing particular.
     *
     * @param os The output stream where the output data is written.
     * @see FileBatchJob#finish(OutputStream)
     */
    public void finish(OutputStream os) {
    }

    /**
     * Create a line in checksum job format from a filename and a checksum.
     *
     * @param filename A filename (no path)
     * @param checksum An MD5 checksum
     * @return A string of the correct format for a checksum job output.
     */
    public static String makeLine(String filename, String checksum) {
        ArgumentNotValid.checkNotNullOrEmpty(filename, "filename");
        ArgumentNotValid.checkNotNullOrEmpty(checksum, "checksum");
        return filename + STRING_FILENAME_SEPARATOR + checksum;
    }

    /**
     * Parse a line of output into a key-value pair.
     *
     * @param line The line to parse, of the form <b>filename</b>##<b>checksum</b>
     * @return The filename->checksum mapping.
     * @throws ArgumentNotValid if the line is not on the correct form.
     */
    public static KeyValuePair<String, String> parseLine(String line) throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(line, "checksum line");
        String[] parts = line.split(STRING_FILENAME_SEPARATOR);
        if (parts.length != 2) {
            throw new ArgumentNotValid("String '" + line + "' is not on checksum output form");
        }
        return new KeyValuePair<String, String>(parts[0], parts[1]);
    }

    /**
     * Write a human-readily description of this ChecksumJob object. Writes out the name of the ChecksumJob, the number
     * of files processed, and the number of files that failed during processing.
     *
     * @return a human-readily description of this ChecksumJob object
     */
    public String toString() {
        int noOfFailedFiles;
        if (filesFailed == null) {
            noOfFailedFiles = 0;
        } else {
            noOfFailedFiles = filesFailed.size();
        }
        return ("Checksum job " + getClass().getName() + ": [Files Processed = " + noOfFilesProcessed
                + "; Files  failed = " + noOfFailedFiles + "]");
    }

    /**
     * Invoke default method for deserializing object, and reinitialise the logger.
     *
     * @param s the InputStream
     */
    private void readObject(ObjectInputStream s) {
        try {
            s.defaultReadObject();
        } catch (Exception e) {
            throw new IOFailure("Unexpected error during deserialization", e);
        }
    }

    /**
     * Invoke default method for serializing object.
     *
     * @param s the OutputStream
     * @throws IOFailure If an exception is caught during writing of the object.
     */
    private void writeObject(ObjectOutputStream s) throws IOFailure {
        try {
            s.defaultWriteObject();
        } catch (Exception e) {
            throw new IOFailure("Unexpected error during serialization", e);
        }
    }

}
