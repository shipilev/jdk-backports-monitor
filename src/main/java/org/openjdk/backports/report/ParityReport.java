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
package org.openjdk.backports.report;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.openjdk.backports.jira.Accessors;
import org.openjdk.backports.jira.Versions;

import java.time.LocalDateTime;
import java.util.*;

public class ParityReport extends AbstractReport {

    private final String in;

    public ParityReport(JiraRestClient restClient, String in) {
        super(restClient);
        this.in = in;
    }

    @Override
    public void run() {
        out.println("PARITY REPORT: " + in);
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows the bird-eye view of parity between OpenJDK and Oracle JDK.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        Multimap<Issue, Issue> mp = HashMultimap.create();

        for (String ver : in.split(",")) {
            List<Issue> backports = jiraIssues.getBasicIssues("project = JDK" +
                    " AND (status in (Closed, Resolved))" +
                    " AND (labels not in (release-note, openjdk-na) OR labels is EMPTY)" +
                    " AND (issuetype != CSR)" +
                    " AND (resolution not in (\"Won't Fix\", Duplicate, \"Cannot Reproduce\", \"Not an Issue\", Withdrawn, Other))" +
                    " AND fixVersion = " + ver);

            List<Issue> parents = jiraIssues.getParentIssues(backports);
            for (int i = 0; i < parents.size(); i++) {
                Issue parent = parents.get(i);
                Issue backport = backports.get(i);
                if (parent == null) parent = backport;
                if (!mp.containsEntry(parent, backport)) {
                    mp.put(parent, backport);
                }
            }

            out.println();
        }

        out.println("Discovered " + mp.size() + " issues.");
        out.println();

        SortedMap<Issue, String> onlyOpen = new TreeMap<>(DEFAULT_ISSUE_SORT);
        SortedMap<Issue, String> onlyOracle = new TreeMap<>(DEFAULT_ISSUE_SORT);
        SortedMap<Issue, String> exactOpenFirst = new TreeMap<>(DEFAULT_ISSUE_SORT);
        SortedMap<Issue, String> exactOracleFirst = new TreeMap<>(DEFAULT_ISSUE_SORT);
        SortedMap<Issue, String> lateOpenFirst = new TreeMap<>(DEFAULT_ISSUE_SORT);
        SortedMap<Issue, String> lateOracleFirst = new TreeMap<>(DEFAULT_ISSUE_SORT);

        for (Issue p : mp.keySet()) {
            String firstOracle = null;
            String firstOracleRaw = null;
            String firstOpen = null;
            String firstOpenRaw = null;
            LocalDateTime timeOracle = null;
            LocalDateTime timeOpen = null;

            for (Issue subIssue : mp.get(p)) {
                String rds = subIssue.getField("resolutiondate").getValue().toString();
                LocalDateTime rd = LocalDateTime.parse(rds.substring(0, rds.indexOf(".")));

                for (String fv : Accessors.getFixVersions(subIssue)) {
                    String sub = Versions.stripVendor(fv);
                    if (Versions.isOracle(fv)) {
                        if (firstOracle == null) {
                            firstOracle = sub;
                            firstOracleRaw = fv;
                            timeOracle = rd;
                        } else {
                            if (Versions.compare(sub, firstOracle) < 0) {
                                firstOracle = sub;
                                firstOracleRaw = fv;
                                timeOracle = rd;
                            }
                        }
                    } else {
                        if (firstOpen == null) {
                            firstOpen = sub;
                            firstOpenRaw = fv;
                            timeOpen = rd;
                        } else {
                            if (Versions.compare(sub, firstOpen) < 0) {
                                firstOpen = sub;
                                firstOpenRaw = fv;
                                timeOpen = rd;
                            }
                        }
                    }
                }
            }

            if (firstOracle == null && firstOpen != null) {
                onlyOpen.put(p, String.format("  %15s, %s: %s", firstOpenRaw, p.getKey(), p.getSummary()));
            }

            if (firstOracle != null && firstOpen == null) {
                onlyOracle.put(p, String.format("  %15s, %s: %s", firstOracleRaw, p.getKey(), p.getSummary()));
            }

            if (firstOracle != null && firstOpen != null && Versions.compare(firstOracleRaw, firstOpen) == 0) {
                if (timeOpen.compareTo(timeOracle) < 0) {
                    exactOpenFirst.put(p, String.format("  %15s -> %15s, %s: %s", firstOpenRaw, firstOracleRaw, p.getKey(), p.getSummary()));
                } else {
                    exactOracleFirst.put(p, String.format("  %15s -> %15s, %s: %s", firstOracleRaw, firstOpenRaw, p.getKey(), p.getSummary()));
                }
            }

            if (firstOracle != null && firstOpen != null && Versions.compare(firstOpen, firstOracle) < 0) {
                lateOpenFirst.put(p, String.format("  %15s -> %15s, %s: %s", firstOpenRaw, firstOracleRaw, p.getKey(), p.getSummary()));
            }

            if (firstOracle != null && firstOpen != null && Versions.compare(firstOpen, firstOracle) > 0) {
                lateOracleFirst.put(p, String.format("  %15s -> %15s, %s: %s", firstOracleRaw, firstOpenRaw, p.getKey(), p.getSummary()));
            }
        }

        out.println("=== EXCLUSIVE: ONLY IN OPENJDK");
        out.println();
        out.println("This is where OpenJDK is ahead of Oracle JDK.");
        out.println("No relevant backports are detected in Oracle JDK yet.");
        out.println("This misses the ongoing backporting work.");
        out.println();
        printSimple(onlyOpen);
        out.println();

        out.println("=== EXCLUSIVE: ONLY IN ORACLE JDK");
        out.println();
        out.println("This is where Oracle JDK is ahead of OpenJDK.");
        out.println("No relevant backports are detected in OpenJDK.");
        out.println("This misses the ongoing backporting work.");
        out.println();
        printSimple(onlyOracle);
        out.println();

        out.println("=== LATE PARITY: ORACLE JDK FOLLOWS OPENJDK IN LATER RELEASES");
        out.println();
        out.println("This is where OpenJDK used to be ahead, and then Oracle JDK caught up in future releases.");
        out.println();
        printSimple(lateOpenFirst);
        out.println();

        out.println("=== LATE PARITY: OPENJDK FOLLOWS ORACLE JDK IN LATER RELEASES");
        out.println();
        out.println("This is where Oracle JDK used to be ahead, and then OpenJDK caught up in future releases.");
        out.println();
        printSimple(lateOracleFirst);
        out.println();

        out.println("=== EXACT PARITY: ORACLE JDK FOLLOWS OPENJDK");
        out.println();
        out.println("This is where OpenJDK made the first backport in the release, and then Oracle JDK followed.");
        out.println("No difference in the final release detected.");
        out.println();
        printSimple(exactOpenFirst);
        out.println();

        out.println("=== EXACT PARITY: OPENJDK FOLLOWS ORACLE JDK");
        out.println();
        out.println("This is where Oracle JDK made the first backport in the release, and then OpenJDK followed.");
        out.println("No difference in the final release detected.");
        out.println();
        printSimple(exactOracleFirst);
        out.println();
    }

    void printSimple(Map<Issue, String> issues) {
        out.println(issues.size() + " issues:");
        for (Map.Entry<Issue,String> kv : issues.entrySet()) {
            out.println(kv.getValue());
        }
        out.println();
    }
}
