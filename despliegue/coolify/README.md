# Vitrina web en Coolify

La web se construye con el `Dockerfile` de la raíz: usa `scripts/preparar-hosting.js` (el mismo de
Firebase Hosting) y la sirve con nginx (`despliegue/coolify/nginx.conf`, mismas cabeceras de seguridad).
GitHub Pages y Firebase Hosting siguen igual.

1. Equipo **VcM** → proyecto **AlerceShopping** → **+ New** → **Public Repository**.
2. Repositorio `https://github.com/Diego-733/EmprendedoresPuertoMontt`, rama `main`.
3. **Build Pack: Dockerfile** (ubicación `/Dockerfile`, directorio base `/`), puerto **80**.
4. **Environment Variables**, las dos marcadas como **Build Variable**:
   - `SUPABASE_URL`: la URL https de Supabase.
   - `SUPABASE_ANON_KEY`: la clave **pública anon**. Nunca `service_role`: la construcción la rechaza.
5. **Deploy**. Al terminar, abre el dominio que asigna Coolify: debe cargar el catálogo real.

Para que la recuperación de contraseña vuelva a este dominio, la administración de Supabase debe
agregarlo a las direcciones permitidas de Auth (Site URL / Redirect URLs).
