/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
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
package org.openjdk.backports;

import org.apache.commons.lang3.text.WordUtils;

public class StringUtils {

    public static final int DEFAULT_WIDTH = 100;

    public static String rewrap(String str, int width) {
        return rewrap(str, width, Integer.MAX_VALUE);
    }

    public static String rewrap(String str, int width, int maxParagraphs) {
        StringBuilder sb = new StringBuilder();

        int pCount = 0;
        for (String paragraph : paragraphs(str)) {
            if (pCount >= maxParagraphs) break;
            if (pCount != 0) {
                sb.append(System.lineSeparator());
                sb.append(System.lineSeparator());
            }
            paragraph = paragraph.replaceAll("\n", " ");
            sb.append(WordUtils.wrap(paragraph, width));
            pCount++;
        }
        return sb.toString();
    }

    public static String[] paragraphs(String str) {
        return str.replaceAll("\r", "").split("\n\n");
    }

    public static String leftPad(String str, int pad) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < pad; c++) {
            sb.append(" ");
        }
        String sPad = sb.toString();
        return sPad + str.replaceAll("\n", "\n" + sPad);
    }

    public static String stripNull(String v) {
        return (v != null) ? v : "";
    }

    public static String tabLine(char v) {
        return tabLine(v, DEFAULT_WIDTH);
    }

    public static String tabLine(char v, int width) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < width; c++) {
            sb.append(v);
        }
        return sb.toString();
    }

    public static String csvEscape(String s) {
        return "\"" + s.replaceAll("\\\"", "'") + "\"";
    }

    public static String cutoff(String s, int limit) {
        if (s == null) {
            return s;
        }
        return s.substring(0, Math.min(s.length(), limit));
    }

}
