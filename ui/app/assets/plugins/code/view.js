define(["text!./viewWrapper.html", "text!./viewDefault.html", "./imageView", "./codeView", "./browse"], function(viewOuter, defaultTemplate, ImageView, CodeView, DirView){

	var ko = req('vendors/knockout-2.2.1.debug');

	// Default view for when we don't know which other to use.
	var DefaultView = Widget({
		id: 'code-default-view',
		template: defaultTemplate,
		init: function(args) {
			this.filename = args.file;
		} 
	});

	// Fetch utility
	function browse(location){
		return $.ajax({
			url: '/api/local/browse',
			type: 'GET',
			dataType: 'json',
			data: { location: location }
		});
	}
	var FileBrowser = Widget({
		id: 'file-browser-widget',
		template: viewOuter,
		init: function(args) {
			var self = this;
			// TODO - Detect bad url?
			self.filename = args.file;
			self.fileLoc = serverAppModel.location + (self.filename ? ('/' + self.filename) : '');
			self.fileLoadUrl = '/api/local/show?location=' + self.fileLoc; // TODO - URL encoded
			self.title = 'Browse: ./' + self.filename;
			self.subView = ko.observable(new DefaultView(args));
			// Loaded via ajax
			self.filetype = ko.observable("unknown");

			self.dataIndex = ko.computed(function() {
				if(self.subView().dataIndex) {
					return self.subView().dataIndex;
				}
				return -1;
			});
			// Now load the widget data.
			self.load();
		},
		load: function() {
			var self = this;
			browse(self.fileLoc).done(function(datas){
				self.filetype(datas.type);
				// Check to see if we need to further load...
				if(datas.type == 'code') {
					self.subView(new CodeView(self));
				} else if(datas.type == 'image') {
					self.subView(new ImageView(self));
				} else if(datas.type == 'directory') {
					self.subView(new DirView(self, datas));
				}
			});
		}
  });

  return FileBrowser;
});
