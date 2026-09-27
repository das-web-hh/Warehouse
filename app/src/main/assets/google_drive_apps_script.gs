/**
 * Warehouse Google Drive Web App
 *
 * Deploy:
 *   1. Extensions → Apps Script.
 *   2. Paste this file into Code.gs.
 *   3. Deploy → New deployment → Web app.
 *      Execute as: Me
 *      Who has access: Anyone
 *   4. Put the /exec URL and the target folder ID into Warehouse Settings.
 *
 * The folder itself is never exposed to the browser. The browser receives
 * only matching file metadata and Drive URLs.
 */

function doGet(e) {
  try {
    var params = (e && e.parameter) || {};
    var action = String(params.action || 'ping').toLowerCase();
    var folder = getFolder_(params.folderId);

    if (action === 'ping') {
      return json_({
        ok: true,
        message: 'Google Drive доступен.',
        folderId: folder.getId(),
        folderName: folder.getName()
      });
    }

    if (action === 'search' || action === 'list') {
      var key = String(params.key || '').trim();
      if (!key) {
        return json_({ok: true, files: []});
      }
      return json_({
        ok: true,
        files: findFilesByKey_(folder, key)
      });
    }

    return json_({ok: false, error: 'Неизвестное действие: ' + action});
  } catch (error) {
    return json_({ok: false, error: errorMessage_(error)});
  }
}

function doPost(e) {
  try {
    var payload = readPayload_(e);
    var action = String(payload.action || 'upload').toLowerCase();
    if (action !== 'upload') {
      return json_({ok: false, error: 'Неизвестное действие: ' + action});
    }

    var folder = getFolder_(payload.folderId);
    var fileName = safeFileName_(payload.fileName);
    var mimeType = String(payload.mimeType || 'application/octet-stream');
    var base64 = String(payload.contentBase64 || '');
    if (!base64) throw new Error('Пустое содержимое файла.');

    var bytes = Utilities.base64Decode(base64);
    var blob = Utilities.newBlob(bytes, mimeType, fileName);
    var file = folder.createFile(blob);

    if (payload.makePublic === true || String(payload.makePublic).toLowerCase() === 'true') {
      try {
        file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
      } catch (sharingError) {
        // Workspace administrators can prohibit public links. The upload
        // remains successful and the authenticated owner can still open it.
      }
    }

    return json_({
      ok: true,
      file: fileInfo_(file),
      message: 'Файл загружен.'
    });
  } catch (error) {
    return json_({ok: false, error: errorMessage_(error)});
  }
}

function readPayload_(e) {
  var parameter = (e && e.parameter) || {};
  var raw = parameter.payload || '';
  if (raw) {
    try {
      return JSON.parse(raw);
    } catch (error) {
      throw new Error('Поле payload содержит некорректный JSON.');
    }
  }

  var body = e && e.postData && e.postData.contents;
  if (body) {
    try {
      return JSON.parse(body);
    } catch (error) {
      throw new Error('Тело POST-запроса содержит некорректный JSON.');
    }
  }
  return parameter;
}

function getFolder_(folderId) {
  var id = String(folderId || '').trim();
  if (!id) throw new Error('Не указан ID папки Google Диска.');
  return DriveApp.getFolderById(id);
}

function findFilesByKey_(folder, key) {
  var files = [];
  var iterator = folder.getFiles();
  while (iterator.hasNext()) {
    var file = iterator.next();
    if (file.getName().indexOf(key) !== -1) {
      files.push(fileInfo_(file));
    }
  }
  files.sort(function(a, b) {
    return String(b.createdTime).localeCompare(String(a.createdTime));
  });
  return files;
}

function fileInfo_(file) {
  var id = file.getId();
  var mimeType = file.getMimeType();
  return {
    id: id,
    name: file.getName(),
    mimeType: mimeType,
    size: file.getSize(),
    createdTime: file.getDateCreated().toISOString(),
    viewUrl: 'https://drive.google.com/uc?export=view&id=' + encodeURIComponent(id),
    previewUrl: 'https://drive.google.com/file/d/' + encodeURIComponent(id) + '/preview',
    downloadUrl: 'https://drive.google.com/uc?export=download&id=' + encodeURIComponent(id),
    webViewLink: file.getUrl()
  };
}

function safeFileName_(value) {
  var name = String(value || 'warehouse-file').trim()
    .replace(/[\\\/:*?"<>|#%{}[\]$!'@`=]/g, '_');
  return name.slice(0, 180) || 'warehouse-file';
}

function errorMessage_(error) {
  return error && error.message ? String(error.message) : String(error);
}

function json_(value) {
  return ContentService
    .createTextOutput(JSON.stringify(value))
    .setMimeType(ContentService.MimeType.JSON);
}