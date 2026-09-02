<?php
declare(strict_types=1);

require dirname(__DIR__) . '/bootstrap.php';
start_secure_session($config);
require_authentication(true);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$id = filter_input(INPUT_GET, 'id', FILTER_VALIDATE_INT);
if (!$id || $id < 1) {
    http_response_code(400);
    echo json_encode(['error' => 'Identificador inválido'], JSON_UNESCAPED_UNICODE);
    exit;
}

try {
    $pdo = db();
    $informeSql = full_documents_sql('muestra_informes');
    $cotizacionSql = full_documents_sql('muestra_cotizaciones');
    $statement = $pdo->prepare(
        "SELECT m.*, {$informeSql} AS informes, {$cotizacionSql} AS cotizaciones," .
        " t.nombre AS tecnico, c.nombre AS custodio, r.nombre AS responsable" .
        " FROM muestras m" .
        " LEFT JOIN usuarios t ON t.id = m.tecnicoId" .
        " LEFT JOIN usuarios c ON c.id = m.custodioId" .
        " LEFT JOIN usuarios r ON r.id = m.responsableId" .
        " WHERE m.id = :id LIMIT 1"
    );
    $statement->execute(['id' => $id]);
    $sample = $statement->fetch();
    if (!$sample) {
        http_response_code(404);
        echo json_encode(['error' => 'La muestra no existe'], JSON_UNESCAPED_UNICODE);
        exit;
    }

    $movementsStatement = $pdo->prepare(
        "SELECT mo.estadoAnterior, mo.estadoNuevo, mo.ubicacionAnterior, mo.ubicacionNueva," .
        " mo.fechaHora, mo.observacion, u.nombre AS usuario" .
        " FROM movimientos mo LEFT JOIN usuarios u ON u.id = mo.usuarioId" .
        " WHERE mo.muestraId = :id ORDER BY mo.fechaHora DESC, mo.id DESC LIMIT 20"
    );
    $movementsStatement->execute(['id' => $id]);

    $reportsStatement = $pdo->prepare(
        'SELECT numero, anio FROM muestra_informes WHERE muestraId = :id ORDER BY anio, numero'
    );
    $reportsStatement->execute(['id' => $id]);
    $quotationsStatement = $pdo->prepare(
        'SELECT numero, anio FROM muestra_cotizaciones WHERE muestraId = :id ORDER BY anio, numero'
    );
    $quotationsStatement->execute(['id' => $id]);

    echo json_encode([
        'muestra' => $sample,
        'movimientos' => $movementsStatement->fetchAll(),
        'informesReferencias' => $reportsStatement->fetchAll(),
        'cotizacionesReferencias' => $quotationsStatement->fetchAll(),
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
} catch (Throwable $error) {
    http_response_code(500);
    echo json_encode(['error' => 'No fue posible consultar la muestra'], JSON_UNESCAPED_UNICODE);
}
