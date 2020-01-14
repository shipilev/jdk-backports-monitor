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
package org.openjdk.backports.report;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.openjdk.backports.Main;
import org.openjdk.backports.StringUtils;
import org.openjdk.backports.hg.HgDB;
import org.openjdk.backports.jira.Accessors;
import org.openjdk.backports.jira.Versions;

import java.util.*;

public class ReleaseNotesReport extends AbstractReport {

    private final String release;

    public ReleaseNotesReport(JiraRestClient restClient, HgDB hgDB, boolean includeDownstream, String release) {
        super(restClient, hgDB, includeDownstream);
        this.release = release;
    }

    @Override
    public void run() {
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

        SortedSet<Issue> carriedOver = new TreeSet<>(DEFAULT_ISSUE_SORT);

        for (Issue issue : regularIssues) {
            boolean backported = false;
            for (String ver : Accessors.getFixVersions(issue)) {
                if (Versions.parseMajor(ver) >= Versions.parseMajor(release)) {
                    backported = true;
                    break;
                }
            }

            if (!backported) {
                // These are parent issues that have "accidental" forward port to requested release.
                // Filter them out as "carried over".
                carriedOver.add(issue);
            } else {
                byComponent.put(Accessors.extractComponents(issue), issue);
            }
        }

        out.println();
        out.println("Filtered " + carriedOver.size() + " issues carried over, " + byComponent.size() + " pushes left.");
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
                Collection<Issue> relNotes = Accessors.getReleaseNotes(issueCli, i);
                if (relNotes.isEmpty()) continue;
                haveRelNotes = true;

                if (!printed) {
                    out.println(component + ":");
                    out.println();
                    printed = true;
                }

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
}
