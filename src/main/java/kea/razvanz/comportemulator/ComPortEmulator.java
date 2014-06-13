/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package kea.razvanz.comportemulator;

/**
 *
 * @author razvanz
 */
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.LinkedTransferQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import kea.razvanz.comportemulator.util.ThrottledInputStream;
import kea.zmirc.systemintegration.p1.shared.UDPSocket;
import kea.zmirc.systemintegration.p1.shared.util.User;

/**
 *
 * @author razvanz
 */
public class ComPortEmulator {
    
    public static final long NANOS_PER_SECOND = 1000000000;
    public static int DEFAULT_BAUD = 9600;
    public static final int DEFAUL_NOISE_EXPENTANCY = 1; // procent
    
    private long baud = DEFAULT_BAUD;
    private long noiseExpectancy = DEFAUL_NOISE_EXPENTANCY;
    
    private long lastTansferRate = 0;
    private long startCountTime = 0;
    private int frameCount = 0;
    
    private long noiselessCounter = 0;
    private User sourceUser = null;
    
    public Queue<byte[]> receivedFrames = new LinkedTransferQueue<byte[]>() ;
    
    private UDPSocket socket;
    private ComPortEmulatorReceiveHandler receiveHndl; 
     
    
    /**
     * @param source what user, given the list from Lasse, is this application
     * @param receiveHandler code to run when a package is received
    */
    public ComPortEmulator(User source, ComPortEmulatorReceiveHandler receiveHandler){
        this(source, receiveHandler, UDPSocket.DEFAULT_PORT, UDPSocket.DEFAULT_BUFFER_SIZE, DEFAULT_BAUD, DEFAUL_NOISE_EXPENTANCY);
    }
    
    /**
     * @param source what user, given the list from Lasse, is this application
     * @param receiveHandler code to run when a package is received
     * @param port on which this socket will bind to
     * @param bufferSize used for receive/send packages
     * @see User
     */
    public ComPortEmulator(User source, ComPortEmulatorReceiveHandler receiveHandler, int port, int bufferSize, long baud , int noiseExpectancy ){
        socket = new UDPSocket(User.Colautti_Matias_Benjamin, (dr, udps) -> {
            handleMessage(dr.getData(), dr.getSource());
        }, port, bufferSize);
        this.baud = baud;
	this.receiveHndl = receiveHandler;
        this.noiseExpectancy = (noiseExpectancy > 0) && (noiseExpectancy < 101) ? noiseExpectancy : 1;
        
	Thread emulator = new Thread(() -> {
            emulate();
	});
	emulator.setDaemon(true);
	emulator.start();
    }


    private void emulate() {
	while (true) {
            if (receivedFrames.size() > 0){
                byte[] frame = receivedFrames.poll();
                noiselessCounter ++;
                if (noiselessCounter % (100/noiseExpectancy) == 0) { // the noise
                    Random rand = new Random();
                    byte[] b = new byte[frame.length];
                    rand.nextBytes(b);
                    throttledSendFrame(b);
                }
                else{
                    throttledSendFrame(frame);
                }
            }
            else{
                Random rand = new Random();
                    byte[] b = new byte[3];
                rand.nextBytes(b);
                throttledSendFrame(b);
                try {
                    // Saving some CPU
                    Thread.sleep((long) (200 * rand.nextDouble()));
                } catch (InterruptedException ex) {
                    Logger.getLogger(ComPortEmulator.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
   	}
    }
    
    private void throttledSendFrame(byte[] frame){
        InputStream is = new ByteArrayInputStream(frame);
        InputStream stream = new ThrottledInputStream(is, 1200); //1200 bytes/s == 9600 bits/s
        try {
            for(int readByte = stream.read(); readByte != -1 ;readByte=stream.read()){
                if(++frameCount/1000 > 1 && startCountTime > 0) calibrateThrottler(System.nanoTime());
                receiveHndl.onReceive((byte) readByte);                
                startCountTime = System.nanoTime();
            }
        } catch (IOException ex) {
            Logger.getLogger(ComPortEmulator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void calibrateThrottler(long endedCounterTime){
        long currentBaud = NANOS_PER_SECOND /  (endedCounterTime - startCountTime + 1);
        System.err.println("BAUD: " + String.valueOf(currentBaud));
        if (currentBaud < baud/8){
            if(currentBaud < lastTansferRate)
                ThrottledInputStream.regulator += (lastTansferRate - currentBaud) * 200;
            else
                ThrottledInputStream.regulator += 400;
        }
        else{
            if(currentBaud > lastTansferRate && lastTansferRate != 0)
                ThrottledInputStream.regulator -= (currentBaud - lastTansferRate) * 200;
            else
                ThrottledInputStream.regulator -= 400;
        }
        // cleanup
        frameCount = 0;
        lastTansferRate = currentBaud;
        return;
    }
       
    private void handleMessage(byte[] data, User user){
        sourceUser = user;
        receivedFrames.add(data);
    }
    
    public void sendMsg(User user, byte[] data) throws UnknownHostException {
        socket.send(user, InetAddress.getByName("127.0.0.1"), UDPSocket.DEFAULT_PORT, data);
    }
}
