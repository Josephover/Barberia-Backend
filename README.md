# 💈 Sistema de Reservas — Barbería (Backend)

API REST para gestionar citas de una barbería con múltiples barberos. El cliente elige un servicio, fecha y hora — el sistema le asigna automáticamente el primer barbero disponible en ese horario, sin que el cliente tenga que conocer la agenda de cada uno.

Muchas barberías coordinan citas por WhatsApp o a mano — este proyecto automatiza ese proceso con reglas de negocio reales: control de roles, validación de horarios, auto-asignación de barbero y prevención de doble reserva.

## 🧩 El problema técnico central

Dado un servicio, una fecha y una hora, el sistema debe encontrar automáticamente un barbero que:

1. Tenga ese horario dentro de su jornada laboral (`HorarioDisponible`).
2. No tenga ya otra cita activa que se solape con ese rango de tiempo (fórmula clásica de solapamiento de intervalos: `A.inicio < B.fin AND B.inicio < A.fin`).

El cliente nunca ve ni elige un barbero manualmente: el backend recorre los barberos activos y asigna al primero que cumpla ambas condiciones. Si ninguno está disponible, se lo informa con un mensaje claro en vez de dejarlo adivinar horarios por prueba y error.

Ambas validaciones corren dentro de una transacción (`@Transactional`) para reducir el riesgo de condiciones de carrera si dos personas reservan casi al mismo tiempo, y están cubiertas por tests unitarios que no dependen de la base de datos.

## 🛠️ Stack técnico

**Backend**
- Java 21 + Spring Boot
- Spring Security + JWT (autenticación sin estado)
- Spring Data JPA + PostgreSQL
- RBAC por rol (`@PreAuthorize`) y por dueño del recurso (ej. solo el barbero asignado puede completar su propia cita)
- JUnit 5 + Mockito (tests unitarios)
- Docker + Docker Compose

**Roles del sistema**
| Rol | Puede hacer |
|---|---|
| `ADMIN` | Gestionar barberos, servicios y horarios laborales |
| `BARBERO` | Ver su propia agenda, marcar sus citas como completadas |
| `CLIENTE` | Reservar citas (con auto-asignación), ver su historial, cancelar sus propias citas |

## 📐 Modelo de datos

```
Usuario (1) ──< (N) Cita >── (1) Barbero
                    │
                    └──< (1) Servicio

Barbero (1) ──< (N) HorarioDisponible
```

**Entidades principales:** `Usuario`, `Servicio`, `Barbero`, `HorarioDisponible`, `Cita`.

## 🔌 Endpoints principales

| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|----------------|-------------|
| POST | `/auth/register` | Público | Registro de usuario (rol `CLIENTE` desde el frontend público) |
| POST | `/auth/login` | Público | Login, devuelve JWT |
| GET | `/servicios` | Público | Lista de servicios activos |
| POST | `/servicios` | ADMIN | Crear servicio |
| POST | `/barberos/{usuarioId}` | ADMIN | Convertir un usuario en barbero |
| POST | `/horarios/barbero/{barberoId}` | ADMIN | Cargar horario laboral de un barbero |
| GET | `/citas/disponible` | CLIENTE | Vista previa: qué barbero quedaría asignado para un servicio + fecha/hora dados (no crea la cita) |
| POST | `/citas/auto` | CLIENTE | Crear una reserva con auto-asignación de barbero |
| POST | `/citas` | CLIENTE | Crear una reserva eligiendo un barbero específico (uso alternativo/administrativo) |
| GET | `/citas/mias` | CLIENTE | Historial del cliente autenticado |
| GET | `/citas/agenda` | BARBERO | Agenda del barbero autenticado |
| PATCH | `/citas/{id}/cancelar` | CLIENTE (dueño) / ADMIN | Cancelar una cita |
| PATCH | `/citas/{id}/completar` | BARBERO (asignado) | Marcar cita como completada |

## 🚀 Cómo correrlo localmente

Requiere [Docker](https://www.docker.com/).

### Opción A — solo el backend (con su propia base de datos)

```bash
git clone https://github.com/Josephover/barberia-backend.git
cd barberia-backend
docker compose up --build
```

La API queda disponible en `http://localhost:8080`.

### Opción B — stack completo (backend + frontend + base de datos)

Ver el [repositorio del frontend](https://github.com/Josephover/barberia-frontend) para el `docker-compose.yml` que orquesta los tres servicios juntos.

### Probar la API

1. Registra un usuario administrador:
```bash
POST http://localhost:8080/auth/register
{
  "nombre": "Admin",
  "email": "admin@barberia.com",
  "password": "admin123",
  "telefono": "0999999999",
  "rol": "ADMIN"
}
```

2. Con el token devuelto, crea un servicio, convierte un usuario en barbero y cárgale un horario.
3. Registra un cliente y prueba `POST /citas/auto` — el sistema le asignará barbero automáticamente.

## ✅ Tests

```bash
./mvnw test
```

Cubren `CitaService` de forma aislada (con mocks de los repositorios): creación exitosa, rechazo por solapamiento, rechazo por horario fuera de servicio, y manejo de barbero inexistente.

## 🗺️ Roadmap

- [x] Auto-asignación de barbero disponible
- [ ] Notificación por email al confirmar una cita
- [ ] Deploy en producción (Render)

## 👤 Autor

Joseph André Sánchez Verdesoto
[GitHub](https://github.com/Josephover) · [LinkedIn](https://linkedin.com/in/joseph-sánchez-b83211188)