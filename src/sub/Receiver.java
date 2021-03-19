import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Random;

public class Receiver {
    DatagramSocket socket;
    int seqNum = 0;
    int ackNum = 0;
    static int totalErrors = 0;
    InetAddress clientHost;
    int clientPort;
    String fileName;
    Logger logger;
    int totalDupeRec =0;
    int totalDupeSent =0;
    long initTime=-1;
    ArrayList<STP> buffer = new ArrayList<STP>();
            
    public Receiver(int re_port, String fileName) throws IOException {
        this.socket = new DatagramSocket(re_port);
        this.fileName = fileName;
        this.logger = new Logger("Receiver_log.txt"); 

    }
    
    private DatagramPacket awaitResponse() throws IOException, ClassNotFoundException {
        //System.out.println("WAITING FOR A PACKET");
        DatagramPacket pkt = new DatagramPacket(new byte[STP.maxSize], STP.maxSize);
        try {
        	this.socket.receive(pkt);
        	if(initTime == -1 ) {
        	    initTime = System.currentTimeMillis();
        	}
        	STP stp = (STP) STP.deserialise(pkt.getData());
        	//STP.analysePacket(stp);
        	//System.out.print("Receiver: Packet receieved ");
            this.setAckNum(stp); 
            this.setSeqNum(stp);
            this.logger.log("rcv", this.initTime, stp);
        	return pkt;
        }
        catch (SocketTimeoutException x) {
        	return null;
        }
    }

    public void setAckNum(STP stp) {
        if (stp.getSeqNum() == this.ackNum) this.ackNum = stp.getSeqNum() + stp.getDataLength();
        if (stp.isSyn() || stp.isFin()) {          
            this.ackNum++;
        }

    }

    public int getAckNum() {
        return this.ackNum;
    }
    
    public void setSeqNum(STP stp) {
        this.seqNum = stp.getAckNum();
    }
    
    public void setClientHost(InetAddress addr) {
        this.clientHost = addr;
    }
    
    public void setClientPort(int port) {
        this.clientPort= port;
    }

    public void sendSynAck() throws IOException, ClassNotFoundException {        
        DatagramPacket reply = STP.generateDatagram(this.seqNum, this.ackNum, null, 0, true, true, false, clientHost, clientPort);
        //System.out.print("Receiver: Sent ");
        //STP.analysePacket(reply);
        this.logger.log("snd", this.initTime, STP.extractSTP(reply));
        this.socket.send(reply);
    }
    
    public void sendAck() throws IOException, ClassNotFoundException {        
        DatagramPacket reply = STP.generateDatagram(this.seqNum, this.ackNum, null, 0, false, true, false, clientHost, clientPort);
        //STP.analysePacket(reply);
        //System.out.printf("Receiver: Sent ");
        this.logger.log("snd", this.initTime, STP.extractSTP(reply));
        this.socket.send(reply);
    }
    
    public void sendFin() throws IOException, ClassNotFoundException {
        DatagramPacket reply = STP.generateDatagram(this.seqNum, this.ackNum, null, 0, false, false, true, clientHost, clientPort);
        //System.out.printf("Receiver: Sent ");
        //STP.analysePacket(reply);
        this.socket.send(reply);
        this.logger.log("snd", this.initTime, STP.extractSTP(reply));
        //System.out.println("IS FINISHED DUDE");
    }

    public void closeAll() throws IOException {
        this.logger.endReceiverLog(totalErrors, totalDupeRec, totalDupeSent);
        this.socket.close();
    }
    
    public void updateBuffer() {
        int initSize = this.buffer.size();
        ArrayList<STP> rem = new ArrayList<STP>();
        for (STP s : this.buffer) { 
            if(s.getSeqNum() == this.ackNum) {
                rem.add(s);
            }
        }
        for (STP s :rem) {
            this.setAckNum(s); 
            this.setSeqNum(s);
            this.buffer.remove(s);
        }
        
        if (initSize != this.buffer.size()) {
            updateBuffer();
        }
    }
    
    public static void main(String[] args) throws Exception
    {
     // Get command line argument.
        if (args.length != 2) {
            System.out.println("Required arguments: receiver_port file_r.pdf");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        String fileName = args[1];
     
        Receiver rcv = new Receiver(port, fileName);
        
        // Initialise connection
        
        DatagramPacket request = rcv.awaitResponse();

        InetAddress clientHost = request.getAddress();
        int clientPort = request.getPort();
        rcv.setClientHost(clientHost);
        rcv.setClientPort(clientPort);        

        rcv.sendSynAck();
        DatagramPacket newPkt = rcv.awaitResponse();

        //DatagramPacket reply = STP.generateDatagram(seqNum, ackNum, null, true, true, false, clientHost, clientPort);
        //rcv.socket.send(reply);
        //System.out.println("Receiver: Sent reply to "+clientHost+":"+clientPort);
   
     // Processing loop.
        ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
        System.out.println("<===== RECEIVING PACKETS ======>");        
        while (true) {

            newPkt = rcv.awaitResponse();
            //System.out.println("Acknum is "+ rcv.ackNum);            
            //System.out.println("Seqnum is "+rcv.seqNum);
            clientHost = newPkt.getAddress();
            clientPort = newPkt.getPort();
            STP received = STP.extractSTP(newPkt);
            if(! received.compareChecksum() ) {
                totalErrors++;
                continue;
            }
            if(received.getData() != null) {
                if(received.getSeqNum() == rcv.getAckNum()) {
                    dataStream.write(received.getData());
                    rcv.updateBuffer();
                } else if (received.getSeqNum() + received.getDataLength() == rcv.getAckNum()){
                    rcv.totalDupeRec++;
                } else if (received.getSeqNum() + received.getDataLength() > rcv.getAckNum()) {
                    rcv.buffer.add(received);
                } else {
                    rcv.totalDupeSent++;
                }
            }
            rcv.sendAck();
            
            // generalise acks and add fin conditions
            if(received.isFin()) {
                System.out.println("<===== CLOSING CONNECTION ======>");

                // Write pdf at receiver end
                dataStream.close();
                byte[] finalByteArray = dataStream.toByteArray();
                FileOutputStream fileStream = new FileOutputStream(fileName);
                fileStream.write(finalByteArray);
                fileStream.close();

                
                // Generate a FIN response
                rcv.sendFin();                
                newPkt = rcv.awaitResponse();
                rcv.closeAll();
                break;
            } 
        }
    }
}
    