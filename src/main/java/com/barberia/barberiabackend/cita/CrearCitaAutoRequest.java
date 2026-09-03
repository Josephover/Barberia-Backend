package com.barberia.barberiabackend.cita;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CrearCitaAutoRequest {
    private Long servicioId;
    private LocalDateTime fechaHora;
}