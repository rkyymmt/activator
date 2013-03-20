define([
	'vendors/knockout-2.2.1.debug',
	'./sbt',
	'./templates',
	'vendors/keymage.min',
	'./utils',
	'vendors/ace/ace',
	'./events'],
	function(ko, sbt, templates, key, utils, ignore_ace, events) {
//base class for widgets, with convenience.
//All widget classes should support the following static fields:
//  id - The identifier of the widget.
//  template - The string value of the template, unless "view" is specified.
//  view     - THe id of the template used in the template engine.
//  init     - A function to run when constructing the widget.  Takes the breadcrumb as argument (TODO - Define).
//In addition, widgets my optionally have the following methods:
//onRender - This is called when the widget is rendered to the screen.
var Widget = function(o) {
	//var oldinit = o.init;
	// Set up templating stuff
	if(o.template && o.id && !o.view) {
		o.view = templates.registerTemplate(o.id, o.template);
	}
	// TODO - Throw error if template + id *or* view are not defined...
	// TODO - Tell user why templating stuff has to be done *statically*, not
	//        on a per-instance basis.
	/*o.init = function(args) {
		// Now call user's init method.
		if(oldinit) { oldinit.call(this, args); }
		}*/
	var WidgetClass = utils.Class(o)
	WidgetClass.extend({
		// Default onRender that does nothing.
		onRender: function(elements) {}
	});
	return WidgetClass;
};

// Add knockout bindings for ace editor.  Try to capture all info we need
// here so we don't have to dig in like crazy when we need a good editor.
// Example:
//  <div class="editor" data-bind="ace: contents"/>
// <div class="editor" data-bind="ace: { contents: contents, theme: 'ace/theme/xcode', dirty: isEditorDirty, highlight: 'scala' }"/>
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
				editor.destroy();
			});
		},
		update: function (element, valueAccessor, allBindingsAccessor, viewModel) {
			var options = valueAccessor();
			var editorValue = options.contents;
			var dirtyValue = options.dirty;
			var content = ko.utils.unwrapObservable(editorValue);
			var editor = viewModel.editor;
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


		// Here we add the ability to tail
		ko.bindingHandlers.autoScroll = {
			init: function(element, valueAccessor) {
				var va = valueAccessor();
				if(ko.utils.unwrapObservable(va)) {
					if(element.scrollIntoView) {
						element.scrollIntoView(true);
					}
				}
			},
			update: function(element, valueAccessor) {
				var va = valueAccessor();
				if(ko.utils.unwrapObservable(va)) {
					if(element.scrollIntoView) {
						element.scrollIntoView(true);
					}
				}
			}
		}

	// Verifies that a new plugin configuration is acceptable for our application, or
	// issues debugging log statements on what the issue is.
	function Plugin(config) {
		//Verify plugins are 'complete'.
		if(!config.id) console.log('Error, plugin has no id: ', config);
		if(!config.name) console.log('Error, plugin has no name: ', config);
		if(!config.icon) console.log('Error, plugin has no icon: ', config);
		if(!config.url) console.log('Error, plugin has no url (default link): ', config)
		if(!config.widgets) config.widgets = [];
		return config;
	}

	var activeWidget = ko.observable();
	function setActiveWidget(widget) {
		if(typeof(widget) == 'string') {
			activeWidget(widget);
		} else if(widget.id){
			activeWidget(widget.id);
		}
	}

	return {
		ko: ko,
		sbt: sbt,
		key: key,
		Class: utils.Class,
		Widget: Widget,
		Plugin: Plugin,
		// TODO - should this be non-public?
		activeWidget: activeWidget,
		setActiveWidget: setActiveWidget,
		events: events
	};
});
