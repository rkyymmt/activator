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

	var CodeView = api.Widget({
		id: 'code-edit-view',
		template: template,
		init: function(args) {
			this.fileLoc = args.fileLoc;
			this.fileLoadUrl = args.fileLoadUrl;
			this.contents = ko.observable('Loading...');
			this.isDirty = ko.observable(false);
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
