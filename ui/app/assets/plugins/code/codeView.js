define(["text!./viewCode.html", 'css!./viewCode.css', 'core/pluginapi'], function(template, css, api){

	var ko = api.ko;

        ko.bindingHandlers.ace = {
                init: function (element, valueAccessor, allBindingsAccessor, viewModel, bindingContext) {
                        var editorValue = valueAccessor();
                        $(element).text(ko.utils.unwrapObservable(editorValue))
                        var editor = ace.edit(element);

                        editor.setTheme("ace/theme/textmate");
                        // TODO - Figure out highlight mode!...
                        editor.getSession().setMode("ace/mode/scala");

                        // Assume we can sneak this on here.
                        viewModel.editor = editor;
                        //handle edits made in the editor
                        editor.getSession().on('change', function (e) {
                                if (ko.isWriteableObservable(editorValue)) {
                                        editorValue(editor.getValue());
                                }
                        });
                },
                update: function (element, valueAccessor, allBindingsAccessor, viewModel) {
                        var content = ko.utils.unwrapObservable(valueAccessor());
                        var editor = viewModel.editor;
                        // TODO - Don't freaking do this all the time.
                        if(editor.getValue() != content) {
                                editor.setValue(content, editor.getCursorPosition());
                        }
                }
        };

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
			this.load();
		},
		load: function() {
			var self = this;
			show(self.fileLoc).done(function(contents) {
				self.contents(contents);
			});
                },
                save: function() {
                        // ZOMG!
                        var self = this;
                        save(self.fileLoc, self.contents()).done(function(contents) {
                                // TODO - update contents?
                        }).error(function() {
                                // TODO - Handle errors?
                                alert("Failed to save file: " + self.fileLoc)
                        });
		}
	});
	return CodeView;
});
