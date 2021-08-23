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
package org.openjdk.backports.report.text;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.openjdk.backports.Main;
import org.openjdk.backports.StringUtils;
import org.openjdk.backports.jira.Accessors;
import org.openjdk.backports.report.BackportStatus;
import org.openjdk.backports.report.model.IssueModel;

import java.io.PrintStream;
import java.util.*;

public class IssueTextReport extends AbstractTextReport {

    private final IssueModel model;
    private final Issue issue;

    public IssueTextReport(IssueModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
        this.issue = model.issue();
    }

    @Override
    protected void doGenerate(PrintStream out) {
        out.println("ISSUE REPORT: " + issue.getKey());
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows a single issue status.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        generateSimple(out);
    }

    public void generateSimple(PrintStream out) {
        out.println();
        out.println(issue.getKey() + ": " + issue.getSummary());
        out.println();
        out.println("  Original Bug:");
        out.println("    URL: " + Main.JIRA_URL + "browse/" + issue.getKey());
        out.println("    Reporter: " + (issue.getReporter() != null ? issue.getReporter().getDisplayName() : "None"));
        out.println("    Assignee: " + (issue.getAssignee() != null ? issue.getAssignee().getDisplayName() : "None"));
        out.println("    Priority: " + issue.getPriority().getName());
        out.println("    Components: " + model.components());
        out.println();

        out.println("  Original Fix:");
        out.printf("    %2d: %s%n", model.fixVersion(), shortIssueLine(issue));
        out.println();

        out.println("  Backports and Forwardports:");

        for (int release : IssueModel.VERSIONS_TO_CARE_FOR_REV) {
            if (release == model.fixVersion()) continue;
            List<Issue> issues = model.existingPorts().get(release);
            if (issues != null) {
                for (Issue i : issues) {
                    out.printf("    %2d: %s%n", release, shortIssueLine(i));
                }
            } else {
                BackportStatus status = model.pendingPorts().get(release);
                String details = model.pendingPortsDetails().get(release);
                out.printf("    %2d: %s%s%n", release, statusToText(status), (details.isEmpty() ? "" : ": " + details));
            }
        }
        out.println();

        SortedMap<Integer, BackportStatus> shBackports = model.shenandoahPorts();
        if (!shBackports.isEmpty()) {
            out.println("  Shenandoah Backports:");
            for (Map.Entry<Integer, BackportStatus> e : shBackports.entrySet()) {
                String details = model.shenandoahPortsDetails().get(e.getKey());
                out.printf("    %2d: %s%s%n", e.getKey(), statusToText(e.getValue()), (details.isEmpty() ? "" : ": " + details));
            }
            out.println();
        }

        Collection<Issue> relNotes = model.releaseNotes();
        if (!relNotes.isEmpty()) {
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
