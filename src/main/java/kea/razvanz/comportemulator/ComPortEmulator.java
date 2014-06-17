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
    private User fromUser = null;
    
    ThrottledInputStream stream = null;
    
    public Queue<byte[]> receivedFrames = new LinkedTransferQueue<byte[]>() ;
    
    private UDPSocket socket;
    private ComPortEmulatorReceiveHandler receiveHndl; 
     
    
    /**Constructor method.
     * @param source what user, given the list from Lasse, is this application
     * @param receiveHandler code to run when a package is received
    */
    public ComPortEmulator(User source, ComPortEmulatorReceiveHandler receiveHandler){
        this(source, receiveHandler, UDPSocket.DEFAULT_PORT, UDPSocket.DEFAULT_BUFFER_SIZE, DEFAULT_BAUD, DEFAUL_NOISE_EXPENTANCY);
    }
    
    /**Constructor method.
     * @param source what user, given the list from Lasse, is this application
     * @param receiveHandler code to run when a package is received
     * @param port on which this socket will bind to
     * @param bufferSize used for receive/send packages
     * @param baud represents the amount of bits per second for the communication
     * @param noiseExpectancy represents the procentage at which the the frames are going to be altered to simulate the wire noise. >0 && <100
     * @see User
     */
    public ComPortEmulator(User source, ComPortEmulatorReceiveHandler receiveHandler, int port, int bufferSize, long baud , int noiseExpectancy ){
        socket = new UDPSocket(source, (dr, udps) -> {
            handleMessage(dr.getData(), dr.getSource());
        }, port, bufferSize);
        this.baud = baud;
	this.receiveHndl = receiveHandler;
        this.noiseExpectancy = (noiseExpectancy > 0) && (noiseExpectancy < 101) ? noiseExpectancy : 1;
        
	Thread emulator = new Thread(() -> {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            emulate();
	});
	emulator.setDaemon(true);
	emulator.start();
    }


    private void emulate() {
	while (true) {
            if (receivedFrames.size() > 0){
                if (noiselessCounter++ % (100/noiseExpectancy) == 0) { // the noise
                    Random rand = new Random();
                    byte[] b = new byte[receivedFrames.poll().length];
                    rand.nextBytes(b);
                    throttledSendFrame(b);
                }
                else{
                    byte[] frame = receivedFrames.poll();
                    throttledSendFrame(frame);
                }
            }
            else{
                try {
                    Thread.sleep((long) (50));

//                    Random rand = new Random();
//                    byte[] b = new byte[3];
//                    rand.nextBytes(b);
//                    throttledSendFrame(b);
//                    long lastNoiseTimestamp = System.currentTimeMillis();
//                    while(!(receivedFrames.size() > 0) && (System.currentTimeMillis() - lastNoiseTimestamp) < 3000){
//                        Thread.sleep((long) (20 * rand.nextDouble()));
//                    }
                } catch (InterruptedException ex) {
                    Logger.getLogger(ComPortEmulator.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
   	}
    }
    
    private void throttledSendFrame(byte[] frame){
        stream = new ThrottledInputStream(new ByteArrayInputStream(frame), baud/8); //1200 bytes/s == 9600 bits/s
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
        System.err.println("BpS speed: " + String.valueOf(currentBaud * 8));
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
        // reset
        frameCount = 0;
        lastTansferRate = currentBaud;
    }
       
    private void handleMessage(byte[] data, User user){
        receivedFrames.add(data);
    }
    
    /**Method used to send a frame
     * 
     * @param user the receiver or the frame
     * @param destination the receiver IP address
     * @param data the frame to be send
     * @param portNumber the receiver port number
     * @throws UnknownHostException if the user specified is not found in the available users library;
     */
    public void sendFrame(User user, String destination, int portNumber,byte[] data) throws UnknownHostException {
        System.out.println(InetAddress.getByName(destination));
        socket.send(user, InetAddress.getByName(destination), portNumber, data);
    }
}
