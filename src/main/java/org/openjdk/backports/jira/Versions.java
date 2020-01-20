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
package org.openjdk.backports.jira;

public class Versions {

    public static int parseMajor(String version) {
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

    public static int parseMinor(String version) {
        if (version.startsWith("openjdk7")) {
            return -1;
        }

        if (parseMajor(version) <= 8) {
            int uIdx = version.indexOf("u");
            if (uIdx != -1) {
                if (uIdx + 1 == version.length()) {
                    return 0;
                }
                try {
                    return Integer.parseInt(version.substring(uIdx + 1));
                } catch (Exception e) {
                    return -1;
                }
            }
        }

        if (parseMajor(version) >= 11) {
            String sub = stripVendor(version);
            int dotIdx = sub.lastIndexOf(".");
            if (dotIdx + 1 == version.length()) {
                return 0;
            }
            if (dotIdx != -1) {
                String[] args = sub.split("\\.");
                if (args.length < 3) {
                    return -1;
                }
                try {
                    return Integer.parseInt(args[2]);
                } catch (Exception e) {
                    return -1;
                }
            }
        }

        return -1;
    }

    public static int parseMajorShenandoah(String version) {
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

    public static int parseMajorAArch64(String version) {
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

    public static boolean isOracle(String version) {
        if (parseMajor(version) >= 11 && version.endsWith("-oracle")) {
            return true;
        }

        if (parseMajor(version) == 8 && parseMinor(version) >= 211 && !version.startsWith("openjdk")) {
            return true;
        }

        return false;
    }

    public static boolean isShared(String version) {
        if (isOracle(version)) {
            return false;
        }

        if (parseMajor(version) >= 11 && parseMinor(version) <= 2) {
            return true;
        }

        if (parseMajor(version) == 8 && parseMinor(version) < 211) {
            return true;
        }

        return false;
    }

    public static String stripVendor(String version) {
        if (version.endsWith("-oracle")) {
            return version.substring(0, version.indexOf("-oracle"));
        }

        if (version.startsWith("openjdk")) {
            return version.substring(7);
        }

        return version;
    }

    public static int compare(String left, String right) {
        String stripLeft = stripVendor(left);
        String stripRight = stripVendor(right);
        if (parseMajor(stripLeft) == 8 && parseMajor(stripRight) == 8) {
            if (Math.abs(parseMinor(stripLeft) - parseMinor(stripRight)) <= 1) {
                return 0;
            }
        }

        return stripLeft.compareTo(stripRight);
    }

}
