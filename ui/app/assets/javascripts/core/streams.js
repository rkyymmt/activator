define(function() {
	var id = window.serverAppModel.wsUrl;
	// We can probably just connect immediately.
	var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;

	console.log("WS opening: " + window.serverAppModel.wsUrl);
	var socket = new WS(window.serverAppModel.wsUrl);

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

	// Internal method to handle receiving websocket events.
	function receiveEvent(event) {
		console.log("WS Event: ", event.data, event);
		var obj = JSON.parse(event.data);
		$.each(subscribers, function(idx, subscriber) {
			if(subscriber.filter(obj)) {
				try {
				  subscriber.handler(obj);
				} catch(e) {
					console.log('Handler ', subscriber, ' failed on message ', obj, ' with error ', e);
				}
			}
		});
	}

	function onOpen(event) {
		console.log("WS opened: ", event)
	}

	function onClose(event) {
		console.log("WS closed: " + event.code + ": " + event.reason, event)
		// TODO we might basically shut down the app here and give the user
		// a reload button (we could auto-reload but might get in an infinite
		// loop that way).
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
		unsubscribe: unsubscribe
	};
});
