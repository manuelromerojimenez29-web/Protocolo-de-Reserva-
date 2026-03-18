import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ClienteReservas {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 65432;

    // Códigos de protocolo
    private static final int CMD_LIST = 1;
    private static final int CMD_BOOK = 2;
    private static final int CMD_CONFIRM = 3;
    private static final int CMD_CANCEL = 4;

    private static final int STATUS_OK = 0;
    private static final int STATUS_ERR = 1;
    private static final int STATUS_NONE = 2;

    private static final Scanner scanner = new Scanner(System.in);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void main(String[] args) {
        iniciarCliente();
    }

    private static int pedirFechaHora() {
        while (true) {
            System.out.print("\nIntroduce fecha y hora de la sesión (ej. 2024-10-25 18:00): ");
            String fechaStr = scanner.nextLine();

            try {
                LocalDateTime dt = LocalDateTime.parse(fechaStr, formatter);

                if (dt.getMinute() != 0 && dt.getMinute() != 30) {
                    System.out.println("\n[ERROR] Horario inválido. Las reservas solo pueden hacerse a las en punto (:00) o a y media (:30).");
                    continue;
                }

                long epochSeconds = dt.atZone(ZoneId.systemDefault()).toEpochSecond();
                return (int) epochSeconds;

            } catch (DateTimeParseException e) {
                System.out.println("\n[ERROR] Formato incorrecto. Usa AAAA-MM-DD HH:MM");
            }
        }
    }

    private static Respuesta enviarComando(DataOutputStream out, DataInputStream in, int cmd, int ts, int asiento, int idRes) throws IOException {
        // Petición: 10 bytes = B I B I
        out.writeByte(cmd);       // 1 byte
        out.writeInt(ts);         // 4 bytes
        out.writeByte(asiento);   // 1 byte
        out.writeInt(idRes);      // 4 bytes
        out.flush();

        // Respuesta: 9 bytes = B I I
        int estado = in.readUnsignedByte(); // 1 byte
        int p1 = in.readInt();              // 4 bytes
        int p2 = in.readInt();              // 4 bytes

        return new Respuesta(estado, p1, p2);
    }

    private static void iniciarCliente() {
        try (
            Socket socket = new Socket(HOST, PORT);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))
        ) {
            System.out.println("Conectado en modo BINARIO al servidor " + HOST + ":" + PORT);

            int tsActual = pedirFechaHora();

            while (true) {
                boolean mostrarSubmenu = false;

                System.out.println("\n--- RESERVAS BINARIAS ---");
                System.out.println("1. Consultar disponibilidad");
                System.out.println("2. Reservar un asiento");
                System.out.println("3. Confirmar reserva (Pagar)");
                System.out.println("4. Cancelar reserva");
                System.out.println("5. Cambiar fecha/hora de búsqueda");
                System.out.println("6. Salir");
                System.out.print("Elige (1-6): ");
                String opcion = scanner.nextLine();

                switch (opcion) {
                    case "1": {
                        Respuesta r = enviarComando(out, in, CMD_LIST, tsActual, 0, 0);

                        if (r.estado == STATUS_NONE) {
                            System.out.println("\n[AVISO] Lo sentimos, no hay asientos disponibles para esta sesión.");
                        } else if (r.estado == STATUS_OK) {
                            List<Integer> libres = new ArrayList<>();
                            for (int i = 1; i <= 4; i++) {
                                if ((r.p1 & (1 << i)) != 0) {
                                    libres.add(i);
                                }
                            }
                            System.out.println("\n[OK] Asientos disponibles: " + libres);
                        } else {
                            System.out.println("\n[ERROR] No se pudo consultar disponibilidad.");
                        }
                        break;
                    }

                    case "2": {
                        try {
                            System.out.print("Número de asiento a reservar (1-4): ");
                            int asiento = Integer.parseInt(scanner.nextLine());

                            Respuesta r = enviarComando(out, in, CMD_BOOK, tsActual, asiento, 0);

                            if (r.estado == STATUS_OK) {
                                System.out.println("\n[ÉXITO] Asiento " + asiento + " BLOQUEADO.");
                                System.out.println("Tu ID temporal es: " + r.p1);
                                System.out.println("⚠️ TIENES " + r.p2 + " SEGUNDOS PARA CONFIRMAR O SE LIBERARÁ.");
                            } else {
                                System.out.println("\n[ERROR] No se pudo reservar. Código de error del servidor: " + r.p1);
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("\n[ERROR] Debes introducir un número válido.");
                        }
                        break;
                    }

                    case "3": {
                        try {
                            System.out.print("Introduce el ID de reserva a confirmar: ");
                            int idRes = Integer.parseInt(scanner.nextLine());

                            Respuesta r = enviarComando(out, in, CMD_CONFIRM, tsActual, 0, idRes);

                            if (r.estado == STATUS_OK) {
                                System.out.println("\n[ÉXITO] Reserva CONFIRMADA permanentemente.");
                            } else {
                                System.out.println("\n[ERROR] ID incorrecto o el tiempo de 60 segundos expiró.");
                            }

                            mostrarSubmenu = true;

                        } catch (NumberFormatException e) {
                            System.out.println("\n[ERROR] Debes introducir un número de ID válido.");
                        }
                        break;
                    }

                    case "4": {
                        try {
                            System.out.print("Introduce el ID de reserva a cancelar: ");
                            int idRes = Integer.parseInt(scanner.nextLine());

                            Respuesta r = enviarComando(out, in, CMD_CANCEL, tsActual, 0, idRes);

                            if (r.estado == STATUS_OK) {
                                System.out.println("\n[ÉXITO] Reserva cancelada.");
                            } else {
                                System.out.println("\n[ERROR] No se pudo cancelar. Código de error del servidor: " + r.p1);
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("\n[ERROR] Debes introducir un número de ID válido.");
                        }
                        break;
                    }

                    case "5":
                        tsActual = pedirFechaHora();
                        break;

                    case "6":
                        System.out.println("Cerrando cliente...");
                        return;

                    default:
                        System.out.println("\n[ERROR] Opción no válida.");
                        continue;
                }

                if (mostrarSubmenu) {
                    boolean salirDelPrograma = false;

                    while (true) {
                        System.out.println("\n¿Qué deseas hacer ahora?");
                        System.out.println("1. Volver al menú principal");
                        System.out.println("2. Salir");
                        System.out.print("Elige (1-2): ");
                        String subOpcion = scanner.nextLine();

                        if ("1".equals(subOpcion)) {
                            break;
                        } else if ("2".equals(subOpcion)) {
                            salirDelPrograma = true;
                            break;
                        } else {
                            System.out.println("Opción no válida.");
                        }
                    }

                    if (salirDelPrograma) {
                        break;
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("Error: Servidor no disponible o conexión fallida.");
            System.out.println("Detalle: " + e.getMessage());
        }

        System.out.println("Cerrando cliente...");
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