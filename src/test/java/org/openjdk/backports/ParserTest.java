package org.openjdk.backports;

import org.junit.Assert;
import org.junit.Test;
import org.openjdk.backports.jira.Parsers;

public class ParserTest {

    static final String SAMPLE_COMMENT_OLD = "URL: http://hg.openjdk.java.net/jdk/jdk/rev/66f5241da404\n" +
            "User: shade\n" +
            "Date: 2019-04-15 16:22:25 +0000";

    static final String SAMPLE_COMMENT = "Changeset: d9cb410e\n" +
            "Author: Aleksey Shipilev <shade@openjdk.org>\n" +
            "Date: 2022-07-31 18:52:16 +0000\n" +
            "URL: https://git.openjdk.org/jdk/commit/d9cb410efc07a60e426f2399e020dcaccaba7dfa";

    @Test
    public void parseURL() {
        Assert.assertEquals(
                "http://hg.openjdk.java.net/jdk/jdk/rev/66f5241da404",
                Parsers.parseURL(SAMPLE_COMMENT_OLD).get()
        );
        Assert.assertEquals(
                "https://git.openjdk.org/jdk/commit/d9cb410efc07a60e426f2399e020dcaccaba7dfa",
                Parsers.parseURL(SAMPLE_COMMENT).get()
        );
    }

    @Test
    public void parseUser() {
        Assert.assertEquals(
                "shade",
                Parsers.parseUser(SAMPLE_COMMENT_OLD).get()
        );
        Assert.assertEquals(
                "shade",
                Parsers.parseUser(SAMPLE_COMMENT).get()
        );
    }

    @Test
    public void parseDayAgo() {
        Assert.assertTrue(Parsers.parseDaysAgo(SAMPLE_COMMENT_OLD).isPresent());
        Assert.assertTrue(Parsers.parseDaysAgo(SAMPLE_COMMENT).isPresent());
    }

    @Test
    public void parseSecondsAgo() {
        Assert.assertTrue(Parsers.parseSecondsAgo(SAMPLE_COMMENT_OLD).isPresent());
        Assert.assertTrue(Parsers.parseSecondsAgo(SAMPLE_COMMENT).isPresent());
    }

    @Test
    public void parsePriority() {
        Assert.assertEquals(1, Parsers.parsePriority("P1"));
        Assert.assertEquals(2, Parsers.parsePriority("P2"));
        Assert.assertEquals(3, Parsers.parsePriority("P3"));
        Assert.assertEquals(4, Parsers.parsePriority("P4"));
        Assert.assertEquals(5, Parsers.parsePriority("P5"));
        Assert.assertEquals(-1, Parsers.parsePriority("P"));
        Assert.assertEquals(-1, Parsers.parsePriority("Px"));
        Assert.assertEquals(-1, Parsers.parsePriority(""));
    }

}
