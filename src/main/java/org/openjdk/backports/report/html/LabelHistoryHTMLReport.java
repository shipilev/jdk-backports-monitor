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

import org.openjdk.backports.report.model.LabelHistoryModel;

import java.io.PrintStream;
import java.util.Date;

public class LabelHistoryHTMLReport extends AbstractHTMLReport {

    private final LabelHistoryModel model;

    public LabelHistoryHTMLReport(LabelHistoryModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    @Override
    public void doGenerate(PrintStream out) {
        out.println("<h1>LABEL HISTORY REPORT: " + model.label() + "</h1>");
        out.println("<p>Report generated: " + new Date() + "</p>");

        out.println("<table>");
        out.println("<tr>");
        out.println("<th nowrap>Date Added</th>");
        out.println("<th nowrap>Added By</th>");
        out.println("<th nowrap>Bug</th>");
        out.println("<th nowrap width=\"99%\">Synopsis</th>");
        out.println("</tr>");
        for (LabelHistoryModel.Record r : model.records()) {
            out.println("<tr>");
            out.println("<td>" + r.date.toLocalDate().toString() + "</td>");
            out.println("<td>" + r.user + "</td>");
            out.println("<td>" + issueLink(r.issue) + "</td>");
            out.println("<td>" + r.issue.getSummary() + "</td>");
            out.println("</tr>");
        }
        out.println("</table>");
    }
}
