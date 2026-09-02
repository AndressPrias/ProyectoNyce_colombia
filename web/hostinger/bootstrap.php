<?php
declare(strict_types=1);

$localConfig = __DIR__ . '/config.local.php';
$config = is_file($localConfig)
    ? require $localConfig
    : [
        'sync_token' => '',
        'session_name' => 'lenc_web_session',
        'timezone' => 'America/Bogota',
    ];

date_default_timezone_set((string)($config['timezone'] ?? 'America/Bogota'));

function database_path(): string
{
    return __DIR__ . '/data/lencdb.db';
}

function database_available(): bool
{
    return is_file(database_path()) && filesize(database_path()) > 100;
}

function db(): PDO
{
    static $connection = null;
    if ($connection instanceof PDO) {
        return $connection;
    }
    if (!database_available()) {
        throw new RuntimeException('La copia de consulta aún no ha sido sincronizada.');
    }

    $connection = new PDO('sqlite:' . database_path(), null, null, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_TIMEOUT => 5,
    ]);
    $connection->exec('PRAGMA query_only = ON');
    $connection->exec('PRAGMA busy_timeout = 5000');
    return $connection;
}

function start_secure_session(array $config): void
{
    if (session_status() === PHP_SESSION_ACTIVE) {
        return;
    }
    session_name((string)($config['session_name'] ?? 'lenc_web_session'));
    session_set_cookie_params([
        'lifetime' => 0,
        'path' => '/',
        'secure' => true,
        'httponly' => true,
        'samesite' => 'Strict',
    ]);
    session_start();
}

function is_authenticated(): bool
{
    return isset($_SESSION['user_id'], $_SESSION['user_name']);
}

function csrf_token(): string
{
    if (!isset($_SESSION['csrf_token']) || !is_string($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf_token'];
}

function require_valid_csrf(): void
{
    $provided = trim((string)($_SERVER['HTTP_X_CSRF_TOKEN'] ?? ''));
    if ($provided === '' || !hash_equals(csrf_token(), $provided)) {
        http_response_code(403);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode(['error' => 'La sesión de carga no es válida. Recarga la página.'], JSON_UNESCAPED_UNICODE);
        exit;
    }
}

function can_upload_sample_photos(): bool
{
    if (!is_authenticated()) {
        return false;
    }

    $statement = db()->prepare(
        'SELECT rol, controlMuestras FROM usuarios WHERE id = :id LIMIT 1'
    );
    $statement->execute(['id' => (int)$_SESSION['user_id']]);
    $user = $statement->fetch();
    if (!$user) {
        return false;
    }

    $role = strtoupper(trim((string)$user['rol']));
    if ($role === 'ADMIN' || $role === 'SUPERVISOR') {
        return true;
    }
    return $role === 'AUXILIAR' && (int)$user['controlMuestras'] === 1;
}

function can_edit_samples(): bool
{
    if (!is_authenticated()) {
        return false;
    }

    $statement = db()->prepare(
        'SELECT nombre, controlMuestras FROM usuarios WHERE id = :id LIMIT 1'
    );
    $statement->execute(['id' => (int)$_SESSION['user_id']]);
    $user = $statement->fetch();
    return is_array($user)
        && (int)$user['controlMuestras'] === 1
        && hash_equals((string)$user['nombre'], (string)$_SESSION['user_name']);
}

function require_sample_edit_permission(): void
{
    if (can_edit_samples()) {
        return;
    }
    http_response_code(403);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['error' => 'Tu usuario no tiene el permiso para controlar todas las muestras.'], JSON_UNESCAPED_UNICODE);
    exit;
}

function require_sample_photo_upload_permission(): void
{
    if (can_upload_sample_photos()) {
        return;
    }
    http_response_code(403);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['error' => 'No tienes permiso para cargar fotografías.'], JSON_UNESCAPED_UNICODE);
    exit;
}

function require_authentication(bool $json = false): void
{
    if (is_authenticated()) {
        return;
    }
    if ($json) {
        http_response_code(401);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode(['error' => 'Sesión no válida'], JSON_UNESCAPED_UNICODE);
        exit;
    }
    header('Location: /');
    exit;
}

function verify_lenc_password(string $password, string $stored): bool
{
    if (!str_starts_with($stored, 'pbkdf2$')) {
        return hash_equals($stored, $password);
    }

    $parts = explode('$', $stored);
    if (count($parts) !== 4 || !ctype_digit($parts[1])) {
        return false;
    }
    $iterations = (int)$parts[1];
    $salt = base64_decode($parts[2], true);
    $expected = base64_decode($parts[3], true);
    if ($salt === false || $expected === false || $iterations < 1) {
        return false;
    }
    $actual = hash_pbkdf2('sha256', $password, $salt, $iterations, strlen($expected), true);
    return hash_equals($expected, $actual);
}

function e(?string $value): string
{
    return htmlspecialchars($value ?? '', ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function display_value(mixed $value, string $fallback = 'Sin datos'): string
{
    $text = trim((string)($value ?? ''));
    return $text === '' ? $fallback : $text;
}

function state_label(?string $state): string
{
    $labels = [
        'EN_CUSTODIA' => 'En custodia',
        'ALMACENADO' => 'Almacenado',
        'EN_CURSO' => 'En curso',
        'REALIZAR_DISPOSICION_FINAL' => 'Disposición final',
        'ENVIADO' => 'Enviado',
        'DESTRUCCION' => 'Destrucción',
    ];
    $key = strtoupper(trim((string)$state));
    return $labels[$key] ?? ucwords(strtolower(str_replace('_', ' ', $key)));
}

function format_date(?string $date): string
{
    $value = trim((string)$date);
    if ($value === '') {
        return 'Sin datos';
    }
    if (preg_match('/^\d{13}$/', $value) === 1) {
        return date('d/m/Y', intdiv((int)$value, 1000));
    }
    $timestamp = strtotime($value);
    return $timestamp === false ? $value : date('d/m/Y', $timestamp);
}

function full_documents_sql(string $table, string $alias = 'm'): string
{
    return "(SELECT group_concat(documento, ' / ') FROM (" .
        "SELECT 'LENC - ' || printf('%02d', anio % 100) || " .
        ($table === 'muestra_informes' ? "' - I '" : "' - C '") .
        " || numero AS documento FROM {$table} " .
        "WHERE muestraId = {$alias}.id ORDER BY anio, numero))";
}
