# Práctica: Protocolo de Reservas con Control de Concurrencia (Caso de Uso 5)

**Autores:** 
* Manuel Romero Jiménez
* Francisco Javier Jiménez Villatoro
* Vittorio Maci

---

## 1. Descripción del Protocolo
Este proyecto implementa un sistema de reservas de recursos limitados en red. El protocolo permite a múltiples clientes consultar el inventario disponible y formalizar reservas de manera consistente. El servidor actúa como la única entidad responsable de mantener el estado global de los recursos, garantizando una consistencia fuerte y evitando la asignación duplicada de un mismo recurso bajo condiciones de alta concurrencia.

## 2. Arquitectura
El sistema se basa en una arquitectura **Cliente-Servidor** utilizando el protocolo de transporte **TCP** mediante la API de sockets nativa. 

Para gestionar la concurrencia exigida por el caso de uso, el servidor implementa un modelo multihilo (`threading`), asignando un hilo de ejecución independiente a cada cliente conectado. Para proteger la integridad del inventario de recursos compartidos y evitar condiciones de carrera durante las operaciones de reserva y cancelación, se utiliza un mecanismo de exclusión mutua mediante un cerrojo (`Lock`).

## 3. Especificación Formal (ABNF)
La comunicación entre cliente y servidor se rige estrictamente por la siguiente gramática ABNF:

```abnf
; --- MENSAJES DEL CLIENTE (REQUESTS) ---

request = (cmd-list / cmd-book / cmd-cancel) LF

cmd-list   = "LIST"
cmd-book   = "BOOK" DELIMITER recurso
cmd-cancel = "CANCEL" DELIMITER id-reserva

recurso    = 1*VCHAR              ; Cualquier cadena de caracteres visibles (ej. ASIENTO_1)
id-reserva = 8HEXDIG              ; 8 caracteres hexadecimales (generados por UUID)
DELIMITER  = "|"                  ; Carácter separador
LF         = %x0A                 ; Salto de línea (\n)


; --- MENSAJES DEL SERVIDOR (RESPONSES) ---

response = (resp-ok / resp-err) LF

resp-ok  = "OK|" (res-list / res-book / res-cancel)
resp-err = "ERR|" razon-error

res-list   = "DISPONIBLES:" [recurso *("," recurso)]
res-book   = "RESERVADO|" id-reserva
res-cancel = "CANCELADO"

razon-error = 1*VCHAR             ; Texto descriptivo del error (ej. "Recurso no disponible") 
```

## 4. Requisitos e Instrucciones de Ejecución

### Requisitos del sistema
* **Python 3.x** instalado en ambas máquinas (o en la máquina local para pruebas).
* Conectividad de red habilitada entre el equipo que ejecuta el cliente y el servidor.

### Instrucciones de ejecución
El servidor debe iniciarse siempre antes que los clientes para habilitar el puerto de escucha.

1. **Lanzar el servidor:**
   Abra una terminal en el directorio raíz del proyecto y ejecute:
   ```bash
   python server.py
   ```
2. **Lanzar el cliente:**
   Abra una nueva terminal (puede ser en la misma máquina o en otra conectada a la red) y ejecute:
   ```bash
   python client.py
   ```

## 5. Ejemplos de uso

## 6. Capturas y Diagramas
