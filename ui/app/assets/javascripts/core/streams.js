define(function() {
    var ko = req('vendors/knockout-2.2.1.debug');
    var id = window.serverAppModel.wsUrl;
    // We can probably just connect immediately.
    var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;
    var socket = new WS(window.serverAppModel.wsUrl);
    
    function sendMessage(msg) {
      socket.send(JSON.stringify(msg));
    }
    
    function receiveEvent(event) {
    	console.log("WS Event: ", event);
    }
    
    socket.onmessage = receiveEvent;
    
    // TODO - Create global event stream or some such so we can add listeners and fire events.
    
    return {
    	// TODO - we need more public API then just "send message".
    	send: sendMessage
    };
});