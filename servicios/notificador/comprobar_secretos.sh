#!/usr/bin/env bash
# Comprueba que el workflow «Enviar avisos» tiene sus cinco secretos.
# Si falta alguno, falla (exit 1) nombrando SOLO los que faltan; nunca muestra valores.
faltan=()
for nombre in SUPABASE_URL SUPABASE_ANON_KEY NOTIFICADOR_EMAIL NOTIFICADOR_CLAVE FIREBASE_CUENTA_SERVICIO; do
  valor="${!nombre}"
  if [ -z "${valor//[[:space:]]/}" ]; then faltan+=("$nombre"); fi
done
if [ ${#faltan[@]} -gt 0 ]; then
  lista="${faltan[*]}"
  mensaje="Faltan secretos de GitHub Actions: ${lista// /, }. Créalos en Settings → Secrets and variables → Actions (docs/entrega7/ENVIO_AUTOMATICO.md, paso B). No se envió ningún aviso."
  echo "::error title=Faltan secretos::${mensaje}"
  echo "${mensaje}" >&2
  exit 1
fi
echo "Los cinco secretos están configurados."
