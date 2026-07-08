package com.flowops.dto.user;

import com.flowops.entity.User;

import java.util.UUID;

// DTO minimo para dropdowns (ex: atribuir responsavel a uma WorkOrder).
// Nunca expor email, role completo ou qualquer dado sensivel aqui -
// so o suficiente para identificar a pessoa na interface.
public record UserOption(UUID uuid, String name) {
    public static UserOption from(User user) {
        return new UserOption(user.getUuid(), user.getName());
    }
}
