(() => {
    const dialog = document.querySelector('#sample-dialog');
    const content = document.querySelector('#dialog-content');
    const title = document.querySelector('#dialog-title');
    if (!dialog || !content || !title) return;
    const photoUploadEnabled = dialog.dataset.photoUploadEnabled === 'true';
    const editEnabled = dialog.dataset.editEnabled === 'true';

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

    const documentRows = (documents, kind) => {
        const rows = documents?.length ? documents : [];
        return rows.map(item => `
            <div class="document-edit-row">
                <input name="${kind}-numero" value="${escapeHtml(item.numero)}"
                       aria-label="Número" ${kind.startsWith('cotizaciones') ? 'inputmode="numeric" pattern="[0-9]{4}" maxlength="4"' : 'maxlength="120"'} required>
                <input name="${kind}-anio" type="number" min="2000" max="9999"
                       value="${escapeHtml(item.anio)}" aria-label="Año" required>
                <button type="button" class="remove-document" aria-label="Eliminar documento">×</button>
            </div>`).join('');
    };

    const addDocumentRow = (container, kind, year = new Date().getFullYear()) => {
        const wrapper = document.createElement('div');
        wrapper.innerHTML = documentRows([{numero: '', anio: year}], kind);
        container.append(wrapper.firstElementChild);
    };

    const bindEditForm = (payload) => {
        const form = content.querySelector('#sample-edit-form');
        const toggle = content.querySelector('#sample-edit-toggle');
        if (!form || !toggle) return;
        toggle.addEventListener('click', () => {
            form.hidden = !form.hidden;
            toggle.textContent = form.hidden ? 'Editar muestra' : 'Ocultar edición';
        });
        form.addEventListener('click', event => {
            const remove = event.target.closest('.remove-document');
            if (remove) remove.closest('.document-edit-row').remove();
            const add = event.target.closest('[data-add-document]');
            if (add) addDocumentRow(form.querySelector(`#${add.dataset.addDocument}`), add.dataset.kind);
        });
        form.addEventListener('submit', async event => {
            event.preventDefault();
            const submit = form.querySelector('[type="submit"]');
            const status = form.querySelector('.edit-status');
            const values = new FormData(form);
            const collect = kind => [...form.querySelectorAll(`#${kind} .document-edit-row`)].map(row => ({
                numero: row.querySelector(`[name="${kind}-numero"]`).value,
                anio: Number(row.querySelector(`[name="${kind}-anio"]`).value)
            }));
            const data = {
                id: Number(payload.muestra.id),
                rotuloCliente: values.get('rotuloCliente'),
                nombreCliente: values.get('nombreCliente'),
                descripcion: values.get('descripcion'),
                cantidad: Number(values.get('cantidad')),
                marca: values.get('marca'),
                referencia: values.get('referencia'),
                fechaRecepcion: values.get('fechaRecepcion'),
                estado: values.get('estado'),
                ubicacion: values.get('ubicacion'),
                observacionAlmacenamiento: values.get('observacionAlmacenamiento'),
                informes: collect('informes-edicion'),
                cotizaciones: collect('cotizaciones-edicion')
            };
            submit.disabled = true;
            status.className = 'edit-status';
            status.textContent = 'Enviando cambio…';
            try {
                const response = await fetch('/api/muestra-editar.php', {
                    method: 'POST', credentials: 'same-origin',
                    headers: {'Content-Type': 'application/json', 'X-CSRF-Token': dialog.dataset.csrfToken || ''},
                    body: JSON.stringify(data)
                });
                const result = await response.json();
                if (!response.ok) throw new Error(result.error || 'No fue posible guardar el cambio');
                status.classList.add('edit-success');
                status.textContent = result.message;
            } catch (error) {
                status.classList.add('edit-error');
                status.textContent = error.message;
            } finally {
                submit.disabled = false;
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
        const editSection = editEnabled ? `
            <section class="sample-edit">
                <button id="sample-edit-toggle" class="primary-button" type="button">Ocultar edición</button>
                <form id="sample-edit-form">
                    <div class="edit-grid">
                        <label>Referencia externa<input name="rotuloCliente" maxlength="500" value="${escapeHtml(sample.rotuloCliente || '')}"></label>
                        <label>Nombre del cliente<input name="nombreCliente" maxlength="500" value="${escapeHtml(sample.nombreCliente || '')}"></label>
                        <label class="edit-wide">Descripción<textarea name="descripcion" maxlength="2000">${escapeHtml(sample.descripcion || '')}</textarea></label>
                        <label>Cantidad<input name="cantidad" type="number" min="1" max="1000000" value="${escapeHtml(sample.cantidad)}" required></label>
                        <label>Fecha de ingreso<input name="fechaRecepcion" type="date" value="${escapeHtml(String(sample.fechaRecepcion || '').slice(0, 10))}" required></label>
                        <label>Marca<input name="marca" maxlength="500" value="${escapeHtml(sample.marca || '')}"></label>
                        <label>Referencia<input name="referencia" maxlength="500" value="${escapeHtml(sample.referencia || '')}"></label>
                        <label>Estado<select name="estado" required>
                            ${['EN_CUSTODIA','ALMACENADO','EN_CURSO','LISTA_PARA_ALMACENAR','LABORATORIO_EXTERNO','REALIZAR_DISPOSICION_FINAL','ENVIADO','DESTRUCCION'].map(value => `<option value="${value}" ${sample.estado === value ? 'selected' : ''}>${escapeHtml(state(value))}</option>`).join('')}
                        </select></label>
                        <label>Ubicación<input name="ubicacion" maxlength="500" value="${escapeHtml(sample.ubicacion || '')}"></label>
                        <label class="edit-wide">Observaciones<textarea name="observacionAlmacenamiento" maxlength="4000">${escapeHtml(sample.observacionAlmacenamiento || '')}</textarea></label>
                    </div>
                    <div class="documents-edit">
                        <section><header><h4>Informes</h4><button type="button" data-add-document="informes-edicion" data-kind="informes-edicion">Agregar</button></header><div id="informes-edicion">${documentRows(payload.informesReferencias, 'informes-edicion')}</div></section>
                        <section><header><h4>Cotizaciones</h4><button type="button" data-add-document="cotizaciones-edicion" data-kind="cotizaciones-edicion">Agregar</button></header><div id="cotizaciones-edicion">${documentRows(payload.cotizacionesReferencias, 'cotizaciones-edicion')}</div></section>
                    </div>
                    <div class="edit-actions"><button class="primary-button" type="submit">Guardar cambios</button><p class="edit-status" role="status"></p></div>
                </form>
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
            ${editSection}
            ${photoUpload}
            <section class="history">
                <h3>Historial reciente</h3>
                <ol>${movements}</ol>
            </section>`;
        if (photoUploadEnabled) {
            bindPhotoUpload(sample);
        }
        if (editEnabled) {
            bindEditForm(payload);
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
