// SPDX-License-Identifier: GPL-3.0-or-later
// Original procedural audio: no third-party samples, downloads, or runtime synthesis.
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

public final class ToneAssetGenerator {
    private static final int RATE=44100;
    private static final String[] SETS={"samt","klar","rund"};
    private static final String[] KINDS={"high","persistent_high","low","falling","rising"};
    private static final double[][] PITCH={{698.456,391.995},{880,493.883},{587.330,329.628}};
    private static final double[][] PARTIAL={{1,.16,.035},{1,.27,.07},{1,.12,.03}};
    private static final double[][] RATIO={{1,2.003,3.99},{1,2.01,4.12},{1,2,3}};
    private static int frames(int milliseconds) { return RATE*milliseconds/1000; }
    private static double[] bell(int family,double pitch,int ms) {
        double[] wave=new double[frames(ms)];
        double sum=0,weight=0;
        for (int i=0;i<wave.length;i++) {
            double time=i/(double)RATE;
            double attack=Math.pow(Math.sin(Math.PI/2*Math.min(1,time/.018)),2);
            double release=Math.pow(Math.sin(Math.PI/2*Math.min(1,(wave.length-1-i)/(RATE*.030))),2);
            double envelope=attack*release;
            double decay=ms>500?.34:.15;
            for (int p=0;p<3;p++) wave[i]+=envelope*PARTIAL[family][p]*Math.exp(-time/(decay/(1+p*.75)))
                    *Math.sin(2*Math.PI*pitch*RATIO[family][p]*time);
            sum+=wave[i]; weight+=envelope;
        }
        // Remove DC without disturbing the zero endpoints.
        double mean=sum/weight,peak=0;
        for (int i=0;i<wave.length;i++) {
            double time=i/(double)RATE;
            double envelope=Math.pow(Math.sin(Math.PI/2*Math.min(1,time/.018)),2)
                    *Math.pow(Math.sin(Math.PI/2*Math.min(1,(wave.length-1-i)/(RATE*.030))),2);
            wave[i]-=mean*envelope; peak=Math.max(peak,Math.abs(wave[i]));
        }
        // Store the full headroom. Runtime gain 0.5 preserves the original level at 0 dB boost.
        for (int i=0;i<wave.length;i++) wave[i]*=.96/peak;
        return wave;
    }
    private static double[] join(List<double[]> clips,int silenceMs) {
        int size=frames(silenceMs)*(clips.size()-1); for (double[] clip:clips) size+=clip.length;
        double[] joined=new double[size]; int offset=0;
        for (double[] clip:clips) { System.arraycopy(clip,0,joined,offset,clip.length); offset+=clip.length+frames(silenceMs); }
        return joined;
    }
    private static double[] tone(int family,int kind) {
        double high=PITCH[family][0],low=PITCH[family][1];
        if (kind==0) return bell(family,high,360);
        if (kind==1) return bell(family,high,760);
        if (kind==2) return bell(family,low,360);
        return join(Arrays.asList(bell(family,kind==3?high:low,200),bell(family,kind==3?low:high,200)),100);
    }
    private static void write(Path path,double[] wave) throws IOException {
        ByteBuffer b=ByteBuffer.allocate(44+wave.length*2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes("US-ASCII")).putInt(36+wave.length*2).put("WAVEfmt ".getBytes("US-ASCII"));
        b.putInt(16).putShort((short)1).putShort((short)1).putInt(RATE).putInt(RATE*2).putShort((short)2).putShort((short)16);
        b.put("data".getBytes("US-ASCII")).putInt(wave.length*2);
        for (double sample:wave) b.putShort((short)Math.round(sample*32767));
        Files.write(path,b.array());
    }
    public static void main(String[] args) throws Exception {
        Path raw=Paths.get(args[0],"raw"),previews=Paths.get(args[1]); Files.createDirectories(raw); Files.createDirectories(previews);
        for (int family=0;family<SETS.length;family++) {
            List<double[]> sequence=new ArrayList<>();
            for (int kind=0;kind<KINDS.length;kind++) {
                double[] wave=tone(family,kind);
                double[] audition=wave.clone(); for (int i=0;i<audition.length;i++) audition[i]*=.5;
                sequence.add(audition);
                write(raw.resolve("xcam_"+SETS[family]+"_"+KINDS[kind]+".wav"),wave);
            }
            write(previews.resolve("XCam-"+SETS[family]+"-Set.wav"),join(sequence,350));
        }
        System.out.println("Generated 15 original soft-bell cues and three set auditions, PCM 44.1 kHz mono");
    }
}
