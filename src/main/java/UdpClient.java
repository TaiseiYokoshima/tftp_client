import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.*;
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

    public DatagramPacket generate_ack_packet(int block_num, InetAddress ip, int port) throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(decode_int_to_unsigned_bytes(4));
        stream.write(decode_int_to_unsigned_bytes(block_num));
        byte[] packet_data = stream.toByteArray();
        return new DatagramPacket(packet_data, packet_data.length, ip,port);
    }

    public DatagramPacket send_ack_packet(DatagramSocket socket, int block_num, InetAddress ip, int port) throws Exception {
        DatagramPacket packet = this.generate_ack_packet(block_num, ip, port);
        socket.send(packet);
        return packet;
    }



    public DatagramPacket send_read_packet(DatagramSocket socket, InetAddress ip, int port, String filepath) throws Exception {
        DatagramPacket packet = generate_read_packet(ip, port, filepath);
        socket.send(packet);
        return packet;
    }

    public Object[] check_packet_for_data(DatagramSocket session_socket, DatagramPacket packet, DatagramPacket last_packet, InetAddress ip, int port, int block_num, int count) throws Exception{
        if ( block_num == 1) {
            if ((!packet.getAddress().equals(ip) || packet.getPort() == port)) {
                System.out.println("ip or port mismatch - packet dropped block 1");
                return new Object[]{true, count};
            }
        } else {
            if ((!packet.getAddress().equals(ip) || packet.getPort() != port)) {
                System.out.println("ip or port mismatch - packet dropped");
                return new Object[]{true, count};
            }
        }


        if (decode_code(packet.getData(), false) != 3) {
            System.out.println("wrong opcode - packet dropped");
            return new Object[]{true, count};
        }
        if (decode_code(packet.getData(), true) != block_num) {
            System.out.printf("wrong block [%d] - packet dropped %n", decode_code(packet.getData(), true));
            session_socket.send(last_packet);
            return new Object[]{true, count + 1};
        }

        return new Object[]{false, count};
    }



    public void read(InetAddress ip, int port, String filepath) throws Exception {
        System.out.println("read initiated");
        DatagramSocket socket = new DatagramSocket();
        DatagramPacket packet = this.send_read_packet(socket, ip, port, filepath);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        System.out.println("Receiving file:  " + filepath);


        socket.setSoTimeout(1000);

        boolean stay = true;
        int block_num = 1;
        int count = 1;
        while (stay) {
            if (count > 10) {
                System.err.println("Retransmitted too many times");
                socket.close();
                stream.close();
                return;
            }

            byte[] data_packet_buffer = new byte[516];
            DatagramPacket data_packet = new DatagramPacket(data_packet_buffer, data_packet_buffer.length);

            try {
                socket.receive(data_packet);
            } catch (SocketTimeoutException e) {
                System.err.println("Server not responding - terminating session");
                System.exit(0);
            }


            Object[] results = check_packet_for_data(socket, data_packet, packet, ip, port, block_num, count);
            boolean result = (boolean) results[0];
            count = (int) results[1];

            if (result) continue;

            port = data_packet.getPort();

            count = 1;

            int length = data_packet.getLength() - 4;

            System.out.println("_______________________________________________________");
            String log = "block " + block_num + " received from " + data_packet.getAddress().toString().substring(1) + ":" + data_packet.getPort();
            System.out.println(log);


            if (length < 512) {
                stay = false;
                System.out.println("Hit last block");
            }

            System.out.println("data size: " + length);
            stream.write(data_packet_buffer, 4, length);

            packet = this.send_ack_packet(socket, block_num, ip, port);
            System.out.println("ack sent");

            block_num++;
        }

        byte[] file_bytes = stream.toByteArray();

        FileOutputStream fos = new FileOutputStream(filepath);
        fos.write(file_bytes);
        fos.close();

        System.out.println("Read request completed.");
        socket.close();
    }

    public void write(InetAddress ip, int port , String filepath) {
        System.out.println("write initiated");
    }



    public static Object[] check_input(String[] args) {
        if (args.length < 3) {
            System.err.println("At least 3 arguments have to be passed.");
            System.exit(1);
        } else if (args.length > 4) {
            System.err.println("No more than 4 arguments can to be passed.");
            System.exit(1);
        }



        int opcode = -1;

        if (!args[0].equalsIgnoreCase("get") && !args[0].equalsIgnoreCase("put")) {
            System.err.println("Request options are either get or put.");
            System.exit(1);
        } else if (args[0].equalsIgnoreCase("get")) {
            opcode = 1;
        }
        else if (args[0].equalsIgnoreCase("put")) {
            opcode = 2;

            File f = new File(args[1]);

            if (!f.exists() || f.isDirectory()) {
                System.err.println("File doesn't exist or is a directory");
                System.exit(1);
            }
        }


        InetAddress ip = null;
        try {
             ip = InetAddress.getByName(args[2]);
        } catch (UnknownHostException e) {
            System.err.println("Invalid ip");
            System.exit(1);
        }

        int port;

        if (args.length < 4) {
            port = 69;
        } else {
            port = -1;
            try {
                port = Integer.parseInt(args[3]);
                if (port > 65535) {
                    System.err.println("Port number should be from 0 to 65535");
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                System.err.println("Port number is not valid.");
                System.exit(1);
            }
        }



        if (port < 0 || ip == null || opcode < 0) throw new RuntimeException("Error caused from parsed arguments");
        return new Object[] {opcode, args[1], ip, port};
    }



    public static void main(String[] args) throws Exception {
        Object[] target =  check_input(args);
        int opcode = (int) target[0];
        String filepath = (String) target[1];
        InetAddress ip = (InetAddress) target[2];
        int port = (int) target[3];


        UdpClient server = new UdpClient();

        if (opcode == 1) server.read(ip, port, filepath);
        if (opcode == 2) server.write(ip, port, filepath);



    }
}
