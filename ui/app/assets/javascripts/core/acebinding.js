define([
	'vendors/knockout-2.2.1.debug',
	'vendors/ace/ace',
	'./markers'],
	function(ko, ignore_ace, markers) {

	function refreshFileMarkers(editor, markers) {
		var annotations = [];
		$.each(markers, function(idx, m) {
			// m.kind is supposed to match what we use for log levels,
			// i.e. info, warn, error; need to convert to ace which is
			// info, warning, error.
			var aceLevel = 'info';
			if (m.kind == 'error')
				aceLevel = 'error';
			else if (m.kind == 'warn')
				aceLevel = 'warning';
			annotations.push({ row: m.line - 1, column: 0, text: m.message, type: aceLevel });
		});

		editor.getSession().clearAnnotations();
		editor.getSession().setAnnotations(annotations);
	}

	// Add knockout bindings for ace editor.  Try to capture all info we need
	// here so we don't have to dig in like crazy when we need a good editor.
	// Example:
	//  <div class="editor" data-bind="ace: contents"/>
	// <div class="editor" data-bind="ace: { contents: contents, theme: 'ace/theme/xcode', dirty: isEditorDirty, highlight: 'scala', file: filemodel }"/>
	ko.bindingHandlers.ace = {
		init: function (element, valueAccessor, allBindingsAccessor, viewModel, bindingContext) {
			// First pull out all the options we may or may not use.
			var options = valueAccessor();
			// If they only provide a text field to bind into, we allow that too.
			var editorValue = options.contents || options;
			var dirtyValue = options.dirty;
			// TODO - unwrap observable?
			var theme = ko.utils.unwrapObservable(options.theme || 'ace/theme/xcode');
			var highlight = ko.utils.unwrapObservable(options.highlight || 'text');

			// We have to write our text into the element before instantiating the editor.
			$(element).text(ko.utils.unwrapObservable(editorValue))

			var editor = ace.edit(element);

			editor.setTheme(theme);
			// TODO - Check for no highlight mode as well, or allow non-built-in
			// highlighting...
			editor.getSession().setMode('ace/mode/'+highlight);

			// Assume we can sneak this on here.
			viewModel.editor = editor;
			//handle edits made in the editor
			editor.getSession().on('change', function (e) {
				if (ko.isWriteableObservable(editorValue)) {
					editorValue(editor.getValue());
				}
				// mark things dirty after an edit.
				if(ko.isWriteableObservable(dirtyValue)) {
					dirtyValue(true);
				}
			});
			// Ensure things are cleaned on destruction.
			ko.utils.domNodeDisposal.addDisposeCallback(element, function() {
				if ('fileMarkersSub' in editor) {
					editor.fileMarkersSub.dispose();
				}
				editor.destroy();
			});
		},
		update: function (element, valueAccessor, allBindingsAccessor, viewModel) {
			var options = valueAccessor();
			var editorValue = options.contents;
			var dirtyValue = options.dirty;
			var content = ko.utils.unwrapObservable(editorValue);
			var file = ko.utils.unwrapObservable(ko.utils.unwrapObservable(options.file).relative);
			var editor = viewModel.editor;

			var fileMarkers = markers.ensureFileMarkers(file);
			var oldMarkers = null;
			var markersSub = null;
			if ('fileMarkers' in editor) {
				oldMarkers = editor.fileMarkers;
				markersSub = editor.fileMarkersSub;
			}
			// when file changes, subscribe to the new markers array
			if (fileMarkers !== oldMarkers) {
				if (markersSub !== null) {
					console.log("editor dropping watch on old file markers: ", oldMarkers());
					markersSub.dispose();
				}
				console.log("editor watching file markers for " + file + ": ", fileMarkers());
				editor.fileMarkers = fileMarkers;
				editor.fileMarkersSub = fileMarkers.subscribe(function(newMarkers) {
					refreshFileMarkers(editor, newMarkers);
				});
				// initially load the file markers
				refreshFileMarkers(editor, fileMarkers());
			}

			// TODO - Don't freaking do this all the time.  We should not
			// involved in changes we caused.
			if(editor.getValue() != content) {
				editor.setValue(content, editor.getCursorPosition());
				// Update dirty value.
				if(ko.isWriteableObservable(dirtyValue)) {
					dirtyValue(false);
				}
			}
		}
	};
	return {};
});
