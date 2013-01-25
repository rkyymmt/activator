define(['css!./play.css','text!./logs.html'], function(css, template){

	var ko = req('vendors/knockout-2.2.0'),
		key = req('vendors/keymage.min');

	var Logs = Class({
		init: function(parameters){
			this.title = ko.observable("Logs")
		},
		render: function(parameters){
			var view = $(template);
			return view;
		},
		update: function(parameters){
		}
	});

	return Logs;

});
