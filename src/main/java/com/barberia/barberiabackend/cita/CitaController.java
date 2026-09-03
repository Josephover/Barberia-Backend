package com.barberia.barberiabackend.cita;

import com.barberia.barberiabackend.barbero.Barbero;
import com.barberia.barberiabackend.barbero.BarberoRepository;
import com.barberia.barberiabackend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/citas")
@RequiredArgsConstructor
public class CitaController {

    private final CitaService citaService;
    private final CitaRepository citaRepository;
    private final BarberoRepository barberoRepository;

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public Cita crear(
            @AuthenticationPrincipal Usuario cliente,
            @RequestBody CrearCitaRequest request) {
        return citaService.crearCita(
                cliente,
                request.getBarberoId(),
                request.getServicioId(),
                request.getFechaHora());
    }

    @PostMapping("/auto")
    @PreAuthorize("hasRole('CLIENTE')")
    public Cita crearAuto(
            @AuthenticationPrincipal Usuario cliente,
            @RequestBody CrearCitaAutoRequest request) {
        return citaService.crearCitaAutoAsignada(cliente, request.getServicioId(), request.getFechaHora());
    }

    @GetMapping("/mias")
    @PreAuthorize("hasRole('CLIENTE')")
    public List<Cita> misCitas(@AuthenticationPrincipal Usuario cliente) {
        return citaRepository.findByClienteId(cliente.getId());
    }

    @GetMapping("/disponible")
    @PreAuthorize("hasRole('CLIENTE')")
    public BarberoDisponibleResponse buscarDisponible(
            @RequestParam Long servicioId,
            @RequestParam String fechaHora) {
        Barbero barbero = citaService.buscarBarberoDisponible(servicioId, LocalDateTime.parse(fechaHora));
        return new BarberoDisponibleResponse(barbero.getId(), barbero.getUsuario().getNombre());
    }

    @GetMapping("/agenda")
    @PreAuthorize("hasRole('BARBERO')")
    public List<Cita> miAgenda(@AuthenticationPrincipal Usuario usuario) {
        Barbero barbero = barberoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new RuntimeException("Este usuario no tiene perfil de barbero"));

        return citaRepository.findByBarberoId(barbero.getId());
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN')")
    public Cita cancelar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        return citaService.cancelarCita(id, usuario);
    }

    @PatchMapping("/{id}/completar")
    @PreAuthorize("hasRole('BARBERO')")
    public Cita completar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        return citaService.completarCita(id, usuario);
    }
}