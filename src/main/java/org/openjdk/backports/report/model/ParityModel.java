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
package org.openjdk.backports.report.model;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.Project;
import com.atlassian.jira.rest.client.api.domain.Version;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.openjdk.backports.jira.Accessors;
import org.openjdk.backports.jira.InterestTags;
import org.openjdk.backports.jira.Versions;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.*;

public class ParityModel extends AbstractModel {

    private final int majorVer;
    private final SortedMap<Issue, String> exactOpenFirst;
    private final SortedMap<Issue, String> exactOracleFirst;
    private final SortedMap<Issue, String> exactUnknown;
    private final SortedMap<Issue, String> lateOpenFirst;
    private final SortedMap<Issue, String> lateOracleFirst;
    private final Map<String, Map<Issue, String>> onlyOpen;
    private final Map<String, Map<Issue, String>> onlyOracle;

    public ParityModel(JiraRestClient cli, PrintStream debugOut, int majorVer) {
        super(cli, debugOut);
        this.majorVer = majorVer;

        Multimap<Issue, Issue> mp = HashMultimap.create();

        List<String> vers = new ArrayList<>();
        int versLen = 0;

        Project proj = cli.getProjectClient().getProject("JDK").claim();
        for (Version ver : proj.getVersions()) {
            String v = ver.getName();
            if (Versions.parseMajor(v) != majorVer) continue;
            if (Versions.isShared(v)) continue;
            vers.add(v);
            versLen = Math.max(versLen, v.length());
        }

        debugOut.println("Auto-detected versions:");
        for (String ver : vers) {
            debugOut.println("  " + ver);
        }
        debugOut.println();

        for (String ver : vers) {
            Multimap<Issue, Issue> pb = jiraIssues.getIssuesWithBackportsOnly("project = JDK" +
                    " AND (status in (Closed, Resolved))" +
                    " AND (labels not in (release-note, openjdk-na, openjdk" + majorVer + "u-WNF) OR labels is EMPTY)" +
                    " AND (issuetype != CSR)" +
                    " AND (resolution not in (\"Won't Fix\", Duplicate, \"Cannot Reproduce\", \"Not an Issue\", Withdrawn, Other))" +
                    " AND fixVersion = " + ver);

            for (Issue parent : pb.keySet()) {
                if (Accessors.isOracleSpecific(parent)) {
                    // There is no parity with these
                    continue;
                }
                if (Accessors.isOpenJDKWontFix(parent, majorVer)) {
                    // Determined as won't fix for OpenJDK, skip
                    continue;
                }
                if (majorVer == 8 && Accessors.extractComponents(parent).startsWith("javafx")) {
                    // JavaFX is not the part of OpenJDK 8, no parity.
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
        }

        debugOut.println("Discovered " + mp.size() + " issues.");

        onlyOpen = new TreeMap<>(Versions::compare);
        onlyOracle = new TreeMap<>(Versions::compare);

        exactOpenFirst = new TreeMap<>(DEFAULT_ISSUE_SORT);
        exactOracleFirst = new TreeMap<>(DEFAULT_ISSUE_SORT);
        exactUnknown = new TreeMap<>(DEFAULT_ISSUE_SORT);
        lateOpenFirst = new TreeMap<>(DEFAULT_ISSUE_SORT);
        lateOracleFirst = new TreeMap<>(DEFAULT_ISSUE_SORT);

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

            // Awkward hack: parent needs to be counted for parity, on the off-chance
            // it has the fix-version after the open/closed split.
            List<Issue> issues = new ArrayList<>();
            issues.addAll(mp.get(p)); // all sub-issues
            issues.add(p);            // and the issue itself

            for (Issue subIssue : issues) {
                IssueField rdf = subIssue.getField("resolutiondate");
                LocalDateTime rd = null;
                if (rdf != null && rdf.getValue() != null) {
                    String rds = rdf.getValue().toString();
                    rd = LocalDateTime.parse(rds.substring(0, rds.indexOf(".")));
                }

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
                Map<Issue, String> map = onlyOpen.computeIfAbsent(firstOpen, k -> new TreeMap<>(DEFAULT_ISSUE_SORT));
                map.put(p, String.format("  %-" + versLen + "s, %s: %s",
                        firstOpenRaw, p.getKey(), p.getSummary()));
            }

            if (firstOracle != null && firstOpen == null) {
                Map<Issue, String> map = onlyOracle.computeIfAbsent(firstOracle, k -> new TreeMap<>(DEFAULT_ISSUE_SORT));
                map.put(p, String.format("  %-" + versLen + "s, %7s %3s %s: %s",
                        firstOracleRaw, interestTags, backportRequested ? "(*)" : "", p.getKey(), p.getSummary()));
            }

            if (firstOracle != null && firstOpen != null && Versions.compare(firstOracleRaw, firstOpen) == 0) {
                if (timeOpen == null || timeOracle == null) {
                    exactUnknown.put(p, String.format("  %-" + versLen + "s ... %-" + versLen + "s, %s: %s",
                            firstOpenRaw, firstOracleRaw, p.getKey(), p.getSummary()));
                } else if (timeOpen.compareTo(timeOracle) < 0) {
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
    }

    public int majorVer() {
        return majorVer;
    }

    public Map<String, Map<Issue, String>> onlyOpen() {
        return onlyOpen;
    }

    public Map<String, Map<Issue, String>> onlyOracle() {
        return onlyOracle;
    }

    public SortedMap<Issue, String> exactOpenFirst() {
        return exactOpenFirst;
    }

    public SortedMap<Issue, String> exactOracleFirst() {
        return exactOracleFirst;
    }

    public SortedMap<Issue, String> exactUnknown() {
        return exactUnknown;
    }

    public SortedMap<Issue, String> lateOpenFirst() {
        return lateOpenFirst;
    }

    public SortedMap<Issue, String> lateOracleFirst() {
        return lateOracleFirst;
    }
}
