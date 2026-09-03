package com.barberia.barberiabackend.cita;

import com.barberia.barberiabackend.barbero.Barbero;
import com.barberia.barberiabackend.barbero.BarberoRepository;
import com.barberia.barberiabackend.barbero.HorarioDisponible;
import com.barberia.barberiabackend.barbero.HorarioDisponibleRepository;
import com.barberia.barberiabackend.servicio.Servicio;
import com.barberia.barberiabackend.servicio.ServicioRepository;
import com.barberia.barberiabackend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CitaService {

    private final CitaRepository citaRepository;
    private final BarberoRepository barberoRepository;
    private final ServicioRepository servicioRepository;
    private final HorarioDisponibleRepository horarioRepository;

    /**
     * Crea una cita con un barbero específico elegido por el cliente.
     * Se mantiene por si en el futuro quieres reactivar la selección manual
     * (por ejemplo, para un panel de administración).
     */
    @Transactional
    public Cita crearCita(Usuario cliente, Long barberoId, Long servicioId, LocalDateTime fechaHora) {
        Barbero barbero = barberoRepository.findById(barberoId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        Servicio servicio = servicioRepository.findById(servicioId)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

        if (!estaDentroDeHorarioLaboral(barbero, fechaHora, servicio.getDuracionMinutos())) {
            throw new IllegalArgumentException(
                    "El barbero no trabaja en ese horario (" + fechaHora.getDayOfWeek() + " " + fechaHora.toLocalTime()
                            + ")");
        }

        if (haySolapamiento(barbero, fechaHora, servicio.getDuracionMinutos())) {
            throw new IllegalArgumentException("El barbero ya tiene una cita en ese horario");
        }

        return guardarCita(cliente, barbero, servicio, fechaHora);
    }

    /**
     * Crea una cita SIN que el cliente elija barbero: el sistema recorre
     * los barberos activos y asigna automáticamente al primero que esté
     * libre y trabajando en ese horario.
     */
    @Transactional
    public Cita crearCitaAutoAsignada(Usuario cliente, Long servicioId, LocalDateTime fechaHora) {
        Servicio servicio = servicioRepository.findById(servicioId)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

        Barbero barbero = encontrarBarberoDisponible(servicio, fechaHora)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay barberos disponibles en ese horario. Prueba con otra fecha u hora."));

        return guardarCita(cliente, barbero, servicio, fechaHora);
    }

    @Transactional
    public Cita cancelarCita(Long citaId, Usuario solicitante) {
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada"));

        boolean esDueño = cita.getCliente().getId().equals(solicitante.getId());
        boolean esAdmin = solicitante.getRol().name().equals("ADMIN");

        if (!esDueño && !esAdmin) {
            throw new IllegalArgumentException("No puedes cancelar una cita que no es tuya");
        }

        if (cita.getEstado() == EstadoCita.COMPLETADA) {
            throw new IllegalArgumentException("No se puede cancelar una cita ya completada");
        }

        cita.setEstado(EstadoCita.CANCELADA);
        return citaRepository.save(cita);
    }

    @Transactional
    public Cita completarCita(Long citaId, Usuario barberoUsuario) {
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada"));

        if (!cita.getBarbero().getUsuario().getId().equals(barberoUsuario.getId())) {
            throw new IllegalArgumentException("No puedes completar una cita que no es tuya");
        }

        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new IllegalArgumentException("No se puede completar una cita cancelada");
        }

        cita.setEstado(EstadoCita.COMPLETADA);
        return citaRepository.save(cita);
    }

    // ---------------------------------------------------------------
    // Helpers privados
    // ---------------------------------------------------------------

    private Cita guardarCita(Usuario cliente, Barbero barbero, Servicio servicio, LocalDateTime fechaHora) {
        Cita cita = new Cita();
        cita.setCliente(cliente);
        cita.setBarbero(barbero);
        cita.setServicio(servicio);
        cita.setFechaHora(fechaHora);
        cita.setEstado(EstadoCita.PENDIENTE);
        return citaRepository.save(cita);
    }

    public Barbero buscarBarberoDisponible(Long servicioId, LocalDateTime fechaHora) {
        Servicio servicio = servicioRepository.findById(servicioId)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

        return encontrarBarberoDisponible(servicio, fechaHora)
                .orElseThrow(() -> new IllegalArgumentException("No hay barberos disponibles en ese horario"));
    }

    private Optional<Barbero> encontrarBarberoDisponible(Servicio servicio, LocalDateTime fechaHora) {
        List<Barbero> barberosActivos = barberoRepository.findAll().stream()
                .filter(Barbero::getActivo)
                .toList();

        return barberosActivos.stream()
                .filter(b -> estaDentroDeHorarioLaboral(b, fechaHora, servicio.getDuracionMinutos())
                        && !haySolapamiento(b, fechaHora, servicio.getDuracionMinutos()))
                .findFirst();
    }

    private boolean estaDentroDeHorarioLaboral(Barbero barbero, LocalDateTime fechaHora, int duracionMinutos) {
        List<HorarioDisponible> horarios = horarioRepository
                .findByBarberoIdAndDiaSemana(barbero.getId(), fechaHora.getDayOfWeek());

        LocalTime horaInicioCita = fechaHora.toLocalTime();
        LocalTime horaFinCita = fechaHora.plusMinutes(duracionMinutos).toLocalTime();

        return horarios.stream()
                .anyMatch(h -> !horaInicioCita.isBefore(h.getHoraInicio()) && !horaFinCita.isAfter(h.getHoraFin()));
    }

    private boolean haySolapamiento(Barbero barbero, LocalDateTime fechaHora, int duracionMinutos) {
        LocalDateTime finNuevaCita = fechaHora.plusMinutes(duracionMinutos);

        List<Cita> citasDelDia = citaRepository.findByBarberoIdAndFechaHoraBetweenAndEstadoNot(
                barbero.getId(),
                fechaHora.toLocalDate().atStartOfDay(),
                fechaHora.toLocalDate().atTime(23, 59, 59),
                EstadoCita.CANCELADA);

        return citasDelDia.stream().anyMatch(citaExistente -> {
            LocalDateTime inicioExistente = citaExistente.getFechaHora();
            LocalDateTime finExistente = inicioExistente.plusMinutes(
                    citaExistente.getServicio().getDuracionMinutos());
            return fechaHora.isBefore(finExistente) && inicioExistente.isBefore(finNuevaCita);
        });
    }
}