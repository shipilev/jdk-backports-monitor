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
import org.openjdk.backports.report.model.ParityModel;

import java.io.PrintStream;
import java.util.Date;
import java.util.Map;

public class ParityHTMLReport extends AbstractHTMLReport {

    private final ParityModel model;

    public ParityHTMLReport(ParityModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    @Override
    protected void doGenerate(PrintStream out) {
        out.println("<h1>PARITY REPORT: JDK " + model.majorVer() + "</h1>");
        out.println();
        out.println("<p>This report shows the bird-eye view of parity between OpenJDK and Oracle JDK.</p>");
        out.println();
        out.println("<p>Report generated: " + new Date() + "</p>");
        out.println();

        out.println("<h2> EXCLUSIVE: ONLY IN ORACLE JDK</h2>");
        out.println();
        out.println("<p>This is where Oracle JDK is ahead of OpenJDK.</p>");
        out.println("<p>No relevant backports are detected in OpenJDK.</p>");
        out.println("<p>This misses the future backporting work.</p>");
        out.println("<p>[...] marks the interest tags.</p>");
        out.println("<p>(*) marks the backporting work in progress.</p>");
        out.println();
        printWithVersionMeta(out, model.onlyOracle());
        out.println();

        out.println("<h2>EXCLUSIVE: ONLY IN OPENJDK</h2>");
        out.println();
        out.println("<p>This is where OpenJDK is ahead of Oracle JDK.</p>");
        out.println("<p>No relevant backports are detected in Oracle JDK yet.</p>");
        out.println("<p>This misses the ongoing backporting work.</p>");
        out.println();
        printWithVersion(out, model.onlyOpen());
        out.println();

        out.println("<h2>LATE PARITY: ORACLE JDK FOLLOWS OPENJDK IN LATER RELEASES</h2>");
        out.println();
        out.println("<p>This is where OpenJDK used to be ahead, and then Oracle JDK caught up in future releases.</p>");
        out.println();
        printDouble(out, model.lateOpenFirst());
        out.println();

        out.println("<h2>LATE PARITY: OPENJDK FOLLOWS ORACLE JDK IN LATER RELEASES</h2>");
        out.println();
        out.println("<p>This is where Oracle JDK used to be ahead, and then OpenJDK caught up in future releases.</p>");
        out.println();
        printDouble(out, model.lateOracleFirst());
        out.println();

        out.println("<h2>EXACT PARITY: ORACLE JDK FOLLOWS OPENJDK</h2>");
        out.println();
        out.println("<p>This is where OpenJDK made the first backport in the release, and then Oracle JDK followed.</p>");
        out.println("<p>No difference in the final release detected.</p>");
        out.println();
        printSingle(out, model.exactOpenFirst());
        out.println();

        out.println("<h2>EXACT PARITY: OPENJDK FOLLOWS ORACLE JDK</h2>");
        out.println();
        out.println("<p>This is where Oracle JDK made the first backport in the release, and then OpenJDK followed.</p>");
        out.println("<p>No difference in the final release detected.</p>");
        out.println();
        printSingle(out, model.exactOracleFirst());
        out.println();

        out.println("<h2>EXACT PARITY: UNKNOWN TIMING</h2>");
        out.println();
        out.println("<p>This is where the difference in time within the release was not identified reliably.</p>");
        out.println("<p>No difference in the final release detected.</p>");
        out.println();
        printDouble(out, model.exactUnknown());
        out.println();
    }

    void printWithVersion(PrintStream out, Map<String, Map<Issue, ParityModel.SingleVers>> issues) {
        int size = 0;
        for (Map.Entry<String, Map<Issue, ParityModel.SingleVers>> kv : issues.entrySet()) {
            size += kv.getValue().size();
        }
        out.println("<p>" + size + " issues in total</p>");

        for (Map.Entry<String, Map<Issue, ParityModel.SingleVers>> kv : issues.entrySet()) {
            out.println("<h3>" + kv.getKey() + "</h3>");
            out.println("<p>" + kv.getValue().size() + " issues</p>");
            out.println("<table>");
            out.println("<tr>");
            out.println("<th nowrap>Version</th>");
            out.println("<th nowrap>Bug</th>");
            out.println("<th nowrap width=\"99%\">Synopsis</th>");
            out.println("</tr>");
            for (Map.Entry<Issue, ParityModel.SingleVers> kv2 : kv.getValue().entrySet()) {
                Issue i = kv2.getKey();
                ParityModel.SingleVers ver = kv2.getValue();
                out.println("<tr>");
                out.println("<td nowrap>" + ver.version() + "</td>");
                out.println("<td nowrap>" + issueLink(i) + "</td>");
                out.println("<td nowrap width=\"99%\">" + i.getSummary() + "</td>");
                out.println("</tr>");
            }
            out.println("</table>");
        }
    }

    void printWithVersionMeta(PrintStream out, Map<String, Map<Issue, ParityModel.SingleVersMetadata>> issues) {
        int size = 0;
        for (Map.Entry<String, Map<Issue, ParityModel.SingleVersMetadata>> kv : issues.entrySet()) {
            size += kv.getValue().size();
        }
        out.println("<p>" + size + " issues in total</p>");

        for (Map.Entry<String, Map<Issue, ParityModel.SingleVersMetadata>> kv : issues.entrySet()) {
            out.println("<h3>" + kv.getKey() + "</h3>");
            out.println("<p>" + kv.getValue().size() + " issues</p>");
            out.println("<table>");
            out.println("<tr>");
            out.println("<th nowrap>Version</th>");
            out.println("<th nowrap>Interest</th>");
            out.println("<th nowrap>Fix</th>");
            out.println("<th nowrap>Bug</th>");
            out.println("<th nowrap width=\"99%\">Synopsis</th>");
            out.println("</tr>");
            for (Map.Entry<Issue, ParityModel.SingleVersMetadata> kv2 : kv.getValue().entrySet()) {
                Issue i = kv2.getKey();
                ParityModel.SingleVersMetadata ver = kv2.getValue();
                out.println("<tr>");
                out.println("<td nowrap>" + ver.version() + "</td>");
                out.println("<td nowrap>" + ver.interestTags() + "</td>");
                out.println("<td nowrap>" + (ver.backportRequested() ? "(*)" : "") + "</td>");
                out.println("<td nowrap>" + issueLink(i) + "</td>");
                out.println("<td nowrap width=\"99%\">" + i.getSummary() + "</td>");
                out.println("</tr>");
            }
            out.println("</table>");
        }
    }

    void printSingle(PrintStream out, Map<Issue, ParityModel.SingleVers> issues) {
        out.println("<p>" + issues.size() + " issues.</p>");

        out.println("<table>");
        out.println("<tr>");
        out.println("<th nowrap>Version</th>");
        out.println("<th nowrap>Bug</th>");
        out.println("<th nowrap width=\"99%\">Synopsis</th>");
        out.println("</tr>");
        for (Map.Entry<Issue,ParityModel.SingleVers> kv : issues.entrySet()) {
            Issue i = kv.getKey();
            ParityModel.SingleVers dVers = kv.getValue();
            out.println("<tr>");
            out.println("<td nowrap>" + dVers.version() + "</td>");
            out.println("<td nowrap>" + issueLink(i) + "</td>");
            out.println("<td nowrap width=\"99%\">" + i.getSummary() + "</td>");
            out.println("</tr>");
        }
        out.println("</table>");
    }

    void printDouble(PrintStream out, Map<Issue, ParityModel.DoubleVers> issues) {
        out.println("<p>" + issues.size() + " issues.</p>");

        out.println("<table>");
        out.println("<tr>");
        out.println("<th nowrap>Version 1</th>");
        out.println("<th nowrap>Version 2</th>");
        out.println("<th nowrap>Bug</th>");
        out.println("<th nowrap width=\"99%\">Synopsis</th>");
        out.println("</tr>");
        for (Map.Entry<Issue,ParityModel.DoubleVers> kv : issues.entrySet()) {
            Issue i = kv.getKey();
            ParityModel.DoubleVers dVers = kv.getValue();
            out.println("<tr>");
            out.println("<td nowrap>" + dVers.version1() + "</td>");
            out.println("<td nowrap>" + dVers.version2() + "</td>");
            out.println("<td nowrap>" + issueLink(i) + "</td>");
            out.println("<td nowrap width=\"99%\">" + i.getSummary() + "</td>");
            out.println("</tr>");
        }
        out.println("</table>");
    }

}
