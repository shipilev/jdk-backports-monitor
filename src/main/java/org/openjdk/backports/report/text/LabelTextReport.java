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

import org.openjdk.backports.report.BackportStatus;
import org.openjdk.backports.report.model.IssueModel;
import org.openjdk.backports.report.model.LabelModel;

import java.io.PrintStream;
import java.util.Date;

public class LabelTextReport extends AbstractTextReport {

    private final LabelModel model;

    public LabelTextReport(LabelModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    @Override
    protected void doGenerate(PrintStream out) {
        out.println("LABEL REPORT: " + model.label());
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows bugs with the given label, along with their backporting status.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();
        out.println("Minimal actionable level to display: " + model.minLevel());
        out.println();
        out.println("For actionable issues, search for these strings:");
        out.println("  \"" + statusToText(BackportStatus.MISSING) + "\"");
        out.println("  \"" + statusToText(BackportStatus.APPROVED) + "\"");
        out.println("  \"" + statusToText(BackportStatus.WARNING) + "\"");
        out.println();
        out.println("For lingering issues, search for these strings:");
        out.println("  \"" + statusToText(BackportStatus.BAKING) + "\"");
        out.println();

        printMinorDelimiterLine(out);
        for (IssueModel im : model.issues()) {
            new IssueTextReport(im, debugLog, logPrefix).generateSimple(out);
            printMinorDelimiterLine(out);
        }

        out.println();
        out.println("" + model.issues().size() + " issues shown.");
    }

}
