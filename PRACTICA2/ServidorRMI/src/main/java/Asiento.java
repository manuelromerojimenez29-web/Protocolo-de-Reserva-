
import java.util.concurrent.ScheduledFuture;


/**
 *
 * @author MSI
 */


public class Asiento {

    public static final String LIBRE = "LIBRE";
    public static final String BLOQUEADO = "BLOQUEADO";
    public static final String CONFIRMADO = "CONFIRMADO";

    private String estado;
    private int idReserva;
    private ScheduledFuture<?> tareaExpiracion;

    public Asiento() {
        this.estado = LIBRE;
        this.idReserva = 0;
        this.tareaExpiracion = null;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public int getIdReserva() {
        return idReserva;
    }

    public void setIdReserva(int idReserva) {
        this.idReserva = idReserva;
    }

    public ScheduledFuture<?> getTareaExpiracion() {
        return tareaExpiracion;
    }

    public void setTareaExpiracion(ScheduledFuture<?> tareaExpiracion) {
        this.tareaExpiracion = tareaExpiracion;
    }
}
