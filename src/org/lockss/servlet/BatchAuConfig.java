/*
 * $Id: BatchAuConfig.java,v 1.2 2005-01-05 09:47:40 tlipkis Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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

import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.text.*;
import org.mortbay.html.*;
import org.mortbay.tools.*;
import org.mortbay.servlet.MultiPartRequest;
import org.apache.commons.collections.*;
import org.apache.commons.collections.map.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.remote.*;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;

/** Create and update AU configuration.
 */
public class BatchAuConfig extends LockssServlet {

  static final String PARAM_INCLUDE_PLUGIN_IN_TITLE_SELECT =
    Configuration.PREFIX + "auconfig.includePluginInTitleSelect";
  static final boolean DEFAULT_INCLUDE_PLUGIN_IN_TITLE_SELECT = false;

  static final String FOOT_REPOSITORY =
    "Local disk on which AU will be stored";

  static Logger log = Logger.getLogger("BatchAuConfig");

  static final Verb VERB_ADD = new Verb(1, "add", "added", true);
  static final Verb VERB_DEL = new Verb(2, "remove", "removed", false);
  static final Verb VERB_RESTORE = new Verb(3, "restore", "restored", true);
  static final Verb[] verbs = {VERB_ADD, VERB_DEL, VERB_RESTORE};

  static final String ACTION_ADD_TITLES = "AddTitles";
  static final String ACTION_REMOVE_TITLES = "RemoveTitles";
  static final String ACTION_BACKUP = "Backup";
  static final String ACTION_RESTORE = "Restore";
  static final String ACTION_SELECT_RESTORE_TITLES = "SelectRestoreTitles";
  static final String ACTION_SELECT_SET_TITLES = "SelectSetTitles";
  static final String ACTION_ADD_AUS = "DoAddAus";
  static final String ACTION_REMOVE_AUS = "DoRemoveAus";

  static final String KEY_VERB = "Verb";
  static final String KEY_REPO = "Repository";
  static final String KEY_TITLE_SET = "TitleSetId";
  static final String KEY_AUID = "auid";
  static final String KEY_REPOS = "repos";

  static final String SESSION_KEY_REPO_MAP = "RepoMap";
  static final String SESSION_KEY_AUID_MAP = "AuidMap";
  static final String SESSION_KEY_STATUS_TABLE = "StatusTable";

  private PluginManager pluginMgr;
  private RemoteApi remoteApi;

  // Used to insert messages into the page
  private String errMsg;
  private String statusMsg;

  String action;			// action request by form
  Verb verb;

  // don't hold onto objects after request finished
  protected void resetLocals() {
    verb = null;
    super.resetLocals();
  }

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    pluginMgr = getLockssDaemon().getPluginManager();
    remoteApi = getLockssDaemon().getRemoteApi();
  }

  protected void lockssHandleRequest() throws IOException {
    errMsg = null;
    statusMsg = null;

    // If the AUs are not started, don't display any AU summary or
    // any form inputs.
    if (!pluginMgr.areAusStarted()) {
      displayNotStarted();
      return;
    }
    action = req.getParameter(ACTION_TAG);
    if (StringUtil.isNullString(action)) {
      try {
	getMultiPartRequest(100000);
	if (multiReq != null) {
	  action = multiReq.getString(ACTION_TAG);
	  log.debug(ACTION_TAG + " = " + action);
	}
      } catch (FormDataTooLongException e) {
	errMsg = "Uploaded file too large: " + e.getMessage();
	// leave action null, will call displayAuSummary() below
      }
    }
    String s = getParameter(KEY_VERB);
    if (!StringUtil.isNullString(s)) {
      try {
	verb = findVerb(s);
      } catch (IllegalVerb e) {
	errMsg = "Illegal Verb: " + s;
	action = null;
      }
    }

    if (StringUtil.isNullString(action)) displayMenu();
    else if (action.equals(ACTION_ADD_TITLES)) chooseSets(VERB_ADD);
    else if (action.equals(ACTION_REMOVE_TITLES)) chooseSets(VERB_DEL);
    else if (action.equals(ACTION_BACKUP)) doSaveAll();
    else if (action.equals(ACTION_RESTORE)) displayRestore();
    else if (action.equals(ACTION_SELECT_SET_TITLES)) selectSetTitles();
    else if (action.equals(ACTION_SELECT_RESTORE_TITLES)) selectRestoreTitles();
    else if (action.equals(ACTION_ADD_AUS)) doAddAus();
    else if (action.equals(ACTION_REMOVE_AUS)) doRemoveAus();
    else {
      errMsg = "Unknown action: " + action;
      displayMenu();
    }
  }

  /** Display "Add Archival Unit" button and list of configured AUs with Edit
   * buttons */
  private void displayMenu() throws IOException {
    Page page = newPage();
    page.add(getErrBlock());
//     page.add(getExplanationBlock("Add a new Archival Unit" +
// 				 (allAUs.isEmpty()
// 				  ? "."
// 				  : ", or edit an existing one.")));
    Table tbl = new Table(0, "align=center cellspacing=2 cellpadding=4");
    addMenuItem(tbl, "Add Titles", ACTION_ADD_TITLES,
		"Add one or more groups of titles");
    addMenuItem(tbl, "Remove Titles", ACTION_REMOVE_TITLES,
		"Remove selected titles");
    addMenuItem(tbl, "Backup", ACTION_BACKUP,
		"Backup cache config to a file on your workstation");
    addMenuItem(tbl, "Restore", ACTION_RESTORE,
		"Restore cache config from a file on your workstation");
    addMenuItem(tbl, "Manual Add/Edit", "Add, Edit or Delete an individual AU",
		SERVLET_AU_CONFIG, null);
    page.add(tbl);
    endPage(page);
  }

  void addMenuItem(Table tbl, String linkText, String expl,
		   ServletDescr descr, String params) {
    tbl.newRow("valign=top");
    tbl.newCell();
    tbl.add("<font size=+1>");
    tbl.add(srvLink(descr, linkText, params));
    tbl.add("</font>");
    tbl.newCell();
    tbl.add(expl);
  }

  void addMenuItem(Table tbl, String linkText, String action, String expl) {
    addMenuItem(tbl, linkText, expl, myServletDescr(), 
		ACTION_TAG + "=" + action + "");
  }

  private void chooseSets(Verb verb) throws IOException {
    this.verb = verb;
    Collection sets = pluginMgr.getTitleSets();
    Page page = newPage();
    addJavaScript(page);
    page.add(getErrBlock());
    page.add(getExplanationBlock("Select one or more collections of titles to "
				 + verb.word + ", " +
				 "then click Select Titles."));
    Form frm = new Form(srvURL(myServletDescr(), null));
    frm.method("POST");
    frm.add(new Input(Input.Hidden, ACTION_TAG));
    frm.add(new Input(Input.Hidden, KEY_VERB, verb.valStr));
    Table tbl = new Table(0, "align=center cellspacing=4 cellpadding=0");
    if (sets.size() >= 10) {
      tbl.newRow();
      tbl.newCell("align=center colspan=2");
      tbl.add(submitButton("Select Titles", ACTION_SELECT_SET_TITLES));
    }
    for (Iterator iter = sets.iterator(); iter.hasNext(); ) {
      TitleSet ts = (TitleSet)iter.next();
      if (isTsAppropriateFor(ts, verb)) {
	tbl.newRow();
	tbl.newCell("align=right valign=center");
	tbl.add(checkBox(null, ts.getName(), KEY_TITLE_SET, false));
	tbl.newCell("valign=center");
	tbl.add(ts.getName());
      }
    }
    tbl.newRow();
    tbl.newCell("align=center colspan=2");
    tbl.add(submitButton("Select Titles", ACTION_SELECT_SET_TITLES));
    frm.add(tbl);
    page.add(frm);
    endPage(page);
  }

  boolean isTsAppropriateFor(TitleSet ts, Verb verb) {
    if (verb.isAdd) {
      return !ts.isDelOnly();
    } else {
      return !ts.isAddOnly();
    }
  }

  private void selectSetTitles() throws IOException {
    String[] setNames = req.getParameterValues(KEY_TITLE_SET);
    if (setNames == null || setNames.length == 0) {
      errMsg = "You must select at least one title set.";
      chooseSets(verb);
      return;
    }
    Collection sets = findTitleSetsFromNames(setNames);
    RemoteApi.BatchAuStatus bas;
    if (verb.isAdd) {
      bas = remoteApi.findAusInSetsToAdd(sets);
    } else {
      bas = remoteApi.findAusInSetsToDelete(sets);
    }      
    if (bas.getStatusList().isEmpty()) {
      errMsg = "Selected set(s) contain no titles";
      chooseSets(verb);
      return;
    }
    selectTitles(bas, verb);
  }

  private void selectTitles(RemoteApi.BatchAuStatus bas, Verb verb)
      throws IOException {
    if (!bas.hasOk()) {
      dontSelectAus(bas);
      return;
    }
    HttpSession session = req.getSession(true);
    setSessionTimeout(session);
    Map auConfs = new HashMap();
    session.setAttribute(SESSION_KEY_AUID_MAP, auConfs);
    java.util.List repos = remoteApi.getRepositoryList();
    boolean repoFlg = verb.isAdd && repos.size() > 1;
    Page page = newPage();
    addJavaScript(page);
    page.add(getErrBlock());
    String buttonText = verb.cap + " Selected AUs";
    String expl = "Select the AUs you wish to " + verb.word;
    if (repoFlg) {
      expl += " and choose a repository for each";
    }
    expl += ". Then click " + buttonText + ".";
    page.add(getExplanationBlock(expl));
    if (repoFlg) {
      OrderedMap repoChoices = new LinkedMap();
      for (Iterator iter = repos.iterator(); iter.hasNext(); ) {
	String repo = (String)iter.next();
	PlatformInfo.DF df = remoteApi.getRepositoryDF(repo);
	repoChoices.put(repo, df);
      }
      page.add(getRepoKeyTable(repoChoices));
      session.setAttribute(SESSION_KEY_REPO_MAP, repoChoices);
    }
    Form frm = new Form(srvURL(myServletDescr(), null));
    frm.method("POST");
    frm.add(new Input(Input.Hidden, ACTION_TAG));
    frm.add(new Input(Input.Hidden, KEY_VERB, verb.valStr));
    frm.add(getSelectAllButtons());
    frm.add(getSelectActionButton(buttonText));
    frm.add(getSelectAusTable(bas, repos, auConfs));
    frm.add(getSelectAllButtons());
    frm.add(getSelectActionButton(buttonText));
    if (bas.hasNotOk()) {
      frm.add("<br>");
      frm.add(getNonOperableAuTable(bas, "These AUs cannot be " + verb.past));
    }
    page.add(frm);
    endPage(page);
  }

  Element getSelectActionButton(String buttonText) {
    Table btnTbl = new Table(0, "align=center cellspacing=4 cellpadding=0");
    btnTbl.newRow();
    btnTbl.newCell("align=center");
    btnTbl.add(submitButton(buttonText,
			    (verb.isAdd ?
			     ACTION_ADD_AUS : ACTION_REMOVE_AUS)));
    return btnTbl;
  }

  private void dontSelectAus(RemoteApi.BatchAuStatus bas)
      throws IOException {
    Page page = newPage();
    addJavaScript(page);
    page.add(getErrBlock());
//     Form frm = new Form(srvURL(myServletDescr(), null));
//     frm.method("POST");
//     frm.add(new Input(Input.Hidden, ACTION_TAG));
//     frm.add(nonOperableTable(bas));
//     page.add(frm);
    page.add(getNonOperableAuTable(bas,
				   "No AUs in set can be " + verb.past));
    endPage(page);
  }

  Table getSelectAusTable(RemoteApi.BatchAuStatus bas, java.util.List repos,
			  Map auConfs) {
    Table tbl = new Table(0, "align=center cellspacing=4 cellpadding=0");
    tbl.newRow();
    tbl.addHeading(verb.cap + "?", "align=center rowSpan=2");
    boolean repoFlg = verb.isAdd && repos.size() > 1;
    int reposSize = repoFlg ? repos.size() : 0;
    if (repoFlg) {
      tbl.addHeading("Repo ID", "align=center colspan=" + reposSize);
    }
    tbl.addHeading("Archival Unit", "align=center rowSpan=2");
    boolean isAdd = verb.isAdd;
    if (isAdd) {
      tbl.addHeading("Est. size", "align=center rowSpan=2");
    }
    tbl.newRow();
    for (int ix = 0; ix < reposSize; ix++) {
      tbl.addHeading(Integer.toString(ix+1), "align=center");
    }
    for (Iterator iter = bas.getStatusList().iterator(); iter.hasNext(); ) {
      RemoteApi.BatchAuStatus.Entry rs =
	(RemoteApi.BatchAuStatus.Entry)iter.next();
      if (rs.isOk()) {
	String auid = rs.getAuId();
	tbl.newRow();
	tbl.newCell("align=right valign=center");
	auConfs.put(auid, rs.getConfig());
	tbl.add(checkBox(null, auid, KEY_AUID, false));
	if (repoFlg) {
	  java.util.List existingRepoNames = rs.getRepoNames();
	  log.debug("existingRepoNames: " + existingRepoNames + ", " + auid);
	  String firstRepo =
	    (existingRepoNames == null || existingRepoNames.isEmpty())
	    ? null : (String)existingRepoNames.get(0);
	  int ix = 1;
	  for (Iterator riter = repos.iterator(); riter.hasNext(); ix++) {
	    String repo = (String)riter.next();
	    tbl.newCell("align=center");
	    if (firstRepo == null) {
	      tbl.add(radioButton(null, Integer.toString(ix),
				  KEY_REPO + "_" + auid, ix == 1));
	    } else if (repo.equals(firstRepo)) {
	      tbl.add(radioButton(null, Integer.toString(ix),
				  KEY_REPO + "_" + auid, true));
	    } else {
	    }
	  }
	} else {
	  tbl.newCell("colspan=" + reposSize);
	}
	tbl.newCell();
	tbl.add(rs.getName());
	TitleConfig tc = rs.getTitleConfig();
	long est;
	if (isAdd && tc != null && (est = tc.getEstimatedSize()) != 0) {
	  tbl.newCell("align=right");
	  tbl.add(StringUtil.sizeToString(est));
	}
      }
    }
    return tbl;
  }

  Element getNonOperableAuTable(RemoteApi.BatchAuStatus bas, String heading) {
    Composite comp = new Block(Block.Center);
    comp.add(heading);
    Table tbl = new Table(0, "align=center cellspacing=4 cellpadding=0");
    tbl.addHeading("Archival Unit");
    tbl.addHeading("Reason");
    for (Iterator iter = bas.getStatusList().iterator(); iter.hasNext(); ) {
      RemoteApi.BatchAuStatus.Entry rs =
	(RemoteApi.BatchAuStatus.Entry)iter.next();
      if (!rs.isOk()) {
	String auid = rs.getAuId();
	tbl.newRow();
// 	tbl.newCell("align=right valign=center");
// 	tbl.add(rs.getStatus());
// 	tbl.add("&nbsp;");
	tbl.newCell();
	tbl.add(rs.getName());
	tbl.newCell();
	tbl.add(rs.getExplanation());
      }
    }
//     return tbl;
    comp.add(tbl);
    return comp;
  }

  Table getRepoKeyTable(OrderedMap repoMap) {
    Table tbl = new Table(0, "align=center cellspacing=4 cellpadding=0");
    tbl.newRow();
    tbl.addHeading("Repo ID");
    tbl.addHeading("Repository Path");
    tbl.addHeading("Size");
    tbl.addHeading("Free");
    tbl.addHeading("%Full");
    int ix = 1;
    for (Iterator iter = repoMap.keySet().iterator(); iter.hasNext(); ix++) {
      String repo = (String)iter.next();
      PlatformInfo.DF df = (PlatformInfo.DF)repoMap.get(repo);
      tbl.newRow();
      tbl.newCell("align=right");
      tbl.add(ix + ".&nbsp;");
      tbl.newCell("align=left");
      tbl.add(repo);
      if (df != null) {
	tbl.newCell("align=right");
	tbl.add("&nbsp;");
	tbl.add(StringUtil.sizeKBToString(df.getSize()));
	tbl.newCell("align=right");
	tbl.add("&nbsp;");
	tbl.add(StringUtil.sizeKBToString(df.getAvail()));
	tbl.newCell("align=right");
	tbl.add("&nbsp;");
	tbl.add(df.getPercentString());
      }
    }
    tbl.newRow();
    tbl.newCell("colspan=6");
    tbl.add(Break.rule);
    return tbl;
  }

  Table getSelectAllButtons() {
    Table tbl = new Table(0, "align=center cellspacing=4 cellpadding=0");
    tbl.newRow();
    tbl.newCell("align=center");
    tbl.add(jsButton("Select All AUs", "selectAll(this.form, 0);"));
    tbl.newCell("align=center");
    tbl.add(jsButton("Invert Selection", "selectAll(this.form, 1);"));
    return tbl;
  }

  Collection findTitleSetsFromNames(String[] names) {
    Map map = pluginMgr.getTitleSetMap();
    Set res = new HashSet(names.length);
    for (int ix = 0; ix < names.length; ix++) {
      TitleSet ts = (TitleSet)map.get(names[ix]);
      if (ts != null) {
	res.add(ts);
      }
    }
    return res;
  }
      
  private void doAddAus() throws IOException {
    String[] auids = req.getParameterValues(KEY_AUID);
    if (auids == null || auids.length == 0) {
      errMsg = "No AUs were selected";
      displayMenu();
      return;
    }
    HttpSession session = req.getSession(false);
    if (session == null) {
      errMsg = "Please enable cookies";
      displayMenu();
      return;
    }
    Configuration createConfig = ConfigManager.newConfiguration(); 
    Map auConfs = (Map)session.getAttribute(SESSION_KEY_AUID_MAP);
    LinkedMap repoMap = (LinkedMap)session.getAttribute(SESSION_KEY_REPO_MAP);
    for (int ix = 0; ix < auids.length; ix++) {
      String auid = auids[ix];
      Configuration tcConfig = (Configuration)auConfs.get(auid);
      String repoId = getParameter(KEY_REPO + "_" + auid);
      if (!StringUtil.isNullString(repoId)) {
	try {
	  int repoIx = Integer.parseInt(repoId);
	  if (!StringUtil.isNullString(repoId)) {
	    tcConfig.put(PluginManager.AU_PARAM_REPOSITORY,
			 (String)repoMap.get(repoIx - 1));
	  }
	} catch (NumberFormatException e) {
	  log.warning("Illegal repoId: " + repoId, e);
	} catch (IndexOutOfBoundsException e) {
	  log.warning("Illegal repoId: " + repoId, e);
	}
      }
      String prefix = PluginManager.PARAM_AU_TREE + "." +
	PluginManager.configKeyFromAuId(auid);
      createConfig.addAsSubTree(tcConfig, prefix);
    }
    if (log.isDebug2()) log.debug2("createConfig: " + createConfig);

    RemoteApi.BatchAuStatus bas = remoteApi.batchAddAus(true, createConfig);
    displayBatchAuStatus(bas);
  }

  private void doRemoveAus() throws IOException {
    String[] auidArr = req.getParameterValues(KEY_AUID);
    if (auidArr == null || auidArr.length == 0) {
      errMsg = "No AUs were selected";
      displayMenu();
      return;
    }
    HttpSession session = req.getSession(false);
    if (session == null) {
      errMsg = "Please enable cookies";
      displayMenu();
      return;
    }
    java.util.List auids = ListUtil.fromArray(auidArr);
    RemoteApi.BatchAuStatus bas = remoteApi.deleteAus(auids);
    displayBatchAuStatus(bas);
  }

  /** Serve the contents of the local AU config file, as
   * application/binary */
  private void doSaveAll() throws IOException {
    try {
      InputStream is = remoteApi.getAuConfigBackupStream(getMachineName());
      Reader rdr = new InputStreamReader(is, Constants.DEFAULT_ENCODING);
      PrintWriter wrtr = resp.getWriter();
      resp.setContentType("application/binary");
      StreamUtil.copy(rdr, wrtr);
      rdr.close();
    } catch (FileNotFoundException e) {
      errMsg = "No AUs have been configured - nothing to backup";
      displayMenu();
    } catch (IOException e) {
      log.warning("doSaveAll()", e);
      throw e;
    }
  }

  /** Display the Restore page */
  private void displayRestore() throws IOException {
    Page page = newPage();
    addJavaScript(page);
    page.add(getErrBlock());
    Form frm = new Form(srvURL(myServletDescr(), null));
    frm.method("POST");
    frm.attribute("enctype", "multipart/form-data");
    frm.add(new Input(Input.Hidden, ACTION_TAG));
    frm.add(new Input(Input.Hidden, KEY_VERB, VERB_RESTORE.valStr));
    Table tbl = new Table(0, "align=center cellspacing=4 cellpadding=0");
    tbl.newRow();
    tbl.newCell("align=center");
    tbl.add("Enter name of AU configuration backup file");
    tbl.newRow();
    tbl.newCell("align=center");
    tbl.add(new Input(Input.File, "AuConfigBackupContents"));
    tbl.newRow();
    tbl.newCell("align=center");
    tbl.add(submitButton("Restore", ACTION_SELECT_RESTORE_TITLES));
    frm.add(tbl);
    page.add(frm);
    endPage(page);
  }

  private void selectRestoreTitles() throws IOException {
    InputStream ins = multiReq.getInputStream("AuConfigBackupContents");
    if (ins == null) {
      errMsg = "No backup file uploaded";
      displayRestore();
    } else {
      try {
	RemoteApi.BatchAuStatus bas = remoteApi.batchAddAus(false, ins);
	if (bas.getStatusList().isEmpty()) {
	  errMsg = "Backup file is empty";
	  displayRestore();
	  return;
	}
	selectTitles(bas, VERB_RESTORE);
      } catch (RemoteApi.InvalidAuConfigBackupFile e) {
	errMsg = "Couldn't restore configuration: " + e.getMessage();
	displayRestore();
      }
    }
  }

  private void displayBatchAuStatus(RemoteApi.BatchAuStatus status)
      throws IOException {
    Page page = newPage();
    page.add(getErrBlock());
    java.util.List statusList = status.getStatusList();
    int okCnt = status.getOkCnt();
    int errCnt = statusList.size() - okCnt;
    page.add(getExplanationBlock(okCnt + " AUs " + verb.past + ", " +
				 errCnt + " skipped"));
    Table tbl = new Table(0, "align=center cellspacing=4 cellpadding=0");
    tbl.addHeading("Status");
    tbl.addHeading("Archival Unit");
    for (Iterator iter = statusList.iterator(); iter.hasNext(); ) {
      RemoteApi.BatchAuStatus.Entry stat =
	(RemoteApi.BatchAuStatus.Entry)iter.next();
      tbl.newRow();
      tbl.newCell();
      tbl.add("&nbsp;");
      tbl.add(stat.getStatus());
      tbl.add("&nbsp;");
      tbl.newCell();
      String name = stat.getName();
      tbl.add(name != null ? name : stat.getAuId());
      if (stat.getExplanation() != null) {
	tbl.newCell();
	tbl.add(stat.getExplanation());
      }
    }
    page.add(tbl);
    endPage(page);
  }

  /** Create message and error message block */
  private Composite getErrBlock() {
    Composite comp = new Composite();
    if (errMsg != null) {
      comp.add("<center><font color=red size=+1>");
      comp.add(errMsg);
      comp.add("</font></center><br>");
    }
    if (statusMsg != null) {
      comp.add("<center><font size=+1>");
      comp.add(statusMsg);
      comp.add("</font></center><br>");
    }
    return comp;
  }

  /** Common and page adds Back link, footer */
  protected void endPage(Page page) throws IOException {
    if (action != null) {
      page.add("<center>");
      page.add(srvLink(myServletDescr(), "Back to Journal Configuration"));
      page.add("</center>");
    }
    page.add(getFooter());
    page.write(resp.getWriter());
  }

  // make me a link in nav table if not on initial journal config page
  protected boolean linkMeInNav() {
    return action != null;
  }

  // Verb support
  Verb findVerb(String valStr) throws IllegalVerb {
    try {
      int val = Integer.parseInt(valStr);
      return verbs[val-1];
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new IllegalVerb();
    } catch (NumberFormatException e) {
      throw new IllegalVerb();
    }
  }

  static class Verb {
    int val;
    String valStr;
    String word;
    String cap;
    String past;
    boolean isAdd;
    Verb(int val, String word, String past, boolean isAdd) {
      this.val = val;
      this.valStr = Integer.toString(val);
      this.word = word;
      this.cap = word.substring(0,1).toUpperCase() + word.substring(1);
      this.past = past;
      this.isAdd = isAdd;
    }
  }

  static class IllegalVerb extends Exception {
  }
}
