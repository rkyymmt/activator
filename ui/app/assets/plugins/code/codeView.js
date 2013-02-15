define(["text!./viewCode.html", 'core/pluginapi'], function(template, api){
	var ko = api.ko;

	// Fetch utility
	function show(location){
		return $.ajax({
			url: '/api/local/show',
			type: 'GET',
			dataType: 'text',
			data: { location: location }
		});
	}
	function save(location, code) {
		return $.ajax({
			url: '/api/local/save',
			type: 'PUT',
			dataType: 'text',
			data: {
				location: location,
				content: code
			}
		});
	}

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

	var CodeView = api.Widget({
		id: 'code-edit-view',
		template: template,
		init: function(args) {
			this.fileLoc = args.fileLoc;
			this.fileLoadUrl = args.fileLoadUrl;
			this.contents = ko.observable('Loading...');
			this.isDirty = ko.observable(false);
			// TODO - Grab the extension for now to figure out highlighting...
			this.highlight = highlightModeFor(args.filename);
			this.load();
		},
		load: function() {
			var self = this;
			show(self.fileLoc).done(function(contents) {
				self.contents(contents);
			});
		},
		save: function() {
			var self = this;
			save(self.fileLoc, self.contents()).done(function(contents) {
				// TODO - update contents or notify user?
			}).error(function() {
				// TODO - Handle errors?
				alert("Failed to save file: " + self.fileLoc)
			});
		}
	});
	return CodeView;
});
