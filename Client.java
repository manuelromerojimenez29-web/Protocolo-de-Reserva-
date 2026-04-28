import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Client {

    private static String buscarServidorAutomaticamente() {
        System.out.println("\nBuscando servidor en la red Wi-Fi...");

        try (DatagramSocket descSock = new DatagramSocket()) {
            descSock.setBroadcast(true);
            descSock.setSoTimeout(5000); // Timeout di 5 secondi

            byte[] sendData = "BUSCANDO_SERVIDOR".getBytes();
            // Invia broadcast
            DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length, InetAddress.getByName("255.255.255.255"), 50000
            );
            descSock.send(sendPacket);

            // Attende la risposta
            byte[] recvData = new byte[1024];
            DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);
            descSock.receive(recvPacket);

            String response = new String(recvPacket.getData(), 0, recvPacket.getLength());
            if ("AQUI_ESTOY".equals(response.trim())) {
                String ipDescubierta = recvPacket.getAddress().getHostAddress();
                System.out.println("[ÉXITO] Servidor encontrado automáticamente en la IP: " + ipDescubierta + "\n");
                return ipDescubierta;
            }
        } catch (SocketTimeoutException e) {
            System.out.println("[ERROR] Nadie respondió. Asegúrate de que:");
            System.out.println("  1. El servidor está ejecutándose.");
            System.out.println("  2. Ambos estáis en la misma red Wi-Fi.");
            System.out.println("  3. El cortafuegos (Firewall) no bloquea la red.");
        } catch (Exception e) {
            System.err.println("Errore di rete: " + e.getMessage());
        }
        return null;
    }

    private static long pedirFechaHora(Scanner scanner) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        while (true) {
            System.out.print("\nIntroduce fecha y hora de la sesión (ej. 2024-10-25 18:00): ");
            String fechaStr = scanner.nextLine();
            try {
                LocalDateTime dt = LocalDateTime.parse(fechaStr, formatter);
                if (dt.getMinute() != 0 && dt.getMinute() != 30) {
                    System.out.println("\n[ERROR] Horario inválido. Las reservas solo pueden hacerse a las en punto (:00) o a y media (:30).");
                    continue;
                }
                // Converte in timestamp UNIX (secondi)
                return dt.atZone(ZoneId.systemDefault()).toEpochSecond();
            } catch (Exception e) {
                System.out.println("\n[ERROR] Formato incorrecto. Usa AAAA-MM-DD HH:MM");
            }
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // 1. Scoperta IP UDP
        String host = buscarServidorAutomaticamente();
        if (host == null) {
            System.out.print("No se encontró el servidor. Introduce la IP manualmente (o 'salir'): ");
            host = scanner.nextLine();
            if ("salir".equalsIgnoreCase(host)) {
                System.out.println("Cerrando programa...");
                System.exit(0);
            }
        }

        // 2. Chiediamo subito la data
        long tsActual = pedirFechaHora(scanner);

        // 3. Connessione RMI al server
        ReservaService stub = null;
        try {
            Registry registry = LocateRegistry.getRegistry(host, 1099);
            stub = (ReservaService) registry.lookup("ReservaService");
            System.out.println("Conectado mediante RMI al servidor " + host);
        } catch (Exception e) {
            System.out.println("Error: Servidor RMI no disponible en " + host);
            e.printStackTrace();
            System.exit(1);
        }

        // 4. Ciclo del menu
        boolean exitProgram = false;
        while (!exitProgram) {
            boolean mostrarSubmenu = false;

            System.out.println("\n--- RESERVAS RMI ---");
            System.out.println("1. Consultar disponibilidad");
            System.out.println("2. Reservar un asiento");
            System.out.println("3. Confirmar reserva (Pagar)");
            System.out.println("4. Cancelar reserva");
            System.out.println("5. Cambiar fecha/hora de búsqueda");
            System.out.println("6. Salir");
            System.out.print("Elige (1-6): ");

            String opcion = scanner.nextLine();

            try {
                if ("1".equals(opcion)) {
                    int[] respuesta = stub.listAsientos(tsActual);
                    if (respuesta[0] == ReservaService.STATUS_NONE) {
                        System.out.println("\n[AVISO] Lo sentimos, no hay asientos disponibles para esta sesión.");
                    } else if (respuesta[0] == ReservaService.STATUS_OK) {
                        int mascara = respuesta[1];
                        List<Integer> libres = new ArrayList<>();
                        for (int i = 1; i <= 4; i++) {
                            if ((mascara & (1 << i)) != 0) libres.add(i);
                        }
                        System.out.println("\n[OK] Asientos disponibles: " + libres);
                    }

                } else if ("2".equals(opcion)) {
                    System.out.print("Número de asiento a reservar (1-4): ");
                    int asiento = Integer.parseInt(scanner.nextLine());

                    int[] respuesta = stub.bookAsiento(tsActual, asiento);
                    if (respuesta[0] == ReservaService.STATUS_OK) {
                        System.out.println("\n[ÉXITO] Asiento " + asiento + " BLOQUEADO.");
                        System.out.println("Tu ID temporal es: " + respuesta[1]);
                        System.out.println("⚠️ TIENES " + respuesta[2] + " SEGUNDOS PARA CONFIRMAR O SE LIBERARÁ.");
                    } else {
                        System.out.println("\n[ERROR] No se pudo reservar. Código de error del servidor: " + respuesta[1]);
                    }

                } else if ("3".equals(opcion)) {
                    System.out.print("Introduce el ID de reserva a confirmar: ");
                    int idRes = Integer.parseInt(scanner.nextLine());

                    int[] respuesta = stub.confirmReserva(idRes);
                    if (respuesta[0] == ReservaService.STATUS_OK) {
                        System.out.println("\n[ÉXITO] Reserva CONFIRMADA permanentemente.");
                    } else {
                        System.out.println("\n[ERROR] ID incorrecto o el tiempo expiró.");
                    }
                    mostrarSubmenu = true;

                } else if ("4".equals(opcion)) {
                    System.out.print("Introduce el ID de reserva a cancelar: ");
                    int idRes = Integer.parseInt(scanner.nextLine());

                    int[] respuesta = stub.cancelReserva(idRes);
                    if (respuesta[0] == ReservaService.STATUS_OK) {
                        System.out.println("\n[ÉXITO] Reserva cancelada.");
                    } else {
                        System.out.println("\n[ERROR] No se pudo cancelar.");
                    }

                } else if ("5".equals(opcion)) {
                    tsActual = pedirFechaHora(scanner);

                } else if ("6".equals(opcion)) {
                    break;
                } else {
                    System.out.println("\n[ERROR] Opción no válida.");
                    continue;
                }
            } catch (NumberFormatException e) {
                System.out.println("\n[ERROR] Debes introducir un número válido.");
            } catch (Exception e) {
                System.out.println("\n[ERROR RMI] Problema de comunicación con el servidor: " + e.getMessage());
            }

            // Sub-menu logica
            if (mostrarSubmenu) {
                while (true) {
                    System.out.println("\n¿Qué deseas hacer ahora?");
                    System.out.println("1. Volver al menú principal");
                    System.out.println("2. Salir");
                    System.out.print("Elige (1-2): ");

                    String subOpcion = scanner.nextLine();
                    if ("1".equals(subOpcion)) {
                        break;
                    } else if ("2".equals(subOpcion)) {
                        exitProgram = true;
                        break;
                    } else {
                        System.out.println("Opción no válida.");
                    }
                }
            }
        }

        System.out.println("Cerrando cliente...");
        scanner.close();
    }
}