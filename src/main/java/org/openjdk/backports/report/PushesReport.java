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
import com.google.common.collect.*;
import org.openjdk.backports.jira.Accessors;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PushesReport extends AbstractReport {

    private final boolean directOnly;
    private final String release;

    public PushesReport(JiraRestClient restClient, boolean directOnly, String release) {
        super(restClient);
        this.directOnly = directOnly;
        this.release = release;
    }

    @Override
    public void run() {
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

        for (Issue issue : issues) {
            String committer = Accessors.getPushUser(issue);
            if (committer.equals("N/A")) {
                // These are pushes to internal repos
                noChangesets.add(issue);
            } else {
                byPriority.add(issue.getPriority().getName());
                byComponent.add(Accessors.extractComponents(issue));
                byCommitter.put(committer, issue);
                byTime.add(issue);
            }
        }

        out.println();
        out.println("Filtered " + noChangesets.size() + " issues without pushes, " + byPriority.size() + " pushes left.");
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

    protected void printByEmailName(PrintStream out, Multimap<String, Issue> byCommitter) {
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

    protected void printByComponent(PrintStream out, Multiset<String> byComponent) {
        Multiset<String> firsts = TreeMultiset.create();
        Map<String, Multiset<String>> seconds = new HashMap<>();

        for (String component : byComponent.elementSet()) {
            String first = component.split("/")[0];
            Multiset<String> bu = seconds.computeIfAbsent(first, k -> HashMultiset.create());
            bu.add(component, byComponent.count(component));
            firsts.add(first, byComponent.count(component));
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

}
