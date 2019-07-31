/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.backports.jira;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class Parsers {

    public static int parseVersion(String version) {
        if (version.equals("solaris_10u7")) {
            // Special-case odd issue: https://bugs.openjdk.java.net/browse/JDK-6913047
            return 0;
        }

        version = version.toLowerCase();

        if (version.startsWith("openjdk")) {
            version = version.substring("openjdk".length());
        }

        if (version.endsWith("shenandoah")) {
            return -1;
        }

        if (version.endsWith("-pool")) {
            int dashIdx = version.lastIndexOf("-");
            try {
                return Integer.parseInt(version.substring(0, dashIdx));
            } catch (Exception e) {
                return -1;
            }
        }

        int dotIdx = version.indexOf(".");
        if (dotIdx != -1) {
            try {
                return Integer.parseInt(version.substring(0, dotIdx));
            } catch (Exception e) {
                return -1;
            }
        }
        int uIdx = version.indexOf("u");
        if (uIdx != -1) {
            try {
                return Integer.parseInt(version.substring(0, uIdx));
            } catch (Exception e) {
                return -1;
            }
        }

        try {
            return Integer.parseInt(version);
        } catch (Exception e) {
            return -1;
        }
    }

    public static int parseVersionShenandoah(String version) {
        if (!version.endsWith("-shenandoah")) {
            return -1;
        }

        version = version.substring(0, version.indexOf("shenandoah") - 1);
        try {
            return Integer.parseInt(version);
        } catch (Exception e) {
            return -1;
        }
    }

    public static int parseVersionAArch64(String version) {
        if (!version.endsWith("-aarch64")) {
            return -1;
        }

        version = version.substring(0, version.indexOf("aarch64") - 1);
        try {
            return Integer.parseInt(version);
        } catch (Exception e) {
            return -1;
        }
    }

    public static String parseURL(String s) {
        for (String l : s.split("\n")) {
            if (l.startsWith("URL")) {
                return l.replaceFirst("URL:", "").trim();
            }
        }
        return "N/A";
    }

    public static String parseUser(String s) {
        for (String l : s.split("\n")) {
            if (l.startsWith("User")) {
                return l.replaceFirst("User:", "").trim();
            }
        }
        return "N/A";
    }

    public static long parseDaysAgo(String s) {
        for (String l : s.split("\n")) {
            if (l.startsWith("Date")) {
                String d = l.replaceFirst("Date:", "").trim();
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
                return ChronoUnit.DAYS.between(LocalDate.parse(d, formatter), LocalDate.now());
            }
        }
        return 0;
    }

    public static long parseSecondsAgo(String s) {
        for (String l : s.split("\n")) {
            if (l.startsWith("Date")) {
                String d = l.replaceFirst("Date:", "").trim();
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
                return ChronoUnit.SECONDS.between(LocalDateTime.parse(d, formatter), LocalDateTime.now());
            }
        }
        return 0;
    }

}
