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
import com.atlassian.jira.rest.client.api.domain.Project;
import com.atlassian.jira.rest.client.api.domain.Version;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.openjdk.backports.jira.Accessors;
import org.openjdk.backports.jira.InterestTags;
import org.openjdk.backports.jira.Versions;

import java.time.LocalDateTime;
import java.util.*;

public class ParityReport extends AbstractReport {

    private final JiraRestClient restClient;
    private final int majorVer;

    public ParityReport(JiraRestClient restClient, int majorVer) {
        super(restClient);
        this.restClient = restClient;
        this.majorVer = majorVer;
    }

    @Override
    public void run() {
        out.println("PARITY REPORT FOR JDK: " + majorVer);
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows the bird-eye view of parity between OpenJDK and Oracle JDK.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        Multimap<Issue, Issue> mp = HashMultimap.create();

        List<String> vers = new ArrayList<>();
        int versLen = 0;

        Project proj = restClient.getProjectClient().getProject("JDK").claim();
        for (Version ver : proj.getVersions()) {
            String v = ver.getName();
            if (Versions.parseMajor(v) != majorVer) continue;
            if (Versions.isShared(v)) continue;
            vers.add(v);
            versLen = Math.max(versLen, v.length());
        }

        out.println("Auto-detected versions:");
        for (String ver : vers) {
            out.println("  " + ver);
        }
        out.println();

        for (String ver : vers) {
            Multimap<Issue, Issue> pb = jiraIssues.getIssuesWithBackports("project = JDK" +
                    " AND (status in (Closed, Resolved))" +
                    " AND (labels not in (release-note, openjdk-na) OR labels is EMPTY)" +
                    " AND (issuetype != CSR)" +
                    " AND (resolution not in (\"Won't Fix\", Duplicate, \"Cannot Reproduce\", \"Not an Issue\", Withdrawn, Other))" +
                    " AND fixVersion = " + ver);

            for (Issue parent : pb.keySet()) {
                if (Accessors.isOracleSpecific(parent)) {
                    // There is no parity with these
                    continue;
                }
                if (mp.containsKey(parent)) {
                    // Already parsed, skip
                    continue;
                }
                for (Issue backport : pb.get(parent)) {
                    if (Accessors.isDelivered(backport)) {
                        mp.put(parent, backport);
                    }
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
            boolean isShared = false;
            String firstOracle = null;
            String firstOracleRaw = null;
            String firstOpen = null;
            String firstOpenRaw = null;
            LocalDateTime timeOracle = null;
            LocalDateTime timeOpen = null;

            boolean backportRequested = p.getLabels().contains("jdk" + majorVer + "u-fix-request");
            String interestTags = InterestTags.shortTags(p.getLabels());

            for (Issue subIssue : mp.get(p)) {
                String rds = subIssue.getField("resolutiondate").getValue().toString();
                LocalDateTime rd = LocalDateTime.parse(rds.substring(0, rds.indexOf(".")));

                for (String fv : Accessors.getFixVersions(subIssue)) {
                    if (Versions.parseMajor(fv) != majorVer) {
                        // Not the release we are looking for
                        continue;
                    }
                    if (Versions.isShared(fv)) {
                        isShared = true;
                    }

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

            if (isShared) {
                continue;
            }

            if (firstOracle == null && firstOpen != null) {
                onlyOpen.put(p, String.format("  %-" + versLen + "s, %s: %s",
                        firstOpenRaw, p.getKey(), p.getSummary()));
            }

            if (firstOracle != null && firstOpen == null) {
                onlyOracle.put(p, String.format("  %-" + versLen + "s, %7s %3s %s: %s",
                        firstOracleRaw, interestTags, backportRequested ? "(*)" : "", p.getKey(), p.getSummary()));
            }

            if (firstOracle != null && firstOpen != null && Versions.compare(firstOracleRaw, firstOpen) == 0) {
                if (timeOpen.compareTo(timeOracle) < 0) {
                    exactOpenFirst.put(p, String.format("  %-" + versLen + "s -> %-" + versLen + "s, %s: %s",
                            firstOpenRaw, firstOracleRaw, p.getKey(), p.getSummary()));
                } else {
                    exactOracleFirst.put(p, String.format("  %-" + versLen + "s -> %-" + versLen + "s, %s: %s",
                            firstOracleRaw, firstOpenRaw, p.getKey(), p.getSummary()));
                }
            }

            if (firstOracle != null && firstOpen != null && Versions.compare(firstOpen, firstOracle) < 0) {
                lateOpenFirst.put(p, String.format("  %-" + versLen + "s -> %-" + versLen + "s, %s: %s",
                        firstOpenRaw, firstOracleRaw, p.getKey(), p.getSummary()));
            }

            if (firstOracle != null && firstOpen != null && Versions.compare(firstOpen, firstOracle) > 0) {
                lateOracleFirst.put(p, String.format("  %-" + versLen + "s -> %-" + versLen + "s, %s: %s",
                        firstOracleRaw, firstOpenRaw, p.getKey(), p.getSummary()));
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
        out.println("This misses the future backporting work.");
        out.println("[...] marks the interest tags.");
        out.println("(*) marks the backporting work in progress.");
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
