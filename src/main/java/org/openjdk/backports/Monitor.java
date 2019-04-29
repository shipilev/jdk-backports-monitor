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
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.*;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Monitor {

    private static final String MSG_NOT_AFFECTED = "Not affected";
    private static final String MSG_BAKING   = "WAITING for patch to bake a little";
    private static final String MSG_MISSING  = "MISSING";
    private static final String MSG_APPROVED = "APPROVED";
    private static final String MSG_WARNING  = "WARNING";

    private static final int BAKE_TIME = 10; // days

    private static final int PAGE_SIZE = 50;

    private static final int VER_INDENT = 10;

    private final UserCache users;
    private final HgDB hgDB;

    public Monitor(JiraRestClient restClient, HgDB hgDB) {
        this.hgDB = hgDB;
        this.users = new UserCache(restClient.getUserClient());
    }

    public void runLabelReport(JiraRestClient restClient, String label, Actionable minLevel) throws URISyntaxException {
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

        List<Issue> found = getIssues(searchCli, issueCli, "labels = " + label +
                " AND (status in (Closed, Resolved))" +
                " AND (resolution not in (\"Won't Fix\", Duplicate, \"Cannot Reproduce\", \"Not an Issue\", Withdrawn))" +
                " AND type != Backport");

        SortedSet<TrackedIssue> issues = new TreeSet<>();
        for (Issue i : found) {
            issues.add(parseIssue(i, issueCli));
        }

        printDelimiterLine(out);
        for (TrackedIssue i : issues) {
            if (i.actionable.ordinal() < minLevel.ordinal()) continue;
            out.println(i.output);
            printDelimiterLine(out);
        }
    }

    public void runIssueReport(JiraRestClient restClient, String issueId) throws URISyntaxException {
        IssueRestClient issueCli = restClient.getIssueClient();

        PrintStream out = System.out;

        out.println("JDK BACKPORTS ISSUE REPORT: " + issueId);
        out.println("=====================================================================================================");
        out.println();
        out.println("This report shows a single issue status.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        Issue issue = issueCli.getIssue(issueId).claim();

        TrackedIssue trackedIssue = parseIssue(issue, issueCli);

        printDelimiterLine(out);
        out.println(trackedIssue.output);
    }

    public void runPendingPushReport(JiraRestClient restClient, String release) throws URISyntaxException {
        SearchRestClient searchCli = restClient.getSearchClient();
        IssueRestClient issueCli = restClient.getIssueClient();

        PrintStream out = System.out;

        out.println("JDK BACKPORTS PENDING PUSH REPORT: " + release);
        out.println("=====================================================================================================");
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

        List<Issue> found = getIssues(searchCli, issueCli, query);

        SortedSet<TrackedIssue> issues = new TreeSet<>();
        for (Issue i : found) {
            issues.add(parseIssue(i, issueCli));
        }

        printDelimiterLine(out);
        for (TrackedIssue i : issues) {
            out.println(i.output);
            printDelimiterLine(out);
        }
    }

    public void runPushesReport(JiraRestClient restClient, String release) throws URISyntaxException {
        SearchRestClient searchCli = restClient.getSearchClient();
        IssueRestClient issueCli = restClient.getIssueClient();

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
        Set<Issue> byTime = new TreeSet<>(Comparator.comparing(Monitor::getPushSecondsAgo).thenComparing(Issue::getKey));

        int filteredSyncs = 0;

        for (Issue issue : issues) {
            String committer = getPushUser(issue);
            if (!committer.equals("N/A")) { // Skip automatic syncs
                byPriority.add(issue.getPriority().getName());
                byComponent.add(extractComponents(issue));
                byCommitter.put(committer, issue);
                byTime.add(issue);
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
        printByEmailName(out, byCommitter);
        out.println();

        out.println("Chronological push log:");
        out.println();

        for (Issue i : byTime) {
            String pushUser = getPushUser(i);
            out.printf("  %3d day(s) ago, %" + users.maxDisplayName() + "s, %" + users.maxAffiliation() + "s, %s: %s%n",
                    TimeUnit.SECONDS.toDays(getPushSecondsAgo(i)),
                    users.getDisplayName(pushUser), users.getAffiliation(pushUser),
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

        out.printf("   %3d: <total issues>%n", byCommitter.size());
        for (String company : Multisets.copyHighestCountFirst(byCompany).elementSet()) {
            out.printf("      %3d: %s%n", byCompany.count(company), company);
            Multiset<String> committers = byCompanyAndCommitter.get(company);
            for (String committer : Multisets.copyHighestCountFirst(committers).elementSet()) {
                out.printf("         %3d: %s%n", committers.count(committer), committer);
            }
        }
    }

    private RetryableIssuePromise getParent(IssueRestClient cli, Issue start) {
        List<RetryableIssuePromise> backports = new ArrayList<>();
        for (IssueLink link : start.getIssueLinks()) {
            if (link.getIssueLinkType().getName().equals("Backport")) {
                String linkKey = link.getTargetIssueKey();
                backports.add(new RetryableIssuePromise(cli, linkKey));
            }
        }

        // If there is only a single "Backport link", report it as parent
        if (backports.size() == 1) {
            return backports.get(0);
        } else {
            return null;
        }
    }

    public void runReleaseNotesReport(JiraRestClient restClient, String release) throws URISyntaxException {
        SearchRestClient searchCli = restClient.getSearchClient();
        IssueRestClient issueCli = restClient.getIssueClient();

        PrintStream out = System.out;

        out.println("RELEASE NOTES FOR: " + release);
        out.println("=====================================================================================================");
        out.println();
        out.println("Notes generated: " + new Date());
        out.println();

        List<Issue> issues = getIssues(searchCli, issueCli, "project = JDK AND fixVersion = " + release);

        Multimap<String, Issue> byComponent = TreeMultimap.create(String::compareTo, Comparator.comparing(BasicIssue::getKey));
        Multimap<String, Issue> byCommitter = TreeMultimap.create(String::compareTo, Comparator.comparing(BasicIssue::getKey));

        int filteredSyncs = 0;

        for (Issue issue : issues) {
            String committer = getPushUser(issue);
            if (!committer.equals("N/A")) { // Skip automatic syncs
                byComponent.put(extractComponents(issue), issue);
                byCommitter.put(committer, issue);
            } else {
                filteredSyncs++;
            }
        }

        out.println("Filtered " + filteredSyncs + " automatic syncs, " + byComponent.size() + " pushes left.");
        out.println();

        out.println("Hint: Parentheses mention the release version for the original fix.");
        out.println("Hint: Prefix bug IDs with " + Main.JIRA_URL + "browse/ to reach the relevant JIRA entry.");
        out.println();

        out.println("Changes by component:");
        out.println();

        for (String component : byComponent.keySet()) {
            out.println("  " + component + ":");

            Map<Issue, RetryableIssuePromise> parents = new HashMap<>();
            for (Issue i : byComponent.get(component)) {
                parents.put(i, getParent(issueCli, i));
            }

            Multimap<String, Issue> byOrigRelease = TreeMultimap.create(String::compareTo, Comparator.comparing(BasicIssue::getKey));
            for (Issue i : byComponent.get(component)) {
                RetryableIssuePromise promise = parents.get(i);
                if (promise != null) {
                    Issue p = promise.claim();
                    byOrigRelease.put("(" + getFixVersion(p) + ")", i);
                } else {
                    byOrigRelease.put("", i);
                }
            }

            for (String origRelease : byOrigRelease.keySet()) {
                for (Issue i : byOrigRelease.get(origRelease)) {
                    out.printf("    %15s %s: %s%n", origRelease, i.getKey(), i.getSummary());
                }
            }
            out.println();
        }
        out.println();

        out.println("Distribution by email/name:");
        printByEmailName(out, byCommitter);
        out.println();
    }

    public void runFilterReport(JiraRestClient restClient, long filterId) throws URISyntaxException {
        SearchRestClient searchCli = restClient.getSearchClient();
        IssueRestClient issueCli = restClient.getIssueClient();

        PrintStream out = System.out;

        Filter filter = searchCli.getFilter(filterId).claim();

        out.println("JDK BACKPORTS FILTER REPORT");
        out.println("=====================================================================================================");
        out.println();
        out.println("This report shows brief list of issues matching the filter.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        List<Issue> issues = getIssues(searchCli, issueCli, filter.getJql());

        out.println("Filter: " + filter.getName());
        out.println("Filter URL: " + Main.JIRA_URL + "issues/?filter=" + filterId);
        out.println();
        out.println("Hint: Prefix bug IDs with " + Main.JIRA_URL + "browse/ to reach the relevant JIRA entry.");
        out.println();

        for (Issue i : issues) {
            out.println("  " + i.getKey() + ": " + i.getSummary());
        }
    }

    static class UserCache {
        private final UserRestClient client;
        private final Map<String, User> users;
        private final Map<String, String> displayNames;
        private final Map<String, String> affiliations;

        public UserCache(UserRestClient client) {
            this.client = client;
            this.users = new HashMap<>();
            this.displayNames = new HashMap<>();
            this.affiliations = new HashMap<>();
        }

        public User getUser(String id) {
            return users.computeIfAbsent(id, u -> client.getUser(u).claim());
        }

        public String getDisplayName(String id) {
            return displayNames.computeIfAbsent(id, u -> getUser(u).getDisplayName());
        }

        public String getAffiliation(String id) {
            return affiliations.computeIfAbsent(id, u -> {
                String email = getUser(u).getEmailAddress();
                return email.substring(email.indexOf("@"));
            });
        }

        public int maxAffiliation() {
            int r = 0;
            for (String v : affiliations.values()) {
                r = Math.max(r, v.length());
            }
            return r;
        }

        public int maxDisplayName() {
            int r = 0;
            for (String v : displayNames.values()) {
                r = Math.max(r, v.length());
            }
            return r;
        }
    }

    private String rewrap(String src, int width) {
        StringBuilder result = new StringBuilder();
        String[] words = src.split("[ \n]");
        String line = "";
        int cols = 0;
        for (String w : words) {
            cols += w.length();
            line += w + " ";
            if (cols > width) {
                result.append(line);
                result.append("\n");
                line = "";
                cols = 0;
            }
        }
        if (!line.trim().isEmpty()) {
            result.append(line);
            result.append("\n");
        }
        return result.toString();
    }

    private List<Issue> getIssues(SearchRestClient searchCli, IssueRestClient cli, String query) {
        List<Issue> issues = new ArrayList<>();

        System.out.println("JIRA Query: " + rewrap(query, 80));
        System.out.println();

        SearchResult poll = new RetryableSearchPromise(searchCli, query, 1, 0).claim();
        int total = poll.getTotal();

        List<RetryableSearchPromise> searchPromises = new ArrayList<>();
        for (int cnt = 0; cnt < total; cnt += PAGE_SIZE) {
            searchPromises.add(new RetryableSearchPromise(searchCli, query, PAGE_SIZE, cnt));
            System.out.println("Acquiring page [" + cnt  + ", " + (cnt + PAGE_SIZE) + "] (total: " + total + ")");
        }

        for (RetryableSearchPromise sp : searchPromises) {
            SearchResult found = sp.claim();

            List<RetryableIssuePromise> batch = new ArrayList<>();
            for (BasicIssue i : found.getIssues()) {
                batch.add(new RetryableIssuePromise(cli, i.getKey()));
            }
            for (RetryableIssuePromise ip : batch) {
                issues.add(ip.claim());
            }

            System.out.println("Loaded " + issues.size() + "/" + total + " matching issues.");
        }

        System.out.println();

        return issues;
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

    private String getPushURL(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return Parsers.parseURL(c.getBody());
            }
        }
        return "N/A";
    }

    private String getPushDate(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return Parsers.parseDaysAgo(c.getBody()) + " day(s) ago";
            }
        }
        return "N/A";
    }

    private String getPushUser(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return Parsers.parseUser(c.getBody());
            }
        }
        return "N/A";
    }

    private static long getPushDaysAgo(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return Parsers.parseDaysAgo(c.getBody());
            }
        }
        return -1;
    }

    private static long getPushSecondsAgo(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return Parsers.parseSecondsAgo(c.getBody());
            }
        }
        return -1;
    }

    private String extractComponents(Issue issue) {
        StringJoiner joiner = new StringJoiner("/");
        for (BasicComponent c : issue.getComponents()) {
            joiner.add(c.getName());
        }
        IssueField subcomponent = issue.getFieldByName("Subcomponent");
        if (subcomponent != null && subcomponent.getValue() != null) {
            try {
                JSONObject o = new JSONObject(subcomponent.getValue().toString());
                joiner.add(o.get("name").toString());
            } catch (JSONException e) {
                // Do nothing
            }
        }
        return joiner.toString();
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
        Actionable actionable = Actionable.NONE;

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
        pw.println("      Components: " + extractComponents(issue));
        pw.println();

        SortedMap<Integer, List<String>> results = new TreeMap<>();

        pw.println("  Original Fix:");

        long daysAgo = getPushDaysAgo(issue);

        pw.printf("  %" + VER_INDENT + "s: %10s, %s, %s%n", getFixVersion(issue), issue.getKey(), getPushURL(issue), getPushDate(issue));
        recordIssue(results, issue, true);
        pw.println();

        Set<Integer> affectedReleases = new HashSet<>();
        Set<Integer> affectedShenandoah = new HashSet<>();
        for (Version v : issue.getAffectedVersions()) {
            String verName = v.getName();
            if (verName.endsWith("-shenandoah")) {
                int ver = Parsers.parseVersionShenandoah(verName);
                if (ver < 0) {
                    pw.println("  " + MSG_WARNING + ": Unknown affected version: " + verName);
                    pw.println();
                    actionable = actionable.mix(Actionable.CRITICAL);
                }
                affectedShenandoah.add(ver);
            } else {
                int ver = Parsers.parseVersion(verName);
                if (ver < 0) {
                    pw.println("  " + MSG_WARNING + ": Unknown affected version: " + verName);
                    pw.println();
                    actionable = actionable.mix(Actionable.CRITICAL);
                }
                affectedReleases.add(ver);
            }
        }

        if (affectedReleases.isEmpty()) {
            pw.println("  " + MSG_WARNING + ": Affected versions is not set.");
            pw.println();
            actionable = actionable.mix(Actionable.CRITICAL);
        }

        pw.println("  Backports and Forwardports:");

        List<RetryableIssuePromise> links = new ArrayList<>();
        for (IssueLink link : issue.getIssueLinks()) {
            if (link.getIssueLinkType().getName().equals("Backport")) {
                String linkKey = link.getTargetIssueKey();
                links.add(new RetryableIssuePromise(cli, linkKey));
            }
        }
        for (RetryableIssuePromise p : links) {
            recordIssue(results, p.claim(), true);
        }

        int origRel = Parsers.parseVersion(getFixVersion(issue));
        int highRel = results.isEmpty() ? origRel : results.lastKey();

        boolean printed = false;
        for (int release : new int[]{13, 12, 11, 8, 7}) {
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
                            actionable = actionable.mix(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else {
                            actionable = actionable.mix(Actionable.MISSING);
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    case 8: {
                        if (issue.getLabels().contains("jdk8u-fix-yes")) {
                            actionable = actionable.mix(Actionable.PUSHABLE);
                            pw.println(MSG_APPROVED + ": jdk8u-fix-yes is set");
                        } else if (issue.getLabels().contains("jdk8u-fix-no")) {
                            pw.println("REJECTED: jdk8u-fix-no is set");
                        } else if (issue.getLabels().contains("jdk8u-fix-request")) {
                            pw.println("Requested: jdk8u-fix-request is set");
                            actionable = actionable.mix(Actionable.REQUESTED);
                        } else if (!affectedReleases.contains(8)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else if (daysAgo >= 0 && daysAgo < BAKE_TIME) {
                            actionable = actionable.mix(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else {
                            actionable = actionable.mix(Actionable.MISSING);
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    case 11: {
                        if (issue.getLabels().contains("jdk11u-fix-yes")) {
                            actionable = actionable.mix(Actionable.PUSHABLE);
                            pw.println(MSG_APPROVED + ": jdk11u-fix-yes is set");
                        } else if (issue.getLabels().contains("jdk11u-fix-no")) {
                            pw.println("REJECTED: jdk11u-fix-no is set");
                        } else if (issue.getLabels().contains("jdk11u-fix-request")) {
                            pw.println("Requested: jdk11u-fix-request is set");
                            actionable = actionable.mix(Actionable.REQUESTED);
                        } else if (!affectedReleases.contains(11)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else if (daysAgo >= 0 && daysAgo < BAKE_TIME) {
                            actionable = actionable.mix(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else {
                            actionable = actionable.mix(Actionable.MISSING);
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    case 12: {
                        if (issue.getLabels().contains("jdk12u-fix-yes")) {
                            actionable = actionable.mix(Actionable.PUSHABLE);
                            pw.println(MSG_APPROVED + ": jdk12u-fix-yes is set");
                        } else if (issue.getLabels().contains("jdk12u-fix-no")) {
                            pw.println("REJECTED: jdk12u-fix-no is set");
                        } else if (issue.getLabels().contains("jdk12u-fix-request")) {
                            pw.println("Requested: jdk12u-fix-request is set");
                            actionable = actionable.mix(Actionable.REQUESTED);
                        } else if (!affectedReleases.contains(12)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else if (daysAgo >= 0 && daysAgo < BAKE_TIME) {
                            actionable = actionable.mix(Actionable.WAITING);
                            pw.println(MSG_BAKING + ": " + (BAKE_TIME - daysAgo) + " days more");
                        } else {
                            actionable = actionable.mix(Actionable.MISSING);
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

        if (issue.getLabels().contains("gc-shenandoah")) {
            pw.println();
            pw.println("  Shenandoah Backports:");

            String synopsis = issue.getKey().replaceFirst("JDK-", "[backport] ");

            for (int ver : new int[]{11, 8}) {
                if (!affectedShenandoah.contains(ver)) {
                    pw.println(MSG_NOT_AFFECTED);
                } else {
                    switch (ver) {
                        case 8:
                            actionable = printHgStatus(actionable, pw, synopsis,
                                    "8-sh", "shenandoah/jdk8");
                            actionable = printHgStatus(actionable, pw, synopsis,
                                    "8-aarch64", "aarch64-port/jdk8u-shenandoah");
                            break;
                        case 11:
                            actionable = printHgStatus(actionable, pw, synopsis,
                                    "11-sh", "shenandoah/jdk11");
                            break;
                        default:
                            pw.println("Unknown release: " + ver);
                    }
                }
            }
        }

        pw.println();

        return new TrackedIssue(sw.toString(), daysAgo, actionable);
    }

    private Actionable printHgStatus(Actionable actionable, PrintWriter pw, String synopsis, String label, String repo) {
        pw.printf("  %" + VER_INDENT + "s: ", label);
        if (!hgDB.hasRepo(repo)) {
            pw.println(MSG_WARNING + ": No Mercurial data available to judge");
        } else {
            List<HgDB.Record> rs = hgDB.search(repo, synopsis);
            if (!rs.isEmpty()) {
                for (HgDB.Record r : rs) pw.println(r.toString());
            } else {
                actionable = actionable.mix(Actionable.MISSING);
                pw.println(MSG_MISSING);
            }
        }
        return actionable;
    }

    private void printDelimiterLine(PrintStream pw) {
        pw.println("-----------------------------------------------------------------------------------------------------");
    }

    private void recordIssue(Map<Integer, List<String>> results, Issue issue, boolean bypassEmpty) {
        String fixVersion = getFixVersion(issue);

        // This is Oracle-internal push. Ignore.
        if (fixVersion.contains("-oracle")) return;

        String pushURL = getPushURL(issue);

        if (pushURL.equals("N/A")) {
            switch (fixVersion) {
                case "11.0.1":
                case "12.0.1":
                    // Oh yeah, issues would have these versions set as "fix", but there would
                    // be no public pushes until CPU releases. Awesome.
                    pushURL = "<'kinda open, but not quite' backport>";
                    break;
                default:
                    break;
            }
        }

        if (bypassEmpty && pushURL.equals("N/A")) {
            return;
        }

        String line = String.format("%s, %10s, %s, %s", fixVersion, issue.getKey(), pushURL, getPushDate(issue));
        int ver = Parsers.parseVersion(fixVersion);
        List<String> list = results.computeIfAbsent(ver, k -> new ArrayList<>());
        list.add(line);
    }

}
