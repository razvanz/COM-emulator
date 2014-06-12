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
  public static final double NANOS_PER_SECOND = 1000000000;
  private static final long SLEEP_DURATION_NS =750000;
  public static final double SMOOTHNESS = .80;

  private int prevSpeed = 0;
  private long startTime = System.nanoTime();
  private long endTime = 0;

  public ThrottledInputStream(InputStream rawStream) {
    this(rawStream, Long.MAX_VALUE);
  }

  public ThrottledInputStream(InputStream rawStream, long maxBytesPerSec) {
    assert maxBytesPerSec > 0 : "Bandwidth " + maxBytesPerSec + " is invalid";
    this.rawStream = rawStream;
    this.maxBytesPerSec = maxBytesPerSec;
  }
  
  /**
* Getter for the read-rate from this stream, since creation.
* @return Read rate, in bytes/sec.
*/
  public int getBytesPerSec()
    {
        long delta = endTime - startTime;
        if (prevSpeed == 0 || delta < 0)
                prevSpeed = (int) maxBytesPerSec + 1; // throttle for buffer of size 1
        else
                prevSpeed = (int) ( (1 / delta) * SMOOTHNESS + (1 - SMOOTHNESS) * prevSpeed );
        return prevSpeed;
    }

  /** @inheritDoc */
  @Override
  public int read() throws IOException {
    throttle();
    int data = rawStream.read();
    endTime = System.nanoTime();
    return data;
  }

  /** @inheritDoc */
  @Override
  public int read(byte[] b) throws IOException {
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
    if (getBytesPerSec() > maxBytesPerSec) {
        startTime = System.nanoTime();
        long end = 0;
        do{
            end = System.nanoTime();
        }while(startTime + SLEEP_DURATION_NS > end);
    }
  }


  /** @inheritDoc */
  @Override
  public String toString() {
    return "ThrottledInputStream{" +
        ", maxBytesPerSec=" + maxBytesPerSec +
        ", bytesPerSec=" + getBytesPerSec() +
        '}';
  }
}