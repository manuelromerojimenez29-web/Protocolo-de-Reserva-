
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;




/**
 *
 * @author MSI
 */


public class ClienteRMI {

    private static final int STATUS_OK = 0;
    private static final int STATUS_NONE = 2;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try {
            String host = "localhost";

            // Si el servidor está en otro ordenador, cambia localhost por su IP:
            // String host = "192.168.158.164";

            Registry registro = LocateRegistry.getRegistry(host, 1099);
            ServicioReservas servicio = (ServicioReservas) registro.lookup("ServicioReservas");

            System.out.println("Conectado al servidor RMI en " + host + ":1099");

            int timestampSesion = pedirFechaHora(scanner);

            while (true) {
                System.out.println("\n--- RESERVAS RMI ---");
                System.out.println("1. Consultar disponibilidad");
                System.out.println("2. Reservar un asiento");
                System.out.println("3. Confirmar reserva (Pagar)");
                System.out.println("4. Cancelar reserva");
                System.out.println("5. Cambiar fecha/hora de búsqueda");
                System.out.println("6. Salir");
                System.out.print("Elige (1-6): ");

                String opcion = scanner.nextLine();

                if (opcion.equals("1")) {
                    RespuestaReserva respuesta = servicio.listarDisponibilidad(timestampSesion);

                    if (respuesta.getEstado() == STATUS_NONE) {
                        System.out.println("\n[AVISO] Lo sentimos, no hay asientos disponibles para esta sesión.");
                    } else if (respuesta.getEstado() == STATUS_OK) {
                        int mascara = respuesta.getParametro1();

                        System.out.print("\n[OK] Asientos disponibles: [");

                        boolean primero = true;
                        for (int i = 1; i <= 4; i++) {
                            if ((mascara & (1 << i)) != 0) {
                                if (!primero) {
                                    System.out.print(", ");
                                }
                                System.out.print(i);
                                primero = false;
                            }
                        }

                        System.out.println("]");
                    } else {
                        System.out.println("\n[ERROR] " + respuesta.getMensaje());
                    }

                } else if (opcion.equals("2")) {
                    try {
                        System.out.print("Número de asiento a reservar (1-4): ");
                        int asiento = Integer.parseInt(scanner.nextLine());

                        RespuestaReserva respuesta = servicio.reservarAsiento(timestampSesion, asiento);

                        if (respuesta.getEstado() == STATUS_OK) {
                            int idReserva = respuesta.getParametro1();
                            int tiempo = respuesta.getParametro2();

                            System.out.println("\n[ÉXITO] Asiento " + asiento + " BLOQUEADO.");
                            System.out.println("Tu ID temporal es: " + idReserva);
                            System.out.println("TIENES " + tiempo + " SEGUNDOS PARA CONFIRMAR O SE LIBERARÁ.");
                        } else {
                            System.out.println("\n[ERROR] No se pudo reservar.");
                            System.out.println("Motivo: " + respuesta.getMensaje());
                            System.out.println("Código de error del servidor: " + respuesta.getParametro1());
                        }

                    } catch (NumberFormatException e) {
                        System.out.println("\n[ERROR] Debes introducir un número válido.");
                    }

                } else if (opcion.equals("3")) {
                    try {
                        System.out.print("Introduce el ID de reserva a confirmar: ");
                        int idReserva = Integer.parseInt(scanner.nextLine());

                        RespuestaReserva respuesta = servicio.confirmarReserva(idReserva);

                        if (respuesta.getEstado() == STATUS_OK) {
                            System.out.println("\n[ÉXITO] Reserva CONFIRMADA permanentemente.");
                        } else {
                            System.out.println("\n[ERROR] ID incorrecto o el tiempo de 60 segundos expiró.");
                        }

                        if (preguntarSalir(scanner)) {
                            break;
                        }

                    } catch (NumberFormatException e) {
                        System.out.println("\n[ERROR] Debes introducir un número de ID válido.");
                    }

                } else if (opcion.equals("4")) {
                    try {
                        System.out.print("Introduce el ID de reserva a cancelar: ");
                        int idReserva = Integer.parseInt(scanner.nextLine());

                        RespuestaReserva respuesta = servicio.cancelarReserva(idReserva);

                        if (respuesta.getEstado() == STATUS_OK) {
                            System.out.println("\n[ÉXITO] Reserva cancelada.");
                        } else {
                            System.out.println("\n[ERROR] No se pudo cancelar.");
                        }

                    } catch (NumberFormatException e) {
                        System.out.println("\n[ERROR] Debes introducir un número de ID válido.");
                    }

                } else if (opcion.equals("5")) {
                    timestampSesion = pedirFechaHora(scanner);

                } else if (opcion.equals("6")) {
                    break;

                } else {
                    System.out.println("\n[ERROR] Opción no válida.");
                }
            }

            System.out.println("Cerrando cliente...");

        } catch (Exception e) {
            System.out.println("Error en el cliente RMI:");
            e.printStackTrace();
        }
    }

    private static int pedirFechaHora(Scanner scanner) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        while (true) {
            System.out.print("\nIntroduce fecha y hora de la sesión (ej. 2026-05-10 18:00): ");
            String fechaStr = scanner.nextLine();

            try {
                LocalDateTime fecha = LocalDateTime.parse(fechaStr, formatter);

                int minuto = fecha.getMinute();

                if (minuto != 0 && minuto != 30) {
                    System.out.println("\n[ERROR] Horario inválido. Las reservas solo pueden hacerse a las en punto (:00) o a y media (:30).");
                    continue;
                }

                long timestamp = fecha.atZone(ZoneId.systemDefault()).toEpochSecond();
                return (int) timestamp;

            } catch (Exception e) {
                System.out.println("\n[ERROR] Formato incorrecto. Usa AAAA-MM-DD HH:MM");
            }
        }
    }

    private static boolean preguntarSalir(Scanner scanner) {
        while (true) {
            System.out.println("\n¿Qué deseas hacer ahora?");
            System.out.println("1. Volver al menú principal");
            System.out.println("2. Salir");
            System.out.print("Elige (1-2): ");

            String opcion = scanner.nextLine();

            if (opcion.equals("1")) {
                return false;
            } else if (opcion.equals("2")) {
                return true;
            } else {
                System.out.println("Opción no válida.");
            }
        }
    }
}