package com.certisafe.security;

/**
 * Politica de contrasenas del sistema.
 *
 * <p>Vive en un unico sitio porque la misma regla se aplica al crear un usuario y al cambiar
 * la contrasena: dos copias del regex acabarian diciendo cosas distintas y una de las dos
 * rutas dejaria pasar una contrasena que la otra rechaza.</p>
 */
public final class PasswordPolicy {

    /** Al menos una minuscula, una mayuscula, un digito y un caracter no alfanumerico. */
    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$";

    /**
     * Mensaje unico que enumera los requisitos.
     *
     * <p>RNF-011 pide mensajes claros y RF-024 pide "indicar los requisitos": decir solo que
     * "la contrasena no cumple la politica" obliga al usuario a adivinar cual falta.</p>
     */
    public static final String MESSAGE = "La contraseña debe incluir mayúscula, minúscula, "
            + "dígito y carácter especial";

    public static final int MIN_LENGTH = 8;

    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }
}
