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
import com.github.rjeschke.txtmark.Processor;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openjdk.backports.report.model.ReleaseNotesModel;

import java.io.PrintStream;
import java.util.*;

public class ReleaseNotesHTMLReport extends AbstractHTMLReport {

    private final ReleaseNotesModel model;

    public ReleaseNotesHTMLReport(ReleaseNotesModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    @Override
    protected void doGenerate(PrintStream out) {
        out.println("<h1>RELEASE NOTES: JDK " + model.release() + "</h1>");
        out.println("<p>Notes generated: " + new Date() + "<p>");

        out.println("<h2>JEPs</h2>");

        List<Issue> jeps = model.jeps();
        if (jeps.isEmpty()) {
            out.println("<p>None.</p>");
        } else {
            out.println("<table>");
            out.println("<tr>");
            out.println("<th>Issue</th>");
            out.println("<th>Description</th>");
            out.println("</tr>");
            for (Issue i : jeps) {
                out.println("<tr>");
                out.println("<td class='multicell'>" + issueLink(i) + "</td>");
                out.println("<td><b>" + i.getSummary() + "</b></td>");
                out.println("</tr>");
                out.println("<tr>");
                out.println("<td></td>");
                out.println("<td class='embedded-block'>");
                String desc = i.getDescription();
                if (desc != null && !desc.isEmpty()) {
                    Document doc = Jsoup.parse(Processor.process(desc));
                    Element p = doc.body().selectFirst("p");
                    if (p != null) {
                        out.println(p.text());
                    } else {
                        out.println("Error selecting the description");
                    }
                    out.println("<br>");
                } else {
                    out.println("<p>No description.</p>");
                }
                out.println("</td>");
                out.println("</tr>");
            }
            out.println("</table>");
        }

        out.println("<h2>RELEASE NOTES</h2>");

        Map<String, Multimap<Issue, Issue>> rns = model.relNotes();

        boolean haveRelNotes = false;
        for (String component : rns.keySet()) {
            boolean printed = false;
            Multimap<Issue, Issue> m = rns.get(component);
            for (Issue i : m.keySet()) {
                haveRelNotes = true;

                if (!printed) {
                    out.println("<h3>" + component + "</h3>");
                    out.println("<table>");
                    out.println("<tr>");
                    out.println("<th>Issue</th>");
                    out.println("<th>Description</th>");
                    out.println("</tr>");
                    printed = true;
                }

                Set<String> dup = new HashSet<>();
                boolean firstPrint = true;
                for (Issue rn : m.get(i)) {
                    out.println("<tr>");

                    if (firstPrint) {
                        out.println("<td class='multicell' rowspan='" + m.get(i).size() + "'>" + issueLink(i) + "</td>");
                        firstPrint = false;
                    }

                    out.println("<td class='embedded-block'>");
                    String descr = rn.getDescription();
                    String summary = rn.getSummary().replaceFirst("Release Note: ", "");
                    if (dup.add(descr)) {
                        out.println("<p><b>" + summary + "</b></p>");
                        out.println("<br>");
                        out.println(Processor.process(descr));
                        out.println("<br>");
                    }
                    out.println("</td>");
                    out.println("</tr>");
                }

            }

            if (printed) {
                out.println("</table>");
            }

        }
        if (!haveRelNotes) {
            out.println("<p>None.</p>");
        }
        out.println();

        out.println("<h2>FIXED ISSUES</h2>");
        out.println();

        Multimap<String, Issue> byComponent = model.byComponent();

        for (String component : byComponent.keySet()) {
            out.println("<h3>" + component + "</h3>");
            Multimap<String, Issue> byPriority = TreeMultimap.create(String::compareTo, DEFAULT_ISSUE_SORT);
            for (Issue i : byComponent.get(component)) {
                byPriority.put(i.getPriority().getName(), i);
            }
            out.println("<table>");
            out.println("<tr>");
            out.println("<th>Priority</th>");
            out.println("<th>Bug</th>");
            out.println("<th width='99%'>Summary</th>");
            out.println("</tr>");
            for (String prio : byPriority.keySet()) {
                for (Issue i : byPriority.get(prio)) {
                    out.println("<tr>");
                    out.println("<td>" + prio + "</td>");
                    out.println("<td>" + issueLink(i) + "</td>");
                    out.println("<td>" + i.getSummary() + "</td>");
                    out.println("</tr>");
                }
            }
            out.println("</table>");
        }
        out.println();

        if (model.includeCarryovers()) {
            out.println("<h2>CARRIED OVER FROM PREVIOUS RELEASES</h2>");
            out.println("<p>These have fixes for the given release, but they are also fixed in the previous");
            out.println("minor version of the same major release.</p>");
            out.println();

            SortedSet<Issue> carriedOver = model.carriedOver();

            if (carriedOver.isEmpty()) {
                out.println("<p>None.</p>");
            }

            out.println("<table>");
            out.println("<tr>");
            out.println("<th>Priority</th>");
            out.println("<th>Bug</th>");
            out.println("<th width='99%'>Summary</th>");
            out.println("</tr>");
            for (Issue i : carriedOver) {
                out.println("<tr>");
                out.println("<td>" + i.getPriority().getName() + "</td>");
                out.println("<td>" + issueLink(i) + "</td>");
                out.println("<td>" + i.getSummary() + "</td>");
                out.println("</tr>");
            }
            out.println("</table>");
        }
    }

}
