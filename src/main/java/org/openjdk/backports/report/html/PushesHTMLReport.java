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
package org.openjdk.backports.report.html;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.collect.*;
import org.openjdk.backports.jira.Accessors;
import org.openjdk.backports.jira.UserCache;
import org.openjdk.backports.report.model.PushesModel;

import java.io.PrintStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PushesHTMLReport extends AbstractHTMLReport {

    private final PushesModel model;

    public PushesHTMLReport(PushesModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    protected void doGenerate(PrintStream out) {
        out.println("<h1>PUSHES REPORT: " + model.release() + "</h1>");

        out.println("<p>");
        if (model.directOnly()) {
            out.println("This report shows who pushed the changesets to the given release.");
            out.println("This usually shows who did the development work, not sponsors/reviewers.");
        } else {
            out.println("This report shows who pushed the backports to the given release.");
            out.println("This usually shows who did the backporting, testing, and review work.");
        }
        out.println("</p>");
        out.println("<p>Report generated: " + new Date() + "</p>");

        UserCache users = model.users();

        Multiset<String> byPriority = model.byPriority();
        Multiset<String> byComponent = model.byComponent();

        out.println("<h2>Distribution by priority</h2>");
        for (String prio : byPriority.elementSet()) {
            out.printf("   %3d: %s%n", byPriority.count(prio), prio);
        }
        out.println();

        out.println("<h2>Distribution by components</h2>");
        {
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
        out.println();

        Multimap<String, Issue> byCommitter = model.byCommitter();

        out.println("<h2>Distribution by affiliation</h2>");
        {
            Multiset<String> byAffiliation = TreeMultiset.create();
            Map<String, Multiset<String>> byAffiliationAndCommitter = new HashMap<>();

            for (String committer : byCommitter.keySet()) {
                String aff = users.getAffiliation(committer);
                byAffiliation.add(aff, byCommitter.get(committer).size());
                Multiset<String> bu = byAffiliationAndCommitter.computeIfAbsent(aff, k -> HashMultiset.create());
                bu.add(users.getDisplayName(committer), byCommitter.get(committer).size());
            }

            int total = byCommitter.size();
            out.printf("   %3d: <total issues>%n", total);
            for (String aff : Multisets.copyHighestCountFirst(byAffiliation).elementSet()) {
                String percAff = String.format("(%.1f%%)", 100.0 * byAffiliation.count(aff) / total);
                out.printf("      %3d %7s: %s%n", byAffiliation.count(aff), percAff, aff);
                Multiset<String> committers = byAffiliationAndCommitter.get(aff);
                for (String committer : Multisets.copyHighestCountFirst(committers).elementSet()) {
                    String percCommitter = String.format("(%.1f%%)", 100.0 * committers.count(committer) / total);
                    out.printf("         %3d %7s: %s%n", committers.count(committer), percCommitter, committer);
                }
            }
        }
        out.println();

        out.println("<h2>Chronological push log:</h2>");
        out.println();
        out.println("<table>");
        for (Issue i : model.byTime()) {
            String pushUser = Accessors.getPushUser(i);
            out.println("<tr>");
            out.println("<td>" + TimeUnit.SECONDS.toDays(Accessors.getPushSecondsAgo(i)) + "</td>");
            out.println("<td>" + users.getDisplayName(pushUser) + "</td>");
            out.println("<td>" + users.getAffiliation(pushUser) + "</td>");
            out.println("<td><a href=\"https://bugs.openjdk.java.net/browse/" + i.getKey() + "\">" + i.getKey() + "</a></td>");
            out.println("<td>" + i.getSummary() + "</td>");
            out.println("</tr>");
        }
        out.println("</table>");

        out.println("<h2>No changesets log</h2>");

        out.println("<table>");
        for (Issue i : model.noChangesets()) {
            out.println("<tr>");
            out.println("<td><a href=\"https://bugs.openjdk.java.net/browse/" + i.getKey() + "\">" + i.getKey() + "</a></td>");
            out.println("<td>" + i.getSummary() + "</td>");
            out.println("</tr>");
        }
        out.println("</table>");

        out.println("<h2>Committer push log</h2>");
        out.println();

        for (String committer : byCommitter.keySet()) {
            out.println("<h3>" + users.getDisplayName(committer) + ", " + users.getAffiliation(committer) + "</h3>");
            out.println("<table>");
            for (Issue i : model.noChangesets()) {
                out.println("<tr>");
                out.println("<td><a href=\"https://bugs.openjdk.java.net/browse/" + i.getKey() + "\">" + i.getKey() + "</a></td>");
                out.println("<td>" + i.getSummary() + "</td>");
                out.println("</tr>");
            }
            out.println("</table>");
        }
    }

}
