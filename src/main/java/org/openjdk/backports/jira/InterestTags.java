/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class InterestTags {

    public static String shortTags(Collection<String> labels) {
        List<String> tags = new ArrayList<>();
        for (String label : labels) {
            String tag = shortTagFor(label);
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }

        if (tags.isEmpty()) return "";

        Collections.sort(tags);

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (String tag : tags) {
            sb.append(tag);
        }
        sb.append("]");
        return sb.toString();
    }

    private static String shortTagFor(String label) {
        switch (label) {
            case "redhat-interest":
                return "R";
            case "sap-interest":
                return "S";
            case "amazon-interest":
                return "A";
            case "google-interest":
                return "G";
            case "azul-interest":
                return "a";
            case "jdk11u-jvmci-defer":
                return "C";
            default:
                if (label.endsWith("-interest")) {
                    return "?";
                } else {
                    return "";
                }
        }
    }
}
