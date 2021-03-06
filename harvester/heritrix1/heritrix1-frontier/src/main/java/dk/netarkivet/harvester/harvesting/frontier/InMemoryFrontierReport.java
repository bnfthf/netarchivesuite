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
package dk.netarkivet.harvester.harvesting.frontier;

import java.io.Serializable;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Implements a frontier report wrapper that is stored in memory. This implementation is intended for small reports that
 * are the result of the filtering of a full frontier report obtained from Heritrix 1. This implementation is
 * serializable, so it can be transmitted in a JMS message.
 * <p>
 * The report lines are sorted according to the natural order defined by {@link FrontierReportLine}, e.g. descending
 * size of the queue.
 */
@SuppressWarnings({"serial"})
public class InMemoryFrontierReport extends AbstractFrontierReport implements Serializable {

    /**
     * The lines of the report, sorted by natural order.
     */
    private TreeSet<FrontierReportLine> lines = new TreeSet<FrontierReportLine>();

    /**
     * The lines of the report, mapped by domain name.
     */
    private TreeMap<String, FrontierReportLine> linesByDomain = new TreeMap<String, FrontierReportLine>();

    /**
     * Default empty contructor.
     */
    InMemoryFrontierReport() {

    }

    /**
     * Builds an empty report.
     *
     * @param jobName the Heritrix job name
     */
    public InMemoryFrontierReport(String jobName) {
        super(jobName);
    }

    /**
     * Returns the lines of the report.
     *
     * @return the lines of the report.
     */
    public FrontierReportLine[] getLines() {
        return (FrontierReportLine[]) lines.toArray(new FrontierReportLine[lines.size()]);
    }

    @Override
    public void addLine(FrontierReportLine line) {
        lines.add(line);
        linesByDomain.put(line.getDomainName(), line);
    }

    @Override
    public FrontierReportLine getLineForDomain(String domainName) {
        return linesByDomain.get(domainName);
    }

    /**
     * Returns the report size, e.g. the count of report lines.
     *
     * @return the report size
     */
    public int getSize() {
        return lines.size();
    }

}
