package org.lockss.exporter.kbart;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.lockss.config.TdbTestUtil;
import org.lockss.exporter.kbart.KbartTitle.Field;
import org.lockss.test.LockssTestCase;

public class TestKbartTitle extends LockssTestCase {

  private static final String DEFAULT_TEST_STRING = "A_big_string_of_test"; 
  private static final String DEFAULT_TITLE = "KBART KSIMPSON"; 
  private static final String DEFAULT_TITLE_LESS = "JBART"; 
  private static final String DEFAULT_TITLE_GREATER = "LBART"; 
  	 
  private KbartTitle testTitle;
  
  protected void setUp() throws Exception {
    super.setUp();
    this.testTitle = createKbartTitle(new HashMap<KbartTitle.Field, String>() {{
      put(KbartTitle.Field.TITLE_ID, TdbTestUtil.DEFAULT_TITLE_ID);
      put(KbartTitle.Field.PRINT_IDENTIFIER, TdbTestUtil.DEFAULT_ISSN_1);
      put(KbartTitle.Field.ONLINE_IDENTIFIER, TdbTestUtil.DEFAULT_EISSN_1);
      put(KbartTitle.Field.PUBLICATION_TITLE, DEFAULT_TITLE);
      put(KbartTitle.Field.PUBLISHER_NAME, TdbTestUtil.DEFAULT_PUBLISHER);
      put(KbartTitle.Field.TITLE_URL, TdbTestUtil.DEFAULT_URL);

      put(KbartTitle.Field.DATE_FIRST_ISSUE_ONLINE, TdbTestUtil.RANGE_1_START);
      put(KbartTitle.Field.DATE_LAST_ISSUE_ONLINE, TdbTestUtil.RANGE_1_END);
      put(KbartTitle.Field.NUM_FIRST_VOL_ONLINE, TdbTestUtil.RANGE_1_START_VOL);
      put(KbartTitle.Field.NUM_LAST_VOL_ONLINE, TdbTestUtil.RANGE_1_END_VOL);
      //put(KbartTitle.Field.NUM_FIRST_ISSUE_ONLINE, TdbTestUtil.RANGE_1_START);
      //put(KbartTitle.Field.NUM_LAST_ISSUE_ONLINE, TdbTestUtil.RANGE_1_END);
    }});
  }

  protected void tearDown() throws Exception {
    super.tearDown();
    this.testTitle = null;
  }

  /**
   * Make a clone and check that it equal but not identical. 
   */
  public final void testClone() {
    KbartTitle clone = testTitle.clone();
    // The clone should have all the same values but be a different object
    assertNotEquals(testTitle, clone);
    assertNotSame(testTitle, clone);
    for (Field f : KbartTitle.Field.values()) {
      assertEquals("Clone differs on "+f.getLabel(), testTitle.getField(f), clone.getField(f)); 
    }
  }

  /**
   * Try setting various values on a field.
   */
  public final void testSetField() {
    // Set a field value (note we use an otherwise unused field)
    testTitle.setField(Field.COVERAGE_DEPTH, DEFAULT_TEST_STRING);
    assertEquals(testTitle.getField(Field.COVERAGE_DEPTH), DEFAULT_TEST_STRING);
    // Try setting a null field value
    testTitle.setField(null, "not likely");
    assertEquals(testTitle.getField(Field.COVERAGE_DEPTH), DEFAULT_TEST_STRING);
    // Try setting a field to a null value
    testTitle.setField(Field.COVERAGE_DEPTH, null);
    assertEquals(testTitle.getField(Field.COVERAGE_DEPTH), "");
  }

  /**
   * Try getting a field value, and passing null values.
   */
  public final void testGetField() {
    // Set a field value (note we use an otherwise unused field)
    testTitle.setField(Field.COVERAGE_DEPTH, DEFAULT_TEST_STRING);
    String s = testTitle.getField(Field.COVERAGE_DEPTH);
    assertNotNull(s);
    assertEquals(s, DEFAULT_TEST_STRING);
    // Test that empty strings are returned where necessary
    assertNotNull(testTitle.getField(null));
    assertEquals(testTitle.getField(null), "");
  }

  /**
   * Check the field values.
   */
  public final void testFieldValues() {
    // Check that all fields are represented, and in the appropriate order
    List<String> vals = new Vector<String>(testTitle.fieldValues());
    Collection<String> labs = KbartTitle.Field.getLabels();
    // The full range of KbartTitle fields:
    EnumSet<KbartTitle.Field> fields = EnumSet.allOf(KbartTitle.Field.class);
    // Check every field has a value
    assertEquals(fields.size(), vals.size());
    
    // Check the content of every field
    Iterator<String> i1 = vals.iterator();
    Iterator<Field> i2 = fields.iterator();
    while (i1.hasNext()) {
      String s1 = i1.next();
      Field f = i2.next();
      String s2 = testTitle.getField(f);
      assertEquals("Field "+f.getLabel()+" differs", s1, s2);      
    }
  }

  /**
   * The KbartTitles are compared based on their publication titles; test titles that are equal, less and greater.
   */
  public final void testCompareTo() {
    // Create a second test title which is a clone of the first
    KbartTitle testTitle2 = testTitle.clone();
    assertTrue(testTitle.compareTo(testTitle2)==0);
    // Change title to a lesser one 
    testTitle2.setField(KbartTitle.Field.PUBLICATION_TITLE, DEFAULT_TITLE_LESS);
    assertTrue(testTitle.compareTo(testTitle2)>0);
    // Change title to a greater one
    testTitle2.setField(KbartTitle.Field.PUBLICATION_TITLE, DEFAULT_TITLE_GREATER);
    assertTrue(testTitle.compareTo(testTitle2)<0);
  }

  /**
   * Create a KbartTitle with fields specified by the supplied properties hash.
   *  
   * @param props as hash of properties to be added to the new title, using the constant field names as keys
   * @return
   */
  protected static KbartTitle createKbartTitle(Map<Field, String> props) {
    KbartTitle kbt = new KbartTitle();
    for (Field f : props.keySet()) {
      kbt.setField(f, props.get(f));
    }
    return kbt;
  }
  
}
