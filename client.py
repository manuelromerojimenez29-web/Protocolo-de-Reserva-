import socket
import sys
import struct
from datetime import datetime

PORT = 65432

# Códigos
CMD_LIST = 1; CMD_BOOK = 2; CMD_CONFIRM = 3; CMD_CANCEL = 4
STATUS_OK = 0; STATUS_ERR = 1; STATUS_NONE = 2

# Auto descubrimiento UDP 
def buscar_servidor_automaticamente():
    print("\nBuscando servidor en la red Wi-Fi...")
    desc_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    
    # Habilitamos el modo Broadcast
    desc_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    desc_sock.settimeout(5.0) # Esperamos 5 segundos máximo a que responda

    try:
        # <broadcast> es un atajo de Python para 255.255.255.255
        desc_sock.sendto(b"BUSCANDO_SERVIDOR", ('<broadcast>', 50000))
        
        # Escuchamos la respuesta del servidor
        data, addr = desc_sock.recvfrom(1024)
        if data == b"AQUI_ESTOY":
            ip_descubierta = addr[0]
            print(f"[ÉXITO] Servidor encontrado automáticamente en la IP: {ip_descubierta}\n")
            return ip_descubierta
            
    except socket.timeout:
        print("[ERROR] Nadie respondió. Asegúrate de que:")
        print("  1. El servidor está ejecutándose.")
        print("  2. Ambos estáis en la misma red Wi-Fi (Punto de acceso del móvil).")
        print("  3. El cortafuegos (Firewall) del servidor no está bloqueando Python.")
        return None
    finally:
        desc_sock.close()
# ----------------------------------------------

def pedir_fecha_hora():
    while True:
        fecha_str = input("\nIntroduce fecha y hora de la sesión (ej. 2024-10-25 18:00): ")
        try:
            dt = datetime.strptime(fecha_str, "%Y-%m-%d %H:%M")
            if dt.minute not in (0, 30):
                print("\n[ERROR] Horario inválido. Las reservas solo pueden hacerse a las en punto (:00) o a y media (:30).")
                continue
            return int(dt.timestamp())
        except ValueError:
            print("\n[ERROR] Formato incorrecto. Usa AAAA-MM-DD HH:MM")

def enviar_comando(sock, cmd, ts, asiento=0, id_res=0):
    # Empaquetamos y enviamos los 10 bytes
    paquete = struct.pack('! B I B I', cmd, ts, asiento, id_res)
    sock.sendall(paquete)
    
    # Búfer para asegurar que leemos exactamente 9 bytes
    respuesta = b''
    while len(respuesta) < 9:
        chunk = sock.recv(9 - len(respuesta))
        if not chunk:
            # Si se corta la conexión, devolvemos un código de error interno para no crashear
            return (STATUS_ERR, 99, 0) 
        respuesta += chunk
        
    return struct.unpack('! B I I', respuesta)

def iniciar_cliente():
    # HOST = buscar_servidor_automaticamente() # Descomentar si UDP 
    HOST = '192.168.14.164' # Ip manualmente, podriamos usar la automatica (puerto 5000 UDP)
    
    if not HOST:
        print("Cerrando programa...")
        sys.exit()

    # 1. PRIMERO PEDIMOS LOS DATOS (Para no hacer esperar a la red)
    ts_actual = pedir_fecha_hora()

    # 2. LUEGO NOS CONECTAMOS AL SERVIDOR
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((HOST, PORT))
        print(f"Conectado en modo BINARIO al servidor {HOST}:{PORT}")
    except ConnectionRefusedError:
        print("Error: Servidor no disponible.")
        sys.exit()

    # 3. ENTRAMOS AL MENÚ DIRECTAMENTE
    try:
        while True:
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
                # Si cambia la fecha, no pasa nada porque ya estamos chateando activamente con el servidor
                ts_actual = pedir_fecha_hora()
            
            elif opcion == '6':
                break
            else:
                print("\n[ERROR] Opción no válida.")
                continue

            if mostrar_submenu:
                salir_del_programa = False
                while True:
                    print("\n¿Qué deseas hacer ahora?")
                    print("1. Volver al menú principal")
                    print("2. Salir")
                    sub_opcion = input("Elige (1-2): ")
                    
                    if sub_opcion == '1':
                        break
                    elif sub_opcion == '2':
                        salir_del_programa = True
                        break
                    else:
                        print("Opción no válida.")
                
                if salir_del_programa:
                    break

    finally:
        sock.close()
        print("Cerrando cliente...")

if __name__ == "__main__":
    iniciar_cliente()