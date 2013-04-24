define([
	'vendors/knockout-2.2.1.debug',
	'./sbt',
	'vendors/keymage.min',
	'./utils',
	'./events',
	'./widget',
	'./acebinding'],
	function(ko, sbt, key, utils, events, Widget, acebinding) {

	var STATUS_DEFAULT = 'default';
	var STATUS_BUSY = 'busy';
	var STATUS_ERROR = 'error;'

	var noOp = function(){};

	var PluginWidget = utils.Class(Widget, {
		onPostActivate: noOp,
		onPreDeactivate: noOp
	});

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

		// validate widgets
		$.each(config.widgets, function(i, widget) {
			if (!(widget instanceof PluginWidget)) {
				console.error("widget for plugin " + config.id + " is not a PluginWidget ", widget);
			}
		});

		return config;
	}

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
		PluginWidget: PluginWidget,
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
