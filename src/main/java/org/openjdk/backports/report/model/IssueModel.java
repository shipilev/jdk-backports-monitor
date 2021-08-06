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
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.Version;
import org.openjdk.backports.Actionable;
import org.openjdk.backports.Actions;
import org.openjdk.backports.hg.HgDB;
import org.openjdk.backports.hg.HgRecord;
import org.openjdk.backports.jira.Accessors;
import org.openjdk.backports.jira.IssuePromise;
import org.openjdk.backports.jira.Versions;
import org.openjdk.backports.report.BackportStatus;

import java.io.PrintStream;
import java.util.*;

public class IssueModel extends AbstractModel {

    private final HgDB hgDB;

    private final SortedMap<Integer, List<Issue>> existingPorts = new TreeMap<>();
    private final SortedMap<Integer, BackportStatus> pendingPorts =  new TreeMap<>();
    private final SortedMap<Integer, String> pendingPortsDetails =  new TreeMap<>();
    private final SortedMap<Integer, BackportStatus> shenandoahPorts = new TreeMap<>();
    private final SortedMap<Integer, String> shenandoahPortsDetails = new TreeMap<>();

    private Collection<Issue> relNotes;
    private Issue issue;
    private int fixVersion;
    private final Actions actions = new Actions();
    private String components;
    private long daysAgo;
    private int priority;

    public IssueModel(JiraRestClient restClient, HgDB hgDB, PrintStream debugOut, String issueId) {
        super(restClient, debugOut);
        this.hgDB = hgDB;
        init(issueCli.getIssue(issueId).claim());
    }

    public IssueModel(JiraRestClient restClient, HgDB hgDB, PrintStream debugOut, Issue issue) {
        super(restClient, debugOut);
        this.hgDB = hgDB;
        init(issue);
    }

    private static <T> Iterable<T> orEmpty(Iterable<T> it) {
        return (it != null) ? it : Collections.emptyList();
    }

    private void init(Issue issue) {
        this.issue = issue;

        components = Accessors.extractComponents(issue);

        Set<Integer> oracleBackports = new HashSet<>();

        daysAgo = Accessors.getPushDaysAgo(issue);
        priority = Accessors.getPriority(issue);

        fixVersion = Versions.parseMajor(Accessors.getFixVersion(issue));
        recordIssue(existingPorts, issue);

        Set<Integer> affectedReleases = new HashSet<>();
        Set<Integer> affectedShenandoah = new HashSet<>();

        for (Version v : orEmpty(issue.getAffectedVersions())) {
            String verName = v.getName();

            int ver = Versions.parseMajor(verName);
            int verSh = Versions.parseMajorShenandoah(verName);

            if (ver == 0) {
                // Special case: odd version, ignore it
            } else if (ver > 0) {
                affectedReleases.add(ver);
            } else if (verSh > 0) {
                affectedShenandoah.add(verSh);
            } else {
                actions.update(Actionable.CRITICAL);
            }
        }

        if (affectedReleases.isEmpty() && affectedShenandoah.isEmpty()) {
            actions.update(Actionable.CRITICAL);
        }

        List<IssuePromise> links = new ArrayList<>();
        for (IssueLink link : orEmpty(issue.getIssueLinks())) {
            if (link.getIssueLinkType().getName().equals("Backport")) {
                String linkKey = link.getTargetIssueKey();
                links.add(jiraIssues.getIssue(linkKey));
            }
        }
        for (IssuePromise p : links) {
            Issue subIssue = p.claim();
            recordIssue(existingPorts, subIssue);
            recordOracleStatus(oracleBackports, subIssue);
        }

        int highRel = existingPorts.isEmpty() ? fixVersion : existingPorts.lastKey();

        for (int release : VERSIONS_TO_CARE_FOR) {
            if (release == fixVersion) continue;
            List<Issue> lines = existingPorts.get(release);
            if (lines != null) {
                affectedReleases.add(release);
            }
        }

        for (int release : VERSIONS_TO_CARE_FOR) {
            if (existingPorts.containsKey(release)) continue;

            BackportStatus status;
            String msg = "";
            if (issue.getLabels().contains("jdk" + release + "u-critical-yes")) {
                actions.update(Actionable.PUSHABLE, importanceCritical(release));
                msg = "jdk" + release + "u-critical-yes is set";
                status = BackportStatus.APPROVED;
            } else if (issue.getLabels().contains("jdk" + release + "u-fix-yes")) {
                actions.update(Actionable.PUSHABLE, importanceDefault(release));
                msg = "jdk" + release + "u-fix-yes is set";
                status = BackportStatus.APPROVED;
            } else if (issue.getLabels().contains("jdk" + release + "u-fix-no")) {
                msg = "jdk" + release + "u-fix-no is set";
                status = BackportStatus.REJECTED;
            } else if (issue.getLabels().contains("jdk" + release + "u-critical-request")) {
                actions.update(Actionable.REQUESTED);
                msg = "jdk" + release + "u-critical-request is set";
                status = BackportStatus.REJECTED;
            } else if (issue.getLabels().contains("jdk" + release + "u-fix-request")) {
                actions.update(Actionable.REQUESTED);
                msg = "jdk" + release + "u-fix-request is set";
                status = BackportStatus.REQUESTED;
            } else if (release > highRel) {
                status = BackportStatus.INHERITED;
            } else if (!affectedReleases.contains(release)) {
                status = BackportStatus.NOT_AFFECTED;
            } else if (daysAgo >= 0 && daysAgo < ISSUE_BAKE_TIME_DAYS) {
                actions.update(Actionable.WAITING);
                msg = (ISSUE_BAKE_TIME_DAYS - daysAgo) + " days more";
                status = BackportStatus.BAKING;
            } else if (oracleBackports.contains(release)) {
                actions.update(Actionable.MISSING, importanceOracle(release));
                status = BackportStatus.MISSING_ORACLE;
            } else {
                actions.update(Actionable.MISSING, importanceDefault(release));
                status = BackportStatus.MISSING;
            }
            pendingPorts.put(release, status);
            pendingPortsDetails.put(release, msg);
        }

        if (issue.getLabels().contains("gc-shenandoah")) {
            String msg = "";
            BackportStatus status;
            if (!affectedShenandoah.contains(8)) {
                status = BackportStatus.NOT_AFFECTED;
            } else if (!hgDB.hasRepo("shenandoah/jdk8")) {
                actions.update(Actionable.CRITICAL);
                msg = "No Mercurial data available to judge";
                status = BackportStatus.WARNING;
            } else {
                String nonBackport = tryPrintHg("shenandoah/jdk8", issue.getKey().replaceFirst("JDK-", ""));
                if (nonBackport != null) {
                    msg = nonBackport;
                    status = BackportStatus.FIXED;
                } else {
                    String backports = tryPrintHg("shenandoah/jdk8", issue.getKey().replaceFirst("JDK-", "[backport] "));
                    if (backports != null) {
                        msg = backports;
                        status = BackportStatus.FIXED;
                    } else if (daysAgo >= 0 && daysAgo < ISSUE_BAKE_TIME_DAYS) {
                        actions.update(Actionable.WAITING);
                        msg = (ISSUE_BAKE_TIME_DAYS - daysAgo) + " days more";
                        status = BackportStatus.BAKING;
                    } else {
                        actions.update(Actionable.MISSING, importanceMerge());
                        status = BackportStatus.MISSING;
                    }
                }
            }

            shenandoahPorts.put(8, status);
            shenandoahPortsDetails.put(8, msg);
        }

        relNotes = jiraIssues.getReleaseNotes(issue);
    }

    private String tryPrintHg(String repo, String synopsis) {
        List<HgRecord> rs = hgDB.search(repo, synopsis);
        if (rs.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (HgRecord r : rs) {
            sb.append(r.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    private void recordOracleStatus(Set<Integer> results, Issue issue) {
        String fixVersion = Accessors.getFixVersion(issue);

        int ver = Versions.parseMajor(fixVersion);
        switch (ver) {
            case 8:
                if (Versions.parseMinor(fixVersion) <= 212) return;
                break;
            case 11:
                if (!fixVersion.contains("-oracle")) return;
                break;
            default:
                return;
        }

        results.add(ver);
    }

    private void recordIssue(Map<Integer, List<Issue>> results, Issue issue) {
        String fixVersion = Accessors.getFixVersion(issue);

        // This is Oracle-internal push. Ignore.
        if (fixVersion.contains("-oracle")) return;

        String pushURL = Accessors.getPushURL(issue);

        if (pushURL.equals("N/A")) {
            switch (fixVersion) {
                case "11.0.1":
                case "12.0.1":
                case "13.0.1":
                    // Oh yeah, issues would have these versions set as "fix", but there would
                    // be no public pushes until CPU releases. Awesome.
                    pushURL = "<'kinda open, but not quite' backport>";
                    break;
                default:
                    break;
            }
        }

        if (pushURL.equals("N/A")) {
            return;
        }

        int ver = Versions.parseMajor(fixVersion);
        List<Issue> list = results.computeIfAbsent(ver, k -> new ArrayList<>());
        list.add(issue);
    }

    public Issue issue() {
        return issue;
    }

    public String issueKey() {
        return issue.getKey();
    }

    public String components() {
        return components;
    }

    public int priority() {
        return priority;
    }

    public long daysAgo() {
        return daysAgo;
    }

    public Collection<Issue> releaseNotes() {
        return relNotes;
    }

    public int fixVersion() {
        return fixVersion;
    }

    public Actions actions() {
        return actions;
    }

    public SortedMap<Integer, List<Issue>> existingPorts() {
        return existingPorts;
    }

    public SortedMap<Integer, BackportStatus> pendingPorts() {
        return pendingPorts;
    }

    public SortedMap<Integer, String> pendingPortsDetails() {
        return pendingPortsDetails;
    }

    public SortedMap<Integer, BackportStatus> shenandoahPorts() {
        return shenandoahPorts;
    }

    public SortedMap<Integer, String> shenandoahPortsDetails() {
        return shenandoahPortsDetails;
    }
}
