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
package org.openjdk.backports.report;

import com.atlassian.jira.rest.client.api.domain.Issue;

import java.util.Comparator;

public class Common {

    // JDK versions we care for in backports
    protected static final int[] VERSIONS_TO_CARE_FOR = {8, 11, 17};
    protected static final int[] VERSIONS_TO_CARE_FOR_REV = {17, 11, 8};

    // Issue bake time before backport is considered
    protected static final int ISSUE_BAKE_TIME_DAYS = 10;

    // Backport importances
    protected static int importanceMerge() {
        return 5;
    }

    protected static int importanceDefault(int release) {
        switch (release) {
            case 7:
            case 8:
            case 11:
            case 17:
                return 10;
            case 13:
            case 15:
                return 1;
            default:
                return 1;
        }
    }

    protected static int importanceCritical(int release) {
        switch (release) {
            case 7:
            case 8:
            case 11:
            case 17:
                return 50;
            case 13:
            case 15:
                return 20;
            default:
                return 15;
        }
    }

    protected static int importanceOracle(int release) {
        switch (release) {
            case 7:
            case 8:
            case 11:
            case 17:
                return 30;
            case 13:
            case 15:
                return 10;
            default:
                return 5;
        }
    }

    // Sort issues by synopsis, alphabetically. This would cluster similar issues
    // together, even when they are separated by large difference in IDs.
    protected static final Comparator<Issue> DEFAULT_ISSUE_SORT = Comparator.comparing(i -> i.getSummary().trim().toLowerCase());

    protected static String statusToText(BackportStatus status) {
        switch (status) {
            case NOT_AFFECTED:
                return "Not affected";
            case INHERITED:
                return "Inherited";
            case FIXED:
                return "Fixed";
            case BAKING:
                return "WAITING for patch to bake a little";
            case MISSING:
                return "MISSING";
            case MISSING_ORACLE:
                return "MISSING (+ on Oracle backport list)";
            case APPROVED:
                return "APPROVED";
            case REJECTED:
                return "Rejected";
            case REQUESTED:
                return "Requested";
            case WARNING:
                return "WARNING";
            default:
                throw new IllegalStateException("Unknown status: " + status);
        }
    }

}
