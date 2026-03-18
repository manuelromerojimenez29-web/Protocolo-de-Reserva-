import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ServidorReservas {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 65432;
    private static final int TIEMPO_EXPIRACION = 60; // segundos

    // Códigos de protocolo
    private static final int CMD_LIST = 1;
    private static final int CMD_BOOK = 2;
    private static final int CMD_CONFIRM = 3;
    private static final int CMD_CANCEL = 4;

    private static final int STATUS_OK = 0;
    private static final int STATUS_ERR = 1;
    private static final int STATUS_NONE = 2;

    // inventario[timestamp][asiento] = Seat
    private static final Map<Integer, Map<Integer, Seat>> inventario = new HashMap<>();

    // Reservas temporales bloqueadas
    private static final Map<Integer, ReservaActiva> reservasActivas = new HashMap<>();

    // Reservas ya confirmadas
    private static final Map<Integer, ReservaActiva> reservasConfirmadas = new HashMap<>();

    private static final Object inventarioLock = new Object();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private static final Random random = new Random();

    public static void main(String[] args) {
        iniciarServidor();
    }

    private static void inicializarSesion(int ts) {
        if (!inventario.containsKey(ts)) {
            Map<Integer, Seat> sesion = new HashMap<>();
            for (int i = 1; i <= 4; i++) {
                sesion.put(i, new Seat());
            }
            inventario.put(ts, sesion);
        }
    }

    private static void expirarReserva(int ts, int asientoId, int idReserva) {
        synchronized (inventarioLock) {
            Map<Integer, Seat> sesion = inventario.get(ts);
            if (sesion == null) {
                return;
            }

            Seat asiento = sesion.get(asientoId);
            if (asiento == null) {
                return;
            }

            if ("BLOQUEADO".equals(asiento.estado) && asiento.idReserva == idReserva) {
                asiento.estado = "LIBRE";
                asiento.idReserva = 0;
                asiento.expirationTask = null;
                reservasActivas.remove(idReserva);

                System.out.println("[INFO] Tiempo agotado. Asiento " + asientoId + " liberado para la sesión " + ts + ".");
            }
        }
    }

    private static Respuesta procesarMensaje(int comando, int ts, int asientoId, int idReserva, SocketAddress addr) {
        synchronized (inventarioLock) {
            inicializarSesion(ts);
            Map<Integer, Seat> sesion = inventario.get(ts);

            switch (comando) {
                case CMD_LIST: {
                    int disponiblesMask = 0;
                    int asientosLibres = 0;

                    for (Map.Entry<Integer, Seat> entry : sesion.entrySet()) {
                        int aId = entry.getKey();
                        Seat datos = entry.getValue();

                        if ("LIBRE".equals(datos.estado)) {
                            disponiblesMask |= (1 << aId);
                            asientosLibres++;
                        }
                    }

                    if (asientosLibres == 0) {
                        return new Respuesta(STATUS_NONE, 0, 0);
                    }
                    return new Respuesta(STATUS_OK, disponiblesMask, 0);
                }

                case CMD_BOOK: {
                    if (asientoId < 1 || asientoId > 4) {
                        return new Respuesta(STATUS_ERR, 1, 0); // asiento inválido
                    }

                    Seat asiento = sesion.get(asientoId);
                    if (!"LIBRE".equals(asiento.estado)) {
                        return new Respuesta(STATUS_ERR, 2, 0); // asiento ocupado/bloqueado
                    }

                    int nuevoId;
                    do {
                        nuevoId = 1000 + random.nextInt(999999 - 1000 + 1);
                    } while (reservasActivas.containsKey(nuevoId) || reservasConfirmadas.containsKey(nuevoId));

                    final int idFinal = nuevoId;

                    asiento.estado = "BLOQUEADO";
                    asiento.idReserva = idFinal;
                    reservasActivas.put(idFinal, new ReservaActiva(ts, asientoId));

                    asiento.expirationTask = scheduler.schedule(
                        () -> expirarReserva(ts, asientoId, idFinal),
                        TIEMPO_EXPIRACION,
                        TimeUnit.SECONDS
                    );

                    System.out.println("[INFO] [" + addr + "] BLOQUEADO: Asiento " + asientoId + " -> ID: " + idFinal);
                    return new Respuesta(STATUS_OK, idFinal, TIEMPO_EXPIRACION);
                }

                case CMD_CONFIRM: {
                    ReservaActiva reserva = reservasActivas.get(idReserva);
                    if (reserva == null) {
                        return new Respuesta(STATUS_ERR, 3, 0); // id no existe como activa
                    }

                    Map<Integer, Seat> sesionRes = inventario.get(reserva.ts);
                    if (sesionRes == null) {
                        return new Respuesta(STATUS_ERR, 3, 0);
                    }

                    Seat asiento = sesionRes.get(reserva.asientoId);
                    if (asiento == null) {
                        return new Respuesta(STATUS_ERR, 3, 0);
                    }

                    if (asiento.expirationTask != null) {
                        asiento.expirationTask.cancel(false);
                    }

                    asiento.estado = "CONFIRMADO";
                    asiento.expirationTask = null;

                    reservasActivas.remove(idReserva);
                    reservasConfirmadas.put(idReserva, reserva);

                    System.out.println("[INFO] [" + addr + "] CONFIRMADO: ID " + idReserva);
                    return new Respuesta(STATUS_OK, idReserva, 0);
                }

                case CMD_CANCEL: {
                    ReservaActiva reserva = reservasActivas.get(idReserva);

                    if (reserva != null) {
                        Map<Integer, Seat> sesionRes = inventario.get(reserva.ts);
                        if (sesionRes == null) {
                            return new Respuesta(STATUS_ERR, 3, 0);
                        }

                        Seat asiento = sesionRes.get(reserva.asientoId);
                        if (asiento == null) {
                            return new Respuesta(STATUS_ERR, 3, 0);
                        }

                        if (asiento.expirationTask != null) {
                            asiento.expirationTask.cancel(false);
                        }

                        asiento.estado = "LIBRE";
                        asiento.idReserva = 0;
                        asiento.expirationTask = null;
                        reservasActivas.remove(idReserva);

                        System.out.println("[INFO] [" + addr + "] CANCELADO (activa): ID " + idReserva);
                        return new Respuesta(STATUS_OK, idReserva, 0);
                    }

                    ReservaActiva reservaConfirmada = reservasConfirmadas.get(idReserva);

                    if (reservaConfirmada != null) {
                        Map<Integer, Seat> sesionRes = inventario.get(reservaConfirmada.ts);
                        if (sesionRes == null) {
                            return new Respuesta(STATUS_ERR, 3, 0);
                        }

                        Seat asiento = sesionRes.get(reservaConfirmada.asientoId);
                        if (asiento == null) {
                            return new Respuesta(STATUS_ERR, 3, 0);
                        }

                        asiento.estado = "LIBRE";
                        asiento.idReserva = 0;
                        asiento.expirationTask = null;
                        reservasConfirmadas.remove(idReserva);

                        System.out.println("[INFO] [" + addr + "] CANCELADO (confirmada): ID " + idReserva);
                        return new Respuesta(STATUS_OK, idReserva, 0);
                    }

                    return new Respuesta(STATUS_ERR, 3, 0);
                }

                default:
                    return new Respuesta(STATUS_ERR, 99, 0);
            }
        }
    }

    private static void manejarCliente(Socket clientSocket) {
        SocketAddress addr = clientSocket.getRemoteSocketAddress();

        try (
            Socket conn = clientSocket;
            DataInputStream in = new DataInputStream(new BufferedInputStream(conn.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(conn.getOutputStream()))
        ) {
            while (true) {
                int comando;
                int ts;
                int asientoId;
                int idReserva;

                try {
                    // Petición: 10 bytes = B I B I
                    comando = in.readUnsignedByte();
                    ts = in.readInt();
                    asientoId = in.readUnsignedByte();
                    idReserva = in.readInt();
                } catch (EOFException e) {
                    break;
                }

                Respuesta r = procesarMensaje(comando, ts, asientoId, idReserva, addr);

                // Respuesta: 9 bytes = B I I
                out.writeByte(r.estado);
                out.writeInt(r.p1);
                out.writeInt(r.p2);
                out.flush();
            }

        } catch (IOException e) {
            System.err.println("[ERROR] Error con " + addr + ": " + e.getMessage());
        }
    }

    private static void iniciarServidor() {
        try (ServerSocket serverSocket = new ServerSocket(PORT, 50)) {
            System.out.println("[INFO] Servidor BINARIO escuchando en " + HOST + ":" + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread hiloCliente = new Thread(() -> manejarCliente(clientSocket));
                hiloCliente.setDaemon(true);
                hiloCliente.start();
            }

        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo iniciar el servidor: " + e.getMessage());
        } finally {
            scheduler.shutdown();
        }
    }

    private static class Seat {
        String estado = "LIBRE";
        int idReserva = 0;
        java.util.concurrent.ScheduledFuture<?> expirationTask = null;
    }

    private static class ReservaActiva {
        int ts;
        int asientoId;

        ReservaActiva(int ts, int asientoId) {
            this.ts = ts;
            this.asientoId = asientoId;
        }
    }

    private static class Respuesta {
        int estado;
        int p1;
        int p2;

        Respuesta(int estado, int p1, int p2) {
            this.estado = estado;
            this.p1 = p1;
            this.p2 = p2;
        }
    }
}