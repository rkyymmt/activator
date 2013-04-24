define(["text!./viewWrapper.html", "text!./viewDefault.html", "./imageView", "./codeView", 'core/pluginapi'], function(viewOuter, defaultTemplate, ImageView, CodeView, api) {

	var ko = api.ko;

	function open(location) {
		return $.ajax({
			url: '/api/local/open',
			type: 'GET',
			data: {
				location: location
			}
		});
	}

	// Default view for when we don't know which other to use.
	var DefaultView = api.Class(api.Widget, {
		id: 'code-default-view',
		template: defaultTemplate,
		init: function(args) {
			var self = this;
			self.file = args.file;
			self.filename = ko.computed(function() {
				return self.file().location;
			});
		},
		afterRender: function(a,b,c){
			//console.log('abc', a,b,c)
		},
		scrollToLine: function(line) {
		}
	});

	// Fetch utility
	var FileBrowser = api.Class(api.Widget, {
		id: 'file-browser-widget',
		template: viewOuter,
		init: function(args) {
			var self = this;
			// TODO - Detect bad url?
			self.file = args.file;

			self.subView = ko.computed(function() {
				var file = self.file()
				if(file && file.type() == 'code') {
					return new CodeView({ file: self.file });
				} else if(file && file.type() == 'image') {
					return new ImageView({ file: self.file});
				}
				return new DefaultView(args);
			});
			self.title = ko.computed(function() {
				return self.file().relative();
			});
			self.isFile = ko.computed(function() {
				return !self.file().isDirectory();
			});
			self.readableFileSize = ko.computed(function() {
				var size = self.file().size();
				if(size < 1024) {
					return size.toFixed(2) + ' b';
				}
				size /= 1024.0;
				if(size < 1024) {
					return size.toFixed(2) + ' Kb';
				}
				size /= 1024.0;
				if(size < 1024) {
					return size.toFixed(2) + 'Mb';
				}
				size /= 1024.0;
				return size.toFixed(2) + 'Gb';
			});
			self.fileKindName = ko.computed(function() {
				if(self.file().type() == 'code') {
					return self.subView().highlight;
					// TODO - Add number of lines?
				}
				return self.file().type();
			});
			self.fileStats = ko.computed(function() {
				return self.fileKindName() + ' / ' + self.readableFileSize();
			});
		},
		openInSystemEditor: function() {
			var self = this;
			var loc =self.file().location;
			open(loc).success(function() {}).error(function(err) {
				console.log('Failed to open file in browser: ', err)
				alert('Failed to open file.  This may be unsupported by your system.');
			});
		},
		scrollToLine: function(line) {
			this.subView().scrollToLine(line);
		}
	});

	return FileBrowser;
});
