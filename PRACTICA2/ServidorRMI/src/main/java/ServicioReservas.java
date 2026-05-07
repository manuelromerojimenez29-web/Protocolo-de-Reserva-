
import java.rmi.Remote;
import java.rmi.RemoteException;


/**
 *
 * @author MSI
 */


public interface ServicioReservas extends Remote {

    RespuestaReserva listarDisponibilidad(int timestampSesion) throws RemoteException;

    RespuestaReserva reservarAsiento(int timestampSesion, int asientoId) throws RemoteException;

    RespuestaReserva confirmarReserva(int idReserva) throws RemoteException;

    RespuestaReserva cancelarReserva(int idReserva) throws RemoteException;
}
    

