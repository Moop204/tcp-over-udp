import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.zip.CRC32;


public class STP implements Serializable {
    boolean syn;
    boolean ack;
    boolean fin;
    static int maxSize = 9999;

    Integer seqNum;
    Integer ackNum;
    byte[] data;
    Integer dataSize;
    long checksum;

  
    public STP(int seqNum1, int ackNum1, byte[] data1, int dataSize1, boolean syn1, boolean ack1, boolean fin1) {
        this.seqNum = seqNum1;
        this.ackNum = ackNum1; 
        this.data = data1;
        this.syn = syn1;
        this.ack = ack1;
        this.fin = fin1;
        this.dataSize = dataSize1;
        this.checksum = generateChecksum(seqNum1, ackNum1, data1, dataSize1, syn1, ack1, fin1);
    }

    static long generateChecksum(int seqNum1, int ackNum1, byte[] data1, int dataSize1, boolean syn1, boolean ack1, boolean fin1) {
        CRC32 crc = new CRC32();
        byte[] b = everythingToByte(seqNum1, ackNum1, data1, dataSize1, syn1, ack1, fin1);
        crc.update(b);
        return crc.getValue();
    }
    
    public long getChecksum() {
        return checksum;
    }
    
    public boolean compareChecksum() {
        return (this.checksum == this.generateChecksum(this.getSeqNum(), this.getAckNum(), this.getData(), this.getDataLength(), this.isSyn(), this.isAck(), this.isFin()));
    }
    
    private static byte[] everythingToByte(int seqNum, int ackNum, byte[] data, int dataSize, boolean syn, boolean ack, boolean fin) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        try {
            dout.writeInt(seqNum);
            dout.writeInt(ackNum);
            if(data != null) {
                dout.write(data);
            }
            dout.writeBoolean(syn);
            dout.writeBoolean(ack);
            dout.writeBoolean(fin);
            dout.writeInt(dataSize);
            dout.flush();
            return bout.toByteArray();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }
    
    static STP generateSTP(int seqNum, int ackNum, byte[] data, int dataSize, boolean syn, boolean ack, boolean fin) {
        return new STP(seqNum, ackNum, data, dataSize, syn,ack, fin);
    }
  
    public int getSeqNum() {
        return this.seqNum;
    }

    public int getAckNum() {
        return this.ackNum;
    }

  int getDataLength() {
      return this.dataSize;
  }
  
  boolean isSyn(){
    return this.syn;
  }

  boolean isAck(){
    return this.ack;
  }

  boolean isFin(){
    return this.fin;
  }

  byte[] getData() {
    return this.data;
  }

  void setData(byte[] data) {
    this.data = data;
  }

  public static byte[] serialise(Object obj) throws IOException {
	ByteArrayOutputStream out = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(out);
    oos.writeObject(obj);
    return out.toByteArray();
  }
  
    public static STP deserialise(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(in);
        return (STP) ois.readObject();
    }
    
    public static STP extractSTP(DatagramPacket dgp) throws ClassNotFoundException, IOException {
        byte[] stpData = dgp.getData();
        STP stp = (STP) deserialise(stpData);
        return stp;
    }
  
    public static DatagramPacket generateDatagram(int seqNum, int ackNum, byte[] data, int dataSize, boolean syn, boolean ack, boolean fin, InetAddress clientHost, int clientPort) throws IOException {
        STP stp = new STP(seqNum, ackNum, data, dataSize, syn, ack, fin); 
        byte[] bStp = STP.serialise(stp);
        //System.out.println("LENGTH OFFFFFF: "+bStp.length);
        return new DatagramPacket(bStp, bStp.length, clientHost, clientPort);
    }
    
    public static DatagramPacket generateDatagram(STP stp, InetAddress clientHost, int clientPort) throws IOException {
        byte[] bStp = STP.serialise(stp);
        return new DatagramPacket(bStp, bStp.length, clientHost, clientPort);
    }
    
    public static String analyseFlags(STP stp) {
        String flags = "";
        if(stp.isSyn()) 
            flags += "S";
        if(stp.isAck()) 
            flags += "A";
        if(stp.isFin()) 
            flags += "F";
        if(stp.dataSize > 0) {
            flags += "D"; 
        }
        return flags;
    }
    
    public static void analysePacket(STP stp) {
        if(stp.isSyn()) 
            System.out.print("SYN ");
        else 
            System.out.print("    ");
        if(stp.isAck()) 
            System.out.print("ACK ");
        else 
            System.out.print("    ");
        if(stp.isFin()) 
            System.out.print("FIN ");
        else 
            System.out.print("    ");
        System.out.print("\n");        
        System.out.println("SeqNo. "+stp.getSeqNum()+" AckNo. "+stp.getAckNum());
        System.out.println("Size "+ stp.getDataLength());
        
    }

    public static void analysePacket(DatagramPacket dg) throws ClassNotFoundException, IOException {
        STP stp = extractSTP(dg);
        analysePacket(stp);
    }
}
