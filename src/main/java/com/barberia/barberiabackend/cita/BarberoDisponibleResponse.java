package com.barberia.barberiabackend.cita;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BarberoDisponibleResponse {
    private Long id;
    private String nombre;
}