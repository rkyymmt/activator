define([
	'webjars!knockout',
	'./sbt',
	'webjars!keymage',
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
		onPreDeactivate: noOp,
		// a list of parameter lists for keymage(),
		// they automatically get scoped to this widget
		keybindings: [],
		_keyScope: null,
		_keysInstalled: false,
		// called by the plugin framework to give each plugin
		// widget a unique scope
		setKeybindingScope: function(scope) {
			if (this._keyScope !== null) {
				console.log("Attempt to set key scope twice", scope);
				return;
			}
			this._keyScope = scope;
			$.each(this.keybindings, function(i, params) {
				// we need to decide if there's a sub-scope in the parameter list,
				// which would look like key('scope', 'ctrl-c', function(){})
				var adjusted = null;
				if (params.length > 2 && typeof(params[2]) == 'function') {
					adjusted = params.slice(0);
					adjusted[0] = scope + '.' + params[0];
				} else {
					adjusted = params.slice(0);
					adjusted.unshift(scope);
				}
				console.log("creating keybinding ", adjusted);
				key.apply(null, adjusted);
			});
		},
		// automatically called when widget becomes active
		installKeybindings: function() {
			if (this._keyScope === null) {
				console.log("nobody set the key scope");
				return;
			}
			if (this._keysInstalled) {
				console.log("tried to install keybindings twice", this);
				return;
			}
			this._keysInstalled = true;
			key.pushScope(this._keyScope);
		},
		// automatically called when widget becomes inactive
		uninstallKeybindings: function() {
			this._keysInstalled = false;
			key.popScope(this._keyScope);
		}
	});

	var Plugin = utils.Class({
		widgets: [],
		status: null,
		init: function() {
			if(!this.id) console.log('Error, plugin has no id: ', this);
			if(!this.name) console.log('Error, plugin has no name: ', this);
			if(!this.icon) console.log('Error, plugin has no icon: ', this);
			if(!this.url) console.log('Error, plugin has no url (default link): ', this);
			if(!this.routes) console.log('Error, plugin has no routes: ', this);

			if(this.status === null)
				this.status = ko.observable(STATUS_DEFAULT);

			this.statusBusy = ko.computed(function() {
				return this.status() == STATUS_BUSY;
			}, this);

			this.statusError = ko.computed(function() {
				return this.status() == STATUS_ERROR;
			}, this);

			this.active = ko.computed(function() {
				return activeWidget() == this.widgets[0].id;
			}, this);

			// validate widgets and set their key scope
			$.each(this.widgets, function(i, widget) {
				if (!(widget instanceof PluginWidget)) {
					console.error("widget for plugin " + this.id + " is not a PluginWidget ", widget);
				}
				widget.setKeybindingScope(this.id.replace('.', '-') + ":" + widget.id.replace('.', '-'));
			});
		}
	});

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

		if (oldWidget !== null) {
			oldWidget.uninstallKeybindings();
			oldWidget.onPreDeactivate();
		}

		activeWidget(newId);

		newWidget.onPostActivate();
		newWidget.installKeybindings();
	}

	return {
		ko: ko,
		sbt: sbt,
		utils: utils,
		Class: utils.Class, // TODO make people use api.utils.Class
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
