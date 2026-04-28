import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Server {
    public static void main(String[] args) {
        try {
            // 1. Avvia il thread per l'auto-scoperta UDP
            Thread udpThread = new Thread(new UdpDiscovery());
            udpThread.setDaemon(true);
            udpThread.start();

            // 2. Crea e avvia il servizio RMI
            ReservaService service = new ReservaServiceImpl();

            // Crea il registry RMI sulla porta standard 1099
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("ReservaService", service);

            System.out.println("Server RMI avviato. In attesa di chiamate...");

            // Mantieni il server in esecuzione
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Errore nell'avvio del server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}