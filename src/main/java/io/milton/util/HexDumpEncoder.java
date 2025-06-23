package io.milton.util;

import java.io.*;

public class HexDumpEncoder {

    private int offset;
    private int thisLineLength;
    private int currentByte;
    private final byte[] thisLine = new byte[16];

    static void hexDigit(PrintStream p, byte x) {
        char c;

        c = (char) ((x >> 4) & 0xf);
        if (c > 9)
            c = (char) ((c - 10) + 'A');
        else
            c = (char) (c + '0');
        p.write(c);
        c = (char) (x & 0xf);
        if (c > 9)
            c = (char) ((c - 10) + 'A');
        else
            c = (char) (c + '0');
        p.write(c);
    }

    protected int bytesPerAtom() {
        return (1);
    }

    protected int bytesPerLine() {
        return (16);
    }

    protected void encodeBufferPrefix(OutputStream o) throws IOException {
        offset = 0;
        pStream = new PrintStream(o);
    }

    protected void encodeLinePrefix(OutputStream o, int len) {
        hexDigit(pStream, (byte) ((offset >>> 8) & 0xff));
        hexDigit(pStream, (byte) (offset & 0xff));
        pStream.print(": ");
        currentByte = 0;
        thisLineLength = len;
    }

    protected void encodeAtom(OutputStream o, byte[] buf, int off, int len)
            throws IOException {
        thisLine[currentByte] = buf[off];
        hexDigit(pStream, buf[off]);
        pStream.print(" ");
        currentByte++;
        if (currentByte == 8)
            pStream.print("  ");
    }

    protected void encodeLineSuffix(OutputStream o) throws IOException {
        if (thisLineLength < 16) {
            for (int i = thisLineLength; i < 16; i++) {
                pStream.print("   ");
                if (i == 7)
                    pStream.print("  ");
            }
        }
        pStream.print(" ");
        for (int i = 0; i < thisLineLength; i++) {
            if ((thisLine[i] < ' ') || (thisLine[i] > 'z')) {
                pStream.print(".");
            } else {
                pStream.write(thisLine[i]);
            }
        }
        pStream.println();
        offset += thisLineLength;
    }

    /**
     * Stream that understands "printing"
     */
    protected PrintStream pStream;

    /**
     * This method works around the bizarre semantics of BufferedInputStream's
     * read method.
     */
    protected int readFully(InputStream in, byte[] buffer)
            throws java.io.IOException {
        for (int i = 0; i < buffer.length; i++) {
            int q = in.read();
            if (q == -1)
                return i;
            buffer[i] = (byte) q;
        }
        return buffer.length;
    }

    /**
     * Encode bytes from the input stream, and write them as text characters
     * to the output stream. This method will run until it exhausts the
     * input stream. It differs from encode in that it will add the
     * line at the end of a final line that is shorter than bytesPerLine().
     */
    public void encodeBuffer(InputStream inStream, OutputStream outStream)
            throws IOException {
        int j;
        int numBytes;
        byte[] tmpbuffer = new byte[bytesPerLine()];

        encodeBufferPrefix(outStream);

        while (true) {
            numBytes = readFully(inStream, tmpbuffer);
            if (numBytes == 0) {
                break;
            }
            encodeLinePrefix(outStream, numBytes);
            for (j = 0; j < numBytes; j += bytesPerAtom()) {
                if ((j + bytesPerAtom()) <= numBytes) {
                    encodeAtom(outStream, tmpbuffer, j, bytesPerAtom());
                } else {
                    encodeAtom(outStream, tmpbuffer, j, (numBytes) - j);
                }
            }
            encodeLineSuffix(outStream);
            if (numBytes < bytesPerLine()) {
                break;
            }
        }
    }

}
