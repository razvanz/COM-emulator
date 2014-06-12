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
import kea.razvanz.comportemulator.util.ThrottledInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.LinkedTransferQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import kea.zmirc.systemintegration.p1.shared.UDPSocket;
import kea.zmirc.systemintegration.p1.shared.util.User;

/**
 *
 * @author razvanz
 */
public class ComPortEmulator {
    
    public static int BITRATE = 9600;
    public static int NOISE_EXPENTANCY = 0;
    
    private long counter = 0;
    private User sourceUser = null;
    
    public Queue<byte[]> receivedFrames = new LinkedTransferQueue<byte[]>() ;
    
    private UDPSocket socket;
    private ComPortEmulatorReceiveHandler receiveHndl; 
     
    
    /**
     * @param source what user, given the list from Lasse, is this application
     * @param receiveHandler code to run when a package is received
    */
    public ComPortEmulator(User source, ComPortEmulatorReceiveHandler receiveHandler){
        this(source, receiveHandler, UDPSocket.DEFAULT_PORT, UDPSocket.DEFAULT_BUFFER_SIZE);
    }
    
    /**
     * @param source what user, given the list from Lasse, is this application
     * @param receiveHandler code to run when a package is received
     * @param port on which this socket will bind to
     * @param bufferSize used for receive/send packages
     * @see User
     */
    public ComPortEmulator(User source, ComPortEmulatorReceiveHandler receiveHandler, int port, int bufferSize ){
        socket = new UDPSocket(User.Colautti_Matias_Benjamin, (dr, udps) -> {
            handleMessage(dr.getData(), dr.getSource());
        }, port, bufferSize);
        
	this.receiveHndl = receiveHandler;
        
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
                counter ++;
                if (counter % (100/NOISE_EXPENTANCY) == 0) { // the noise
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
                byte[] b = new byte[1];
                rand.nextBytes(b);
                throttledSendFrame(b);
            }
            
   	}
    }
    
    public void throttledSendFrame(byte[] frame){
        InputStream is = new ByteArrayInputStream(frame);
        InputStream stream = new ThrottledInputStream(is, 1200); //1200 bytes/s == 9600 bits/s
        try {
            for(int readByte = stream.read(); readByte != -1 ;readByte=stream.read()){
                receiveHndl.onReceive((byte) readByte);
            }
        } catch (IOException ex) {
            Logger.getLogger(ComPortEmulator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
       
    public void handleMessage(byte[] data, User user){
        sourceUser = user;
        receivedFrames.add(data);
    }
}
