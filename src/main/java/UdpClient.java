import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;


public class UdpClient {
    private static String local_filepath;
    private static String remote_filepath;
    private static int port;
    private static InetAddress ip;
    private static DatagramSocket socket = null;
    private static Closeable closeable = null;
    public UdpClient(String local, String remote, InetAddress ip, int port) {
        UdpClient.local_filepath = format_filepath(local) ;
//        UdpClient.remote_filepath = format_filepath(remote);
        UdpClient.remote_filepath = remote;
        UdpClient.ip = ip;
        UdpClient.port = port;
        System.out.println("local " + local_filepath);
        System.out.println("remote " + remote_filepath);
    }

    private static String format_filepath(String filepath) {
        String output = filepath;
        output = output.replace('\\', '/');
        if (!output.startsWith("./")) output = "./" + output;
        return output;
    }



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

    public static DatagramPacket generate_read_packet() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(decode_int_to_unsigned_bytes(1));
        stream.write((remote_filepath + "\0").getBytes(StandardCharsets.US_ASCII));
        byte[] packet_data = stream.toByteArray();
        return new DatagramPacket(packet_data, packet_data.length, ip,port);
    }

    public static DatagramPacket generate_write_packet() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(decode_int_to_unsigned_bytes(2));
        stream.write((remote_filepath + "\0").getBytes(StandardCharsets.US_ASCII));
        stream.write(("octet\0").getBytes(StandardCharsets.US_ASCII));
        byte[] packet_data = stream.toByteArray();
        return new DatagramPacket(packet_data, packet_data.length, ip,port);
    }

    public static DatagramPacket generate_ack_packet(int block_num) throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(decode_int_to_unsigned_bytes(4));
        stream.write(decode_int_to_unsigned_bytes(block_num));
        byte[] packet_data = stream.toByteArray();
        return new DatagramPacket(packet_data, packet_data.length, ip,port);
    }
    public static DatagramPacket generate_data_packet(int block_num, byte[] data) throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(decode_int_to_unsigned_bytes(3));
        stream.write(decode_int_to_unsigned_bytes(block_num));
        stream.write(data);
        byte[] packet_data = stream.toByteArray();
        return new DatagramPacket(packet_data, packet_data.length, ip,port);
    }

    public DatagramPacket send_data_packet(int block_num, byte[] data) throws Exception {
        DatagramPacket packet = generate_data_packet(block_num, data);
        socket.send(packet);
        return packet;
    }

    public DatagramPacket send_ack_packet(int block_num) throws Exception {
        DatagramPacket packet = generate_ack_packet(block_num);
        socket.send(packet);
        return packet;
    }



    public DatagramPacket send_read_packet() throws Exception {
        DatagramPacket packet = generate_read_packet();
        socket.send(packet);
        return packet;
    }


    public static DatagramPacket send_write_packet() throws  Exception {
        DatagramPacket packet = generate_write_packet();
        socket.send(packet);
        return packet;
    }

    public static int check_packet_for_ack(DatagramPacket ack_packet, int block_num) {
        byte[] ack_packet_buffer = ack_packet.getData();
        if (!ack_packet.getAddress().equals(ip)) {
            System.out.println("ip mismatch - packet dropped block " + block_num);
            return 0;
        }

        if (block_num == 0) {
            if (ack_packet.getPort() == port) {
                System.out.println("port mismatch - packet dropped block 0");
                return 0;
            }

            if (decode_code(ack_packet_buffer, false) == 5) {
                terminate("directory in the path not found - terminating");
            }

            if (decode_code(ack_packet_buffer, false) != 4) {
                terminate("opcode mismatch - terminating");
            }

            if (decode_code(ack_packet_buffer, true) != block_num) {
                terminate("block mismatch - terminating");
            }

            port = ack_packet.getPort();
            return 1;
        }


        if (ack_packet.getPort() != port) {
            System.out.println("port mismatch - packed dropped");
            return 0;
        }


        if (decode_code(ack_packet_buffer, true) == block_num - 1) {
            System.out.println("mismatch block - retransmitting");
            return 2;
        }
        return 1;
    }


    public void read() throws Exception {
        System.out.println("read initiated");
        if (!validate_local_path(local_filepath)) terminate("local filepath not valid");
        socket = new DatagramSocket();
        socket.setSoTimeout(500);

        DatagramPacket packet = this.send_read_packet();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        closeable = stream;
        System.out.println("Receiving file:  " + remote_filepath);

        boolean stay = true;
        int block_num = 1;
        while (stay) {
            DatagramPacket data_packet = accept_data_packet(packet, block_num);
            int length = data_packet.getLength() - 4;

            System.out.println("_______________________________________________________");
            String log = "block " + block_num + " received from " + data_packet.getAddress().toString().substring(1) + ":" + data_packet.getPort();
            System.out.println(log);


            if (length < 512) {
                stay = false;
                System.out.println("Hit last block");
            }

            System.out.println("data size: " + length);
            stream.write(data_packet.getData(), 4, length);

            packet = send_ack_packet(block_num);
            System.out.println("ack sent");
            block_num++;
        }

        byte[] file_bytes = stream.toByteArray();

        FileOutputStream fos = new FileOutputStream(local_filepath);
        fos.write(file_bytes);
        fos.close();

        System.out.println("Read request completed.");
        socket.close();
        stream.close();
    }

    @SuppressWarnings("unused")
    public void write() throws Exception {
        System.out.println("write initiated");
        File f = new File(local_filepath);
        if (!f.exists() || f.isDirectory()) terminate("local filepath is absent or a directory");
        System.out.println("Sending file:  " + local_filepath);
        socket = new DatagramSocket();



        byte[] file_bytes = Files.readAllBytes(f.toPath());

        //create a stream to read maximum of 512 bytes at a time
        ByteArrayInputStream inputStream = new ByteArrayInputStream(file_bytes);
        closeable = inputStream;

        socket.setSoTimeout(100);

        DatagramPacket packet = send_write_packet();

        int block_num = 0;
        boolean stay = true;
        while(stay) {
            accept_ack_packet(packet, block_num);

            System.out.println("_______________________________________________________");
            System.out.println("available bytes to read: " + inputStream.available());
            //exit condition
            //1 now there is no available bytes to be read
            //2 the available bytes is less than 512 which means this will be last packet
            // this will provide the last iteration of this loop

            int available = inputStream.available();
            if (available < 512) {
                System.out.println("hit last packet");
                stay = false;
            }

            int to_read = (stay) ? 512 : available;

            byte[] file_buffer = new byte[to_read];
            int result = inputStream.read(file_buffer, 0, to_read);

            block_num++;

            packet = this.send_data_packet(block_num, file_buffer);
            System.out.println("block " + block_num + " sent");

        }
        System.out.println("Write request completed");
        socket.close();
        inputStream.close();
    }


    public static void accept_ack_packet(DatagramPacket packet, int block_num) throws Exception {
        int count = 0;
        while(true) {
            if (count > 10) terminate("retransmitted too many times - terminating");

            byte[] ack_packet_buffer = new byte[4];
            DatagramPacket ack_packet = new DatagramPacket(ack_packet_buffer, ack_packet_buffer.length);

            try {
                socket.receive(ack_packet);
            } catch (SocketTimeoutException e ) {
                socket.send(packet);
                System.out.println("retransmitted data block : " + block_num);
                count++;
                continue;
            }


            int check = check_packet_for_ack(ack_packet, block_num);

            switch (check) {
                case 0: continue;
                case 2: {
                    count++;
                    socket.send(packet);
                    continue;
                }
            }

            return;
        }
    }


    public static Object[] check_input(String[] args) {
        if (args.length < 3) {
            System.err.println("At least 4 arguments have to be passed.");
            System.exit(1);
        } else if (args.length > 5) {
            System.err.println("No more than 5 arguments can to be passed.");
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
             ip = InetAddress.getByName(args[3]);
        } catch (UnknownHostException e) {
            System.err.println("Invalid ip");
            System.exit(1);
        }


        int port;
        if (args.length == 3) {
            port = 69;
        } else {
            port = -1;
            try {
                port = Integer.parseInt(args[4]);
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
        return new Object[] {opcode, args[1], args[2] ,ip, port};
    }

    private static boolean validate_local_path(String filepath) {
        int index = filepath.substring(2).lastIndexOf('/');
        if (index < 0) return true;
        String directory_path = filepath.substring(0, index);
        File directory = new File(directory_path);
        return directory.exists() && directory.isDirectory();
    }



    public static void main(String[] args) throws Exception {
        System.out.println(System.getProperty("user.dir"));
        Object[] target =  check_input(args);
        int opcode = (int) target[0];
        String local = (String) target[1];
        String remote = (String) target[2];
        InetAddress ip = (InetAddress) target[3];
        int port = (int) target[4];

        System.out.println("code: " + opcode);
        System.out.println("ip: " + ip.toString().substring(1));
        System.out.println("port: " + port);


        UdpClient server = new UdpClient(local, remote, ip, port);

        long startTime = System.currentTimeMillis();


        if (opcode == 1) server.read();
        if (opcode == 2) server.write();

        long endTime = System.currentTimeMillis();
        double duration = (endTime - startTime) / 1000.0;
        System.out.println("ran in " + duration);





    }

    public static void terminate(String message) {
        System.err.println(message);
        try {
            if (socket != null)  socket.close();
            if (closeable != null) closeable.close();
        } catch (IOException e) {
            System.err.println("failed to close session resource");
        }
        System.exit(1);
    }


    public static DatagramPacket accept_data_packet(DatagramPacket packet, int block_num) throws Exception {
        int count = 0;
        while (true) {
            if (count > 10) terminate("retransmitted too many times - terminating");
            byte[] data_packet_buffer = new byte[516];
            DatagramPacket data_packet = new DatagramPacket(data_packet_buffer, data_packet_buffer.length);

            try {
                socket.receive(data_packet);
            } catch (SocketTimeoutException e) {
                if (block_num != 1) terminate("server not responding - terminating session");
                socket.send(packet);
                count++;
                continue;
            }


             switch (check_packet_for_data(data_packet, block_num)) {
                 case 0: continue;
                 case 2: {
                     count++;
                     socket.send(packet);
                     continue;
                 }
             }

            return data_packet;
        }
    }

    public static int check_packet_for_data(DatagramPacket packet, int block_num) {
        if (!packet.getAddress().equals(ip)) {
            System.out.println("ip mismatch - packet dropped block " + block_num);
            return 0;
        }

        if (block_num == 1) {
            if (packet.getPort() == port) {
                System.out.println("port mismatch - packet dropped block 1");
                return 0;
            }
            if(decode_code(packet.getData(), false) == 5) {
                terminate("file not found - terminating");
            }

            if(decode_code(packet.getData(), false) != 3) {
                System.out.println("opcode mismatch - packet dropped block 1");
                return 0;
            }

            if(decode_code(packet.getData(), true) != 1) {
                terminate("block mismatch for the first block - terminating");
            }

            port = packet.getPort();
            return 1;
        }

        if (packet.getPort() != port) {
            System.out.println("port mismatch - packet dropped");
            return 0;
        }

        if (decode_code(packet.getData(), false) != 3) {
            terminate("wrong opcode - terminating");
        }
        if (decode_code(packet.getData(), true) != block_num && decode_code(packet.getData(), true) != block_num - 1) {
            terminate("wrong block - terminating");
        }

        if (decode_code(packet.getData(), true) == block_num - 1) {
            System.out.println("wrong block - retransmitting");
            return 2;
        }

        return 1;
    }


}
