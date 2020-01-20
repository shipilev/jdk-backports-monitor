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
package org.openjdk.backports.jira;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.domain.*;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.util.*;

public class Accessors {

    public static String getFixVersion(Issue issue) {
        Iterator<Version> it = issue.getFixVersions().iterator();
        if (!it.hasNext()) {
            return "N/A";
        }
        Version fixVersion = it.next();
        if (it.hasNext()) {
            throw new IllegalStateException("Multiple fix versions: " + issue.getKey());
        }
        return fixVersion.getName();
    }

    public static List<String> getFixVersions(Issue issue) {
        List<String> res = new ArrayList<>();
        for (Version ver : issue.getFixVersions()) {
            res.add(ver.getName());
        }
        return res;
    }

    public static String getPushURL(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return Parsers.parseURL(c.getBody());
            }
        }
        return "N/A";
    }

    public static String getPushDate(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return Parsers.parseDaysAgo(c.getBody()) + " day(s) ago";
            }
        }
        return "N/A";
    }

    public static String getPushUser(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return Parsers.parseUser(c.getBody());
            }
        }
        return "N/A";
    }

    public static long getPushDaysAgo(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return Parsers.parseDaysAgo(c.getBody());
            }
        }
        return -1;
    }

    public static long getPushSecondsAgo(Issue issue) {
        for (Comment c : issue.getComments()) {
            if (c.getAuthor().getName().equals("hgupdate")) {
                return Parsers.parseSecondsAgo(c.getBody());
            }
        }
        return -1;
    }

    public static String extractComponents(Issue issue) {
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

    private static String getStatus(Issue issue) {
        Status s = issue.getStatus();
        if (s == null) {
            return "";
        }

        String n = s.getName();
        if (n == null) {
            return "";
        }
        return n;
    }

    private static String getResolution(Issue issue) {
        Resolution r = issue.getResolution();
        if (r == null) {
            return "";
        }

        String n = r.getName();
        if (n == null) {
            return "";
        }
        return n;
    }

    public static boolean isDelivered(Issue issue) {
        switch (getStatus(issue)) {
            case "Closed":
            case "Resolved":
                break;
            case "Withdrawn":
                return false;
            default:
                // Default to "not delivered"
                return false;
        }

        switch (getResolution(issue)) {
            case "Withdrawn":
            case "Won't Fix":
            case "Duplicate":
            case "Cannot Reproduce":
            case "Not an Issue":
            case "Other":
                return false;
            case "Resolved":
            case "Fixed":
                return true;
            default:
                // Default to "delivered"
                return true;
        }
    }

    public static boolean isReleaseNoteTag(Issue issue) {
        return issue.getLabels().contains("release-note");
    }

    public static boolean isReleaseNote(Issue issue) {
        // Brilliant, we cannot trust "release-note" tags?
        //   See: https://mail.openjdk.java.net/pipermail/jdk-dev/2019-July/003083.html
        return (isReleaseNoteTag(issue) || issue.getSummary().toLowerCase().trim().startsWith("release note"))
                && isDelivered(issue);
    }

    public static Collection<Issue> getReleaseNotes(IssueRestClient cli, Issue start) {
        List<RetryableIssuePromise> relnotes = new ArrayList<>();
        Set<Issue> releaseNotes = new HashSet<>();

        // Direct hit?
        if (isReleaseNote(start)) {
            releaseNotes.add(start);
        }

        // Search in sub-tasks
        for (Subtask link : start.getSubtasks()) {
            String linkKey = link.getIssueKey();
            relnotes.add(new RetryableIssuePromise(null, cli, linkKey));
        }

        // Search in related issues
        for (IssueLink link : start.getIssueLinks()) {
            if (link.getIssueLinkType().getName().equals("Relates")) {
                String linkKey = link.getTargetIssueKey();
                relnotes.add(new RetryableIssuePromise(null, cli, linkKey));
            }
        }

        for (RetryableIssuePromise p : relnotes) {
            Issue i = p.claim();
            if (isReleaseNote(i)) {
                releaseNotes.add(i);
            }
        }

        return releaseNotes;
    }

}
