define(["text!./viewWrapper.html", "text!./viewDefault.html", "text!./viewImage.html", "text!./viewCode.html"], function(viewOuter, defaultTemplate, imageTemplate, codeTemplate){

	var ko = req('vendors/knockout-2.2.1.debug');
  var templates = req('core/templates');


	// Fetch utility
	function browse(location){
		return $.ajax({
			url: '/api/local/browse',
			type: 'GET',
			dataType: 'json',
			data: { location: location }
		});
	}

	// Fetch utility
	function showCode(location){
		return $.ajax({
			url: '/api/local/show',
			type: 'GET',
			dataType: 'text',
			data: { location: location }
		});
	}

  var FileViewer = Widget({
    id: 'file-viewer-widget',
    template: viewOuter,
    defaultTemplate: templates.registerTemplate('view-default-template', imageTemplate),
    imageTemplate: templates.registerTemplate('view-image-template', imageTemplate),
    codeTemplate: templates.registerTemplate('view-code-template', codeTemplate),
    init: function(args) {
      var self = this;
      // TODO - Detect bad url?
      self.filename = args.file;
      self.fileLoc = self.url = serverAppModel.location + '/' + self.filename;
      self.title = 'View ./' + self.filename;
      // Loaded via ajax
      self.filetype = ko.observable("unknown");
      self.contents = ko.observable('Loading...');
      self.fileLoadUrl = '/api/local/show?location=' + self.fileLoc; // TODO - URL encoded
      // Computed
      self.text = ko.computed(function() {
        var type = self.filetype();
        if(type == "unknown") {
          return "Loading ./" + self.filename;
        }
        return 'Would display: ' + type;
      });
      self.viewTemplateId = ko.computed(function() {
        var type = self.filetype();
        if(type == 'image') {
          return self.imageTemplate;
        }
        if(type == 'code') {
          return self.codeTemplate;
        }
        return self.defaultTemplate;
      });
      // Now load the widget data.
      self.loadInfo();
    },
    loadInfo: function() {
      var self = this;
      browse(this.fileLoc).done(function(datas){
			  self.filetype(datas.type);
        // Check to see if we need to further load...
        if(datas.type == 'code') {
          self.loadContents();
        }
      });
    },
    loadContents: function() {
      var self = this;
      showCode(self.fileLoc).done(function(contents) {
        self.contents(contents);
      });
    }
  });

  return FileViewer;
});
