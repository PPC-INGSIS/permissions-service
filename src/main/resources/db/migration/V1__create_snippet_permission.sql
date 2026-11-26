CREATE TABLE snippet_permission (
    snippet_id UUID         NOT NULL,
    user_id    VARCHAR(255) NOT NULL,
    -- El CHECK es la misma regla que el enum Role, sostenida por el motor: un INSERT
    -- a mano o desde otro servicio tampoco puede dejar un rol que no existe.
    role       VARCHAR(20)  NOT NULL CHECK (role IN ('OWNER', 'READ')),
    PRIMARY KEY (snippet_id, user_id)
);

-- La clave primaria ya resuelve "que rol tiene este usuario sobre este snippet".
-- Este indice es para la otra consulta del servicio: "que snippets ve este usuario"
-- (US 5, 11, 12, 14 y 15), que sin el seria un scan de toda la tabla.
CREATE INDEX snippet_permission_user ON snippet_permission (user_id);
