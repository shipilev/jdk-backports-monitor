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

import org.openjdk.backports.report.Common;

import java.io.*;

abstract class AbstractHTMLReport extends Common {

    protected final PrintStream debugLog;
    protected final String logPrefix;

    public AbstractHTMLReport(PrintStream debugLog, String logPrefix) {
        this.debugLog = debugLog;
        this.logPrefix = logPrefix;
    }

    public final void generate() throws IOException {
        String fileName = logPrefix + ".html";
        PrintStream out = new PrintStream(fileName);
        debugLog.println("Generating HTML log to " + fileName);
        out.println("<html>");
        out.println("<head>");
        out.println("<meta charset=\"UTF-8\">");
        out.println("<style>");
        try (InputStream is = AbstractHTMLReport.class.getResourceAsStream("/style.css");
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                out.println(line);
            }
        }
        out.println("</style>");
        out.println("<body>");
        doGenerate(out);
        out.println("</body>");
        out.println("</html>");
        out.close();
    }

    protected abstract void doGenerate(PrintStream out);

}
