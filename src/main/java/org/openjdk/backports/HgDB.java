/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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
package org.openjdk.backports;

import java.io.*;
import java.util.*;

public class HgDB {

    private final Set<Record> records;

    public HgDB() {
        this.records = new HashSet<>();
    }

    public void load(String hgRepos) {
        PrintStream pw = System.out;

        if (hgRepos == null || hgRepos.isEmpty()) {
            return;
        }

        for (String repoPath : hgRepos.split(",")) {
            pw.print("Loading changeset metadata from " + repoPath + "... ");

            String repo = "N/A";

            try {
                List<String> lines = exec("hg", "paths", "-R", repoPath);
                for (String line : lines) {
                    if (line.startsWith("default = ")) {
                        String[] split = line.split(" = ");
                        repo = split[1];
                    }
                }
            } catch (Exception e) {
                pw.println("Cannot figure out repo url");
            }

            try {
                List<String> lines = exec("hg", "log", "-R", repoPath, "-T",
                        "{node|short}BACKPORT-SEPARATOR{latesttag}BACKPORT-SEPARATOR{desc|firstline}\n");
                for (String line : lines) {
                    String[] split = line.split("BACKPORT-SEPARATOR");
                    records.add(new Record(repo, split[0], split[1], split[2]));
                }

                pw.println(" " + lines.size() + " loaded.");
            } catch (Exception e) {
                pw.println("Cannot get changeset log");
            }
        }

        pw.println("HG DB has " + records.size() + " records.");
        pw.println();
    }

    private List<String> exec(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder().command(command);
        Process p = pb.start();

        List<String> result = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) {
            result.add(line);
        }

        p.waitFor();
        return result;
    }

    public boolean hasRepo(String repo) {
        for (Record record : records) {
            if (record.repo.contains(repo)) {
                return true;
            }
        }
        return false;
    }

    public List<Record> search(String repo, String synopsis) {
        List<Record> result = new ArrayList<>();
        for (Record record : records) {
            if (record.repo.contains(repo) && record.synopsis.startsWith(synopsis)) {
                result.add(record);
            }
        }
        return result;
    }

    public static class Record {
        final String repo;
        final String hash;
        final String tag;
        final String synopsis;

        private Record(String repo, String hash, String tag, String synopsis) {
            this.repo = repo;
            this.hash = hash;
            this.tag = tag;
            this.synopsis = synopsis;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Record record = (Record) o;

            if (!repo.equals(record.repo)) return false;
            return hash.equals(record.hash);
        }

        @Override
        public int hashCode() {
            int result = repo.hashCode();
            result = 31 * result + hash.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return tag + ", "+ repo + "/rev/" + hash;
        }
    }

}
