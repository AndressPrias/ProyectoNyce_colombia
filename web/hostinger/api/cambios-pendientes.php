<?php
declare(strict_types=1);

require dirname(__DIR__) . '/bootstrap.php';
header('Cache-Control: no-store');

$expected = trim((string)($config['sync_token'] ?? ''));
$provided = trim((string)($_SERVER['HTTP_X_SYNC_TOKEN'] ?? ''));
if ($expected === '' || !hash_equals($expected, $provided)) {
    http_response_code(401);
    exit;
}

$queue = dirname(database_path()) . '/cambios_pendientes';
$jobId = trim((string)($_GET['id'] ?? ''));
if ($_SERVER['REQUEST_METHOD'] === 'DELETE') {
    if (!preg_match('/^[a-f0-9]{32}$/', $jobId)) {
        http_response_code(400);
        exit;
    }
    @unlink($queue . '/' . $jobId . '.json');
    http_response_code(204);
    exit;
}
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    header('Allow: GET, DELETE');
    exit;
}
if (!is_dir($queue)) {
    http_response_code(204);
    exit;
}

$files = glob($queue . '/*.json') ?: [];
usort($files, static fn(string $a, string $b): int => filemtime($a) <=> filemtime($b));
foreach ($files as $file) {
    $job = json_decode((string)file_get_contents($file), true);
    if (!is_array($job) || !preg_match('/^[a-f0-9]{32}$/', (string)($job['id'] ?? ''))) {
        continue;
    }
    $encodeDocuments = static function (mixed $documents): string {
        if (!is_array($documents)) return '';
        return implode(',', array_map(static fn(array $item): string =>
            (int)($item['anio'] ?? 0) . ':' . base64_encode((string)($item['numero'] ?? '')), $documents));
    };
    $payload = $job;
    $payload['informes'] = $encodeDocuments($job['informes'] ?? []);
    $payload['cotizaciones'] = $encodeDocuments($job['cotizaciones'] ?? []);
    header('Content-Type: application/x-www-form-urlencoded; charset=utf-8');
    echo http_build_query($payload, '', '&', PHP_QUERY_RFC3986);
    exit;
}
http_response_code(204);
