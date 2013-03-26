
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
	require(['core/streams', 'core/widgets/fileselection'], function(streams, FileSelection) {
		// Register handlers on the UI.
		$(function() {
			function toggleWorking() {
				$('#homePage, #workingPage').toggle();
			}
			// TODO - Remove debugging...
			window.streams = streams;
			window.toggleWorking = toggleWorking;
			streams.subscribe(function(event) {
				// Handle all the remote events here...
				switch(event.response) {
					case 'BadRequest':
						alert('Unable to perform request: ' + event.errors.join(' \n'));
						toggleWorking();
						break;
					case 'RedirectToApplication':
						window.location.href = window.location.href.replace('home', 'app/'+event.appId);
						break;
					default:
						console.log('Unknown event: ', event);
				}
			});
			var appNameInput = $('#newappName');
			var appLocationInput = $('#newappLocation');
			var homeDir = appLocationInput.attr('placeholder');
			var evilLocationStore = homeDir;
			function updateAppLocation(location) {
				if(location) {
					evilLocationStore = location;
					appLocationInput.val('');
				}
				var currentAppName = appNameInput.val() || '';
				appLocationInput.attr('placeholder', evilLocationStore + '/' + currentAppName);
			}
			appNameInput.on('keyup', function() { updateAppLocation(); });
			$('#newButton').on('click', function(){
				if(!appLocationInput.val())
					appLocationInput.val(appLocationInput.attr('placeholder'));
			});
			function toggleDirectoryBrowser() {
				$('#newAppForm, #newAppLocationBrowser').toggle();
			};
			function toggleAppBrowser() {
				$('#openAppForm, #openAppLocationBrowser').toggle();
			};
			var fs = new FileSelection({
				title: "Select location for new application",
				initialDir: homeDir,
				selectText: 'Select this Folder',
				onSelect: function(file) {
					// Update our store...
					updateAppLocation(file);
					toggleDirectoryBrowser();
				},
				onCancel: function() {
					toggleDirectoryBrowser();
				}
			});
			fs.renderTo('#newAppLocationBrowser');
			var openFs = new FileSelection({
				selectText: 'Open this Project',
				listingText: 'Open as project:',
				initialDir: homeDir,
				onCancel: function() {
					toggleAppBrowser();
				},
				onSelect: function(file) {
					// TODO - Grey out the app while we wait for response.
					streams.send({
						request: 'OpenExistingApplication',
						location: file
					});
					toggleWorking();
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
