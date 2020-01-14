package org.openjdk.backports;

import org.junit.Assert;
import org.junit.Test;
import org.openjdk.backports.jira.Parsers;

public class ParserTest {

    static final String SAMPLE_COMMENT = "URL: http://hg.openjdk.java.net/jdk/jdk/rev/66f5241da404\n" +
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
