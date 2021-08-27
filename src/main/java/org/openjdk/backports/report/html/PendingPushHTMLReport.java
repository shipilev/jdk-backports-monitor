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

import org.openjdk.backports.jira.Versions;
import org.openjdk.backports.report.model.IssueModel;
import org.openjdk.backports.report.model.PendingPushModel;

import java.io.PrintStream;
import java.util.Date;

public class PendingPushHTMLReport extends AbstractHTMLReport {

    private final PendingPushModel model;

    public PendingPushHTMLReport(PendingPushModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    @Override
    protected void doGenerate(PrintStream out) {
        out.println("<h1>PENDING PUSH REPORT: " + model.release() + "</h2>");
        out.println();
        out.println("<p>This report shows backports that were approved, but not yet pushed.");
        out.println("Some of them are true orphans with original backport requesters never got sponsored.</p>");
        out.println();
        out.println("<p>Report generated: " + new Date() + "</p>");
        out.println();

        int v = Versions.parseMajor(model.release());

        out.println("<table>");
        out.println("<tr>");
        out.println("<th nowrap></th>");
        out.println("<th nowrap>JDK " + v + "</th>");
        out.println("<th nowrap>Bug</th>");
        out.println("<th nowrap width=\"99%\">Synopsis</th>");
        out.println("</tr>");
        for (IssueModel m : model.models()) {
            new IssueHTMLReport(m, debugLog, logPrefix).generateTableLine(out, v, v);
        }
        out.println("</table>");
    }

}
