define([
	'vendors/knockout-2.2.1.debug',
	'./sbt',
	'vendors/keymage.min',
	'./utils',
	'vendors/ace/ace',
	'./events',
	'./widget'],
	function(ko, sbt, key, utils, ignore_ace, events, Widget) {


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

	var STATUS_DEFAULT = 'default';
	var STATUS_BUSY = 'busy';
	var STATUS_ERROR = 'error;'

	// Verifies that a new plugin configuration is acceptable for our application, or
	// issues debugging log statements on what the issue is.
	function Plugin(config) {
		//Verify plugins are 'complete'.
		if(!config.id) console.log('Error, plugin has no id: ', config);
		if(!config.name) console.log('Error, plugin has no name: ', config);
		if(!config.icon) console.log('Error, plugin has no icon: ', config);
		if(!config.url) console.log('Error, plugin has no url (default link): ', config)
		if(!config.widgets) config.widgets = [];
		if(!config.status) {
			console.log('Plugin has no status attribute');
			config.status = ko.observable(STATUS_DEFAULT);
		}

		config.statusBusy = ko.computed(function() {
			return this.status() == STATUS_BUSY
		}, config);

		config.statusError = ko.computed(function() {
			return this.status() == STATUS_ERROR
		}, config);

		config.active = ko.computed(function() {
			return activeWidget() == config.widgets[0].id
		}, config);

		// add plugin-specific hooks to widgets
		// (these aren't in Widget class since they
		// are plugin-specific, would probably be cleaner
		// to make a PluginWidget base class)
		var noOp = function() {};
		var hooks = [ 'onPostActivate', 'onPreDeactivate' ]
		$.each(config.widgets, function(i, widget) {
			$.each(hooks, function(i, hook) {
				if (!(hook in widget)) {
					widget[hook] = noOp;
				}
			});
		});

		return config;
	}

	var scrollStates = {};
	function findWidget(id) {
		if (!('model' in window)) {
			// this most likely means we are setting the active widget
			// from inside model.init() ...
			return null;
		}

		var matches = $.grep(window.model.widgets, function(w) { return w.id === id; });
		if (matches.length == 0) {
			return null;
		} else {
			return matches[0];
		}
	}

	var activeWidget = ko.observable("");
	function setActiveWidget(widget) {
		var newId = null;
		if (typeof(widget) == 'string') {
			newId = widget;
		} else if (widget.id){
			newId = widget.id;
		} else {
			throw new Error("need a widget id not " + widget);
		}

		var oldId = activeWidget();

		if (newId == oldId)
			return;  // no change

		var oldWidget = findWidget(oldId);
		var newWidget = findWidget(newId);
		if (newWidget === null) {
			// this probably means the app model is still being
			// constructed so widgets aren't registered.
			// In that scenario we MUST set to a widget, not an
			// ID.
			if (typeof(widget) != 'string')
				newWidget = widget;
			else
				throw new Error("don't know the widget yet for " + newId);
		}

		if (oldWidget !== null)
			oldWidget.onPreDeactivate();

		activeWidget(newId);

		newWidget.onPostActivate();
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
		events: events,
		STATUS_DEFAULT: STATUS_DEFAULT,
		STATUS_BUSY: STATUS_BUSY,
		STATUS_ERROR: STATUS_ERROR
	};
});
