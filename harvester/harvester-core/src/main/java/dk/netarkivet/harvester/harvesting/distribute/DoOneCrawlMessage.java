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
package dk.netarkivet.harvester.harvesting.distribute;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

import dk.netarkivet.common.distribute.ChannelID;
import dk.netarkivet.common.distribute.Channels;
import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.harvester.datamodel.HarvestDefinitionInfo;
import dk.netarkivet.harvester.datamodel.Job;
import dk.netarkivet.harvester.distribute.HarvesterMessage;
import dk.netarkivet.harvester.distribute.HarvesterMessageVisitor;
import dk.netarkivet.harvester.harvesting.metadata.MetadataEntry;

/**
 * Container for doOneCrawl request. Contains the crawler job definition.
 */
@SuppressWarnings({"serial"})
public class DoOneCrawlMessage extends HarvesterMessage implements Serializable {

    /** the Job to crawl. */
    private Job submittedJob;

    /** The original harvest info. */
    private final HarvestDefinitionInfo origHarvestInfo;

    /** Extra metadata associated with the crawl-job. */
    private List<MetadataEntry> metadata;

    /**
     * A NetarkivetMessage that contains a Job for Heritrix.
     *
     * @param submittedJob the Job to crawl
     * @param to the ChannelID for the Server
     * @param metadata A list of job-metadata
     * @throws ArgumentNotValid when submittedJob is null
     */
    public DoOneCrawlMessage(Job submittedJob, ChannelID to, HarvestDefinitionInfo harvestInfo,
            List<MetadataEntry> metadata) throws ArgumentNotValid {
        super(to, Channels.getError());
        ArgumentNotValid.checkNotNull(submittedJob, "submittedJob");
        ArgumentNotValid.checkNotNull(metadata, "metadata");
        this.submittedJob = submittedJob;
        this.origHarvestInfo = harvestInfo;
        this.metadata = metadata;
    }

    /**
     * @return the Job
     */
    public Job getJob() {
        return submittedJob;
    }

    /**
     * @return the origHarvestInfo
     */
    public HarvestDefinitionInfo getOrigHarvestInfo() {
        return origHarvestInfo;
    }

    /**
     * @return Returns the metadata.
     */
    public List<MetadataEntry> getMetadata() {
        return metadata;
    }

    /**
     * Should be implemented as a part of the visitor pattern. fx.: public void accept(HarvesterMessageVisitor v) {
     * v.visit(this); }
     *
     * @param v A message visitor
     */
    public void accept(HarvesterMessageVisitor v) {
        v.visit(this);
    }

    /**
     * @return a String that represents the message - only for debugging !
     */
    public String toString() {
        return super.toString() + " Job: " + submittedJob + ", metadata: " + metadata;
    }

    /**
     * Method needed to de-serializable an object of this class.
     *
     * @param s an ObjectInputStream
     * @throws ClassNotFoundException In case the object read is of unknown class.
     * @throws IOException On I/O trouble reading the object.
     */
    private void readObject(ObjectInputStream s) throws ClassNotFoundException, IOException {
        s.defaultReadObject();
    }

    /**
     * Method needed to serializable an object of this class.
     *
     * @param s an ObjectOutputStream
     * @throws IOException On I/O trouble writing the object.
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
    }

}
