
import java.io.Serializable;


/**
 *
 * @author MSI
 */


public class RespuestaReserva implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_OK = 0;
    public static final int STATUS_ERR = 1;
    public static final int STATUS_NONE = 2;

    private final int estado;
    private final int parametro1;
    private final int parametro2;
    private final String mensaje;

    public RespuestaReserva(int estado, int parametro1, int parametro2, String mensaje) {
        this.estado = estado;
        this.parametro1 = parametro1;
        this.parametro2 = parametro2;
        this.mensaje = mensaje;
    }

    public int getEstado() {
        return estado;
    }

    public int getParametro1() {
        return parametro1;
    }

    public int getParametro2() {
        return parametro2;
    }

    public String getMensaje() {
        return mensaje;
    }
}