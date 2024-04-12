import java.net.DatagramSocket;

public class UdpServer {
    private final DatagramSocket socket;

    public static byte[] decode_short_to_unsigned_bytes(int num) {
        int unsigned16Bit = num & 0xFFFF;
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((unsigned16Bit >> 8) & 0xFF);
        bytes[1] = (byte) (unsigned16Bit & 0xFF);
        return bytes;
    }

    public static int decode_code(byte[] packet, boolean block_num) {
        int first;
        int second;

        if (block_num) {
            first = 2;
            second = 3;
        } else {
            first = 0;
            second = 1;
        }

        //bitwise operation to appropriately parse unsigned 16 bit binary int to signed int
        return ((packet[first] & 0xFF) << 8) | (packet[second] & 0xFF);
    }

    public void read() {
        ;
    }

    public void write() {
        ;
    }


    public UdpServer() {
        return;
    }

    public static void main(String[] args) throws Exception {
        ;
    }
}
