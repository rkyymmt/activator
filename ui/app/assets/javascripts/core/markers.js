// track annotations for files where both editors and logs can see them
define(['webjars!knockout'], function(ko) {

	// { filename : [ { owner: ownerId, line: line, kind: kind, message: message } ] }
	var fileMarkers = {};

	function ensureFileMarkers(filename) {
		if (filename.length > 0 && filename[0] == '/') {
			filename = filename.slice(1);
		}

		if (typeof(filename) !== 'string')
			throw new Error("filename is not a string: " + filename);
		var file;
		if (filename in fileMarkers) {
			file = fileMarkers[filename];
		} else {
			file = ko.observableArray();
			fileMarkers[filename] = file;
		}
		return file;
	}

	function registerFileMarker(ownerId, filename, line, kind, message) {
		var markers = ensureFileMarkers(filename);
		if (typeof(line) == 'string')
			line = parseInt(line);
		var marker = { owner: ownerId, line: line, kind: kind, message: message };

		// remove previous marker on same line
		markers.remove(function(m) {
			return m.line == marker.line;
		});
		// put in the new one
		markers.push(marker);
		console.log("file markers for '" + filename + "' now ", markers());
	}

	function clearFileMarkers(ownerId) {
		console.log("clearing file markers for ", ownerId);
		$.each(fileMarkers, function(filename, markers) {
			markers.remove(function(item) {
				return item.owner == ownerId;
			});
		});
	}

	return {
		ensureFileMarkers: ensureFileMarkers,
		registerFileMarker: registerFileMarker,
		clearFileMarkers: clearFileMarkers
	};
});
