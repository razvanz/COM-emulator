/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package kea.razvanz.comportemulator.util;

/**
 *
 * @author razvanz
 */
import java.io.IOException;
import java.io.InputStream;

/**
* The ThrottleInputStream provides bandwidth throttling on a specified
* InputStream. It is implemented as a wrapper on top of another InputStream
* instance.
* The throttling works by examining the number of bytes read from the underlying
* InputStream from the beginning, and sleep()ing for a time interval if
* the byte-transfer is found exceed the specified tolerable maximum.
* (Thus, while the read-rate might exceed the maximum for a given short interval,
* the average tends towards the specified maximum, overall.)
*/
public class ThrottledInputStream extends InputStream {

  private final InputStream rawStream;
  private final long maxBytesPerSec ;
  public static final long NANOS_PER_SECOND = 1000000000;
  public static long regulator = 1000;

  private long startTime = 0;
  


  public ThrottledInputStream(InputStream rawStream) {
    this(rawStream, Long.MAX_VALUE);
  }

  public ThrottledInputStream(InputStream rawStream, long maxBytesPerSec) {
    assert maxBytesPerSec > 0 : "Bandwidth " + maxBytesPerSec + " is invalid";
    this.rawStream = rawStream;
    this.maxBytesPerSec = maxBytesPerSec;
    
  }
  
  /** @inheritDoc */
  @Override
  public int read() throws IOException {
    startTime = System.nanoTime();
    int data = rawStream.read();
    if (data != -1) throttle();

    return data;
  }

  /** @inheritDoc */
  @Override
  public int read(byte[] b) throws IOException {
    // untested functionality
    int bufferSize = b.length;
    if(bufferSize == 0) return 0;
    int readByte = 0, readLen;
    for(readLen = 0; readLen < bufferSize; readLen ++ ){
        readByte = this.read();
        if(readByte == -1) break;
        b[readLen] = (byte) readByte;
    }
    return readLen - 1;
  }

  /** @inheritDoc */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    // untested functionality
    int bufferSize = b.length;
    if(bufferSize == 0 || len == 0 || off > bufferSize || (off + len) > bufferSize) return 0;
    int readByte = 0, readLen = 0;
    for(int i = 0; i < bufferSize && readLen < len; i ++ ){
        readByte = this.read();
        if(readByte == -1) break;
        if (i >= off){
            b[readLen] = (byte) readByte;
            readLen++;
        }
    }
    return readLen;
  }

  private void throttle() throws IOException {
    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
    long expectedDelivery = startTime +  NANOS_PER_SECOND / maxBytesPerSec - regulator;
    long end = 0;
    do{
        end = System.nanoTime();
    }while(expectedDelivery > end);
  }


  /** @inheritDoc */
  @Override
  public String toString() {
    return "ThrottledInputStream{" +
        ", maxBytesPerSec=" + maxBytesPerSec +
        '}';
  }
}