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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.openjdk.backports.jira.Accessors;
import org.openjdk.backports.jira.Versions;

import java.io.PrintStream;
import java.util.*;

public class ReleaseNotesModel extends AbstractModel {

    private final String release;
    private final boolean includeCarryovers;
    private final List<Issue> jepIssues;
    private final Map<String, Multimap<Issue, Issue>> relNotes;
    private final Multimap<String, Issue> byComponent;
    private final SortedSet<Issue> carriedOver;

    public ReleaseNotesModel(JiraRestClient restClient, PrintStream debugOut, boolean includeCarryovers, String release) {
        super(restClient, debugOut);
        this.release = release;
        this.includeCarryovers = includeCarryovers;

        Multimap<Issue, Issue> issues = jiraIssues.getIssuesWithBackportsFull("project = JDK" +
                " AND (status in (Closed, Resolved))" +
                " AND (resolution not in (\"Won't Fix\", Duplicate, \"Cannot Reproduce\", \"Not an Issue\", Withdrawn))" +
                " AND (labels not in (release-note, testbug, openjdk-na, testbug) OR labels is EMPTY)" +
                " AND (summary !~ 'testbug')" +
                " AND (summary !~ 'problemlist') AND (summary !~ 'problem list') AND (summary !~ 'release note')" +
                " AND (issuetype != CSR)" +
                " AND fixVersion = " + release);

        jepIssues = jiraIssues.getParentIssues("project = JDK AND issuetype = JEP" +
                " AND fixVersion = " + release + "" +
                " ORDER BY summary ASC");

        byComponent = TreeMultimap.create(String::compareTo, DEFAULT_ISSUE_SORT);

        carriedOver = new TreeSet<>(DEFAULT_ISSUE_SORT);

        int majorRelease = Versions.parseMajor(release);
        int minorRelease = Versions.parseMinor(release);

        for (Issue issue : issues.keySet()) {
            boolean firstInTrain = false;

            // Check the root issue is later than the one we want for the backport
            for (String ver : Accessors.getFixVersions(issue)) {
                if (Versions.parseMajor(ver) >= majorRelease) {
                    firstInTrain = true;
                    break;
                }
            }

            // Check the root issue does not fix earlier minor versions in the same major train
            if (firstInTrain) {
                for (String ver : Accessors.getFixVersions(issue)) {
                    if (Versions.isOpen(ver) &&
                        Versions.parseMajor(ver) == majorRelease &&
                        Versions.parseMinor(ver) < minorRelease) {
                        firstInTrain = false;
                        break;
                    }
                }
            }

            // Check there are no backports in earlier minor versions in the same major train
            if (firstInTrain) {
                for (Issue bp : issues.get(issue)) {
                    if (!Accessors.isDelivered(bp)) continue;
                    for (String ver : Accessors.getFixVersions(bp)) {
                        if (Versions.isOpen(ver) &&
                            Versions.parseMajor(ver) == majorRelease &&
                            Versions.parseMinor(ver) < minorRelease) {
                            firstInTrain = false;
                            break;
                        }
                    }
                }
            }

            if (firstInTrain) {
                byComponent.put(Accessors.extractComponents(issue), issue);
            } else {
                // These are parent issues that have "accidental" forward port to requested release.
                // Filter them out as "carried over".
                carriedOver.add(issue);
            }
        }

        debugOut.println("Filtered " + carriedOver.size() + " issues carried over, " + byComponent.size() + " pushes left.");

        relNotes = new HashMap<>();

        for (String component : byComponent.keySet()) {
            Multimap<Issue, Issue> m = HashMultimap.create();
            relNotes.put(component, m);
            for (Issue i : byComponent.get(component)) {
                Collection<Issue> rns = jiraIssues.getReleaseNotes(i);
                if (!rns.isEmpty()) {
                    m.putAll(i, rns);
                }
            }
        }
    }

    public boolean includeCarryovers() {
        return includeCarryovers;
    }

    public String release() {
        return release;
    }

    public List<Issue> jeps() {
        return jepIssues;
    }

    public Map<String, Multimap<Issue, Issue>> relNotes() {
        return relNotes;
    }

    public Multimap<String, Issue> byComponent() {
        return byComponent;
    }

    public SortedSet<Issue> carriedOver() {
        return carriedOver;
    }
}
