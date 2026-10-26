// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** UI catalog for the programmatic Android views. Stored user text and protocol fields bypass it. */
public final class AppText {
    private static final Properties EN=new Properties();
    private static final Map<String,String> SOURCE=new HashMap<>();
    private static volatile String language="de";
    static {
        try(InputStream input=AppText.class.getResourceAsStream("/i18n/ui-en.tsv")) {
            if(input!=null) {
                BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8)); String line;
                while((line=reader.readLine())!=null) {
                    if(line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts=line.split("\t",-1); if(parts.length!=2) throw new IOException("Invalid UI catalog");
                    EN.setProperty(decode(parts[0]),decode(parts[1]));
                }
            }
        } catch(IOException e) { throw new ExceptionInInitializerError(e); }
        for(String key:EN.stringPropertyNames()) SOURCE.putIfAbsent(EN.getProperty(key),key);
    }
    public static void configure(Locale locale) { language=locale!=null && "de".equals(locale.getLanguage())?"de":"en"; }
    public static String language() { return language; }
    public static String tr(String source) { return in(language,source); }
    public static String in(String language,String source) { return source==null?null:"en".equals(language)?EN.getProperty(source,source):source; }
    static String source(String text) { return "en".equals(language)?SOURCE.getOrDefault(text,text):text; }
    private static String decode(String value) throws IOException {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='\\') {
                if(++i==value.length()) throw new IOException("Invalid UI escape"); c=value.charAt(i);
                if(c=='n') c='\n'; else if(c=='r') c='\r'; else if(c=='t') c='\t'; else if(c!='\\') throw new IOException("Invalid UI escape");
            }
            out.append(c);
        }
        return out.toString();
    }
    private AppText() {}
}
