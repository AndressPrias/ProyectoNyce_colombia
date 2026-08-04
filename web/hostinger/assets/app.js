(() => {
    const dialog = document.querySelector('#sample-dialog');
    const content = document.querySelector('#dialog-content');
    const title = document.querySelector('#dialog-title');
    if (!dialog || !content || !title) return;

    const escapeHtml = (value) => String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');

    const text = (value, fallback = 'Sin datos') => {
        const normalized = String(value ?? '').trim();
        return normalized || fallback;
    };

    const state = (value) => text(value).toLowerCase().replaceAll('_', ' ')
        .replace(/(^|\s)\S/g, letter => letter.toUpperCase());

    const date = (value) => {
        if (!value) return 'Sin datos';
        const [day] = String(value).split(' ');
        const parts = day.split('-');
        return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : value;
    };

    const field = (label, value) => `
        <div class="detail-field">
            <span>${escapeHtml(label)}</span>
            <strong>${escapeHtml(text(value))}</strong>
        </div>`;

    const render = (payload) => {
        const sample = payload.muestra;
        title.textContent = text(sample.codigoInterno);
        const movements = payload.movimientos?.length
            ? payload.movimientos.map(item => `
                <li>
                    <span>${escapeHtml(date(item.fechaHora))}</span>
                    <strong>${escapeHtml(state(item.estadoNuevo))}</strong>
                    <p>${escapeHtml(text(item.observacion, 'Actualización de la muestra'))}</p>
                    <small>${escapeHtml(text(item.usuario, 'Sistema'))}</small>
                </li>`).join('')
            : '<li class="empty-movements">No hay movimientos registrados.</li>';

        content.innerHTML = `
            <section class="detail-grid">
                ${field('Fecha de ingreso', date(sample.fechaRecepcion))}
                ${field('Estado actual', state(sample.estado))}
                ${field('Nombre del cliente', sample.nombreCliente)}
                ${field('Referencia externa', sample.rotuloCliente)}
                ${field('Descripción muestra', sample.descripcion)}
                ${field('Cantidad', sample.cantidad)}
                ${field('Marca', sample.marca)}
                ${field('Referencia', sample.referencia)}
                ${field('Ubicación', sample.ubicacion)}
                ${field('Técnico', sample.tecnico)}
                ${field('Custodio', sample.custodio)}
                ${field('Responsable', sample.responsable)}
                ${field('Informes', sample.informes)}
                ${field('Cotizaciones', sample.cotizaciones)}
                ${field('Remisión', sample.remision)}
                ${field('Observaciones', sample.observacionAlmacenamiento)}
            </section>
            <section class="history">
                <h3>Historial reciente</h3>
                <ol>${movements}</ol>
            </section>`;
    };

    document.querySelectorAll('[data-sample-id]').forEach(button => {
        button.addEventListener('click', async () => {
            title.textContent = 'Cargando…';
            content.innerHTML = '<div class="loading-state"><span></span>Consultando información</div>';
            dialog.showModal();
            try {
                const response = await fetch(`/api/muestra.php?id=${encodeURIComponent(button.dataset.sampleId)}`, {
                    credentials: 'same-origin',
                    headers: {'Accept': 'application/json'}
                });
                const payload = await response.json();
                if (!response.ok) throw new Error(payload.error || 'No fue posible consultar la muestra');
                render(payload);
            } catch (error) {
                content.innerHTML = `<div class="alert">${escapeHtml(error.message)}</div>`;
            }
        });
    });

    dialog.querySelector('.dialog-close').addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', event => {
        if (event.target === dialog) dialog.close();
    });
})();
