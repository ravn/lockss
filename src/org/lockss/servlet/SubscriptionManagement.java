/*
 * $Id: SubscriptionManagement.java,v 1.1 2013-05-22 23:57:08 fergaloy-sf Exp $
 */

/*

 Copyright (c) 2013 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import org.apache.commons.collections.FactoryUtils;
import org.apache.commons.collections.map.MultiValueMap;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.plugin.PluginManager;
import org.lockss.subscription.BibliographicPeriod;
import org.lockss.subscription.SerialPublication;
import org.lockss.subscription.Subscription;
import org.lockss.subscription.SubscriptionManager;
import org.lockss.subscription.SubscriptionOperationStatus;
import org.lockss.util.ListUtil;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;
import org.mortbay.html.Block;
import org.mortbay.html.Form;
import org.mortbay.html.Image;
import org.mortbay.html.Input;
import org.mortbay.html.Link;
import org.mortbay.html.Page;
import org.mortbay.html.Table;

/**
 * Subscription management servlet.
 * 
 * @author Fernando Garcia-Loygorri
 */
@SuppressWarnings("serial")
public class SubscriptionManagement extends LockssServlet {
  private static final Logger log = Logger
      .getLogger(SubscriptionManagement.class);

  // Prefix for the subscription configuration entries.
  private static final String PREFIX = Configuration.PREFIX + "subscription.";

  /**
   * The maximum number of entries that force a single-tab interface.
   */
  public static final String PARAM_MAX_SINGLE_TAB_COUNT = PREFIX
      + "maxSingleTabCount";
  public static final int DEFAULT_MAX_SINGLE_TAB_COUNT = 20;

  public static final String AUTO_ADD_SUBSCRIPTIONS_ACTION = "autoAdd";
  public static final String SHOW_ADD_PAGE_ACTION = "showAdd";
  public static final String SHOW_UPDATE_PAGE_ACTION = "showUpdate";

  private static final String ADD_SUBSCRIPTIONS_ACTION = "Add";
  private static final String UPDATE_SUBSCRIPTIONS_ACTION = "Update";

  private static final String SUBSCRIBED_RANGES_PARAM_NAME = "subscribedRanges";
  private static final String UNSUBSCRIBED_RANGES_PARAM_NAME =
      "unsubscribedRanges";
  private static final String SUBSCRIBE_ALL_PARAM_NAME = "subscribeAll";
  private static final String UNSUBSCRIBE_ALL_PARAM_NAME = "unsubscribeAll";

  private static final String SUBSCRIPTIONS_SESSION_KEY = "subscriptions";
  private static final String UNDECIDED_TITLES_SESSION_KEY = "undecidedTitles";
  private static final String BACK_LINK_TEXT_PREFIX = "Back to ";

  private static final int LETTERS_IN_ALPHABET = 26;
  private static final int DEFAULT_LETTERS_PER_TAB = 3;

  private static final String rangesFootnote =
      "Enter ranges separated by commas."
	  + "<br>Each range is of the form "
	  + "Year(Volume)(Issue)-Year(Volume)(Issue) where any element may be "
	  + "omitted; any empty rightmost parenthesis pair may be omitted too."
	  + "<br>If both range ends are identical, the dash and everything "
	  + "following it may be omitted."
	  + "<br>A range starting with a dash extends to infinity in the past."
	  + "<br>A range ending with a dash extends to infinity in the future."
	  + "<br>"
	  + "<br>Examples of valid ranges:"
	  + "<br>1954-2000(10)"
	  + "<br>1988(12)(28)"
	  + "<br>()(5)-"
	  + "<br>-2000(10)";

  // The column headers of the tab content.
  private List<String> tabColumnHeaderNames = (List<String>)ListUtil
      .list("Subscribe All",
	    "Subscribed Ranges&nbsp;" + addFootnote(rangesFootnote),
	    "Unsubscribed Ranges&nbsp;" + addFootnote(rangesFootnote),
	    "Unsubscribe All");

  private PluginManager pluginManager;
  private SubscriptionManager subManager;
  private int maxSingleTabCount;

  /**
   * Initializes the configuration when loaded.
   * 
   * @param config
   *          A ServletConfig with the servlet configuration.
   * @throws ServletException
   */
  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    pluginManager = getLockssDaemon().getPluginManager();
    subManager = getLockssDaemon().getSubscriptionManager();
    configure();
  }

  /**
   * Handles configuration options.
   */
  private void configure() {
    final String DEBUG_HEADER = "config(): ";
    Configuration config = ConfigManager.getCurrentConfig();

    // Get the number of publications beyond which a multi-tabbed interface is
    // to be used.
    maxSingleTabCount =
	config.getInt(PARAM_MAX_SINGLE_TAB_COUNT, DEFAULT_MAX_SINGLE_TAB_COUNT);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "maxSingleTabCount = " + maxSingleTabCount);
  }

  /**
   * Processes the user request.
   * 
   * @throws IOException
   */
  public void lockssHandleRequest() throws IOException {
    final String DEBUG_HEADER = "lockssHandleRequest(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    // If the AUs are not started, display a warning message.
    if (!pluginManager.areAusStarted()) {
      displayNotStarted();
      return;
    }

    // The operation to be performed.
    String action = req.getParameter(ACTION_TAG);

    try {
      if (SHOW_ADD_PAGE_ACTION.equals(action)) {
	displayAddPage();
      } else if (SHOW_UPDATE_PAGE_ACTION.equals(action)) {
	displayUpdatePage();
      } else if (ADD_SUBSCRIPTIONS_ACTION.equals(action)) {
	errMsg = addSubscriptions();
	displayResults();
      } else if (UPDATE_SUBSCRIPTIONS_ACTION.equals(action)) {
	errMsg = updateSubscriptions();
	displayResults();
      } else if (AUTO_ADD_SUBSCRIPTIONS_ACTION.equals(action)) {
	errMsg = subscribePublicationsWithConfiguredAus();
	displayResults();
      } else {
	displayAddPage();
      }
    } catch (SQLException sqle) {
      throw new RuntimeException(sqle);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Displays the page used to add new subscription decisions.
   * 
   * @throws IOException
   *           , SQLException
   */
  private void displayAddPage() throws IOException, SQLException {
    final String DEBUG_HEADER = "displayAddPage(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    // Start the page.
    Page page = newPage();
    addJavaScript(page);
    addCssLocations(page);
    addJQueryLocations(page);

    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "pluginMgr.areAusStarted() = "
	+ pluginManager.areAusStarted());
    if (!pluginManager.areAusStarted()) {
      page.add(ServletUtil.notStartedWarning());
    }

    ServletUtil.layoutExplanationBlock(page,
	"Add new serial title subscription options");
    layoutErrorBlock(page);

    // Create the form.
    Form form = ServletUtil.newForm(srvURL(myServletDescr()));

    // Get the publications for which no subscription decision has been made.
    List<SerialPublication> publications =
	subManager.getUndecidedPublications();

    // Determine whether to use a single-tab or multiple-tab interface.
    int lettersPerTab = DEFAULT_LETTERS_PER_TAB;

    if (publications.size() <= maxSingleTabCount) {
      lettersPerTab = LETTERS_IN_ALPHABET;
    }

    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "lettersPerTab = " + lettersPerTab);

    // Create the tabs container, an HTML division element, as required by
    // jQuery tabs.
    Block tabsDiv = new Block(Block.Div, "id=\"tabs\"");

    // Add it to the form.
    form.add(tabsDiv);

    // Create the tabs on the page, add them to the tabs container and obtain a
    // map of the tab tables keyed by the letters they cover.
    Map<String, Table> divTableMap =
	ServletUtil.createTabsWithTable(LETTERS_IN_ALPHABET, lettersPerTab,
	    tabColumnHeaderNames, tabsDiv);

    // Populate the tabs content with the publications for which no subscription
    // decision has been made.
    populateTabsPublications(publications, divTableMap);

    // Save the undecided publications in the session to compare after the form
    // is submitted.
    HttpSession session = getSession();
    session.setAttribute(UNDECIDED_TITLES_SESSION_KEY, publications);

    // Add the submit button.
    ServletUtil.layoutSubmitButton(this, form, ACTION_TAG,
	ADD_SUBSCRIPTIONS_ACTION, ADD_SUBSCRIPTIONS_ACTION);

    // Add the form to the page.
    page.add(form);

    // Add the link to go back to the previous page.
    ServletUtil.layoutBackLink(page,
	srvLink(AdminServletManager.SERVLET_BATCH_AU_CONFIG,
	    	BACK_LINK_TEXT_PREFIX
	    	+ getHeading(AdminServletManager.SERVLET_BATCH_AU_CONFIG)));

    // Finish up.
    endPage(page);

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Populates the tabs with the publication data to be displayed.
   * 
   * @param publications
   *          A List<SerialPublication> with the publications to be used to
   *          populate the tabs.
   * @param divTableMap
   *          A Map<String, Table> with the tabs tables mapped by the first
   *          letter of the tab letter group.
   */
  private void populateTabsPublications(List<SerialPublication> publications,
      Map<String, Table> divTableMap) {
    final String DEBUG_HEADER = "populateTabsPublications(): ";
    if (log.isDebug2()) {
      if (publications != null) {
	log.debug2(DEBUG_HEADER + "publications.size() = "
	    + publications.size());
      } else {
	log.debug2(DEBUG_HEADER + "publications is null");
      }
    }

    Map.Entry<String, TreeSet<SerialPublication>> pubEntry;
    String publisherName;
    TreeSet<SerialPublication> pubSet;

    // Get a map of the publications keyed by their publisher.
    MultiValueMap publicationMap = orderPublicationsByPublisher(publications);
    if (log.isDebug3()) {
      if (publicationMap != null) {
	log.debug3(DEBUG_HEADER + "publicationMap.size() = "
	    + publicationMap.size());
      } else {
	log.debug3(DEBUG_HEADER + "publicationMap is null");
      }
    }

    // Loop through all the publications mapped by their publisher.
    Iterator<Map.Entry<String, TreeSet<SerialPublication>>> pubIterator =
	(Iterator<Map.Entry<String, TreeSet<SerialPublication>>>)publicationMap
	.entrySet().iterator();

    while (pubIterator.hasNext()) {
      pubEntry = pubIterator.next();

      // Get the publisher name.
      publisherName = pubEntry.getKey();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "publisherName = " + publisherName);

      // Get the set of publications for this publisher.
      pubSet = pubEntry.getValue();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "pubSet.size() = " + pubSet.size());

      // Populate a tab with the publications for this publisher.
      populateTabPublisherPublications(publisherName, pubSet, divTableMap);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Maps publications by publisher.
   * 
   * @param publications
   *          A List<SerialPublication> with the publications.
   * @return a MultiValueMap with the map in the desired format.
   */
  private MultiValueMap orderPublicationsByPublisher(
      List<SerialPublication> publications) {
    final String DEBUG_HEADER = "orderPublicationsByPublisher(): ";
    if (log.isDebug2()) {
      if (publications != null) {
	log.debug2(DEBUG_HEADER + "publications.size() = "
	    + publications.size());
      } else {
	log.debug2(DEBUG_HEADER + "publications is null");
      }
    }

    SerialPublication publication;
    String publisherName;
    String publicationName;
    String uniqueName = null;

    // Initialize the resulting map.
    MultiValueMap publicationMap = MultiValueMap
	.decorate(new TreeMap<String, TreeSet<SerialPublication>>(),
	    FactoryUtils.prototypeFactory(new TreeSet<SerialPublication>(
		subManager.getPublicationComparator())));

    int publicationCount = 0;

    if (publications != null) {
      publicationCount = publications.size();
    }

    // Loop through all the publications.
    for (int i = 0; i < publicationCount; i++) {
      publication = publications.get(i);

      // The publisher name.
      publisherName = publication.getPublisherName();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "publisherName = " + publisherName);

      // Do nothing with this publication if it has no publisher.
      if (StringUtil.isNullString(publisherName)) {
	log.error("Publication '" + publication.getPublicationName()
	    + "' is unsubscribable because it has no publisher.");
	continue;
      }

      // The publication name.
      publicationName = publication.getPublicationName();
      if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "publicationName = " + publicationName);

      // Check whether the publication name displayed must be qualified with the
      // platform name to make it unique.
      if ((i > 0
	  && publicationName.equals(publications.get(i - 1)
	      .getPublicationName()) && publisherName.equals(publications.get(
	  i - 1).getPublisherName()))
	  || (i < publicationCount - 1
	      && publicationName.equals(publications.get(i + 1)
	          .getPublicationName()) && publisherName.equals(publications
	      .get(i + 1).getPublisherName()))) {
	// Yes: Create the unique name.
	if (!StringUtil.isNullString(publication.getPlatformName())) {
	  uniqueName += " [" + publication.getPlatformName() + "]";
	}

	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "uniqueName = " + uniqueName);
      } else {
	// No: Just use the publication name as the unique name.
	uniqueName = publicationName;
      }

      // Remember the publication unique name.
      publication.setUniqueName(uniqueName);

      // Add the publication to this publisher set of publications.
      publicationMap.put(publisherName, publication);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
    return publicationMap;
  }

  /**
   * Populates a tab with the publications for a publisher.
   * 
   * @param publisherName
   *          A String with the name of the publisher.
   * @param pubSet
   *          A TreeSet<SerialPublication> with the publisher publications.
   * @param divTableMap
   *          A Map<String, Table> with the tabs tables mapped by the first
   *          letter of the tab letter group.
   */
  private void populateTabPublisherPublications(String publisherName,
      TreeSet<SerialPublication> pubSet, Map<String, Table> divTableMap) {
    final String DEBUG_HEADER = "populateTabPublisherPublications(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "publisherName = " + publisherName);

    // The publisher name first letter.
    String firstLetterPub = publisherName.substring(0, 1).toUpperCase();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "firstLetterPub = " + firstLetterPub);

    // Get the tab table that corresponds to this publisher.
    Table divTable = divTableMap.get(firstLetterPub);

    // Check whether no table corresponds naturally to this publisher.
    if (divTable == null) {
      // Yes: Use the first table.
      divTable = divTableMap.get("A");

      // Check whether no table is found.
      if (divTable == null) {
	// Yes: Report the problem and skip this publisher.
	log.error("Publisher '" + publisherName
	    + "' belongs to an unknown tab: Skipped.");

	return;
      }
    }

    // Sanitize the publisher name so that it can be used as an HTML division
    // identifier.
    String cleanNameString = StringUtil.sanitizeToIdentifier(publisherName);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "cleanNameString = " + cleanNameString);

    // Create in the table the title row for the publisher.
    createPublisherRow(publisherName, cleanNameString, divTable);

    // Check whether there are any publications to show.
    if (pubSet != null) {
      // Yes: Add them.
      int rowIndex = 0;

      // Loop through all the publications.
      for (SerialPublication publication : pubSet) {
        // Create in the table a row for the publication.
        createPublicationRow(publication, cleanNameString, rowIndex, divTable);
        rowIndex++;
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Creates a row for a publisher.
   * 
   * @param publisherName
   *          A String with the name of the publisher.
   * @param publisherId
   *          A String with the identifier of the publisher.
   * @param divTable
   *          A Table with the table where to create the row.
   */
  private void createPublisherRow(String publisherName, String publisherId,
      Table divTable) {
    final String DEBUG_HEADER = "createPublisherRow(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "publisherName = " + publisherName);
      log.debug2(DEBUG_HEADER + "publisherId = " + publisherId);
    }

    divTable.newRow();

    Link headingLink = new Link("javascript:showRows('" + publisherId
	+ "_class', '" + publisherId + "_id', '" + publisherId
	+ "_Publisherimage')");
    headingLink.attribute("id=\"" + publisherId + "_id\"");

    Image headingLinkImage = new Image("images/expand.png");
    headingLinkImage.attribute("id =\"" + publisherId + "_Publisherimage\"");
    headingLinkImage.attribute("class=\"title-icon\"");
    headingLinkImage.attribute("alt", "Expand Publisher");
    headingLinkImage.attribute("title", "Expand Publisher");

    headingLink.add(headingLinkImage);
    headingLink.add(publisherName);

    Block boldHeadingLink = new Block(Block.Bold);
    boldHeadingLink.add(headingLink);
    divTable.addHeading(boldHeadingLink, "class=\"pub-title\"");

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Creates a row for a publication.
   * 
   * @param publication
   *          A SerialPublication with the publication.
   * @param publisherId
   *          A String with the identifier of the publisher.
   * @param rowIndex
   *          An int with row number.
   * @param divTable
   *          A Table with the table where to create the row.
   */
  private void createPublicationRow(SerialPublication publication,
      String publisherId, int rowIndex, Table divTable) {
    final String DEBUG_HEADER = "createPublicationRow(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "publication = " + publication);
      log.debug2(DEBUG_HEADER + "publisherId = " + publisherId);
      log.debug2(DEBUG_HEADER + "rowIndex = " + rowIndex);
    }

    divTable.newRow();

    Block newRow = divTable.row();
    newRow.attribute("class", publisherId + "_class hide-row "
	+ ServletUtil.rowCss(rowIndex, 3));

    divTable.addCell(publication.getUniqueName());

    Integer publicationNumber = publication.getPublicationNumber();
    String subscribeAllId = SUBSCRIBE_ALL_PARAM_NAME + publicationNumber;
    String subscribedRangesId =
	SUBSCRIBED_RANGES_PARAM_NAME + publicationNumber;
    String unsubscribedRangesId =
	UNSUBSCRIBED_RANGES_PARAM_NAME + publicationNumber;
    String unsubscribeAllId = UNSUBSCRIBE_ALL_PARAM_NAME + publicationNumber;

    // The subscribe all checkbox.
    Input subscribeAllCheckBox =
	new Input(Input.Checkbox, subscribeAllId, "true");
    subscribeAllCheckBox.attribute("id", subscribeAllId);

    // Disable the subscribe range input box and the unsubscribe all check box,
    // but allow the unsubscribe range input box for exceptions.
    subscribeAllCheckBox.attribute("onchange", "selectDisable2(this, '"
	+ unsubscribeAllId + "', '" + subscribedRangesId + "')");

    divTable.addCell(subscribeAllCheckBox);

    // The subscribed ranges.
    Input subscribedInputBox = new Input(Input.Text, subscribedRangesId, "");
    subscribedInputBox.setSize(25);
    subscribedInputBox.attribute("id", subscribedRangesId);

    divTable.addCell(subscribedInputBox);

    // The unsubscribed ranges.
    Input unsubscribedInputBox =
	new Input(Input.Text, unsubscribedRangesId, "");
    unsubscribedInputBox.setSize(25);
    unsubscribedInputBox.attribute("id", unsubscribedRangesId);

    divTable.addCell(unsubscribedInputBox);

    // The unsubscribe all checkbox.
    Input unsubscribeAllCheckBox =
	new Input(Input.Checkbox, unsubscribeAllId, "true");
    unsubscribeAllCheckBox.attribute("id", unsubscribeAllId);

    // Disable all the other input elements for this row.
    unsubscribeAllCheckBox.attribute("onchange", "selectDisable3(this, '"
	+ subscribeAllId + "', '" + subscribedRangesId + "', '"
	+ unsubscribedRangesId + "')");

    divTable.addCell(unsubscribeAllCheckBox);

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Creates any subscriptions specified by the user in the form.
   * 
   * @return a String with a message with the result of the operation.
   */
  private String addSubscriptions() {
    final String DEBUG_HEADER = "addSubscriptions(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    if (!hasSession()) {
      return "Please enable cookies";
    } else {
      session = getSession();
    }

    // Get the undecided publications presented in the form just submitted.
    List<SerialPublication> publications = (List<SerialPublication>) session
	.getAttribute(UNDECIDED_TITLES_SESSION_KEY);
    if (log.isDebug3()) {
      if (publications != null) {
	log.debug3(DEBUG_HEADER + "publications.size() = "
	    + publications.size());
      } else {
	log.debug3(DEBUG_HEADER + "publications is null");
      }
    }

    // Get the map of parameters received from the submitted form.
    Map<String,String> parameterMap = getParamsAsMap();

    String message = null;
    Subscription subscription;
    Collection<Subscription> subscriptions = new ArrayList<Subscription>();
    SubscriptionOperationStatus status = new SubscriptionOperationStatus();

    // Loop through all the publications presented in the form.
    for (SerialPublication publication : publications) {
      subscription = buildPublicationSubscriptionIfNeeded(publication,
	  parameterMap, status);

      if (subscription != null) {
	subscriptions.add(subscription);
      }
    }

    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "subscriptions.size() = "
	+ subscriptions.size());

    if (subscriptions.size() > 0) {
      subManager.addSubscriptions(subscriptions, status);
    }

    session.removeAttribute(UNDECIDED_TITLES_SESSION_KEY);

    message = "Success: Subscriptions = "
	+ status.getSuccessSubscriptionAddCount()
	+ ", AUs = " + status.getSuccessAuAddCount()
	+ "<br />Failure: Subscriptions = "
	+ status.getFailureSubscriptionAddCount()
	+ ", AUs = " + status.getFailureAuAddCount();

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "message = " + message);
    return message;
  }

  /**
   * Builds a subscription for a publication, if needed.
   * 
   * @param publication
   *          A SerialPublication with the publication.
   * @param parameterMap
   *          A Map<String, String> with the submitted form parameter names and
   *          their values.
   * @param status
   *          A SubscriptionOperationStatus where to record any failures.
   * @return a Subscription with the subscription to be created, if needed, or
   *         <code>null</code> otherwise.
   */
  private Subscription buildPublicationSubscriptionIfNeeded(
      SerialPublication publication, Map<String, String> parameterMap,
      SubscriptionOperationStatus status) {
    final String DEBUG_HEADER = "buildPublicationSubscriptionIfNeeded(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "publication = " + publication);

    String subscribedRangesText = "";
    String unsubscribedRangesText = "";

    Subscription subscription = null;

    Integer publicationNumber = publication.getPublicationNumber();
    String subscribeAllParamName = SUBSCRIBE_ALL_PARAM_NAME + publicationNumber;
    String unsubscribeAllParamName =
	UNSUBSCRIBE_ALL_PARAM_NAME + publicationNumber;

    if (parameterMap.containsKey(subscribeAllParamName)) {
      // Handle a full subscription request.
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + subscribeAllParamName
	  + " => " + parameterMap.get(subscribeAllParamName));

      subscription = new Subscription();
      subscription.setPublication(publication);
      subscription.setSubscribedRanges(Collections
	  .singletonList(BibliographicPeriod.ALL_TIME_PERIOD));

      String unsubscribedRangesParamName =
	  UNSUBSCRIBED_RANGES_PARAM_NAME + publicationNumber;

      if (parameterMap.containsKey(unsubscribedRangesParamName)) {
	// Handle exceptions to a full subscription request.
	unsubscribedRangesText = StringUtil
	    .replaceString(parameterMap.get(unsubscribedRangesParamName), " ",
		"");
	if (log.isDebug3()) log.debug3(DEBUG_HEADER
	    + "unsubscribedRangesText = " + unsubscribedRangesText);

	if (!StringUtil.isNullString(unsubscribedRangesText)) {
	  Collection<BibliographicPeriod> unsubscribedRanges =
	      BibliographicPeriod.createCollection(unsubscribedRangesText);

	  if (subManager.areAllRangesValid(unsubscribedRanges)) {
	    subscription.setUnsubscribedRanges(unsubscribedRanges);
	  } else {
	    status.addFailureSubscriptionAdd(1);
	    if (log.isDebug2())
	      log.debug2(DEBUG_HEADER + "subscription = null");
	    return null;
	  }
	}
      }
    } else if (parameterMap.containsKey(unsubscribeAllParamName)) {
      // Handle a full unsubscription request.
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + unsubscribeAllParamName
	  + " = " + parameterMap.get(unsubscribeAllParamName));
      subscription = new Subscription();
      subscription.setPublication(publication);
      subscription.setUnsubscribedRanges(Collections
	  .singletonList(BibliographicPeriod.ALL_TIME_PERIOD));
    } else {
      // Handle a partial subscription request.
      String unsubscribedRangesParamName =
	  UNSUBSCRIBED_RANGES_PARAM_NAME + publicationNumber;

      if (parameterMap.containsKey(unsubscribedRangesParamName)) {
	unsubscribedRangesText = StringUtil
	    .replaceString(parameterMap.get(unsubscribedRangesParamName), " ",
		"");
	if (log.isDebug3()) log.debug3(DEBUG_HEADER
	    + "unsubscribedRangesText = " + unsubscribedRangesText);

	if (!StringUtil.isNullString(unsubscribedRangesText)) {
	  Collection<BibliographicPeriod> unsubscribedRanges =
	      BibliographicPeriod.createCollection(unsubscribedRangesText);

	  if (subManager.areAllRangesValid(unsubscribedRanges)) {
	    subscription = new Subscription();
	    subscription.setPublication(publication);
	    subscription.setUnsubscribedRanges(unsubscribedRanges);
	  } else {
	    status.addFailureSubscriptionAdd(1);
	    if (log.isDebug2())
	      log.debug2(DEBUG_HEADER + "subscription = null");
	    return null;
	  }
	}
      }

      String subscribedRangesParamName =
	  SUBSCRIBED_RANGES_PARAM_NAME + publicationNumber;

      if (parameterMap.containsKey(subscribedRangesParamName)) {
	subscribedRangesText = StringUtil
	    .replaceString(parameterMap.get(subscribedRangesParamName), " ",
		"");
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "subscribedRangesText = "
	    + subscribedRangesText);

	if (!StringUtil.isNullString(subscribedRangesText)) {
	  Collection<BibliographicPeriod> subscribedRanges =
	      BibliographicPeriod.createCollection(subscribedRangesText);

	  if (subManager.areAllRangesValid(subscribedRanges)) {
	    if (subscription == null) {
	      subscription = new Subscription();
	      subscription.setPublication(publication);
	    }

	    subscription.setSubscribedRanges(subscribedRanges);
	  } else {
	    status.addFailureSubscriptionAdd(1);
	    if (log.isDebug2())
	      log.debug2(DEBUG_HEADER + "subscription = null");
	    return null;
	  }
	}
      }
    }

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscription = " + subscription);
    return subscription;
  }

  /**
   * Displays a page with the results of the previous operation.
   * 
   * @throws IOException
   *           , SQLException
   */
  private void displayResults() throws IOException, SQLException {
    final String DEBUG_HEADER = "displayMenu(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    // Start the page.
    Page page = newPage();
    addJavaScript(page);

    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "pluginMgr.areAusStarted() = "
	+ pluginManager.areAusStarted());
    if (!pluginManager.areAusStarted()) {
      page.add(ServletUtil.notStartedWarning());
    }

    ServletUtil.layoutExplanationBlock(page,
	"Results");
    layoutErrorBlock(page);

    /*// The link to go back to the previous page.
    ServletUtil.layoutBackLink(page,
	srvLink(AdminServletManager.SERVLET_BATCH_AU_CONFIG,
	    	BACK_LINK_TEXT_PREFIX
	    	+ getHeading(AdminServletManager.SERVLET_BATCH_AU_CONFIG)));*/

    // The link to go back to the journal configuration page.
    ServletUtil.layoutBackLink(page,
	srvLink(AdminServletManager.SERVLET_BATCH_AU_CONFIG,
	    	BACK_LINK_TEXT_PREFIX
	    	+ getHeading(AdminServletManager.SERVLET_BATCH_AU_CONFIG)));

    // Finish up.
    endPage(page);

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Displays the page that allows the user to change subscription decisions.
   * 
   * @throws IOException
   *           , SQLException
   */
  private void displayUpdatePage() throws IOException, SQLException {
    final String DEBUG_HEADER = "displayUpdatePage(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    // Start the page.
    Page page = newPage();
    addJavaScript(page);
    addCssLocations(page);
    addJQueryLocations(page);

    ServletUtil.layoutExplanationBlock(page,
	"Update existing subscription options for serial titles");

    // Get the existing subscriptions with ranges.
    List<Subscription> subscriptions =
	subManager.findAllSubscriptionsAndRanges();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "subscriptions.size() = "
	+ subscriptions.size());

    if (subscriptions.size() > 0) {
      layoutErrorBlock(page);

      // Create the form.
      Form form = ServletUtil.newForm(srvURL(myServletDescr()));

      // Determine whether to use a single-tab or multiple-tab interface.
      int lettersPerTab = DEFAULT_LETTERS_PER_TAB;

      if (subscriptions.size() <= maxSingleTabCount) {
        lettersPerTab = LETTERS_IN_ALPHABET;
      }

      if (log.isDebug3())
        log.debug3(DEBUG_HEADER + "lettersPerTab = " + lettersPerTab);

      // Create the tabs container, an HTML division element, as required by
      // jQuery tabs.
      Block tabsDiv = new Block(Block.Div, "id=\"tabs\"");

      // Add it to the form.
      form.add(tabsDiv);

      // Create the tabs on the page, add them to the tabs container and obtain
      // a map of the tab tables keyed by the letters they cover.
      Map<String, Table> divTableMap =
	  ServletUtil.createTabsWithTable(LETTERS_IN_ALPHABET, lettersPerTab,
	      tabColumnHeaderNames, tabsDiv);

      // Populate the tabs content with the publications for which subscription
      // decisions have already been made.
      populateTabsSubscriptions(subscriptions, divTableMap);

      // Save the existing subscriptions in the session to compare after the
      // form is submitted.
      HttpSession session = getSession();
      session.setAttribute(SUBSCRIPTIONS_SESSION_KEY, subscriptions);

      // The submit button.
      ServletUtil.layoutSubmitButton(this, form, ACTION_TAG,
	  UPDATE_SUBSCRIPTIONS_ACTION, UPDATE_SUBSCRIPTIONS_ACTION);

      // Add the form to the page.
      page.add(form);
    } else {
      errMsg = "There are no subscriptions to update";
      layoutErrorBlock(page);
    }

    // The link to go back to the previous page.
    ServletUtil.layoutBackLink(page,
	srvLink(AdminServletManager.SERVLET_BATCH_AU_CONFIG,
	    	BACK_LINK_TEXT_PREFIX
	    	+ getHeading(AdminServletManager.SERVLET_BATCH_AU_CONFIG)));

    // Finish up.
    endPage(page);

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Populates the tabs with the subscription data to be displayed.
   * 
   * @param subscriptions
   *          A List<Subscription> with the subscriptions to be used to populate
   *          the tabs.
   * @param divTableMap
   *          A Map<String, Table> with the tabs tables mapped by the first
   *          letter of the tab letter group.
   */
  private void populateTabsSubscriptions(List<Subscription> subscriptions,
      Map<String, Table> divTableMap) {
    final String DEBUG_HEADER = "populateTabsSubscriptions(): ";
    if (log.isDebug2()) {
      if (subscriptions != null) {
	log.debug2(DEBUG_HEADER + "subscriptions.size() = "
	    + subscriptions.size());
      } else {
	log.debug2(DEBUG_HEADER + "subscriptions is null");
      }
    }

    Map.Entry<String, TreeSet<Subscription>> subEntry;
    String publisherName;
    TreeSet<Subscription> subSet;

    // Get a map of the subscriptions keyed by their publisher.
    MultiValueMap subscriptionMap =
	orderSubscriptionsByPublisher(subscriptions);
    if (log.isDebug3()) {
      if (subscriptionMap != null) {
	log.debug3(DEBUG_HEADER + "subscriptionMap.size() = "
	    + subscriptionMap.size());
      } else {
	log.debug3(DEBUG_HEADER + "subscriptionMap is null");
      }
    }

    // Loop through all the subscriptions mapped by their publisher.
    Iterator<Map.Entry<String, TreeSet<Subscription>>> subIterator =
	(Iterator<Map.Entry<String, TreeSet<Subscription>>>)subscriptionMap
	.entrySet().iterator();

    while (subIterator.hasNext()) {
      subEntry = subIterator.next();

      // Get the publisher name.
      publisherName = subEntry.getKey();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "publisherName = " + publisherName);

      // Get the set of subscriptions for this publisher.
      subSet = subEntry.getValue();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "subSet.size() = " + subSet.size());

      // Populate a tab with the subscriptions for this publisher.
      populateTabPublisherSubscriptions(publisherName, subSet, divTableMap);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Maps subscriptions by publisher.
   * 
   * @param subscriptions
   *          A Collection<Subscription> with the subscriptions.
   * @return a MultiValueMap with the map in the desired format.
   */
  private MultiValueMap orderSubscriptionsByPublisher(
      List<Subscription> subscriptions) {
    final String DEBUG_HEADER = "orderSubscriptionsByPublisher(): ";
    if (log.isDebug2()) {
      if (subscriptions != null) {
	log.debug2(DEBUG_HEADER + "subscriptions.size() = "
	    + subscriptions.size());
      } else {
	log.debug2(DEBUG_HEADER + "subscriptions is null");
      }
    }

    Subscription subscription;
    SerialPublication publication;
    String publisherName;
    String publicationName;
    String uniqueName = null;

    // Initialize the resulting map.
    MultiValueMap subscriptionMap = MultiValueMap
	.decorate(new TreeMap<String, TreeSet<Subscription>>(),
	    FactoryUtils.prototypeFactory(new TreeSet<Subscription>(
		subManager.getSubscriptionByPublicationComparator())));

    //TreeSet<Subscription> subSet;
    int subscriptionCount = 0;

    if (subscriptions != null) {
      subscriptionCount = subscriptions.size();
    }

    // Loop through all the subscriptions.
    for (int i = 0; i < subscriptionCount; i++) {
      subscription = subscriptions.get(i);

      // The publication.
      publication = subscription.getPublication();

      // The publisher name.
      publisherName = publication.getPublisherName();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "publisherName = " + publisherName);

      // Do nothing with this subscription if it has no publisher.
      if (StringUtil.isNullString(publisherName)) {
	log.error("Publication '" + publication.getPublicationName()
	    + "' is unsubscribable because it has no publisher.");
	continue;
      }

      // The publication name and platform.
      publicationName = publication.getPublicationName();
      if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "publicationName = " + publicationName);

      // Check whether the publication name displayed must be qualified with the
      // platform name to make it unique.
      if ((i > 0
	   && publicationName.equals(subscriptions.get(i - 1).getPublication()
	       .getPublicationName())
	   && publisherName.equals(subscriptions.get(i - 1).getPublication()
	       .getPublisherName()))
	  ||
	  (i < subscriptionCount - 1
	   && publicationName.equals(subscriptions.get(i + 1).getPublication()
	       .getPublicationName())
	   && publisherName.equals(subscriptions.get(i + 1).getPublication()
	       .getPublisherName()))) {
	// Yes: Create the unique name.
	if (!StringUtil.isNullString(publication.getPlatformName())) {
	  uniqueName += " [" + publication.getPlatformName() + "]";
	}

	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "uniqueName = " + uniqueName);
      } else {
	// No: Just use the publication name as the unique name.
	uniqueName = publicationName;
      }

      // Remember the publication unique name.
      publication.setUniqueName(uniqueName);

      // Add the subscription to this publisher set of subscriptions.
      subscriptionMap.put(publisherName, subscription);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
    return subscriptionMap;
  }

  /**
   * Populates a tab with the subscriptions for a publisher.
   * 
   * @param publisherName
   *          A String with the name of the publisher.
   * @param subSet
   *          A TreeSet<Subscription> with the publisher subscriptions.
   * @param divTableMap
   *          A Map<String, Table> with the tabs tables mapped by the first
   *          letter of the tab letter group.
   */
  private void populateTabPublisherSubscriptions(String publisherName,
      TreeSet<Subscription> subSet, Map<String, Table> divTableMap) {
    final String DEBUG_HEADER = "populateTabPublisherSubscriptions(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "publisherName = " + publisherName);

    // The publisher name first letter.
    String firstLetterPub = publisherName.substring(0, 1).toUpperCase();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "firstLetterPub = " + firstLetterPub);

    // Get the tab table that corresponds to this publisher.
    Table divTable = divTableMap.get(firstLetterPub);

    // Check whether no table corresponds naturally to this publisher.
    if (divTable == null) {
      // Yes: Use the first table.
      divTable = divTableMap.get("A");

      // Check whether no table is found.
      if (divTable == null) {
	// Yes: Report the problem and skip this publisher.
	log.error("Publisher '" + publisherName
	    + "' belongs to an unknown tab: Skipped.");

	return;
      }
    }

    // Sanitize the publisher name so that it can be used as an HTML division
    // identifier.
    String cleanNameString = StringUtil.sanitizeToIdentifier(publisherName);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "cleanNameString = " + cleanNameString);

    // Create in the table the title row for the publisher.
    createPublisherRow(publisherName, cleanNameString, divTable);

    // Check whether there are any subscriptions to show.
    if (subSet != null) {
      // Yes: Add them.
      int rowIndex = 0;

      // Loop through all the subscriptions.
      for (Subscription subscription : subSet) {
        // Create in the table a row for the subscription.
        createSubscriptionRow(subscription, cleanNameString, rowIndex,
            divTable);
        rowIndex++;
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Creates a row for a subscription.
   * 
   * @param subscription
   *          A Subscription with the subscription.
   * @param publisherId
   *          A String with the identifier of the publisher.
   * @param rowIndex
   *          An int with row number.
   * @param divTable
   *          A Table with the table where to create the row.
   */
  private void createSubscriptionRow(Subscription subscription,
      String publisherId, int rowIndex, Table divTable) {
    final String DEBUG_HEADER = "createSubscriptionRow(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "subscription = " + subscription);
      log.debug2(DEBUG_HEADER + "publisherId = " + publisherId);
      log.debug2(DEBUG_HEADER + "rowIndex = " + rowIndex);
    }

    divTable.newRow();

    Block newRow = divTable.row();
    newRow.attribute("class", publisherId + "_class hide-row "
	+ ServletUtil.rowCss(rowIndex, 3));

    // The subscription publication.
    SerialPublication publication = subscription.getPublication();

    divTable.addCell(publication.getUniqueName());

    Long subscriptionSeq = subscription.getSubscriptionSeq();
    String subscribeAllId = SUBSCRIBE_ALL_PARAM_NAME + subscriptionSeq;
    String subscribedRangesId =
	SUBSCRIBED_RANGES_PARAM_NAME + subscriptionSeq;
    String unsubscribedRangesId =
	UNSUBSCRIBED_RANGES_PARAM_NAME + subscriptionSeq;
    String unsubscribeAllId = UNSUBSCRIBE_ALL_PARAM_NAME + subscriptionSeq;

    // Initialize the input elements.
    boolean subscribeAllChecked = false;
    boolean subscribeAllDisabled = false;

    // The subscribed ranges.
    Collection<BibliographicPeriod> subscribedRanges =
	subscription.getSubscribedRanges();
    boolean subscribedRangesDisabled = false;
    String subscribedRangesText =
	BibliographicPeriod.rangesAsString(subscribedRanges);

    // The unsubscribed ranges.
    Collection<BibliographicPeriod> unsubscribedRanges =
	subscription.getUnsubscribedRanges();
    boolean unsubscribedRangesDisabled = false;
    String unsubscribedRangesText =
	BibliographicPeriod.rangesAsString(unsubscribedRanges);

    boolean unsubscribeAllChecked = false;
    boolean unsubscribeAllDisabled = false;

    if (subscribedRanges != null && subscribedRanges.size() == 1
	&& subscribedRanges.iterator().next().isAllTime()) {
      subscribeAllChecked = true;
      subscribedRangesDisabled = true;
      subscribedRangesText = "";
      unsubscribeAllDisabled = true;
    } else if (unsubscribedRanges != null && unsubscribedRanges.size() == 1
	&& unsubscribedRanges.iterator().next().isAllTime()) {
      subscribeAllDisabled = true;
      subscribedRangesDisabled = true;
      subscribedRangesText = "";
      unsubscribedRangesDisabled = true;
      unsubscribedRangesText = "";
      unsubscribeAllChecked = true;
    }

    // The subscribe all checkbox.
    Input subscribeAllCheckBox =
	new Input(Input.Checkbox, subscribeAllId, "true");
    subscribeAllCheckBox.attribute("id", subscribeAllId);

    if (subscribeAllChecked) {
      subscribeAllCheckBox.attribute("checked", true);
    }

    if (subscribeAllDisabled) {
      subscribeAllCheckBox.attribute("disabled", true);
    }

    // When this is checked, disable the subscribe range input box and the
    // unsubscribe all check box, but allow the unsubscribe range input box for
    // exceptions.
    subscribeAllCheckBox.attribute("onchange", "selectDisable2(this, '"
	+ unsubscribeAllId + "', '" + subscribedRangesId + "')");

    divTable.addCell(subscribeAllCheckBox);

    // The subscribed ranges.
    Input subscribedInputBox = new Input(Input.Text, subscribedRangesId,
	subscribedRangesText);
    subscribedInputBox.setSize(25);
    subscribedInputBox.attribute("id", subscribedRangesId);

    if (subscribedRangesDisabled) {
      subscribedInputBox.attribute("disabled", true);
    }

    divTable.addCell(subscribedInputBox);
    
    Input unsubscribedInputBox = new Input(Input.Text, unsubscribedRangesId,
	unsubscribedRangesText);
    unsubscribedInputBox.setSize(25);
    unsubscribedInputBox.attribute("id", unsubscribedRangesId);

    if (unsubscribedRangesDisabled) {
      unsubscribedInputBox.attribute("disabled", true);
    }

    divTable.addCell(unsubscribedInputBox);

    // The unsubscribe all checkbox.
    Input unsubscribeAllCheckBox =
	new Input(Input.Checkbox, unsubscribeAllId, "true");
    unsubscribeAllCheckBox.attribute("id", unsubscribeAllId);

    if (unsubscribeAllChecked) {
      unsubscribeAllCheckBox.attribute("checked", true);
    }

    if (unsubscribeAllDisabled) {
      unsubscribeAllCheckBox.attribute("disabled", true);
    }

    // When this is checked, disable all the other input elements for this row.
    unsubscribeAllCheckBox.attribute("onchange", "selectDisable3(this, '"
	+ subscribeAllId + "', '" + subscribedRangesId + "', '"
	+ unsubscribedRangesId + "')");

    divTable.addCell(unsubscribeAllCheckBox);

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Updates any subscriptions specified by the user in the form.
   * 
   * @return a String with a message with the result of the operation.
   */
  private String updateSubscriptions() {
    final String DEBUG_HEADER = "updateSubscriptions(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    if (!hasSession()) {
      return "Please enable cookies";
    } else {
      session = getSession();
    }

    // Get the subscriptions presented in the form just submitted.
    List<Subscription> subscriptions =
	(List<Subscription>) session.getAttribute(SUBSCRIPTIONS_SESSION_KEY);

    if (subscriptions == null || subscriptions.size() == 0) {
      return "Session expired";
    }

    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "subscriptions.size() = "
	  + subscriptions.size());

    // Get the map of parameters received from the submitted form.
    Map<String,String> parameterMap = getParamsAsMap();

    String message = null;
    boolean subChanged = false;
    Collection<Subscription> updateSubscriptions =
	new ArrayList<Subscription>();
    SubscriptionOperationStatus status = new SubscriptionOperationStatus();

    // Loop through all the subscriptions presented in the form.
    for (Subscription subscription : subscriptions) {
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "subscription = " + subscription);

      // Get an indication of whether the subscription has been changed.
      subChanged =
	  isSubscriptionUpdateNeeded(subscription, parameterMap, status);
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "subChanged = " + subChanged);

      if (subChanged) {
	updateSubscriptions.add(subscription);
      }
    }

    if (log.isDebug3()) log.debug3(DEBUG_HEADER
	+ "updateSubscriptions.size() = " + updateSubscriptions.size());

    if (updateSubscriptions.size() > 0) {
      subManager.updateSubscriptions(updateSubscriptions, status);
    }

    session.removeAttribute(SUBSCRIPTIONS_SESSION_KEY);

    message = "Success: Subscriptions = "
	+ status.getSuccessSubscriptionUpdateCount()
	+ ", AUs = " + status.getSuccessAuAddCount()
	+ "<br />Failure: Subscriptions = "
	+ status.getFailureSubscriptionUpdateCount()
	+ ", AUs = " + status.getFailureAuAddCount();

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "message = " + message);
    return message;
  }

  /**
   * Provides an indication of whether a subscription needs to be updated.
   * 
   * @param subscription
   *          A Subscription with the subscription.
   * @param parameterMap
   *          A Map<String, String> with the submitted form parameter names and
   *          their values.
   * @param status
   *          A SubscriptionOperationStatus where to record any failures.
   * @return a boolean with <code>true</code> if the subscription needs to be
   *         updated, or <code>false</code> otherwise.
   */
  private boolean isSubscriptionUpdateNeeded(Subscription subscription,
      Map<String, String> parameterMap, SubscriptionOperationStatus status) {
    final String DEBUG_HEADER = "isSubscriptionUpdateNeeded(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscription = " + subscription);

    boolean subsChanged = false;
    boolean unsubsChanged = false;
    String subscribedRangesText = "";
    String unsubscribedRangesText = "";
    Collection<BibliographicPeriod> subscribedRanges =
	subscription.getSubscribedRanges();
    Collection<BibliographicPeriod> unsubscribedRanges =
	subscription.getUnsubscribedRanges();
    String subscribedRangesAsString = "";
    String unsubscribedRangesAsString = "";

    Long subscriptionSeq = subscription.getSubscriptionSeq();
    if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);

    String subscribeAllParamName = SUBSCRIBE_ALL_PARAM_NAME + subscriptionSeq;
    String unsubscribeAllParamName =
	UNSUBSCRIBE_ALL_PARAM_NAME + subscriptionSeq;

    if (parameterMap.containsKey(subscribeAllParamName)) {
      // Handle a full subscription request.
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + subscribeAllParamName
	  + " = " + parameterMap.get(subscribeAllParamName));

      subsChanged = subscribedRanges == null || subscribedRanges.size() != 1
		|| !subscribedRanges.iterator().next().isAllTime();

      subscription.setSubscribedRanges(Collections
	  .singletonList(BibliographicPeriod.ALL_TIME_PERIOD));

      String unsubscribedRangesParamName =
	  UNSUBSCRIBED_RANGES_PARAM_NAME + subscriptionSeq;

      if (parameterMap.containsKey(unsubscribedRangesParamName)) {
	unsubscribedRangesText = StringUtil
	    .replaceString(parameterMap.get(unsubscribedRangesParamName), " ",
		"");
	if (log.isDebug3()) log.debug3(DEBUG_HEADER
	    + "unsubscribedRangesText = " + unsubscribedRangesText);
      }

      unsubscribedRangesAsString =
	  BibliographicPeriod.rangesAsString(unsubscribedRanges);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER
	  + "unsubscribedRangesAsString = " + unsubscribedRangesAsString);

      if (unsubscribedRangesAsString == null) {
	unsubscribedRangesAsString = "";
	if (log.isDebug3()) log.debug3(DEBUG_HEADER
	    + "unsubscribedRangesAsString = " + unsubscribedRangesAsString);
      }

      unsubsChanged =
	  !unsubscribedRangesText.equals(unsubscribedRangesAsString);
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "unsubsChanged = " + unsubsChanged);

      if (unsubsChanged) {
	unsubscribedRanges =
	    BibliographicPeriod.createCollection(unsubscribedRangesText);

	if (unsubscribedRanges != null && unsubscribedRanges.size() > 0) {
	  if (!subManager.areAllRangesValid(unsubscribedRanges)) {
	    status.addFailureSubscriptionAdd(1);
	    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = false");
	    return false;
	  }
	}

	subscription.setUnsubscribedRanges(unsubscribedRanges);
      }
    } else if (parameterMap.containsKey(unsubscribeAllParamName)) {
      // Handle a full unsubscription request.
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + unsubscribeAllParamName
	  + " = " + parameterMap.get(unsubscribeAllParamName));
      
      subsChanged = subscribedRanges != null && subscribedRanges.size() > 0;
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "subsChanged = " + subsChanged);

      unsubsChanged = unsubscribedRanges == null
	  || unsubscribedRanges.size() != 1
	  || !unsubscribedRanges.iterator().next().isAllTime();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "unsubsChanged = " + unsubsChanged);

      if (subsChanged || unsubsChanged) {
	subscription.setSubscribedRanges(Collections
	    .singletonList(new BibliographicPeriod("")));
	subscription.setUnsubscribedRanges(Collections
	    .singletonList(BibliographicPeriod.ALL_TIME_PERIOD));
      }
    } else {
      // Handle a partial subscription request.
      String unsubscribedRangesParamName =
	  UNSUBSCRIBED_RANGES_PARAM_NAME + subscriptionSeq;

      if (parameterMap.containsKey(unsubscribedRangesParamName)) {
	unsubscribedRangesText = StringUtil
	    .replaceString(parameterMap.get(unsubscribedRangesParamName), " ",
		"");
      }

      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "unsubscribedRangesText = "
	  + unsubscribedRangesText);

      unsubscribedRangesAsString =
	  BibliographicPeriod.rangesAsString(unsubscribedRanges);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER
	  + "unsubscribedRangesAsString = " + unsubscribedRangesAsString);

      if (unsubscribedRangesAsString == null) {
	unsubscribedRangesAsString = "";
	if (log.isDebug3()) log.debug3(DEBUG_HEADER
	    + "unsubscribedRangesAsString = " + unsubscribedRangesAsString);
      }

      unsubsChanged =
	  !unsubscribedRangesText.equals(unsubscribedRangesAsString);
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "unsubsChanged = " + unsubsChanged);

      if (unsubsChanged) {
	unsubscribedRanges =
	    BibliographicPeriod.createCollection(unsubscribedRangesText);

	if (unsubscribedRanges != null && unsubscribedRanges.size() > 0) {
	  if (!subManager.areAllRangesValid(unsubscribedRanges)) {
	    status.addFailureSubscriptionAdd(1);
	    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = false");
	    return false;
	  }
	}

	subscription.setUnsubscribedRanges(unsubscribedRanges);
      }

      String subscribedRangesParamName =
	  SUBSCRIBED_RANGES_PARAM_NAME + subscriptionSeq;

      if (parameterMap.containsKey(subscribedRangesParamName)) {
	subscribedRangesText = StringUtil
	    .replaceString(parameterMap.get(subscribedRangesParamName), " ",
		"");
      }

      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "subscribedRangesText = "
	  + subscribedRangesText);

      subscribedRangesAsString =
	  BibliographicPeriod.rangesAsString(subscribedRanges);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER
	  + "subscribedRangesAsString = " + subscribedRangesAsString);

      if (subscribedRangesAsString == null) {
	subscribedRangesAsString = "";
	if (log.isDebug3()) log.debug3(DEBUG_HEADER
	    + "subscribedRangesAsString = " + subscribedRangesAsString);
      }

      subsChanged = !subscribedRangesText.equals(subscribedRangesAsString);
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "subsChanged = " + subsChanged);

      if (subsChanged) {
	subscribedRanges =
	    BibliographicPeriod.createCollection(subscribedRangesText);

	if (subscribedRanges != null && subscribedRanges.size() > 0) {
	  if (!subManager.areAllRangesValid(subscribedRanges)) {
	    status.addFailureSubscriptionAdd(1);
	    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = false");
	    return false;
	  }
	}

	subscription.setSubscribedRanges(subscribedRanges);
      }
    }

    boolean result = subsChanged || unsubsChanged;
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  /**
   * Creates subscriptions to publications matching the currently configured
   * Archival Units, if necessary.
   * 
   * @return a String with a status message.
   */
  private String subscribePublicationsWithConfiguredAus() {
    final String DEBUG_HEADER = "subscribePublicationsWithConfiguredAus(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    // Add the necessary subscription options so that all the configured AUs
    // fall in subscribed ranges and do not fall in any unsubscribed range.
    SubscriptionOperationStatus status = new SubscriptionOperationStatus();
    subManager.subscribeAllConfiguredAus(status);

    // Create the status message.
    String message = "Success: " + status.getSuccessSubscriptionAddCount()
	+ " Subscriptions<br />Failure: "
	+ status.getFailureSubscriptionAddCount() + " Subscriptions";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "message = " + message);
    return message;
  }
}
