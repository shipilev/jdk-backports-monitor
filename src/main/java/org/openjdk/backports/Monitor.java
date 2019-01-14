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

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.domain.*;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class Monitor {
    public static final String JIRA_URL = "https://bugs.openjdk.java.net/";

    public static final String MSG_NOT_AFFECTED = "Not affected";
    public static final String MSG_MISSING  = "MISSING";
    public static final String MSG_APPROVED = "APPROVED";

    private final Options options;

    public Monitor(Options options) {
        this.options = options;
    }

    public void run() throws URISyntaxException {
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(options.getAuthProps())){
            p.load(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String user = p.getProperty("user");
        String pass = p.getProperty("pass");

        if (user == null || pass == null) {
            throw new IllegalStateException("user/pass keys are missing in auth file: " + options.getAuthProps());
        }

        JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
        JiraRestClient restClient = factory.createWithBasicHttpAuthentication(new URI(JIRA_URL), user, pass);

        IssueRestClient cli = restClient.getIssueClient();

        System.out.println("JDK BACKPORTS MONITORING REPORT");
        System.out.println("=====================================================================================================");
        System.out.println();

        System.out.println("Search for \"" + MSG_MISSING + "\", \"" + MSG_APPROVED + "\", etc. to discover actionable issues.");
        System.out.println();

        System.out.println("Closed bugs with \"redhat-openjdk\" label:");
        System.out.println();

        SearchResult rhIssues = restClient.getSearchClient().searchJql("labels = redhat-openjdk AND (status = Closed OR status = Resolved) AND type != Backport",
                options.getMaxIssues(), 0, null).claim();
        for (BasicIssue i : rhIssues.getIssues()) {
            printIssue(System.out, cli.getIssue(i.getKey()).claim(), cli);
        }

        try {
            restClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFixVersion(Issue issue) {
        Iterator<Version> it = issue.getFixVersions().iterator();
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

    private String parseDate(String s) {
        for (String l : s.split("\n")) {
            if (l.startsWith("Date")) {
                return l.replaceFirst("Date:", "").trim();
            }
        }
        return "N/A";
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
                return parseDate(c.getBody());
            }
        }
        return "N/A";
    }

    private String extractComponents(Issue issue) {
        StringJoiner joiner = new StringJoiner(",");
        for (BasicComponent c : issue.getComponents()) {
            joiner.add(c.getName());
        }
        return joiner.toString();
    }

    private void printIssue(PrintStream pw, Issue issue, IssueRestClient cli) {
        Set<Integer> affectedReleases = new HashSet<>();
        for (Version v : issue.getAffectedVersions()) {
            int ver = extractVersion(v.getName());
            affectedReleases.add(ver);
        }

        pw.println();
        pw.println(issue.getKey() + ": " + issue.getSummary());
        pw.println();
        pw.println("  Original Bug:");
        pw.println("      URL: " + JIRA_URL + "browse/" + issue.getKey());
        pw.println("      Reporter: " + issue.getReporter().getDisplayName());
        pw.println("      Assignee: " + issue.getAssignee().getDisplayName());
        pw.println("      Priority: " + issue.getPriority().getName());
        pw.println("      Components: " + extractComponents(issue));
        pw.println();

        SortedMap<Integer, List<String>> results = new TreeMap<>();

        pw.println("  Original Fix:");

        pw.printf("  %8s: %10s, %s, %s%n", getFixVersion(issue), issue.getKey(), getPushURL(issue), getPushDate(issue));
        recordIssue(results, issue);

        pw.println();
        pw.println("  Backports and Forwardports:");

        for (IssueLink link : issue.getIssueLinks()) {
            if (link.getIssueLinkType().getName().equals("Backport")) {
                String linkKey = link.getTargetIssueKey();
                Issue backport = cli.getIssue(linkKey).claim();
                recordIssue(results, backport);
            }
        }

        int origRel = getFixReleaseVersion(issue);
        int highRel = results.lastKey();

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
                        } else {
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    case 11: {
                        if (issue.getLabels().contains("jdk11u-fix-yes")) {
                            pw.println(MSG_APPROVED + ": jdk11u-fix-yes is set");
                        } else if (issue.getLabels().contains("jdk11u-fix-no")) {
                            pw.println("REJECTED: jdk11u-fix-no is set");
                        } else if (issue.getLabels().contains("jdk11u-fix-request")) {
                            pw.println("Requested: jdk11u-fix-request is set");
                        } else if (!affectedReleases.contains(11)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else {
                            pw.println(MSG_MISSING);
                        }
                        break;
                    }
                    case 12: {
                        if (!affectedReleases.contains(12)) {
                            pw.println(MSG_NOT_AFFECTED);
                        } else {
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
        pw.println("-----------------------------------------------------------------------------------------------------");
    }

    private void recordIssue(Map<Integer, List<String>> results, Issue issue) {
        String line = String.format("%s, %10s, %s, %s", getFixVersion(issue), issue.getKey(), getPushURL(issue), getPushDate(issue));
        int ver = extractVersion(getFixVersion(issue));
        List<String> list = results.computeIfAbsent(ver, k -> new ArrayList<>());
        list.add(line);
    }

}
