<?php
declare(strict_types=1);

require dirname(__DIR__) . '/bootstrap.php';

header('Cache-Control: no-store');

$configuredToken = trim((string)($config['sync_token'] ?? ''));
$providedToken = trim((string)($_SERVER['HTTP_X_SYNC_TOKEN'] ?? ''));
if ($configuredToken === '' || !hash_equals($configuredToken, $providedToken)) {
    http_response_code(401);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['error' => 'Credencial de sincronización inválida'], JSON_UNESCAPED_UNICODE);
    exit;
}

$queueDirectory = dirname(database_path()) . '/fotos_pendientes';
$jobId = trim((string)($_GET['id'] ?? ''));

if ($_SERVER['REQUEST_METHOD'] === 'DELETE') {
    if (!preg_match('/^[a-f0-9]{32}$/', $jobId)) {
        http_response_code(400);
        exit;
    }
    $metadataPath = $queueDirectory . '/' . $jobId . '.json';
    $metadata = is_file($metadataPath)
        ? json_decode((string)file_get_contents($metadataPath), true)
        : null;
    if (is_array($metadata)) {
        $extension = (string)($metadata['extension'] ?? '');
        if (preg_match('/^(jpg|png|webp)$/', $extension)) {
            @unlink($queueDirectory . '/' . $jobId . '.' . $extension);
        }
    }
    @unlink($metadataPath);
    http_response_code(204);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    header('Allow: GET, DELETE');
    exit;
}

if (!is_dir($queueDirectory)) {
    http_response_code(204);
    exit;
}

$metadataFiles = glob($queueDirectory . '/*.json') ?: [];
usort($metadataFiles, static fn(string $left, string $right): int =>
    filemtime($left) <=> filemtime($right)
);

foreach ($metadataFiles as $metadataPath) {
    $metadata = json_decode((string)file_get_contents($metadataPath), true);
    if (!is_array($metadata)) {
        continue;
    }
    $currentId = (string)($metadata['id'] ?? '');
    $extension = (string)($metadata['extension'] ?? '');
    if (!preg_match('/^[a-f0-9]{32}$/', $currentId)
        || !preg_match('/^(jpg|png|webp)$/', $extension)) {
        continue;
    }
    $imagePath = $queueDirectory . '/' . $currentId . '.' . $extension;
    if (!is_file($imagePath)) {
        continue;
    }

    header('Content-Type: ' . (string)($metadata['mime'] ?? 'application/octet-stream'));
    header('Content-Length: ' . filesize($imagePath));
    header('X-Photo-ID: ' . $currentId);
    header('X-Sample-ID: ' . (int)($metadata['sample_id'] ?? 0));
    header('X-Sample-Code: ' . rawurlencode((string)($metadata['sample_code'] ?? '')));
    header('X-Photo-Extension: ' . $extension);
    readfile($imagePath);
    exit;
}

http_response_code(204);
