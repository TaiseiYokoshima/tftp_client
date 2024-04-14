import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class UdpClient {
    public static byte[] decode_int_to_unsigned_bytes(int num) {
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

    public static DatagramPacket generate_read_packet(InetAddress ip, int port, String filepath) throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(decode_int_to_unsigned_bytes(1));
        stream.write((filepath + "\0").getBytes(StandardCharsets.US_ASCII));
        byte[] packet_data = stream.toByteArray();
        return new DatagramPacket(packet_data, packet_data.length, ip,port);





    }

    public void read(String[] args) throws Exception {
        System.out.println("read initiated");

        File f = new File(args[3]);

        if (!f.exists() || f.isDirectory()) {
            System.err.println("File doesn't exit or file is a directory");
            System.exit(1);
        }

        DatagramSocket socket = new DatagramSocket();










    }

    public void write(String[] args) {
        System.out.println("write initiated");
    }



    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            System.out.println(args[i]);
        }

        if (args.length < 3) {
            System.err.println("Provide the appropriate arguments");
            System.exit(1);
        }

        if (!args[0].toLowerCase().equals("get") && !args[0].toLowerCase().equals("put") ) {
            System.err.println("Unsupported request: Supported requests are get, put");
            System.exit(1);
        }

        UdpClient server = new UdpClient();

        if (args[0].toLowerCase().equals("get")) server.read(args);
        if (args[0].toLowerCase().equals("put")) server.write(args);



    }
}
