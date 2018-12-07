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
        String fullVersion = getFixVersion(issue);
        int dotIdx = fullVersion.indexOf(".");
        if (dotIdx != -1) {
            try {
                return Integer.parseInt(fullVersion.substring(0, dotIdx));
            } catch (Exception e) {
                return -1;
            }
        }
        int uIdx = fullVersion.indexOf("u");
        if (uIdx != -1) {
            try {
                return Integer.parseInt(fullVersion.substring(0, uIdx));
            } catch (Exception e) {
                return -1;
            }
        }

        try {
            return Integer.parseInt(fullVersion);
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

        pw.println("  Original Fix:");

        pw.printf("  %8s: %10s, %s, %s%n", getFixVersion(issue), issue.getKey(), getPushURL(issue), getPushDate(issue));

        Set<Integer> fixedReleases = new HashSet<>();
        boolean printed = false;

        pw.println();
        pw.println("  Completed Backports:");
        Iterable<IssueLink> links = issue.getIssueLinks();
        for (IssueLink link : links) {
            if (link.getIssueLinkType().getName().equals("Backport")) {
                String linkKey = link.getTargetIssueKey();
                Issue backport = cli.getIssue(linkKey).claim();

                fixedReleases.add(getFixReleaseVersion(backport));

                pw.printf("  %8s: %10s, %s, %s%n", getFixVersion(backport), linkKey, getPushURL(backport), getPushDate(backport));
                printed = true;
            }
        }

        if (!printed) {
            pw.println("      None.");
        }

        printed = false;
        pw.println();
        pw.println("  Missing/Pending Backports:");

        int origRel = getFixReleaseVersion(issue);
        if (origRel == -1) {
            throw new IllegalStateException("Cannot parse fix release version: " + getFixVersion(issue));
        }

        if (origRel > 11 && !fixedReleases.contains(11)) {
            pw.printf("  %8s: ", "11");
            if (issue.getLabels().contains("jdk11u-fix-yes")) {
                pw.println("Approved to push: jdk11u-fix-yes is set");
            } else if (issue.getLabels().contains("jdk11u-fix-no")) {
                pw.println("REJECTED: jdk11u-fix-no is set");
            } else if (issue.getLabels().contains("jdk11u-fix-request")) {
                pw.println("Requested: jdk11u-fix-request is set");
            } else {
                pw.println("MISSING");
            }
            printed = true;
        }

        if (origRel > 8 && !fixedReleases.contains(8)) {
            pw.printf("  %8s: ", "8");
            pw.println("MISSING");
            printed = true;
        }

        if (!printed) {
            pw.println("      None.");
        }

        pw.println();
        pw.println("-----------------------------------------------------------------------------------------------------");
    }

}
