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

	var CodeView = api.Widget({
		id: 'code-edit-view',
		template: template,
		init: function(args) {
			this.fileLoc = args.fileLoc;
			this.fileLoadUrl = args.fileLoadUrl;
			this.contents = ko.observable('Loading...');
			this.load();
		},
		load: function() {
			var self = this;
			show(self.fileLoc).done(function(contents) {
				self.contents(contents);
			});
		}
	});
	return CodeView;
});