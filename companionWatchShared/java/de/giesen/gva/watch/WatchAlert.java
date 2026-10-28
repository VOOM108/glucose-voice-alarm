// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.gva.watch;
import java.io.*;
import java.util.UUID;
/** Ephemeral phone-to-watch alert events. Never a therapy or phone-control command. */
public final class WatchAlert {
    public static final String PATH="/gva/alert/v1";
    public static final int MAX_BYTES=2048;
    public static final long MAX_DELAY=30000,FUTURE=60000;
    public enum Action { SHOW,UPDATE,CLEAR }
    public final Action action;
    public final String session,token,title,text;
    public final long sequence,issued;
    public final boolean test;
    public WatchAlert(Action action,String session,long sequence,String token,long issued,String title,String text,boolean test) {
        if(action==null||!uuid(session)||!uuid(token)||sequence<=0||issued<=0||!safe(title,80)||!safe(text,500)
                ||(action!=Action.CLEAR&&(title.isEmpty()||text.isEmpty()))||(test&&action!=Action.SHOW))throw new IllegalArgumentException("Invalid alert event");
        this.action=action;this.session=session;this.sequence=sequence;this.token=token;this.issued=issued;this.title=title;this.text=text;this.test=test;
    }
    private static boolean uuid(String s){try{return s!=null&&UUID.fromString(s).toString().equals(s);}catch(IllegalArgumentException e){return false;}}
    private static boolean safe(String s,int max){if(s==null||s.length()>max)return false;for(int i=0;i<s.length();i++)if(Character.isISOControl(s.charAt(i))&&s.charAt(i)!='\n')return false;return true;}
    public boolean validAt(long now){return issued<=now+FUTURE&&issued>=now-MAX_DELAY;}
    public byte[] encode()throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){out.writeInt(0x47564141);out.writeInt(1);out.writeByte(action.ordinal());out.writeUTF(session);out.writeLong(sequence);out.writeUTF(token);out.writeLong(issued);out.writeUTF(title);out.writeUTF(text);out.writeBoolean(test);}
        if(bytes.size()>MAX_BYTES)throw new IOException("Alert packet too large");return bytes.toByteArray();
    }
    public static WatchAlert decode(byte[] bytes,long now)throws IOException {
        if(bytes==null||bytes.length>MAX_BYTES)throw new IOException("Invalid alert size");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(in.readInt()!=0x47564141||in.readInt()!=1)throw new IOException("Unsupported alert event");int a=in.readUnsignedByte();
            if(a>=Action.values().length)throw new IOException("Invalid alert action");
            WatchAlert event=new WatchAlert(Action.values()[a],in.readUTF(),in.readLong(),in.readUTF(),in.readLong(),in.readUTF(),in.readUTF(),in.readBoolean());
            if(in.read()!=-1||!event.validAt(now))throw new IOException("Expired or ambiguous alert event");return event;
        }catch(IllegalArgumentException e){throw new IOException("Invalid alert event",e);}
    }
}
