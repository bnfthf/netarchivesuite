/* File:        $Id$Id$
 * Revision:    $Revision$Revision$
 * Author:      $Author$Author$
 * Date:        $Date$Date$
 *
 * The Netarchive Suite - Software to harvest and preserve websites
 * Copyright 2004-2010 Det Kongelige Bibliotek and Statsbiblioteket, Denmark
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.exceptions.PermissionDenied;
import dk.netarkivet.common.exceptions.UnknownID;
import dk.netarkivet.common.utils.DBUtils;
import dk.netarkivet.common.utils.ExceptionUtils;

/**
 * A database-based implementation of the ExtendedFieldDBDAO class.
 */
public class ExtendedFieldDBDAO extends ExtendedFieldDAO {
	/** The logger for this class. */
	private final Log log = LogFactory.getLog(getClass());

    protected ExtendedFieldDBDAO() {

        Connection connection = HarvestDBConnection.get();
        try {
            DBSpecifics.getInstance().updateTable(
                    DBSpecifics.EXTENDEDFIELDTYPE_TABLE,
                    DBSpecifics.EXTENDEDFIELDTYPE_TABLE_REQUIRED_VERSION);

            DBSpecifics.getInstance().updateTable(
                    DBSpecifics.EXTENDEDFIELD_TABLE,
                    DBSpecifics.EXTENDEDFIELD_TABLE_REQUIRED_VERSION);

            DBSpecifics.getInstance().updateTable(
                    DBSpecifics.EXTENDEDFIELDVALUE_TABLE,
                    DBSpecifics.EXTENDEDFIELDVALUE_TABLE_REQUIRED_VERSION);
            
        } finally {
            HarvestDBConnection.release(connection);
        }
    }
	
	
    protected Connection getConnection() {
    	return HarvestDBConnection.get();
    }
    
	public synchronized void create(ExtendedField aExtendedField) {
		ArgumentNotValid.checkNotNull(aExtendedField, "aExtendedField");

		Connection connection = getConnection();
		if (aExtendedField.getExtendedFieldID() != null) {
			log
				.warn("The extendedFieldID for this extended Field is already set. "
						+ "This should probably never happen.");
		} else {
			aExtendedField.setExtendedFieldID(generateNextID(connection));
		}

		log.debug("Creating " + aExtendedField.toString());

		PreparedStatement statement = null;
		try {
			connection.setAutoCommit(false);
			statement = connection.prepareStatement(""
			        + "INSERT INTO extendedfield "
					+ "            (extendedfield_id, "
					+ "             extendedfieldtype_id, "
					+ "             name, "
					+ "             format, "
					+ "             defaultvalue, "
					+ "             options, "
					+ "             datatype, "
					+ "             mandatory, "
					+ "             sequencenr) "
					+ "VALUES      (?, "
					+ "             ?, "
					+ "             ?, "
					+ "             ?, "
					+ "             ?, "
					+ "             ?, "
					+ "             ?, "
					+ "             ?, "
					+ "             ?) ");

			statement.setLong(1, aExtendedField.getExtendedFieldID());
			statement.setLong(2, aExtendedField.getExtendedFieldTypeID());
			statement.setString(3, aExtendedField.getName());
			statement.setString(4, aExtendedField.getFormattingPattern());
			statement.setString(5, aExtendedField.getDefaultValue());
			statement.setString(6, aExtendedField.getOptions());
			statement.setInt(7, aExtendedField.getDatatype());
			statement.setBoolean(8, aExtendedField.isMandatory());
			statement.setInt(9, aExtendedField.getSequencenr());

			statement.executeUpdate();
			connection.commit();
		} catch (SQLException e) {
			String message = "SQL error creating extended field "
					+ aExtendedField + " in database" + "\n"
					+ ExceptionUtils.getSQLExceptionCause(e);
			log.warn(message, e);
			throw new IOFailure(message, e);
		} finally {
			DBUtils.rollbackIfNeeded(connection, "create extended field",
					aExtendedField);
			HarvestDBConnection.release(connection);
		}
	}

	/**
	 * Generates the next id of a extended field. this implementation
	 * retrieves the maximum value of extendedfield_id in the DB, and returns this
	 * value + 1.
	 * 
	 * @return The next available ID
	 */
	private Long generateNextID(Connection c) {
		Long maxVal = DBUtils.selectLongValue(c,
				"SELECT max(extendedfield_id) FROM extendedfield");
		
		if (maxVal == null) {
			maxVal = 0L;
		}
		return maxVal + 1L;
	}

	/**
	 * Check whether a particular extended Field exists.
	 * 
	 * @param aExtendedfield_id
	 *            Id of the extended field.
	 * @return true if the extended field exists.
	 */
	public boolean exists(Long aExtendedfield_id) {
		ArgumentNotValid.checkNotNull(aExtendedfield_id,
				"Long aExtendedfield_id");

		Connection c = getConnection();
		try {
			return exists(c, aExtendedfield_id);
		} finally {
			HarvestDBConnection.release(c);
		}

	}

	private synchronized boolean exists(Connection c, Long aExtendedfield_id) {
		return 1 == DBUtils
				.selectLongValue(
						c,
						"SELECT COUNT(*) FROM extendedfield WHERE extendedfield_id = ?",
						aExtendedfield_id);
	}

	@Override
	public synchronized void update(ExtendedField aExtendedField) {
		ArgumentNotValid.checkNotNull(aExtendedField, "aExtendedField");

		Connection connection = getConnection();
		
		PreparedStatement statement = null;
		try {
			final Long extendedfield_id = aExtendedField.getExtendedFieldID();
			if (!exists(connection, extendedfield_id)) {
				throw new UnknownID("Extended Field id " + extendedfield_id
						+ " is not known in persistent storage");
			}

			connection.setAutoCommit(false);
			
			statement = connection.prepareStatement(""
			    + "UPDATE extendedfield "
				+ "SET    extendedfield_id = ?, "
				+ "       extendedfieldtype_id = ?, "
				+ "       name = ?, "
				+ "       format = ?, "
				+ "       defaultvalue = ?, "
				+ "       options = ?, "
				+ "       datatype = ?, "
				+ "       mandatory = ?, "
				+ "       sequencenr = ? "
				+ "WHERE  extendedfield_id = ? ");
			
			statement.setLong(1, aExtendedField.getExtendedFieldID());
			statement.setLong(2, aExtendedField.getExtendedFieldTypeID());
			statement.setString(3, aExtendedField.getName());
			statement.setString(4, aExtendedField.getFormattingPattern());
			statement.setString(5, aExtendedField.getDefaultValue());
			statement.setString(6, aExtendedField.getOptions());
			statement.setInt(7, aExtendedField.getDatatype());
			statement.setBoolean(8, aExtendedField.isMandatory());
			statement.setInt(9, aExtendedField.getSequencenr());
			statement.setLong(10, aExtendedField.getExtendedFieldID());
			
			statement.executeUpdate();
			connection.commit();
		} catch (SQLException e) {
			String message = "SQL error updating extendedfield " + aExtendedField + " in database"
					+ "\n" + ExceptionUtils.getSQLExceptionCause(e);
			log.warn(message, e);
			throw new IOFailure(message, e);
		} finally {
			DBUtils.rollbackIfNeeded(connection, "update extendedfield", aExtendedField);
			HarvestDBConnection.release(connection);
		}
	}

	@Override
	public synchronized ExtendedField read(Long aExtendedfield_id) {
		ArgumentNotValid.checkNotNull(aExtendedfield_id, "aExtendedfield_id");
		Connection connection = getConnection();
		try {
			return read(connection, aExtendedfield_id);
		} finally {
			HarvestDBConnection.release(connection);
		}
	}

	private synchronized ExtendedField read(Connection connection, Long aExtendedfield_id) {
		if (!exists(connection, aExtendedfield_id)) {
			throw new UnknownID("Extended Field id " + aExtendedfield_id
					+ " is not known in persistent storage");
		}
		
		ExtendedField extendedField = null;
		PreparedStatement statement = null;
		try {
			statement = connection.prepareStatement(""
				+ "SELECT extendedfieldtype_id, "
				+ "       name, "
				+ "       format, "
				+ "       defaultvalue, "
				+ "       options, "
				+ "       datatype, "
				+ "       mandatory, "
				+ "       sequencenr "
				+ "FROM   extendedfield "
				+ "WHERE  extendedfield_id = ? ");
			
			statement.setLong(1, aExtendedfield_id);
			ResultSet result = statement.executeQuery();
			result.next();
			
			long extendedfieldtype_id = result.getLong(1);
			String name = result.getString(2);
			String format = result.getString(3);
			String defaultvalue = result.getString(4);
			String options = result.getString(5);
			int datatype = result.getInt(6);
			boolean mandatory = result.getBoolean(7);
			int sequencenr = result.getInt(8);

			extendedField = new ExtendedField(aExtendedfield_id, extendedfieldtype_id, name, format, datatype, mandatory, sequencenr, defaultvalue, options);

			return extendedField;
		} catch (SQLException e) {
			String message = "SQL error reading extended Field " + aExtendedfield_id + " in database"
					+ "\n" + ExceptionUtils.getSQLExceptionCause(e);
			log.warn(message, e);
			throw new IOFailure(message, e);
		}
	}

	public synchronized List<ExtendedField> getAll(long aExtendedFieldType_id) {
		Connection c = getConnection();
		try {
			List<Long> idList = DBUtils.selectLongList(c,
					"SELECT extendedfield_id FROM extendedfield WHERE extendedfieldtype_id = ? "
							+ "ORDER BY sequencenr ASC", aExtendedFieldType_id);
			List<ExtendedField> extendedFields = new LinkedList<ExtendedField>();
			for (Long extendedfield_id : idList) {
				extendedFields.add(read(c, extendedfield_id));
			}
			return extendedFields;
		} finally {
			HarvestDBConnection.release(c);
		}
	}



	@Override
	public void delete(long aExtendedfield_id) throws IOFailure {
        ArgumentNotValid.checkNotNull(aExtendedfield_id, "aExtendedfield_id");

		Connection c = getConnection();
        PreparedStatement stm = null;
        try {
            c.setAutoCommit(false);

            stm = c.prepareStatement("DELETE FROM extendedfieldvalue WHERE extendedfield_id = ?");
            stm.setLong(1, aExtendedfield_id);
            stm.executeUpdate();
            stm = c.prepareStatement("DELETE FROM extendedfield WHERE extendedfield_id = ?");
            stm.setLong(1, aExtendedfield_id);
            stm.executeUpdate();
            
            c.commit();

        } catch (SQLException e) {
            String message =
                "SQL error deleting extended fields for ID " + aExtendedfield_id
                + "\n"+ ExceptionUtils.getSQLExceptionCause(e);
            log.warn(message, e);
        } finally {
            DBUtils.rollbackIfNeeded(c, "delete extended field", aExtendedfield_id);
            HarvestDBConnection.release(c);
        }
		
	}

    public static synchronized ExtendedFieldDAO getInstance() {
        if (instance == null) {
            instance = new ExtendedFieldDBDAO();
        }
        return instance;
    }
}
