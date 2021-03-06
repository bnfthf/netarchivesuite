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

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.exceptions.IllegalState;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.harvester.HarvesterSettings;
import dk.netarkivet.harvester.datamodel.H1HeritrixTemplate;
import dk.netarkivet.harvester.datamodel.HeritrixTemplate;

/**
 * A HeritrixLauncher object wraps around an instance of the web crawler Heritrix. The object is constructed with the
 * necessary information to do a crawl. The crawl is performed when doOneCrawl() is called. doOneCrawl() monitors
 * progress and returns when the crawl is finished or must be stopped because it has stalled.
 */
public abstract class HeritrixLauncher {

    /** Class encapsulating placement of various files. */
    private HeritrixFiles files;

    /** the arguments passed to the HeritricController constructor. */
    private Object[] args;

    /** The period to wait in seconds before checking if Heritrix has done anything. */
    protected static final int CRAWL_CONTROL_WAIT_PERIOD = Settings.getInt(HarvesterSettings.CRAWL_LOOP_WAIT_TIME);

    /**
     * Private HeritrixLauncher constructor. Sets up the HeritrixLauncher from the given order file and seedsfile.
     *
     * @param files Object encapsulating location of Heritrix crawldir and configuration files.
     * @throws ArgumentNotValid If either seedsfile or orderfile does not exist.
     */
    protected HeritrixLauncher(HeritrixFiles files) throws ArgumentNotValid {
        if (!files.getOrderXmlFile().isFile()) {
            throw new ArgumentNotValid("File '" + files.getOrderXmlFile().getName() + "' must exist in order for "
                    + "Heritrix to run. This filepath does not refer to existing file: "
                    + files.getOrderXmlFile().getAbsolutePath());
        }
        if (!files.getSeedsTxtFile().isFile()) {
            throw new ArgumentNotValid("File '" + files.getSeedsTxtFile().getName() + "' must exist in order for "
                    + "Heritrix to run. This filepath does not refer to existing file: "
                    + files.getSeedsTxtFile().getAbsolutePath());
        }
        this.files = files;
        this.args = new Object[] {files};
    }

    /**
     * Generic constructor to allow HeritrixLauncher to use any implementation of HeritrixController.
     *
     * @param args the arguments to be passed to the constructor or non-static factory method of the HeritrixController
     * class specified in settings
     */
    public HeritrixLauncher(Object... args) {
        this.args = args;
    }

    /**
     * Launches the crawl and monitors its progress.
     *
     * @throws IOFailure
     */
    public abstract void doCrawl() throws IOFailure;

    /**
     * @return an instance of the wrapper class for Heritrix files.
     */
    protected HeritrixFiles getHeritrixFiles() {
        return files;
    }

    /**
     * @return the optional arguments used to initialize the chosen Heritrix1 controller implementation.
     */
    protected Object[] getControllerArguments() {
        return args;
    }

    public void setupOrderfile(HeritrixFiles files) {
        makeTemplateReadyForHeritrix1(files);
    }
    
    /**
     * 
     * Updates the diskpath value, archivefile_prefix, seedsfile, and deduplication -information.
     * @param files Files associated with a Heritrix1 crawl-job.
     * @throws IOFailure
     *  
     *
     * This method prepares the orderfile used by the Heritrix crawler. 
     * </p> 1. Verify that the template is in fact a H1HeritrixTemplate 
     * </p> 2. alters the orderfile in the
     * following-way: (overriding whatever is in the orderfile)</br>
     * <ol>
     * <li>sets the disk-path to the outputdir specified in HeritrixFiles.</li>
     * <li>sets the seedsfile to the seedsfile specified in HeritrixFiles.</li>
     * <li>sets the prefix of the arcfiles to unique prefix defined in HeritrixFiles</li>
     * <li>checks that the arcs-file dir is 'arcs' - to ensure that we know where the arc-files are when crawl finishes</li>
     * <p>
     * <li>if deduplication is enabled, sets the node pointing to index directory for deduplication (see step 3)</li>
     * </ol>
     * 3. saves the orderfile back to disk</p>
     * <p>
     * 4. if deduplication is enabled in the order.xml, it writes the absolute path of the lucene index used by the
     * deduplication processor.
     *
     * @throws IOFailure - When the orderfile could not be saved to disk 
     *                     When a specific element cannot be found in the document. 
     */
    public static void makeTemplateReadyForHeritrix1(HeritrixFiles files) throws IOFailure {
    	HeritrixTemplate templ = HeritrixTemplate.read(files.getOrderXmlFile());
    	// Verify that the template in the job is a Heritrix3Template
    	if (templ instanceof H1HeritrixTemplate) {
    		templ.setDiskPath(files.getCrawlDir().getAbsolutePath());
    		templ.setArchiveFilePrefix(files.getArchiveFilePrefix());
    		templ.setSeedsFilePath(files.getSeedsTxtFile().getAbsolutePath());
    		if (templ.IsDeduplicationEnabled()) {
    			templ.setDeduplicationIndexLocation(files.getIndexDir().getAbsolutePath());
    		}
    		files.writeOrderXml(templ);
    	} else {
    		throw new IllegalState("The template is not a H1 template!");
    	}
    }

    
    
    
    

}
