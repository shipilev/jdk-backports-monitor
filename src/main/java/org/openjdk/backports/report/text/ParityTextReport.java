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
import org.openjdk.backports.report.model.ParityModel;

import java.io.PrintStream;
import java.util.Date;
import java.util.Map;

public class ParityTextReport extends AbstractTextReport {

    private final ParityModel model;

    public ParityTextReport(ParityModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    @Override
    protected void doGenerate(PrintStream out) {
        out.println("PARITY REPORT FOR JDK: " + model.majorVer());
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows the bird-eye view of parity between OpenJDK and Oracle JDK.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        out.println("=== EXCLUSIVE: ONLY IN ORACLE JDK");
        out.println();
        out.println("This is where Oracle JDK is ahead of OpenJDK.");
        out.println("No relevant backports are detected in OpenJDK.");
        out.println("This misses the future backporting work.");
        out.println("[...] marks the interest tags.");
        out.println("(*) marks the backporting work in progress.");
        out.println();
        printWithVersion(out, model.onlyOracle());
        out.println();

        out.println("=== EXCLUSIVE: ONLY IN OPENJDK");
        out.println();
        out.println("This is where OpenJDK is ahead of Oracle JDK.");
        out.println("No relevant backports are detected in Oracle JDK yet.");
        out.println("This misses the ongoing backporting work.");
        out.println();
        printWithVersion(out, model.onlyOpen());
        out.println();

        out.println("=== LATE PARITY: ORACLE JDK FOLLOWS OPENJDK IN LATER RELEASES");
        out.println();
        out.println("This is where OpenJDK used to be ahead, and then Oracle JDK caught up in future releases.");
        out.println();
        printSimple(out, model.lateOpenFirst());
        out.println();

        out.println("=== LATE PARITY: OPENJDK FOLLOWS ORACLE JDK IN LATER RELEASES");
        out.println();
        out.println("This is where Oracle JDK used to be ahead, and then OpenJDK caught up in future releases.");
        out.println();
        printSimple(out, model.lateOracleFirst());
        out.println();

        out.println("=== EXACT PARITY: ORACLE JDK FOLLOWS OPENJDK");
        out.println();
        out.println("This is where OpenJDK made the first backport in the release, and then Oracle JDK followed.");
        out.println("No difference in the final release detected.");
        out.println();
        printSimple(out, model.exactOpenFirst());
        out.println();

        out.println("=== EXACT PARITY: OPENJDK FOLLOWS ORACLE JDK");
        out.println();
        out.println("This is where Oracle JDK made the first backport in the release, and then OpenJDK followed.");
        out.println("No difference in the final release detected.");
        out.println();
        printSimple(out, model.exactOracleFirst());
        out.println();

        out.println("=== EXACT PARITY: UNKNOWN TIMING");
        out.println();
        out.println("This is where the difference in time within the release was not identified reliably.");
        out.println("No difference in the final release detected.");
        out.println();
        printSimple(out, model.exactUnknown());
        out.println();
    }

    void printWithVersion(PrintStream out, Map<String, Map<Issue, String>> issues) {
        int size = 0;
        for (Map.Entry<String, Map<Issue, String>> kv : issues.entrySet()) {
            size += kv.getValue().size();
        }
        out.println(size + " issues in total");
        out.println();

        for (Map.Entry<String, Map<Issue, String>> kv : issues.entrySet()) {
            out.println(kv.getKey() + " (" + kv.getValue().size() + " issues):");
            for (Map.Entry<Issue, String> kv2 : kv.getValue().entrySet()) {
                Issue i = kv2.getKey();
                out.println(kv2.getValue() + " " + i.getKey() + ": " + i.getSummary());
            }
            out.println();
        }
    }

    void printSimple(PrintStream out, Map<Issue, String> issues) {
        out.println(issues.size() + " issues:");
        for (Map.Entry<Issue,String> kv : issues.entrySet()) {
            Issue i = kv.getKey();
            out.println(kv.getValue() + ", " + i.getKey() + ": " + i.getSummary());
        }
        out.println();
    }

}
