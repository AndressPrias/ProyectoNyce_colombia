<?php
declare(strict_types=1);

require __DIR__ . '/bootstrap.php';
start_secure_session($config);

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['action'])) {
    if ($_POST['action'] === 'logout') {
        $_SESSION = [];
        session_destroy();
        header('Location: /');
        exit;
    }

    if ($_POST['action'] === 'login') {
        $user = trim((string)($_POST['user'] ?? ''));
        $password = (string)($_POST['password'] ?? '');
        $now = time();
        $blockedUntil = (int)($_SESSION['login_blocked_until'] ?? 0);

        if ($blockedUntil > $now) {
            $loginError = 'Espera unos minutos antes de intentarlo nuevamente.';
        } elseif (!database_available()) {
            $loginError = 'La base de consulta aún no ha sido sincronizada.';
        } else {
            $statement = db()->prepare(
                'SELECT id, nombre, rol, password FROM usuarios WHERE LOWER(nombre) = LOWER(:nombre) LIMIT 1'
            );
            $statement->execute(['nombre' => $user]);
            $found = $statement->fetch();

            if ($found && verify_lenc_password($password, (string)$found['password'])) {
                session_regenerate_id(true);
                $_SESSION['user_id'] = (int)$found['id'];
                $_SESSION['user_name'] = (string)$found['nombre'];
                $_SESSION['user_role'] = (string)$found['rol'];
                unset($_SESSION['login_attempts'], $_SESSION['login_blocked_until']);
                header('Location: /');
                exit;
            }

            $attempts = (int)($_SESSION['login_attempts'] ?? 0) + 1;
            $_SESSION['login_attempts'] = $attempts;
            if ($attempts >= 5) {
                $_SESSION['login_blocked_until'] = $now + 300;
                $_SESSION['login_attempts'] = 0;
            }
            $loginError = 'Usuario o contraseña incorrectos.';
        }
    }
}

$authenticated = is_authenticated();
$rows = [];
$states = [];
$summary = ['total' => 0, 'custodia' => 0, 'curso' => 0, 'enviadas' => 0];
$page = max(1, (int)($_GET['page'] ?? 1));
$perPage = 25;
$totalRows = 0;
$totalPages = 1;
$canUploadPhotos = false;
$query = trim((string)($_GET['q'] ?? ''));
$selectedState = trim((string)($_GET['estado'] ?? ''));
$dateFrom = trim((string)($_GET['desde'] ?? ''));
$dateTo = trim((string)($_GET['hasta'] ?? ''));

if ($authenticated) {
    $pdo = db();
    $canUploadPhotos = can_upload_sample_photos();
    $summaryRow = $pdo->query(
        "SELECT COUNT(*) total," .
        " SUM(CASE WHEN estado = 'EN_CUSTODIA' THEN 1 ELSE 0 END) custodia," .
        " SUM(CASE WHEN estado = 'EN_CURSO' THEN 1 ELSE 0 END) curso," .
        " SUM(CASE WHEN estado = 'ENVIADO' THEN 1 ELSE 0 END) enviadas" .
        " FROM muestras"
    )->fetch();
    if ($summaryRow) {
        $summary = array_map('intval', $summaryRow);
    }
    $states = $pdo->query(
        "SELECT estado, COUNT(*) cantidad FROM muestras GROUP BY estado ORDER BY estado"
    )->fetchAll();

    $conditions = [];
    $parameters = [];
    if ($query !== '') {
        $conditions[] = '(' .
            'm.codigoInterno LIKE :buscar OR m.rotuloCliente LIKE :buscar OR ' .
            'm.nombreCliente LIKE :buscar OR m.descripcion LIKE :buscar OR ' .
            'm.marca LIKE :buscar OR m.referencia LIKE :buscar OR ' .
            'm.remision LIKE :buscar OR EXISTS (SELECT 1 FROM muestra_informes mi ' .
            'WHERE mi.muestraId = m.id AND mi.numero LIKE :buscar) OR ' .
            'EXISTS (SELECT 1 FROM muestra_cotizaciones mc ' .
            'WHERE mc.muestraId = m.id AND mc.numero LIKE :buscar))';
        $parameters['buscar'] = '%' . $query . '%';
    }
    if ($selectedState !== '') {
        $conditions[] = 'm.estado = :estado';
        $parameters['estado'] = $selectedState;
    }
    if ($dateFrom !== '') {
        $conditions[] = 'm.fechaRecepcion >= :desde';
        $parameters['desde'] = $dateFrom;
    }
    if ($dateTo !== '') {
        $conditions[] = 'm.fechaRecepcion <= :hasta';
        $parameters['hasta'] = $dateTo;
    }
    $where = $conditions ? ' WHERE ' . implode(' AND ', $conditions) : '';

    $countStatement = $pdo->prepare('SELECT COUNT(*) FROM muestras m' . $where);
    $countStatement->execute($parameters);
    $totalRows = (int)$countStatement->fetchColumn();
    $totalPages = max(1, (int)ceil($totalRows / $perPage));
    $page = min($page, $totalPages);
    $offset = ($page - 1) * $perPage;

    $informeSql = full_documents_sql('muestra_informes');
    $cotizacionSql = full_documents_sql('muestra_cotizaciones');
    $sql = "SELECT m.id, m.codigoInterno, m.rotuloCliente, m.nombreCliente, m.descripcion, m.cantidad," .
        " m.estado, m.ubicacion, m.fechaRecepcion, m.remision," .
        " {$informeSql} AS informes, {$cotizacionSql} AS cotizaciones" .
        " FROM muestras m{$where} ORDER BY m.fechaRecepcion DESC, m.id DESC LIMIT :limite OFFSET :offset";
    $statement = $pdo->prepare($sql);
    foreach ($parameters as $key => $value) {
        $statement->bindValue(':' . $key, $value, PDO::PARAM_STR);
    }
    $statement->bindValue(':limite', $perPage, PDO::PARAM_INT);
    $statement->bindValue(':offset', $offset, PDO::PARAM_INT);
    $statement->execute();
    $rows = $statement->fetchAll();
}

$syncTime = database_available() ? date('d/m/Y H:i', filemtime(database_path())) : 'Pendiente';
?>
<!doctype html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex,nofollow">
    <title>Control Muestras LENC</title>
    <link rel="stylesheet" href="/assets/styles.css?v=4">
</head>
<body class="<?= $authenticated ? 'app-shell' : 'login-shell' ?>">
<?php if (!$authenticated): ?>
    <main class="login-layout">
        <section class="login-brand" aria-label="Control Muestras LENC">
            <span class="eyebrow">Laboratorio de ensayos</span>
            <h1>Control Muestras <strong>LENC</strong></h1>
            <p>Consulta segura y actualizada del inventario de muestras.</p>
            <div class="brand-points">
                <span>Solo lectura</span>
                <span>Acceso protegido</span>
                <span>Información centralizada</span>
            </div>
        </section>
        <section class="login-card">
            <div class="brand-mark">NYCE <small>COLOMBIA</small></div>
            <h2>Ingresar</h2>
            <p>Utiliza el mismo usuario y contraseña de la aplicación.</p>
            <?php if (isset($loginError)): ?>
                <div class="alert" role="alert"><?= e($loginError) ?></div>
            <?php endif; ?>
            <form method="post" autocomplete="on">
                <input type="hidden" name="action" value="login">
                <label for="user">Usuario</label>
                <input id="user" name="user" required autocomplete="username" autofocus>
                <label for="password">Contraseña</label>
                <input id="password" type="password" name="password" required autocomplete="current-password">
                <button class="primary-button" type="submit">Acceder al sistema</button>
            </form>
            <p class="sync-note">Última copia recibida: <?= e($syncTime) ?></p>
        </section>
    </main>
<?php else: ?>
    <header class="topbar">
        <div>
            <span class="eyebrow">NYCE Colombia</span>
            <h1>Control Muestras LENC</h1>
        </div>
        <div class="user-area">
            <div>
                <strong><?= e((string)$_SESSION['user_name']) ?></strong>
                <span><?= e((string)$_SESSION['user_role']) ?></span>
            </div>
            <form method="post">
                <input type="hidden" name="action" value="logout">
                <button class="ghost-button" type="submit">Cerrar sesión</button>
            </form>
        </div>
    </header>

    <main class="dashboard">
        <section class="dashboard-heading">
            <div>
                <span class="eyebrow">Consulta de inventario</span>
                <h2>Estado general de las muestras</h2>
                <p>Información de consulta. Los cambios se realizan únicamente desde la aplicación de escritorio.</p>
            </div>
            <div class="sync-badge"><span></span> Actualizado <?= e($syncTime) ?></div>
        </section>

        <section class="metrics" aria-label="Resumen de muestras">
            <article><span>Total</span><strong><?= $summary['total'] ?></strong><small>Muestras registradas</small></article>
            <article><span>En custodia</span><strong><?= $summary['custodia'] ?></strong><small>Pendientes de proceso</small></article>
            <article><span>En curso</span><strong><?= $summary['curso'] ?></strong><small>En gestión técnica</small></article>
            <article><span>Enviadas</span><strong><?= $summary['enviadas'] ?></strong><small>Con salida registrada</small></article>
        </section>

        <section class="content-card">
            <form class="filters" method="get">
                <div class="search-field">
                    <label for="q">Buscar muestra</label>
                    <input id="q" name="q" value="<?= e($query) ?>"
                           placeholder="ID, cliente, descripción, informe, cotización…">
                </div>
                <div>
                    <label for="estado">Estado</label>
                    <select id="estado" name="estado">
                        <option value="">Todos</option>
                        <?php foreach ($states as $state): ?>
                            <option value="<?= e($state['estado']) ?>"
                                <?= $selectedState === $state['estado'] ? 'selected' : '' ?>>
                                <?= e(state_label($state['estado'])) ?> (<?= (int)$state['cantidad'] ?>)
                            </option>
                        <?php endforeach; ?>
                    </select>
                </div>
                <div>
                    <label for="desde">Desde</label>
                    <input id="desde" type="date" name="desde" value="<?= e($dateFrom) ?>">
                </div>
                <div>
                    <label for="hasta">Hasta</label>
                    <input id="hasta" type="date" name="hasta" value="<?= e($dateTo) ?>">
                </div>
                <button class="primary-button filter-button" type="submit">Consultar</button>
                <a class="clear-button" href="/">Limpiar</a>
            </form>

            <div class="table-heading">
                <div>
                    <h3>Muestras registradas</h3>
                    <p><?= number_format($totalRows, 0, ',', '.') ?> resultado<?= $totalRows === 1 ? '' : 's' ?></p>
                </div>
                <span>Página <?= $page ?> de <?= $totalPages ?></span>
            </div>

            <div class="table-wrap">
                <table>
                    <thead>
                    <tr>
                        <th>ID</th>
                        <th>Referencia externa</th>
                        <th>Fecha de ingreso</th>
                        <th>Nombre del cliente</th>
                        <th>Descripción muestra</th>
                        <th>Cant.</th>
                        <th>Estado</th>
                        <th>Ubicación</th>
                        <th>Informe</th>
                        <th>Cotización</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <?php if (!$rows): ?>
                        <tr><td colspan="11" class="empty-state">No se encontraron muestras con estos filtros.</td></tr>
                    <?php endif; ?>
                    <?php foreach ($rows as $row): ?>
                        <tr>
                            <td><strong><?= e($row['codigoInterno']) ?></strong></td>
                            <td><?= e(display_value($row['rotuloCliente'])) ?></td>
                            <td><?= e(format_date($row['fechaRecepcion'])) ?></td>
                            <td><?= e(display_value($row['nombreCliente'])) ?></td>
                            <td><?= e(display_value($row['descripcion'])) ?></td>
                            <td class="center"><?= (int)$row['cantidad'] ?></td>
                            <td><span class="state state-<?= e(strtolower((string)$row['estado'])) ?>"><?= e(state_label($row['estado'])) ?></span></td>
                            <td><?= e(display_value($row['ubicacion'])) ?></td>
                            <td class="document-cell"><?= e(display_value($row['informes'], 'Sin asignar')) ?></td>
                            <td class="document-cell"><?= e(display_value($row['cotizaciones'], 'Sin asignar')) ?></td>
                            <td><button class="detail-button" type="button" data-sample-id="<?= (int)$row['id'] ?>">Ver detalle</button></td>
                        </tr>
                    <?php endforeach; ?>
                    </tbody>
                </table>
            </div>

            <section class="mobile-samples" aria-label="Muestras registradas en formato de tarjetas">
                <?php if (!$rows): ?>
                    <div class="empty-state">No se encontraron muestras con estos filtros.</div>
                <?php endif; ?>
                <?php foreach ($rows as $row): ?>
                    <article class="sample-card">
                        <header class="sample-card-header">
                            <div>
                                <span class="sample-card-label">ID de muestra</span>
                                <strong><?= e($row['codigoInterno']) ?></strong>
                            </div>
                            <span class="state state-<?= e(strtolower((string)$row['estado'])) ?>">
                                <?= e(state_label($row['estado'])) ?>
                            </span>
                        </header>

                        <div class="sample-card-reference">
                            <span class="sample-card-label">Referencia externa</span>
                            <strong><?= e(display_value($row['rotuloCliente'])) ?></strong>
                        </div>

                        <dl class="sample-card-grid">
                            <div>
                                <dt>Fecha de ingreso</dt>
                                <dd><?= e(format_date($row['fechaRecepcion'])) ?></dd>
                            </div>
                            <div>
                                <dt>Cantidad</dt>
                                <dd><?= (int)$row['cantidad'] ?></dd>
                            </div>
                            <div class="sample-card-wide">
                                <dt>Nombre del cliente</dt>
                                <dd><?= e(display_value($row['nombreCliente'])) ?></dd>
                            </div>
                            <div class="sample-card-wide">
                                <dt>Descripción</dt>
                                <dd><?= e(display_value($row['descripcion'])) ?></dd>
                            </div>
                            <div class="sample-card-wide">
                                <dt>Ubicación</dt>
                                <dd><?= e(display_value($row['ubicacion'])) ?></dd>
                            </div>
                            <div>
                                <dt>Informe</dt>
                                <dd><?= e(display_value($row['informes'], 'Sin asignar')) ?></dd>
                            </div>
                            <div>
                                <dt>Cotización</dt>
                                <dd><?= e(display_value($row['cotizaciones'], 'Sin asignar')) ?></dd>
                            </div>
                        </dl>

                        <button class="detail-button sample-card-button" type="button"
                                data-sample-id="<?= (int)$row['id'] ?>">
                            Ver detalle
                        </button>
                    </article>
                <?php endforeach; ?>
            </section>

            <?php if ($totalPages > 1): ?>
                <nav class="pagination" aria-label="Paginación">
                    <?php
                    $baseParams = ['q' => $query, 'estado' => $selectedState, 'desde' => $dateFrom, 'hasta' => $dateTo];
                    $previous = '?' . http_build_query(array_merge($baseParams, ['page' => max(1, $page - 1)]));
                    $next = '?' . http_build_query(array_merge($baseParams, ['page' => min($totalPages, $page + 1)]));
                    ?>
                    <a class="<?= $page <= 1 ? 'disabled' : '' ?>" href="<?= e($previous) ?>">Anterior</a>
                    <span><?= $page ?> / <?= $totalPages ?></span>
                    <a class="<?= $page >= $totalPages ? 'disabled' : '' ?>" href="<?= e($next) ?>">Siguiente</a>
                </nav>
            <?php endif; ?>
        </section>
    </main>

    <dialog id="sample-dialog" class="sample-dialog" aria-labelledby="dialog-title"
            data-photo-upload-enabled="<?= $canUploadPhotos ? 'true' : 'false' ?>"
            data-csrf-token="<?= $canUploadPhotos ? e(csrf_token()) : '' ?>">
        <div class="dialog-header">
            <div>
                <span class="eyebrow">Detalle de la muestra</span>
                <h2 id="dialog-title">Cargando…</h2>
            </div>
            <button class="dialog-close" type="button" aria-label="Cerrar">×</button>
        </div>
        <div id="dialog-content" class="dialog-content" aria-live="polite"></div>
    </dialog>
    <script src="/assets/app.js?v=6" defer></script>
<?php endif; ?>
</body>
</html>
