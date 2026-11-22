# Retiro de respaldos publicados por error (procedimiento, no ejecutado)

**Qué pasó.** El commit `28b65a0` («entrega 5»), que ya está en `main` del remoto, versionó carpetas locales que no debían subirse:
- `.herramientas/supabase-admin-local/`: herramientas de administración, un respaldo de la instancia con datos personales (correos de cuentas) y dos archivos de Storage;
- `.herramientas/pg15/`: binarios de PostgreSQL;
- `node_modules/`, `test-results/`;
- las skills personales (`.agents/`, `.claude/`, `skills-lock.json`).

Este documento **no** reproduce ese contenido.

**Ya hecho en la rama `entrega7/ferias-seguidores`** (sin reescribir el historial):
- `git rm --cached` de esas rutas: dejan de estar en los próximos commits;
- los archivos locales se conservan;
- las rutas están en `.gitignore`.

**Lo que esto NO resuelve:** el contenido sigue en el historial (commit `28b65a0`) y en cualquier copia o bifurcación del repositorio. Quien tenga el enlace a ese commit puede verlo.

## Decisión del grupo (antes de tocar el historial)

1. Confirmar si el repositorio es público. Si lo es, avisar al profesor: es una exposición de datos personales (RNF-PRI-01).
2. Decidir si se purga el historial. Implica forzar la actualización del remoto: todo el grupo debe coordinarse.

## Procedimiento de purga (solo si el grupo lo aprueba)

Lo hace una sola persona con permiso de administración del repositorio, en una ventana acordada.

1. **Congelar:** todo el grupo sube su trabajo pendiente a ramas del remoto y no hace push durante el proceso.
2. **Inventario y copia de seguridad del repositorio** (guárdala fuera del proyecto y sin subirla):
   ```bash
   git clone --mirror https://github.com/Diego-733/EmprendedoresPuertoMontt.git respaldo-repo.git
   git -C respaldo-repo.git for-each-ref --format="%(refname)" > refs-antes.txt
   ```
3. **Purgar solo las rutas privadas o generadas**, en otra copia espejo y con `git filter-repo` (herramienta oficial recomendada por GitHub):
   ```bash
   git clone --mirror https://github.com/Diego-733/EmprendedoresPuertoMontt.git purga.git
   cd purga.git
   git filter-repo --invert-paths --path .herramientas/ --path node_modules/ --path test-results/ \
     --path .agents/ --path .claude/ --path skills-lock.json
   ```
4. **Verificar** antes de publicar:
   - `git log --all -- .herramientas` no muestra nada;
   - `git for-each-ref` mantiene las mismas ramas que `refs-antes.txt`;
   - una copia normal compila y pasa `npm run test:web`.
5. **Publicar:** `git push --force --mirror`. Revisar en GitHub que estén las ramas `main`, `entrega5/…`, `entrega6/…` y `entrega7/…`.
6. **Cada integrante:** clona de nuevo. Si tiene trabajo local sin subir, lo pasa a la copia nueva con `git format-patch` / `git am` o `git rebase --onto`. No hace push desde una copia antigua: devolvería los archivos al historial.
7. **GitHub:**
   - las vistas en caché y las referencias de pull requests pueden conservar el contenido; pedir la limpieza al soporte de GitHub (formulario de datos sensibles);
   - revisar las bifurcaciones (por ejemplo, el repositorio original del grupo) y aplicar lo mismo si incluyen el commit.
8. **Credenciales:** el archivo de acceso local no se subió. Si alguna contraseña de administración se compartió por otra vía, cambiarla igual.

## Si el grupo decide no purgar

Dejar constancia en el informe final del motivo. Revisar que nadie vuelva a agregar esas rutas: `git ls-files` no debe listar nada de ellas.
