(() => {
    const dialog = document.querySelector('#sample-dialog');
    const content = document.querySelector('#dialog-content');
    const title = document.querySelector('#dialog-title');
    if (!dialog || !content || !title) return;
    const photoUploadEnabled = dialog.dataset.photoUploadEnabled === 'true';

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

    const date = (value, includeTime = false) => {
        if (!value) return 'Sin datos';
        const normalized = String(value).trim();
        if (/^\d{13}$/.test(normalized)) {
            const options = {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric'
            };
            if (includeTime) {
                options.hour = '2-digit';
                options.minute = '2-digit';
                options.hour12 = false;
            }
            return new Intl.DateTimeFormat('es-CO', options).format(new Date(Number(normalized)));
        }
        const parts = normalized.match(
            /^(\d{4})-(\d{2})-(\d{2})(?:[T\s](\d{2}):(\d{2}))?/
        );
        if (!parts) return value;
        const formattedDate = `${parts[3]}/${parts[2]}/${parts[1]}`;
        return includeTime && parts[4] && parts[5]
            ? `${formattedDate} ${parts[4]}:${parts[5]}`
            : formattedDate;
    };

    const field = (label, value) => `
        <div class="detail-field">
            <span>${escapeHtml(label)}</span>
            <strong>${escapeHtml(text(value))}</strong>
        </div>`;

    const bindPhotoUpload = (sample) => {
        const input = content.querySelector('#sample-photo-input');
        const status = content.querySelector('#sample-photo-status');
        if (!input || !status) return;

        input.addEventListener('change', async () => {
            const photo = input.files?.[0];
            if (!photo) return;

            input.disabled = true;
            status.className = 'photo-upload-status';
            status.textContent = 'Enviando fotografía…';

            const formData = new FormData();
            formData.append('sample_id', String(sample.id));
            formData.append('photo', photo);

            try {
                const response = await fetch('/api/foto-subir.php', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: {'X-CSRF-Token': dialog.dataset.csrfToken || ''},
                    body: formData
                });
                const payload = await response.json();
                if (!response.ok) {
                    throw new Error(payload.error || 'No fue posible enviar la fotografía');
                }
                status.classList.add('photo-upload-success');
                status.textContent = payload.message;
            } catch (error) {
                status.classList.add('photo-upload-error');
                status.textContent = error.message;
            } finally {
                input.disabled = false;
                input.value = '';
            }
        });
    };

    const render = (payload) => {
        const sample = payload.muestra;
        title.textContent = text(sample.codigoInterno);
        const movements = payload.movimientos?.length
            ? payload.movimientos.map(item => `
                <li>
                    <span>${escapeHtml(date(item.fechaHora, true))}</span>
                    <strong>${escapeHtml(state(item.estadoNuevo))}</strong>
                    <p>${escapeHtml(text(item.observacion, 'Actualización de la muestra'))}</p>
                    <small>${escapeHtml(text(item.usuario, 'Sistema'))}</small>
                </li>`).join('')
            : '<li class="empty-movements">No hay movimientos registrados.</li>';
        const photoUpload = photoUploadEnabled ? `
            <section class="photo-upload">
                <div>
                    <h3>Fotografía de la muestra</h3>
                    <p>Toma una foto con el móvil o selecciónala. Se guardará en la carpeta de fotos del programa durante la próxima sincronización.</p>
                </div>
                <label class="photo-upload-button" for="sample-photo-input">
                    Tomar o seleccionar foto
                </label>
                <input id="sample-photo-input" type="file" accept="image/jpeg,image/png,image/webp"
                       capture="environment">
                <p id="sample-photo-status" class="photo-upload-status" role="status" aria-live="polite">
                    JPG, PNG o WEBP · máximo 8 MB
                </p>
            </section>` : '';

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
            ${photoUpload}
            <section class="history">
                <h3>Historial reciente</h3>
                <ol>${movements}</ol>
            </section>`;
        if (photoUploadEnabled) {
            bindPhotoUpload(sample);
        }
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
