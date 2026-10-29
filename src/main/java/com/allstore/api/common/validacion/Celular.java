package com.allstore.api.common.validacion;

import com.allstore.api.common.exception.BusinessException;
import org.springframework.util.StringUtils;

/** Celular para WhatsApp: con código de país y solo dígitos (ej. 51987654321). */
public final class Celular {

    private Celular() {
    }

    /**
     * Un celular peruano (9 dígitos que empiezan con 9) recibe el 51; uno de otro país debe venir
     * con su código (+ y de 10 a 15 dígitos).
     *
     * @throws BusinessException si no es un celular válido
     */
    public static String paraWhatsapp(String celular) {
        if (!StringUtils.hasText(celular)) {
            throw new BusinessException("Indica el celular del cliente para enviarle el comprobante por WhatsApp");
        }
        String digitos = celular.replaceAll("\\D", "");
        if (digitos.length() == 9 && digitos.startsWith("9")) {
            return "51" + digitos;
        }
        if (digitos.length() == 11 && digitos.startsWith("519")) {
            return digitos;
        }
        if (celular.trim().startsWith("+") && digitos.length() >= 10 && digitos.length() <= 15 && !digitos.startsWith("51")) {
            return digitos;
        }
        throw new BusinessException("El celular " + celular + " no es válido: usa 9 dígitos (ej. 987654321) o +código de país");
    }
}
