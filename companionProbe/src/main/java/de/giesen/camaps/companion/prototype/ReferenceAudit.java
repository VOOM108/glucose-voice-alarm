// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import java.util.*;

/** Bounded private event identities; only the aggregate report may leave the phone. */
public final class ReferenceAudit {
    public enum Match { EXACT, SHAPE, UNKNOWN, CONFLICT, NO_CANDIDATE }
    public static final int LIMIT=128;
    private static List<String[]> decode(String encoded) {
        if (encoded==null || encoded.length()>24000) throw new IllegalArgumentException("Invalid reference audit");
        List<String[]> rows=new ArrayList<>();
        if (!encoded.isEmpty()) for (String line:encoded.split("\n",-1)) {
            String[] parts=line.split(":",-1);
            if (parts.length!=3) throw new IllegalArgumentException("Invalid reference audit");
            Match.valueOf(parts[0]);
            if (!parts[1].matches("-|[0-9a-f]{64}") || !parts[2].matches("-|[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid audit identity");
            rows.add(parts);
        }
        if (rows.size()>LIMIT) throw new IllegalArgumentException("Oversized reference audit"); return rows;
    }
    public static String append(String previous,Match match,String exact,String shape) {
        List<String[]> rows=decode(previous); String row=match.name()+":"+(exact==null?"-":exact)+":"+(shape==null?"-":shape);
        decode(row); rows.add(row.split(":")); while (rows.size()>LIMIT) rows.remove(0);
        StringBuilder result=new StringBuilder(); for (String[] parts:rows) {
            if (result.length()>0) result.append('\n'); result.append(String.join(":",parts));
        }
        return result.toString();
    }
    public static String report(String version,String encoded,String confirmations) {
        List<String[]> rows=decode(encoded); int exact=0,shape=0,unknown=0,conflict=0,none=0,repeated=0,variants=0;
        Set<String> seen=new HashSet<>(); Map<String,Set<String>> forms=new HashMap<>();
        for (String[] row:rows) {
            Match match=Match.valueOf(row[0]);
            if (match==Match.EXACT) exact++; else if (match==Match.SHAPE) shape++;
            else if (match==Match.UNKNOWN) {
                unknown++; if (seen.contains(row[1])) repeated++;
                if (!row[2].equals("-") && forms.containsKey(row[2]) && !forms.get(row[2]).contains(row[1])) variants++;
            } else if (match==Match.CONFLICT) conflict++; else none++;
            if (!row[1].equals("-")) seen.add(row[1]);
            if (!row[2].equals("-")) forms.computeIfAbsent(row[2],key -> new HashSet<>()).add(row[1]);
        }
        int confirmed=VectorHistory.decode(confirmations).size();
        return "Prototyp "+version+"\nReferenzdiagnose: nur Summen, keine Fingerabdruecke\n"
                +"Letzte Grafikpruefungen: "+rows.size()+"/128\nExakte Treffer: "+exact+"\nFormtreffer: "+shape
                +"\nUnbekannte Grafiken: "+unknown+"\nWiederholt exakt unbekannt: "+repeated
                +"\nUnbekannte Formvarianten: "+variants+"\nKonflikte: "+conflict+"\nOhne Kandidat: "+none
                +"\nManuelle Bestaetigungen: "+confirmed+"/64\nDatenbasis: keine Bilder, Werte oder Zeitstempel\n";
    }
    private ReferenceAudit() {}
}
