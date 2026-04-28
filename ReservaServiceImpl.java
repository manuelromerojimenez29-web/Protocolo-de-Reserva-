import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ReservaServiceImpl extends UnicastRemoteObject implements ReservaService {

    private final float TIEMPO_EXPIRACION = 60.0f;

    // Strutture dati interne
    private class Asiento {
        String estado = "LIBRE";
        int idReserva = 0;
        ScheduledFuture<?> timerTask = null;
    }

    private class ReservaLocal {
        long ts;
        int aId;
        ReservaLocal(long ts, int aId) { this.ts = ts; this.aId = aId; }
    }

    private final Map<Long, Map<Integer, Asiento>> inventario = new HashMap<>();
    private final Map<Integer, ReservaLocal> reservasActivas = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final Object lock = new Object(); // Sostituisce inventario_lock
    private final Random random = new Random();

    public ReservaServiceImpl() throws RemoteException {
        super();
    }

    private void inicializarSesion(long ts) {
        if (!inventario.containsKey(ts)) {
            Map<Integer, Asiento> sesion = new HashMap<>();
            for (int i = 1; i <= 4; i++) {
                sesion.put(i, new Asiento());
            }
            inventario.put(ts, sesion);
        }
    }

    private void expirarReserva(long ts, int asientoId, int idReserva) {
        synchronized (lock) {
            try {
                Asiento asiento = inventario.get(ts).get(asientoId);
                if ("BLOQUEADO".equals(asiento.estado) && asiento.idReserva == idReserva) {
                    asiento.estado = "LIBRE";
                    asiento.idReserva = 0;
                    asiento.timerTask = null;
                    reservasActivas.remove(idReserva);
                    System.out.println("Tempo esaurito. Posto " + asientoId + " liberato per la sessione " + ts + ".");
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public int[] listAsientos(long ts) throws RemoteException {
        synchronized (lock) {
            inicializarSesion(ts);
            Map<Integer, Asiento> sesion = inventario.get(ts);
            int disponiblesMask = 0;
            int asientosLibres = 0;

            for (Map.Entry<Integer, Asiento> entry : sesion.entrySet()) {
                if ("LIBRE".equals(entry.getValue().estado)) {
                    disponiblesMask |= (1 << entry.getKey());
                    asientosLibres++;
                }
            }

            if (asientosLibres == 0) return new int[]{STATUS_NONE, 0, 0};
            return new int[]{STATUS_OK, disponiblesMask, 0};
        }
    }

    @Override
    public int[] bookAsiento(long ts, int asientoId) throws RemoteException {
        synchronized (lock) {
            inicializarSesion(ts);
            if (asientoId < 1 || asientoId > 4) return new int[]{STATUS_ERR, 1, 0};

            Asiento asiento = inventario.get(ts).get(asientoId);
            if (!"LIBRE".equals(asiento.estado)) return new int[]{STATUS_ERR, 2, 0};

            int nuevoId = 1000 + random.nextInt(999000); // 1000 - 999999
            asiento.estado = "BLOQUEADO";
            asiento.idReserva = nuevoId;
            reservasActivas.put(nuevoId, new ReservaLocal(ts, asientoId));

            // Timer di scadenza
            asiento.timerTask = scheduler.schedule(
                    () -> expirarReserva(ts, asientoId, nuevoId),
                    (long) TIEMPO_EXPIRACION, TimeUnit.SECONDS
            );

            System.out.println("BLOCCATO: Posto " + asientoId + " -> ID: " + nuevoId);
            return new int[]{STATUS_OK, nuevoId, (int) TIEMPO_EXPIRACION};
        }
    }

    @Override
    public int[] confirmReserva(int idReserva) throws RemoteException {
        synchronized (lock) {
            if (!reservasActivas.containsKey(idReserva)) return new int[]{STATUS_ERR, 3, 0};

            ReservaLocal res = reservasActivas.get(idReserva);
            Asiento asiento = inventario.get(res.ts).get(res.aId);

            if (asiento.timerTask != null) asiento.timerTask.cancel(false);
            asiento.estado = "CONFIRMADO";

            System.out.println("CONFERMATO: ID " + idReserva);
            return new int[]{STATUS_OK, idReserva, 0};
        }
    }

    @Override
    public int[] cancelReserva(int idReserva) throws RemoteException {
        synchronized (lock) {
            if (!reservasActivas.containsKey(idReserva)) return new int[]{STATUS_ERR, 3, 0};

            ReservaLocal res = reservasActivas.get(idReserva);
            Asiento asiento = inventario.get(res.ts).get(res.aId);

            if (asiento.timerTask != null) asiento.timerTask.cancel(false);

            asiento.estado = "LIBRE";
            asiento.idReserva = 0;
            asiento.timerTask = null;
            reservasActivas.remove(idReserva);

            System.out.println("CANCELLATO: ID " + idReserva);
            return new int[]{STATUS_OK, idReserva, 0};
        }
    }
}
