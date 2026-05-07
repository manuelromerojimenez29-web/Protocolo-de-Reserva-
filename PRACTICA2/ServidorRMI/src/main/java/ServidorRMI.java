
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;


/**
 *
 * @author MSI
 */


public class ServidorRMI {

    public static void main(String[] args) {
        try {
            
            System.setProperty("java.rmi.server.hostname", "192.168.158.164");
            
            ServicioReservas servicio = new ServicioReservasImpl();

            Registry registro = LocateRegistry.createRegistry(1099);

            registro.rebind("ServicioReservas", servicio);

            System.out.println("Servidor RMI listo.");
            System.out.println("Servicio registrado como: ServicioReservas");
            System.out.println("Puerto RMI: 1099");

        } catch (Exception e) {
            System.out.println("Error al iniciar el servidor RMI:");
            e.printStackTrace();
        }
    }
}