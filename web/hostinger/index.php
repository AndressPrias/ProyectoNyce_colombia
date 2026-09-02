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
$canEditSamples = false;
$query = trim((string)($_GET['q'] ?? ''));
$selectedState = trim((string)($_GET['estado'] ?? ''));
$dateFrom = trim((string)($_GET['desde'] ?? ''));
$dateTo = trim((string)($_GET['hasta'] ?? ''));

if ($authenticated) {
    $pdo = db();
    $canUploadPhotos = can_upload_sample_photos();
    $canEditSamples = can_edit_samples();
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
    <link rel="icon" type="image/png" sizes="64x64" href="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAABaJSURBVHhe7VoJVFRH1n4iKqKydtMbvdN0N9BAI4uIxN0YYxI1xkwWExONW2LUuOC+oYKKuIDIIrsCgoj7FreocdfE6DgYEzPjzCTOlploZpIz8+rUf2+9ahZFA5hz5j8Zv3Puqbfc9+rdW7fuUvWEJ3iC/y5UZrOGH/7vQWm2zzE6uv7TbHXM4Zf+d+Cn0M6KGzKCdqs5QBUDh1CL1vi/owQ/mXaW4+nn6NxPr9LYG78nwp6T1NB/MA3WmJI4yy8XMPJJYSB8Egg/5PKviduqDFGoOSy6HzxNDf2eBSUYZnDWXx785OqZYQMG0xkg/NDL14iwsZgIKelEmL2ACDuPEKaEvr9QJbCRB+FnuoTfUECE7BIipGUQYe5CIsxZRIRdR0g7poRBNEj1/08JbYDceOs6bkiu6w3vtUlcuLCtXGGYGcqFH/YJFz6riAi5m4mwChQwf4lEc1EJR0m7A6CEPoOoRW38QIDnG7zP1U9T501df3z46G0vy4IdxwwRsTcYRcbVstaJ1O2GEQhb6RwprtYcFV+L14NjE2+ozSE3Q/sNojMuX6HDLl+VhN+IwpcSIQ8VsB6EX0yEhUu5EuB49zHSbt8pqu85gJotYTeDY3vcMDm71QZ17V6Lras/YxS2/Hvwu/BaZNznrm+RWxxHfQzBI7goLYeX3vpWLIxcYcU2euTEx3T/keMP0L669hjdf/QjeuDoCZqxqZguSc+kT78wggb37C8J7xp5FD4HTB8pb4s0BebByKMCFi4DJSRL02H3MdF970mqSehDh734Cl28OoNmF2+hB4+dgD4+4n1By7+B0WH4hsPS9xyG7y2C744G5fvpbW9wkVqEduoQ562KHXvo13f+THYdPEw2b9shbt62k2ypbkxlQHi9rHoXKa3aQXJKK8jIse+RkKefE6ddvU6GXPtcEja/jAiFFfVUXEWEtdlEWASCJ6cCrZBoMZwvAEXsP0XcDpwmqt4DyZiJk0ne5q2kbPtuEahR/9I37YJ7u/j5DrG0qoZcq71Jtu7cS1X2iC9AHndJrGbDQ+no0ef7S59doxU1u8nitPXi6o2byKoNeXRFRnYDymGUyo+Bh742bhLVOrvRV0vL6YsHjlCP5FXUY2ka9UxZQz2Xp0uUApS6lnrOS6aek6dTz6lJ1PODWbwFwmvvTaMeRdtopzW5VBMeQ0e9O5Wuzs6nKzNzoT9Xv0Drs1n/SKuy8ujqrE0kOT2DbCzaIn58/hJFOUAeBResufBQRj7V/+5peEFW4WaSBsK/N2uBGAsmFd33GUYxcBwDHhtb6fxZ1mrDoq6YYxL2mKO7n1XbIs7ow5xnTI5ooK7noD2Lx2Z2HiVdj4hhZA6POYvEzrF1RJ012Rxn9M64s9rIuH2BYVFXu/YZ2KA/V9/SOd7Ddty02SQ9u4Bk5JeIR0+doRGJfe+iPFyw5kJSAGoQNMlGPqr3QOouDxzlHqANqSOFJrTheUeNMVQIDOzAX/KzQq1We+D76/rE1kW8fzc/9fCwhN7/QevYULCZHEMFPNXvO8Gj5Raginyq371ToIDs4jKCZuYEryx4yY2c4ecAKqozb38mePo5Enr/sGxtFiiglBz/+CzFgQQFtNwCnL3qLQDnF1pAR7nRwRlaDHeZPrxtgP4DH4OtShce/YmtW+Lt0IRed2xxPW7DtLnkpbeUu8kDJ7TzV1n4Iy2Hj1IX3qMPU0DmYypABSPOLKBeAU+3SgHuAYYRfubQ490GPEcnzV5Ac0vK6O5DR+gx+LiPzpxnIXbH/g+hn810wvQ5NLJn/x+76Cw17ZX6RP6K5gMU4OiBFrCBTYHjp89SZ+stYMDduilQr4AwzvCTaK8y9PAx2j8eOOJ1WlBeRa/8upZeq/2cfHT6vLh970GxpLJGLCirFAvLt4kQxsTdB4+Qk+cuikdPnqFpG3JpXP9nqWegudBTafHnr/xpoAIS+vxrOU4BcN7HT597DAWABXx84TLNLikj6FS6MgVomqUAD5VxUUj3XjSroIR+/uVX9NzlK2Lx1moRQhiBjxOXrdkALdC6LDwny9ZkkqUQupZCuza3kNTs/5AcOn6STp23BOJ45K0Oal08f/WjwadAnQJwCvRsnRNkPuD0hUs0p6ScRQEMMR4wjzlDk3A6ne5dtObKwa+OomcufsJGfBMkMGCSTNiVGTlkZWYOScvKI5hXMIJjvAZ5BVnBW1RQGsTz3YeOknW5BTSoa/y/PBX6Z3g3D4cSLcA1BUoJTjNw5q2zABYFzl2kaKIpEAXCE/tRT63ZzhkexLx5bToFmneOnDiZjTqkoyQFhEbhV23IZcJCmswIFQrJCoUEi+JopUMCBXGbrMkpYMqQFJLDLAKmCtmQX0KDohN+7KA2P9oSXFNgXf0UiHyq773WKSCx773zn3xG8zZXkF6Q10NKWSQMHYqVVpPooDJkvTR6Av3iq9+RHfsOMZOG8AnC5JL1IDSMKJ0wcz4d8NLrYJYD/mOPe+pOcHTC7dD4nv9ABzn87fF07vI0KvFKVoGUsn4jyS+rQkVSSIa+7uhvDOBdPgjXFAAFYAInKQAsAOThHM2FhzKm3zN3wSnRQb96g/rog3P4jSbRTqF9MX7g8/Sz67V074dHWSqKHw8jzYSfDHM5GjJFpS3iQy990Kh2Sp1V6NLFCx7tiAJ11hp6eBttqfrImDvDRo2jayDlTc/O5+/IZdOiAJQwZspM6mu0Vku9NoEGCqjPA9gUaJkP8PDXKdAJPvPKmxQ8eXrnzp29DaFRl6DsfJ+z1KGLweCltIb/sWr3Pnr20qds/rqEX5dXRF4d/z5Vh0bd9jZYn+OPPBQdjcYAX5O9qNcLL9H1eUWNlIA+ITO/lKC1eOnNA/gjjdAefICUB/AwWJcH+LXMAizduqmNUfFUbg1L94ZszeiMuxb9yigKeX0GZ6lD+wDd3Lfen0ZvgunjHEaTxQ/GOf3ahMlUaXV85qtq2fq/Z2BQ8oDhr1J0oDj6qAQkVADUJFRhdZzkrI3QXq7XuxIh9AGSE2xFGDRGRWmNkbGzPCG1DDSHXB9RtIW+XFJOZSpdGmeRoFR21IQ5/4i1+B5m+plM+PV5xWTq/GSYs13v+AZa1Jy7ReisMZWOmzYL6vpq5kxRAegbMCRDfk+9dOYozlqPznI9FD8/otOuzwRbMQUQILy/xmT/fBgkQTPu/SBGJa+k6kDTan5bgr/quRdGvs3CHZorjhZWjkgx/QfDRwYN55wth5dXF0Nk7Nc5xWV0XW5RvRWAYC++NZ76GqzLOGcdPLRapSU6gaAzzS2tYMVQZGIrFCCX64xqc8gtFH72n78lpv3HSMeJ06jFbE/nLAweamN2yvosCuESvH4mfKBk+u+CmYLDO8PZWo22cvXMd6Ym0Yode1jChO9HvzIFrEttjzzL2RpBERyW5IS6BSwRfZJUDrdEATJZoEljDvlqeGYOnfWnvxLz9n1sRcdvyixQgLWRBShsEee3gfOr3LmXOz8p5GGo89JaxnC21qO9jy6mzzM/YB+p4FvQAjCfWLhyLbVEd/9bZ53Oh3M2Qhd90ISufQfR6j0HcM3iH+BeHx46G0IWyIS/hcLPBuFN2/cSITOfLV+hAqyNLaCTNTbxG1x/yy0tZzEfPTWbo4n9/t1OZWh9VdcAOkfXTyGNZvkBTjGMLrgSBH6AeCoaJ2YaZw+HOTzmtE1jDAUlvBLV5xkaEt/zB3+d7qctQCbTmHHkXwThZ975i2io3iMtZOLqbck2bgH2BhbgoezaZ+Dd46fPUzRLHB2MAktWZ1BIcO4IgjfW+o8Nf3PILqwUcU0QlYxOdvXGfCiWBtP7awSz2f6S8fkRVJ/Q926Ip5/Nz+p4xeDsRo1RCVrO0jT8jfYgvSXsty9lZtPp3/yF6Lbtrhd+0xYilFYT3ymzqcVkbRAFOqhj+z17D0MNFDAi5vG4FLVo1TrI3bv/DhkkvseDr9G2HbI6TMlBAVKIxbVBzAc6KLXdORuDpZPvEPmkJNqxcj81Rcbes3T0duii4nvroqMfbgEKk0mmtUf8qX9WPh3/hz+Jms3biLAulwhZhdIydnYxTIFK4jc5CRXQwAI8ZVAwfXvo2ElQQBFTAIwMhMIN1Brb41uhUydfzvhYUNgcp0oqtzMLWIHTDKYA1hKsPNc0Xp+wyJRDla+NpsLV29SzqIqaImK/t3nJnPx201Db7b7mUOdnuuTVVLhQKwoZm0QhaYEoLFguCouWE0bL04gf5OoWq6NhHuAeFN391o79h1jayUIgfhzk/LhY2UGjj+V8j4FOvjCH/455xtqcQtYHOkEokqgtLvFuR4VCzhkZbGrTUNWwV6lQtpMIl78gnoVbQQkx31sD9D05S9OA/MnbbLSdkM1YRIVzv8GRF92S5knr87hpsXQlV0BYoyggCwr5EIolWrx1e30GuKmEDHlzLPXWBy/hbK2Hj/K1l8dMpCfOXuBhMIetF0xbuIxqQ6M+5Vx1YAoYMZKyqVu4lQiXbpJOhZXUDJZgVgQ+xdmaRphC4RlsCD6omDKXCudv4PwXhVnzJSUsAwW8NY5a71NA+4DA+dMWLqU7DxyuC4P4gXOWraK68OhvBC9jF87aKkD9cLUEIkD13gN1NQamw78aO4n6mm1rOVsdmAJeAgUUlIMPg+hVVAlK+JwpASzh3k8qASaLu1UXVKOaMA0soZbgi9xmgSUsTQULGAcWEN5IAYK3LLzHoCGQBh+ri9OoBMzDR8DIeeksmzhny+GvWvLymAn08tXr7L3oYzAVBmJ7AT7G4ATOWYdGCsgB34VKKNwqCpdvip0KtlIIkWAJhkcrAdAmWGsuU7/9Hm1z5jplW1gLloAFjH1QAQA/s/3E2pwCWgBemq36gAIwH0C/0Buqug5K3VzO2my4+2vGOHsNoLgitaV6p4gLI/hezDInJs2nKlv4Bc7aCDa1XpoCOPI4DfJK0YmLcC4KF2+InfIrqCE28Z4xOLw3f+ThsGoMmZrX36FtTl+jQvV+4gc5P/iARqkwor0scECfISPo9r0HqatoQVoDIRH9A1Z1mDILnZvO2u6Dm5tcuxj3IA5/dAo3W0VIsVmEQaWuAgvAdQUfs/15zt8IFr1piGrIy1RYk0XZzjNSWobkzHFH+sY3RJ60hJrNtken6YGJiR3U9jjfYIV2iXbEm7TN+RvUZ9kaarm/GOKAubp9xqLlFDdHXXMVPfbavCKxvGY3nThzHviEmN+2lemmC51lJv5YPToq5G38NCP9TCEXXx49gY08E34NCM+yv1yWYwwF5yq3hJXzpx6AzWQbqh48jLINV/7fgVvSXOIGUUw4eYV4rcmDpKjbHWNIRAx/pGnoI+L08MG3PHUWq91f9b5mJHS8cOUDxZALuKqjdUT/ccX6jTS7pBzKV8kfuD6+ctc+grF8/PTZFHN7cJBX5MGOPX5BITVKe8TpkPhef0PBSyqrcfmcgNIgo1zP3oNrgzj38T2h3Xtj8jOSd/sAmAKee5EKySkQvZKJ2+z5xC0tUxTOXRe9V+dQsy3itjlAG8LZHw5/u10BeT41RcV/J/gp7Faj9a3g+F7U6IxrUgGIDgp9rCWm+/fgDOnGorK6qbAK5i4KsrG4TNx14AjZCw6zAqyioKyS5m/ZSsu372L7+WcvfkJ2HTwixXmoLFNBeahAVACGWPAx4pZtNSQ4pse99nLN07zbRgAFDFE/PxwUkEpReCE9C4T/jeibup4G2Ry3jBpNEGf9KXgoY/sP+m4TfKAtruc/2/oouwc5nIkmZ5yZMzSJ9pBwQHL0d5wOOSXlomuVF0cPBcFYjsUMbraAReBIsw0SrPFRyKUwfTDfR158xqVEJFxdrtq9X0SFQSX4Q3u5ri/vtg6oABVawDzIZ/Dfg7O/IX7J6TTIElprV+p1nK05YMvid69cr6XJ6ZmY24s+OkuTWr8fkJ6GqSFJwcVNzNgy+XI3CoTWgN4cRxcFRsLwied4nZk7X0tcCfzM8YHweB2VgCtOoASypXoHNTnj/9n+vpBmQQUMfIEKGyGNh1xGtmAFDQ4K+cTmp235qrCzp7QvULS1msxckiopwRQSyBkeDbXaw88ckgrp6g+YDyxcuQYrRrZMhWUtKgTrBiQ8RoHxOi6lLV+3kY6ZmsQ2Y0eMnsjK4IaWANUmgTqfYIlsiIy721au78Z7hVpAPVQ58h0qXP6SyGcng8+ynTBIq88thev/gItscxTLT8zvPdS6SM7QLPjpwqz+QaHrQHlf40rvyHenUlwrnJeymmLVuDgtgy5YuZbOXJzChB70yigaltDnR6U1fLe30foqLqi+PnEKU4LLelyWULPvQ7IJwqwuPPZbL62ZFTxBMs0wxZj3qWx+KoWsdo+/v3+AISKmDJ06+6Bmw4PvDp9zbY7msD8w7q++movOer03CDRYFhS6ShMadQh8yfXgmITbYCF/AOV8oXPEnFVYHcU+ButYL5Opzs+gxanskTffnDSN7Ry5FIDTA5MjdJrrcgtx0+YK8usd0QMtPfrRoFBnha+vShNgizgHjpzq7I8oh5uEhzQF0AJcu8Nsc7SVCngIPKDY9YT2kT8weWstBnWo88u3J8+gWGg1nA6QIInFlTU0GqaL0MEnEPOXoPDowV2U2q4Qlm/MXrqSRvcZ+F2L9wUaKkD6P0CyAPfH+EHicQA1hVETFvXb0VNmMqfKhAfHib5jXmo6NTu7/d21PijTB4Xrw6P/OnneYnrxyjXq7Nn/u2avCdbDQxXJf5BACwAv/FhT4OeAp8Fqgenze5cSsNaAqUB7D/0VDbA6UiQuWzttWNevJkDmWVhRzfYFIqRl8ZZHAQyDGAUwnqMCsDjBvTfO8F+Bp0IhCwyLOtF3yIh/D31jDIFB+keAJSyV32YwQ6oLWSTb1q/fGmuFAvDPCtwcBQsQMVz1fH44cfNX57rLtePd5arxbjL1RDifwI7hmpsftEj+gRPc5OoJeOwuD4T7eM6J8ajH4X13WeC7Ej/wIjXkc/Gy9+M5HEMfkJW+0VYWON1DqT/loTJ86aHSVwh+6lFt/NXvuPlrxgpdAhbgKnAKJFOYeT6OBajYDxIXL+M2s4iJCiQs4qRZ8+nYD5Iobllh6zrGHH8cEhxL5Dq+/5qL4Jw/MxaOx37g4pGo8TWJh12H/nCjZPTkaXT0+9PEd6bMYOeu6/gdmFitzs4XM/JLxY9wexwVAAPKBWsuOspBk99f/PSq9KfoqnVQjeWL2yAVhZeKx0+fI9AyAi3jPrx0fuY8v36eX3OdNziu44Fn6+7VH2Prorp7H993H85dx8ca9H/o+CkRfQMmSwXlVeLpC5dxX+AeTJ7m/2fE0UZuCb2AW9S///oOKamqgXhbxH6XxX0/bHEvEAl/TcVqDa9JhNcLGGGWh+Q6r79WSHCRE1tcN2h4P72On78f3wl9uM6bJJZVSn3j+/M2bxVrb94SMUcAOc6jPJJYLYDMHBpv7hp/Nyk5hW4oKKHTFy2TTJGZ7hyguVI7DY+R0FSB6u7x+/fTDKS5dDzQ/ffwmnS9/h57J06bBnyM8D119/FY4ps0ZyFNy8qlM5eksGrW12yL4yK1HH46i9VTa1rpa7JXKYIdVQpbeKXKJrWK4PDKAHaNE5wzwntAAcFhVfXk4nHUHbvuSe9xHeM9PHdRGLzTxV//DHsHI3iGvxePXfw+Znulp8a8wh9CJxflCZ7gCZ7gCRpDEP4PxFn2OefoTXQAAAAASUVORK5CYII=">
    <title>Control Muestras LENC</title>
    <link rel="stylesheet" href="/assets/styles.css?v=5">
</head>
<body class="<?= $authenticated ? 'app-shell' : 'login-shell' ?>">
<?php if (!$authenticated): ?>
    <main class="login-layout">
        <section class="login-brand" aria-label="Control Muestras LENC">
            <span class="eyebrow">Laboratorio de ensayos</span>
            <h1>Control Muestras <strong>LENC</strong></h1>
            <p>Consulta segura y actualizada del inventario de muestras.</p>
            <div class="brand-points">
                <span>Acceso por permisos</span>
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
                <p><?= $canEditSamples
                    ? 'Puedes modificar las muestras. Los cambios se aplican en la próxima sincronización.'
                    : 'Información de consulta. Solo el usuario autorizado puede modificar muestras.' ?></p>
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
                            <td><button class="detail-button" type="button" data-sample-id="<?= (int)$row['id'] ?>"><?= $canEditSamples ? 'Editar muestra' : 'Ver detalle' ?></button></td>
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
                            <?= $canEditSamples ? 'Editar muestra' : 'Ver detalle' ?>
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
            data-edit-enabled="<?= $canEditSamples ? 'true' : 'false' ?>"
            data-csrf-token="<?= ($canUploadPhotos || $canEditSamples) ? e(csrf_token()) : '' ?>">
        <div class="dialog-header">
            <div>
                <span class="eyebrow">Detalle de la muestra</span>
                <h2 id="dialog-title">Cargando…</h2>
            </div>
            <button class="dialog-close" type="button" aria-label="Cerrar">×</button>
        </div>
        <div id="dialog-content" class="dialog-content" aria-live="polite"></div>
    </dialog>
    <script src="/assets/app.js?v=8" defer></script>
<?php endif; ?>
</body>
</html>
