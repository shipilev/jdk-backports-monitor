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

    private Set<Record> records;

    public HgDB() {
        this.records = new HashSet<>();
    }

    public void load() throws Exception {
        try {
            FileInputStream fis = new FileInputStream("hg.db");
            BufferedInputStream bis = new BufferedInputStream(fis);
            ObjectInputStream ois = new ObjectInputStream(bis);
            records = (Set<Record>) ois.readObject();
            ois.close();
            bis.close();
            fis.close();
        } catch (Exception e) {
            records = new HashSet<>();
            throw e;
        }
    }

    public void update(String repoPath) throws IOException, InterruptedException {
        String repo = "";

        {
            List<String> lines = exec("hg", "paths", "-R", repoPath);
            for (String line : lines) {
                if (line.startsWith("default = ")) {
                    String[] split = line.split("=");
                    repo = split[1];
                }
            }
        }

        {
            List<String> lines = exec("hg", "log", "-R", repoPath, "-T", "{node|short}BACKPORT-SEPARATOR{desc|firstline}\n");
            for (String line : lines) {
                String[] split = line.split("BACKPORT-SEPARATOR");
                records.add(new Record(repo, split[0].trim(), split[1].trim()));
            }
        }
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

    public void save() throws IOException {
        try {
            FileOutputStream fos = new FileOutputStream("hg.db");
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(records);
            oos.close();
            bos.close();
            fos.close();
        } catch (Exception e) {
            records = new HashSet<>();
            throw e;
        }
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

    public static class Record implements Serializable {
        final String repo;
        final String hash;
        final String synopsis;

        private Record(String repo, String hash, String synopsis) {
            this.repo = repo;
            this.hash = hash;
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
    }

}
