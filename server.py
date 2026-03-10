import socket
import threading
import uuid
import logging

# Configuración del servidor
HOST = '127.0.0.1'  # Localhost
PORT = 65432        # Puerto de escucha

# Configuración del log interno
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# Estado global del inventario (Recurso -> ID_Reserva o None si está libre)
recursos = {
    'ASIENTO_1': None,
    'ASIENTO_2': None,
    'ASIENTO_3': None,
    'ASIENTO_4': None
}

# Diccionario para mapear ID_Reserva -> Recurso
reservas_activas = {}

# Cerrojo (Lock) para garantizar la exclusión mutua y evitar condiciones de carrera
inventario_lock = threading.Lock()

def procesar_mensaje(mensaje, addr):
    """Procesa el mensaje del protocolo de aplicación y devuelve una respuesta."""
    partes = mensaje.strip().split('|')
    comando = partes[0].upper()

    if comando == 'LIST':
        # Consulta de disponibilidad
        with inventario_lock:
            disponibles = [res for res, estado in recursos.items() if estado is None]
        logging.info(f"[{addr}] Consulta de disponibilidad. Libres: {len(disponibles)}")
        return f"OK|DISPONIBLES:{','.join(disponibles)}\n"

    elif comando == 'BOOK':
        # Solicitud de reserva: BOOK|RECURSO
        if len(partes) < 2:
            return "ERR|Faltan parametros. Uso: BOOK|RECURSO\n"
        
        recurso_solicitado = partes[1]
        
        with inventario_lock:  # Entramos en la sección crítica
            if recurso_solicitado not in recursos:
                logging.warning(f"[{addr}] Intento de reserva de recurso inexistente: {recurso_solicitado}")
                return "ERR|Recurso inexistente\n"
            
            if recursos[recurso_solicitado] is not None:
                logging.warning(f"[{addr}] Intento de reserva de recurso ocupado: {recurso_solicitado}")
                return "ERR|Recurso no disponible\n"
            
            # Realizamos la reserva
            id_reserva = str(uuid.uuid4())[:8] # Generamos un ID único corto
            recursos[recurso_solicitado] = id_reserva
            reservas_activas[id_reserva] = recurso_solicitado
            
            logging.info(f"[{addr}] RESERVA EXITOSA: {recurso_solicitado} -> ID: {id_reserva}")
            return f"OK|RESERVADO|{id_reserva}\n"

    elif comando == 'CANCEL':
        # Cancelación explícita: CANCEL|ID_RESERVA
        if len(partes) < 2:
            return "ERR|Faltan parametros. Uso: CANCEL|ID_RESERVA\n"
        
        id_reserva = partes[1]
        
        with inventario_lock:
            if id_reserva not in reservas_activas:
                logging.warning(f"[{addr}] Intento de cancelación fallida. ID no existe: {id_reserva}")
                return "ERR|Reserva inexistente\n"
            
            # Liberamos el recurso
            recurso_liberado = reservas_activas[id_reserva]
            recursos[recurso_liberado] = None
            del reservas_activas[id_reserva]
            
            logging.info(f"[{addr}] CANCELACION EXITOSA: ID {id_reserva}. Recurso liberado: {recurso_liberado}")
            return "OK|CANCELADO\n"

    else:
        logging.warning(f"[{addr}] Comando no reconocido: {comando}")
        return "ERR|Comando no reconocido\n"

def manejar_cliente(conn, addr):
    """Maneja la conexión de un cliente de forma independiente."""
    logging.info(f"Nuevo cliente conectado: {addr}")
    try:
        with conn:
            while True:
                data = conn.recv(1024)
                if not data:
                    break # El cliente cerró la conexión
                
                mensaje = data.decode('utf-8')
                respuesta = procesar_mensaje(mensaje, addr)
                conn.sendall(respuesta.encode('utf-8'))
                
    except ConnectionResetError:
        logging.warning(f"El cliente {addr} se desconectó abruptamente.")
    except Exception as e:
        logging.error(f"Error con el cliente {addr}: {e}")
    finally:
        logging.info(f"Conexión cerrada con: {addr}")

def iniciar_servidor():
    """Inicia el servidor en el puerto especificado y escucha conexiones."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server_socket:
        # Permite reutilizar la dirección/puerto inmediatamente después de cerrar el script
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_socket.bind((HOST, PORT))
        server_socket.listen()
        logging.info(f"Servidor TCP escuchando en {HOST}:{PORT}...")

        try:
            while True:
                conn, addr = server_socket.accept()
                # Creamos un hilo nuevo por cada cliente para soportar concurrencia
                hilo_cliente = threading.Thread(target=manejar_cliente, args=(conn, addr))
                hilo_cliente.start()
        except KeyboardInterrupt:
            logging.info("Apagando el servidor...")

if __name__ == "__main__":
    iniciar_servidor()