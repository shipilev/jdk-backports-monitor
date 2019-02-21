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
import com.atlassian.util.concurrent.Promise;
import com.google.common.collect.*;

import java.io.*;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Monitor {

    private static final String MSG_NOT_AFFECTED = "Not affected";
    private static final String MSG_BAKING   = "WAITING for patch to bake a little";
    private static final String MSG_MISSING  = "MISSING";
    private static final String MSG_APPROVED = "APPROVED";
    private static final String MSG_WARNING  = "WARNING";

    private static final int BAKE_TIME = 14; // days

    private final int maxIssues;

    public Monitor(int maxIssues) {
        this.maxIssues = maxIssues;
    }

    public void runLabelReport(JiraRestClient restClient, String label) throws URISyntaxException {
        SearchRestClient searchCli = restClient.getSearchClient();
        IssueRestClient issueCli = restClient.getIssueClient();

        PrintStream out = System.out;

        out.println("JDK BACKPORTS LABEL REPORT: " + label);
        out.println("=====================================================================================================");
        out.println();
        out.println("This report shows bugs with the given label, along with their backporting status.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();
        out.println("For actionable issues, search for these strings:");
        out.println("  \"" + MSG_MISSING + "\"");
        out.println("  \"" + MSG_APPROVED + "\"");
        out.println("  \"" + MSG_WARNING + "\"");
        out.println();
        out.println("For lingering issues, search for these strings:");
        out.println("  \"" + MSG_BAKING + "\"");
        out.println();

        List<Issue> found = getIssues(searchCli, issueCli, "labels = " + label + " AND (status = Closed OR status = Resolved) AND type != Backport");

        SortedSet<TrackedIssue> issues = new TreeSet<>();
        for (Issue i : found) {
            issues.add(parseIssue(i, issueCli));
        }

        printDelimiterLine(out);
        for (TrackedIssue i : issues) {
            out.println(i.output);
            printDelimiterLine(out);
        }

        try {
            restClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void runOrphansReport(JiraRestClient restClient, String release) throws URISyntaxException {
        SearchRestClient searchCli = restClient.getSearchClient();
        IssueRestClient issueCli = restClient.getIssueClient();

        PrintStream out = System.out;

        out.println("JDK BACKPORTS ORPHANS REPORT: " + release);
        out.println("=====================================================================================================");
        out.println();
        out.println("This report shows backports that were approved, but not yet pushed.");
        out.println("Some of them are true orphans with original backport requesters never got sponsored.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        List<Issue> found = getIssues(searchCli, issueCli, "labels = jdk" + release + "u-fix-yes AND " +
                "fixVersion !~ '" + release + ".*' AND " +
                "issue not in linked-subquery(\"issue in subquery(\\\"fixVersion ~ '" + release + ".*' AND fixVersion !~ '*oracle'\\\")\")");

        SortedSet<TrackedIssue> issues = new TreeSet<>();
        for (Issue i : found) {
            issues.add(parseIssue(i, issueCli));
        }

        printDelimiterLine(out);
        for (TrackedIssue i : issues) {
            out.println(i.output);
            printDelimiterLine(out);
        }

        try {
            restClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void runPushesReport(JiraRestClient restClient, String release) throws URISyntaxException {
        SearchRestClient searchCli = restClient.getSearchClient();
        IssueRestClient issueCli = restClient.getIssueClient();
        UserRestClient userCli = restClient.getUserClient();

        PrintStream out = System.out;

        out.println("JDK BACKPORTS PUSHES REPORT: " + release);
        out.println("=====================================================================================================");
        out.println();
        out.println("This report shows who pushed the backports to the given release.");
        out.println("This usually shows who did the backporting, testing, and review work.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        List<Issue> issues = getIssues(searchCli, issueCli, "project = JDK AND fixVersion = " + release);

        Multiset<String> byPriority = TreeMultiset.create();
        Multiset<String> byComponent = HashMultiset.create();
        Multimap<String, Issue> byCommitter = TreeMultimap.create(String::compareTo, Comparator.comparing(BasicIssue::getKey));

        int filteredSyncs = 0;

        for (Issue issue : issues) {
            String committer = getPushUser(issue);
            if (!committer.equals("N/A")) { // Skip automatic syncs
                byPriority.add(issue.getPriority().getName());
                byComponent.add(extractComponents(issue));
                byCommitter.put(committer, issue);
            } else {
                filteredSyncs++;
            }
        }

        out.println("Filtered " + filteredSyncs + " automatic syncs, " + byPriority.size() + " pushes left.");
        out.println();

        out.println("Distribution by priority:");
        for (String prio : byPriority.elementSet()) {
            out.printf("   %3d: %s%n", byPriority.count(prio), prio);
        }
        out.println();

        out.println("Distribution by components:");
        for (String comp : Multisets.copyHighestCountFirst(byComponent).elementSet()) {
            out.printf("   %3d: %s%n", byComponent.count(comp), comp);
        }
        out.println();

        out.println("Distribution by email/name:");

        Multiset<String> byCompany = TreeMultiset.create();
        Map<String, Multiset<String>> byCompanyAndCommitter = new HashMap<>();

        for (String committer : byCommitter.keySet()) {
            User user = userCli.getUser(committer).claim();
            String email = user.getEmailAddress();
            String company = email.substring(email.indexOf("@"));

            byCompany.add(company, byCommitter.get(committer).size());
            Multiset<String> users = byCompanyAndCommitter.computeIfAbsent(company, k -> HashMultiset.create());
            users.add(user.getDisplayName(), byCommitter.get(committer).size());
        }

        out.printf("   %3d: <total issues>%n", byCommitter.size());
        for (String company : Multisets.copyHighestCountFirst(byCompany).elementSet()) {
            out.printf("      %3d: %s%n", byCompany.count(company), company);
            Multiset<String> committers = byCompanyAndCommitter.get(company);
            for (String committer : Multisets.copyHighestCountFirst(committers).elementSet()) {
                out.printf("         %3d: %s%n", committers.count(committer), committer);
            }
        }
        out.println();

        out.println("Commits:");
        out.println();

        for (String committer : byCommitter.keySet()) {
            User user = userCli.getUser(committer).claim();

            out.println(user.getDisplayName() + ":");
            for (Issue i : byCommitter.get(committer)) {
                out.println("  " + i.getKey() + ": " + i.getSummary());
            }
            out.println();
        }
    }

    static class RetryableIssuePromise {
        private final IssueRestClient cli;
        private final String key;
        private Promise<Issue> cur;

        public RetryableIssuePromise(IssueRestClient cli, String key) {
            this.cli = cli;
            this.key = key;
            this.cur = cli.getIssue(key);
        }

        public Issue claim() {
            for (int t = 0; t < 5; t++) {
                try {
                    return cur.claim();
                } catch (Exception e) {
                    backoff(100 + t * 500);
                    cur = cli.getIssue(key);
                }
            }
            return cur.claim();
        }

    }

    private List<Issue> getIssues(SearchRestClient searchCli, IssueRestClient cli, String query) {
        List<Issue> issues = new ArrayList<>();

        System.out.println("JIRA Query: " + query);
        System.out.println();

        SearchResult found = searchCli.searchJql(query, maxIssues, 0, null).claim();

        System.out.println("Found " + found.getTotal() + " matching issues, processing first " + found.getMaxResults() + " issues.");
        System.out.println();

        // Poor man's rate limiter:

        List<RetryableIssuePromise> batch = new ArrayList<>();
        int cnt = 0;
        for (BasicIssue i : found.getIssues()) {
            backoff(20);
            batch.add(new RetryableIssuePromise(cli, i.getKey()));
            if (cnt++ > 10) {
                for (RetryableIssuePromise p : batch) {
                    issues.add(p.claim());
                }
                batch.clear();
                cnt = 0;
            }
        }
        for (RetryableIssuePromise p : batch) {
            issues.add(p.claim());
        }

        return issues;
    }

    private static void backoff(int msec) {
        try {
            TimeUnit.MILLISECONDS.sleep(msec);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }

    private String getFixVersion(Issue issue) {
        Iterator<Version> it = issue.getFixVersions().iterator();
        if (!it.hasNext()) {
            return "N/A";
        }
        Version fixVersion = it.next();
        if (it.hasNext()) {
            throw new IllegalStateException("Multiple fix versions");
        }
        return fixVersion.getName();
    }

    private int getFixReleaseVersion(Issue issue) {
        return extractVersion(getFixVersion(issue));
    }

    private int extractVersion(String version) {
        if (version.startsWith("openjdk")) {
            version = version.substring("openjdk".length());
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

    private String parseURL(String s) {
        for (String l : s.split("\n")) {
            if (l.startsWith("URL")) {
                return l.replaceFirst("URL:", "").trim();
            }
        }
        return "N/A";
    }

    private String parseUser(String s) {
        for (String l : s.split("\n")) {
            if (l.startsWith("User")) {
                return l.replaceFirst("User:", "").trim();
            }
        }
        return "N/A";
    }

    private long parseDaysAgo(String s) {
        for (String l : s.split("\n")) {
            if (l.startsWith("Date")) {
                String d = l.replaceFirst("Date:", "").trim();
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
                return ChronoUnit.DAYS.between(LocalDate.parse(d, formatter), LocalDate.now());
            }
        }
        return 0;
    }

    private String getPushURL(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return parseURL(c.getBody());
            }
        }
        return "N/A";
    }

    private String getPushDate(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return parseDaysAgo(c.getBody()) + " day(s) ago";
            }
        }
        return "N/A";
    }

    private String getPushUser(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return parseUser(c.getBody());
            }
        }
        return "N/A";
    }

    private long getPushDaysAgo(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return parseDaysAgo(c.getBody());
            }
        }
        return 0;
    }

    private String extractComponents(Issue issue) {
        StringJoiner joiner = new StringJoiner(",");
        for (BasicComponent c : issue.getComponents()) {
            joiner.add(c.getName());
        }
        return joiner.toString();
    }

    private enum Actionable {
        NONE,
        WAITING,
        ACTIONABLE,
        ;

        public Actionable mix(Actionable a) {
            return Actionable.values()[Math.max(ordinal(), a.ordinal())];
        }
    }

    private static class TrackedIssue implements Comparable<TrackedIssue> {
        final String output;
        final long age;
        final Actionable actionable;

        public TrackedIssue(String output, long age, Actionable actionable) {
            this.output = output;
            this.age = age;
            this.actionable = actionable;
        }

        @Override
        public int compareTo(TrackedIssue other) {
            int v1 = Integer.compare(other.actionable.ordinal(), this.actionable.ordinal());
            if (v1 != 0) {
                return v1;
            }
            int v2 = Long.compare(other.age, this.age);
            if (v2 != 0) {
                return v2;
            }
            return this.output.compareTo(other.output);
        }
    }

    private TrackedIssue parseIssue(Issue issue, IssueRestClient cli) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        Set<Integer> affectedReleases = new HashSet<>();
        for (Version v : issue.getAffectedVersions()) {
            int ver = extractVersion(v.getName());
            affectedReleases.add(ver);
        }

        pw.println();
        pw.println(issue.getKey() + ": " + issue.getSummary());
        pw.println();
        pw.println("  Original Bug:");
        pw.println("      URL: " + Main.JIRA_URL + "browse/" + issue.getKey());
        pw.println("      Reporter: " + (issue.getReporter() != null ? issue.getReporter().getDisplayName() : "None"));
        pw.println("      Assignee: " + (issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : "None"));
        pw.println("      Priority: " + issue.getPriority().getName());
        pw.println("      Components: " + extractComponents(issue));
        pw.println();

        SortedMap<Integer, List<String>> results = new TreeMap<>();

        pw.println("  Original Fix:");

        long daysAgo = getPushDaysAgo(issue);

        pw.printf("  %8s: %10s, %s, %s%n", getFixVersion(issue), issue.getKey(), getPushURL(issue), getPushDate(issue));
        recordIssue(results, issue, false);
        pw.println();

        if (affectedReleases.isEmpty()) {
            pw.println("  " + MSG_WARNING + ": Affected versions is not set.");
            pw.println();
        }

        pw.println("  Backports and Forwardports:");

        for (IssueLink link : issue.getIssueLinks()) {
            if (link.getIssueLinkType().getName().equals("Backport")) {
                String linkKey = link.getTargetIssueKey();
                Issue backport = cli.getIssue(linkKey).claim();
                recordIssue(results, backport, true);
            }
        }

        int origRel = getFixReleaseVersion(issue);
        int highRel = results.isEmpty() ? origRel : results.lastKey();

        Actionable actionable = Actionable.NONE;

        boolean printed = false;
        for (int release : new int[]{13, 12, 11, 8}) {
            List<String> lines = results.get(release);
            if (lines != null) {
                if (release != origRel) {
                    Collections.sort(lines, Comparator.reverseOrder());

                    boolean first = true;
                    for (String line : lines) {
                        if (first) {
                            pw.printf("  %8s: ", release);
                            first = false;
                        } else {
                            pw.printf("  %8s  ", "");
                        }
                        pw.println(line);
                        printed = true;
                    }
                }
            } else if (release <= highRel) {
                pw.printf("  %8s: ", release);
                switch (release) {
                    case 8: {
                        if (!affectedReleases.contains(8)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else if (daysAgo < BAKE_TIME) {
                            actionable = actionable.mix(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else {
                            actionable = actionable.mix(Actionable.ACTIONABLE);
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    case 11: {
                        if (issue.getLabels().contains("jdk11u-fix-yes")) {
                            actionable = actionable.mix(Actionable.ACTIONABLE);
                            pw.println(MSG_APPROVED + ": jdk11u-fix-yes is set");
                        } else if (issue.getLabels().contains("jdk11u-fix-no")) {
                            pw.println("REJECTED: jdk11u-fix-no is set");
                        } else if (issue.getLabels().contains("jdk11u-fix-request")) {
                            pw.println("Requested: jdk11u-fix-request is set");
                            actionable = actionable.mix(Actionable.WAITING);
                        } else if (!affectedReleases.contains(11)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else if (daysAgo < BAKE_TIME) {
                            actionable = actionable.mix(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else {
                            actionable = actionable.mix(Actionable.ACTIONABLE);
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    case 12: {
                        if (issue.getLabels().contains("jdk12u-fix-yes")) {
                            actionable = actionable.mix(Actionable.ACTIONABLE);
                            pw.println(MSG_APPROVED + ": jdk12u-fix-yes is set");
                        } else if (issue.getLabels().contains("jdk12u-fix-no")) {
                            pw.println("REJECTED: jdk12u-fix-no is set");
                        } else if (issue.getLabels().contains("jdk12u-fix-request")) {
                            pw.println("Requested: jdk12u-fix-request is set");
                            actionable = actionable.mix(Actionable.WAITING);
                        } else if (!affectedReleases.contains(12)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else if (daysAgo < BAKE_TIME) {
                            actionable = actionable.mix(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else {
                            actionable = actionable.mix(Actionable.ACTIONABLE);
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    default:
                        pw.println("Unknown release: " + release);
                }
                printed = true;
            }
        }

        if (!printed) {
            pw.println("      None.");
        }

        pw.println();

        return new TrackedIssue(sw.toString(), daysAgo, actionable);
    }

    private void printDelimiterLine(PrintStream pw) {
        pw.println("-----------------------------------------------------------------------------------------------------");
    }

    private void recordIssue(Map<Integer, List<String>> results, Issue issue, boolean bypassEmpty) {
        String fixVersion = getFixVersion(issue);
        if (fixVersion.contains("-oracle")) return;

        String pushURL = getPushURL(issue);
        if (bypassEmpty && pushURL.equals("N/A")) return; // skip automatic syncs and closed backports

        String line = String.format("%s, %10s, %s, %s", fixVersion, issue.getKey(), pushURL, getPushDate(issue));
        int ver = extractVersion(fixVersion);
        List<String> list = results.computeIfAbsent(ver, k -> new ArrayList<>());
        list.add(line);
    }

}
