import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Random;

public class PLD {
    
    Double pDelay;
    Double pOrder;
    int maxOrder;
    int maxDelay;
    Double pCorrupt;
    Double pDuplicate;
    Double pDrop;
    int gamma;
    static Random randomGen;
	STP storedOrder;
	int storedCounter;
	DatagramSocket socket; 
	long initTime;
	
    public PLD(Double pDrop, Double pDuplicate, Double pCorrupt, Double pOrder, Double pDelay, int seed, int maxOrder, int maxDelay, int gamma, DatagramSocket socket) {
        this.socket = socket;
        this.pDelay = pDelay;
        this.pOrder = pOrder;
        this.pCorrupt = pCorrupt;
        this.pDuplicate = pDuplicate;
        this.pDrop = pDrop;
        this.maxOrder = maxOrder;
        this.maxDelay = maxDelay;
        this.gamma = gamma;
        this.randomGen = new Random(seed);
    	this.storedOrder = null;
    	this.storedCounter = -1;
    	this.initTime = 0;
    }
    
    private float getRandom() { 
    	return this.randomGen.nextFloat();
    }

    
    
    public void send(STP stpPkt, Sender s, InetAddress clientHost, int clientPort) throws IOException, InterruptedException, ClassNotFoundException {
        /*
        if (stpPkt == null) {
            System.out.println("NAH BRO ITS FUCKED");
        } else {
            System.out.println("ALL GUDS");
        }
        */
        if(this.storedCounter > 0) {
            this.storedCounter--;
        } else if (this.storedCounter == 0) {
            sendDatagram(this.storedOrder, clientHost, clientPort, s, "snd/rord");
        }
        
        //STP.analysePacket(stpPkt);
        
        if (stpPkt.isAck() || stpPkt.isFin() || stpPkt.isSyn()) {
            sendDatagram(stpPkt, clientHost, clientPort, s, "snd");            
            return;
        }

    	if (getRandom() < pDrop) {
            s.senderLogger.log("drop", initTime, stpPkt);
    		return;
    	} else if (getRandom() < pDuplicate) {
            sendDatagram(stpPkt, clientHost, clientPort, s, "snd/dup");
            sendDatagram(stpPkt, clientHost, clientPort, s, "snd/dup");
    	} else if (getRandom()< pCorrupt) {
    		byte[] data = stpPkt.getData();
    		byte mask = (byte) 1;
    		byte check = (byte) (data[0] & mask);
    		if ((int) check == 1) {
    		    byte wMask = ~(byte) 1;
    		    data[0] = (byte) (data[0] & wMask);
    		} else {
                byte wMask = (byte) 1;
                data[0] = (byte) (data[0] | wMask);    		    
    		}
    		stpPkt.setData(data);
            sendDatagram(stpPkt, clientHost, clientPort, s, "snd/corr");
            return;
    	} else if (getRandom() < pOrder) {
    		if (this.storedOrder == null) {
    			this.storedOrder = stpPkt;
    			this.storedCounter = maxOrder;
    		} else {
    		    sendDatagram(stpPkt, clientHost, clientPort, s, "snd");
    		}
            return;
    	} else if (getRandom() < pDelay) {
    	    DelayedSend dThread = new DelayedSend(getRandom(), stpPkt, clientHost, clientPort, s);
            s.senderLogger.log("snd/dely", initTime, stpPkt);
            return;
    	} else {
            sendDatagram(stpPkt, clientHost, clientPort, s, "snd");    	    
            return;
    	}
    	System.out.println("You're not meant to be here?!");
    }
    
    private void sendDatagram(STP stpPkt, InetAddress clientHost, int clientPort, Sender s, String event) throws IOException, ClassNotFoundException {
        DatagramPacket datagramPkt = STP.generateDatagram(stpPkt, clientHost, clientPort);
        s.senderLogger.log(event, initTime, stpPkt);

        //STP.analysePacket(datagramPkt);
        this.socket.send(datagramPkt);
    }

    class DelayedSend implements Runnable {
        private double random;
        private STP stpPkt;
        private InetAddress clientHost;
        private int clientPort;
        private Sender sender;
        
        public DelayedSend(double random, STP stpPkt, InetAddress clientHost, int clientPort, Sender s) {
            this.random = random;
            this.stpPkt = stpPkt;
            this.clientHost = clientHost;
            this.clientPort = clientPort;
            this.sender = s;
        }
        @Override
        public void run() {
            try {
                Thread.sleep((long) (random*maxDelay));
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            try {
                sendDatagram(stpPkt, clientHost, clientPort, sender, "snd/dely");
            } catch (ClassNotFoundException | IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            // TODO Auto-generated method stub
            
        }
        
    }
    
}
