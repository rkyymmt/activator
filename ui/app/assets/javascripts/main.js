// bind() polyfill from
// https://developer.mozilla.org/en-US/docs/JavaScript/Reference/Global_Objects/Function/bind
// Monkeypatch w00t
if (!Function.prototype.bind) {
	Function.prototype.bind = function(oThis) {
		if (typeof this !== "function") {
			// closest thing possible to the ECMAScript 5 internal IsCallable
			// function
			throw new TypeError("Function.prototype.bind - what is trying to be bound is not callable");
		}

		var aArgs = Array.prototype.slice.call(arguments, 1), fToBind = this, fNOP = function() {
		}, fBound = function() {
			return fToBind.apply(this instanceof fNOP && oThis ? this : oThis,
					aArgs.concat(Array.prototype.slice.call(arguments)));
		};

		fNOP.prototype = this.prototype;
		fBound.prototype = new fNOP();

		return fBound;
	};
}

require.config({
	baseUrl:	'/public',
	urlArgs: 'bust=' + (new Date()).getTime(),
	paths: {
		// Common paths
		vendors:	'javascripts/vendors',
		core:		'javascripts/core',
		plugins:	'plugins'
	},
	map: {
		'*': {
			'text':	'vendors/text',
			'css':	'vendors/css.min'
		}
	}
})

require.onError = function (err) {
	if (err.requireType === 'timeout') {
		console.log('modules: ' + err.requireModules);
	}
}

window.req = require

require([
	// Vendors
	'vendors/text',
	'vendors/css.min',
	'vendors/jquery-2.0.0b1',
	'vendors/chain',
	'vendors/keymage.min',
	'vendors/knockout-2.2.1.debug'
],function() {
	require(['core/templates'], function() {
	require([
		'core/effects',
		// Core
		'core/utils',
		'core/sbt'
	], function() {
		require(['core/snap'])
	})
	})
})
