define(["text!./viewCode.html", 'core/pluginapi'], function(template, api){
	var ko = api.ko;

	function endsWith(str, suffix) {
		return str.indexOf(suffix, str.length - suffix.length) !== -1;
	}
	// TODO - Maybe move this somewhere more utility-like and expand it more.
	function highlightModeFor(filename) {
		if(endsWith(filename, "coffee.js")) {
			return 'coffee';
		}
		var ext = filename.split('.').pop().toLowerCase();
		if(ext == 'scala' || ext == 'sbt') {
			return 'scala';
		}
		if(ext == 'js') {
			return 'javascript';
		}
		if(ext == 'coffee') {
			return 'coffee';
		}
		if(ext == 'java') {
			return 'java';
		}
		if(ext == 'md') {
			return 'markdown';
		}
		if(ext == 'html') {
			return 'html';
		}
		if(ext == 'less') {
			return 'less';
		}
		if(ext == 'css') {
			return 'css';
		}
		if(ext == 'py') {
			return 'python';
		}
		if(ext == 'ruby') {
			return 'ruby';
		}
		return 'text';
	}

	var CodeView = api.Class(api.Widget, {
		id: 'code-edit-view',
		template: template,
		init: function(args) {
			var self = this;
			this.file = args.file;
			this.contents = args.file().contents;
			this.isDirty = args.file().isContentsDirty;
			// TODO - Grab the extension for now to figure out highlighting...
			this.highlight = highlightModeFor(args.file().name());
			this.file().loadContents();
		},
		load: function() {
			this.file().loadContents();
		},
		save: function(onDone, onCancel) {
			this.file().saveContents(onDone, onCancel);
		},
		scrollToLine: function(line) {
			// naughty knowledge of our view... not sure how else
			// to go about this.
			if ('editor' in this) {
				// rows are 0-based, lines 1-based
				this.editor.moveCursorTo(line - 1, 0);
				this.editor.scrollToRow(line - 1);
			} else {
				console.error("No editor to scroll to? bug");
			}
		}
	});
	return CodeView;
});
