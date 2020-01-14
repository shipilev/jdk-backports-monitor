package org.openjdk.backports;

import org.junit.Assert;
import org.junit.Test;
import org.openjdk.backports.jira.Parsers;
import org.openjdk.backports.jira.Versions;

public class VersionsTest {

    @Test
    public void testParseVersion() {
        Assert.assertEquals(8,  Versions.parseMajorShenandoah("8-shenandoah"));
        Assert.assertEquals(11, Versions.parseMajorShenandoah("11-shenandoah"));
        Assert.assertEquals(-1, Versions.parseMajorAArch64("8-shenandoah"));
        Assert.assertEquals(-1, Versions.parseMajorAArch64("11-shenandoah"));
        Assert.assertEquals(-1, Versions.parseMajor("8-shenandoah"));
        Assert.assertEquals(-1, Versions.parseMajor("11-shenandoah"));

        Assert.assertEquals(-1, Versions.parseMajorShenandoah("8-aarch64"));
        Assert.assertEquals(8,  Versions.parseMajorAArch64("8-aarch64"));
        Assert.assertEquals(-1, Versions.parseMajor("8-aarch64"));

        Assert.assertEquals(10, Versions.parseMajor("10"));
        Assert.assertEquals(10, Versions.parseMajor("10.0.2"));
        Assert.assertEquals(10, Versions.parseMajor("10u-open"));
        Assert.assertEquals(10, Versions.parseMajor("10-pool"));

        Assert.assertEquals(11, Versions.parseMajor("11"));
        Assert.assertEquals(11, Versions.parseMajor("11.0.3"));
        Assert.assertEquals(11, Versions.parseMajor("11.0.3-oracle"));
        Assert.assertEquals(11, Versions.parseMajor("11-pool"));

        Assert.assertEquals(12, Versions.parseMajor("12"));
        Assert.assertEquals(12, Versions.parseMajor("12.0.2"));
        Assert.assertEquals(12, Versions.parseMajor("12-pool"));

        Assert.assertEquals(13, Versions.parseMajor("13"));
        Assert.assertEquals(13, Versions.parseMajor("13.0.1"));
        Assert.assertEquals(13, Versions.parseMajor("13-pool"));

        Assert.assertEquals(14, Versions.parseMajor("14"));
        Assert.assertEquals(14, Versions.parseMajor("14-pool"));

        Assert.assertEquals(15, Versions.parseMajor("15"));
        Assert.assertEquals(15, Versions.parseMajor("15-pool"));

        Assert.assertEquals(7, Versions.parseMajor("7"));
        Assert.assertEquals(7, Versions.parseMajor("7u40"));
        Assert.assertEquals(7, Versions.parseMajor("7u231"));

        Assert.assertEquals(6, Versions.parseMajor("6"));

        Assert.assertEquals(8, Versions.parseMajor("8"));
        Assert.assertEquals(8, Versions.parseMajor("8u40"));
        Assert.assertEquals(8, Versions.parseMajor("8u111"));
        Assert.assertEquals(8, Versions.parseMajor("8-pool"));

        Assert.assertEquals(9, Versions.parseMajor("9"));
        Assert.assertEquals(9, Versions.parseMajor("9.0.4"));
        Assert.assertEquals(9, Versions.parseMajor("9-pool"));

        Assert.assertEquals(6, Versions.parseMajor("OpenJDK6"));
        Assert.assertEquals(7, Versions.parseMajor("openjdk7u"));
        Assert.assertEquals(8, Versions.parseMajor("openjdk8u"));
        Assert.assertEquals(8, Versions.parseMajor("openjdk8u212"));

        Assert.assertEquals(0, Versions.parseMajor("solaris_10u7"));
    }

    @Test
    public void testParseSubversion() {
        Assert.assertEquals(-1,  Versions.parseMinor("8-shenandoah"));
        Assert.assertEquals(-1,  Versions.parseMinor("11-shenandoah"));
        Assert.assertEquals(-1,  Versions.parseMinor("8-aarch64"));
        Assert.assertEquals(-1,  Versions.parseMinor("11.0.3"));

        Assert.assertEquals(-1,  Versions.parseMinor("7"));
        Assert.assertEquals(40,  Versions.parseMinor("7u40"));
        Assert.assertEquals(231, Versions.parseMinor("7u231"));

        Assert.assertEquals(-1,  Versions.parseMinor("8"));
        Assert.assertEquals(40,  Versions.parseMinor("8u40"));
        Assert.assertEquals(111, Versions.parseMinor("8u111"));
        Assert.assertEquals(-1,  Versions.parseMinor("8-pool"));

        Assert.assertEquals(-1,  Versions.parseMinor("OpenJDK6"));
        Assert.assertEquals(-1,  Versions.parseMinor("openjdk7u"));
        Assert.assertEquals(-1,  Versions.parseMinor("openjdk8u"));
        Assert.assertEquals(-1,  Versions.parseMinor("openjdk8u212"));
    }

    static String SAMPLE_COMMENT = "URL: http://hg.openjdk.java.net/jdk/jdk/rev/66f5241da404\n" +
            "User: shade\n" +
            "Date: 2019-04-15 16:22:25 +0000";

    @Test
    public void parseURL() {
        Assert.assertEquals(
                "http://hg.openjdk.java.net/jdk/jdk/rev/66f5241da404",
                Parsers.parseURL(SAMPLE_COMMENT)
        );
    }

    @Test
    public void parseUser() {
        Assert.assertEquals(
                "shade",
                Parsers.parseUser(SAMPLE_COMMENT)
        );
    }

    @Test
    public void parseDayAgo() {
        Assert.assertTrue(Parsers.parseDaysAgo(SAMPLE_COMMENT) > 0);
    }

    @Test
    public void parseSecondsAgo() {
        Assert.assertTrue(Parsers.parseSecondsAgo(SAMPLE_COMMENT) > 0);
    }

}
