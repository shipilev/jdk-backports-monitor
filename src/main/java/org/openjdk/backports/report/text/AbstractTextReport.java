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

import org.openjdk.backports.StringUtils;
import org.openjdk.backports.report.BackportStatus;
import org.openjdk.backports.report.Common;

import java.io.IOException;
import java.io.PrintStream;

abstract class AbstractTextReport extends Common {

    protected final PrintStream debugLog;
    protected final String logPrefix;

    public AbstractTextReport(PrintStream debugLog, String logPrefix) {
        this.debugLog = debugLog;
        this.logPrefix = logPrefix;
    }

    public final void generate() throws IOException {
        String fileName = logPrefix + ".txt";
        PrintStream out = new PrintStream(fileName);
        debugLog.println("Generating TXT log to " + fileName);
        doGenerate(out);
        out.close();
    }

    protected abstract void doGenerate(PrintStream out);

    protected void printMajorDelimiterLine(PrintStream out) {
        out.println(StringUtils.tabLine('='));
    }

    protected void printMinorDelimiterLine(PrintStream out) {
        out.println(StringUtils.tabLine('-'));
    }

    protected static String statusToText(BackportStatus status) {
        switch (status) {
            case NOT_AFFECTED:
                return "Not affected";
            case INHERITED:
                return "Inherited";
            case FIXED:
                return "Fixed";
            case BAKING:
                return "WAITING for patch to bake a little";
            case MISSING:
                return "MISSING";
            case MISSING_ORACLE:
                return "MISSING (+ on Oracle backport list)";
            case APPROVED:
                return "APPROVED";
            case REJECTED:
                return "Rejected";
            case REQUESTED:
                return "Requested";
            case WARNING:
                return "WARNING";
            default:
                throw new IllegalStateException("Unknown status: " + status);
        }
    }


}
