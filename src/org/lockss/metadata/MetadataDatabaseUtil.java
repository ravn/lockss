/*
 * $Id: MetadataDatabaseUtil.java,v 1.2 2013-01-04 21:57:37 fergaloy-sf Exp $
 */

/*

 Copyright (c) 2012 Board of Trustees of Leland Stanford Jr. University,
 all rights reserved.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 Except as contained in this notice, the name of Stanford University shall not
 be used in advertising or otherwise to promote the sale, use or other dealings
 in this Software without prior written authorization from Stanford University.

 */

package org.lockss.metadata;

import static org.lockss.db.DbManager.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.lockss.app.LockssDaemon;
import org.lockss.db.DbManager;
import org.lockss.exporter.biblio.BibliographicItem;
import org.lockss.util.Logger;
import org.lockss.util.MetadataUtil;

/**
 * This class contains a set of static methods for returning metadata database
 * records as BibliobraphicItem records.
 * 
 * @author phil
 * 
 */
final public class MetadataDatabaseUtil {
  protected static Logger log = Logger.getLogger(MetadataDatabaseUtil.class);

  private MetadataDatabaseUtil() {
  }

  /**
   * Get the current LOCKSS daemon.
   * 
   * @return the LOCKSS daemon.
   */
  static private LockssDaemon getDaemon() {
    return LockssDaemon.getLockssDaemon();
  }

  /**
   * This class implements a BibliographicItem from a metadata database query.
   * 
   * @author Philip Gust
   * 
   */
  static class BibliographicDatabaseItem implements BibliographicItem {
    final String publisher;
    final String title;
    final String printisbn;
    final String eisbn;
    final String printissn;
    final String eissn;
    final String startyear;
    final String endyear;
    final String startvolume;
    final String endvolume;

    /**
     * Creates an instance from the current query result set record.
     * 
     * @param resultSet
     *          the query result set
     * @throws SQLException
     *           if any problem occurred accessing the database.
     */
    public BibliographicDatabaseItem(ResultSet resultSet) throws SQLException {
      publisher = resultSet.getString(1);
      title = resultSet.getString(2);
      printissn = MetadataUtil.formatIssn(resultSet.getString(3));
      eissn = MetadataUtil.formatIssn(resultSet.getString(4));
      printisbn = MetadataUtil.formatIssn(resultSet.getString(5));
      eisbn = MetadataUtil.formatIssn(resultSet.getString(6));
      startvolume = resultSet.getString(7);
      endvolume = resultSet.getString(7);
      startyear = resultSet.getString(8);
      endyear = resultSet.getString(8);
    }

    @Override
    public String getIsbn() {
      return (printisbn != null) ? printisbn : eisbn;
    }

    @Override
    public String getPrintIsbn() {
      return printisbn;
    }

    @Override
    public String getEisbn() {
      return eisbn;
    }

    @Override
    public String getIssn() {
      return (printissn != null) ? printissn : eissn;
    }

    @Override
    public String getPrintIssn() {
      return printissn;
    }

    @Override
    public String getEissn() {
      return eissn;
    }

    @Override
    public String getIssnL() {
      return null;
    }

    @Override
    public String getJournalTitle() {
      return title;
    }

    @Override
    public String getPublisherName() {
      return publisher;
    }

    @Override
    public String getName() {
      return null;
    }

    @Override
    public String getVolume() {
      if (startvolume == null) {
	return null;
      } else if (endvolume == null) {
	return endvolume;
      }
      return startvolume + "-" + endvolume;
    }

    @Override
    public String getYear() {
      if (startyear == null) {
	return null;
      } else if (endyear == null) {
	return endyear;
      }
      return startyear + "-" + endyear;
    }

    @Override
    public String getIssue() {
      return null;
    }

    @Override
    public String getStartVolume() {
      return startvolume;
    }

    @Override
    public String getEndVolume() {
      return endvolume;
    }

    @Override
    public String getStartYear() {
      return startyear;
    }

    @Override
    public String getEndYear() {
      return endyear;
    }

    @Override
    public String getStartIssue() {
      return null;
    }

    @Override
    public String getEndIssue() {
      return null;
    }
  }

  /**
   * The metadata database query generates rows of:
   *   publisher, title, printissn, eissn, volume, year
   * for each AU with an ISSN in the table.
   * <p>
   * The title comes from the title table, or from the title database if the
   * AU does not have a title in the title table. Entries in the title table
   * only exist if the title database title is not the journal title.
   * <p>
   * Both printissn and eissn should come from the issn table, however there
   * is no marker to identify which is which, and the title may only have one
   * or the other. If there are both, the printissn is the "smaller" of the
   * two. If there is only one there is no way to determine which it is.
   * <p>
   * The printissn comes from the title database if it exists. Otherwise,
   * it is the smaller issn in the issn table if there are two entries,
   * or null otherwise.
   * <p>
   * The eissn comes from the title database if it exists. Otherwise it is
   * the larger issn in the issn table if it is different than the printissn
   * in the title database, or null otherwise.
   * <p>
   * The year comes from the date field if it exists, or from the start year
   * in the title database otherwise. This is because the start year in the
   * title database may indicate the year of ingest rather than the year of
   * publication. Note, however, that this relies on the date field being
   * the actual publication date rather than an earlier date such as the
   * on-line date, which may be the previous year for an early-year issue.
   */
  static final String bibliographicItemsQuery;

  static {
    StringBuilder sb = new StringBuilder();
    sb.append("select distinct ");
    sb.append(" publisherFromAuid(plugin_id,au_key) as \"publisher\",");
    sb.append(" coalesce(");
    sb.append("  (select ");
    sb.append(      MD_ITEM_TABLE);
    sb.append(        ".");
    sb.append(      PRIMARY_NAME_COLUMN);
    sb.append("   from ");
    sb.append(      MD_ITEM_TABLE);
    sb.append("  ),");
    sb.append("  titleFromAuid(plugin_id,au_key)) as \"title\", ");
    sb.append(" printISSNFromAuid(plugin_id,au_key) as \"printissn\",");
    sb.append(" coalesce(");
    sb.append("  eISSNFromAuid(plugin_id,au_key),");
    sb.append("  nullif(");
    sb.append("    (select formatIssn(max(");
    sb.append(        ISSN_TABLE);
    sb.append(          ".");
    sb.append(        ISSN_COLUMN);
    sb.append("  )) from ");
    sb.append(        ISSN_TABLE);
    sb.append(          ", ");
    sb.append(        MD_ITEM_TABLE);
    sb.append("     where ");
    sb.append(        ISSN_TABLE);
    sb.append(          ".");
    sb.append(        MD_ITEM_SEQ_COLUMN);
    sb.append(        "=");
    sb.append(        MD_ITEM_TABLE);
    sb.append(          ".");
    sb.append(        MD_ITEM_SEQ_COLUMN);
    sb.append("    ),");
    sb.append("    printISSNFromAuid(plugin_id,au_key))) as \"eissn\", ");
    sb.append(" printISBNFromAuid(plugin_id,au_key) as \"printisbn\",");
    sb.append(" coalesce(");
    sb.append("  eISBNFromAuid(plugin_id,au_key),");
    sb.append("  nullif(");
    sb.append("    (select formatIsbn(max(");
    sb.append(        ISBN_COLUMN);
    sb.append("  )) from ");
    sb.append(        ISBN_TABLE);
    sb.append(          ", ");
    sb.append(        MD_ITEM_TABLE);
    sb.append("     where ");
    sb.append(        ISBN_TABLE);
    sb.append(          ".");
    sb.append(        MD_ITEM_SEQ_COLUMN);
    sb.append(        "=");
    sb.append(        MD_ITEM_TABLE);
    sb.append(          ".");
    sb.append(        MD_ITEM_SEQ_COLUMN);
    sb.append("    ),");
    sb.append("    printISBNFromAuid(plugin_id,au_key))) as \"eisbn\", ");
    sb.append(   BIB_ITEM_TABLE);
    sb.append(     ".");
    sb.append(   VOLUME_COLUMN);
    sb.append(" from ");
    sb.append(    BIB_ITEM_TABLE);
    sb.append(      ", ");
    sb.append(    MD_ITEM_TABLE);
    sb.append(" where ");
    sb.append(    BIB_ITEM_TABLE);
    sb.append(      ".");
    sb.append(    MD_ITEM_SEQ_COLUMN);
    sb.append(    "=");
    sb.append(    MD_ITEM_TABLE);
    sb.append(      ".");
    sb.append(    MD_ITEM_SEQ_COLUMN);
    sb.append(" ,");
    sb.append(" coalesce(");
    sb.append("  yearFromDate(date),");
    sb.append("  startYearFromAuid(plugin_id,au_key)) as \"year\" ");
    sb.append("from " + MD_ITEM_TABLE + " ");
    sb.append(" order by 1,2,8");  // publisher, title, date

    bibliographicItemsQuery = sb.toString();
  }

  /**
   * Returns a list of BibliographicItems from the metadata database.
   * 
   * @return a list of BibliobraphicItems from the metadata database.
   */
  static public List<? extends BibliographicItem> getBibliographicItems() {
    List<BibliographicItem> items = new ArrayList<BibliographicItem>();
    Connection conn = null;
    DbManager dbManager = null;
    PreparedStatement statement = null;
    ResultSet resultSet = null;

    try {
      dbManager = getDaemon().getDbManager();
      conn = dbManager.getConnection();
      statement = conn.prepareStatement(bibliographicItemsQuery);
      resultSet = dbManager.executeQuery(statement);

      while (resultSet.next()) {
	BibliographicItem item = new BibliographicDatabaseItem(resultSet);
	items.add(item);
      }
    } catch (IllegalArgumentException ex) {
      log.warning(ex.getMessage());
    } catch (SQLException ex) {
      log.warning(ex.getMessage());
    } finally {
      DbManager.safeCloseResultSet(resultSet);
      DbManager.safeCloseStatement(statement);
      DbManager.safeRollbackAndClose(conn);
    }
    return items;
  }
}
