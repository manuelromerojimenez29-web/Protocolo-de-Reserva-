import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ReservaService extends Remote {
    int STATUS_OK = 0;
    int STATUS_ERR = 1;
    int STATUS_NONE = 2;

    // Ritorna un array [status, mask_o_id, timer] come faceva struct.pack
    int[] listAsientos(long timestamp) throws RemoteException;
    int[] bookAsiento(long timestamp, int asientoId) throws RemoteException;
    int[] confirmReserva(int idReserva) throws RemoteException;
    int[] cancelReserva(int idReserva) throws RemoteException;
}