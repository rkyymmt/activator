
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
			'css':	'vendors/css'
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
	'vendors/css',
	'vendors/jquery-2.0.0b1',
	'vendors/knockout-2.2.0',
	'vendors/chain',
	'vendors/keymage.min'
],function(){
	require(['core/streams', 'core/widgets/fileselection', 'core/widgets/log'], function(streams, FileSelection, log) {
		// Register handlers on the UI.
		$(function() {
			// Create log widget before we start recording websocket events...
			var logs = new log.Log();
			logs.renderTo($('#loading-logs'));
			// Register webSocket error handler
			streams.subscribe({
				handler: function(event) {
					// TODO - Can we try to reconnect X times before failing?
					alert("Connection lost; you will need to reload the page or restart Builder");
				},
				filter: function(event) {
					return event.type == streams.WEB_SOCKET_CLOSED;
				}
			});

			function toggleWorking() {
				$('#homePage, #workingPage').toggle();
			}
			streams.subscribe(function(event) {
				// Handle all the remote events here...
				switch(event.response) {
					case 'Status':
						logs.info(event.info);
						break;
					case 'BadRequest':
						// TODO - Do better than an alert!
						alert('Unable to perform request: ' + event.errors.join(' \n'));
						toggleWorking();
						break;
					case 'RedirectToApplication':
						// NOTE - Comment this out if you want to debug showing logs!
						window.location.href = window.location.href.replace('home', 'app/'+event.appId);
						break;
					default:
						// Now check for log events...
						if(event.event && event.event.type == 'LogEvent') {
							logs.event(event.event);
						} else {
							// TODO - Should we do something more useful with these?
							console.debug('Unhandled event: ', event)
						}
					break;
				}
			});
			// Save these lookups so we don't have to do them repeatedly.
			var appNameInput = $('#newappName');
			var appLocationInput = $('#newappLocation');
			var homeDir = appLocationInput.attr('placeholder');
			var appTemplateName = $('#newAppTemplateName');
			var newButton = $('#newButton');
			var evilLocationStore = homeDir;
			function updateAppLocation(location) {
				if(location) {
					evilLocationStore = location;
					appLocationInput.val('');
				}
				var currentAppName = appNameInput.val() || appNameInput.attr('placeholder') || '';
				appLocationInput.attr('placeholder', evilLocationStore + '/' + currentAppName);
			}
			appNameInput.on('keyup', function() {
				checkFormReady();
				updateAppLocation();
			});
			function checkFormReady() {
				// if there's a template name then we should have filled in
				// at least placeholders for the other two fields.
				if (appTemplateName.val().length > 0) {
					newButton.prop("disabled", false);
					return true;
				}
				else {
					// form is not ready
					newButton.prop("disabled", true);
					return false;
				}
			}
			// Helper method to rip out form values appropriately...
			// TODO - This probably belongs in util.
			function formToJson(form) {
				var data = $(form).serializeArray();
				var o = {}
				$.each(data, function() {
					if (o[this.name] !== undefined) {
						if (!o[this.name].push) {
							o[this.name] = [o[this.name]];
						}
						o[this.name].push(this.value || '');
					} else {
						o[this.name] = this.value || '';
					}
				});
				return o;
			};
			// Hook Submissions to send to the websocket.
			$('form#newApp').on('submit', function(event) {
				event.preventDefault();

				if (checkFormReady()) {
					// disable the create button
					newButton.prop("disabled", true);

					// use the placeholder values, unless one was manually specified
					if(!appLocationInput.val())
						appLocationInput.val(appLocationInput.attr('placeholder'));
					if (!appNameInput.val())
						appNameInput.val(appNameInput.attr('placeholder'));

					var msg = formToJson(event.currentTarget);
					msg.request = 'CreateNewApplication';
					streams.send(msg);
					toggleWorking();
				}
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
			// Register fancy radio button controls.
			$('#new').on('click', 'li.template', function(event) {
				// ???
				$('input:radio', this).prop('checked',true);
				var name = $('h3', this).text();
				appTemplateName.val(name);
				var dirname = name.replace(' ', '-').replace(/[^A-Za-z0-9_-]/g, '').toLowerCase();
				appNameInput.attr('placeholder', dirname);
				updateAppLocation();
				checkFormReady()
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
