import socket
import sys

# Configuración del cliente (debe coincidir con el servidor)
HOST = '127.0.0.1'
PORT = 65432

def mostrar_menu():
    print("\n--- PROTOCOLO DE RESERVAS ---")
    print("1. Consultar disponibilidad (LIST)")
    print("2. Reservar un recurso (BOOK)")
    print("3. Cancelar una reserva (CANCEL)")
    print("4. Salir")
    print("-----------------------------")

def iniciar_cliente():
    # Establecimiento de la comunicación (TCP)
    try:
        client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client_socket.connect((HOST, PORT))
        print(f"Conectado exitosamente al servidor {HOST}:{PORT}")
    except ConnectionRefusedError:
        print("Error: No se pudo conectar al servidor. ¿Está en ejecución?")
        sys.exit()

    try:
        while True:
            mostrar_menu()
            opcion = input("Elige una opción (1-4): ")
            
            mensaje = ""
            if opcion == '1':
                mensaje = "LIST\n"
            elif opcion == '2':
                recurso = input("Introduce el nombre del recurso a reservar (ej. ASIENTO_1): ")
                mensaje = f"BOOK|{recurso}\n"
            elif opcion == '3':
                id_reserva = input("Introduce el ID de la reserva a cancelar: ")
                mensaje = f"CANCEL|{id_reserva}\n"
            elif opcion == '4':
                print("Cerrando cliente...")
                break
            else:
                print("Opción no válida.")
                continue

            # Construcción y envío del mensaje
            client_socket.sendall(mensaje.encode('utf-8'))
            
            # Gestión de la recepción de respuestas
            respuesta = client_socket.recv(1024).decode('utf-8').strip()
            print(f"\n[RESPUESTA DEL SERVIDOR] -> {respuesta}")

    except ConnectionResetError:
        print("\nError: El servidor ha cerrado la conexión inesperadamente.")
    except Exception as e:
        print(f"\nOcurrió un error inesperado: {e}")
    finally:
        # Cierre ordenado de la conexión
        client_socket.close()

if __name__ == "__main__":
    iniciar_cliente()