
require.config({
	baseUrl:	'/public',
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
	'vendors/knockout-2.2.0',
	'vendors/chain',
	'vendors/keymage.min'
],function(){
	require(['core/widgets/fileselection'], function(FileSelection) {
		// Register handlers on the UI.
		$(function() {
			function toggleDirectoryBrowser() {
				$('#newAppForm, #newAppLocationBrowser').toggle();
			};
			function toggleAppBrowser() {
				$('#openAppForm, #openAppLocationBrowser').toggle();
			};
			var homeDir = $('#newappLocation').attr('placeholder');
			var fs = new FileSelection({
				title: "Select location for new application",
				initialDir: homeDir,
				selectText: 'Select this Folder',
				onSelect: function(file) {
					$('#newappLocation').val(file);
					toggleDirectoryBrowser();
				},
				onCancel: function() {
					toggleDirectoryBrowser();
				}
			});
			fs.renderTo('#newAppLocationBrowser');
			var openFs = new FileSelection({
				selectText: 'Open this Project',
				initialDir: homeDir,
				onCancel: function() {
					toggleAppBrowser();
				},
				onSelect: function(file) {
					// TODO - Grey out the app while we wait for response.
					$.ajax({
						url: '/loadLocation',
						type: 'GET',
						dataType: 'text',
						data: {
							location: file
						}
					}).done(function(id) {
						window.location.href = window.location.href.replace('home', 'app/'+id);
					}).error(function(failure) {
						// TODO - Ungrey the app.
						alert('Failed to load project at location: ' + file)
					});
				}
			});
			openFs.renderTo('#openAppLocationBrowser');
			// Register fancy radio button controlls.
			$('#new').on('click', 'li.template', function(event) {
				// ???
				$('input:radio', this).prop('checked',true);
				var name = $('h3', this).text();
				$('#newAppTemplateName').val(name);
			})
			.on('click', '#browseAppLocation', function(event) {
				event.preventDefault();
				toggleDirectoryBrowser();
			});
			$('#open').on('click', '#openButton', function(event) {
				event.preventDefault();
				toggleAppBrowser();
			});
			// TODO - Register file selection widget...
			// Register fancy click and open app buttons
			$('#open').on('click', 'li.recentApp', function(event) {
				var url = $('a', this).attr('href');
				// TODO - Better way to do this?
				window.location.href = url;
			})
		});
	})
})
