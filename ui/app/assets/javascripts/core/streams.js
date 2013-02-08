define(function() {
    var ko = req('vendors/knockout-2.2.1.debug');
    var id = window.serverAppModel.wsUrl;
    // We can probably just connect immediately.
    var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;

    console.log("WS opening: " + window.serverAppModel.wsUrl);
    var socket = new WS(window.serverAppModel.wsUrl);
    
    function sendMessage(msg) {
      socket.send(JSON.stringify(msg));
    }
    
    function receiveEvent(event) {
    	console.log("WS Event: ", event.data, event);
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
    	send: sendMessage
    };
});
