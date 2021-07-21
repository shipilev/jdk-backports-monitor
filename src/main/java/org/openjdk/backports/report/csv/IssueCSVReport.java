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
package org.openjdk.backports.report.csv;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.openjdk.backports.report.model.IssueModel;

import java.io.PrintStream;
import java.util.List;

public class IssueCSVReport extends AbstractCSVReport {

    private final IssueModel model;

    public IssueCSVReport(IssueModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    protected void doGenerate(PrintStream out) {
        generateSimple(out);
    }

    protected void generateSimple(PrintStream out) {
        Issue issue = model.issue();
        out.print("\"" + issue.getKey() + "\", ");
        out.print("\"" + issue.getSummary() + "\", ");
        for (int release : IssueModel.VERSIONS_TO_CARE_FOR) {
            List<Issue> issues = model.existingPorts().get(release);
            if (issues != null) {
                out.print("\"Fixed\", ");
            } else {
                String status = model.pendingPorts().get(release);
                out.print("\"" + status + "\", ");
            }
        }
        out.println();
    }

}
