
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 *
 * @author MSI
 */


public class ServicioReservasImpl extends UnicastRemoteObject implements ServicioReservas {

    private static final long serialVersionUID = 1L;

    private static final int TIEMPO_EXPIRACION = 60;

    private final Map<Integer, Map<Integer, Asiento>> inventario;
    private final Map<Integer, int[]> reservasActivas;
    private final ScheduledExecutorService scheduler;
    private final Random random;

    public ServicioReservasImpl() throws RemoteException {
        super();
        this.inventario = new HashMap<>();
        this.reservasActivas = new HashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(4);
        this.random = new Random();
    }

    private void inicializarSesion(int timestampSesion) {
        if (!inventario.containsKey(timestampSesion)) {
            Map<Integer, Asiento> sesion = new HashMap<>();

            for (int i = 1; i <= 4; i++) {
                sesion.put(i, new Asiento());
            }

            inventario.put(timestampSesion, sesion);
            System.out.println("Sesión creada: " + timestampSesion);
        }
    }

    @Override
    public synchronized RespuestaReserva listarDisponibilidad(int timestampSesion) throws RemoteException {
        inicializarSesion(timestampSesion);

        Map<Integer, Asiento> sesion = inventario.get(timestampSesion);

        int mascaraDisponibles = 0;
        int asientosLibres = 0;

        for (int i = 1; i <= 4; i++) {
            Asiento asiento = sesion.get(i);

            if (Asiento.LIBRE.equals(asiento.getEstado())) {
                mascaraDisponibles |= (1 << i);
                asientosLibres++;
            }
        }

        if (asientosLibres == 0) {
            return new RespuestaReserva(
                    RespuestaReserva.STATUS_NONE,
                    0,
                    0,
                    "No hay asientos disponibles para esta sesión."
            );
        }

        return new RespuestaReserva(
                RespuestaReserva.STATUS_OK,
                mascaraDisponibles,
                0,
                "Disponibilidad consultada correctamente."
        );
    }

    @Override
    public synchronized RespuestaReserva reservarAsiento(int timestampSesion, int asientoId) throws RemoteException {
        inicializarSesion(timestampSesion);

        if (asientoId < 1 || asientoId > 4) {
            return new RespuestaReserva(
                    RespuestaReserva.STATUS_ERR,
                    1,
                    0,
                    "Asiento inválido."
            );
        }

        Map<Integer, Asiento> sesion = inventario.get(timestampSesion);
        Asiento asiento = sesion.get(asientoId);

        if (!Asiento.LIBRE.equals(asiento.getEstado())) {
            return new RespuestaReserva(
                    RespuestaReserva.STATUS_ERR,
                    2,
                    0,
                    "El asiento no está libre."
            );
        }

        int nuevoId = generarIdReservaUnico();

        asiento.setEstado(Asiento.BLOQUEADO);
        asiento.setIdReserva(nuevoId);
        reservasActivas.put(nuevoId, new int[]{timestampSesion, asientoId});

        asiento.setTareaExpiracion(
                scheduler.schedule(
                        () -> expirarReserva(timestampSesion, asientoId, nuevoId),
                        TIEMPO_EXPIRACION,
                        TimeUnit.SECONDS
                )
        );

        System.out.println("BLOQUEADO: Asiento " + asientoId + " -> ID: " + nuevoId);

        return new RespuestaReserva(
                RespuestaReserva.STATUS_OK,
                nuevoId,
                TIEMPO_EXPIRACION,
                "Asiento bloqueado temporalmente."
        );
    }

    @Override
    public synchronized RespuestaReserva confirmarReserva(int idReserva) throws RemoteException {
        if (!reservasActivas.containsKey(idReserva)) {
            return new RespuestaReserva(
                    RespuestaReserva.STATUS_ERR,
                    3,
                    0,
                    "ID incorrecto o tiempo expirado."
            );
        }

        int[] datosReserva = reservasActivas.get(idReserva);
        int timestampSesion = datosReserva[0];
        int asientoId = datosReserva[1];

        Asiento asiento = inventario.get(timestampSesion).get(asientoId);

        if (asiento.getTareaExpiracion() != null) {
            asiento.getTareaExpiracion().cancel(false);
        }

        asiento.setEstado(Asiento.CONFIRMADO);

        System.out.println("CONFIRMADO: ID " + idReserva);

        return new RespuestaReserva(
                RespuestaReserva.STATUS_OK,
                idReserva,
                0,
                "Reserva confirmada correctamente."
        );
    }

    @Override
    public synchronized RespuestaReserva cancelarReserva(int idReserva) throws RemoteException {
        if (!reservasActivas.containsKey(idReserva)) {
            return new RespuestaReserva(
                    RespuestaReserva.STATUS_ERR,
                    3,
                    0,
                    "ID incorrecto o tiempo expirado."
            );
        }

        int[] datosReserva = reservasActivas.get(idReserva);
        int timestampSesion = datosReserva[0];
        int asientoId = datosReserva[1];

        Asiento asiento = inventario.get(timestampSesion).get(asientoId);

        if (asiento.getTareaExpiracion() != null) {
            asiento.getTareaExpiracion().cancel(false);
        }

        asiento.setEstado(Asiento.LIBRE);
        asiento.setIdReserva(0);
        asiento.setTareaExpiracion(null);

        reservasActivas.remove(idReserva);

        System.out.println("CANCELADO: ID " + idReserva);

        return new RespuestaReserva(
                RespuestaReserva.STATUS_OK,
                idReserva,
                0,
                "Reserva cancelada correctamente."
        );
    }

    private synchronized void expirarReserva(int timestampSesion, int asientoId, int idReserva) {
        try {
            Asiento asiento = inventario.get(timestampSesion).get(asientoId);

            if (Asiento.BLOQUEADO.equals(asiento.getEstado()) && asiento.getIdReserva() == idReserva) {
                asiento.setEstado(Asiento.LIBRE);
                asiento.setIdReserva(0);
                asiento.setTareaExpiracion(null);
                reservasActivas.remove(idReserva);

                System.out.println("Tiempo agotado. Asiento " + asientoId
                        + " liberado para la sesión " + timestampSesion);
            }
        } catch (Exception e) {
            System.out.println("Error al expirar reserva: " + e.getMessage());
        }
    }

    private int generarIdReservaUnico() {
        int id;

        do {
            id = 1000 + random.nextInt(999000);
        } while (reservasActivas.containsKey(id));

        return id;
    }
}
