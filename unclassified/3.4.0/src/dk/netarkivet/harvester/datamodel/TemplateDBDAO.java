/* File:        $Id$
 * Revision:    $Revision$
 * Author:      $Author$
 * Date:        $Date$
 *
 * The Netarchive Suite - Software to harvest and preserve websites
 * Copyright 2004-2007 Det Kongelige Bibliotek and Statsbiblioteket, Denmark
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package dk.netarkivet.harvester.datamodel;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.exceptions.PermissionDenied;
import dk.netarkivet.common.exceptions.UnknownID;

/**
 * Implements the TemplateDAO with databases.
 *
 * The statements to create the tables are now in
 * scripts/sql/createfullhddb.sql
 */

public class TemplateDBDAO extends TemplateDAO {
    /** the log.*/
    private final Log log = LogFactory.getLog(getClass());

    TemplateDBDAO() {
        DBConnect.checkTableVersion("ordertemplates", 1);
    }

    /**
     * Read an XML order file for the named order XML.
     *
     * @param orderXmlName The name of the order.xml document
     * @return The contents of this order.xml document
     */
    public synchronized HeritrixTemplate read(String orderXmlName) {
        ArgumentNotValid.checkNotNullOrEmpty(orderXmlName, "String orderXmlName");
        Connection c = DBConnect.getDBConnection();
        PreparedStatement s = null;
        try {
            s = c.prepareStatement(
                    "SELECT orderxml FROM ordertemplates WHERE name = ?");
            s.setString(1, orderXmlName);
            ResultSet res = s.executeQuery();
            if (!res.next()) {
                throw new UnknownID("Can't find template " + orderXmlName);
            }
            Clob clob = res.getClob(1);
            SAXReader reader = new SAXReader();
            // TODO: Check what happens on non-ascii
            Document orderXMLdoc = reader.read(clob.getCharacterStream());
            return new HeritrixTemplate(orderXMLdoc);
        } catch (SQLException e) {
            final String message = "SQL error finding order.xml for "
                + orderXmlName;
            log.warn(message, e);
            throw new IOFailure(message, e);
        } catch (DocumentException e) {
            final String message = "Error parsing order.xml string for "
                + orderXmlName;
            log.warn(message, e);
            throw new IOFailure(message, e);
        } finally {
            DBConnect.closeStatementIfOpen(s);
        }
    }

    /**
     * Returns an iterator with all names of order.xml-templates.
     *
     * @return Iterator<String> with all names of templates (without .xml).
     */
    public synchronized Iterator<String> getAll() {
        try {
            List<String> names = DBConnect.selectStringlist("SELECT name FROM ordertemplates ORDER BY name");
            return names.iterator();
        } catch (SQLException e) {
            throw new IOFailure("SQL error finding order.xml names", e);
        }
    }

    /** Return true if the database contains a template with the given name.
     *
     * @param orderXmlName Name of an order.xml template (without .xml).
     * @return True if such a template exists.
     */
    public synchronized boolean exists(String orderXmlName) {
        int count = DBConnect.selectIntValue("SELECT COUNT(*) FROM ordertemplates WHERE name = ?", orderXmlName);
        return count == 1;
    }

    /** Create a template. The template must not already exist.
     *
     * @param orderXmlName Name of the template.
     * @param orderXml XML documents that is a Heritrix order.xml template.
     */
    public synchronized void create(String orderXmlName, HeritrixTemplate orderXml) {
        if (exists(orderXmlName)) {
            throw new PermissionDenied("An order template called "
                    + orderXmlName + " already exists");
        }
        Connection c = DBConnect.getDBConnection();
        PreparedStatement s = null;
        try {
            s = c.prepareStatement("INSERT INTO ordertemplates "
                    + "( name, orderxml ) VALUES ( ?, ? )");
            DBConnect.setStringMaxLength(s, 1, orderXmlName,
                    Constants.MAX_NAME_SIZE, orderXmlName, "length");
            DBConnect.setClobMaxLength(s, 2, orderXml.getXML(),
                    Constants.MAX_ORDERXML_SIZE, "size", orderXmlName);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new IOFailure("SQL error creating template " + orderXmlName, e);
        } finally {
            DBConnect.closeStatementIfOpen(s);
        }
    }

    /** Describe where a given template has been used.
     *
     * @param orderXmlName Name of the template.
     * @return A string describing the usages of the template, or null if it
     *  is not used.
     */
    public String describeUsages(String orderXmlName) {
        return DBConnect.getUsages("SELECT DISTINCT domains.name "
                + "  FROM domains, configurations, ordertemplates"
                + " WHERE ordertemplates.name = ?"
                + "   AND configurations.template_id = ordertemplates.template_id"
                + "   AND domains.domain_id = configurations.domain_id",
                orderXmlName, orderXmlName);
    }

    /** Update a template. The template must already exist.
     *
     * @param orderXmlName Name of the template.
     * @param orderXml XML document that is a Heritrix order.xml template.
     * @throws ArgumentNotValid if not valid order.xml
     */
    public synchronized void update(String orderXmlName, HeritrixTemplate orderXml) {
        if (!exists(orderXmlName)) {
            throw new PermissionDenied("No order template called "
                    + orderXmlName + " exists");
        } 
        Connection c = DBConnect.getDBConnection();
        PreparedStatement s = null;
        try {
            s = c.prepareStatement("UPDATE ordertemplates "
                    + "SET orderxml = ? "
                    + "WHERE name = ?");
            DBConnect.setClobMaxLength(s, 1, orderXml.getXML(),
                    Constants.MAX_ORDERXML_SIZE, "size", orderXmlName);
            s.setString(2, orderXmlName);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new IOFailure("SQL error updating template "
                    + orderXmlName, e);
        } finally {
            DBConnect.closeStatementIfOpen(s);
        }
    }

    /** Delete a template entirely from the database.
     *
     * @param orderXmlName Name of the template to delete.
     * @throws ArgumentNotValid if the template is in use.
     */
    public synchronized void delete(String orderXmlName) {
        if (!exists(orderXmlName)) {
            throw new PermissionDenied("No order template called "
                    + orderXmlName + " exists");
        }
        Connection c = DBConnect.getDBConnection();
        PreparedStatement s = null;
        try {
            String usages = describeUsages(orderXmlName);
            if (usages != null) {
                String message = "Cannot delete " + orderXmlName
                        + " as it is used in " + usages;
                log.debug(message);
                throw new PermissionDenied(message);
            }
            s = c.prepareStatement("DELETE FROM ordertemplates "
                                + "WHERE name = ?");
            s.setString(1, orderXmlName);
            s.executeUpdate();
            log.debug("Deleting template " + orderXmlName);
        } catch (SQLException e) {
            throw new IOFailure("SQL error deleting template "
                    + orderXmlName, e);
        } finally {
            DBConnect.closeStatementIfOpen(s);
        }
    }
}