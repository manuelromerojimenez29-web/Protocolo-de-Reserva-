# cliente
import socket
import sys
import struct
from datetime import datetime

HOST = '127.0.0.1'
PORT = 65432

# Códigos
CMD_LIST = 1; CMD_BOOK = 2; CMD_CONFIRM = 3; CMD_CANCEL = 4
STATUS_OK = 0; STATUS_ERR = 1; STATUS_NONE = 2

def pedir_fecha_hora():
    while True:
        fecha_str = input("\nIntroduce fecha y hora de la sesión (ej. 2024-10-25 18:00): ")
        try:
            dt = datetime.strptime(fecha_str, "%Y-%m-%d %H:%M")
            
            # --- NUEVA LÓGICA DE VALIDACIÓN DE MINUTOS ---
            if dt.minute not in (0, 30):
                print("\n[ERROR] Horario inválido. Las reservas solo pueden hacerse a las en punto (:00) o a y media (:30).")
                continue # Vuelve a pedir la fecha sin romper el bucle
            # ---------------------------------------------
            
            return int(dt.timestamp()) # Convertimos a segundos (entero de 32 bits)
        except ValueError:
            print("\n[ERROR] Formato incorrecto. Usa AAAA-MM-DD HH:MM")

def enviar_comando(sock, cmd, ts, asiento=0, id_res=0):
    # Empaquetamos y enviamos: 10 bytes
    paquete = struct.pack('! B I B I', cmd, ts, asiento, id_res)
    sock.sendall(paquete)
    
    # Recibimos respuesta: 9 bytes
    respuesta = sock.recv(9)
    if len(respuesta) == 9:
        return struct.unpack('! B I I', respuesta)
    return None

def iniciar_cliente():
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((HOST, PORT))
        print(f"Conectado en modo BINARIO al servidor {HOST}:{PORT}")
    except ConnectionRefusedError:
        print("Error: Servidor no disponible.")
        sys.exit()

    try:
        ts_actual = pedir_fecha_hora()

        while True:
            # Variable para controlar si mostramos el menú de salida
            mostrar_submenu = False 

            print("\n--- RESERVAS BINARIAS ---")
            print("1. Consultar disponibilidad")
            print("2. Reservar un asiento")
            print("3. Confirmar reserva (Pagar)")
            print("4. Cancelar reserva")
            print("5. Cambiar fecha/hora de búsqueda")
            print("6. Salir")
            opcion = input("Elige (1-6): ")

            if opcion == '1':
                estado, p1, p2 = enviar_comando(sock, CMD_LIST, ts_actual)
                if estado == STATUS_NONE:
                    print("\n[AVISO] Lo sentimos, no hay asientos disponibles para esta sesión.")
                elif estado == STATUS_OK:
                    libres = [i for i in range(1, 5) if (p1 & (1 << i))]
                    print(f"\n[OK] Asientos disponibles: {libres}")

            elif opcion == '2':
                try:
                    asiento = int(input("Número de asiento a reservar (1-4): "))
                    estado, id_res, tiempo = enviar_comando(sock, CMD_BOOK, ts_actual, asiento=asiento)
                    if estado == STATUS_OK:
                        print(f"\n[ÉXITO] Asiento {asiento} BLOQUEADO.")
                        print(f"Tu ID temporal es: {id_res}")
                        print(f"⚠️ TIENES {tiempo} SEGUNDOS PARA CONFIRMAR O SE LIBERARÁ.")
                    else:
                        print(f"\n[ERROR] No se pudo reservar. Código de error del servidor: {p1}")
                except ValueError:
                    print("\n[ERROR] Debes introducir un número válido.")

            elif opcion == '3':
                try:
                    id_res = int(input("Introduce el ID de reserva a confirmar: "))
                    estado, p1, p2 = enviar_comando(sock, CMD_CONFIRM, ts_actual, id_res=id_res)
                    if estado == STATUS_OK:
                        print("\n[ÉXITO] Reserva CONFIRMADA permanentemente.")
                    else:
                        print("\n[ERROR] ID incorrecto o el tiempo de 60 segundos expiró.")
                    
                    # ¡AQUÍ activamos el sub-menú porque el usuario ya terminó o falló la confirmación!
                    mostrar_submenu = True 

                except ValueError:
                    print("\n[ERROR] Debes introducir un número de ID válido.")

            elif opcion == '4':
                try:
                    id_res = int(input("Introduce el ID de reserva a cancelar: "))
                    estado, p1, p2 = enviar_comando(sock, CMD_CANCEL, ts_actual, id_res=id_res)
                    print("\n[ÉXITO] Reserva cancelada." if estado == STATUS_OK else "\n[ERROR] No se pudo cancelar.")
                except ValueError:
                    print("\n[ERROR] Debes introducir un número de ID válido.")

            elif opcion == '5':
                ts_actual = pedir_fecha_hora()
            
            elif opcion == '6':
                break
            else:
                print("\n[ERROR] Opción no válida.")
                continue

            # --- SUB-MENÚ DE PAUSA CONDICIONAL ---
            if mostrar_submenu:
                salir_del_programa = False
                while True:
                    print("\n¿Qué deseas hacer ahora?")
                    print("1. Volver al menú principal")
                    print("2. Salir")
                    sub_opcion = input("Elige (1-2): ")
                    
                    if sub_opcion == '1':
                        break # Vuelve al bucle principal
                    elif sub_opcion == '2':
                        salir_del_programa = True
                        break # Rompe para salir
                    else:
                        print("Opción no válida.")
                
                if salir_del_programa:
                    break

    finally:
        sock.close()
        print("Cerrando cliente...")

if __name__ == "__main__":
    iniciar_cliente()