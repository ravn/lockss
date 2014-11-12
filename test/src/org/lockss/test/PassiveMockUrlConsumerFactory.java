package org.lockss.test;

import java.io.IOException;

import org.lockss.daemon.Crawler.CrawlerFacade;
import org.lockss.daemon.LockssWatchdog;
import org.lockss.plugin.FetchedUrlData;
import org.lockss.plugin.UrlConsumer;
import org.lockss.plugin.UrlConsumerFactory;

public class PassiveMockUrlConsumerFactory implements UrlConsumerFactory {

  public class PassiveMockUrlConsumer implements UrlConsumer {
  
    @Override
    public void consume() throws IOException {
      //Do nothing
    }

    @Override
    public void setWatchdog(LockssWatchdog wdog) {
      //Do nothing
      
    }
  
  }

  @Override
  public UrlConsumer createUrlConsumer(CrawlerFacade crawlFacade,
      FetchedUrlData fud) {
    return new PassiveMockUrlConsumer();
  }
}