/*
 * #%L
 * Netarchivesuite - monitor
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

package dk.netarkivet.monitor.webinterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.RuntimeMBeanException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.utils.ExceptionUtils;
import dk.netarkivet.common.utils.I18n;
import dk.netarkivet.common.webinterface.HTMLUtils;
import dk.netarkivet.monitor.jmx.HostForwarding;
import dk.netarkivet.monitor.logging.SingleLogRecord;

/**
 * Implementation of StatusEntry, that receives its data from the MBeanServer (JMX).
 */
public class JMXStatusEntry implements StatusEntry {

    private static final Logger log = LoggerFactory.getLogger(JMXStatusEntry.class);


    /** The ObjectName assigned to the MBean for this JMXStatusEntry. */
    private ObjectName mBeanName;
    /** JMX Query to retrieve the logmessage associated with this Entry. */
    private static final String LOGGING_QUERY = "dk.netarkivet.common.logging:*";
    /** JMX Attribute containing the logmessage itself. */
    private static final String JMXLogMessageAttribute = "RecordString";
    /** MBeanserver used by this class. */
    private static final MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer();

    /** Internationalisation object. */
    private static final I18n I18N = new I18n(dk.netarkivet.monitor.Constants.TRANSLATIONS_BUNDLE);

    /**
     * Constructor for the JMXStatusEntry.
     *
     * @param mBeanName The ObjectName to be assigned to the MBean representing this JMXStatusEntry.
     */
    public JMXStatusEntry(ObjectName mBeanName) {
        ArgumentNotValid.checkNotNull(mBeanName, "ObjectName mBeanName");
        this.mBeanName = mBeanName;
    }

    /**
     * @return the location designated by the key {@link JMXSummaryUtils#JMXPhysLocationProperty}
     */
    public String getPhysicalLocation() {
        return mBeanName.getKeyProperty(JMXSummaryUtils.JMXPhysLocationProperty);
    }

    /**
     * @return the hostname designated by the key {@link JMXSummaryUtils#JMXMachineNameProperty}
     */
    public String getMachineName() {
        return mBeanName.getKeyProperty(JMXSummaryUtils.JMXMachineNameProperty);
    }

    /**
     * @return the http-port designated by the key {@link JMXSummaryUtils#JMXHttpportProperty}
     */
    public String getHTTPPort() {
        return mBeanName.getKeyProperty(JMXSummaryUtils.JMXHttpportProperty);
    }

    /**
     * @return the application name designated by the key {@link JMXSummaryUtils#JMXApplicationNameProperty}
     */
    public String getApplicationName() {
        return mBeanName.getKeyProperty(JMXSummaryUtils.JMXApplicationNameProperty);
    }

    /**
     * @return the application inst id designated by the key {@link JMXSummaryUtils#JMXApplicationInstIdProperty}
     */
    public String getApplicationInstanceID() {
        return mBeanName.getKeyProperty(JMXSummaryUtils.JMXApplicationInstIdProperty);
    }

    /**
     * @return the harvest priority designated by the key {@link JMXSummaryUtils#JMXHarvestChannelProperty}
     */
    public String getHarvestPriority() {
        return mBeanName.getKeyProperty(JMXSummaryUtils.JMXHarvestChannelProperty);
    }

    /**
     * @return the replica id designated by the key {@link JMXSummaryUtils#JMXArchiveReplicaNameProperty}
     */
    public String getArchiveReplicaName() {
        return mBeanName.getKeyProperty(JMXSummaryUtils.JMXArchiveReplicaNameProperty);
    }

    /**
     * @return the index designated by the key {@link JMXSummaryUtils#JMXIndexProperty}
     */
    public String getIndex() {
        return mBeanName.getKeyProperty(JMXSummaryUtils.JMXIndexProperty);
    }

    /**
     * Gets the log message from this status entry. This implementation actually talks to an MBeanServer to get the log
     * message. Will return an explanation if remote host does not respond, throws exception or returns null.
     *
     * @param l the current Locale
     * @return A log message.
     * @throws ArgumentNotValid if the current Locale is null
     */
    public String getLogMessage(Locale l) {
        ArgumentNotValid.checkNotNull(l, "l");
        // Make sure mbeans are forwarded
        HostForwarding.getInstance(SingleLogRecord.class, mBeanServer, LOGGING_QUERY);
        try {
            String logMessage = (String) mBeanServer.getAttribute(mBeanName, JMXLogMessageAttribute);
            if (logMessage == null) {
                return HTMLUtils.escapeHtmlValues(getLogDate()
                        + I18N.getString(l, "errormsg;remote.host.returned.null.log.record"));
            } else {
                return logMessage;
            }
        } catch (RuntimeMBeanException e) {
            return HTMLUtils.escapeHtmlValues(getLogDate()
                    + I18N.getString(l, "errormsg;jmx.error.while.getting.log.record") + "\n"
                    + I18N.getString(l, "errormsg;probably.host.is.not.responding") + "\n"
                    + ExceptionUtils.getStackTrace(e));
        } catch (Exception e) {
            return HTMLUtils.escapeHtmlValues(getLogDate()
                    + I18N.getString(l, "errormsg;remote.jmx.bean.generated.exception") + "\n"
                    + ExceptionUtils.getStackTrace(e));
        }
    }

    private String getLogDate() {
        return "[" + new Date() + "] ";
    }

    /**
     * Compares two entries according to first their location, then their machine name, then their ports, and then their
     * application name, and then their index.
     *
     * @param o The object to compare with
     * @return A negative number if this entry comes first, a positive if it comes second and 0 if they are equal.
     */
    public int compareTo(StatusEntry o) {
        int c;

        if (getPhysicalLocation() != null && o.getPhysicalLocation() != null) {
            c = getPhysicalLocation().compareTo(o.getPhysicalLocation());
            if (c != 0) {
                return c;
            }
        } else if (getPhysicalLocation() == null) {
            return -1;
        } else {
            return 1;
        }

        if (getMachineName() != null && o.getMachineName() != null) {
            c = getMachineName().compareTo(o.getMachineName());
            if (c != 0) {
                return c;
            }
        } else if (getMachineName() == null) {
            return -1;
        } else {
            return 1;
        }

        if (getHTTPPort() != null && o.getHTTPPort() != null) {
            c = getHTTPPort().compareTo(o.getHTTPPort());
            if (c != 0) {
                return c;
            }
        } else if (getHTTPPort() == null) {
            return -1;
        } else {
            return 1;
        }

        if (getApplicationName() != null && o.getApplicationName() != null) {
            c = getApplicationName().compareTo(o.getApplicationName());
            if (c != 0) {
                return c;
            }
        } else if (getApplicationName() == null) {
            return -1;
        } else {
            return 1;
        }

        if (getApplicationInstanceID() != null && o.getApplicationInstanceID() != null) {
            c = getApplicationInstanceID().compareTo(o.getApplicationInstanceID());
            if (c != 0) {
                return c;
            }
        } else if (getApplicationInstanceID() == null) {
            return -1;
        } else {
            return 1;
        }

        if (getHarvestPriority() != null && o.getHarvestPriority() != null) {
            c = getHarvestPriority().compareTo(o.getHarvestPriority());
            if (c != 0) {
                return c;
            }
        } else if (getHarvestPriority() == null) {
            return -1;
        } else {
            return 1;
        }

        if (getArchiveReplicaName() != null && o.getArchiveReplicaName() != null) {
            c = getArchiveReplicaName().compareTo(o.getArchiveReplicaName());
            if (c != 0) {
                return c;
            }
        } else if (getArchiveReplicaName() == null) {
            return -1;
        } else {
            return 1;
        }

        Integer i1;
        Integer i2;
        try {
            i1 = Integer.valueOf(getIndex());
        } catch (NumberFormatException e) {
            i1 = null;
        }
        try {
            i2 = Integer.valueOf(o.getIndex());
        } catch (NumberFormatException e) {
            i2 = null;
        }

        if (i1 != null && i2 != null) {
            c = i1.compareTo(i2);
            if (c != 0) {
                return c;
            }
        } else if (i1 == null) {
            return -1;
        } else {
            return 1;
        }

        return 0;
    }

    /**
     * Query the JMX system for system status mbeans.
     *
     * @param query A JMX request, e.g. dk.netarkivet.logging:location=EAST,httpport=8080,*
     * @return A list of status entries for the mbeans that match the query.
     * @throws MalformedObjectNameException If the query has wrong format.
     */
    public static List<StatusEntry> queryJMX(String query) throws MalformedObjectNameException {
        ArgumentNotValid.checkNotNull(query, "query");

        List<StatusEntry> entries = new ArrayList<StatusEntry>();

        // Make sure mbeans are forwarded
        HostForwarding.getInstance(SingleLogRecord.class, mBeanServer, LOGGING_QUERY);
        // The "null" in this case is used to indicate no further filters on the
        // query.
        log.debug("Querying mbean server {} with {}.", mBeanServer.toString(), LOGGING_QUERY);
        Set<ObjectName> resultSet = mBeanServer.queryNames(new ObjectName(query), null);
        for (ObjectName objectName : resultSet) {
            entries.add(new JMXStatusEntry(objectName));
        }
        Collections.sort(entries);
        log.debug("Query returned {} results.", entries.size());
        return entries;
    }

    /**
     * Unregister an JMX MBean instance.
     *
     * @param query A JMX request, for picking the beans to unregister.
     * @throws MalformedObjectNameException if query is malformed.
     * @throws InstanceNotFoundException if the instanced unregistered doesn't exists.
     * @throws MBeanRegistrationException if unregeterBean is thrown.
     */
    public static void unregisterJMXInstance(String query) throws MalformedObjectNameException,
            InstanceNotFoundException, MBeanRegistrationException {
        ArgumentNotValid.checkNotNull(query, "query");
        Set<ObjectName> namesMatchingQuery = mBeanServer.queryNames(new ObjectName(query), null);
        for (ObjectName name : namesMatchingQuery) {
            mBeanServer.unregisterMBean(name);
        }
    }
}
