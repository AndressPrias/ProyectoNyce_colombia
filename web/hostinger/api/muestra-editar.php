<?php
declare(strict_types=1);

require dirname(__DIR__) . '/bootstrap.php';
start_secure_session($config);
require_authentication(true);
require_sample_edit_permission();
require_valid_csrf();

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    header('Allow: POST');
    echo json_encode(['error' => 'Método no permitido'], JSON_UNESCAPED_UNICODE);
    exit;
}

function edit_text(mixed $value, string $field, int $maximum, bool $required = false): string
{
    if (!is_string($value)) {
        throw new InvalidArgumentException("El campo {$field} no es válido.");
    }
    $text = trim($value);
    if ($required && $text === '') {
        throw new InvalidArgumentException("El campo {$field} es obligatorio.");
    }
    if (mb_strlen($text, 'UTF-8') > $maximum) {
        throw new InvalidArgumentException("El campo {$field} supera {$maximum} caracteres.");
    }
    return $text;
}

function edit_documents(mixed $value, bool $quotation): array
{
    if (!is_array($value) || count($value) > 20) {
        throw new InvalidArgumentException('La lista de documentos no es válida.');
    }
    $documents = [];
    $seen = [];
    foreach ($value as $document) {
        if (!is_array($document)) {
            throw new InvalidArgumentException('Uno de los documentos no es válido.');
        }
        $number = edit_text($document['numero'] ?? null, $quotation ? 'cotización' : 'informe', 120, true);
        $year = filter_var($document['anio'] ?? null, FILTER_VALIDATE_INT);
        if ($year === false || $year < 2000 || $year > 9999) {
            throw new InvalidArgumentException('El año del documento no es válido.');
        }
        if ($quotation && preg_match('/^\d{4}$/', $number) !== 1) {
            throw new InvalidArgumentException('Cada cotización debe contener exactamente 4 dígitos.');
        }
        $key = $year . "\0" . $number;
        if (isset($seen[$key])) {
            throw new InvalidArgumentException('No se permiten documentos repetidos.');
        }
        $seen[$key] = true;
        $documents[] = ['numero' => $number, 'anio' => $year];
    }
    return $documents;
}

try {
    $body = json_decode((string)file_get_contents('php://input'), true, 64, JSON_THROW_ON_ERROR);
    if (!is_array($body)) {
        throw new InvalidArgumentException('Solicitud no válida.');
    }
    $sampleId = filter_var($body['id'] ?? null, FILTER_VALIDATE_INT);
    $quantity = filter_var($body['cantidad'] ?? null, FILTER_VALIDATE_INT);
    if ($sampleId === false || $sampleId < 1 || $quantity === false || $quantity < 1 || $quantity > 1000000) {
        throw new InvalidArgumentException('El identificador o la cantidad no es válido.');
    }
    $states = ['EN_CUSTODIA', 'ALMACENADO', 'EN_CURSO', 'LISTA_PARA_ALMACENAR',
        'LABORATORIO_EXTERNO', 'REALIZAR_DISPOSICION_FINAL', 'ENVIADO', 'DESTRUCCION'];
    $state = edit_text($body['estado'] ?? null, 'estado', 50, true);
    if (!in_array($state, $states, true)) {
        throw new InvalidArgumentException('El estado seleccionado no es válido.');
    }
    $date = edit_text($body['fechaRecepcion'] ?? null, 'fecha de ingreso', 10, true);
    $parsedDate = DateTimeImmutable::createFromFormat('!Y-m-d', $date);
    if (!$parsedDate || $parsedDate->format('Y-m-d') !== $date) {
        throw new InvalidArgumentException('La fecha de ingreso no es válida.');
    }

    $statement = db()->prepare('SELECT codigoInterno FROM muestras WHERE id = :id LIMIT 1');
    $statement->execute(['id' => $sampleId]);
    $sampleCode = $statement->fetchColumn();
    if (!is_string($sampleCode) || trim($sampleCode) === '') {
        http_response_code(404);
        echo json_encode(['error' => 'La muestra no existe.'], JSON_UNESCAPED_UNICODE);
        exit;
    }

    $job = [
        'id' => bin2hex(random_bytes(16)),
        'sample_id' => $sampleId,
        'sample_code' => $sampleCode,
        'user_id' => (int)$_SESSION['user_id'],
        'user_name' => (string)$_SESSION['user_name'],
        'created_at' => date(DATE_ATOM),
        'rotuloCliente' => edit_text($body['rotuloCliente'] ?? null, 'referencia externa', 500),
        'nombreCliente' => edit_text($body['nombreCliente'] ?? null, 'nombre del cliente', 500),
        'descripcion' => edit_text($body['descripcion'] ?? null, 'descripción', 2000),
        'cantidad' => $quantity,
        'marca' => edit_text($body['marca'] ?? null, 'marca', 500),
        'referencia' => edit_text($body['referencia'] ?? null, 'referencia', 500),
        'fechaRecepcion' => $date,
        'estado' => $state,
        'ubicacion' => edit_text($body['ubicacion'] ?? null, 'ubicación', 500),
        'observacionAlmacenamiento' => edit_text($body['observacionAlmacenamiento'] ?? null, 'observaciones', 4000),
        'informes' => edit_documents($body['informes'] ?? null, false),
        'cotizaciones' => edit_documents($body['cotizaciones'] ?? null, true),
    ];

    $queue = dirname(database_path()) . '/cambios_pendientes';
    if (!is_dir($queue) && !mkdir($queue, 0750, true) && !is_dir($queue)) {
        throw new RuntimeException('No fue posible preparar la cola de cambios.');
    }
    $path = $queue . '/' . $job['id'] . '.json';
    $encoded = json_encode($job, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    if (file_put_contents($path, $encoded, LOCK_EX) === false) {
        throw new RuntimeException('No fue posible guardar el cambio.');
    }
    @chmod($path, 0640);
    echo json_encode(['message' => 'Cambio enviado. Se aplicará en la próxima sincronización.'], JSON_UNESCAPED_UNICODE);
} catch (InvalidArgumentException|JsonException $error) {
    http_response_code(422);
    echo json_encode(['error' => $error->getMessage()], JSON_UNESCAPED_UNICODE);
} catch (Throwable $error) {
    http_response_code(500);
    echo json_encode(['error' => 'No fue posible guardar el cambio.'], JSON_UNESCAPED_UNICODE);
}
