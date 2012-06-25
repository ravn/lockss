package org.lockss.plugin.etd.vtetdplugin;

import java.util.*;
import java.io.File;

import org.lockss.daemon.*;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.base.BaseCachedUrlSet;
import org.lockss.plugin.definable.*;
import org.lockss.test.*;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.util.SetUtil;

public class TestVTETD97And98Plugin extends LockssPluginTestCase {
  static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
  static final String YEAR_KEY = ConfigParamDescr.YEAR.getKey();
  static final String OAI_KEY = ConfigParamDescr.OAI_REQUEST_URL.getKey();

  static final String ROOT_URL = "http://scholar.lib.vt.edu/";
  static final String OAI_PROVIDER = "http://scholar.lib.vt.edu/theses/OAI2/";

  static final String PLUGIN_ID = 
    "org.lockss.plugin.etd.vtetdplugin.VTETD97And98Plugin";

  private DefinablePlugin plugin;

  public void setUp() throws Exception {
    super.setUp();
  }

  private Properties makeProps(String url, int year, String oaiProvider) {
    Properties props = new Properties();
    props.setProperty(YEAR_KEY, Integer.toString(year));
    props.setProperty(BASE_URL_KEY, url);
    props.setProperty(OAI_KEY, oaiProvider);
    return props;
  }

  public void testShouldCacheProperPages() throws Exception {
    int year = 1998;
    ArchivalUnit bbAu = 
      makeAu(PLUGIN_ID, makeProps(ROOT_URL, year, OAI_PROVIDER));
    MockLockssDaemon theDaemon = getMockLockssDaemon();
    theDaemon.getLockssRepository(bbAu);
    theDaemon.getNodeManager(bbAu);
    BaseCachedUrlSet cus = 
      new BaseCachedUrlSet(bbAu, new RangeCachedUrlSetSpec(ROOT_URL));

    Set urlsToCache = 
      SetUtil.set(
                  "http://scholar.lib.vt.edu/lockss_permission.html",
                  "http://scholar.lib.vt.edu/theses/available/etd-1898-17448/",
                  "http://scholar.lib.vt.edu/theses/available/etd-7898-13842/unrestricted/Vita.pdf"
                  );
    
    assertShouldCache(urlsToCache, bbAu, cus);

    Set urlsNotToCache = 
      SetUtil.set(
                  "http://scholar.lib.vt.edu/theses/available/etd-07011998-215241/",
                  "http://scholar.lib.vt.edu/theses/available/etd-10121998-224043/unrestricted/HafnerThesis.pdf",
                  "http://scholar.lib.vt.edu/theses/available/etd-1899-17448/",
                  "http://scholar.lib.vt.edu/theses/available/etd-7899-13842/unrestricted/Vita.pdf",
                  "http://scholar.lib.vt.edu/theses/available/etd-4199-155242/unrestricted/5.PDF",
                  "http://scholar.lib.vt.edu/theses/available/etd-3132141279612241/",
                  "http://scholar.lib.vt.edu/theses/available/etd-3034112939721181/unrestricted/etd.pdf",
                  "http://scholar.lib.vt.edu/theses/available/etd-07012003-215241/",            
                  "http://scholar.lib.vt.edu/theses/available/etd-07012003-215241/"
                  );
    
    assertShouldNotCache(urlsNotToCache, bbAu, cus);
  }

  public void testGetName() throws Exception {
    ArchivalUnit au = 
      makeAu(PLUGIN_ID, makeProps(ROOT_URL, 1999, OAI_PROVIDER));
    assertEquals("Virginia Tech ETD Collection 1999", au.getName());

    au = makeAu(PLUGIN_ID, makeProps(ROOT_URL, 1998, OAI_PROVIDER));
    assertEquals("Virginia Tech ETD Collection 1998", au.getName());
  }
}
