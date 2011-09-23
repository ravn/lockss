/*
 * $Id: TdbAuOrderScorer.java,v 1.3 2011-09-23 13:23:15 easyonthemayo Exp $
 */

/*

Copyright (c) 2011 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.exporter.kbart;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.oro.text.regex.MatchResult;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;
import org.lockss.config.TdbAu;
import org.lockss.exporter.kbart.KbartConverter.TitleRange;
import org.lockss.util.NumberUtil;
import org.lockss.util.RegexpUtil;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;

/**
 * This is a utility class intended to provide some measure of whether a
 * particular ordering of TdbAu objects, based on either year or volume fields,
 * provides an appropriately consistent ordering for both fields.
 * The main purpose is to decide which ordering gives the most consistent (and
 * therefore, we assume, more likely to be correct) results. To decide this
 * we need to know which field was used to order the list. There are two ways
 * of assessing the ordering - looking at the consistency of the whole list,
 * and at the consistency of individual ranges that have been created by
 * identifying coverage gaps in that list.
 * <p>
 * When a list of AUs is ordered in a supposedly chronological order, we expect
 * that the years will be monotonically increasing. This is unfortunately not
 * always the case, so we have to provide a more flexible metric which allows
 * them to overlap slightly. We also expect that the volume sequence should only
 * increase, but this assumption is routinely broken because volume identifier
 * formats change or reset, or sometimes are inconsistently mixed.
 * <p>
 * Volume is the preferred field to inform the calculation of coverage gaps,
 * because it tends to display more consistency. There should be minimal
 * duplication and in general the values should be increasing; it should
 * therefore be simpler to identify whether or not a sequence is unbroken.
 * However because of the inconsistencies mentioned above, and missing data, it
 * is sometimes necessary to fall back on the year sequence to validate the
 * coverage gaps suggested by the volume ordering. When the volume ordering
 * looks particularly suspicious or inconsistent, the list of AUs can be
 * reordered by year to see if that gives a more consistent ordering and set
 * of coverage ranges.
 *
 * <h2>Volume/Year Sequence Consistency</h2>
 *
 * <i>
 * Volume strings can be interpreted as numbers (integers or Roman numerals),
 * mixed strings, or a range defined by a pair of either of the preceding types
 * separated by a hyphen.
 * <br/><br/>
 * Year strings can only be interpreted as 4-digit integers or a range defined
 * by a pair of 4-digit integers separated by a hyphen.
 * </i>
 * <p>
 * A volume sequence is consistent if, over each range defined by the
 * ordered sequence, each volume identifier (or the start or end identifier
 * of a range, depending on the context of the AU in the sequence) is equal to,
 * or an increment of, the previous one. This gets harder to assess for
 * string-based volumes, but sequences which are numerical and consecutive will
 * in general get a high score.
 * <p>
 * A year sequence is consistent if, over each range defined by the
 * ordered sequence, each start date is greater than or equal to the
 * previous start date <b><i>or</i></b> the start of the first range is
 * is included in the second range (in other words there is some kind of
 * overlap).
 *
 * <h2>Appropriate Consecutivity</h2>
 *
 * It is acceptable for consecutive years to be the same; they can also exhibit
 * breaks within a coverage period as long as the volumes are appropriately
 * consecutive and the years are increasing. This allows for the fact that there
 * may be legitimate gaps between consecutive volumes of greater than a year,
 * and also that multiple volumes may get published within a single year.
 * Additionally, particular volume identifiers can be repeated within
 * consecutive AUs, because for example the volume was published in chunks
 * across several years.
 * <p>
 * The concept of "appropriate consecutivity" is more flexible for years; if
 * volumes cannot be recognised as appropriately consecutive, it is safer to
 * record a coverage gap.
 *
 * <h2>Summary</h2>
 *
 * There are methods for checking the strict consecutivity of both years and
 * volumes represented as integers or strings; for checking whether values are
 * increasing or decreasing; and whether pairs of years or volumes appear to be
 * <i>appropriately consecutive</i> (are close enough to be considered
 * consecutive if the other field suggests so) or <i>appropriately sequenced</i>
 * (not causing concern that the AU ordering is deficient).
 * <p>
 * Finally there are methods which bring all these measures together in order
 * to produce a measure of consistency for a list of years or volumes which is
 * the outcome of a particular ordering. These are based on the concepts of
 * (a) how many breaks are in a range and whether they are positive or negative
 * breaks; (b) how much redundancy is in a range; (c) the frequency of coverage
 * gaps produced by an ordering. The definitions of these concepts differ for
 * year and volume, and depending on the ordering context.
 *
 * @author Neil Mayo
 */
public final class TdbAuOrderScorer {

  private static Logger log = Logger.getLogger("TdbAuOrderScorer");

  /** Do not allow instantiation outside of package or subclasses. */
  protected TdbAuOrderScorer() { /* No instantiation. */ }

  /**
   * A pattern to find the last numerical token in a volume string. If the
   * string has no digits, the pattern does not match and the string can bex
   * processed alphabetically; otherwise three matches are produced - the
   * string before the matched token, the final numerical token, and the
   * string after the token. The two surrounding strings may be empty.
   */
  private static final Pattern finalNumericalTokenPattern =
    RegexpUtil.uncheckedCompile("^(.*?)(\\d+)([^\\d]*)$");

  /**
   * An enum to allow the primary sort field to be specified when passing in a
   * list of ranges. Also simplifies access to the appropriate methods when
   * deciding the relationship between a pair of values in the given field.
   */
  static enum SORT_FIELD {

    VOLUME {
      public SORT_FIELD other() { return YEAR; };
      public String getValue(TdbAu au) { return au.getVolume(); }
      // Volumes are compared end year to start year
      public String getValueForComparisonAsPrevious(TdbAu au) {
        return au.getEndVolume();
      }
      public String getValueForComparisonAsCurrent(TdbAu au) {
        return au.getStartVolume();
      }
      public boolean areIncreasing(String first, String second) {
        return areVolumesIncreasing(first, second);
      }
      /**
       * We consider a volume to be decreasing if it is not monotonically
       * increasing. If two volumes are equal, increasing, or if the formats
       * change, <code>false</code> is returned.
       * @param first a volume string
       * @param second a second volume string
       * @return <tt>true</tt> if the volume strings are decreasing
       */
      public boolean areDecreasing(String first, String second) {
        return changeOfFormats(first, second) ? false :
            !areVolumesIncreasing(first, second);
      }
      public boolean areConsecutive(String first, String second) {
        return areVolumesConsecutive(first, second);
      }
      /**
       * Consecutive TdbAus must usually have valid (non-zero) and strictly
       * consecutive volumes. However, because of occasional interleaving of
       * duplicate records which appear because of genuine duplicates when a
       * publisher re-releases on a new platform, we allow consecutive TdbAus
       * to have the same volume string if their other fields are also
       * duplicated.
       * <p>
       * Because of overlapping volume ranges (see for example "Laser Chemistry")
       * we also allow the first TdbAu's end volume to be equal to the second's
       * start volume.
       * <p>
       * A third possibility is that consecutive TdbAus have the same volume
       * string and consecutive years, suggesting a volume published in several
       * instalments over a period.
       * <p>
       * This method handles volume ranges, splitting them before checking
       * whether the end volume of the first TdbAu and the start volume of the
       * second TdbAu are consecutive. With volumes there is no room for fuzzy
       * overlap as there is when comparing years.
       *
       * @param first a TdbAu
       * @param second another TdbAu
       * @return <code>true</code> if the TdbAu volume ranges appear to be appropriately consecutive
       */
      public boolean areAppropriatelyConsecutive(TdbAu first, TdbAu second) {
        String e1 = first.getEndVolume();
        String s2 = second.getStartVolume();
        return
            // True if valid and consecutive volumes
            (areVolumeStringsValid(e1, s2) && areVolumesConsecutive(e1, s2)) ||
            // True if valid and equal boundary volumes
            (areVolumeStringsValid(e1, s2) && e1.equals(s2)) ||
            // True if consecutive duplicate TdbAus, including volume
            areVolumesConsecutiveDuplicates(first, second) ||
            // True if valid volumes extending across appropriately consecutive years
            (areVolumeStringsValid(e1, s2) && areExtendedVolume(first, second));
      }

      /**
       * TdbAu sequences must display monotonically increasing volumes.
       */
      public boolean areAppropriatelySequenced(TdbAu first, TdbAu second) {
        return isMonotonicallyIncreasing(Arrays.asList(first, second), this);
      }
    },


    YEAR {
      public SORT_FIELD other() { return VOLUME; };
      public String getValue(TdbAu au) { return au.getYear(); }
      // Year are compared start year to start year
      public String getValueForComparisonAsPrevious(TdbAu au) {
        return au.getStartYear();
      }
      public String getValueForComparisonAsCurrent(TdbAu au) {
        return au.getStartYear();
      }
      public boolean areIncreasing(String first, String second) {
        return areYearsIncreasing(first, second);
      }
      /** Years are decreasing if not strictly consecutive and not increasing. */
      public boolean areDecreasing(String first, String second) {
        return !areConsecutive(first, second) &&
            !areVolumesIncreasing(first, second);
      }
      public boolean areConsecutive(String first, String second) {
        return areYearsConsecutive(first, second);
      }
      /** Consecutive TdbAus must have appropriately consecutive year ranges. */
      public boolean areAppropriatelyConsecutive(TdbAu first, TdbAu second) {
        // Appropriate sequencing, plus no significant gap between the ranges
        return areYearRangesAppropriatelyConsecutive(first.getYear(),
            second.getYear());
      }
      /** TdbAu sequences must have appropriately sequenced year ranges. */
      public boolean areAppropriatelySequenced(TdbAu first, TdbAu second) {
        return areYearRangesAppropriatelySequenced(first.getYear(),
            second.getYear());
      }
    }
    ;

    /** Whether the TdbAu has a valid value on the field. */
    public boolean hasValue(TdbAu au) { return getValue(au)!=null; }
    // Abstract methods for SORT_FIELD
    /** The 'other' or secondary sort field when this is the primary sort field. */
    public abstract SORT_FIELD other();
    /** Gets the full value of the field. */
    public abstract String getValue(TdbAu au);
    /**
     * Gets the value of the field's range to use for comparison when the TdbAu
     * is the <i>previous</i> one in a pair comparison. This is not necessarily
     * equivalent to the end of the range, for example years are only compared
     * on start year due to the frequent unreliability of end years.
     */
    public abstract String getValueForComparisonAsPrevious(TdbAu au);
    /**
     * Gets the value of the field's range to use for comparison when the TdbAu
     * is the <i>current</i> one in a pair comparison. This is not necessarily
     * equivalent to the start of the range.
     */
    public abstract String getValueForComparisonAsCurrent(TdbAu au);
    /** Whether the TdbAus have increasing values on the field. */
    public abstract boolean areIncreasing(String first, String second);
    /** Whether the TdbAus have decreasing values on the field. */
    public abstract boolean areDecreasing(String first, String second);
    /** Whether the TdbAus have consecutive values on the field. */
    public abstract boolean areConsecutive(String first, String second);
    /** Whether the TdbAus have field values which are appropriately sequenced. */
    public abstract boolean areAppropriatelySequenced(TdbAu first, TdbAu second);
    /** Whether the TdbAus have field values which are appropriately consecutive. */
    public abstract boolean areAppropriatelyConsecutive(TdbAu first, TdbAu second);
    // TODO Add throws declarations to these methods?
    // NumberFormatException, NullPointerException
  };


  /**
   * An object to represent the several values comprising a consistency score.
   * For a given ordering, there is a score for each field based on how
   * consistent a set of ranges it produces, and a score for each field based
   * on how consistent the full list of field values is.
   */
  static class ConsistencyScore {
    public final float volScore, yearScore, volListScore, yearListScore, score;
    public ConsistencyScore(float volScore, float yearScore,
                            float volListScore, float yearListScore) {
      this.volScore = volScore;
      this.yearScore = yearScore;
      this.volListScore = volListScore;
      this.yearListScore = yearListScore;
      // Overall score is the sum of the products of the range scores and
      // full sequence scores. This measure might be improved.
      this.score = yearScore * volScore + yearListScore * volListScore;
    }
    public String toString() {
      return String.format("ConsistencyScore vol %f \t year %f \t " +
          "volList %f \t yearList %f \t Overall %s",
          volScore, yearScore, volListScore, yearListScore, score);
    }
  }


  /**
   * Determines whether a String appears to represent a range. In general, a
   * range is considered to be two strings which are both either numeric
   * (representing an integer or a Roman numeral) or non-numeric,
   * separated by a hyphen '-' and with optional whitespace.
   * The second value is expected to be numerically or lexically greater than
   * or equal to the first. For example, "s1-4" would not qualify as either a
   * numeric or a non-numeric range, while "I-4" ("I" being a Roman number) and
   * the string range "a-baa" would. Numerical ordering is checked if the
   * strings are numerical, otherwise case-insensitive lexical ordering is
   * checked.
   * <p>
   * A numeric range must have two numeric values separated by a '-', while a
   * non-numeric range has two non-numeric values. In theory a string like
   * "s1-4" could represent a range, as could "1-s4", but it is impossible to
   * say whether either of these represents a genuine range with
   * different-format endpoints, or a single identifier. This is why the
   * condition is imposed that the strings either side of the hyphen must be
   * both numeric, or both non-numeric and of roughly the same format.
   * That is, either they are both parseable as integers, or they both involve
   * non-digit tokens that cannot be parsed as integers.
   * <p>
   * To allow for identifers that themselves incorporate a hyphen, the input
   * string is only split around the centremost hyphen. If there is an even
   * number of hyphens, the input string is assumed not to represent a parseable
   * range.
   * <p>
   * It might also be useful to enforce further restrictions upon non-numerical
   * strings that are considered valid endpoints of a range - for example, that
   * when tokenised, each pair of non-numerical tokens are equivalent, or the
   * same length, or lexically either equal or increasing.
   *
   * @param range the input String
   * @return <tt>true</tt> if the input string represents a range
   */
  public static boolean isVolumeRange(String range) {
    if (range == null) return false;
    // Check first if it is a numerical range
    if (NumberUtil.isNumericalRange(range)) return true;
    // We are now dealing with either a non-range, or at least one non-numerical
    // endpoint. Find the range-separating hyphen.
    int hyphenPos = NumberUtil.findRangeHyphen(range);
    if (hyphenPos < 0) return false;
    // Zero-padding up to 4 positions should be ample for volumes
    int pad = 4;
    // Split string at middlemost hyphen, and pad numerical tokens with 0
    String s1 = NumberUtil.padNumbers(range.substring(0, hyphenPos).trim(), pad);
    String s2 = NumberUtil.padNumbers(range.substring(hyphenPos+1).trim(),  pad);
    // Check format of strings
    if (TdbAuOrderScorer.changeOfFormats(s1, s2)) return false;
    // TODO further check tokens if required
    // Check lexical order
    return s2.compareToIgnoreCase(s1) >= 0;
  }

  /**
   * Checks whether two years are strictly consecutive, that is, the second is
   * one greater than the first.
   *
   * @param first an integer representing the first year
   * @param second an integer representing the second year
   * @return true if the second year is one greater than the first
   */
  static final boolean areYearsConsecutive(int first, int second) {
    return NumberUtil.areConsecutive(first, second);
  }

  /**
   * Checks whether two string years are strictly consecutive, that is, the
   * second is one greater than the first.
   *
   * @param first a String representing the first year
   * @param second a String representing the subsequent year
   * @return true if the years can be parsed and the second year is one greater than the first
   */
  static final boolean areYearsConsecutive(String first, String second) {
    return NumberUtil.areConsecutive(first, second);
  }

  /**
   * Whether there appears to be a coverage gap between year ranges. That is,
   * whether there is a positive gap greater than one between the end of one
   * range and the start of another. If the supplied strings
   * are not parseable as ranges or single years, it will be impossible to
   * establish whether there is a gap and an exception is thrown.
   *
   * @param firstRange the first range or year
   * @param secondRange the second range or year
   * @return true if the strings could be parsed and there is a gap between the ranges
   * @throws NumberFormatException if the Strings do not represent year ranges or integers
   */
  static final boolean isGapBetween(String firstRange, String secondRange)
  throws NumberFormatException {
    int s2 = NumberUtil.parseInt(NumberUtil.getRangeStart(secondRange));
    int e1 = NumberUtil.parseInt(NumberUtil.getRangeEnd(firstRange));
    return s2 - e1 > 1;
  }

  /**
   * A volume string is considered to be valid if (when trimmed of whitespace)
   * it is non-null, not empty, and is equal neither to zero nor a lone hyphen.
   * This applies to a range string too, though it does not indicate whether the
   * range itself is valid.
   * @param vol a volume string
   * @return <code>true</code> if the string is non-empty, not zero and not a single hyphen
   */
  static final boolean isVolumeStringValid(String vol) {
    if (vol==null) return false;
    vol = vol.trim();
    // False if the string is null or empty
    if (StringUtil.isNullString(vol)) return false;
    // False if the trimmed string is a hyphen
    if (StringUtil.equalStringsIgnoreCase(vol, "-")) return false;
    // Finally, check if the string is equivalent to zero
    try {
      return NumberUtil.parseInt(vol) != 0;
    } catch (NumberFormatException e) {
      // String could not be parsed as an integer - consider it valid!
      return true;
    }
  }

  /**
   * A pair of volume strings are considered to be valid if either one is valid.
   * This applies to range strings too, though it does not indicate whether the
   * ranges themselves are valid.
   * @param first a volume string
   * @param second another volume string
   * @return <code>true</code> if either string is valid
   */
  static final boolean areVolumeStringsValid(String first, String second) {
    return isVolumeStringValid(first) || isVolumeStringValid(second);
  }

  /**
   * Checks whether two integer volumes are strictly consecutive, that is, the
   * second is one greater than the first.
   *
   * @param first an integer representing the first volume
   * @param second an integer representing the subsequent volume
   * @return true if the second volume is one greater than the first
   */
  static final boolean areVolumesConsecutive(int first, int second) {
    return NumberUtil.areConsecutive(first, second);
  }

  /**
   * Two string-based volume identifiers are considered consecutive based on a
   * simple test - if the final numerical token of the second string increments
   * in value while the rest of the string remains the same as in the first,
   * we consider them consecutive. For example, "s1-4" and "s1-5" are
   * considered consecutive (examples taken from Journal of Orthopaedic Surgery).
   * <p>
   * This is a simplistic test but anything more complicated would likely
   * result in false positives. It also is not clever enough to recognise that,
   * for example, "s1-12" and "s2-1" might reasonably be construed as
   * consecutive, especially if they occur in the same or adjacent years.
   * <p>
   * We assume Arabic numbered suffixes only. Philip reports that Latin texts
   * that include Roman numbers in line tend to either parenthesize the number
   * or draw a bar over it. However if the match pattern is adapted to
   * recognise Roman numeral tokens, it should work the same because we use
   * {@link NumberUtil.areConsecutive(String, String)}.
   *
   * @param first a String representing the first volume
   * @param second a String representing the subsequent volume
   * @return whether the volumes appear to be consecutive
   */
  static final boolean areVolumesConsecutive(String first, String second) {
    // First try and compare as numbers
    try {
      return NumberUtil.areConsecutive(first, second);
    } catch (NumberFormatException e) {
      // fall through to the string-based comparison
    }
    Perl5Matcher matcher = RegexpUtil.getMatcher();
    MatchResult mr1, mr2;
    // Does the first vol string match
    if (matcher.contains(first, finalNumericalTokenPattern)) {
      mr1 = matcher.getMatch();
      // Does the second vol string match
      if (matcher.contains(second, finalNumericalTokenPattern)) {
        mr2 = matcher.getMatch();
        // If the outer tokens match, test the consecutivity of the number tokens
        return mr1.group(1).equals(mr2.group(1)) &&
            mr1.group(3).equals(mr2.group(3)) &&
            NumberUtil.areConsecutive(mr1.group(2), mr2.group(2));
      }
    }
    // At least one string doesn't match
    return false;
  }

  /**
   * Two TdbAus may be considered consecutive even if they share a volume
   * string, as long as the rest of their fields are also equivalent. This
   * is taken to indicate that the volumes are genuine duplicates supplied
   * by the publisher, because for example they have re-released on a new
   * platform, rather than accidental duplicates introduced through
   * errors in either the TDB or the publisher records.
   * @param first a TdbAu representing the first volume
   * @param second a TdbAu representing the subsequent volume
   * @return whether the volumes appear to be consecutive
   */
  static final boolean areVolumesConsecutiveDuplicates(TdbAu first, TdbAu second) {
    return KbartTdbAuUtil.areApparentlyEquivalent(first, second);
  }

  /**
   * A pair of numbers represents an increasing sequence if the second
   * is greater than or equal to the first.
   *
   * @param first the first integer in the pair
   * @param second the second integer in the pair
   * @return true if the difference between the integers is not less than 0
   */
  static final boolean areValuesIncreasing(int first, int second) {
    return second - first >= 0;
  }

  /**
   * A pair of integer values represents an increasing sequence if the second
   * integer is greater than or equal to the first. This version of the method
   * works on integers represented as strings, including Roman numerals.
   *
   * @param first a string representing the first integer
   * @param second a string representing the subsequent integer
   * @return true if the strings could be parsed and the difference between the integers is non-negative
   * @throws NumberFormatException if the Strings do not represent integers
   */
  static final boolean areValuesIncreasing(String first, String second)
      throws NumberFormatException {
    return areValuesIncreasing(NumberUtil.parseInt(first.trim()),
        NumberUtil.parseInt(second.trim()));
  }

  /**
   * A pair of integer values represents a decreasing sequence if the second
   * integer is less than the first. This version of the method
   * works on integers represented as strings, including Roman numerals.
   *
   * @param first a string representing the first integer
   * @param second a string representing the subsequent integer
   * @return true if the strings could be parsed and the second is less than the first
   * @throws NumberFormatException if the Strings do not represent integers
   */
  static final boolean areValuesDecreasing(String first, String second)
      throws NumberFormatException {
    return NumberUtil.parseInt(first.trim()) > NumberUtil.parseInt(second.trim());
  }

  /**
   * A pair of volume numbers represents an increasing sequence if the second
   * is greater than or equal to the first.
   *
   * @param first an integer representing the first volume
   * @param second an integer representing the subsequent volume
   * @return true if the difference between the volumes is not less than 0
   */
  static final boolean areVolumesIncreasing(int first, int second) {
    return areValuesIncreasing(first, second);
  }

  /**
   * Two string-based volume identifiers are considered to be increasing based
   * on a simple test - if the strings are the same except for the final
   * numerical token, and that token in the second string represents a greater
   * or equal value than that in the first, we consider them to be increasing.
   * For example, "s1-4" and "s1-15" are considered to be generally increasing.
   * <p>
   * We assume arabic numbered suffixes only. Philip reports that Latin texts
   * that include Roman numbers in line tend to either parenthesize the number
   * or draw a bar over it. However if the match pattern is adapted to
   * recognise Roman numeral tokens, it should work the same because we use
   * {@link NumberUtil.areIntegersConsecutive}.
   *
   * @param first a String representing the first volume
   * @param second a String representing the subsequent volume
   * @return whether the volumes appear to be monotonically increasing
   * @throws NumberFormatException if the Strings do not represent integers
   */
  static final boolean areVolumesIncreasing(String first, String second)
      throws NumberFormatException {
    // If the strings are equal, there's no need to tokenise
    if (first.equals(second)) return true;
    Perl5Matcher matcher = RegexpUtil.getMatcher();
    MatchResult mr1, mr2;
    // Does the first vol string match
    if (matcher.contains(first, finalNumericalTokenPattern)) {
      mr1 = matcher.getMatch();
      // Does the second vol string match
      if (matcher.contains(second, finalNumericalTokenPattern)) {
        mr2 = matcher.getMatch();
        // If the outer tokens match, test the consecutivity of the number tokens
        return mr1.group(1).equals(mr2.group(1)) &&
            mr1.group(3).equals(mr2.group(3)) &&
            areValuesIncreasing(mr1.group(2), mr2.group(2));
      }
    }
    // At least one string doesn't match
    return false;
  }

  /**
   * A pair of TdbAus represent the same (extended) volume if they
   * have the same value non-zero volume string along with appropriately
   * sequential year ranges.
   * @param first a TdbAu
   * @param second another TdbAu
   * @return whether the TdbAus seem to represent a volume extending over consecutive years
   * @throws NumberFormatException
   */
  static final boolean areExtendedVolume(TdbAu first, TdbAu second)
      throws NumberFormatException {
    String vol1 = first.getVolume();
    String vol2 = second.getVolume();
    // Same volume range (not 0), appropriately consecutive years
    return NumberUtil.areRangesEqual(vol1, vol2) && areVolumeStringsValid(vol1, vol2) &&
        SORT_FIELD.YEAR.areAppropriatelySequenced(first, second);
  }

  /**
   * A pair of years represents an increasing sequence if the second
   * year is greater than or equal to the first year.
   *
   * @param first an integer representing the first date
   * @param second an integer representing the subsequent date
   * @return true if the difference between the years is non-negative
   */
  static final boolean areYearsIncreasing(int first, int second) {
    return areValuesIncreasing(first, second);
  }

  /**
   * A pair of years represents an increasing sequence if the second
   * year is greater than or equal to the first year. This version
   * works on years represented as strings. This method does not accept
   * year range strings, but does accept Roman numerals.
   *
   * @param first a string representing the first date
   * @param second a string representing the subsequent date
   * @return true if the strings could be parsed and the difference between the years is non-negative
   * @throws NumberFormatException if the Strings do not represent integers
   */
  static final boolean areYearsIncreasing(String first, String second)
      throws NumberFormatException {
    return areYearsIncreasing(NumberUtil.parseInt(first.trim()),
        NumberUtil.parseInt(second.trim()));
  }

  /**
   * Checks whether a pair of year ranges appears to be in an order consistent
   * with a chronological ordering of volumes with no coverage gap. If a pair
   * of years or year ranges is <i>appropriately consecutive</i>, it suggests
   * that there is no coverage gap between them. If they are not
   * <i>appropriately consecutive</i> but they are <i>appropriately
   * sequential</i>, the volumes may be consulted to indicate whether or not
   * there is a coverage gap.
   * <p>
   * Note that the concept of <i>appropriate consecutivity</i> embodied here
   * is for establishing that TdbAus relating to a journal range are
   * reasonably ordered and 'consecutive enough' not to warrant the creation of
   * a coverage gap based on years. However there may be legitimate gaps
   * between consecutive volumes of greater than a year, and also multiple
   * volumes may get published within a single year - to assess this more
   * flexible criterion, the {@link areYearRangesAppropriatelySequenced()}
   * method should be used.
   * <p>
   * This method accounts for string-based years, representing either a single
   * year, or a year range in the form "1989-1991". For the purposes of
   * exposition, a single year A may be considered to be a year range of A to A.
   * In general, year ranges are considered to be appropriately consecutive if
   * there is some kind of overlap between the two ranges or if there is no
   * more than a year between them.
   * <blockquote>
   * <p>
   * A pair of year ranges are considered to be <i>appropriately consecutive</i>
   * if the second start year is greater than or equal to the first start year
   * while being at most one greater than the first end year (i.e. the start of
   * the second range follows immediately upon or is simultaneous with or is
   * within the first range) or if the first start year is included in the
   * range of the second (i.e. the start of the first range is within the
   * second). That is, it is <i>appropriately sequential</i> and there is no gap
   * between the end of the first range and the start of the second.
   * <br/>
   * <i>Note: perhaps we should also ensure that the first range's end year is
   * less than or equal to the second range's end year.</i>
   * </p>
   * </blockquote>
   * <p>
   * This rather flexible definition accounts for some oddities in the years
   * specified for certain journals - see for example:
   * <ul>
   * <li>Experimental Astronomy vols 3-5</li>
   * <li>Fresenius Zeitschrift f&uuml;r Analytische Chemie vols 275-277</li>
   * <li>Journal of Endocrinology vols 22-23</li>
   * <li>Proceedings of the Yorkshire Geological Society vols 10-11</li>
   * <li>Communication Disorders Quarterly vols 12-14</li>
   * </ul>
   * <p>
   * Even worse publication dates sometimes occur and will not be ratified by
   * this method; however luckily the journals involved do have consistent
   * volumes. For example:
   * <ul>
   * <li>Geological Society of London Memoirs vols 13-15</li>
   * <li>Geological Society of London Special Publications 287-289</li>
   * </ul>
   *
   * @param first a string representing a year or year range
   * @param second a string representing the subsequent year or year range
   * @return whether the year ranges may be considered to be appropriately consecutive
   * @throws NumberFormatException if the Strings do not represent year ranges or integers
   */
  static final boolean areYearRangesAppropriatelyConsecutive(String first,
                                                             String second)
      throws NumberFormatException {
    String e1 = NumberUtil.getRangeEnd(first);
    String s2 = NumberUtil.getRangeStart(second);
    // Appropriate sequencing, plus no significant gap between the ranges
    return areYearRangesAppropriatelySequenced(first, second) &&
        !isGapBetween(e1, s2);
  }

  /**
   * Check whether a pair of year ranges appears to be in an order consistent
   * with a chronological ordering of volumes. If each sequential pair of years
   * in a volume-ordered list of TdbAus is <i>appropriately sequential</i>,
   * it suggests that the ordering is correct. If they are <i>appropriately
   * sequential</i> but not <i>appropriately consecutive</i>, the volumes may
   * be consulted to indicate whether or not there is a coverage gap.
   * <p>
   * Note that the concept of <i>appropriate sequence</i> embodied here is
   * useful for establishing that TdbAus in an ordered list of volumes display
   * a reasonable ordering of years. However <i>it is not an appropriate
   * concept for use in deciding where coverage gaps should occur based on the
   * year field. For this application the more strict
   * {@link areYearRangesAppropriatelyConsecutive()} method should be used
   * to decide where there are temporal breaks.</i>
   * <p>
   * This method accounts for string-based years, representing either a single
   * year or a year range in the form "1989-1991". For the purposes of
   * exposition, a single year A may be considered to be a year range of A to A.
   * In general, year ranges are considered to be appropriately sequenced if
   * there is some kind of overlap between the two ranges or if the start of
   * the second range is later than the start of the first.
   * <blockquote>
   * <p>
   * A pair of year ranges are considered to be <i>appropriately sequential</i>
   * if the second start year is greater than or equal to the first start year
   * (i.e. the start of the second range follows or is simultaneous with or is
   * within the first range) or if the first start year is included in the
   * range of the second (i.e. start of first is within second).
   * <br/>
   * <i>Note: perhaps we should also ensure that the first range's end year is
   * less than or equal to the second range's end year.</i>
   * </p>
   * </blockquote>
   *
   * @param first a string representing a year or year range
   * @param second a string representing the subsequent year or year range
   * @return whether the year ranges may be considered to be appropriately sequenced
   * @throws NumberFormatException if the Strings do not represent year ranges or integers
   */
  static final boolean areYearRangesAppropriatelySequenced(String first,
                                                           String second)
      throws NumberFormatException {
    String s1 = NumberUtil.getRangeStart(first);
    String s2 = NumberUtil.getRangeStart(second);
    return areYearsIncreasing(s1, s2) || NumberUtil.rangeIncludes(second, s1);
    // Alternatively, check also that if the first range starts after the second,
    // it is entirely contained within the second: && NumberUtil.rangeIncludes(second, e1)
  }

  /**
   * Calculate the average proportion of breaks there are in a set of title ranges.
   * @param ranges a list of title ranges
   * @param fieldToCheck the field to analyse for breaks
   * @return a decimal value between 0 and 1
   */
  static final float countProportionOfBreaks(List<TitleRange> ranges,
                                             SORT_FIELD fieldToCheck) {
    float total = 0;
    for (TitleRange tr: ranges) {
      total += countProportionOfBreaksInRange(tr.tdbAus, fieldToCheck);
    }
    return total/ranges.size();
  }

  /**
   * Calculate the average proportion of redundancy in a set of title ranges.
   * This is only really useful across coverage ranges, for the whole range of
   * the title.
   * @param ranges a list of title ranges
   * @param fieldToCheck the field to analyse for redundancy
   * @return a decimal value between 0 and 1
   */
  static final float countProportionOfRedundancy(List<TitleRange> ranges,
                                                 SORT_FIELD fieldToCheck) {
    float total = 0;
    for (TitleRange tr: ranges) {
      total += countProportionOfRedundancyInRange(tr.tdbAus, fieldToCheck);
    }
    return total/ranges.size();
  }

  /**
   * Calculate the proportion of value pairs in the range that have a break
   * between them. Note that if this method is passed a sequence which
   * represents a gapless range, the result will be zero if
   * <code>fieldToCheck</code> is the primary field informing the ordering.
   *
   * @param aus a list of TdbAus
   * @param fieldToCheck the field to analyse for breaks
   * @return a decimal value between 0 and 1
   */
  static final float countProportionOfBreaksInRange(List<TdbAu> aus,
                                                    SORT_FIELD fieldToCheck) {
    int numPairs = aus.size() - 1;
    if (numPairs<1) return 0;
    int total = 0;
    for (int i=1; i<=numPairs; i++) {
      TdbAu lastAu = aus.get(i-1);
      TdbAu thisAu = aus.get(i);
      // If there is not a full set of field values, return a high value
      if (!fieldToCheck.hasValue(lastAu) || !fieldToCheck.hasValue(thisAu))
        return 1f;
      // Compare the values to see if there is a break.
      if (!fieldToCheck.areAppropriatelyConsecutive(lastAu, thisAu)) {
        total++;
      }
    }
    return (float)total/(float)numPairs;
  }

  /**
   * Calculate the proportion of year pairs in the range that have a break
   * between them which is not also apparent in the volume field. When
   * there is a parallel break in volumes, the break is less likely to indicate
   * a problem in the years. By contrast, any break in volume is considered a
   * coverage gap.
   *
   * @param aus a list of TdbAus
   * @return a decimal value between 0 and 1
   */
  static final float countProportionOfUniquelyYearBreaks(List<TdbAu> aus) {
    SORT_FIELD yearField = SORT_FIELD.YEAR;
    int numPairs = aus.size() - 1;
    if (numPairs<1) return 0;
    int total = 0;
    for (int i=1; i<=numPairs; i++) {
      TdbAu lastAu = aus.get(i-1);
      TdbAu thisAu = aus.get(i);
      // If there is not a full set of field values, return a high value
      if (!yearField.hasValue(lastAu) || !yearField.hasValue(thisAu))
        return 1f;
      // Compare the values to see if there is a break.
      boolean isYearBreak = !yearField.areAppropriatelyConsecutive(lastAu, thisAu);
      // Don't count a year break when there is a parallel break in the volume
      // field. This will only apply when counting a full unsplit sequence, and
      // should not occur when counting breaks in coverage ranges.
      if (isYearBreak) {
        // First check that volume fields are available for reference and not null.
        SORT_FIELD volField = SORT_FIELD.VOLUME;
        boolean isVolAvailable = volField.hasValue(lastAu) && volField.hasValue(thisAu);
        // Count a break if the volume info is not available, or if there is
        // no parallel break in volume
        if (!isVolAvailable || volField.areAppropriatelyConsecutive(lastAu, thisAu))
          total++;
      }
    }
    return (float)total/(float)numPairs;
  }

  /**
   * Calculate the proportion of value pairs in the range that have a break
   * between them which represents decreasing values. Note that breaks are
   * identified using the {@link SORT_FIELD.areAppropriatelyConsecutive()}
   * method, and descending year values may actually be appropriately
   * consecutive. To count towards the proportion, years must be both
   * non-consecutive by that measure, and also decreasing.
   *
   * @param aus a list of TdbAus
   * @param fieldToCheck the field to analyse for breaks
   * @return a decimal value between 0 and 1
   */
  static final float countProportionOfNegativeBreaksInRange(List<TdbAu> aus,
      SORT_FIELD fieldToCheck) {
    int numPairs = aus.size() - 1;
    if (numPairs<1) return 0;
    int total = 0;
    for (int i=1; i<=numPairs; i++) {
      TdbAu lastAu = aus.get(i-1);
      TdbAu thisAu = aus.get(i);
      String lastVal = fieldToCheck.getValueForComparisonAsPrevious(lastAu);
      String thisVal = fieldToCheck.getValueForComparisonAsCurrent(thisAu);
      // If there is not a full set of field values, return a high value
      if (lastVal==null || thisVal==null) return 1f;
      // Record a negative break
      if (fieldToCheck.areDecreasing(lastVal, thisVal)) {
        total++;
      }
    }
    return (float)total/(float)numPairs;
  }

  /**
   * How much redundancy there is in the values of the range. <s>For volumes,
   * which should not have duplicates (but do), this is the proportion of values
   * which are repeated. For years, which can have duplicates that should be
   * consecutive, this is the proportion of values which duplicate earlier
   * values but not their preceding value.</s>
   * <p>
   * Duplicates can appear in both year and volume fields. For volumes, this is
   * mostly because of duplicate TDB records due to something like a publisher
   * moving to a different platform. It should not (but does) occur within the
   * volume fields of a single journal run. For this reason, a duplicate value
   * is not counted if it duplicates only the preceding value.
   *
   * @param aus a list of TdbAus
   * @param fieldToCheck the field to analyse for redundancy
   * @return a decimal value between 0 and 1
   */
  static final float countProportionOfRedundancyInRange(List<TdbAu> aus,
                                                        SORT_FIELD fieldToCheck) {
    int numVals = aus.size();
    if (numVals<2) return 0;
    Set<String> uniqueVals = new HashSet<String>();
    String lastValue = null;
    int redundantEntries = 0;
    for (TdbAu au : aus) {
      // If there is not a full set of field values, return a high value
      if (!fieldToCheck.hasValue(au)) return 1f;
      String value = fieldToCheck.getValue(au);
      // If the value is a duplicate and different from the last value, record it
      if (!uniqueVals.add(value) && !value.equals(lastValue)) redundantEntries++;
      lastValue = value;
    }
    return (float)redundantEntries / (float)numVals;
  }

  /**
   * Determine whether a list of TdbAus is ordered in such a way that the
   * sequence of values in the specified field is monotonically increasing,
   * that is, the consecutive values do not decrease at any point. Note that
   * the values of some fields change format over the course of a publication;
   * the primary and most obvious example is switching between a purely numeric
   * and a string format. For this reason, a switch of formats between the
   * pairs is allowed.
   *
   * @param aus a list of TdbAus
   * @param fieldToCheck the field to check for monotonic increase
   * @return whether the list of TdbAus shows monotonic increase in the specified value
   */
  static final boolean isMonotonicallyIncreasing(List<TdbAu> aus, SORT_FIELD fieldToCheck) {
    int numPairs = aus.size() - 1;
    if (numPairs<1) return true; // Uninterestingly true
    String thisVal, lastVal;
    for (int i=1; i<=numPairs; i++) {
      lastVal = fieldToCheck.getValueForComparisonAsPrevious(aus.get(i-1));
      thisVal = fieldToCheck.getValueForComparisonAsCurrent(aus.get(i));
      if (!fieldToCheck.areIncreasing(lastVal, thisVal)) {
        // Check if there is a change of formats
        if (changeOfFormats(lastVal, thisVal)) {
          log.warning(String.format("Ignoring change of formats in %s: %s %s\n",
              fieldToCheck, lastVal, thisVal));
          continue;
        }
        return false;
      }
    }
    return true;
  }

  /**
   * Check whether the supplied strings indicate a change of format in their
   * field; this is inevitably a fuzzy concept, and for the moment we check
   * for the very simple distinction between digit-numerical and
   * non-numerical strings. A numerical string is one that represents an integer
   * with either digits or Roman numerals. Note that digit-numerical and
   * non-numerical are not inverse concepts.
   * <p>
   * A change between Roman numerals and Arabic numbers is not considered a
   * change of formats. Strings that contain Roman numerals can be interpreted
   * as either integers or strings, so if either <code>lastVal</code> or
   * <code>thisVal</code> can be parsed as a Roman numeral, there is no change
   * of formats regardless of the other value. It is not possible to say
   * strictly whether a particular string that meets
   * {@link NumberUtil.isRomanNumeral()} is intended as an integer or a string,
   * so we allow it to meet either criterion.
   * <p>
   * In future it might be desirable to use a more complex measure
   * of similarity or difference, for example Levenstein distance or a regexp
   * and tokenisation. (For example if the formats are both string, test that
   * there is a common substring at the start.)
   *
   * @param lastVal the last value
   * @param thisVal the current value
   * @return true if one string contains only digits while the other is not parseable as a number
   */
  static final boolean changeOfFormats(String lastVal, String thisVal) {
    // If either is a Roman number, we can't be sure there is a change
    if (NumberUtil.isRomanNumber(lastVal) || NumberUtil.isRomanNumber(thisVal))
      return false;
    // TODO It might be better to only allow normalised Roman numbers
    // using NumberUtil.parseRomanNumber(s, true);

    // Are these strings digit-numerical and/or numerical?
    boolean i1 = NumberUtil.isInteger(lastVal);
    boolean i2 = NumberUtil.isInteger(thisVal);
    boolean n1 = NumberUtil.isNumber(lastVal);
    boolean n2 = NumberUtil.isNumber(thisVal);
    // If one is digit-numerical and the other is non-numerical,
    // there is a definite change of formats.
    return (i1 && !n2) || (!n1 && i2);
  }

  /**
   * Uses a rough heuristic to calculate a consistency value for
   * the sequence of start years provided by the given list of AUs.
   * Contributing measures include the proportion of redundancy,
   * the number of breaks which are unique to the year field, and
   * the number of negative breaks.
   *
   * @param aus a list of TdbAus describing a full ordered sequence
   * @return a consistency rating for the sequence of values in the year field
   */
  static final float getYearListConsistency(List<TdbAu> aus) {
    // Combine redundancy, breaks, etc to produce a measure
    float red = countProportionOfRedundancyInRange(aus, SORT_FIELD.YEAR);
    // For years in a sequence, we only count breaks which occur uniquely in the
    // year field; if there is a volume break too it is not considered a problem.
    // Positive year breaks are expected in a list of AUs that has not been split
    // on coverage gaps.
    float brk = countProportionOfUniquelyYearBreaks(aus);
    float negbrk = countProportionOfNegativeBreaksInRange(aus, SORT_FIELD.YEAR);
    return (1-red) * (1-brk) * (1-negbrk);
  }

  /**
   * Uses a rough heuristic to calculate a consistency value for
   * the sequence of volumes provided by the given list of AUs.
   * Contributing measures include the proportion of redundancy,
   * the number of breaks, and the number of negative breaks.
   *
   * @param aus a list of TdbAus describing a full ordered sequence
   * @return a consistency rating for the sequence of values in the volume field
   */
  static final float getVolumeListConsistency(List<TdbAu> aus) {
    // For volumes in a sequence, although there should be no duplication,
    // we do sometimes see it because there are two copies of an AU out there,
    // one 'released' and one 'down'. These will get interleaved in the AU
    // ordering, and so we accept redundancy between consecutive volumes.
    float red = countProportionOfRedundancyInRange(aus, SORT_FIELD.VOLUME);
    float brk = countProportionOfBreaksInRange(aus, SORT_FIELD.VOLUME);
    float negbrk = countProportionOfNegativeBreaksInRange(aus, SORT_FIELD.VOLUME);
    return (1-red) * (1-brk) * (1-negbrk);
  }

  /**
   * Get a consistency score for the years in a set of coverage ranges, which
   * were calculated based on a particular ordering.
   * <p>
   * Contributing measures include the proportion of redundancy, the proportion
   * of breaks, and the proportion of negative breaks. These are calculated for
   * each range and then averaged. Finally a discount is applied based on the
   * frequency of coverage gaps (ranges) in the full sequence. In general,
   * a good ordering should produce less coverage gaps. Note that we do not
   * use {@link countProportionOfUniquelyYearBreaks()} in this measure, so all
   * year breaks in a specific coverage range detract from the value of the
   * year ordering.
   *
   * @param ranges a list of ranges calculated from an ordering
   * @return a consistency score for the calculated ranges
   */
  static final float getYearRangeConsistency(List<TitleRange> ranges) {
    SORT_FIELD sf = SORT_FIELD.YEAR;
    float totalRed = 0, totalBrk = 0, totalAus = 0, totalNegbrk = 0;
    for (TitleRange rng : ranges) {
      float red = countProportionOfRedundancyInRange(rng.tdbAus, sf);
      float brk = countProportionOfBreaksInRange(rng.tdbAus, sf);
      float negbrk = countProportionOfNegativeBreaksInRange(rng.tdbAus, sf);
      totalRed += red;
      totalBrk += brk;
      totalNegbrk += negbrk;
      totalAus += rng.tdbAus.size();
    }
    float numRanges = (float)ranges.size();
    // Discount for large number of ranges - prefer less ranges
    float gfs = getCoverageGapFrequencyDiscount((float)totalAus, numRanges);
    // Average the redundancy and break scores
    float red = totalRed/numRanges;
    float brk = totalBrk/numRanges;
    float negbrk = totalNegbrk/numRanges;
    return (1-red) * (1-brk) * (1-gfs) * (1-negbrk);
  }

  /**
   * Get a consistency score for the volumes in a set of coverage ranges, which
   * were calculated based on a particular ordering.
   * <p>
   * Contributing measures include the proportion of redundancy, the proportion
   * of breaks, and the proportion of negative breaks. These are calculated for
   * each range and then averaged. Finally a discount is applied based on the
   * frequency of coverage gaps (ranges) in the full sequence. In general,
   * a good ordering should produce less coverage gaps.
   * <p>
   * Contributing measures include the proportion of redundancy, the number of
   * breaks, and the number of negative breaks. These are calculated for each
   * range and then averaged. Finally a discount is applied based on the
   * frequency of coverage gaps (ranges) in the full sequence. In general,
   * a good ordering should produce less coverage gaps.
   *
   * @param ranges a list of ranges calculated from an ordering
   * @return a consistency score for the calculated rangesx
   */
  static final float getVolumeRangeConsistency(List<TitleRange> ranges) {
    SORT_FIELD sf = SORT_FIELD.VOLUME;
    float totalRed = 0, totalBrk = 0, totalAus = 0, totalNegbrk = 0;
    for (TitleRange rng : ranges) {
      float red = countProportionOfRedundancyInRange(rng.tdbAus, sf);
      float brk = countProportionOfBreaksInRange(rng.tdbAus, sf);
      float negbrk = countProportionOfNegativeBreaksInRange(rng.tdbAus, sf);
      totalRed += red;
      totalBrk += brk;
      totalNegbrk += negbrk;
      totalAus += rng.tdbAus.size();
    }
    float numRanges = (float)ranges.size();
    // Discount for large number of ranges - prefer less ranges
    float gfs = getCoverageGapFrequencyDiscount((float)totalAus, numRanges);
    // Average the redundancy and break scores
    float red = totalRed/numRanges;
    float brk = totalBrk/numRanges;
    float negbrk = totalNegbrk/numRanges;
    return (1-red) * (1-brk) * (1-gfs) * (1-negbrk);
  }

  /**
   * The more frequently coverage gaps occur, the lower the score. Sometimes
   * there are genuinely lots of coverage gaps, but in general we prefer an
   * ordering which produces fewer gaps.
   *
   * @param numAus how many TdbAus are in the sequence
   * @param numRanges how many ranges are in the sequence; should be less than or equal to <code>numAus</code>
   * @return a floating point multiplier between 0 and 1 - 1/numAus which is greater the less gaps there are
   */
  static final float getCoverageGapFrequencyDiscount(float numAus, float numRanges) {
    if (numAus==0f) return 0f;
    return (numRanges - 1) / numAus;
  }

  /**
   * Calculate a consistency score for the given list of calculated ranges.
   * @param aus the full list of aus ordered by the sortField
   * @param rangesByVol an ordered list of title ranges derived from an ordering
   * @return a score between 0 and 1
   */
  static final ConsistencyScore getConsistencyScore(List<TdbAu> aus,
      					 List<KbartConverter.TitleRange> rangesByVol) {
    float volScore = getVolumeRangeConsistency(rangesByVol);
    float yearScore = getYearRangeConsistency(rangesByVol);
    float volListScore  = TdbAuOrderScorer.getVolumeListConsistency(aus);
    float yearListScore = TdbAuOrderScorer.getYearListConsistency(aus);
    return new ConsistencyScore(volScore, yearScore, volListScore, yearListScore);
  }

  /**
   * Look at the scores and decide whether the volume ordering yields a net
   * benefit over the year scores and should be preferred.
   * @param volScore  the ConsistencyScore for volume ordering
   * @param yearScore the ConsistencyScore for year ordering
   * @return whether to prefer the volume ordering
   */
  static final boolean preferVolume(ConsistencyScore volScore,
                                    ConsistencyScore yearScore) {
    // Use the volume ordering if the benefit of using it outweighs
    // the loss to year orderings
    return calculateRelativeBenefitToVolume(volScore, yearScore) >=
        calculateRelativeLossToYear(volScore, yearScore);
  }

  /**
   * Calculate the relative benefit to volume orderings of the first ordering
   * over the second ordering. This is the increase in the aggregated volume
   * score plus the increase in the volume score for the full sequence.
   * The return value will be negative, zero or positive as the first
   * consistency score represents relative deficit, equality or benefit to
   * the second.
   * @param firstScore  the ConsistencyScore for the first ordering
   * @param secondScore the ConsistencyScore for the second ordering
   * @return the relative benefit to volume sequences of using the first ordering
   */
  static final float calculateRelativeBenefitToVolume(ConsistencyScore firstScore,
                                                      ConsistencyScore secondScore) {
    // Relative benefit of first ordering to volume coverage ranges
    float firstOrdVolBen = firstScore.volScore - secondScore.volScore;
    // Relative benefit of first ordering to full ordered volume sequence
    float firstOrdVolListBen = firstScore.volListScore - secondScore.volListScore;
    // Benefit to volume sequence and ranges of using the first ordering
    return firstOrdVolBen + firstOrdVolListBen;
  }

  /**
   * Calculate the relative loss to year orderings of the first ordering
   * over the second ordering. This is the decrease in the aggregated year
   * score plus the decrease in the year score for the full sequence.
   * The return value will be negative, zero or positive as the first
   * consistency score represents relative benefit, equality or deficit/loss to
   * the second. Note that a positive result indicates loss.
   * @param firstScore  the ConsistencyScore for the first ordering
   * @param secondScore the ConsistencyScore for the second ordering
   * @return the relative loss to year sequences of using the first ordering
   */
  static final float calculateRelativeLossToYear(ConsistencyScore firstScore,
                                                 ConsistencyScore secondScore) {
    // Relative loss of first ordering to year coverage ranges
    float firstOrdYearLoss = secondScore.yearScore - firstScore.yearScore;
    // Relative loss of first ordering to full ordered year sequence
    float firstOrdYearListLoss = secondScore.yearListScore - firstScore.yearListScore;
   // Benefit to year sequence and ranges of using the first ordering
    return firstOrdYearLoss + firstOrdYearListLoss;

    // Note that the loss of using first over second is equal to the benefit
    // of using the second over the first, in case that is more intelligible.
    // Relative benefit of second ordering to year coverage ranges
    //float secondOrdYearBen = firstScore.yearScore - secondScore.yearScore;
    // Relative benefit of second ordering to full ordered year sequence
    //float secondOrdYearListBen = firstScore.yearListScore - secondScore.yearListScore;
    //return secondOrdYearBen + secondOrdYearListBen;
  }

}
