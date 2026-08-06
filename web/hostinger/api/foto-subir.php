<?php
declare(strict_types=1);

require dirname(__DIR__) . '/bootstrap.php';
start_secure_session($config);
require_authentication(true);
require_sample_photo_upload_permission();
require_valid_csrf();

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    header('Allow: POST');
    echo json_encode(['error' => 'Método no permitido'], JSON_UNESCAPED_UNICODE);
    exit;
}

$sampleId = filter_input(INPUT_POST, 'sample_id', FILTER_VALIDATE_INT);
$photo = $_FILES['photo'] ?? null;
$maximumBytes = 8 * 1024 * 1024;

if (!$sampleId || $sampleId < 1 || !is_array($photo)) {
    http_response_code(400);
    echo json_encode(['error' => 'Selecciona una muestra y una fotografía.'], JSON_UNESCAPED_UNICODE);
    exit;
}
if (($photo['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
    http_response_code(400);
    echo json_encode(['error' => 'No fue posible recibir la fotografía.'], JSON_UNESCAPED_UNICODE);
    exit;
}

$temporaryUpload = (string)($photo['tmp_name'] ?? '');
$size = (int)($photo['size'] ?? 0);
if ($size < 1 || $size > $maximumBytes || !is_uploaded_file($temporaryUpload)) {
    http_response_code(413);
    echo json_encode(['error' => 'La fotografía debe pesar máximo 8 MB.'], JSON_UNESCAPED_UNICODE);
    exit;
}

$imageInfo = @getimagesize($temporaryUpload);
$mime = is_array($imageInfo) ? (string)($imageInfo['mime'] ?? '') : '';
$allowedTypes = [
    'image/jpeg' => 'jpg',
    'image/png' => 'png',
    'image/webp' => 'webp',
];
if (!isset($allowedTypes[$mime])) {
    http_response_code(415);
    echo json_encode(['error' => 'Usa una imagen JPG, PNG o WEBP.'], JSON_UNESCAPED_UNICODE);
    exit;
}

$statement = db()->prepare('SELECT codigoInterno FROM muestras WHERE id = :id LIMIT 1');
$statement->execute(['id' => $sampleId]);
$sampleCode = trim((string)$statement->fetchColumn());
if ($sampleCode === '') {
    http_response_code(404);
    echo json_encode(['error' => 'La muestra seleccionada ya no existe.'], JSON_UNESCAPED_UNICODE);
    exit;
}

$queueDirectory = dirname(database_path()) . '/fotos_pendientes';
if (!is_dir($queueDirectory) && !mkdir($queueDirectory, 0750, true) && !is_dir($queueDirectory)) {
    http_response_code(500);
    echo json_encode(['error' => 'No fue posible preparar la carga.'], JSON_UNESCAPED_UNICODE);
    exit;
}

$jobId = bin2hex(random_bytes(16));
$extension = $allowedTypes[$mime];
$imagePath = $queueDirectory . '/' . $jobId . '.' . $extension;
$metadataPath = $queueDirectory . '/' . $jobId . '.json';

if (!move_uploaded_file($temporaryUpload, $imagePath)) {
    http_response_code(500);
    echo json_encode(['error' => 'No fue posible guardar temporalmente la fotografía.'], JSON_UNESCAPED_UNICODE);
    exit;
}

$metadata = [
    'id' => $jobId,
    'sample_id' => $sampleId,
    'sample_code' => $sampleCode,
    'extension' => $extension,
    'mime' => $mime,
    'user_id' => (int)$_SESSION['user_id'],
    'created_at' => date(DATE_ATOM),
];
$encodedMetadata = json_encode($metadata, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
if ($encodedMetadata === false || file_put_contents($metadataPath, $encodedMetadata, LOCK_EX) === false) {
    @unlink($imagePath);
    http_response_code(500);
    echo json_encode(['error' => 'No fue posible registrar la fotografía.'], JSON_UNESCAPED_UNICODE);
    exit;
}

chmod($imagePath, 0640);
chmod($metadataPath, 0640);
echo json_encode([
    'ok' => true,
    'message' => 'Fotografía enviada. Se guardará en la carpeta del programa durante la próxima sincronización.',
], JSON_UNESCAPED_UNICODE);
