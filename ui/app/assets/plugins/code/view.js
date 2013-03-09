define(["text!./viewWrapper.html", "text!./viewDefault.html", "./imageView", "./codeView", 'core/pluginapi'], function(viewOuter, defaultTemplate, ImageView, CodeView, api) {

	var ko = api.ko,
		key = api.key;

	// Default view for when we don't know which other to use.
	var DefaultView = api.Widget({
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
			console.log('abc', a,b,c)
		}
	});

	// Fetch utility
	var FileBrowser = api.Widget({
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
				return './' + self.file().name();
			});
			self.isFile = ko.computed(function() {
				return !self.file().isDirectory();
			});
			self.footer = ko.computed(function() {
				return self.file().location;
			});
		}
	});

	return FileBrowser;
});
