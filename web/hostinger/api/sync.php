<?php
declare(strict_types=1);

require dirname(__DIR__) . '/bootstrap.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    header('Allow: POST');
    echo json_encode(['error' => 'Método no permitido'], JSON_UNESCAPED_UNICODE);
    exit;
}

$configuredToken = trim((string)($config['sync_token'] ?? ''));
$providedToken = trim((string)($_SERVER['HTTP_X_SYNC_TOKEN'] ?? ''));
if ($configuredToken === '' || !hash_equals($configuredToken, $providedToken)) {
    http_response_code(401);
    echo json_encode(['error' => 'Credencial de sincronización inválida'], JSON_UNESCAPED_UNICODE);
    exit;
}

$contentLength = (int)($_SERVER['CONTENT_LENGTH'] ?? 0);
$maximumBytes = 64 * 1024 * 1024;
if ($contentLength < 100 || $contentLength > $maximumBytes) {
    http_response_code(413);
    echo json_encode(['error' => 'Tamaño de archivo no permitido'], JSON_UNESCAPED_UNICODE);
    exit;
}

$dataDirectory = dirname(database_path());
if (!is_dir($dataDirectory) && !mkdir($dataDirectory, 0750, true) && !is_dir($dataDirectory)) {
    http_response_code(500);
    echo json_encode(['error' => 'No fue posible preparar el almacenamiento'], JSON_UNESCAPED_UNICODE);
    exit;
}

$temporaryPath = $dataDirectory . '/incoming-' . bin2hex(random_bytes(8)) . '.tmp';
$input = fopen('php://input', 'rb');
$output = fopen($temporaryPath, 'xb');
if ($input === false || $output === false) {
    @unlink($temporaryPath);
    http_response_code(500);
    echo json_encode(['error' => 'No fue posible recibir el archivo'], JSON_UNESCAPED_UNICODE);
    exit;
}

$written = stream_copy_to_stream($input, $output, $maximumBytes + 1);
fclose($input);
fclose($output);
if ($written === false || $written > $maximumBytes) {
    @unlink($temporaryPath);
    http_response_code(413);
    echo json_encode(['error' => 'El archivo excede el límite permitido'], JSON_UNESCAPED_UNICODE);
    exit;
}

$handle = fopen($temporaryPath, 'rb');
$signature = $handle ? fread($handle, 16) : false;
if ($handle) {
    fclose($handle);
}
if ($signature !== "SQLite format 3\0") {
    @unlink($temporaryPath);
    http_response_code(422);
    echo json_encode(['error' => 'El archivo recibido no es una base SQLite válida'], JSON_UNESCAPED_UNICODE);
    exit;
}

try {
    $candidate = new PDO('sqlite:' . $temporaryPath, null, null, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_TIMEOUT => 5,
    ]);
    $integrity = $candidate->query('PRAGMA integrity_check')->fetchColumn();
    if ($integrity !== 'ok') {
        throw new RuntimeException('Falló la validación de integridad');
    }
    $required = ['muestras', 'usuarios', 'movimientos', 'remisiones'];
    $placeholders = implode(',', array_fill(0, count($required), '?'));
    $check = $candidate->prepare(
        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ({$placeholders})"
    );
    $check->execute($required);
    if ((int)$check->fetchColumn() !== count($required)) {
        throw new RuntimeException('Faltan tablas requeridas');
    }
    $candidate = null;

    $currentPath = database_path();
    $backupPath = $dataDirectory . '/lencdb.backup.bak';
    if (is_file($currentPath)) {
        @unlink($backupPath);
        if (!copy($currentPath, $backupPath)) {
            throw new RuntimeException('No fue posible crear el respaldo');
        }
    }
    if (!rename($temporaryPath, $currentPath)) {
        throw new RuntimeException('No fue posible activar la nueva copia');
    }
    chmod($currentPath, 0640);

    $countConnection = new PDO('sqlite:' . $currentPath);
    $sampleCount = (int)$countConnection->query('SELECT COUNT(*) FROM muestras')->fetchColumn();
    echo json_encode([
        'ok' => true,
        'muestras' => $sampleCount,
        'actualizado' => date(DATE_ATOM),
    ], JSON_UNESCAPED_UNICODE);
} catch (Throwable $error) {
    @unlink($temporaryPath);
    http_response_code(422);
    echo json_encode(['error' => 'La copia recibida no superó la validación'], JSON_UNESCAPED_UNICODE);
}
