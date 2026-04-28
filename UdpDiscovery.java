import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UdpDiscovery implements Runnable {
    @Override
    public void run() {
        try (DatagramSocket descSock = new DatagramSocket(50000)) {
            System.out.println("Server UDP in ascolto per discovery sulla porta 50000...");
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                descSock.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength()).trim();

                if ("BUSCANDO_SERVIDOR".equals(message)) {
                    byte[] response = "AQUI_ESTOY".getBytes();
                    DatagramPacket responsePacket = new DatagramPacket(
                            response, response.length,
                            packet.getAddress(), packet.getPort()
                    );
                    descSock.send(responsePacket);
                }
            }
        } catch (Exception e) {
            System.err.println("Errore in UDP Discovery: " + e.getMessage());
        }
    }
}