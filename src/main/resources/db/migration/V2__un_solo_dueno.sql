-- Un solo OWNER por snippet, sostenido por el motor.
--
-- El servicio chequea "¿ya tiene dueño?" antes de insertar, pero el chequeo y el INSERT son dos
-- statements: dos usuarios que se registran como dueños a la vez pasan los dos el chequeo, y como
-- la clave es (snippet_id, user_id), no chocan entre sí. El índice cierra esa ventana: el segundo
-- INSERT falla. Es parcial (WHERE role = 'OWNER') porque lectores puede haber muchos.
CREATE UNIQUE INDEX snippet_permission_un_solo_dueno ON snippet_permission (snippet_id) WHERE role = 'OWNER';
