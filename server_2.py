import socket
import threading
import random
import logging
import struct
import time

# Configuración
HOST = '0.0.0.0'  # 0.0.0.0 permite recibir conexiones de cualquier IP de la red local
PORT = 65432
TIEMPO_EXPIRACION = 60.0  # 60 segundos para confirmar

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# Inventario estructurado: inventario[timestamp_sesion][id_asiento] = {estado, id_reserva, timer}
inventario = {}
reservas_activas = {}  # id_reserva -> (timestamp, id_asiento)
inventario_lock = threading.Lock()

# Códigos de Protocolo
CMD_LIST = 1; CMD_BOOK = 2; CMD_CONFIRM = 3; CMD_CANCEL = 4
STATUS_OK = 0; STATUS_ERR = 1; STATUS_NONE = 2

# --- NUEVA FUNCIÓN: AUTO-DESCUBRIMIENTO UDP ---
def responder_descubrimiento():
    # Creamos un socket UDP
    desc_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    desc_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    # Escuchamos en el puerto 50000 de cualquier IP
    desc_sock.bind(('0.0.0.0', 50000)) 
    logging.info("Servidor UDP escuchando gritos de descubrimiento en el puerto 50000...")
    
    while True:
        try:
            data, addr = desc_sock.recvfrom(1024)
            if data == b"BUSCANDO_SERVIDOR":
                # Le respondemos a la IP que ha gritado
                desc_sock.sendto(b"AQUI_ESTOY", addr)
        except Exception as e:
            logging.error(f"Error en descubrimiento UDP: {e}")
# ----------------------------------------------

def inicializar_sesion(ts):
    """Crea los 4 asientos para un timestamp específico si no existen."""
    if ts not in inventario:
        inventario[ts] = {
            i: {'estado': 'LIBRE', 'id_reserva': 0, 'timer': None} for i in range(1, 5)
        }

def expirar_reserva(ts, asiento_id, id_reserva):
    """Libera el asiento si el temporizador llega a cero sin confirmación."""
    with inventario_lock:
        try:
            asiento = inventario[ts][asiento_id]
            if asiento['estado'] == 'BLOQUEADO' and asiento['id_reserva'] == id_reserva:
                asiento['estado'] = 'LIBRE'
                asiento['id_reserva'] = 0
                asiento['timer'] = None
                if id_reserva in reservas_activas:
                    del reservas_activas[id_reserva]
                logging.info(f"Tiempo agotado. Asiento {asiento_id} liberado para la sesión {ts}.")
        except KeyError:
            pass

def procesar_mensaje(data, addr):
    """Desempaqueta el mensaje binario, procesa la lógica y empaqueta la respuesta."""
    comando, ts, asiento_id, id_reserva = struct.unpack('! B I B I', data)
    
    with inventario_lock:
        inicializar_sesion(ts)
        sesion = inventario[ts]

        if comando == CMD_LIST:
            disponibles_mask = 0
            asientos_libres = 0
            for a_id, datos in sesion.items():
                if datos['estado'] == 'LIBRE':
                    disponibles_mask |= (1 << a_id)
                    asientos_libres += 1
            
            if asientos_libres == 0:
                return struct.pack('! B I I', STATUS_NONE, 0, 0)
            return struct.pack('! B I I', STATUS_OK, disponibles_mask, 0)

        elif comando == CMD_BOOK:
            if asiento_id < 1 or asiento_id > 4:
                return struct.pack('! B I I', STATUS_ERR, 1, 0)
            
            asiento = sesion[asiento_id]
            if asiento['estado'] != 'LIBRE':
                return struct.pack('! B I I', STATUS_ERR, 2, 0)
            
            nuevo_id = random.randint(1000, 999999)
            asiento['estado'] = 'BLOQUEADO'
            asiento['id_reserva'] = nuevo_id
            reservas_activas[nuevo_id] = (ts, asiento_id)
            
            t = threading.Timer(TIEMPO_EXPIRACION, expirar_reserva, args=(ts, asiento_id, nuevo_id))
            asiento['timer'] = t
            t.start()
            
            logging.info(f"[{addr}] BLOQUEADO: Asiento {asiento_id} -> ID: {nuevo_id}")
            return struct.pack('! B I I', STATUS_OK, nuevo_id, int(TIEMPO_EXPIRACION))

        elif comando == CMD_CONFIRM:
            if id_reserva not in reservas_activas:
                return struct.pack('! B I I', STATUS_ERR, 3, 0)
            
            ts_res, a_id = reservas_activas[id_reserva]
            asiento = inventario[ts_res][a_id]
            
            if asiento['timer']:
                asiento['timer'].cancel()
            
            asiento['estado'] = 'CONFIRMADO'
            logging.info(f"[{addr}] CONFIRMADO: ID {id_reserva}")
            return struct.pack('! B I I', STATUS_OK, id_reserva, 0)

        elif comando == CMD_CANCEL:
            if id_reserva not in reservas_activas:
                return struct.pack('! B I I', STATUS_ERR, 3, 0)
            
            ts_res, a_id = reservas_activas[id_reserva]
            asiento = inventario[ts_res][a_id]
            
            if asiento['timer']:
                asiento['timer'].cancel()
                
            asiento['estado'] = 'LIBRE'
            asiento['id_reserva'] = 0
            asiento['timer'] = None
            del reservas_activas[id_reserva]
            
            logging.info(f"[{addr}] CANCELADO: ID {id_reserva}")
            return struct.pack('! B I I', STATUS_OK, id_reserva, 0)

    return struct.pack('! B I I', STATUS_ERR, 99, 0)

def manejar_cliente(conn, addr):
    try:
        with conn:
            while True:
                # Búfer para asegurar que leemos exactamente 10 bytes
                data = b''
                while len(data) < 10:
                    chunk = conn.recv(10 - len(data))
                    if not chunk:
                        break # El cliente se desconectó limpiamente
                    data += chunk
                
                # Si salió del bucle y la trama está incompleta, cerramos
                if len(data) < 10:
                    break
                    
                respuesta_binaria = procesar_mensaje(data, addr)
                conn.sendall(respuesta_binaria)
    except Exception as e:
        logging.error(f"Error con {addr}: {e}")

def iniciar_servidor():
    # Lanzamos el hilo que responde al grito UDP del cliente ANTES de abrir el servidor TCP
    threading.Thread(target=responder_descubrimiento, daemon=True).start()

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server_socket:
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_socket.bind((HOST, PORT))
        server_socket.listen()
        logging.info(f"Servidor TCP BINARIO escuchando peticiones en el puerto {PORT}...")
        while True:
            conn, addr = server_socket.accept()
            threading.Thread(target=manejar_cliente, args=(conn, addr), daemon=True).start()

if __name__ == "__main__":
    iniciar_servidor()