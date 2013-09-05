package org.lockss.extractor;

import org.apache.commons.lang.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.lockss.daemon.PluginException;
import org.lockss.plugin.CachedUrl;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

/**
 * XML Tag Extractor which uses jsoup parser
 */
public class JsoupXmlTagExtractor extends SimpleFileMetadataExtractor {
  static Logger theLog = Logger.getLogger("JsoupXmlTagExtractor");
  protected Collection<String> m_tags;
  Parser m_parser = Parser.xmlParser();

  JsoupXmlTagExtractor()
  {

  }

  /**
   * Create an extractor what will extract the value(s) of the xml tags in
   * <code>tags</code>
   * @param tags the list of XML tags whose value to extract
   */
  public JsoupXmlTagExtractor(Collection<String> tags) {
    m_tags = tags;
  }

  /**
   * Create an extractor that will extract the value(s) of the xml tags in
   * <code>tagMap.keySet()</code>
   * @param tagMap a map from XML tags to cooked keys.  (Only the set of
   * tags is used by this object.)
   */
  public JsoupXmlTagExtractor(Map tagMap) {
    m_tags = tagMap.keySet();
  }

  /**
   * set the tags for extraction
   * @param tagMap the map with keys to to be used as the tags for extraction
   */
  public void setTags(Map tagMap)
  {
    m_tags = tagMap.keySet();
  }

  /**
   * set the tags for extraction
   * @param tags the collection of tags we will extract data from
   */
  public void setTags(Collection<String> tags)
  {
    m_tags = tags;
  }

  @Override
  public ArticleMetadata extract(final MetadataTarget target,
                                 final CachedUrl cu) throws
      IOException, PluginException {
    // validate input
    if (cu == null) {
      throw new IllegalArgumentException("extract() called with null CachedUrl");
    }
    ArticleMetadata am_ret = new ArticleMetadata();
    if(cu.hasContent()) {
      InputStream in = cu.getUnfilteredInputStream();
      Document doc = Jsoup.parse(in, null, cu.getUrl(), m_parser);
      extractTags(doc, am_ret);
    }
    return am_ret;
  }

  /**
   * extract the values for the desired tags and store them in article metadata
   * @param doc the jsoup parsed doc
   * @param articleMeta the ArticleMetadata in which to store the tag/value(s)
   */
  void extractTags(Document doc, ArticleMetadata articleMeta)
  {
    // if we don't have any tags, there is nothing to do so we return
    if(m_tags == null || m_tags.isEmpty()) return;
    for(String tag : m_tags) {
      String value;
      Elements tag_elements = doc.select(tag);
      for(Element tag_el : tag_elements) {
        if(tag_el.hasText()) {
          value = processXml(tag, tag_el.text());
          articleMeta.putRaw(tag, value);
        }
      }
    }
  }

  /**
   * take the value for a tag from an xml page and perform the necessary
   * transformations to regularize it for storing in the article metadata.
   * this will unescape any escaped xml and remove any extra spaces.
   *
   * @param name the tag name
   * @param value the value
   * @return the regularized value
   */
  private String processXml(final String name, String value) {
    // remove character entities from content
    value = StringEscapeUtils.unescapeXml(value);
    // normalize multiple whitespaces to a single space character
    value = value.replaceAll("\\s+", " ");
    if (theLog.isDebug3()) theLog.debug3("Add: " + name + " = " + value);
    return value;
  }

}
