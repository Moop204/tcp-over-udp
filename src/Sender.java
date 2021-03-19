
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;

public class Sender{

    public Integer mss;
    Integer mws;
    DatagramSocket socket;
    InetAddress reIp;
    Integer rePort;
    public Integer seqNum;
    public Integer ackNum;
    public volatile static Integer minWin;
    public volatile static Integer maxWin;
    Integer estRTT;
    Integer devRTT;
    Timer toTimer;
    File file; 
    public Long initialTime; 
    PLD pld;
    public Logger senderLogger;
    Integer expectedAck;
    public int repeats = 0;
    public int pldSegments =0;
    int toReTrans =0;
    int fReTrans=0;
    int totalDupeAcks=0;
    
    public Sender(InetAddress reIp, int rePort, String file, int MSS, int MWS, PLD pld, DatagramSocket socket) throws IOException {
        this.mss = MSS;
        this.mws = MWS;
        this.socket = socket;
        this.reIp = reIp;
        this.rePort = rePort;
        this.seqNum = 0;
        this.ackNum = 0;
        this.toTimer = new Timer();
        this.estRTT = 500;
        this.devRTT = 250;
    	this.file = new File(file);
        this.pld = pld;
        this.senderLogger = new Logger("Sender_log.txt"); 
        this.expectedAck = 0;
    }
    
    public void closeAll() throws IOException {
        
        SendThread s = new SendThread();
        ReceiveThread r = new ReceiveThread();
        byte[] bFile = null;
        try {
            bFile = Files.readAllBytes(file.toPath());
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        int size = bFile.length;

    	this.socket.close();
    	this.toTimer.cancel();
    	this.senderLogger.endSenderLog(size, pldSegments, toReTrans, fReTrans, totalDupeAcks);
    }
    
    public void setAckNum(STP stp) {
        this.ackNum = stp.getSeqNum() + stp.getDataLength();
        if (stp.isSyn() || stp.isFin()) {
            this.ackNum++;
        } 
    }

    public int getExpAck() {
        return this.expectedAck;
    }

    public void setExpAck(int exp) {
        this.expectedAck = exp;
    }
    
    public void setSeqNum(STP stp) {
        this.seqNum = stp.getAckNum();
    }

    public void setSeqNum(int i) {
        this.seqNum = i;
    }

    private int calcTimeoutInterval() {
        return this.estRTT + 4*devRTT;
    }
    
    public void send(DatagramPacket dp) throws IOException {
        this.socket.send(dp);
    }

    private DatagramPacket noWaitResponse() throws ClassNotFoundException, IOException {
        //System.out.println("WAITING");
        DatagramPacket pkt = new DatagramPacket(new byte[STP.maxSize], STP.maxSize);
        try {
            int tmp = this.socket.getSoTimeout();
            try {
                this.socket.setSoTimeout(1);
                this.socket.receive(pkt);
                this.socket.setSoTimeout(tmp);
            } catch (IOException e) {
                this.socket.setSoTimeout(tmp);
                return null;
            }
            //System.out.print("Sender: Packet receieved ");
            this.setAckNum(STP.extractSTP(pkt));
            this.setSeqNum(STP.extractSTP(pkt));
            //STP.analysePacket(pkt);
            this.senderLogger.log("rcv", this.initialTime, STP.extractSTP(pkt));
            return pkt;
        }
        catch (SocketTimeoutException x) {
            return null;
        }
    }

    private DatagramPacket awaitResponse() throws ClassNotFoundException, IOException {
        DatagramPacket pkt = new DatagramPacket(new byte[STP.maxSize], STP.maxSize);
        try {
            try {
                this.socket.receive(pkt);
            } catch (IOException e) {
                return null;
            }
            this.setAckNum(STP.extractSTP(pkt));
            this.setSeqNum(STP.extractSTP(pkt));
            this.senderLogger.log("rcv", this.initialTime, STP.extractSTP(pkt));
            return pkt;
        }
        catch (SocketTimeoutException x) {
            return null;
        }
    }
    
    public void initiateConnection() throws IOException, ClassNotFoundException, InterruptedException {
        this.initialTime = System.currentTimeMillis();
        this.sendSyn();        
        // Collect Syn + Ack Response
        this.awaitResponse();   
        // Send Ack
        this.sendAck();
        return;
    }
        
    private boolean expectedAck(int recAck, int winSta) {
        return (recAck == (winSta+1)*mss+1 || recAck == file.length()+1);
    }
    
    public void transferFile() throws ClassNotFoundException, IOException, InterruptedException {
        
        maxWin = 0;
        minWin = 0;
        
        SendThread s = new SendThread();
        ReceiveThread r = new ReceiveThread();
        byte[] bFile = null;
        try {
            bFile = Files.readAllBytes(file.toPath());
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        int size = bFile.length;

        int totalSeg = size/mss;
        if(size%mss != 0) 
            totalSeg++;
        
        while(minWin < totalSeg) {
            s.run();
            r.run();
        }
    
    }
    class SendThread implements Runnable {
        @Override 
        public void run() {

            byte[] bFile = null;
            try {
                bFile = Files.readAllBytes(file.toPath());
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            int size = bFile.length;
        
            int totalSeg = size/mss;
            if(size%mss != 0) 
                totalSeg++;
            

            if (repeats == 3) {
                int fileStart=minWin*mss;
                setSeqNum(fileStart+1);
                int fileEnd=Math.min(fileStart+mss, bFile.length);
                try {
                    sendData(fileStart, fileEnd);
                } catch (ClassNotFoundException | IOException | InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                fReTrans++;
            }
            
            while(maxWin - minWin < mws) {     
                toTimer.schedule(new Timeout() , calcTimeoutInterval());                
                
                if (maxWin >= totalSeg)
                    break;
                
                int fileStart=maxWin*mss;
                setSeqNum(fileStart+1);
                int fileEnd=Math.min(fileStart+mss, bFile.length);
                try {
                    sendData(fileStart, fileEnd);
                } catch (ClassNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } 
                
                if (maxWin < totalSeg)
                    maxWin ++;
            }

        }
    }

    public class Timeout extends TimerTask {
        public void run() {
            maxWin = minWin;
            toReTrans+=mws;
        }
    }
    
    class ReceiveThread implements Runnable {
        @Override
        public void run() {
            STP received = null;
            try {
                DatagramPacket tmp = noWaitResponse();
                if (tmp == null) {
                    return;
                } else {
                    received = STP.extractSTP(tmp);
                }
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            if (expectedAck(received.getAckNum(), minWin)) {
                minWin++;
                repeats = 0;
            } else {
                totalDupeAcks++;
                repeats++;
            }
        }
    }
    
    public void sendData(int dataStart, int dataEnd) throws IOException, ClassNotFoundException, InterruptedException {
        byte[] fileFragment = Arrays.copyOfRange(Files.readAllBytes(file.toPath()), dataStart, dataEnd);
        STP pkt= STP.generateSTP(seqNum, ackNum, fileFragment, fileFragment.length, false, false, false);
        this.pldSegments++;
        this.pld.send(pkt, this, reIp, rePort);        
        // TODO Move logger call to PLD
    }

    public void sendSyn() throws IOException, ClassNotFoundException, InterruptedException {
        
        DatagramPacket reply = STP.generateDatagram(seqNum, ackNum, null, 0, true, false, false, reIp, rePort);
        //System.out.printf("Sender: Sent ");
        //STP.analysePacket(reply);
        this.senderLogger.log("snd", this.initialTime, STP.extractSTP(reply));
        this.socket.send(reply);
    }

    public void sendAck() throws IOException, ClassNotFoundException, InterruptedException {
        DatagramPacket reply = STP.generateDatagram(seqNum, ackNum, null, 0, false, true, false, reIp, rePort);
        //System.out.printf("Sender: Sent ");
        //STP.analysePacket(reply);
        this.senderLogger.log("snd", this.initialTime, STP.extractSTP(reply));
        this.socket.send(reply);
    }

    public void sendFin() throws IOException, ClassNotFoundException, InterruptedException {
        DatagramPacket reply = STP.generateDatagram(seqNum, ackNum, null, 0, false, false, true, reIp, rePort);
        //System.out.printf("Sender: Sent ");
        //STP.analysePacket(reply);
        this.senderLogger.log("snd", this.initialTime, STP.extractSTP(reply));
        this.socket.send(reply);
    }
    
    private void terminateConnection() throws ClassNotFoundException, IOException, InterruptedException {
        this.sendFin();
        this.awaitResponse();
        DatagramPacket expAck = null;         
        byte[] bFile = null;
        try {
            bFile = Files.readAllBytes(file.toPath());
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        int size = bFile.length;
        
        while (expAck == null  || ! (STP.extractSTP(expAck).getAckNum() == size+2)) {
            expAck = this.awaitResponse();
        }
        
        
        DatagramPacket expFin = null;         
        while (expFin == null  || !STP.extractSTP(expFin).isFin()) {
            expFin = this.awaitResponse();
        }
        this.sendAck();
        this.closeAll();
        return;
    }
    
    private static Boolean validateRange(String name, Double val, Double min, Double max) {
        if(val > max || val < min) {
          System.out.println(String.format("Error: %s must be a value between %f and %f.", name, min, max));
          return false;
        }
        return true;
    }

    private static Boolean validateRange(String name, int val, int min, int max) {
        if(val > max || val < min) {
            System.out.println(String.format("Error: %s must be a value between %d and %d.", name, min, max));
            return false;
        }
        return true;
    }

    private static Sender constructSender(String[] args) throws IOException {
        if (args.length != 14) {
            System.out.println("Required arguments: receiver_host_ip receiver_port file.pdf MWS MSS gamma pDrop pDuplicate pCorrupt pOrder maxOrder pDelay maxDelay seed");
            return null;
        }
        InetAddress reIp = InetAddress.getByName(args[0]);
        int rePort = Integer.parseInt(args[1]);    
        String fileName = args[2];
        int MWS = Integer.parseInt(args[3]);
        int MSS = Integer.parseInt(args[4]);
        int gamma = Integer.parseInt(args[5]);
        Double pDrop = Double.parseDouble(args[6]);
        if(!validateRange("pDrop", pDrop, 0.0, 1.0)){
          return null;
        }
        Double pDuplicate = Double.parseDouble(args[7]);
        if(!validateRange("pDuplicate", pDuplicate, 0.0, 1.0)){
          return null;
        }
        Double pCorrupt = Double.parseDouble(args[8]);
        if(!validateRange("pCorrupt", pCorrupt, 0.0, 1.0)){
          return null;
        }
        Double pOrder = Double.parseDouble(args[9]);
        if(!validateRange("pOrder", pOrder, 0.0, 1.0)){
          return null;
        }
        int maxOrder = Integer.parseInt(args[10]);
        if(!validateRange("maxOrder", maxOrder, 1, 6)){
          return null;
        }
        Double pDelay = Double.parseDouble(args[11]);
        if(!validateRange("pDelay", pDelay, 0.0, 1.0)){
          return null;
        }
        int maxDelay = Integer.parseInt(args[12]);
        int seed = Integer.parseInt(args[13]);
        
        DatagramSocket socket = new DatagramSocket();
        PLD pld = new PLD(pDrop, pDuplicate, pCorrupt, pOrder, pDelay, maxOrder, maxDelay, gamma, seed, socket);
        return new Sender(reIp, rePort, fileName, MSS, MWS, pld, socket);
    }

    public static void main(String[] args) throws Exception {
        Sender snd = constructSender(args);
        System.out.println("<===== CONNECTING ======>");
        snd.initiateConnection();
        System.out.println("<===== TRANSFERING ======>");
        snd.transferFile();
        System.out.println("<===== CLOSING ======>");
        snd.terminateConnection();
        System.out.println("<===== FINISHED =====>");
        return;
    }

}
