import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Logger {
    private FileWriter fwriter;
    private int totalDataReceived;
    private int totalSegmentsReceived;
    private int totalDataSegmentsReceived;

    private int totalDuplicateAcks;
    private int totalSegmentsSent;
    int drops;
    int corrupts;
    int reords;
    int dupes;
    int delays;


    public Logger (String name) throws IOException {
        FileWriter cleaner = new FileWriter(name, false);
        cleaner.write("");
        cleaner.close();
        this.fwriter = new FileWriter(name, true);
        this.fwriter.write("");
        this.totalDataReceived = 0;
        this.totalSegmentsReceived = 0;
        this.totalDataSegmentsReceived = 0;
        this.totalDuplicateAcks = 0;
        this.drops=0;
        this.corrupts=0;
        this.reords=0;
        this.dupes=0;
        this.delays=0;
    }

    public void log(String event, long time, STP pkt) throws IOException {
        long curTime = System.currentTimeMillis();

        this.totalDataReceived+=pkt.dataSize;
        String[] fields = {
            event,
            Long.toString((curTime-time)/1000), 
            STP.analyseFlags(pkt),
            Integer.toString(pkt.getSeqNum()),
            Integer.toString(pkt.getDataLength()),
            Integer.toString(pkt.getAckNum()),
            "\n"
        };

        if(event.equals("drop")) {
            this.drops++;
        } else if (event.equals("snd/corr")) {
            this.corrupts++;
        } else if (event.equals("snd/rord")) {
            this.reords++;
        } else if (event.equals("snd/dup")) {
            this.dupes++;
        } else if (event.equals("snd/dely")) {
            this.delays++;
        }

        if(event.equals("snd") || event.equals("drop")) {
            totalSegmentsSent++;
        }
        if(pkt.dataSize != 0) {
            totalDataReceived+= pkt.dataSize;
            totalDataSegmentsReceived++;
        }

        String record = String.join(" ", fields);
        this.fwriter.write(record);
        //this.bwriter.write(record);
        System.out.println(record);
    }

    public void logLiteral(String str) {
        try {
            this.fwriter.write(str);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(str);
    }

    public void endReceiverLog(int totalErrors, int totalDupeRec, int totalDupeSent) throws IOException {
        this.logLiteral("==============================================\n");
        this.logLiteral("Amount of data received (bytes)\t\t"+Integer.toString(totalDataReceived)+"\n");
        this.logLiteral("Total Segments Received\t\t\t"+Integer.toString(totalSegmentsReceived)+"\n");
        this.logLiteral("Data segments received\t\t\t"+Integer.toString(totalDataSegmentsReceived)+"\n");
        this.logLiteral("Data segments with Bit Errors\t\t" + Integer.toString(totalErrors)+"\n");
        this.logLiteral("Duplicate data segments received\t"+Integer.toString(totalDupeRec)+"\n");
        this.logLiteral("Duplicate ACKs sent\t\t\t"+Integer.toString(totalDupeSent)+"\n");
        this.logLiteral("==============================================\n");

        this.fwriter.close();
    }

    public void endSenderLog(int fileSize, int pldSeg, int toReTrans, int fReTrans, int dupeAcks ) throws IOException {
        this.logLiteral("=============================================================\n");
        this.logLiteral("Size of the file (in Bytes)\t\t\t\t"+fileSize);
        this.logLiteral("Segments transmitted (including drop & RXT)\t\t"+totalSegmentsSent);
        this.logLiteral("Number of Segments handled by PLD\t\t\t"+pldSeg);
        this.logLiteral("Number of Segments dropped\t\t\t\t"+drops);
        this.logLiteral("Number of Segments Corrupted\t\t\t\t"+corrupts);
        this.logLiteral("Number of Segments Re-ordered\t\t\t\t"+reords);
        this.logLiteral("Number of Segments Duplicated\t\t\t\t"+dupes);
        this.logLiteral("Number of Segments Delayed\t\t\t\t"+delays);
        this.logLiteral("Number of Retransmissions due to TIMEOUT\t\t"+toReTrans);
        this.logLiteral("Number of FAST RETRANSMISSION\t\t\t\t"+fReTrans);
        this.logLiteral("Number of DUP ACKS received\t\t\t\t"+dupeAcks);
        this.logLiteral("=============================================================\n");

    }
}
