// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.util.*;

/** Last 64 manual confirmations, hashes/labels only. No images, values or timestamps. */
public final class VectorHistory {
    public static final int LIMIT=64;
    public static final class Entry {
        public final String exact,shape;
        public final NotificationParser.Trend label;
        public Entry(String exact,String shape,NotificationParser.Trend label) {
            if (exact==null || !exact.matches("[0-9a-f]{64}") || (shape!=null && !shape.matches("[0-9a-f]{64}"))
                    || label==null || label==NotificationParser.Trend.UNKNOWN) throw new IllegalArgumentException("Invalid confirmation history");
            this.exact=exact; this.shape=shape; this.label=label;
        }
    }
    public static List<Entry> decode(String encoded) {
        if (encoded==null || encoded.length()>10000) throw new IllegalArgumentException("Invalid confirmation history");
        List<Entry> result=new ArrayList<>();
        if (!encoded.isEmpty()) for (String line:encoded.split("\n",-1)) {
            String[] fields=line.split(":",-1); if (fields.length!=3) throw new IllegalArgumentException("Invalid confirmation history");
            result.add(new Entry(fields[0],fields[1].equals("-")?null:fields[1],NotificationParser.Trend.valueOf(fields[2])));
        }
        if (result.size()>LIMIT) throw new IllegalArgumentException("Too much confirmation history");
        return Collections.unmodifiableList(result);
    }
    public static String append(String previous,Entry entry) {
        List<Entry> entries=new ArrayList<>(decode(previous)); entries.add(entry);
        while (entries.size()>LIMIT) entries.remove(0);
        StringBuilder result=new StringBuilder();
        for (Entry row:entries) {
            if (result.length()>0) result.append('\n');
            result.append(row.exact).append(':').append(row.shape==null?"-":row.shape).append(':').append(row.label.name());
        }
        return result.toString();
    }
    public static int[] counts(String encoded,String exact,String shape) {
        int same=0,forms=0; Set<String> variants=new HashSet<>();
        for (Entry entry:decode(encoded)) {
            if (entry.exact.equals(exact)) same++;
            if (shape!=null && shape.equals(entry.shape)) { forms++; variants.add(entry.exact); }
        }
        return new int[]{same,forms,variants.size()};
    }
    private VectorHistory() {}
}
