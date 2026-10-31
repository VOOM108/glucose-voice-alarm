// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;
import de.giesen.gva.watch.ArrowColor;
import java.io.*;
import java.util.*;

/** Literal optional colour-specific words. Disabled or blank entries inherit the normal word. */
public final class TrendColorOverrides {
    public static final int COUNT=7;
    public final List<String> red,yellow;
    public final List<Boolean> redEnabled,yellowEnabled;
    public TrendColorOverrides(List<String> red,List<Boolean> redEnabled,List<String> yellow,List<Boolean> yellowEnabled) {
        this.red=words(red);this.yellow=words(yellow);this.redEnabled=flags(redEnabled);this.yellowEnabled=flags(yellowEnabled);validate(80);
    }
    private static List<String> words(List<String> source) {
        if(source==null || source.size()!=COUNT || source.contains(null))throw new IllegalArgumentException(AppText.tr("Sieben Farbtexte erforderlich"));
        // Validate before trimming: a trailing line break must not silently become valid text.
        for(String word:source)if(word.codePoints().anyMatch(Character::isISOControl) || word.contains("{") || word.contains("}"))throw new IllegalArgumentException(AppText.tr("Farbtexte müssen einzeilig sein und dürfen keine Platzhalter enthalten"));
        List<String> copy=new ArrayList<>();for(String word:source)copy.add(word.trim());return Collections.unmodifiableList(copy);
    }
    private static List<Boolean> flags(List<Boolean> source) {
        if(source==null || source.size()!=COUNT || source.contains(null))throw new IllegalArgumentException(AppText.tr("Sieben Farbauswahlen erforderlich"));
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
    public void validate(int maximum) {
        for(List<String> entries:Arrays.asList(red,yellow))for(String word:entries)if(word.length()>maximum)
            throw new IllegalArgumentException(maximum==40?AppText.tr("Anzeigetexte: höchstens 40 Zeichen, ohne Zeilenumbrüche oder Platzhalter"):AppText.tr("Eigene Farbtexte: höchstens 80 Zeichen, einzeilig, ohne Platzhalter"));
    }
    public static TrendColorOverrides defaults(){return new TrendColorOverrides(Collections.nCopies(COUNT,""),Collections.nCopies(COUNT,false),Collections.nCopies(COUNT,""),Collections.nCopies(COUNT,false));}
    public String resolve(int index,ArrowColor color,String normal) {
        if(index<0 || index>=COUNT || color==null || color==ArrowColor.NORMAL)return normal;
        List<String> entries=color==ArrowColor.RED?red:yellow;List<Boolean> flags=color==ArrowColor.RED?redEnabled:yellowEnabled;
        return flags.get(index) && !entries.get(index).isEmpty()?entries.get(index):normal;
    }
    public void write(DataOutputStream out)throws IOException {
        for(int i=0;i<COUNT;i++){out.writeBoolean(redEnabled.get(i));out.writeUTF(red.get(i));out.writeBoolean(yellowEnabled.get(i));out.writeUTF(yellow.get(i));}
    }
    public static TrendColorOverrides read(DataInputStream in,int maximum)throws IOException {
        List<String> red=new ArrayList<>(),yellow=new ArrayList<>();List<Boolean> r=new ArrayList<>(),y=new ArrayList<>();
        for(int i=0;i<COUNT;i++){r.add(in.readBoolean());red.add(in.readUTF());y.add(in.readBoolean());yellow.add(in.readUTF());}
        TrendColorOverrides result=new TrendColorOverrides(red,r,yellow,y);result.validate(maximum);return result;
    }
}
