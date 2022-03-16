package org.openjdk.backports;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class StringUtilsTest {

    @Test
    public void lines() {
        String[] examples = {
                "line1\rline2\rline3\r",
                "line1\nline2\nline3\n",
                "line1\r\nline2\r\nline3\r\n",
        };

        for (String ex : examples) {
            List<String> lines = StringUtils.lines(ex);
            Assert.assertEquals(3, lines.size());
            Assert.assertEquals("line1", lines.get(0));
            Assert.assertEquals("line2", lines.get(1));
            Assert.assertEquals("line3", lines.get(2));
        }
    }

}
