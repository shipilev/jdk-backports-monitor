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
import org.openjdk.backports.Main;
import org.openjdk.backports.StringUtils;
import org.openjdk.backports.jira.Accessors;
import org.openjdk.backports.report.BackportStatus;
import org.openjdk.backports.report.model.IssueModel;

import java.io.PrintStream;
import java.util.*;

public class IssueHTMLReport extends AbstractHTMLReport {

    private final IssueModel model;
    private final Issue issue;

    public IssueHTMLReport(IssueModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
        this.issue = model.issue();
    }

    @Override
    public void doGenerate(PrintStream out) {
        out.println("<h1>ISSUE REPORT: " + issue.getKey() + "</h1>");
        out.println("This report shows a single issue status.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        generateSimple(out);
    }

    public void generateTableLine(PrintStream out) {
        out.println("<tr>");
        out.println("<td nowrap><a href=\"" + Main.JIRA_URL + "browse/" + issue.getKey() + "\">" + issue.getKey() + "</a></td>");
        out.println("<td>" + issue.getSummary() + "</td>");
        out.println("<td>" + issue.getPriority().getName() + "</td>");
        out.println("<td nowrap>" + model.components() + "</td>");
        out.println("<td><a href=\"\">" + model.fixVersion() + "</a></td>");

        for (int release : IssueModel.VERSIONS_TO_CARE_FOR) {
            List<Issue> issues = model.existingPorts().get(release);
            out.println("<td>");
            if (issues != null) {
                for (Issue i : issues) {
                    out.println(shortIssueHTMLLine(i, release == model.fixVersion()));
                }
            } else {
                BackportStatus status = model.pendingPorts().get(release);
                out.println(shortStatusHTMLLine(status));
            }
            out.println("</td>");
        }

        SortedMap<Integer, BackportStatus> shBackports = model.shenandoahPorts();
        if (!shBackports.isEmpty()) {
            out.println("<td>");
            for (Map.Entry<Integer, BackportStatus> e : shBackports.entrySet()) {
                out.println(shortStatusHTMLLine(e.getValue()));
            }
            out.println("</td>");
        }
        out.println("</tr>");
    }

    public void generateSimple(PrintStream out) {
        out.println("<td><a href=\"" + Main.JIRA_URL + "browse/" + issue.getKey() + "\">" + issue.getKey() + "</a></td>");
        out.println("<td>" + issue.getSummary() + "</td>");
        out.println("<td>" + (issue.getReporter() != null ? issue.getReporter().getDisplayName() : "None") + "</td>");
        out.println("<td>" + (issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : "None") + "</td>");
        out.println("<td>" + issue.getPriority().getName() + "</td>");
        out.println("<td>" + model.components() + "</td>");
        out.println("<td><a href=\"\">" + model.fixVersion() + "</a></td>");

        out.println("  Backports and Forwardports:");

        for (int release : IssueModel.VERSIONS_TO_CARE_FOR) {
            if (release == model.fixVersion()) continue;
            List<Issue> issues = model.existingPorts().get(release);
            if (issues != null) {
                for (Issue i : issues) {
                    out.printf("    %2d: %s%n", release, shortIssueLine(i));
                }
            } else {
                BackportStatus status = model.pendingPorts().get(release);
                String details = model.pendingPortsDetails().get(release);
                out.printf("    %2d: %s%s%n", release, (details.isEmpty() ? "" : ": " + details), status);
            }
        }
        out.println();

        SortedMap<Integer, BackportStatus> shBackports = model.shenandoahPorts();
        if (!shBackports.isEmpty()) {
            out.println("  Shenandoah Backports:");
            for (Map.Entry<Integer, BackportStatus> e : shBackports.entrySet()) {
                String details = model.pendingPortsDetails().get(e.getKey());
                out.printf("    %2d: %s%s%n", e.getKey(), e.getValue(), (details.isEmpty() ? "" : ": " + details));
            }
            out.println();
        }

        Collection<Issue> relNotes = model.releaseNotes();
        if (!relNotes.isEmpty()) {
            out.println();
            out.println("  Release Notes:");
            out.println();
            printReleaseNotes(out, relNotes);
        }

        List<String> warns = model.warnings();
        if (!warns.isEmpty()) {
            out.println("  WARNINGS:");
            for (String m : warns) {
                out.println("    " + m);
            }
            out.println();
        }

    }

    private static String shortIssueLine(Issue issue) {
        return String.format("%s, %10s, %s, %s",
                Accessors.getFixVersion(issue),
                issue.getKey(),
                Accessors.getPushURL(issue),
                Accessors.getPushDate(issue));
    }

    private static String shortIssueHTMLLine(Issue issue, boolean original) {
        return String.format("<a href=\"%s\">" + (original ? "\u2741" : "\u2714") + "</a>",
                Main.JIRA_URL + "browse/" + issue.getKey());
    }

    private static String shortStatusHTMLLine(BackportStatus status) {
        switch (status) {
            case BAKING:
                return "\u22EF";
            case INHERITED:
            case FIXED:
                return "\u2727";
            case MISSING:
                return "<font color=red><b>!</b></font>";
            case MISSING_ORACLE:
                return "<font color=red><b>!!</b></font>";
            case NOT_AFFECTED:
                return "<font color=gray>\u2716</font>";
            case REJECTED:
                return "<font color=red>\u2716</font>";
            case REQUESTED:
                return "\u270B";
            case APPROVED:
                return "\u270C";
            case WARNING:
                return "\u2757";
            default:
                throw new IllegalStateException("Unhandled status: " + status);
        }
    }

    protected static void printReleaseNotes(PrintStream out, Collection<Issue> relNotes) {
        Set<String> dup = new HashSet<>();
        for (Issue rn : relNotes) {
            String summary = StringUtils.leftPad(rn.getKey() + ": " + rn.getSummary().replaceFirst("Release Note: ", ""), 2);
            String descr = StringUtils.leftPad(StringUtils.rewrap(StringUtils.stripNull(rn.getDescription()), StringUtils.DEFAULT_WIDTH - 6), 6);
            if (dup.add(descr)) {
                out.println(summary);
                out.println();
                out.println(descr);
                out.println();
            }
        }
    }

}
