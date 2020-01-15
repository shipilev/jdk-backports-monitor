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

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.Version;
import org.openjdk.backports.Actionable;
import org.openjdk.backports.Actions;
import org.openjdk.backports.Main;
import org.openjdk.backports.hg.HgDB;
import org.openjdk.backports.hg.HgRecord;
import org.openjdk.backports.jira.Accessors;
import org.openjdk.backports.jira.RetryableIssuePromise;
import org.openjdk.backports.jira.TrackedIssue;
import org.openjdk.backports.jira.Versions;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public abstract class AbstractIssueReport extends AbstractReport {

    private static final int BAKE_TIME = 10; // days

    private static final int VER_INDENT = 9; // spaces

    private static final int[] VERSIONS_TO_CARE_FOR = {14, 11, 8};

    // LTS backports are most important, then merges, then STS backports
    private static final int IMPORTANCE_LTS_BACKPORT_CRITICAL = 50;
    private static final int IMPORTANCE_LTS_BACKPORT_ORACLE = 30;
    private static final int IMPORTANCE_LTS_BACKPORT = 10;
    private static final int IMPORTANCE_MERGE        = 3;
    private static final int IMPORTANCE_STS_BACKPORT = 1;

    private final HgDB hgDB;
    private final boolean includeDownstream;

    public AbstractIssueReport(JiraRestClient restClient, String hgRepos, boolean includeDownstream) {
        super(restClient);
        this.hgDB = new HgDB();
        if (hgRepos != null) {
            hgDB.load(hgRepos);
        }
        this.includeDownstream = includeDownstream;
    }

    private static <T> Iterable<T> orEmpty(Iterable<T> it) {
        return (it != null) ? it : Collections.emptyList();
    }

    protected TrackedIssue parseIssue(Issue issue) {
        Actions actions = new Actions();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        pw.println();
        pw.println(issue.getKey() + ": " + issue.getSummary());
        pw.println();
        pw.println("  Original Bug:");
        pw.println("      URL: " + Main.JIRA_URL + "browse/" + issue.getKey());
        pw.println("      Reporter: " + (issue.getReporter() != null ? issue.getReporter().getDisplayName() : "None"));
        pw.println("      Assignee: " + (issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : "None"));
        pw.println("      Priority: " + issue.getPriority().getName());
        pw.println("      Components: " + Accessors.extractComponents(issue));
        pw.println();

        SortedMap<Integer, List<String>> results = new TreeMap<>();
        Set<Integer> oracleBackports = new HashSet<>();

        pw.println("  Original Fix:");

        long daysAgo = Accessors.getPushDaysAgo(issue);

        pw.printf("  %" + VER_INDENT + "s: %10s, %s, %s%n", Accessors.getFixVersion(issue), issue.getKey(), Accessors.getPushURL(issue), Accessors.getPushDate(issue));
        recordIssue(results, issue);
        pw.println();

        Set<Integer> affectedReleases = new HashSet<>();
        Set<Integer> affectedShenandoah = new HashSet<>();
        Set<Integer> affectedAArch64 = new HashSet<>();

        for (Version v : orEmpty(issue.getAffectedVersions())) {
            String verName = v.getName();

            int ver = Versions.parseMajor(verName);
            int verSh = Versions.parseMajorShenandoah(verName);
            int verAarch64 = Versions.parseMajorAArch64(verName);

            if (ver == 0) {
                // Special case: odd version, ignore it
            } else if (ver > 0) {
                affectedReleases.add(ver);
            } else if (verSh > 0) {
                affectedShenandoah.add(verSh);
            } else if (verAarch64 > 0) {
                affectedAArch64.add(verAarch64);
            } else {
                pw.println("  " + MSG_WARNING + ": Unknown affected version: " + verName);
                pw.println();
                actions.update(Actionable.CRITICAL);
            }
        }

        if (affectedReleases.isEmpty()) {
            pw.println("  " + MSG_WARNING + ": Affected versions is not set.");
            pw.println();
            actions.update(Actionable.CRITICAL);
        }

        List<RetryableIssuePromise> links = new ArrayList<>();
        for (IssueLink link : orEmpty(issue.getIssueLinks())) {
            if (link.getIssueLinkType().getName().equals("Backport")) {
                String linkKey = link.getTargetIssueKey();
                links.add(new RetryableIssuePromise(issueCli, linkKey));
            }
        }
        for (RetryableIssuePromise p : links) {
            Issue subIssue = p.claim();
            recordIssue(results, subIssue);
            recordOracleStatus(oracleBackports, subIssue);
        }

        int origRel = Versions.parseMajor(Accessors.getFixVersion(issue));
        int highRel = results.isEmpty() ? origRel : results.lastKey();

        for (int release : VERSIONS_TO_CARE_FOR) {
            if (release == origRel) continue;
            List<String> lines = results.get(release);
            if (lines != null && !affectedReleases.contains(release)) {
                pw.println("  " + MSG_WARNING + ": Port to " + release + " was found. No relevant affected version is set, assuming one.");
                pw.println();
                affectedReleases.add(release);
            }
        }

        {
            boolean foundInPublic = false;
            boolean printedWarning = false;

            for (String repo : new String[] {"jdk/jdk", "jdk-updates/jdk11u", "jdk8u/jdk8u", "jdk7u/jdk7u"}) {
                if (!hgDB.hasRepo(repo)) {
                    pw.println("  " + MSG_WARNING + ": " + repo + " repository is not available to check changeset");
                    printedWarning = true;
                } else {
                    List<HgRecord> rs = hgDB.search(repo, issue.getKey().replaceFirst("JDK-", ""));
                    if (!rs.isEmpty()) {
                        foundInPublic = true;
                        break;
                    }
                }
            }

            if (!foundInPublic) {
                actions.update(Actionable.CRITICAL);
                pw.println("  " + MSG_WARNING + ": The change is missing in all open repos.");
                printedWarning = true;
            }

            if (printedWarning) {
                pw.println();
            }
        }

        pw.println("  Backports and Forwardports:");

        boolean printed = false;
        for (int release : VERSIONS_TO_CARE_FOR) {
            List<String> lines = results.get(release);
            if (lines != null) {
                if (release != origRel) {
                    lines.sort(Comparator.reverseOrder());

                    boolean first = true;
                    for (String line : lines) {
                        if (first) {
                            pw.printf("  %" + VER_INDENT + "s: ", release);
                            first = false;
                        } else {
                            pw.printf("  %" + VER_INDENT + "s  ", "");
                        }
                        pw.println(line);
                        printed = true;
                    }
                }
            } else if (release <= highRel) {
                pw.printf("  %" + VER_INDENT + "s: ", release);
                switch (release) {
                    case 7: {
                        if (!affectedReleases.contains(7)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else if (daysAgo >= 0 && daysAgo < BAKE_TIME) {
                            actions.update(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else {
                            actions.update(Actionable.MISSING, IMPORTANCE_LTS_BACKPORT);
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    case 8: {
                        if (issue.getLabels().contains("jdk8u-critical-yes")) {
                            actions.update(Actionable.PUSHABLE, IMPORTANCE_LTS_BACKPORT_CRITICAL);
                            pw.println(MSG_APPROVED + ": jdk8u-critical-yes is set");
                        } else if (issue.getLabels().contains("jdk8u-fix-yes")) {
                            actions.update(Actionable.PUSHABLE, IMPORTANCE_LTS_BACKPORT);
                            pw.println(MSG_APPROVED + ": jdk8u-fix-yes is set");
                        } else if (issue.getLabels().contains("jdk8u-fix-no")) {
                            pw.println("REJECTED: jdk8u-fix-no is set");
                        } else if (issue.getLabels().contains("jdk8u-critical-request")) {
                            pw.println("Requested: jdk8u-critical-request is set");
                            actions.update(Actionable.REQUESTED);
                        } else if (issue.getLabels().contains("jdk8u-fix-request")) {
                            pw.println("Requested: jdk8u-fix-request is set");
                            actions.update(Actionable.REQUESTED);
                        } else if (!affectedReleases.contains(8)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else if (daysAgo >= 0 && daysAgo < BAKE_TIME) {
                            actions.update(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else if (oracleBackports.contains(8)) {
                            actions.update(Actionable.MISSING, IMPORTANCE_LTS_BACKPORT_ORACLE);
                            pw.println(MSG_MISSING_ORACLE);
                        } else {
                            actions.update(Actionable.MISSING, IMPORTANCE_LTS_BACKPORT);
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    case 11: {
                        if (issue.getLabels().contains("jdk11u-critical-yes")) {
                            actions.update(Actionable.PUSHABLE, IMPORTANCE_LTS_BACKPORT_CRITICAL);
                            pw.println(MSG_APPROVED + ": jdk11u-critical-yes is set");
                        } else if (issue.getLabels().contains("jdk11u-fix-yes")) {
                            actions.update(Actionable.PUSHABLE, IMPORTANCE_LTS_BACKPORT);
                            pw.println(MSG_APPROVED + ": jdk11u-fix-yes is set");
                        } else if (issue.getLabels().contains("jdk11u-fix-no")) {
                            pw.println("REJECTED: jdk11u-fix-no is set");
                        } else if (issue.getLabels().contains("jdk11u-critical-request")) {
                            pw.println("Requested: jdk11u-critical-request is set");
                            actions.update(Actionable.REQUESTED);
                        } else if (issue.getLabels().contains("jdk11u-fix-request")) {
                            pw.println("Requested: jdk11u-fix-request is set");
                            actions.update(Actionable.REQUESTED);
                        } else if (!affectedReleases.contains(11)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else if (daysAgo >= 0 && daysAgo < BAKE_TIME) {
                            actions.update(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else if (oracleBackports.contains(11)) {
                            actions.update(Actionable.MISSING, IMPORTANCE_LTS_BACKPORT_ORACLE);
                            pw.println(MSG_MISSING_ORACLE);
                        } else {
                            actions.update(Actionable.MISSING, IMPORTANCE_LTS_BACKPORT);
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    case 13: {
                        if (issue.getLabels().contains("jdk13u-fix-yes")) {
                            actions.update(Actionable.PUSHABLE, IMPORTANCE_STS_BACKPORT);
                            pw.println(MSG_APPROVED + ": jdk13u-fix-yes is set");
                        } else if (issue.getLabels().contains("jdk13u-fix-no")) {
                            pw.println("REJECTED: jdk13u-fix-no is set");
                        } else if (issue.getLabels().contains("jdk13u-fix-request")) {
                            pw.println("Requested: jdk13u-fix-request is set");
                            actions.update(Actionable.REQUESTED);
                        } else if (!affectedReleases.contains(13)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else if (daysAgo >= 0 && daysAgo < BAKE_TIME) {
                            actions.update(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else {
                            actions.update(Actionable.MISSING, IMPORTANCE_STS_BACKPORT);
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    case 14: {
                        if (issue.getLabels().contains("jdk14-fix-yes")) {
                            actions.update(Actionable.PUSHABLE, IMPORTANCE_STS_BACKPORT);
                            pw.println(MSG_APPROVED + ": jdk14-fix-yes is set");
                        } else if (issue.getLabels().contains("jdk14-fix-no")) {
                            pw.println("REJECTED: jdk14-fix-no is set");
                        } else if (issue.getLabels().contains("jdk14-fix-request")) {
                            pw.println("Requested: jdk14-fix-request is set");
                            actions.update(Actionable.REQUESTED);
                        } else if (!affectedReleases.contains(14)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else if (daysAgo >= 0 && daysAgo < BAKE_TIME) {
                            actions.update(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else {
                            actions.update(Actionable.MISSING, IMPORTANCE_STS_BACKPORT);
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    default:
                        pw.println("Unknown release: " + release);
                        actions.update(Actionable.CRITICAL);
                }
                printed = true;
            }
        }

        if (!printed) {
            pw.println("      None.");
        }

        if (issue.getLabels().contains("gc-shenandoah")) {
            pw.println();
            pw.println("  Shenandoah Backports:");

            for (int ver : new int[]{11, 8}) {
                boolean affected = affectedShenandoah.contains(ver);
                switch (ver) {
                    case 8:
                        printHgStatus(affected, actions, pw, issue, "8", "shenandoah/jdk8");
                        break;
                    case 11:
                        printHgStatus(affected, actions, pw, issue, "11", "shenandoah/jdk11");
                        break;
                    default:
                        pw.println("Unknown release: " + ver);
                }
            }
        }

        if (!affectedAArch64.isEmpty()) {
            pw.println();
            pw.println("  AArch64 Backports:");

            printHgStatus(true, actions, pw, issue, "8", "aarch64-port/jdk8u-shenandoah");
        }

        if (includeDownstream) {
            pw.println();
            pw.println("  Downstream Repositories:");

            printed = false;
            if (affectedReleases.contains(11)) {
                printHgStatus(true, actions, pw, issue, "11-sh", "shenandoah/jdk11");
                printed = true;
            }
            if (affectedReleases.contains(8) || affectedShenandoah.contains(8) || affectedAArch64.contains(8)) {
                printHgStatus(true, actions, pw, issue, "8-a64-sh", "aarch64-port/jdk8u-shenandoah");
                printed = true;
            }
            if (affectedReleases.contains(7)) {
                printHgStatus(true, actions, pw, issue, "7-it-2.6", "icedtea7-forest-2.6");
                printed = true;
            }
            if (!printed) {
                pw.println("      None.");
            }
        }
        pw.println();

        Collection<Issue> relNotes = Accessors.getReleaseNotes(issueCli, issue);
        if (!relNotes.isEmpty()) {
            pw.println("  Release Notes:");
            pw.println();
            printReleaseNotes(pw, relNotes);
            pw.println();
        }

        return new TrackedIssue(sw.toString(), daysAgo, actions);
    }

    private void printHgStatus(boolean affected, Actions actions, PrintWriter pw, Issue issue, String label, String repo) {
        pw.printf("  %" + VER_INDENT + "s: ", label);

        if (!affected) {
            pw.println(MSG_NOT_AFFECTED);
            return;
        }

        if (!hgDB.hasRepo(repo)) {
            pw.println(MSG_WARNING + ": No Mercurial data available to judge");
            return;
        }

        if (tryPrintHg(pw, repo, issue.getKey().replaceFirst("JDK-", ""))) {
            return;
        }

        if (tryPrintHg(pw, repo, issue.getKey().replaceFirst("JDK-", "[backport] "))) {
            return;
        }

        actions.update(Actionable.MISSING, IMPORTANCE_MERGE);
        pw.println(MSG_MISSING);
    }

    private boolean tryPrintHg(PrintWriter pw, String repo, String synopsis) {
        List<HgRecord> rs = hgDB.search(repo, synopsis);
        if (rs.isEmpty()) {
            return false;
        }

        boolean first = true;
        for (HgRecord r : rs) {
            if (first) {
                first = false;
            } else {
                pw.printf("  %" + VER_INDENT + "s  ", "");
            }
            pw.println(r.toString());
        }
        return true;
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

    private void recordIssue(Map<Integer, List<String>> results, Issue issue) {
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

        String line = String.format("%s, %10s, %s, %s", fixVersion, issue.getKey(), pushURL, Accessors.getPushDate(issue));
        int ver = Versions.parseMajor(fixVersion);
        List<String> list = results.computeIfAbsent(ver, k -> new ArrayList<>());
        list.add(line);
    }

}
