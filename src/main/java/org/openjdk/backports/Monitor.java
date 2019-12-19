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
package org.openjdk.backports;

import com.atlassian.jira.rest.client.api.*;
import com.atlassian.jira.rest.client.api.domain.*;
import com.google.common.collect.*;
import org.openjdk.backports.census.Census;
import org.openjdk.backports.hg.HgDB;
import org.openjdk.backports.hg.HgRecord;
import org.openjdk.backports.jira.*;

import java.io.*;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Monitor {

    private static final String MSG_NOT_AFFECTED = "Not affected";
    private static final String MSG_BAKING = "WAITING for patch to bake a little";
    private static final String MSG_MISSING = "MISSING";
    private static final String MSG_MISSING_ORACLE = "MISSING (+ on Oracle backport list)";
    private static final String MSG_APPROVED = "APPROVED";
    private static final String MSG_WARNING = "WARNING";

    private static final int BAKE_TIME = 10; // days

    private static final int VER_INDENT = 9; // spaces

    private static final int[] VERSIONS_TO_CARE_FOR = {14, 11, 8};

    // LTS backports are most important, then merges, then STS backports
    private static final int IMPORTANCE_LTS_BACKPORT_CRITICAL = 50;
    private static final int IMPORTANCE_LTS_BACKPORT_ORACLE = 30;
    private static final int IMPORTANCE_LTS_BACKPORT = 10;
    private static final int IMPORTANCE_MERGE        = 3;
    private static final int IMPORTANCE_STS_BACKPORT = 1;

    // Sort issues by synopsis, alphabetically. This would cluster similar issues
    // together, even when they are separated by large difference in IDs.
    private static final Comparator<Issue> DEFAULT_ISSUE_SORT = Comparator.comparing(i -> i.getSummary().trim().toLowerCase());

    private final UserCache users;
    private final HgDB hgDB;
    private final boolean includeDownstream;
    private final boolean directOnly;
    private final SearchRestClient searchCli;
    private final IssueRestClient issueCli;
    private final PrintStream out;
    private final Issues jiraIssues;

    public Monitor(JiraRestClient restClient, HgDB hgDB, boolean includeDownstream, boolean directOnly) {
        this.hgDB = hgDB;
        this.out = System.out;
        this.searchCli = restClient.getSearchClient();
        this.issueCli = restClient.getIssueClient();
        this.jiraIssues = new Issues(out, searchCli, issueCli);
        this.users = new UserCache(restClient.getUserClient());
        this.includeDownstream = includeDownstream;
        this.directOnly = directOnly;
    }

    public void runLabelReport(String label, Actionable minLevel) {
        out.println("LABEL REPORT: " + label);
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows bugs with the given label, along with their backporting status.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();
        out.println("Minimal actionable level to display: " + minLevel);
        out.println();
        out.println("For actionable issues, search for these strings:");
        out.println("  \"" + MSG_MISSING + "\"");
        out.println("  \"" + MSG_APPROVED + "\"");
        out.println("  \"" + MSG_WARNING + "\"");
        out.println();
        out.println("For lingering issues, search for these strings:");
        out.println("  \"" + MSG_BAKING + "\"");
        out.println();

        List<Issue> found = jiraIssues.getIssues("labels = " + label +
                " AND (status in (Closed, Resolved))" +
                " AND (resolution not in (\"Won't Fix\", Duplicate, \"Cannot Reproduce\", \"Not an Issue\", Withdrawn, Other))" +
                " AND type != Backport");
        out.println();

        List<TrackedIssue> issues = found
                .parallelStream()
                .map(this::parseIssue)
                .filter(ti -> ti.getActions().actionable.ordinal() >= minLevel.ordinal())
                .collect(Collectors.toList());

        Collections.sort(issues);

        printDelimiterLine(out);
        for (TrackedIssue i : issues) {
            out.println(i.getOutput());
            printDelimiterLine(out);
        }

        out.println();
        out.println("" + issues.size() + " issues shown.");
    }

    public void runIssueReport(String issueId) {
        out.println("ISSUE REPORT: " + issueId);
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows a single issue status.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        Issue issue = issueCli.getIssue(issueId).claim();

        TrackedIssue trackedIssue = parseIssue(issue);

        printDelimiterLine(out);
        out.println(trackedIssue.getOutput());
    }

    public void runPendingPushReport(String release) {
        out.println("PENDING PUSH REPORT: " + release);
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows backports that were approved, but not yet pushed.");
        out.println("Some of them are true orphans with original backport requesters never got sponsored.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        String query = "labels = jdk" + release + "u-fix-yes AND labels != openjdk-na AND fixVersion !~ '" + release + ".*'";

        switch (release) {
            case "8":
                query += " AND fixVersion !~ 'openjdk8u*'";
                query += " AND issue not in linked-subquery(\"issue in subquery(\\\"fixVersion ~ 'openjdk8u*' AND (status = Closed OR status = Resolved)\\\")\")";
                break;
            default:
                query += " AND issue not in linked-subquery(\"issue in subquery(\\\"fixVersion ~ '" + release + ".*' AND fixVersion !~ '*oracle' AND (status = Closed OR status = Resolved)\\\")\")";
        }

        List<Issue> found = jiraIssues.getIssues(query);

        SortedSet<TrackedIssue> issues = new TreeSet<>();
        for (Issue i : found) {
            issues.add(parseIssue(i));
        }

        out.println();
        printDelimiterLine(out);
        for (TrackedIssue i : issues) {
            out.println(i.getOutput());
            printDelimiterLine(out);
        }
    }

    public void runPushesReport(String release) {
        out.println("PUSHES REPORT: " + release);
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows who pushed the backports to the given release.");
        out.println("This usually shows who did the backporting, testing, and review work.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        List<Issue> issues = jiraIssues.getIssues("project = JDK" +
                " AND (status in (Closed, Resolved))" +
                " AND (resolution not in (\"Won't Fix\", Duplicate, \"Cannot Reproduce\", \"Not an Issue\", Withdrawn, Other))" +
                (directOnly ? " AND type != Backport" : "") +
                " AND (issuetype != CSR)" +
                " AND fixVersion = " + release);

        Comparator<Issue> chronologicalCompare = Comparator.comparing(Accessors::getPushSecondsAgo).thenComparing(Comparator.comparing(Issue::getKey).reversed());

        Multiset<String> byPriority = TreeMultiset.create();
        Multiset<String> byComponent = HashMultiset.create();
        Multimap<String, Issue> byCommitter = TreeMultimap.create(String::compareTo, DEFAULT_ISSUE_SORT);
        Set<Issue> byTime = new TreeSet<>(chronologicalCompare);

        SortedSet<Issue> noChangesets = new TreeSet<>(chronologicalCompare);
        SortedSet<Issue> syncs = new TreeSet<>(chronologicalCompare);

        for (Issue issue : issues) {
            String committer = Accessors.getPushUser(issue);
            if (committer.equals("N/A")) {
                // These are pushes to internal repos
                noChangesets.add(issue);
            } else if (issue.getIssueType().getName().equals("Backport") && issue.getLabels().contains("hgupdate-sync")) {
                // These are usually syncs across release versions, not the original pushes
                syncs.add(issue);
            } else {
                byPriority.add(issue.getPriority().getName());
                byComponent.add(Accessors.extractComponents(issue));
                byCommitter.put(committer, issue);
                byTime.add(issue);
            }
        }

        out.println();
        out.println("Filtered " + noChangesets.size() + " issues without pushes, " + syncs.size() + " sync pushes, " + byPriority.size() + " pushes left.");
        out.println();

        out.println("Distribution by priority:");
        for (String prio : byPriority.elementSet()) {
            out.printf("   %3d: %s%n", byPriority.count(prio), prio);
        }
        out.println();

        out.println("Distribution by components:");
        printByComponent(out, byComponent);
        out.println();

        out.println("Distribution by email/name:");
        printByEmailName(out, byCommitter);
        out.println();

        out.println("Chronological push log:");
        out.println();
        for (Issue i : byTime) {
            String pushUser = Accessors.getPushUser(i);
            out.printf("  %3d day(s) ago, %" + users.maxDisplayName() + "s, %" + users.maxAffiliation() + "s, %s: %s%n",
                    TimeUnit.SECONDS.toDays(Accessors.getPushSecondsAgo(i)),
                    users.getDisplayName(pushUser), users.getAffiliation(pushUser),
                    i.getKey(), i.getSummary());
        }
        out.println();

        out.println("Chronological syncs log:");
        out.println();
        for (Issue i : syncs) {
            out.printf("  %3d day(s) ago, %s: %s%n",
                    TimeUnit.SECONDS.toDays(Accessors.getPushSecondsAgo(i)),
                    i.getKey(), i.getSummary());
        }
        out.println();

        out.println("No changesets log:");
        out.println();
        for (Issue i : noChangesets) {
            out.printf("  %s: %s%n",
                    i.getKey(), i.getSummary());
        }
        out.println();

        out.println("Committer push log:");
        out.println();

        for (String committer : byCommitter.keySet()) {
            out.println("  " + users.getDisplayName(committer) + ", " + users.getAffiliation(committer) + ":");
            for (Issue i : byCommitter.get(committer)) {
                out.println("    " + i.getKey() + ": " + i.getSummary());
            }
            out.println();
        }
    }

    private void printByEmailName(PrintStream out, Multimap<String, Issue> byCommitter) {
        Multiset<String> byCompany = TreeMultiset.create();
        Map<String, Multiset<String>> byCompanyAndCommitter = new HashMap<>();

        for (String committer : byCommitter.keySet()) {
            String company = users.getAffiliation(committer);
            byCompany.add(company, byCommitter.get(committer).size());
            Multiset<String> bu = byCompanyAndCommitter.computeIfAbsent(company, k -> HashMultiset.create());
            bu.add(users.getDisplayName(committer), byCommitter.get(committer).size());
        }

        int total = byCommitter.size();
        out.printf("   %3d: <total issues>%n", total);
        for (String company : Multisets.copyHighestCountFirst(byCompany).elementSet()) {
            String percCompany = String.format("(%.1f%%)", 100.0 * byCompany.count(company) / total);
            out.printf("      %3d %7s: %s%n", byCompany.count(company), percCompany, company);
            Multiset<String> committers = byCompanyAndCommitter.get(company);
            for (String committer : Multisets.copyHighestCountFirst(committers).elementSet()) {
                String percCommitter = String.format("(%.1f%%)", 100.0 * committers.count(committer) / total);
                out.printf("         %3d %7s: %s%n", committers.count(committer), percCommitter, committer);
            }
        }
    }

    private void printByComponent(PrintStream out, Multiset<String> byComponent) {
        Multiset<String> firsts = TreeMultiset.create();
        Map<String, Multiset<String>> seconds = new HashMap<>();

        for (String component : byComponent.elementSet()) {
            String first = component.split("/")[0];
            Multiset<String> bu = seconds.computeIfAbsent(first, k -> HashMultiset.create());
            bu.add(component, byComponent.count(component));
            firsts.add(first);
        }

        int total = byComponent.size();
        out.printf("   %3d: <total issues>%n", total);
        for (String first : Multisets.copyHighestCountFirst(firsts).elementSet()) {
            String percFirst = String.format("(%.1f%%)", 100.0 * firsts.count(first) / total);
            out.printf("      %3d %7s: %s%n", firsts.count(first), percFirst, first);
            Multiset<String> ms = seconds.get(first);
            for (String component : Multisets.copyHighestCountFirst(ms).elementSet()) {
                String percComponent = String.format("(%.1f%%)", 100.0 * ms.count(component) / total);
                out.printf("         %3d %7s: %s%n", ms.count(component), percComponent, component);
            }
        }
    }

    public void runReleaseNotesReport(String release) {
        out.println("RELEASE NOTES FOR: " + release);
        printMajorDelimiterLine(out);
        out.println();
        out.println("Notes generated: " + new Date());
        out.println();

        List<Issue> regularIssues = jiraIssues.getParentIssues("project = JDK" +
                " AND (status in (Closed, Resolved))" +
                " AND (resolution not in (\"Won't Fix\", Duplicate, \"Cannot Reproduce\", \"Not an Issue\", Withdrawn, Other))" +
                " AND (labels not in (release-note, testbug, openjdk-na, testbug) OR labels is EMPTY)" +
                " AND (summary !~ 'testbug')" +
                " AND (summary !~ 'problemlist') AND (summary !~ 'problem list') AND (summary !~ 'release note')" +
                " AND (issuetype != CSR)" +
                " AND fixVersion = " + release);

        out.println();

        List<Issue> jepIssues = jiraIssues.getParentIssues("project = JDK AND issuetype = JEP" +
                " AND fixVersion = " + release + "" +
                " ORDER BY summary ASC");

        Multimap<String, Issue> byComponent = TreeMultimap.create(String::compareTo, DEFAULT_ISSUE_SORT);

        SortedSet<Issue> noChangesets = new TreeSet<>(DEFAULT_ISSUE_SORT);
        SortedSet<Issue> carriedOver = new TreeSet<>(DEFAULT_ISSUE_SORT);

        for (Issue issue : regularIssues) {
            String committer = Accessors.getPushUser(issue);
            if (committer.equals("N/A")) {
                // These are pushes to internal repos
                noChangesets.add(issue);
            } else if (Parsers.parseVersion(Accessors.getFixVersion(issue)) < Parsers.parseVersion(release)) {
                // These are parent issues that have "accidental" forward port to requested release.
                // Filter them out as "carried over".
                carriedOver.add(issue);
            } else {
                byComponent.put(Accessors.extractComponents(issue), issue);
            }
        }

        out.println();
        out.println("Filtered " + noChangesets.size() + " issues without pushes, " + carriedOver.size() + " issues carried over, " + byComponent.size() + " pushes left.");
        out.println();

        out.println("Hint: Prefix bug IDs with " + Main.JIRA_URL + "browse/ to reach the relevant JIRA entry.");
        out.println();

        out.println("JAVA ENHANCEMENT PROPOSALS (JEP):");
        out.println();

        if (jepIssues.isEmpty()) {
            out.println("  None.");
        }

        for (Issue i : jepIssues) {
            out.println("  " + i.getSummary());
            out.println();

            String[] par = StringUtils.paragraphs(i.getDescription());
            if (par.length > 2) {
                // Second one is summary
                out.println(StringUtils.leftPad(StringUtils.rewrap(par[1], 100, 2), 6));
            } else {
                out.println(StringUtils.leftPad("No description.", 6));
            }
            out.println();
        }
        out.println();

        out.println("RELEASE NOTES, BY COMPONENT:");
        out.println();

        boolean haveRelNotes = false;
        for (String component : byComponent.keySet()) {
            boolean printed = false;
            for (Issue i : byComponent.get(component)) {
                Collection<String> relNotes = Accessors.getReleaseNotes(issueCli, i);
                if (relNotes.isEmpty()) continue;
                haveRelNotes = true;

                if (!printed) {
                    out.println(component + ":");
                    out.println();
                    printed = true;
                }

                out.println("  " + i.getKey() + ": " + i.getSummary());
                out.println();

                printReleaseNotes(out, relNotes);
            }
        }
        if (!haveRelNotes) {
            out.println("  None.");
        }
        out.println();

        out.println("ALL FIXED ISSUES, BY COMPONENT:");
        out.println();

        for (String component : byComponent.keySet()) {
            out.println(component + ":");
            for (Issue i : byComponent.get(component)) {
                out.println("  " + i.getKey() + ": " + i.getSummary());
            }
            out.println();
        }
        out.println();

        out.println("NO CHANGESETS RECORDED:");
        out.println();
        if (noChangesets.isEmpty()) {
            out.println("  None.");
        }

        for (Issue i : noChangesets) {
            out.printf("  %s: %s%n",
                    i.getKey(), i.getSummary());
        }
        out.println();

        out.println("CARRIED OVER FROM PREVIOUS RELEASES:");
        out.println();
        if (carriedOver.isEmpty()) {
            out.println("  None.");
        }

        for (Issue i : carriedOver) {
            out.printf("  %s: %s%n",
                    i.getKey(), i.getSummary());
        }
        out.println();
    }

    public void runFilterReport(long filterId) {
        Filter filter = searchCli.getFilter(filterId).claim();

        out.println("FILTER REPORT");
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows brief list of issues matching the filter.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();
        out.println("Filter: " + filter.getName());
        out.println("Filter URL: " + Main.JIRA_URL + "issues/?filter=" + filterId);
        out.println();

        List<Issue> issues = jiraIssues.getBasicIssues(filter.getJql());

        out.println();
        out.println("Hint: Prefix bug IDs with " + Main.JIRA_URL + "browse/ to reach the relevant JIRA entry.");
        out.println();

        Collections.sort(issues, DEFAULT_ISSUE_SORT);

        Multimap<String, Issue> byComponent = TreeMultimap.create(String::compareTo, DEFAULT_ISSUE_SORT);

        for (Issue issue : issues) {
            byComponent.put(Accessors.extractComponents(issue), issue);
        }

        for (String component : byComponent.keySet()) {
            out.println(component + ":");
            for (Issue i : byComponent.get(component)) {
                out.println("  " + i.getKey() + ": " + i.getSummary());
            }
            out.println();
        }
        out.println();
    }

    public void runAffiliationReport() {
        out.println("AFFILIATION REPORT");
        printMajorDelimiterLine(out);
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        List<String> userIds = Census.userIds();

        // Start async resolve
        for (String uid : userIds) {
            users.getUserAsync(uid);
        }

        // Get all data and compute column widths
        int maxUid = 0;
        for (String uid : userIds) {
            users.getDisplayName(uid);
            users.getAffiliation(uid);
            maxUid = Math.max(maxUid, uid.length());
        }

        int maxDisplayName = users.maxDisplayName();
        int maxAffiliation = users.maxAffiliation();
        for (String uid : userIds) {
            out.printf("%" + maxUid + "s, %" + maxDisplayName + "s, %" + maxAffiliation + "s%n",
                    uid, users.getDisplayName(uid), users.getAffiliation(uid));
        }
    }

    private static <T> Iterable<T> orEmpty(Iterable<T> it) {
        return (it != null) ? it : Collections.emptyList();
    }

    private TrackedIssue parseIssue(Issue issue) {
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

            int ver = Parsers.parseVersion(verName);
            int verSh = Parsers.parseVersionShenandoah(verName);
            int verAarch64 = Parsers.parseVersionAArch64(verName);

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

        int origRel = Parsers.parseVersion(Accessors.getFixVersion(issue));
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
                    Collections.sort(lines, Comparator.reverseOrder());

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

        Collection<String> relNotes = Accessors.getReleaseNotes(issueCli, issue);
        if (!relNotes.isEmpty()) {
            pw.println("  Release Notes:");
            printReleaseNotes(pw, relNotes);
            pw.println();
        }

        return new TrackedIssue(sw.toString(), daysAgo, actions);
    }

    private void printReleaseNotes(PrintStream ps, Collection<String> relNotes) {
        PrintWriter pw = new PrintWriter(ps);
        printReleaseNotes(pw, relNotes);
        pw.flush();
    }

    private void printReleaseNotes(PrintWriter pw, Collection<String> relNotes) {
        Set<String> dup = new HashSet<>();
        for (String rn : relNotes) {
            String fmtd = StringUtils.leftPad(StringUtils.rewrap(rn, StringUtils.DEFAULT_WIDTH - 6), 6);
            if (dup.add(fmtd)) {
                pw.println(fmtd);
                pw.println();
            }
        }
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

    private void printDelimiterLine(PrintStream pw) {
        pw.println(StringUtils.tabLine('-'));
    }

    private void printMajorDelimiterLine(PrintStream pw) {
        pw.println(StringUtils.tabLine('='));
    }

    private void recordOracleStatus(Set<Integer> results, Issue issue) {
        String fixVersion = Accessors.getFixVersion(issue);

        int ver = Parsers.parseVersion(fixVersion);
        switch (ver) {
            case 8:
                if (Parsers.parseSubversion(fixVersion) <= 212) return;
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
        int ver = Parsers.parseVersion(fixVersion);
        List<String> list = results.computeIfAbsent(ver, k -> new ArrayList<>());
        list.add(line);
    }

}
