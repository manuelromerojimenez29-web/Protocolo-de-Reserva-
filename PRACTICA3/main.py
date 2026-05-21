import threading
import random
import logging
from datetime import datetime  
from typing import Dict
from fastapi import FastAPI, HTTPException, status, BackgroundTasks
from pydantic import BaseModel, Field

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

TIEMPO_EXPIRACION = 60.0  # 60 segundos para confirmar

inventario: Dict[str, Dict[int, dict]] = {}
reservas_activas: Dict[int, tuple] = {}
inventario_lock = threading.Lock()

def inicializar_sesion(fecha_sesion: str):
    """Crea los 4 asientos para una fecha específica si no existen."""
    if fecha_sesion not in inventario:
        inventario[fecha_sesion] = {
            i: {'estado': 'LIBRE', 'id_reserva': 0, 'timer': None} for i in range(1, 5)
        }

def expirar_reserva_rest(fecha_sesion: str, asiento_id: int, id_reserva: int):
    """Libera el asiento tras 60 segundos si no se ha confirmado."""
    with inventario_lock:
        try:
            asiento = inventario[fecha_sesion][asiento_id]
            if asiento['estado'] == 'BLOQUEADO' and asiento['id_reserva'] == id_reserva:
                asiento['estado'] = 'LIBRE'
                asiento['id_reserva'] = 0
                asiento['timer'] = None
                if id_reserva in reservas_activas:
                    del reservas_activas[id_reserva]
                logging.info(f"[EXPIRACIÓN] Tiempo agotado. Asiento {asiento_id} liberado en sesión {fecha_sesion}.")
        except KeyError:
            pass

def validar_fecha_y_hora(fecha_sesion: str):
    """
    Valida rigurosamente que:
    1. El formato sea estrictamente AAAA-MM-DD HH:MM
    2. Sea una fecha real del calendario (evita meses como el 222 o días como el 35)
    3. Los minutos sean obligatoriamente :00 o :30 (regla de tu protocolo original)
    """
    try:
        # Intenta parsear la fecha real del calendario
        dt = datetime.strptime(fecha_sesion, "%Y-%m-%d %H:%M")
    except ValueError:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Formato de fecha incorrecto o fecha inválida. Debe ser estrictamente AAAA-MM-DD HH:MM (ej. 2026-05-19 18:00)."
        )

    # Verifica la regla de los minutos de tu práctica
    if dt.minute not in (0, 30):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Horario inválido. Las reservas solo pueden hacerse a las en punto (:00) o a y media (:30)."
        )

# --- MODELOS DE DATOS ---
class ReservaRequest(BaseModel):
    fecha_sesion: str = Field(..., description="Fecha y hora (AAAA-MM-DD HH:MM)", example="2026-05-19 18:00")
    asiento_id: int = Field(..., description="Número de asiento del 1 al 4", ge=1, le=4, example=2)

class ReservaResponse(BaseModel):
    id_reserva: int
    fecha_sesion: str
    asiento_id: int
    estado: str
    tiempo_expiracion: int

class ConfirmacionRequest(BaseModel):
    estado: str = Field(..., description="Debe ser 'CONFIRMADO'", example="CONFIRMADO")

# --- INSTANCIA DE FASTAPI ---
app = FastAPI(title="API REST Reservas (Validación Estricta)", version="1.1.0")

# --- ENDPOINTS ---

@app.get("/sessions/{fecha_sesion}/seats")
def consultar_disponibilidad(fecha_sesion: str):
    validar_fecha_y_hora(fecha_sesion)  # Validamos antes de tocar el inventario
    with inventario_lock:
        inicializar_sesion(fecha_sesion)
        sesion = inventario[fecha_sesion]
        
        asientos_resultado = [{"asiento_id": a_id, "estado": datos['estado']} for a_id, datos in sesion.items()]
            
    return {"fecha_sesion": fecha_sesion, "asientos": asientos_resultado}


@app.post("/reservations", status_code=status.HTTP_201_CREATED, response_model=ReservaResponse)
def reservar_asiento(payload: ReservaRequest, background_tasks: BackgroundTasks):
    validar_fecha_y_hora(payload.fecha_sesion)  # Validamos antes de bloquear nada
    
    with inventario_lock:
        inicializar_sesion(payload.fecha_sesion)
        sesion = inventario[payload.fecha_sesion]
        asiento = sesion[payload.asiento_id]
        
        if asiento['estado'] != 'LIBRE':
            raise HTTPException(status_code=400, detail=f"El asiento {payload.asiento_id} no está libre.")
            
        nuevo_id = random.randint(1000, 999999)
        asiento['estado'] = 'BLOQUEADO'
        asiento['id_reserva'] = nuevo_id
        reservas_activas[nuevo_id] = (payload.fecha_sesion, payload.asiento_id)
        
        import asyncio
        async def delay_expiracion():
            await asyncio.sleep(TIEMPO_EXPIRACION)
            expirar_reserva_rest(payload.fecha_sesion, payload.asiento_id, nuevo_id)
            
        background_tasks.add_task(delay_expiracion)
        logging.info(f"[REST] Asiento {payload.asiento_id} BLOQUEADO en {payload.fecha_sesion}.")
        
        return {
            "id_reserva": nuevo_id,
            "fecha_sesion": payload.fecha_sesion,
            "asiento_id": payload.asiento_id,
            "estado": "BLOQUEADO",
            "tiempo_expiracion": int(TIEMPO_EXPIRACION)
        }

@app.patch("/reservations/{id_reserva}")
def confirmar_reserva(id_reserva: int, payload: ConfirmacionRequest):
    if payload.estado != "CONFIRMADO":
        raise HTTPException(status_code=400, detail="Estado inválido.")
        
    with inventario_lock:
        if id_reserva not in reservas_activas:
            raise HTTPException(status_code=404, detail="Reserva no existe o expiró.")
            
        fecha_res, a_id = reservas_activas[id_reserva]
        asiento = inventario[fecha_res][a_id]
        
        if asiento['estado'] == 'CONFIRMADO':
            return {"detail": "Ya estaba confirmada.", "id_reserva": id_reserva}
            
        asiento['estado'] = 'CONFIRMADO'
        return {"id_reserva": id_reserva, "asiento_id": a_id, "estado": "CONFIRMADO"}

@app.delete("/reservations/{id_reserva}")
def cancelar_reserva(id_reserva: int):
    with inventario_lock:
        if id_reserva not in reservas_activas:
            raise HTTPException(status_code=404, detail="Reserva no existe.")
            
        fecha_res, a_id = reservas_activas[id_reserva]
        asiento = inventario[fecha_res][a_id]
        
        asiento['estado'] = 'LIBRE'
        asiento['id_reserva'] = 0
        del reservas_activas[id_reserva]
        
        return {"detail": f"Reserva {id_reserva} cancelada."}
    
    
#Activate 
#uvicorn main:app --host 0.0.0.0 --port 8000
#http://localhost:8000/docs
#Cliente http://192.168.158.164:8000/docs