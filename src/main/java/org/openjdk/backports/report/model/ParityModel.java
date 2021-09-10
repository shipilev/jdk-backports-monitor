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
    private final SortedMap<Issue, SingleVers> exactOpenFirst;
    private final SortedMap<Issue, SingleVers> exactOracleFirst;
    private final SortedMap<Issue, SingleVers> openRejected;
    private final SortedMap<Issue, DoubleVers> exactUnknown;
    private final SortedMap<Issue, DoubleVers> lateOpenFirst;
    private final SortedMap<Issue, DoubleVers> lateOracleFirst;
    private final Map<String, Map<Issue, SingleVers>> onlyOpen;
    private final Map<String, Map<Issue, SingleVersMetadata>> onlyOracle;
    private int versLen;

    public ParityModel(JiraRestClient cli, PrintStream debugOut, int majorVer) {
        super(cli, debugOut);
        this.majorVer = majorVer;

        Multimap<Issue, Issue> mp = HashMultimap.create();

        List<String> vers = new ArrayList<>();

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
                    " AND (labels not in (release-note) OR labels is EMPTY)" +
                    " AND (issuetype != CSR)" +
                    " AND (resolution not in (\"Won't Fix\", Duplicate, \"Cannot Reproduce\", \"Not an Issue\", Withdrawn, Other))" +
                    " AND fixVersion = " + ver);

            for (Issue parent : pb.keySet()) {
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
        openRejected = new TreeMap<>(DEFAULT_ISSUE_SORT);

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

            if (Accessors.isOracleSpecific(p) ||
                Accessors.isOpenJDKWontFix(p, majorVer) ||
                Accessors.ifUpdateReleaseNo(p, majorVer)) {
                openRejected.put(p, new SingleVers(firstOracle));
                continue;
            }

            if (firstOracle == null && firstOpen != null) {
                Map<Issue, SingleVers> map = onlyOpen.computeIfAbsent(firstOpen, k -> new TreeMap<>(DEFAULT_ISSUE_SORT));
                map.put(p, new SingleVers(firstOpenRaw));
            }

            if (firstOracle != null && firstOpen == null) {
                Map<Issue, SingleVersMetadata> map = onlyOracle.computeIfAbsent(firstOracle, k -> new TreeMap<>(DEFAULT_ISSUE_SORT));
                map.put(p, new SingleVersMetadata(firstOracleRaw, interestTags, backportRequested));
            }

            if (firstOracle != null && firstOpen != null && Versions.compare(firstOracleRaw, firstOpen) == 0) {
                if (timeOpen == null || timeOracle == null) {
                    exactUnknown.put(p, new DoubleVers(firstOpenRaw, firstOracleRaw));
                } else if (timeOpen.compareTo(timeOracle) < 0) {
                    exactOpenFirst.put(p, new SingleVers(firstOpenRaw));
                } else {
                    exactOracleFirst.put(p, new SingleVers(firstOracleRaw));
                }
            }

            if (firstOracle != null && firstOpen != null && Versions.compare(firstOpen, firstOracle) < 0) {
                lateOpenFirst.put(p, new DoubleVers(firstOpenRaw, firstOracleRaw));
            }

            if (firstOracle != null && firstOpen != null && Versions.compare(firstOpen, firstOracle) > 0) {
                lateOracleFirst.put(p, new DoubleVers(firstOracleRaw, firstOpenRaw));
            }
        }
    }

    public int majorVer() {
        return majorVer;
    }

    public Map<String, Map<Issue, SingleVers>> onlyOpen() {
        return onlyOpen;
    }

    public Map<String, Map<Issue, SingleVersMetadata>> onlyOracle() {
        return onlyOracle;
    }

    public SortedMap<Issue, SingleVers> exactOpenFirst() {
        return exactOpenFirst;
    }

    public SortedMap<Issue, SingleVers> exactOracleFirst() {
        return exactOracleFirst;
    }

    public SortedMap<Issue, SingleVers> openRejected() {
        return openRejected;
    }

    public SortedMap<Issue, DoubleVers> exactUnknown() {
        return exactUnknown;
    }

    public SortedMap<Issue, DoubleVers> lateOpenFirst() {
        return lateOpenFirst;
    }

    public SortedMap<Issue, DoubleVers> lateOracleFirst() {
        return lateOracleFirst;
    }

    public int getVersLen() {
        return versLen;
    }

    public static class SingleVers {
        private final String ver;
        public SingleVers(String ver) {
            this.ver = ver;
        }

        public String version() {
            return ver;
        }
    }

    public static class SingleVersMetadata {
        private final String ver;
        private final String interestTags;
        private final boolean backportRequested;

        public SingleVersMetadata(String ver, String interestTags, boolean backportRequested) {
            this.ver = ver;
            this.interestTags = interestTags;
            this.backportRequested = backportRequested;
        }

        public String version() {
            return ver;
        }
        public String interestTags() {
            return interestTags;
        }
        public boolean backportRequested() {
            return backportRequested;
        }
    }

    public static class DoubleVers {
        private final String v1, v2;
        public DoubleVers(String v1, String v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        public String version1() {
            return v1;
        }

        public String version2() {
            return v2;
        }
    }


}
