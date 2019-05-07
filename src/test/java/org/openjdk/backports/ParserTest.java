package org.openjdk.backports;

import org.junit.Assert;
import org.junit.Test;

public class ParserTest {

    @Test
    public void testParseVersion() {
        Assert.assertEquals(8,  Parsers.parseVersionShenandoah("8-shenandoah"));
        Assert.assertEquals(11, Parsers.parseVersionShenandoah("11-shenandoah"));
        Assert.assertEquals(-1, Parsers.parseVersionAArch64("8-shenandoah"));
        Assert.assertEquals(-1, Parsers.parseVersionAArch64("11-shenandoah"));
        Assert.assertEquals(-1, Parsers.parseVersion("8-shenandoah"));
        Assert.assertEquals(-1, Parsers.parseVersion("11-shenandoah"));

        Assert.assertEquals(-1, Parsers.parseVersionShenandoah("8-aarch64"));
        Assert.assertEquals(8,  Parsers.parseVersionAArch64("8-aarch64"));
        Assert.assertEquals(-1, Parsers.parseVersion("8-aarch64"));

        Assert.assertEquals(10, Parsers.parseVersion("10"));
        Assert.assertEquals(10, Parsers.parseVersion("10.0.2"));
        Assert.assertEquals(10, Parsers.parseVersion("10u-open"));

        Assert.assertEquals(11, Parsers.parseVersion("11"));
        Assert.assertEquals(11, Parsers.parseVersion("11.0.3"));
        Assert.assertEquals(11, Parsers.parseVersion("11.0.3-oracle"));

        Assert.assertEquals(12, Parsers.parseVersion("12"));
        Assert.assertEquals(12, Parsers.parseVersion("12.0.2"));

        Assert.assertEquals(13, Parsers.parseVersion("13"));

        Assert.assertEquals(7, Parsers.parseVersion("7"));
        Assert.assertEquals(7, Parsers.parseVersion("7u40"));
        Assert.assertEquals(7, Parsers.parseVersion("7u231"));

        Assert.assertEquals(6, Parsers.parseVersion("6"));

        Assert.assertEquals(8, Parsers.parseVersion("8"));
        Assert.assertEquals(8, Parsers.parseVersion("8u40"));
        Assert.assertEquals(8, Parsers.parseVersion("8u111"));

        Assert.assertEquals(9, Parsers.parseVersion("9"));
        Assert.assertEquals(9, Parsers.parseVersion("9.0.4"));

        Assert.assertEquals(6, Parsers.parseVersion("OpenJDK6"));
        Assert.assertEquals(7, Parsers.parseVersion("openjdk7u"));
        Assert.assertEquals(8, Parsers.parseVersion("openjdk8u"));
        Assert.assertEquals(8, Parsers.parseVersion("openjdk8u212"));

        Assert.assertEquals(0, Parsers.parseVersion("solaris_10u7"));
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
