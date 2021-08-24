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

    public void generateTableLine(PrintStream out, int minV, int maxV) {
        out.println("<tr>");

        SortedMap<Integer, BackportStatus> shBackports = model.shenandoahPorts();
        out.println("<td>");
        if (!shBackports.isEmpty()) {
            for (Map.Entry<Integer, BackportStatus> e : shBackports.entrySet()) {
                out.println(shortStatusHTMLLine(e.getKey(), e.getValue(), model.shenandoahPortsDetails().get(e.getKey())));
            }
        }
        out.println("</td>");

        for (int release = minV; release <= maxV; release++) {
            List<Issue> issues = model.existingPorts().get(release);
            out.println("<td>");
            if (issues != null) {
                if (release == model.fixVersion()) {
                    out.println("<span title=\"JDK " + release + ": Fixed in this release\">");
                    out.println("<font color=black>\u2749</font>");
                    out.println("</span>");
                } else {
                    out.println("<span title=\"JDK " + release + ": Ported to this release\">");
                    out.println("<font color=green>\u2714</font>");
                    out.println("</span>");
                }
            } else {
                BackportStatus status = model.pendingPorts().get(release);
                String details = model.pendingPortsDetails().get(release);
                out.println(shortStatusHTMLLine(release, status, details));
            }
            out.println("</td>");
        }

        out.println("<td nowrap><a href=\"" + Main.JIRA_URL + "browse/" + issue.getKey() + "\">" + issue.getKey() + "</a></td>");
        out.println("<td width=\"99%\">" + issue.getSummary() + "</td>");

        out.println("</tr>");
    }

    public void generateSimple(PrintStream out) {
        out.println("<td nowrap><a href=\"" + Main.JIRA_URL + "browse/" + issue.getKey() + "\">" + issue.getKey() + "</a></td>");
        out.println("<td width=\"99%\">" + issue.getSummary() + "</td>");
        out.println("<td nowrap>" + (issue.getReporter() != null ? issue.getReporter().getDisplayName() : "None") + "</td>");
        out.println("<td nowrap>" + (issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : "None") + "</td>");
        out.println("<td nowrap>" + issue.getPriority().getName() + "</td>");
        out.println("<td nowrap>" + model.components() + "</td>");

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

    private static String shortStatusHTMLLine(int release, BackportStatus status, String details) {
        StringBuilder sb = new StringBuilder();
        if (status == null) {
            sb.append("<font color=white>\u2716</font>");
        } else {
            sb.append("<span title=\"JDK ")
                    .append(release)
                    .append(": ")
                    .append(statusToText(status))
                    .append((details == null || details.isEmpty()) ? "" : ": " + details)
                    .append("\">");
            switch (status) {
                case BAKING:
                    sb.append("\u2668");
                    break;
                case INHERITED:
                    sb.append("<font color=white>\u2714</font>");
                    break;
                case FIXED:
                    sb.append("<font color=green>\u2714</font>");
                    break;
                case MISSING:
                case MISSING_ORACLE:
                case REJECTED:
                    sb.append("<font color=red>\u2716</font>");
                    break;
                case NOT_AFFECTED:
                    sb.append("<font color=white>\u2716</font>");
                    break;
                case REQUESTED:
                    sb.append("\u270B");
                    break;
                case APPROVED:
                    sb.append("\u270C");
                    break;
                case WARNING:
                    sb.append("<b><font color=red>\u26A0</font></b>");
                    break;
                default:
                    throw new IllegalStateException("Unhandled status: " + status);
            }
            sb.append("</span>");
        }
        return sb.toString();
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
