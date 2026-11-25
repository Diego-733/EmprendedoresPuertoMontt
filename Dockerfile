# Vitrina web de Alerce para Coolify (u otro servidor con Docker).
# Coolify: «Build Pack» = Dockerfile, puerto 80. Variables de construcción (Build Variables):
#   SUPABASE_URL       URL https de Supabase
#   SUPABASE_ANON_KEY  clave PÚBLICA anon (nunca service_role: el script la rechaza)
# Usa el mismo script que Firebase Hosting (scripts/preparar-hosting.js): valida la clave, no la
# imprime y deja fuera los APK (la descarga sigue en GitHub Releases).

FROM node:22-alpine AS preparar
WORKDIR /app
COPY scripts ./scripts
COPY ["LA PAgina (segun)/proyectode10", "./LA PAgina (segun)/proyectode10"]
ARG SUPABASE_URL
ARG SUPABASE_ANON_KEY
RUN SUPABASE_URL="$SUPABASE_URL" SUPABASE_ANON_KEY="$SUPABASE_ANON_KEY" node scripts/preparar-hosting.js --destino /sitio

FROM nginx:1.27-alpine
COPY despliegue/coolify/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=preparar /sitio /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
