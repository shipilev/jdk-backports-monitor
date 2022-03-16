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

import org.openjdk.backports.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parsers {

    static final Pattern OPENJDK_USER_ID = Pattern.compile("(.*)<(.*)@openjdk.org>");
    static final Pattern GENERIC_USER_ID = Pattern.compile("(.*)<(.*)>");

    public static Optional<String> parseURL(String s) {
        for (String l : StringUtils.lines(s)) {
            if (l.startsWith("URL")) {
                return Optional.of(l.replaceFirst("URL:", "").trim());
            }
        }
        return Optional.empty();
    }

    public static Optional<String> parseUser(String s) {
        for (String l : StringUtils.lines(s)) {
            if (l.startsWith("User")) {
                return Optional.of(l.replaceFirst("User:", "").trim());
            }
            if (l.startsWith("Author")) {
                Matcher m1 = OPENJDK_USER_ID.matcher(l);
                if (m1.matches()) {
                    return Optional.of(m1.group(2));
                }
                Matcher m2 = GENERIC_USER_ID.matcher(l);
                if (m2.matches()) {
                    return Optional.of(m2.group(2));
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<Long> parseDaysAgo(String s) {
        for (String l : StringUtils.lines(s)) {
            if (l.startsWith("Date")) {
                String d = l.replaceFirst("Date:", "").trim();
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
                return Optional.of(ChronoUnit.DAYS.between(LocalDate.parse(d, formatter), LocalDate.now()));
            }
        }
        return Optional.empty();
    }

    public static Optional<Long> parseSecondsAgo(String s) {
        for (String l : StringUtils.lines(s)) {
            if (l.startsWith("Date")) {
                String d = l.replaceFirst("Date:", "").trim();
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
                return Optional.of(ChronoUnit.SECONDS.between(LocalDateTime.parse(d, formatter), LocalDateTime.now()));
            }
        }
        return Optional.empty();
    }

    public static int parsePriority(String s) {
        if (s.length() != 2 && !s.startsWith("P")) {
            return -1;
        }

        try {
            return Integer.parseInt(s.substring(1));
        } catch (NumberFormatException nfe) {
            return -1;
        }
    }
}
