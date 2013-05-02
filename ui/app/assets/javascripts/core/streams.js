define(function() {
	var WEB_SOCKET_CLOSED = 'WebSocketClosed';
	var id = window.wsUrl;
	// We can probably just connect immediately.
	var WS;
	if ('MozWebSocket' in window) {
		WS = window.MozWebSocket;
	} else if ('WebSocket' in window) {
		WS = window.WebSocket;
	} else {
		WS = null;
		var message = "This browser does not support WebSocket, but currently Activator requires it. " +
		"Newer IE, Firefox, Safari, or Chrome should work for example.";
		// be sure we at least log it
		console.log(message);
		// if jquery is screwy on the ancient browser, we still want to show the alert,
		// so put it in a try block.
		try {
			$('body').html('<div class="error" style="font-size: 24px; margin: 40px;"></div>');
			$('div.error').text(message);
		} catch(e) {
			console.log("jquery not working either", e);
		}

		alert(message);

		// and give up, since this module isn't going to work.
		throw new Error(message);
	}

	console.log("WS opening: " + id);
	var socket = new WS(id);

	var subscribers = []

	/** Sends a message down the event stream socket to the server.
	 *  @param msg {object}
	 */
	function sendMessage(msg) {
		socket.send(JSON.stringify(msg));
	}

	// Base filtering function to use in absense of any other.
	function allPass() {
		return true;
	}

	function randomShort() {
		return Math.floor(Math.random() * 65536)
	}

	function randomId() {
		return "listener-" + (new Date().getTime()) + "-" + randomShort() + "-" + randomShort() + "-" + randomShort();
	}

	/** Generic subscription service.
	 * @param o {function|object} Either an event handling function or an object
	 *                              consisting of:
	 *                              - id (optional): The id used to deregister later
	 *                              - handler: The event handler
	 *                              - filter (optional): A filter on messages you wish to receive.
	 *
	 * @return {object}  The subscription information, including
	 *                   the chosen filter, id and event handler.
	 *                   Note: You need to remember the ID to unregister.
	 */
	function subscribe(o) {
		var subscriber = {};
		if(typeof(o) == 'function') {
			subscriber.handler = o
		} else {
			subscriber.handler = o.handler;
		}
		subscriber.filter = o.filter || allPass;
		subscriber.id = o.id || randomId();
		subscribers.push(subscriber)
		return subscriber;
	}

	/**
	 * Unsubscribes a message handler.
	 *
	 * @param o {String|Object}  Either the id of the listener, or the subscription object
	 *                           returned by `subscribe` method.
	 */
	function unsubscribe(o) {
		// Assume an object or a string
		var id = o.id || o;
		subscribers = $.grep(subscribers, function(subscriber, idx) {
			return subscriber.id = id;
		});

	}
	// Internal method that just sends events to subscribers.
	function sendEvent(event) {
		$.each(subscribers, function(idx, subscriber) {
			if(subscriber.filter(event)) {
				try {
					subscriber.handler(event);
				} catch(e) {
					console.log('Handler ', subscriber, ' failed on message ', event, ' with error ', e);
				}
			}
		});
	}
	// Internal method to handle receiving websocket events.
	function receiveEvent(event) {
		console.log("WS Event: ", event.data, event);
		var obj = JSON.parse(event.data);
		sendEvent(obj);
	}

	function onOpen(event) {
		console.log("WS opened: ", event)
	}

	function onClose(event) {
		console.log("WS closed: " + event.code + ": " + event.reason, event)

		// TODO it would be nicer to do some kind of in-DOM lightbox dialog with
		// two buttons like "Reload" and "Go away" (maybe it's useful to not reload
		// if you need to cut-and-paste some logs for example).

		// We send out a message on failure for anyone using us to handle.

		// This is in a timeout so that when we navigate away from the page we
		// don't flash the alert box briefly.
		setTimeout(function() {
			sendEvent({
				type: WEB_SOCKET_CLOSED,
				id: id
			});
		}, 1000);
	}

	function onError(event) {
		console.log("WS error: ", event)
		// TODO do same as for closed?
	}

	socket.onopen = onOpen;
	socket.onmessage = receiveEvent;
	socket.onclose = onClose;
	socket.onerror = onError;

	// TODO - Create global event stream or some such so we can add listeners and fire events.

	return {
		// TODO - we need more public API then just "send message".
		send: sendMessage,
		subscribe: subscribe,
		unsubscribe: unsubscribe,
		WEB_SOCKET_CLOSED: WEB_SOCKET_CLOSED
	};
});
